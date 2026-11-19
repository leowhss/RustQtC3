import QtQuick
import QtWebEngine

// Third map tab: a tilted (pitched camera) raster map via embedded MapLibre
// GL JS, loaded from its public CDN with plain OpenStreetMap raster tiles —
// no API key needed, unlike Mapbox GL (MapLibre's proprietary cousin), which
// requires one beyond a free tier. QtWebEngine is used instead of QtWebView
// because the latter's own docs warn against embedding it among sibling QML
// items in a tabbed/stacked layout, which is exactly this use case.
Item {
    id: root

    required property var trackModel
    property string selectedTrackId: ""

    signal trackSelected(string trackId, string label, string kind, real lat, real lon, real heading, real speed, real altitude)

    WebEngineView {
        id: webView
        anchors.fill: parent
        url: Qt.resolvedUrl("../assets/maplibre_view.html")

        Component.onCompleted: console.log("[maplibre-debug] resolved url:", url)

        onLoadingChanged: (loadRequest) => {
            console.log("[maplibre-debug] status:", loadRequest.status, "errorCode:", loadRequest.errorCode, "errorDomain:", loadRequest.errorDomain, "errorString:", loadRequest.errorString);
        }

        onJavaScriptConsoleMessage: (level, message, lineNumber, sourceId) => {
            console.log("[maplibre]", message, "(" + sourceId + ":" + lineNumber + ")");
        }
    }

    // TrackListModel's data() doesn't emit dataChanged on in-place updates
    // (see bridge.rs), so a signal-driven bridge would miss most position
    // updates. Reusing this codebase's established "read the model through a
    // delegate" pattern (same as MapItemView/Repeater3D elsewhere) instead of
    // introducing a different model-access approach.
    Repeater {
        id: trackReader
        model: root.trackModel
        delegate: Item {
            required property string trackId
            required property string kind
            required property string label
            required property real lat
            required property real lon
            required property real heading
            required property real speed
            required property real altitude
        }
    }

    function collectTrackSnapshot() {
        const rows = [];
        for (let i = 0; i < trackReader.count; i++) {
            const item = trackReader.itemAt(i);
            if (item) {
                rows.push({
                    trackId: item.trackId,
                    kind: item.kind,
                    label: item.label,
                    lat: item.lat,
                    lon: item.lon,
                    heading: item.heading,
                    speed: item.speed,
                    altitude: item.altitude,
                });
            }
        }
        return rows;
    }

    property string lastSeenClickedId: ""

    Timer {
        interval: 1500
        running: true
        repeat: true
        onTriggered: {
            // JSON.stringify's output is valid JS literal syntax, so it can be
            // spliced directly as an argument expression — no need to wrap it
            // in an outer string literal (which would require escaping quotes
            // and backslashes twice over).
            const snapshotJson = JSON.stringify(root.collectTrackSnapshot());
            const selectedJson = JSON.stringify(root.selectedTrackId);
            webView.runJavaScript("window.setSelectedTrackId(" + selectedJson + "); window.updateTracks(" + snapshotJson + ");");

            webView.runJavaScript("window.lastClickedTrackId || ''", (result) => {
                if (result && result !== root.lastSeenClickedId) {
                    root.lastSeenClickedId = result;
                    const match = root.collectTrackSnapshot().find((t) => t.trackId === result);
                    if (match) {
                        root.trackSelected(match.trackId, match.label, match.kind, match.lat, match.lon, match.heading, match.speed, match.altitude);
                    }
                }
            });
        }
    }
}
