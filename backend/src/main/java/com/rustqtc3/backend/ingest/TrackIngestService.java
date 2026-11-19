package com.rustqtc3.backend.ingest;

import com.rustqtc3.backend.api.dto.TrackEvent;
import com.rustqtc3.backend.engine.GeofenceEngineService;
import com.rustqtc3.backend.model.Track;
import com.rustqtc3.backend.model.TrackHistoryPoint;
import com.rustqtc3.backend.repository.TrackHistoryRepository;
import com.rustqtc3.backend.repository.TrackRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Single entry point both ingestion sources (OpenSky, AISStream) funnel through:
 * upsert current state, append a history sample, broadcast to live clients, and
 * let the geofence engine evaluate the new position.
 */
@Service
public class TrackIngestService {

    private final TrackRepository trackRepository;
    private final TrackHistoryRepository trackHistoryRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final GeofenceEngineService geofenceEngineService;

    public TrackIngestService(TrackRepository trackRepository,
                               TrackHistoryRepository trackHistoryRepository,
                               SimpMessagingTemplate messagingTemplate,
                               GeofenceEngineService geofenceEngineService) {
        this.trackRepository = trackRepository;
        this.trackHistoryRepository = trackHistoryRepository;
        this.messagingTemplate = messagingTemplate;
        this.geofenceEngineService = geofenceEngineService;
    }

    public void ingest(Track track) {
        track.setLastUpdated(Instant.now());
        trackRepository.save(track);
        trackHistoryRepository.save(
                new TrackHistoryPoint(track.getId(), track.getLat(), track.getLon(), track.getLastUpdated()));

        messagingTemplate.convertAndSend("/topic/tracks", TrackEvent.upsert(track));
        geofenceEngineService.evaluate(track);
    }
}
