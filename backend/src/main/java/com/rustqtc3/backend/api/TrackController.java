package com.rustqtc3.backend.api;

import com.rustqtc3.backend.model.Track;
import com.rustqtc3.backend.model.TrackHistoryPoint;
import com.rustqtc3.backend.model.TrackType;
import com.rustqtc3.backend.repository.TrackHistoryRepository;
import com.rustqtc3.backend.repository.TrackRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/tracks")
public class TrackController {

    private final TrackRepository trackRepository;
    private final TrackHistoryRepository trackHistoryRepository;

    public TrackController(TrackRepository trackRepository, TrackHistoryRepository trackHistoryRepository) {
        this.trackRepository = trackRepository;
        this.trackHistoryRepository = trackHistoryRepository;
    }

    /**
     * Current track snapshot with optional filters. All filters are applied
     * in-memory over the (small, demo-scale) current track set.
     */
    @GetMapping
    public List<Track> list(@RequestParam(required = false) TrackType type,
                             @RequestParam(required = false) String query,
                             @RequestParam(required = false) Double minSpeed,
                             @RequestParam(required = false) Double maxSpeed,
                             @RequestParam(required = false) Double minAltitude,
                             @RequestParam(required = false) Double maxAltitude) {
        String normalizedQuery = query == null ? null : query.toLowerCase(Locale.ROOT);

        return trackRepository.findAll().stream()
                .filter(t -> type == null || t.getType() == type)
                .filter(t -> normalizedQuery == null
                        || (t.getCallsignOrName() != null
                            && t.getCallsignOrName().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                        || (t.getId() != null && t.getId().toLowerCase(Locale.ROOT).contains(normalizedQuery)))
                .filter(t -> minSpeed == null || (t.getSpeed() != null && t.getSpeed() >= minSpeed))
                .filter(t -> maxSpeed == null || (t.getSpeed() != null && t.getSpeed() <= maxSpeed))
                .filter(t -> minAltitude == null || (t.getAltitude() != null && t.getAltitude() >= minAltitude))
                .filter(t -> maxAltitude == null || (t.getAltitude() != null && t.getAltitude() <= maxAltitude))
                .toList();
    }

    @GetMapping("/{id}/history")
    public List<TrackHistoryPoint> history(@PathVariable String id) {
        return trackHistoryRepository.findByTrackIdOrderByTimestampAsc(id);
    }
}
