// Build pattern uses the installed cxx-qt-build 0.7.3 API (verified against its
// actual source in the cargo registry — newer unreleased examples on cxx-qt's
// GitHub main branch use a different, not-yet-published fluent builder).
// QtQuick/QtQml linkage comes from the `qt_qml` feature on cxx-qt-lib (see
// Cargo.toml). QtLocation/QtPositioning are QML-only plugin modules with no
// C++ headers consumed from Rust, so they aren't declared as qt_module()
// either — they must be discoverable on the QML import path at runtime (see
// client/README.md). WebEngineQuick is the one exception: it needs a C++
// initialize() call before QGuiApplication is constructed (see
// src/webengine_init.rs / main.rs), so unlike the other map-tab modules it
// does need qt_module() linkage and a real header include.
use cxx_qt_build::CxxQtBuilder;
use cxx_qt_build::QmlModule;

fn main() {
    CxxQtBuilder::new()
        .qt_module("WebEngineQuick")
        .qt_module("WebChannel")
        .qt_module("Quick3D")
        .cc_builder(|cc| {
            cc.include("include");
        })
        .file("src/webengine_init.rs")
        .qml_module(QmlModule {
            uri: "com.rustqtc3",
            rust_files: &["src/bridge.rs", "src/geometry_smoketest.rs"],
            qml_files: &[
                "qml/Main.qml",
                "qml/TrackDetailPanel.qml",
                "qml/SearchBar.qml",
                "qml/AlertPanel.qml",
                "qml/PlaneIcon.qml",
                "qml/ShipIcon.qml",
                "qml/Map3DView.qml",
                "qml/MapLibreView.qml",
            ],
            qrc_files: &["assets/maplibre_view.html"],
            ..Default::default()
        })
        .build();
}
