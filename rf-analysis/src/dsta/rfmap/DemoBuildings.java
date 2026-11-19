package dsta.rfmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import dsta.prop.BuildingRaster;
import gov.nasa.worldwind.geom.LatLon;

/**
 * Synthetic demo building footprints used only for 3D visualization and
 * optional LOS blockage checks. This is NOT real building data -
 * {@link dsta.prop.BuildingRasterImpl} (which always returns 0) is left
 * untouched and is still what the real P.1546 propagation calculation uses.
 */
public class DemoBuildings {

    private static final double EARTH_RADIUS_M = 6371000.0;

    public static class Building {
        public final double lon, lat;
        public final double widthM, depthM;
        public final double heightM;

        public Building(double lon, double lat, double widthM, double depthM, double heightM) {
            this.lon = lon;
            this.lat = lat;
            this.widthM = widthM;
            this.depthM = depthM;
            this.heightM = heightM;
        }

        /** Four corner coordinates of the footprint (axis-aligned box in local metres), for ExtrudedPolygon. */
        public List<LatLon> footprintCorners() {
            double metersPerDegLon = (Math.PI / 180.0) * EARTH_RADIUS_M * Math.cos(Math.toRadians(lat));
            double metersPerDegLat = (Math.PI / 180.0) * EARTH_RADIUS_M;
            double dLon = (widthM / 2.0) / metersPerDegLon;
            double dLat = (depthM / 2.0) / metersPerDegLat;
            List<LatLon> corners = new ArrayList<>(4);
            corners.add(LatLon.fromDegrees(lat - dLat, lon - dLon));
            corners.add(LatLon.fromDegrees(lat - dLat, lon + dLon));
            corners.add(LatLon.fromDegrees(lat + dLat, lon + dLon));
            corners.add(LatLon.fromDegrees(lat + dLat, lon - dLon));
            return corners;
        }
    }

    private final List<Building> buildings = new ArrayList<>();

    /** Centered near the RUNME.java demo coordinates so the skyline sits where TX/RX are typically placed. */
    public DemoBuildings() {
        this(103.745, 1.330, 0.010, 0.010, 24, 42L);
    }

    public DemoBuildings(double centerLon, double centerLat, double spanLon, double spanLat, int count, long seed) {
        Random rnd = new Random(seed);
        for (int i = 0; i < count; i++) {
            double lon = centerLon + (rnd.nextDouble() - 0.5) * spanLon;
            double lat = centerLat + (rnd.nextDouble() - 0.5) * spanLat;
            double width = 20 + rnd.nextDouble() * 40;
            double depth = 20 + rnd.nextDouble() * 40;
            double height = 10 + rnd.nextDouble() * 110;
            buildings.add(new Building(lon, lat, width, depth, height));
        }
    }

    public List<Building> getBuildings() {
        return buildings;
    }

    /** Building height at (lon,lat), or 0 if outside every footprint. Uses a simple axis-aligned box test in local metres. */
    public double getHeightAt(double lon, double lat) {
        double tallest = 0;
        for (Building b : buildings) {
            double metersPerDegLon = (Math.PI / 180.0) * EARTH_RADIUS_M * Math.cos(Math.toRadians(b.lat));
            double metersPerDegLat = (Math.PI / 180.0) * EARTH_RADIUS_M;
            double dxM = (lon - b.lon) * metersPerDegLon;
            double dzM = (lat - b.lat) * metersPerDegLat;
            if (Math.abs(dxM) <= b.widthM / 2.0 && Math.abs(dzM) <= b.depthM / 2.0) {
                tallest = Math.max(tallest, b.heightM);
            }
        }
        return tallest;
    }

    /** Adapter exposing the demo buildings through the existing {@link BuildingRaster} interface. */
    public BuildingRaster asBuildingRaster() {
        return this::getHeightAt;
    }
}
