package com.rustqtc3.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "alerts")
public class Alert {

    @Id
    private String id;

    private String trackId;
    private String trackLabel;
    private String geofenceId;
    private String geofenceName;
    private AlertType type;
    private Instant timestamp;
    private boolean acknowledged;

    public Alert() {
    }

    public Alert(String trackId, String trackLabel, String geofenceId, String geofenceName,
                 AlertType type, Instant timestamp) {
        this.trackId = trackId;
        this.trackLabel = trackLabel;
        this.geofenceId = geofenceId;
        this.geofenceName = geofenceName;
        this.type = type;
        this.timestamp = timestamp;
        this.acknowledged = false;
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

    public String getTrackLabel() {
        return trackLabel;
    }

    public void setTrackLabel(String trackLabel) {
        this.trackLabel = trackLabel;
    }

    public String getGeofenceId() {
        return geofenceId;
    }

    public void setGeofenceId(String geofenceId) {
        this.geofenceId = geofenceId;
    }

    public String getGeofenceName() {
        return geofenceName;
    }

    public void setGeofenceName(String geofenceName) {
        this.geofenceName = geofenceName;
    }

    public AlertType getType() {
        return type;
    }

    public void setType(AlertType type) {
        this.type = type;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public void setAcknowledged(boolean acknowledged) {
        this.acknowledged = acknowledged;
    }
}
