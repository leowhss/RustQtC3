package com.rustqtc3.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Unified live track document for both air (ADS-B) and sea (AIS) contacts.
 * {@code id} is the natural source key, prefixed by type to avoid collisions
 * between ICAO24 hex addresses and MMSI numbers (e.g. "AIR-4b1a12", "SEA-366123456").
 */
@Document(collection = "tracks")
public class Track {

    @Id
    private String id;

    private TrackType type;
    private String callsignOrName;
    private double lat;
    private double lon;
    private Double heading;
    private Double speed;
    private Double altitude;
    private Instant lastUpdated;
    private String source;

    public Track() {
    }

    public Track(String id, TrackType type, String callsignOrName, double lat, double lon,
                 Double heading, Double speed, Double altitude, Instant lastUpdated, String source) {
        this.id = id;
        this.type = type;
        this.callsignOrName = callsignOrName;
        this.lat = lat;
        this.lon = lon;
        this.heading = heading;
        this.speed = speed;
        this.altitude = altitude;
        this.lastUpdated = lastUpdated;
        this.source = source;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public TrackType getType() {
        return type;
    }

    public void setType(TrackType type) {
        this.type = type;
    }

    public String getCallsignOrName() {
        return callsignOrName;
    }

    public void setCallsignOrName(String callsignOrName) {
        this.callsignOrName = callsignOrName;
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

    public Double getHeading() {
        return heading;
    }

    public void setHeading(Double heading) {
        this.heading = heading;
    }

    public Double getSpeed() {
        return speed;
    }

    public void setSpeed(Double speed) {
        this.speed = speed;
    }

    public Double getAltitude() {
        return altitude;
    }

    public void setAltitude(Double altitude) {
        this.altitude = altitude;
    }

    public Instant getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(Instant lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
