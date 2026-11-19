package com.rustqtc3.backend.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A zone of interest. Either a circle (center + radiusMeters) or a polygon
 * (closed or open ring of points) depending on {@code shape}. {@code appliesTo}
 * restricts which track types trigger alerts for this fence.
 */
@Document(collection = "geofences")
public class Geofence {

    public enum Shape { CIRCLE, POLYGON }

    @Id
    private String id;

    private String name;
    private Shape shape;
    private GeoPoint center;
    private Double radiusMeters;
    private List<GeoPoint> polygon;
    private TrackType appliesTo;

    public Geofence() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Shape getShape() {
        return shape;
    }

    public void setShape(Shape shape) {
        this.shape = shape;
    }

    public GeoPoint getCenter() {
        return center;
    }

    public void setCenter(GeoPoint center) {
        this.center = center;
    }

    public Double getRadiusMeters() {
        return radiusMeters;
    }

    public void setRadiusMeters(Double radiusMeters) {
        this.radiusMeters = radiusMeters;
    }

    public List<GeoPoint> getPolygon() {
        return polygon;
    }

    public void setPolygon(List<GeoPoint> polygon) {
        this.polygon = polygon;
    }

    public TrackType getAppliesTo() {
        return appliesTo;
    }

    public void setAppliesTo(TrackType appliesTo) {
        this.appliesTo = appliesTo;
    }
}
