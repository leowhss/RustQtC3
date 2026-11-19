package dsta.rfmap;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.awt.WorldWindowGLJPanel;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.BasicShapeAttributes;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.Path;
import gov.nasa.worldwind.render.PointPlacemark;
import gov.nasa.worldwind.render.PointPlacemarkAttributes;
import gov.nasa.worldwind.render.ShapeAttributes;

import dsta.prop.BuildingRasterImpl;
import dsta.prop.DEMImpl;
import dsta.prop.P1546Model;
import dsta.prop.WorldCoverImpl;

/**
 * Swing application: a 3D map (NASA WorldWind globe with DEM-derived terrain and synthetic
 * demo buildings) with click-to-place TX/RX points, reporting line-of-sight visibility and
 * ITU-R P.1546 propagation loss, reusing the existing dsta.prop DEM/BuildingRaster/WorldCover/
 * P1546Model code from CUAS_Release.
 *
 * <p>The 3D view and the control/results panel are deliberately separate top-level windows
 * (see {@link ControlsFrame}). WorldWindowGLJPanel's offscreen (FBO) rendering backend, on this
 * JOGL/driver combination, paints over lightweight Swing sibling components (JTextField,
 * JSpinner) when they share a window with it - splitting them into two JFrames sidesteps the
 * issue entirely since there is no shared component tree to mix.
 */
public class MapApp extends JFrame {

    private final DEMImpl dem = new DEMImpl();
    private final WorldCoverImpl worldCover = new WorldCoverImpl();
    private final BuildingRasterImpl buildingRaster = new BuildingRasterImpl();
    private final DemoBuildings demoBuildings = new DemoBuildings();
    private final P1546Model propModel = new P1546Model(buildingRaster, worldCover, dem);
    private final LosCalculator losCalculator = new LosCalculator(dem, demoBuildings.asBuildingRaster());

    private final WorldWindSceneBuilder sceneBuilder = new WorldWindSceneBuilder();

    private WorldWindowGLJPanel wwd;
    private ControlsFrame controls;

    private Double txLon, txLat, rxLon, rxLat;

    public MapApp() {
        super("RF Analysis - 3D Terrain Map (WorldWind)");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        java.awt.Dimension screen = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
        setSize((int) (screen.width * 0.62), (int) (screen.height * 0.88));
        setLocation(0, 0);

        buildWorldWindPanel();

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(wwd, BorderLayout.CENTER);
        getContentPane().add(buildToolbar(), BorderLayout.NORTH);
    }

    public void setControls(ControlsFrame controls) {
        this.controls = controls;
    }

    private void buildWorldWindPanel() {
        wwd = new WorldWindowGLJPanel();
        wwd.setPreferredSize(new Dimension(900, 800));

        Model model = sceneBuilder.buildModel(dem, demoBuildings);
        wwd.setModel(model);

        // Use WorldWind's own click/pick disambiguation (SelectListener) rather than a raw
        // AWT MouseListener + getCurrentPosition(): the latter reports the position under the
        // cursor as of the last rendered frame, which can be stale relative to the actual click
        // point (especially right after a camera drag), giving wrong TX/RX coordinates.
        wwd.addSelectListener(event -> {
            if (!gov.nasa.worldwind.event.SelectEvent.LEFT_CLICK.equals(event.getEventAction())) return;
            handleMapClick(event);
        });
    }

