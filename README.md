# RustQtC3

A demo Command, Control & Communications (C3) application that fuses live air
tracks (ADS-B, via OpenSky Network) and sea vessel tracks (AIS, via
AISStream.io) onto a real-time map.

- **`backend/`** — Spring Boot service: polls/streams the live feeds, normalizes
  both into a common `Track` model, stores to MongoDB, evaluates geofences, and
  serves a REST + STOMP WebSocket API.
- **`client/`** — Rust desktop operator client (cxx-qt + QML/QtLocation) showing
  a live map, track details, search/filtering, and geofence drawing/alerts.

## Prerequisites

- JDK 17+, Maven 3.9+
- A local MongoDB instance (`mongodb://localhost:27017` by default)
- Rust toolchain (stable) + Cargo
- Qt 6 (QtQuick, QtQml, QtPositioning, QtLocation modules) with `qmake`/Qt's
  CMake config discoverable on `PATH`/`CMAKE_PREFIX_PATH`. On Windows, the Qt
  build's toolchain must match your Rust toolchain: `win64_msvc2022_64` for
  the default `*-pc-windows-msvc` Rust target, or `win64_mingw` for a
  `*-pc-windows-gnu` Rust toolchain — mixing them fails at the final link step.
- A free [AISStream.io](https://aisstream.io) API key (sea tracks are skipped
  without one; air tracks work regardless since OpenSky's anonymous API needs
  no key)

## Running the backend

```bash
cd backend
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# edit application-local.yml and set c3.aisstream.api-key
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The default bounding box covers the Singapore Strait (dense mixed air/sea
traffic). Override `c3.opensky.lamin/lomin/lamax/lomax` in
`application-local.yml` to watch a different region.

Verify it's working:

```bash
curl http://localhost:8080/api/tracks
```

## Running the client

```bash
cd client
cargo run
```

On Windows, QtLocation's QML plugin must be discoverable at runtime. If the
map fails to load, set `QML2_IMPORT_PATH` to your Qt installation's `qml`
directory, e.g.:

```bash
export QML2_IMPORT_PATH="/c/Qt/6.x.x/msvc2022_64/qml"   # or mingw_64/qml, matching your toolchain
```

The client connects to `http://localhost:8080` / `ws://localhost:8080/ws` by
default — start the backend first.

## Features (v1)

- Live map display of air and sea tracks with type-specific symbology
- Track detail panel (callsign/MMSI, position, heading, speed, altitude)
- Track history trail (stored in MongoDB, TTL-purged after 48h by default)
- Geofencing: draw circular/polygon zones, get ENTER/EXIT alerts
- Search/filter by callsign, MMSI/ICAO24, speed, altitude, track type

## Data retention

Historical position samples (`track_history` collection) are purged
automatically via a MongoDB TTL index. Configure the window with
`c3.history.retention-hours` (default 48).

## Verified status

Both halves have been built and run end-to-end on Windows:

- **Backend**: `mvn spring-boot:run` against local MongoDB — confirmed polling
  live OpenSky data (tracks visible via `GET /api/tracks`), geofence create/list
  round-tripping over REST, and the geofence engine raising real ENTER alerts
  against live aircraft.
- **Client**: built with Qt 6.8.2 (`win64_msvc2022_64` — must match the MSVC
  Rust toolchain; a MinGW Qt build will fail to link against an MSVC Rust
  toolchain, and vice versa) via `cargo build`, then run with
  `QML2_IMPORT_PATH` pointed at that Qt's `qml/` directory. The window opens,
  connects to a running backend, and loads with no QML warnings.

If you're on a MinGW Rust toolchain instead, install Qt's `win64_mingw` build
to match — the key constraint is that the Rust toolchain and Qt build use the
same ABI/linker.
