package com.rustqtc3.backend.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustqtc3.backend.model.Track;
import com.rustqtc3.backend.model.TrackType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Maintains a persistent WebSocket connection to AISStream.io and feeds parsed
 * PositionReport messages into TrackIngestService. Reconnects with a fixed
 * backoff on close/error since this is a best-effort live feed, not a
 * transactional channel.
 *
 * Uses the Java-WebSocket library (org.java-websocket) rather than
 * java.net.http.WebSocket or jakarta.websocket: both of those async-I/O-based
 * clients were observed to connect successfully but never deliver a single
 * message on this environment (confirmed via thread dump and raw-socket
 * byte-level testing — the TLS/TCP connection was healthy but the read
 * completion callback never fired). Java-WebSocket's classic blocking-socket
 * model was verified independently to receive data immediately against the
 * same endpoint/key on this same machine.
 *
 * Subscription message: {"APIKey": ..., "BoundingBoxes": [[[lat,lon],[lat,lon]]]}
 * PositionReport shape (field casing verified against live traffic, which
 * differs from AISStream's own published docs for MetaData's coordinates):
 *   {"MessageType": "PositionReport",
 *    "MetaData": {"MMSI": ..., "ShipName": ..., "latitude": ..., "longitude": ...},
 *    "Message": {"PositionReport": {"Sog": ..., "Cog": ..., "TrueHeading": ...}}}
 */
@Service
public class AisStreamClientService {

    private static final Logger log = LoggerFactory.getLogger(AisStreamClientService.class);
    private static final URI AISSTREAM_URI = URI.create("wss://stream.aisstream.io/v0/stream");
    private static final long RECONNECT_DELAY_MS = 10_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TrackIngestService trackIngestService;
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "aisstream-reconnect");
        t.setDaemon(true);
        return t;
    });
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final String apiKey;
    private final double lamin;
    private final double lomin;
    private final double lamax;
    private final double lomax;

    private volatile WebSocketClient webSocketClient;

    public AisStreamClientService(TrackIngestService trackIngestService,
                                   @Value("${c3.aisstream.api-key:}") String apiKey,
                                   @Value("${c3.opensky.lamin:1.0}") double lamin,
                                   @Value("${c3.opensky.lomin:103.0}") double lomin,
                                   @Value("${c3.opensky.lamax:1.6}") double lamax,
                                   @Value("${c3.opensky.lomax:104.2}") double lomax) {
        this.trackIngestService = trackIngestService;
        this.apiKey = apiKey;
        this.lamin = lamin;
        this.lomin = lomin;
        this.lamax = lamax;
        this.lomax = lomax;
    }

    @PostConstruct
    public void start() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("c3.aisstream.api-key not set; skipping AIS ingestion (sea tracks disabled)");
            return;
        }
        connect();
    }

    @PreDestroy
    public void stop() {
        shuttingDown.set(true);
        reconnectExecutor.shutdownNow();
        if (webSocketClient != null) {
            webSocketClient.close();
        }
    }

    private void connect() {
        if (shuttingDown.get()) {
            return;
        }
        try {
            webSocketClient = new WebSocketClient(AISSTREAM_URI) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    log.info("Connected to AISStream");
                    send(buildSubscription());
                }

                @Override
                public void onMessage(String message) {
                    handleMessage(message);
                }

                @Override
                public void onMessage(ByteBuffer bytes) {
                    handleMessage(StandardCharsets.UTF_8.decode(bytes).toString());
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    log.warn("AISStream closed: {} {}", code, reason);
                    scheduleReconnect();
                }

                @Override
                public void onError(Exception ex) {
                    log.warn("AISStream error: {}", ex.getMessage());
                }
            };
            webSocketClient.connect();
        } catch (Exception e) {
            log.warn("AISStream connect failed: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (shuttingDown.get()) {
            return;
        }
        reconnectExecutor.schedule(this::connect, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    private String buildSubscription() {
        return """
                {"APIKey":"%s","BoundingBoxes":[[[%f,%f],[%f,%f]]]}
                """.formatted(apiKey, lamin, lomin, lamax, lomax).strip();
    }

    private void handleMessage(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            String messageType = root.path("MessageType").asText("");
            log.debug("AISStream message received: {}", messageType);
            if (!"PositionReport".equals(messageType)) {
                return;
            }

            JsonNode metaData = root.path("MetaData");
            JsonNode positionReport = root.path("Message").path("PositionReport");

            long mmsi = metaData.path("MMSI").asLong(-1);
            if (mmsi < 0) {
                return;
            }

            Track track = new Track();
            track.setId("SEA-" + mmsi);
            track.setType(TrackType.SEA);
            String shipName = metaData.path("ShipName").asText("").trim();
            track.setCallsignOrName(shipName.isEmpty() ? ("MMSI " + mmsi) : shipName);
            // AISStream's live MetaData payload uses lowercase latitude/longitude,
            // not the Latitude/Longitude casing shown in its own published docs.
            track.setLat(metaData.path("latitude").asDouble());
            track.setLon(metaData.path("longitude").asDouble());
            track.setHeading(resolveHeading(positionReport));
            track.setSpeed(positionReport.has("Sog") ? positionReport.get("Sog").asDouble() : null);
            track.setSource("AISStream");

            trackIngestService.ingest(track);
        } catch (Exception e) {
            log.debug("Failed to parse AISStream message: {}", e.getMessage());
        }
    }

    /**
     * AIS TrueHeading reports 511 when the heading sensor is unavailable, which
     * would otherwise make a topdown vessel icon render pointing in a bogus
     * direction. Course over ground (Cog) is populated far more reliably and is
     * a reasonable stand-in for heading on a topdown map display.
     */
    private Double resolveHeading(JsonNode positionReport) {
        if (positionReport.has("TrueHeading")) {
            double trueHeading = positionReport.get("TrueHeading").asDouble();
            if (trueHeading >= 0 && trueHeading < 360) {
                return trueHeading;
            }
        }
        if (positionReport.has("Cog")) {
            return positionReport.get("Cog").asDouble();
        }
        return null;
    }
}