    private void handleMapClick(gov.nasa.worldwind.event.SelectEvent event) {
        gov.nasa.worldwind.pick.PickedObject terrain = event.getObjects() == null ? null : event.getObjects().getTerrainObject();
        if (terrain == null || !terrain.hasPosition()) {
            controls.setStatus("Click did not hit terrain - try again.");
            return;
        }
        Position pos = terrain.getPosition();
        double lon = pos.getLongitude().getDegrees();
        double lat = pos.getLatitude().getDegrees();

        if (txLon == null || (txLon != null && rxLon != null)) {
            txLon = lon;
            txLat = lat;
            rxLon = null;
            rxLat = null;
            controls.setStatus("TX set. Click again to set RX.");
        } else {
            rxLon = lon;
            rxLat = lat;
            controls.setStatus("RX set. Computing...");
        }
        controls.syncFieldsFromPoints(txLon, txLat, rxLon, rxLat);
        recompute();
    }

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton resetCameraBtn = new JButton("Reset camera");
        resetCameraBtn.addActionListener(e -> resetCamera());
        toolbar.add(resetCameraBtn);
        toolbar.add(new JLabel("Drag to rotate/pan, scroll to zoom (WorldWind default controls)."));
        return toolbar;
    }

    public void resetCamera() {
        wwd.getView().goTo(WorldWindSceneBuilder.viewportCenter(), 250000);
    }

    public void setPointsFromFields(double txLon, double txLat, double rxLon, double rxLat) {
        this.txLon = txLon;
        this.txLat = txLat;
        this.rxLon = rxLon;
        this.rxLat = rxLat;
        recompute();
    }

    public void clearPoints() {
        txLon = txLat = rxLon = rxLat = null;
        sceneBuilder.getMarkerLayer().removeAllRenderables();
        controls.clearResults();
        controls.setStatus("Points cleared. Click on the terrain to set TX.");
        wwd.redraw();
    }

    private void recompute() {
        if (txLon == null || txLat == null || rxLon == null || rxLat == null) {
            return;
        }
        double txHeight = controls.getTxHeight();
        double rxHeight = controls.getRxHeight();
        double freq = controls.getFrequency();
        double percent = controls.getPercentTime();

        LosCalculator.LosResult los = losCalculator.computeLos(txLon, txLat, txHeight, rxLon, rxLat, rxHeight);

        updateMarkersAndPath(los);

        double groundDist = los.distanceM[los.distanceM.length - 1];

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Ground distance: %.1f m (%.2f km)%n%n", groundDist, groundDist / 1000.0));
        sb.append("--- Line of Sight (incl. demo buildings) ---\n");
        if (los.clear) {
            sb.append("Status: CLEAR\n");
        } else {
            sb.append("Status: BLOCKED\n");
            sb.append(String.format("Blocked at %.1f m along path%n", los.blockingDistanceM));
            sb.append(String.format("Obstruction lon/lat: %.6f, %.6f%n", los.blockingLon, los.blockingLat));
            sb.append(String.format("Obstruction height (terrain+building): %.1f m MSL%n", los.blockingObstructionHeightM));
        }

        sb.append(String.format("%n"));
        sb.append("--- ITU-R P.1546 Propagation Loss ---\n");
        sb.append("(real DEM + WorldCover; BuildingRasterImpl stub = 0)\n");
        try {
            double lossDb = LosCalculator.computePropagationLossDb(propModel, freq, txHeight, rxHeight,
                    txLon, txLat, rxLon, rxLat, percent);
            sb.append(String.format("Path loss: %.2f dB%n", lossDb));
            sb.append(String.format("(freq=%.0f MHz, txH=%.1f m, rxH=%.1f m, time=%.0f%%)%n", freq, txHeight, rxHeight, percent));
        } catch (Exception ex) {
            sb.append("Could not compute propagation loss: ").append(ex.getMessage()).append("\n");
        }

        controls.setResults(sb.toString());
        controls.setStatus("Done. Click again to start a new TX/RX pair.");
    }

    private void updateMarkersAndPath(LosCalculator.LosResult los) {
        RenderableLayer layer = sceneBuilder.getMarkerLayer();
        layer.removeAllRenderables();

        layer.addRenderable(makeMarker(txLon, txLat, Color.RED, "TX"));
        layer.addRenderable(makeMarker(rxLon, rxLat, Color.BLUE, "RX"));

        // Draw the LOS path as a sequence of segments, colored green (clear) or red (blocked)
        // from the point where blockage starts onward.
        ShapeAttributes clearAttrs = lineAttrs(Color.GREEN);
        ShapeAttributes blockedAttrs = lineAttrs(Color.RED);

        LatLon txLatLon = LatLon.fromDegrees(txLat, txLon);
        LatLon rxLatLon = LatLon.fromDegrees(rxLat, rxLon);

        int n = los.distanceM.length;
        List<Position> currentSegment = new ArrayList<>();
        boolean currentBlocked = false;
        for (int i = 0; i < n; i++) {
            double t = (double) i / (n - 1);
            LatLon pt = LatLon.interpolateGreatCircle(t, txLatLon, rxLatLon);
            double lon = pt.getLongitude().getDegrees();
            double lat = pt.getLatitude().getDegrees();
            double alt = los.losHeightM[i];
            boolean blocked = !los.clear && los.blockingDistanceM >= 0 && los.distanceM[i] >= los.blockingDistanceM;

            if (!currentSegment.isEmpty() && blocked != currentBlocked) {
                currentSegment.add(Position.fromDegrees(lat, lon, alt));
                flushSegment(layer, currentSegment, currentBlocked ? blockedAttrs : clearAttrs);
                currentSegment = new ArrayList<>();
                currentSegment.add(Position.fromDegrees(lat, lon, alt));
            } else {
                currentSegment.add(Position.fromDegrees(lat, lon, alt));
            }
            currentBlocked = blocked;
        }
        flushSegment(layer, currentSegment, currentBlocked ? blockedAttrs : clearAttrs);

        wwd.redraw();
    }

    private void flushSegment(RenderableLayer layer, List<Position> positions, ShapeAttributes attrs) {
        if (positions.size() < 2) return;
        Path path = new Path(positions);
        path.setAttributes(attrs);
        path.setAltitudeMode(WorldWind.ABSOLUTE);
        layer.addRenderable(path);
    }

    private ShapeAttributes lineAttrs(Color color) {
        ShapeAttributes attrs = new BasicShapeAttributes();
        attrs.setOutlineMaterial(new Material(color));
        attrs.setOutlineWidth(3.0);
        return attrs;
    }

    private PointPlacemark makeMarker(double lon, double lat, Color color, String label) {
        double groundHeight = dem.getHeight(lon, lat);
        if (groundHeight <= -900) groundHeight = 0;
        PointPlacemark marker = new PointPlacemark(Position.fromDegrees(lat, lon, groundHeight + 5));
        marker.setLabelText(label);
        marker.setAltitudeMode(WorldWind.ABSOLUTE);
        PointPlacemarkAttributes attrs = new PointPlacemarkAttributes();
        attrs.setLineMaterial(new Material(color));
        attrs.setUsePointAsDefaultImage(true);
        attrs.setScale(8.0);
        marker.setAttributes(attrs);
        return marker;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MapApp map = new MapApp();
            ControlsFrame controls = new ControlsFrame(map);
            map.setControls(controls);

            map.setVisible(true);
            controls.setLocation(map.getX() + map.getWidth(), map.getY());
            controls.setVisible(true);

            SwingUtilities.invokeLater(map::resetCamera);
        });
    }
}
