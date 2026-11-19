//! Wire-format types mirroring the backend's JSON DTOs (see backend's
//! com.rustqtc3.backend.model / api.dto packages). Field names match the
//! backend's Jackson defaults (camelCase getters -> camelCase JSON keys).

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct TrackDto {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String, // "AIR" | "SEA"
    #[serde(rename = "callsignOrName")]
    pub callsign_or_name: String,
    pub lat: f64,
    pub lon: f64,
    pub heading: Option<f64>,
    pub speed: Option<f64>,
    pub altitude: Option<f64>,
    #[serde(rename = "lastUpdated")]
    pub last_updated: Option<String>,
    pub source: Option<String>,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct TrackEventDto {
    pub kind: String, // "UPSERT" | "REMOVE"
    pub track: Option<TrackDto>,
    #[serde(rename = "trackId")]
    pub track_id: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct TrackHistoryPointDto {
    #[serde(rename = "trackId")]
    pub track_id: String,
    pub lat: f64,
    pub lon: f64,
    pub timestamp: String,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GeoPointDto {
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct GeofenceDto {
    pub id: Option<String>,
    pub name: String,
    pub shape: String, // "CIRCLE" | "POLYGON"
    pub center: Option<GeoPointDto>,
    #[serde(rename = "radiusMeters")]
    pub radius_meters: Option<f64>,
    pub polygon: Option<Vec<GeoPointDto>>,
    #[serde(rename = "appliesTo")]
    pub applies_to: Option<String>, // "AIR" | "SEA" | null
}

#[derive(Debug, Clone, Deserialize, Serialize)]
pub struct AlertDto {
    pub id: String,
    #[serde(rename = "trackId")]
    pub track_id: String,
    #[serde(rename = "trackLabel")]
    pub track_label: Option<String>,
    #[serde(rename = "geofenceId")]
    pub geofence_id: String,
    #[serde(rename = "geofenceName")]
    pub geofence_name: Option<String>,
    #[serde(rename = "type")]
    pub kind: String, // "ENTER" | "EXIT"
    pub timestamp: String,
    pub acknowledged: bool,
}
