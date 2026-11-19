package com.rustqtc3.backend.engine;

import com.rustqtc3.backend.model.Alert;
import com.rustqtc3.backend.model.AlertType;
import com.rustqtc3.backend.model.GeoPoint;
import com.rustqtc3.backend.model.Geofence;
import com.rustqtc3.backend.model.Track;
import com.rustqtc3.backend.repository.AlertRepository;
import com.rustqtc3.backend.repository.GeofenceRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evaluates each incoming track position against active geofences and raises
 * ENTER/EXIT alerts on state transitions. Containment state is tracked per
 * (trackId, geofenceId) pair in memory; this is a single-instance demo service,
 * not designed for horizontal scaling.
 */
@Service
public class GeofenceEngineService {

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private final GeofenceRepository geofenceRepository;
    private final AlertRepository alertRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, Boolean> containmentState = new ConcurrentHashMap<>();

    public GeofenceEngineService(GeofenceRepository geofenceRepository,
                                  AlertRepository alertRepository,
                                  SimpMessagingTemplate messagingTemplate) {
        this.geofenceRepository = geofenceRepository;
        this.alertRepository = alertRepository;
        this.messagingTemplate = messagingTemplate;
    }

    public void evaluate(Track track) {
        List<Geofence> fences = geofenceRepository.findAll();
        for (Geofence fence : fences) {
            if (fence.getAppliesTo() != null && fence.getAppliesTo() != track.getType()) {
                continue;
            }

            boolean inside = contains(fence, track.getLat(), track.getLon());
            String stateKey = track.getId() + "|" + fence.getId();
            Boolean wasInside = containmentState.put(stateKey, inside);

            boolean transitioned = (wasInside == null && inside) || (wasInside != null && wasInside != inside);
            if (!transitioned) {
                continue;
            }
            // Suppress the initial observation (wasInside == null) unless it is already inside,
            // which is a real "entered while we were watching" event worth surfacing on first sight.
            AlertType alertType = inside ? AlertType.ENTER : AlertType.EXIT;
            raiseAlert(track, fence, alertType);
        }
    }

    private void raiseAlert(Track track, Geofence fence, AlertType type) {
        Alert alert = new Alert(track.getId(), track.getCallsignOrName(), fence.getId(), fence.getName(),
                type, Instant.now());
        alertRepository.save(alert);
        messagingTemplate.convertAndSend("/topic/alerts", alert);
    }

    private boolean contains(Geofence fence, double lat, double lon) {
        if (fence.getShape() == Geofence.Shape.CIRCLE) {
            return containsCircle(fence, lat, lon);
        }
        return containsPolygon(fence, lat, lon);
    }

    private boolean containsCircle(Geofence fence, double lat, double lon) {
        GeoPoint center = fence.getCenter();
        if (center == null || fence.getRadiusMeters() == null) {
            return false;
        }
        return haversineMeters(center.getLat(), center.getLon(), lat, lon) <= fence.getRadiusMeters();
    }

    private double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_METERS * c;
    }

    /** Standard ray-casting point-in-polygon test (treats lon/lat as a flat plane; fine at demo scale). */
    private boolean containsPolygon(Geofence fence, double lat, double lon) {
        List<GeoPoint> polygon = fence.getPolygon();
        if (polygon == null || polygon.size() < 3) {
            return false;
        }
        boolean inside = false;
        int n = polygon.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            GeoPoint pi = polygon.get(i);
            GeoPoint pj = polygon.get(j);
            boolean intersects = ((pi.getLon() > lon) != (pj.getLon() > lon))
                    && (lat < (pj.getLat() - pi.getLat()) * (lon - pi.getLon()) / (pj.getLon() - pi.getLon()) + pi.getLat());
            if (intersects) {
                inside = !inside;
            }
        }
        return inside;
    }
}
