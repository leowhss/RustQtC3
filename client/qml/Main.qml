import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import QtLocation
import QtPositioning
import com.rustqtc3 1.0

ApplicationWindow {
    id: window
    width: 1280
    height: 800
    visible: true
    title: "RustQtC3 — Live Air & Sea Picture"

    property var selectedTrack: null
    property string selectedTrackId: ""
    property bool drawingGeofence: false
    property var drawnPoints: []

    Component {
        id: planeIconComponent
        PlaneIcon {}
    }

    Component {
        id: shipIconComponent
        ShipIcon {}
    }

    Plugin {
        id: osmPlugin
        name: "osm"

        // Qt's osm plugin normally fetches a dynamic providers list, and even
        // its hardcoded fallback (used when providersrepository is disabled)
        // can go stale across Qt releases and end up pointing at a provider
        // that requires an API key. Pointing it directly at the plain OSM
        // tile server sidesteps both paths entirely.
        PluginParameter {
            name: "osm.mapping.providersrepository.disabled"
            value: true
        }
        PluginParameter {
            name: "osm.mapping.custom.host"
            value: "https://tile.openstreetmap.org/"
        }
        PluginParameter {
            name: "osm.mapping.custom.mapcopyright"
            value: "© OpenStreetMap contributors"
        }
        PluginParameter {
            name: "osm.useragent"
            value: "RustQtC3/0.1"
        }
    }

    TrackListModel {
        id: trackModel
    }

    GeofenceListModel {
        id: geofenceModel
    }

    AlertListModel {
        id: alertModel
    }

    BackendClient {
        id: backendClient
        trackModel: trackModel
        geofenceModel: geofenceModel
        alertModel: alertModel
    }

    RowLayout {
        anchors.fill: parent
        spacing: 0

        ColumnLayout {
            Layout.fillWidth: true
            Layout.fillHeight: true
            spacing: 0

            TabBar {
                id: mapTabBar
                Layout.fillWidth: true
                TabButton { text: "2D Map" }
                TabButton { text: "3D View" }
                TabButton { text: "3D Map (MapLibre)" }
            }

            StackLayout {
                Layout.fillWidth: true
                Layout.fillHeight: true
                currentIndex: mapTabBar.currentIndex

        Map {
            id: map
            plugin: osmPlugin
            center: QtPositioning.coordinate(1.3, 103.6) // Singapore Strait, matches backend default bbox
            zoomLevel: 9
            minimumZoomLevel: 2
            maximumZoomLevel: 18

            // osm.mapping.custom.host (see Plugin above) only ADDS a MapType.CustomMap
            // entry — it doesn't replace the plugin's default map types, which still
            // require a key at some zoom levels in this Qt version's hardcoded fallback
            // table. The custom type is always the last entry in supportedMapTypes.
            Component.onCompleted: {
                for (let i = 0; i < supportedMapTypes.length; i++) {
                    if (supportedMapTypes[i].style === MapType.CustomMap) {
                        activeMapType = supportedMapTypes[i];
                        break;
                    }
                }
            }

            // Qt 6's QtLocation Map has no built-in MapGestureArea (removed from Qt 5);
            // panning/zooming is wired up manually via input handlers driving
            // pan()/zoomLevel directly.
            WheelHandler {
                acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad
                onWheel: (event) => {
                    const step = event.angleDelta.y > 0 ? 0.5 : -0.5;
                    map.zoomLevel = Math.max(map.minimumZoomLevel, Math.min(map.maximumZoomLevel, map.zoomLevel + step));
                }
            }

            DragHandler {
                id: panHandler
                target: null
                enabled: !window.drawingGeofence
                onTranslationChanged: (delta) => map.pan(-delta.x, -delta.y)
            }

            PinchHandler {
                target: null
                enabled: !window.drawingGeofence
                onScaleChanged: (delta) => {
                    const factor = Math.log2(delta);
                    map.zoomLevel = Math.max(map.minimumZoomLevel, Math.min(map.maximumZoomLevel, map.zoomLevel + factor));
                }
            }

            MapItemView {
                model: trackModel
                delegate: MapQuickItem {
                    coordinate: QtPositioning.coordinate(model.lat, model.lon)
                    anchorPoint.x: hook.width / 2
                    anchorPoint.y: hook.height / 2
                    sourceItem: Item {
                        id: hook
                        width: 36
                        height: 36

                        // Selection hook: a pulsing ring shown only around the
                        // currently selected track, drawn behind the track icon
                        // so it stays centered on the live (moving) position.
                        Rectangle {
                            id: selectionRing
                            visible: model.trackId === window.selectedTrackId
                            anchors.centerIn: parent
                            width: 30
                            height: 30
                            radius: width / 2
                            color: "transparent"
                            border.color: "#ffd54f"
                            border.width: 2

                            SequentialAnimation on opacity {
                                running: selectionRing.visible
                                loops: Animation.Infinite
                                NumberAnimation { from: 1.0; to: 0.3; duration: 700 }
                                NumberAnimation { from: 0.3; to: 1.0; duration: 700 }
                            }
                        }

                        Loader {
                            id: icon
                            anchors.centerIn: parent
                            width: model.kind === "AIR" ? 20 : 14
                            height: 20
                            rotation: model.heading
                            sourceComponent: model.kind === "AIR" ? planeIconComponent : shipIconComponent

                            MouseArea {
                                anchors.fill: parent
                                onClicked: {
                                    window.selectedTrackId = model.trackId;
                                    window.selectedTrack = {
                                        trackId: model.trackId,
                                        label: model.label,
                                        kind: model.kind,
                                        lat: model.lat,
                                        lon: model.lon,
                                        heading: model.heading,
                                        speed: model.speed,
                                        altitude: model.altitude
                                    }
                                }
                            }
                        }
                    }
                }
            }

            MapItemView {
                model: geofenceModel
                delegate: MapCircle {
                    visible: model.shape === "CIRCLE"
                    center: QtPositioning.coordinate(model.centerLat, model.centerLon)
                    radius: model.radiusMeters
                    color: "#3344aaff"
                    border.color: "#4488ff"
                    border.width: 2
                }
            }

            MapItemView {
                model: geofenceModel
                delegate: MapPolygon {
                    visible: model.shape === "POLYGON"
                    color: "#3344aaff"
                    border.color: "#4488ff"
                    border.width: 2
                    path: {
                        const points = JSON.parse(model.polygonJson || "[]");
                        return points.map((p) => QtPositioning.coordinate(p[0], p[1]));
                    }
                }
            }

            MapPolyline {
                id: drawingPreview
                visible: window.drawingGeofence && drawnPoints.length >= 4
                line.width: 2
                line.color: "#ffd54f"
                path: {
                    const coords = [];
                    for (let i = 0; i + 1 < drawnPoints.length; i += 2) {
                        coords.push(QtPositioning.coordinate(drawnPoints[i], drawnPoints[i + 1]));
                    }
                    return coords;
                }
            }

            MapItemView {
                model: {
                    const points = [];
                    for (let i = 0; i + 1 < drawnPoints.length; i += 2) {
                        points.push({ lat: drawnPoints[i], lon: drawnPoints[i + 1] });
                    }
                    return window.drawingGeofence ? points : [];
                }
                delegate: MapQuickItem {
                    coordinate: QtPositioning.coordinate(modelData.lat, modelData.lon)
                    anchorPoint.x: 4
                    anchorPoint.y: 4
                    sourceItem: Rectangle {
                        width: 8
                        height: 8
                        radius: 4
                        color: "#ffd54f"
                        border.color: "white"
                        border.width: 1
                    }
                }
            }

            MouseArea {
                anchors.fill: parent
                enabled: window.drawingGeofence
                onClicked: (mouse) => {
                    const coord = map.toCoordinate(Qt.point(mouse.x, mouse.y));
                    // Flat [lat1, lon1, lat2, lon2, ...] — matches BackendClient's
                    // createPolygonGeofence(name, flat_coords: QList<QVariant>) signature.
                    // Reassign (not push()) — property var arrays don't notify/persist
                    // in-place mutations in QML.
                    drawnPoints = drawnPoints.concat([coord.latitude, coord.longitude]);
                }
            }
        }

                Map3DView {
                    plugin: osmPlugin
                    trackModel: trackModel
                    centerLat: map.center.latitude
                    centerLon: map.center.longitude
                    zoomLevel: map.zoomLevel
                    selectedTrackId: window.selectedTrackId
                    onTrackClicked: (trackId, label, kind, lat, lon, heading, speed, altitude) => {
                        window.selectedTrackId = trackId;
                        window.selectedTrack = { trackId, label, kind, lat, lon, heading, speed, altitude };
                    }
                }

                MapLibreView {
                    trackModel: trackModel
                    selectedTrackId: window.selectedTrackId
                    onTrackSelected: (trackId, label, kind, lat, lon, heading, speed, altitude) => {
                        window.selectedTrackId = trackId;
                        window.selectedTrack = { trackId, label, kind, lat, lon, heading, speed, altitude };
                    }
                }
            }
        }

        Rectangle {
            Layout.preferredWidth: 340
            Layout.fillHeight: true
            color: "#1b1f24"

            ColumnLayout {
                anchors.fill: parent
                anchors.margins: 12
                spacing: 12

                SearchBar {
                    Layout.fillWidth: true
                    onQueryChanged: (query) => backendClient.search(query)
                }

                TrackDetailPanel {
                    Layout.fillWidth: true
                    Layout.preferredHeight: 220
                    track: window.selectedTrack
                }

                AlertPanel {
                    Layout.fillWidth: true
                    Layout.fillHeight: true
                    model: alertModel
                }

                Button {
                    Layout.fillWidth: true
                    property int pointCount: Math.floor(drawnPoints.length / 2)
                    text: window.drawingGeofence
                        ? "Finish geofence (" + pointCount + " point" + (pointCount === 1 ? "" : "s") + " — click map to add more)"
                        : "Draw geofence"
                    onClicked: {
                        if (window.drawingGeofence) {
                            if (pointCount >= 3) {
                                backendClient.createPolygonGeofence("Zone " + (geofenceModel.rowCount + 1), drawnPoints);
                            }
                            drawnPoints = [];
                        }
                        window.drawingGeofence = !window.drawingGeofence;
                    }
                }
            }
        }
    }

    Component.onCompleted: {
        backendClient.loadInitialTracks();
        backendClient.loadGeofences();
        backendClient.startLiveFeed();
    }
}
