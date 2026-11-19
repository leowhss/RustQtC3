package com.rustqtc3.backend.api.dto;

import com.rustqtc3.backend.model.Track;

/**
 * Event broadcast on /topic/tracks whenever a track is created/updated or removed
 * (removed = not seen for longer than the staleness window).
 */
public class TrackEvent {

    public enum Kind { UPSERT, REMOVE }

    private Kind kind;
    private Track track;
    private String trackId;

    public TrackEvent() {
    }

    public static TrackEvent upsert(Track track) {
        TrackEvent event = new TrackEvent();
        event.kind = Kind.UPSERT;
        event.track = track;
        event.trackId = track.getId();
        return event;
    }

    public static TrackEvent remove(String trackId) {
        TrackEvent event = new TrackEvent();
        event.kind = Kind.REMOVE;
        event.trackId = trackId;
        return event;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }
}
