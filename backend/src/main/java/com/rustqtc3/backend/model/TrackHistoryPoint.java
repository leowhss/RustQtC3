package com.rustqtc3.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One historical position sample for a track. The {@code timestamp} field carries
 * a TTL index (see MongoIndexConfig) so old samples are purged automatically.
 */
@Document(collection = "track_history")
public class TrackHistoryPoint {

    @Id
    private String id;

    @Indexed
    private String trackId;

    private double lat;
    private double lon;

    @Indexed(name = "timestamp_ttl")
    private Instant timestamp;

    public TrackHistoryPoint() {
    }

    public TrackHistoryPoint(String trackId, double lat, double lon, Instant timestamp) {
        this.trackId = trackId;
        this.lat = lat;
        this.lon = lon;
        this.timestamp = timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLon() {
        return lon;
    }

    public void setLon(double lon) {
        this.lon = lon;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
