import QtQuick
import QtQuick3D
import QtLocation
import QtPositioning

// 3D tactical plot: a QtQuick3D scene whose ground plane is textured with a
// live capture of a second, offscreen QtLocation Map (same plugin/center/zoom
// as the 2D tab), so real OSM imagery shows under the tracks without any
// custom tile-fetch/stitch code. Air tracks render as cones whose height
// encodes altitude; sea tracks render as flat discs on the ground.
Item {
    id: root

    // Supplied by Main.qml so this view shares the same tile plugin/config
    // and stays in sync with the 2D map's current view.
    required property var plugin
    required property var trackModel
    required property real centerLat
    required property real centerLon
    required property real zoomLevel
    property string selectedTrackId: ""

    signal trackClicked(string trackId, string label, string kind, real lat, real lon, real heading, real speed, real altitude)

    // Meters-per-scene-unit at the current zoom: chosen so the ground plane
    // (groundSizeUnits wide) covers roughly the same geographic extent the 2D
    // map shows at this zoom level. Approximate — this is a tactical plot, not
    // a surveying tool.
    readonly property real groundSizeUnits: 2000
    readonly property real metersPerUnit: 156543.03392 * Math.cos(centerLat * Math.PI / 180) / Math.pow(2, zoomLevel) * (groundSizeUnits / 2000)
    readonly property real altitudeScale: 20.0 // scene units per 1000m of altitude

    // Equirectangular local projection: fine at city/strait scale, not geodesic.
    function lonToX(lon) {
        const meters = (lon - centerLon) * 111320 * Math.cos(centerLat * Math.PI / 180);
        return meters / metersPerUnit;
    }
    function latToZ(lat) {
        const meters = (centerLat - lat) * 110574;
        return meters / metersPerUnit;
    }

    Map {
        id: offscreenMap
        width: 1024
        height: 1024
        plugin: root.plugin
        center: QtPositioning.coordinate(root.centerLat, root.centerLon)
        zoomLevel: root.zoomLevel

        // Never added to a visible layout — only exists to be captured by
        // Texture.sourceItem below. Qt renders sourceItem offscreen regardless
        // of whether it's shown in the normal 2D scene.
        visible: false

        Component.onCompleted: {
            for (let i = 0; i < supportedMapTypes.length; i++) {
                if (supportedMapTypes[i].style === MapType.CustomMap) {
                    activeMapType = supportedMapTypes[i];
                    break;
                }
            }
        }
    }

    View3D {
        id: view3d
        anchors.fill: parent
        environment: SceneEnvironment {
            clearColor: "#1b1f24"
            backgroundMode: SceneEnvironment.Color
        }

        PerspectiveCamera {
            id: camera
            position: Qt.vector3d(0, 900, 900)
            eulerRotation: Qt.vector3d(-45, 0, 0)
            clipFar: 10000
        }

        DirectionalLight {
            eulerRotation: Qt.vector3d(-45, -45, 0)
            brightness: 1.2
        }

        Model {
            id: ground
            source: "#Rectangle"
            scale: Qt.vector3d(root.groundSizeUnits / 100, root.groundSizeUnits / 100, 1)
            eulerRotation: Qt.vector3d(-90, 0, 0)
            materials: PrincipledMaterial {
                baseColorMap: Texture {
                    sourceItem: offscreenMap
                }
                lighting: PrincipledMaterial.NoLighting
            }
        }

        Repeater3D {
            model: root.trackModel
            delegate: Node {
                required property string trackId
                required property string kind
                required property string label
                required property real lat
                required property real lon
                required property real heading
                required property real speed
                required property real altitude

                position: Qt.vector3d(root.lonToX(lon), kind === "AIR" ? altitude / 1000 * root.altitudeScale : 1, root.latToZ(lat))

                Model {
                    visible: kind === "AIR"
                    source: "#Cone"
                    scale: Qt.vector3d(0.15, 0.3, 0.15)
                    eulerRotation: Qt.vector3d(0, heading, 0)
                    materials: PrincipledMaterial {
                        baseColor: "#2d8cff"
                        lighting: PrincipledMaterial.FragmentLighting
                    }

                    // Altitude "stalk" connecting the cone down to the ground,
                    // so height reads clearly instead of floating ambiguously.
                    Model {
                        visible: altitude > 0
                        source: "#Cylinder"
                        scale: Qt.vector3d(0.01, altitude / 1000 * root.altitudeScale / 100, 0.01)
                        position: Qt.vector3d(0, -(altitude / 1000 * root.altitudeScale) / 2, 0)
                        materials: PrincipledMaterial {
                            baseColor: "#4488ff"
                            lighting: PrincipledMaterial.NoLighting
                        }
                    }
                }

                Model {
                    visible: kind !== "AIR"
                    source: "#Cylinder"
                    scale: Qt.vector3d(0.12, 0.02, 0.12)
                    eulerRotation: Qt.vector3d(0, heading, 0)
                    materials: PrincipledMaterial {
                        baseColor: "#2dd881"
                        lighting: PrincipledMaterial.FragmentLighting
                    }
                }

                // Selection indicator: a pulsing ring on the ground beneath
                // the marker, mirroring the 2D map's selection hook.
                Model {
                    visible: trackId === root.selectedTrackId
                    source: "#Cylinder"
                    scale: Qt.vector3d(0.25, 0.005, 0.25)
                    position: Qt.vector3d(0, kind === "AIR" ? -(altitude / 1000 * root.altitudeScale) : 0, 0)
                    materials: PrincipledMaterial {
                        baseColor: "#ffd54f"
                        lighting: PrincipledMaterial.NoLighting
                        opacity: 0.8
                    }

                    SequentialAnimation on opacity {
                        running: true
                        loops: Animation.Infinite
                        NumberAnimation { from: 1.0; to: 0.3; duration: 700 }
                        NumberAnimation { from: 0.3; to: 1.0; duration: 700 }
                    }
                }

                TapHandler {
                    onTapped: root.trackClicked(trackId, label, kind, lat, lon, heading, speed, altitude)
                }
            }
        }
    }

    // QtQuick3D has no built-in orbit-camera gesture helper (same situation as
    // QtLocation's missing MapGestureArea in Qt 6) — drag rotates the camera
    // around the scene origin, wheel moves it closer/farther.
    DragHandler {
        id: orbitHandler
        target: null
        onTranslationChanged: (delta) => {
            // Reassign the whole vector3d rather than mutating .x/.y in place —
            // QML value-type sub-property writes don't reliably notify bindings.
            const newY = camera.eulerRotation.y - delta.x * 0.3;
            const newX = Math.max(-85, Math.min(-10, camera.eulerRotation.x - delta.y * 0.3));
            camera.eulerRotation = Qt.vector3d(newX, newY, camera.eulerRotation.z);
        }
    }

    WheelHandler {
        acceptedDevices: PointerDevice.Mouse | PointerDevice.TouchPad
        onWheel: (event) => {
            const factor = event.angleDelta.y > 0 ? 0.9 : 1.1;
            const pos = camera.position;
            const dist = Math.sqrt(pos.x * pos.x + pos.y * pos.y + pos.z * pos.z);
            const newDist = Math.max(200, Math.min(3000, dist * factor));
            const scale = newDist / dist;
            camera.position = Qt.vector3d(pos.x * scale, pos.y * scale, pos.z * scale);
        }
    }
}
