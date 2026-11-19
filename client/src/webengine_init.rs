//! Thin FFI shim for QtWebEngineQuick::initialize(), which cxx-qt-lib doesn't
//! wrap. Must be called before QGuiApplication is constructed (Chromium's
//! sandbox/GPU process setup hooks in at this point) — see main.rs.

#[cxx::bridge]
mod ffi {
    unsafe extern "C++" {
        include!("webengine_init_shim.h");

        #[rust_name = "initialize_web_engine_quick"]
        fn qtWebEngineQuickInitialize();
    }
}

pub fn initialize() {
    ffi::initialize_web_engine_quick();
}
