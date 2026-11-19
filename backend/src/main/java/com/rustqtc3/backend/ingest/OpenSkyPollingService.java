package com.rustqtc3.backend.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustqtc3.backend.model.Track;
import com.rustqtc3.backend.model.TrackType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Polls OpenSky Network's anonymous REST API for ADS-B state vectors within a
 * configured bounding box and feeds each into TrackIngestService.
 *
 * Response shape: {"time": ..., "states": [[icao24, callsign, origin_country,
 * time_position, last_contact, longitude, latitude, baro_altitude, on_ground,
 * velocity, true_track, vertical_rate, sensors, geo_altitude, squawk, spi,
 * position_source, category], ...]}
 */
@Service
public class OpenSkyPollingService {

    private static final Logger log = LoggerFactory.getLogger(OpenSkyPollingService.class);
    private static final String STATES_URL = "https://opensky-network.org/api/states/all";

    private final TrackIngestService trackIngestService;
    private final RestClient restClient = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final double lamin;
    private final double lomin;
    private final double lamax;
    private final double lomax;

    public OpenSkyPollingService(TrackIngestService trackIngestService,
                                  @Value("${c3.opensky.lamin:1.0}") double lamin,
                                  @Value("${c3.opensky.lomin:103.0}") double lomin,
                                  @Value("${c3.opensky.lamax:1.6}") double lamax,
                                  @Value("${c3.opensky.lomax:104.2}") double lomax) {
        this.trackIngestService = trackIngestService;
        this.lamin = lamin;
        this.lomin = lomin;
        this.lamax = lamax;
        this.lomax = lomax;
    }

    @Scheduled(fixedDelayString = "${c3.opensky.poll-interval-ms:12000}")
    public void poll() {
        try {
            String body = restClient.get()
                    .uri(STATES_URL + "?lamin={lamin}&lomin={lomin}&lamax={lamax}&lomax={lomax}",
                            lamin, lomin, lamax, lomax)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            JsonNode states = root.path("states");
            if (!states.isArray()) {
                return;
            }

            int ingested = 0;
            for (JsonNode state : states) {
                Track track = toTrack(state);
                if (track != null) {
                    trackIngestService.ingest(track);
                    ingested++;
                }
            }
            log.debug("OpenSky poll: ingested {} air tracks", ingested);
        } catch (Exception e) {
            log.warn("OpenSky poll failed: {}", e.getMessage());
        }
    }

    private Track toTrack(JsonNode state) {
        if (state.size() < 11) {
            return null;
        }
        String icao24 = textOrNull(state.get(0));
        JsonNode lonNode = state.get(5);
        JsonNode latNode = state.get(6);
        if (icao24 == null || lonNode.isNull() || latNode.isNull()) {
            return null;
        }

        String callsign = textOrNull(state.get(1));
        Double baroAltitude = doubleOrNull(state.get(7));
        Double velocity = doubleOrNull(state.get(9));
        Double trueTrack = doubleOrNull(state.get(10));

        Track track = new Track();
        track.setId("AIR-" + icao24);
        track.setType(TrackType.AIR);
        track.setCallsignOrName(callsign != null ? callsign.trim() : icao24.toUpperCase());
        track.setLat(latNode.asDouble());
        track.setLon(lonNode.asDouble());
        track.setHeading(trueTrack);
        track.setSpeed(velocity);
        track.setAltitude(baroAltitude);
        track.setSource("OpenSky");
        return track;
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asText();
    }

    private Double doubleOrNull(JsonNode node) {
        return (node == null || node.isNull()) ? null : node.asDouble();
    }
}
