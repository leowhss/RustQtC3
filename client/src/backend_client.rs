//! Owns the live connection to the Spring Boot backend: a tokio task holding
//! the STOMP-over-WebSocket subscription that pushes track/alert updates into
//! the Qt-side list models, plus REST helpers for history/search/geofence CRUD
//! that QML invokes on demand (via BackendClient's #[qinvokable] methods,
//! declared alongside the models in bridge.rs).

use crate::api_types::{AlertDto, GeofenceDto, TrackDto, TrackEventDto, TrackHistoryPointDto};
use crate::bridge::TrackRow;
use crate::stomp;
use futures_util::{SinkExt, StreamExt};
use tokio_tungstenite::tungstenite::Message;

const DEFAULT_BASE_URL: &str = "http://localhost:8080";
const DEFAULT_WS_URL: &str = "ws://localhost:8080/ws";

fn track_dto_to_row(dto: &TrackDto) -> TrackRow {
    TrackRow {
        id: dto.id.clone(),
        kind: dto.kind.clone(),
        label: dto.callsign_or_name.clone(),
        lat: dto.lat,
        lon: dto.lon,
        heading: dto.heading.unwrap_or(0.0),
        speed: dto.speed.unwrap_or(0.0),
        altitude: dto.altitude.unwrap_or(0.0),
    }
}

/// Fetches the current track snapshot via REST. Called once at startup to
/// seed the model before the live WebSocket feed takes over.
pub async fn fetch_initial_tracks() -> anyhow::Result<Vec<TrackRow>> {
    let url = format!("{DEFAULT_BASE_URL}/api/tracks");
    let tracks: Vec<TrackDto> = reqwest::get(&url).await?.json().await?;
    Ok(tracks.iter().map(track_dto_to_row).collect())
}

pub async fn fetch_track_history(track_id: &str) -> anyhow::Result<Vec<TrackHistoryPointDto>> {
    let url = format!("{DEFAULT_BASE_URL}/api/tracks/{track_id}/history");
    let points = reqwest::get(&url).await?.json().await?;
    Ok(points)
}

/// Re-fetches the track snapshot filtered by a free-text query (callsign/MMSI/ICAO24
/// substring match, case-insensitive — see backend's TrackController#list).
pub async fn search_tracks(query: &str) -> anyhow::Result<Vec<TrackRow>> {
    let url = format!("{DEFAULT_BASE_URL}/api/tracks");
    let tracks: Vec<TrackDto> = reqwest::Client::new()
        .get(&url)
        .query(&[("query", query)])
        .send()
        .await?
        .json()
        .await?;
    Ok(tracks.iter().map(track_dto_to_row).collect())
}

pub async fn fetch_geofences() -> anyhow::Result<Vec<GeofenceDto>> {
    let url = format!("{DEFAULT_BASE_URL}/api/geofences");
    let fences = reqwest::get(&url).await?.json().await?;
    Ok(fences)
}

pub async fn create_geofence(geofence: &GeofenceDto) -> anyhow::Result<GeofenceDto> {
    let url = format!("{DEFAULT_BASE_URL}/api/geofences");
    let client = reqwest::Client::new();
    let created = client.post(&url).json(geofence).send().await?.json().await?;
    Ok(created)
}

pub async fn fetch_alerts() -> anyhow::Result<Vec<AlertDto>> {
    let url = format!("{DEFAULT_BASE_URL}/api/alerts");
    let alerts = reqwest::get(&url).await?.json().await?;
    Ok(alerts)
}

/// Callbacks invoked on the tokio task as events arrive; the caller is
/// responsible for marshaling these onto the Qt GUI thread (e.g. via
/// `qt_thread.queue(...)`) since this module has no Qt dependency.
pub struct LiveFeedHandlers {
    pub on_track_upsert: Box<dyn Fn(TrackRow) + Send + Sync>,
    pub on_track_remove: Box<dyn Fn(String) + Send + Sync>,
    pub on_alert: Box<dyn Fn(AlertDto) + Send + Sync>,
}

/// Runs forever, reconnecting on failure, maintaining the STOMP subscription
/// to /topic/tracks and /topic/alerts. Intended to be spawned as a dedicated
/// tokio task from the client's main entry point.
pub async fn run_live_feed(handlers: LiveFeedHandlers) {
    loop {
        if let Err(err) = run_live_feed_once(&handlers).await {
            tracing::warn!("live feed connection lost: {err:#}; reconnecting in 5s");
        }
        tokio::time::sleep(std::time::Duration::from_secs(5)).await;
    }
}

async fn run_live_feed_once(handlers: &LiveFeedHandlers) -> anyhow::Result<()> {
    let (ws_stream, _) = tokio_tungstenite::connect_async(DEFAULT_WS_URL).await?;
    let (mut write, mut read) = ws_stream.split();

    write.send(Message::Text(stomp::connect_frame())).await?;
    write
        .send(Message::Text(stomp::subscribe_frame("sub-tracks", "/topic/tracks")))
        .await?;
    write
        .send(Message::Text(stomp::subscribe_frame("sub-alerts", "/topic/alerts")))
        .await?;

    while let Some(message) = read.next().await {
        let message = message?;
        let Message::Text(text) = message else {
            continue;
        };
        let Some(frame) = stomp::parse_frame(&text) else {
            continue;
        };
        if frame.command != "MESSAGE" {
            continue;
        }

        match frame.destination.as_deref() {
            Some("/topic/tracks") => handle_track_event(&frame.body, handlers),
            Some("/topic/alerts") => handle_alert_event(&frame.body, handlers),
            _ => {}
        }
    }

    Ok(())
}

fn handle_track_event(body: &str, handlers: &LiveFeedHandlers) {
    match serde_json::from_str::<TrackEventDto>(body) {
        Ok(event) if event.kind == "UPSERT" => {
            if let Some(track) = event.track {
                (handlers.on_track_upsert)(track_dto_to_row(&track));
            }
        }
        Ok(event) if event.kind == "REMOVE" => {
            (handlers.on_track_remove)(event.track_id);
        }
        Ok(_) => {}
        Err(err) => tracing::debug!("failed to parse track event: {err}"),
    }
}

fn handle_alert_event(body: &str, handlers: &LiveFeedHandlers) {
    match serde_json::from_str::<AlertDto>(body) {
        Ok(alert) => (handlers.on_alert)(alert),
        Err(err) => tracing::debug!("failed to parse alert event: {err}"),
    }
}
