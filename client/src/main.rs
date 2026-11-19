mod api_types;
mod backend_client;
mod bridge;
mod geometry_smoketest;
mod stomp;
mod webengine_init;

use cxx_qt_lib::{QGuiApplication, QQmlApplicationEngine, QUrl};

fn main() {
    tracing_subscriber::fmt::init();

    // Must happen before QGuiApplication is constructed — Chromium's
    // sandbox/GPU process setup hooks in at this point.
    webengine_init::initialize();

    let mut app = QGuiApplication::new();
    let mut engine = QQmlApplicationEngine::new();

    if let Some(engine) = engine.as_mut() {
        engine.load(&QUrl::from("qrc:/qt/qml/com/rustqtc3/qml/Main.qml"));
    }

    if let Some(app) = app.as_mut() {
        app.exec();
    }
}
