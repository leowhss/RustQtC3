package dsta.rfmap;

import dsta.prop.BuildingRaster;
import dsta.prop.DEM;
import dsta.prop.P1546Model;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.globes.Earth;

/**
 * Computes line-of-sight visibility between two points (sampling the DEM and
 * demo buildings along the great-circle path), and separately calls the existing
 * ITU-R P.1546 {@link P1546Model} for the real propagation-loss figure.
 */
public class LosCalculator {

    private static final double EARTH_RADIUS_M = Earth.WGS84_EQUATORIAL_RADIUS;
    private static final double EFFECTIVE_EARTH_RADIUS_M = EARTH_RADIUS_M * 4.0 / 3.0;
    private static final double MAX_SAMPLE_SPACING_M = 25.0;
    private static final int MAX_SAMPLES = 2000;

    public static class LosResult {
        public boolean clear;
        public double[] distanceM;
        public double[] terrainHeightM;
        public double[] buildingHeightM;
        public double[] losHeightM;
        public double blockingDistanceM = -1;
        public double blockingLon, blockingLat;
        public double blockingObstructionHeightM;
    }

    private final DEM dem;
    private final BuildingRaster buildingRaster;

    public LosCalculator(DEM dem, BuildingRaster buildingRaster) {
        this.dem = dem;
        this.buildingRaster = buildingRaster;
    }

    /**
     * @param txLon,txLat   transmitter location
     * @param txMastM       transmitter mast/antenna height above ground (metres)
     * @param rxLon,rxLat   receiver location
     * @param rxMastM       receiver mast/antenna height above ground (metres)
     */
    public LosResult computeLos(double txLon, double txLat, double txMastM,
                                 double rxLon, double rxLat, double rxMastM) {
        LatLon txLatLon = LatLon.fromDegrees(txLat, txLon);
        LatLon rxLatLon = LatLon.fromDegrees(rxLat, rxLon);
        double groundDistM = LatLon.greatCircleDistance(txLatLon, rxLatLon).getRadians() * EARTH_RADIUS_M;
        int samples = (int) Math.min(MAX_SAMPLES, Math.max(2, Math.ceil(groundDistM / MAX_SAMPLE_SPACING_M)));

        double txTerrain = safeHeight(dem, txLon, txLat);
        double rxTerrain = safeHeight(dem, rxLon, rxLat);
        double txEye = txTerrain + txMastM;
        double rxEye = rxTerrain + rxMastM;

        LosResult result = new LosResult();
        result.distanceM = new double[samples + 1];
        result.terrainHeightM = new double[samples + 1];
        result.buildingHeightM = new double[samples + 1];
        result.losHeightM = new double[samples + 1];
        result.clear = true;

        for (int i = 0; i <= samples; i++) {
            double t = (double) i / samples;
            LatLon pt = LatLon.interpolateGreatCircle(t, txLatLon, rxLatLon);
            double lon = pt.getLongitude().getDegrees();
            double lat = pt.getLatitude().getDegrees();
            double d = groundDistM * t;

            double terrain = safeHeight(dem, lon, lat);
            double building = buildingRaster != null ? buildingRaster.getHeight(lon, lat) : 0;
            double obstruction = terrain + building;

            // Straight sight-line height between the two eye points, plus earth-curvature bulge correction.
            double losStraight = txEye + (rxEye - txEye) * t;
            double curvatureDrop = (d * (groundDistM - d)) / (2.0 * EFFECTIVE_EARTH_RADIUS_M);
            double losHeight = losStraight - curvatureDrop;

            result.distanceM[i] = d;
            result.terrainHeightM[i] = terrain;
            result.buildingHeightM[i] = building;
            result.losHeightM[i] = losHeight;

            // Skip the immediate vicinity of TX/RX themselves to avoid flagging the antenna's own mast/building.
            boolean endpointVicinity = i == 0 || i == samples;
            if (!endpointVicinity && obstruction > losHeight && result.clear) {
                result.clear = false;
                result.blockingDistanceM = d;
                result.blockingLon = lon;
                result.blockingLat = lat;
                result.blockingObstructionHeightM = obstruction;
            }
        }

        return result;
    }

    private static double safeHeight(DEM dem, double lon, double lat) {
        double h = dem.getHeight(lon, lat);
        return h <= -900 ? 0 : h; // DEM/WorldCover implementations return -999 for out-of-coverage/error
    }

    /**
     * Runs the real ITU-R P.1546 model exactly as wired in RUNME.java (real DEM + WorldCover,
     * stub BuildingRaster), independent of the demo-building LOS check above.
     */
    public static double computePropagationLossDb(P1546Model model, double frequencyMHz,
                                                    double txHeightM, double rxHeightM,
                                                    double txLon, double txLat,
                                                    double rxLon, double rxLat,
                                                    double percentageTime) {
        return model.getPropagationLoss(frequencyMHz, txHeightM, rxHeightM, txLon, txLat, rxLon, rxLat, percentageTime);
    }
}
