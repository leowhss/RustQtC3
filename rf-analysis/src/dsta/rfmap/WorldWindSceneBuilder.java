package dsta.rfmap;

import java.util.List;

import dsta.prop.DEM;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.Globe;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.layers.SkyGradientLayer;
import gov.nasa.worldwind.layers.StarsLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.ExtrudedPolygon;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.ShapeAttributes;

/**
 * Builds a minimal NASA WorldWind {@link Model}: a globe whose elevation comes from the existing
 * {@link DEM} (GeoTools-backed SRTM reader, via {@link DemElevationModel} - WorldWind's own
 * raster-reading pipeline depends on GDAL native bindings that aren't available here), a
 * Esri World Imagery satellite aerial imagery base layer ({@link EsriWorldImageryLayer}, requires
 * internet access), and a layer of synthetic demo buildings.
 */
public class WorldWindSceneBuilder {

    // Nudged slightly inward from the raw 102-105/0-3 DEM/WorldCover coverage overlap to avoid
    // edge-pixel PointOutsideCoverageExceptions from GeoTools at the exact GeoTIFF boundary.
    private static final double EDGE_MARGIN_DEG = 0.01;
    public static final double MIN_LON = 102.0 + EDGE_MARGIN_DEG;
    public static final double MAX_LON = 105.0 - EDGE_MARGIN_DEG;
    public static final double MIN_LAT = 0.0 + EDGE_MARGIN_DEG;
    public static final double MAX_LAT = 3.0 - EDGE_MARGIN_DEG;

    /** Approximate SRTM resolution (~90m at the equator, in degrees). */
    private static final double DEM_RESOLUTION_DEGREES = 90.0 / 111320.0;

    private final RenderableLayer markerLayer = new RenderableLayer();

    public Model buildModel(DEM dem, DemoBuildings demoBuildings) {
        Globe globe = new Earth();
        Sector coverageSector = Sector.fromDegrees(MIN_LAT, MAX_LAT, MIN_LON, MAX_LON);
        DemElevationModel elevationModel = new DemElevationModel(dem, coverageSector, DEM_RESOLUTION_DEGREES);
        globe.setElevationModel(elevationModel);

        LayerList layers = new LayerList();
        layers.add(new StarsLayer());
        layers.add(new SkyGradientLayer());
        layers.add(new EsriWorldImageryLayer());
        layers.add(buildingsLayer(demoBuildings));
        markerLayer.setName("TX/RX Markers and LOS Path");
        layers.add(markerLayer);

        return new BasicModel(globe, layers);
    }

    private Layer buildingsLayer(DemoBuildings demoBuildings) {
        RenderableLayer layer = new RenderableLayer();
        layer.setName("Demo Buildings (synthetic, for visualization only)");
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setDrawInterior(true);
        attrs.setDrawOutline(true);
        attrs.setInteriorMaterial(new Material(new java.awt.Color(0.72f, 0.72f, 0.76f)));
        attrs.setOutlineMaterial(new Material(java.awt.Color.DARK_GRAY));
        attrs.setInteriorOpacity(0.95);

        for (DemoBuildings.Building b : demoBuildings.getBuildings()) {
            List<LatLon> corners = b.footprintCorners();
            ExtrudedPolygon polygon = new ExtrudedPolygon(corners, b.heightM);
            polygon.setSideAttributes(attrs);
            polygon.setCapAttributes(attrs);
            polygon.setEnableCap(true);
            polygon.setEnableSides(true);
            layer.addRenderable(polygon);
        }
        return layer;
    }

    public RenderableLayer getMarkerLayer() {
        return markerLayer;
    }

    /** A reasonable initial camera center for the viewport (middle of the data coverage area). */
    public static Position viewportCenter() {
        return Position.fromDegrees((MIN_LAT + MAX_LAT) / 2.0, (MIN_LON + MAX_LON) / 2.0, 0);
    }
}
