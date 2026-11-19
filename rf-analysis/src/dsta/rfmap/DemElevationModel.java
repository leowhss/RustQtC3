package dsta.rfmap;

import java.util.List;

import dsta.prop.DEM;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.terrain.AbstractElevationModel;

/**
 * A WorldWind {@link gov.nasa.worldwind.globes.ElevationModel} that delegates straight to the
 * existing {@link DEM} (GeoTools-backed SRTM reader), instead of WorldWind's own raster-reading
 * pipeline (which depends on GDAL bindings that aren't bundled/working in this setup). This keeps
 * a single source of truth for elevation shared with the LOS/propagation calculations.
 */
public class DemElevationModel extends AbstractElevationModel {

    private final DEM dem;
    private final Sector coverageSector;
    private final double bestResolutionDegrees;
    private boolean extremesCachingEnabled = true;

    public DemElevationModel(DEM dem, Sector coverageSector, double bestResolutionDegrees) {
        this.dem = dem;
        this.coverageSector = coverageSector;
        this.bestResolutionDegrees = bestResolutionDegrees;
        setMissingDataSignal(-999);
        setMissingDataReplacement(0);
    }

    @Override
    public void setExtremesCachingEnabled(boolean enabled) {
        this.extremesCachingEnabled = enabled;
    }

    @Override
    public boolean isExtremesCachingEnabled() {
        return extremesCachingEnabled;
    }

    private double lookup(double lonDeg, double latDeg) {
        // Avoid calling into the GeoTools-backed DEM for points outside its coverage: each
        // out-of-range call throws (and logs) a PointOutsideCoverageException internally.
        if (!coverageSector.contains(Angle.fromDegrees(latDeg), Angle.fromDegrees(lonDeg))) {
            return 0;
        }
        double h = dem.getHeight(lonDeg, latDeg);
        return h <= -900 ? 0 : h;
    }

    @Override
    public int intersects(Sector sector) {
        return coverageSector.intersects(sector) ? 0 : -1;
    }

    @Override
    public boolean contains(Angle latitude, Angle longitude) {
        return coverageSector.contains(latitude, longitude);
    }

    @Override
    public double getMaxElevation() {
        return 3000; // generous upper bound for this region; only used for view-frustum/culling heuristics
    }

    @Override
    public double getMinElevation() {
        return -50;
    }

    @Override
    public double[] getExtremeElevations(Angle latitude, Angle longitude) {
        return new double[]{getMinElevation(), getMaxElevation()};
    }

    @Override
    public double[] getExtremeElevations(Sector sector) {
        return new double[]{getMinElevation(), getMaxElevation()};
    }

    @Override
    public double getBestResolution(Sector sector) {
        return Math.toRadians(bestResolutionDegrees);
    }

    @Override
    public double getUnmappedElevation(Angle latitude, Angle longitude) {
        return lookup(longitude.getDegrees(), latitude.getDegrees());
    }

    @Override
    public double getElevations(Sector sector, List<? extends LatLon> latlons, double targetResolution, double[] buffer) {
        return getUnmappedElevations(sector, latlons, targetResolution, buffer);
    }

    @Override
    public double getUnmappedElevations(Sector sector, List<? extends LatLon> latlons, double targetResolution, double[] buffer) {
        for (int i = 0; i < latlons.size(); i++) {
            LatLon ll = latlons.get(i);
            buffer[i] = lookup(ll.getLongitude().getDegrees(), ll.getLatitude().getDegrees());
        }
        return getBestResolution(sector);
    }

    @Override
    public double getLocalDataAvailability(Sector sector, Double targetResolution) {
        return 1.0;
    }
}
