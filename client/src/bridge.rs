//! Single cxx-qt bridge containing all four QObjects exposed to QML:
//! TrackListModel, GeofenceListModel, AlertListModel (QAbstractListModel
//! subclasses, pattern verified against cxx-qt's own
//! examples/todo_app/src/todo_list.rs) and BackendClient, which owns the
//! tokio runtime / REST+STOMP networking.
//!
//! All four live in one bridge module because cxx-qt's nested-QObject pattern
//! (examples/qml_features/rust/src/nested_qobjects.rs) only demonstrates one
//! Rust-defined QObject holding a `*mut OtherQObject` qproperty when both are
//! declared in the SAME #[cxx_qt::bridge] module; referencing a QObject type
//! declared in a different bridge/crate was not something we could verify, so
//! we avoid relying on it. BackendClient holds `*mut TrackListModel` etc. as
//! qproperties, set once from QML's Component.onCompleted, and converts them
//! to `Pin<&mut T>` via `Pin::new_unchecked` to call their methods — exactly
//! as nested_qobjects.rs does for `OuterObject`/`InnerObject`.
//!
//! Cross-thread updates from the tokio task are marshaled onto the Qt GUI
//! thread via `qt_thread().queue(...)` (cxx_qt::Threading on BackendClient);
//! the model pointers are only ever dereferenced from inside that queued
//! closure, i.e. always on the GUI thread.

use crate::api_types::GeofenceDto;
use crate::backend_client::{self, LiveFeedHandlers};

#[cxx_qt::bridge]
pub mod qobject {
    unsafe extern "C++" {
        include!(<QAbstractListModel>);
        type QAbstractListModel;

        include!("cxx-qt-lib/qmodelindex.h");
        type QModelIndex = cxx_qt_lib::QModelIndex;

        include!("cxx-qt-lib/qvariant.h");
        type QVariant = cxx_qt_lib::QVariant;

        include!("cxx-qt-lib/qstring.h");
        type QString = cxx_qt_lib::QString;

        include!("cxx-qt-lib/qhash.h");
        type QHash_i32_QByteArray = cxx_qt_lib::QHash<cxx_qt_lib::QHashPair_i32_QByteArray>;

        include!("cxx-qt-lib/qlist.h");
        type QList_QVariant = cxx_qt_lib::QList<QVariant>;
    }

    // ---------------------------------------------------------------
    // TrackListModel
    // ---------------------------------------------------------------

    #[qenum(TrackListModel)]
    enum TrackRoles {
        TrackId,
        Kind,
        Label,
        Lat,
        Lon,
        Heading,
        Speed,
        Altitude,
    }

    unsafe extern "RustQt" {
        #[qobject]
        #[qml_element]
        #[base = QAbstractListModel]
        type TrackListModel = super::TrackListModelRust;

        #[cxx_override]
        #[rust_name = "track_row_count"]
        fn rowCount(self: &TrackListModel, parent: &QModelIndex) -> i32;

        #[cxx_override]
        #[rust_name = "track_data"]
        fn data(self: &TrackListModel, index: &QModelIndex, role: i32) -> QVariant;

        #[cxx_override]
        #[rust_name = "track_role_names"]
        fn roleNames(self: &TrackListModel) -> QHash_i32_QByteArray;

        #[inherit]
        #[rust_name = "track_index"]
        fn index(self: &TrackListModel, row: i32, column: i32, parent: &QModelIndex) -> QModelIndex;

        #[inherit]
        #[rust_name = "track_begin_insert_rows"]
        unsafe fn beginInsertRows(self: Pin<&mut TrackListModel>, parent: &QModelIndex, first: i32, last: i32);

        #[inherit]
        #[rust_name = "track_end_insert_rows"]
        unsafe fn endInsertRows(self: Pin<&mut TrackListModel>);

        #[inherit]
        #[rust_name = "track_begin_remove_rows"]
        unsafe fn beginRemoveRows(self: Pin<&mut TrackListModel>, parent: &QModelIndex, first: i32, last: i32);

        #[inherit]
        #[rust_name = "track_end_remove_rows"]
        unsafe fn endRemoveRows(self: Pin<&mut TrackListModel>);
    }

    // ---------------------------------------------------------------
    // GeofenceListModel
    // ---------------------------------------------------------------

    #[qenum(GeofenceListModel)]
    enum GeofenceRoles {
        GeofenceId,
        Name,
        Shape,
        CenterLat,
        CenterLon,
        RadiusMeters,
        PolygonJson,
    }

    unsafe extern "RustQt" {
        #[qobject]
        #[qml_element]
        #[base = QAbstractListModel]
        type GeofenceListModel = super::GeofenceListModelRust;

        #[cxx_override]
        #[rust_name = "geofence_row_count"]
        fn rowCount(self: &GeofenceListModel, parent: &QModelIndex) -> i32;

        #[cxx_override]
        #[rust_name = "geofence_data"]
        fn data(self: &GeofenceListModel, index: &QModelIndex, role: i32) -> QVariant;

        #[cxx_override]
        #[rust_name = "geofence_role_names"]
        fn roleNames(self: &GeofenceListModel) -> QHash_i32_QByteArray;

        #[inherit]
        #[rust_name = "geofence_begin_reset_model"]
        fn beginResetModel(self: Pin<&mut GeofenceListModel>);

        #[inherit]
        #[rust_name = "geofence_end_reset_model"]
        fn endResetModel(self: Pin<&mut GeofenceListModel>);
    }

    // ---------------------------------------------------------------
    // AlertListModel
    // ---------------------------------------------------------------

    #[qenum(AlertListModel)]
    enum AlertRoles {
        TrackLabel,
        GeofenceName,
        AlertType,
        Timestamp,
    }

    unsafe extern "RustQt" {
        #[qobject]
        #[qml_element]
        #[base = QAbstractListModel]
        type AlertListModel = super::AlertListModelRust;

        #[cxx_override]
        #[rust_name = "alert_row_count"]
        fn rowCount(self: &AlertListModel, parent: &QModelIndex) -> i32;

        #[cxx_override]
        #[rust_name = "alert_data"]
        fn data(self: &AlertListModel, index: &QModelIndex, role: i32) -> QVariant;

        #[cxx_override]
        #[rust_name = "alert_role_names"]
        fn roleNames(self: &AlertListModel) -> QHash_i32_QByteArray;

        #[inherit]
        #[rust_name = "alert_begin_insert_rows"]
        unsafe fn beginInsertRows(self: Pin<&mut AlertListModel>, parent: &QModelIndex, first: i32, last: i32);

        #[inherit]
        #[rust_name = "alert_end_insert_rows"]
        unsafe fn endInsertRows(self: Pin<&mut AlertListModel>);

        #[inherit]
        #[rust_name = "alert_begin_remove_rows"]
        unsafe fn beginRemoveRows(self: Pin<&mut AlertListModel>, parent: &QModelIndex, first: i32, last: i32);

        #[inherit]
        #[rust_name = "alert_end_remove_rows"]
        unsafe fn endRemoveRows(self: Pin<&mut AlertListModel>);
    }

    // ---------------------------------------------------------------
    // BackendClient
    // ---------------------------------------------------------------

    unsafe extern "RustQt" {
        #[qobject]
        #[qml_element]
        #[qproperty(*mut TrackListModel, track_model, cxx_name = "trackModel")]
        #[qproperty(*mut GeofenceListModel, geofence_model, cxx_name = "geofenceModel")]
        #[qproperty(*mut AlertListModel, alert_model, cxx_name = "alertModel")]
        type BackendClient = super::BackendClientRust;

        /// Fetches the current track snapshot over REST and seeds TrackListModel.
        #[qinvokable]
        #[cxx_name = "loadInitialTracks"]
        fn load_initial_tracks(self: Pin<&mut BackendClient>);

        /// Fetches current geofences over REST and seeds GeofenceListModel.
        #[qinvokable]
        #[cxx_name = "loadGeofences"]
        fn load_geofences(self: Pin<&mut BackendClient>);

        /// Opens the STOMP WebSocket and starts pushing live updates into the models.
        #[qinvokable]
        #[cxx_name = "startLiveFeed"]
        fn start_live_feed(self: Pin<&mut BackendClient>);

        /// Re-applies the current REST search filter (used by the client-side
        /// search box; the full track list is re-fetched with a query param).
        #[qinvokable]
        fn search(self: Pin<&mut BackendClient>, query: &QString);

        /// Creates a polygon geofence from map-clicked points and POSTs it to the
        /// backend. `flat_coords` is a flat [lat1, lon1, lat2, lon2, ...] list of
        /// f64, built in QML from the clicked points.
        #[qinvokable]
        #[cxx_name = "createPolygonGeofence"]
        fn create_polygon_geofence(self: Pin<&mut BackendClient>, name: &QString, flat_coords: &QList_QVariant);
    }

    impl cxx_qt::Threading for BackendClient {}
}

use cxx_qt::{CxxQtType, Threading};
use cxx_qt_lib::{QModelIndex, QString, QVariant};
use qobject::{AlertRoles, GeofenceRoles, QHash_i32_QByteArray, QList_QVariant, TrackRoles};
use std::pin::Pin;

// =====================================================================
// TrackListModel
// =====================================================================

#[derive(Clone, Debug, PartialEq)]
pub struct TrackRow {
    pub id: String,
    pub kind: String, // "AIR" or "SEA"
    pub label: String,
    pub lat: f64,
    pub lon: f64,
    pub heading: f64,
    pub speed: f64,
    pub altitude: f64,
}

#[derive(Default)]
pub struct TrackListModelRust {
    rows: Vec<TrackRow>,
}

impl qobject::TrackListModel {
    fn track_row_count(&self, _parent: &QModelIndex) -> i32 {
        self.rows.len() as i32
    }

    fn track_data(&self, index: &QModelIndex, role: i32) -> QVariant {
        let role = TrackRoles { repr: role };
        let Some(track) = self.rows.get(index.row() as usize) else {
            return QVariant::default();
        };

        match role {
            TrackRoles::TrackId => QVariant::from(&QString::from(track.id.as_str())),
            TrackRoles::Kind => QVariant::from(&QString::from(track.kind.as_str())),
            TrackRoles::Label => QVariant::from(&QString::from(track.label.as_str())),
            TrackRoles::Lat => QVariant::from(&track.lat),
            TrackRoles::Lon => QVariant::from(&track.lon),
            TrackRoles::Heading => QVariant::from(&track.heading),
            TrackRoles::Speed => QVariant::from(&track.speed),
            TrackRoles::Altitude => QVariant::from(&track.altitude),
            _ => QVariant::default(),
        }
    }

    fn track_role_names(&self) -> QHash_i32_QByteArray {
        let mut hash = QHash_i32_QByteArray::default();
        hash.insert(TrackRoles::TrackId.repr, "trackId".into());
        hash.insert(TrackRoles::Kind.repr, "kind".into());
        hash.insert(TrackRoles::Label.repr, "label".into());
        hash.insert(TrackRoles::Lat.repr, "lat".into());
        hash.insert(TrackRoles::Lon.repr, "lon".into());
        hash.insert(TrackRoles::Heading.repr, "heading".into());
        hash.insert(TrackRoles::Speed.repr, "speed".into());
        hash.insert(TrackRoles::Altitude.repr, "altitude".into());
        hash
    }

    /// Insert or update a track by id. In-place updates don't emit dataChanged
    /// (would require inheriting that signal too); QML delegates re-evaluate
    /// often enough for a live map that this is an acceptable simplification.
    pub fn upsert_track(mut self: Pin<&mut Self>, row: TrackRow) {
        let existing = self.rows.iter().position(|t| t.id == row.id);
        match existing {
            Some(idx) => {
                self.as_mut().rust_mut().rows[idx] = row;
            }
            None => {
                let new_row = self.rows.len() as i32;
                unsafe {
                    self.as_mut()
                        .track_begin_insert_rows(&QModelIndex::default(), new_row, new_row);
                    self.as_mut().rust_mut().rows.push(row);
                    self.as_mut().track_end_insert_rows();
                }
            }
        }
    }

    /// Remove a track by id (e.g. when the backend reports it as stale/gone).
    pub fn remove_track(mut self: Pin<&mut Self>, id: &str) {
        if let Some(idx) = self.rows.iter().position(|t| t.id == id) {
            unsafe {
                self.as_mut()
                    .track_begin_remove_rows(&QModelIndex::default(), idx as i32, idx as i32);
                self.as_mut().rust_mut().rows.remove(idx);
                self.as_mut().track_end_remove_rows();
            }
        }
    }
}

// =====================================================================
// GeofenceListModel
// =====================================================================

/// `polygon_json` carries a JSON-encoded `[[lat,lon],...]` array for polygon
/// shapes (QML parses it with JSON.parse) rather than exposing a nested list
/// role, which keeps the list-model role set flat and simple.
#[derive(Clone, Debug, PartialEq)]
pub struct GeofenceRow {
    pub id: String,
    pub name: String,
    pub shape: String, // "CIRCLE" or "POLYGON"
    pub center_lat: f64,
    pub center_lon: f64,
    pub radius_meters: f64,
    pub polygon_json: String,
}

#[derive(Default)]
pub struct GeofenceListModelRust {
    rows: Vec<GeofenceRow>,
}

impl qobject::GeofenceListModel {
    fn geofence_row_count(&self, _parent: &QModelIndex) -> i32 {
        self.rows.len() as i32
    }

    fn geofence_data(&self, index: &QModelIndex, role: i32) -> QVariant {
        let role = GeofenceRoles { repr: role };
        let Some(fence) = self.rows.get(index.row() as usize) else {
            return QVariant::default();
        };

        match role {
            GeofenceRoles::GeofenceId => QVariant::from(&QString::from(fence.id.as_str())),
            GeofenceRoles::Name => QVariant::from(&QString::from(fence.name.as_str())),
            GeofenceRoles::Shape => QVariant::from(&QString::from(fence.shape.as_str())),
            GeofenceRoles::CenterLat => QVariant::from(&fence.center_lat),
            GeofenceRoles::CenterLon => QVariant::from(&fence.center_lon),
            GeofenceRoles::RadiusMeters => QVariant::from(&fence.radius_meters),
            GeofenceRoles::PolygonJson => QVariant::from(&QString::from(fence.polygon_json.as_str())),
            _ => QVariant::default(),
        }
    }

    fn geofence_role_names(&self) -> QHash_i32_QByteArray {
        let mut hash = QHash_i32_QByteArray::default();
        hash.insert(GeofenceRoles::GeofenceId.repr, "geofenceId".into());
        hash.insert(GeofenceRoles::Name.repr, "name".into());
        hash.insert(GeofenceRoles::Shape.repr, "shape".into());
        hash.insert(GeofenceRoles::CenterLat.repr, "centerLat".into());
        hash.insert(GeofenceRoles::CenterLon.repr, "centerLon".into());
        hash.insert(GeofenceRoles::RadiusMeters.repr, "radiusMeters".into());
        hash.insert(GeofenceRoles::PolygonJson.repr, "polygonJson".into());
        hash
    }

    /// Replace the full set of geofences (called after a REST fetch).
    pub fn set_geofences(mut self: Pin<&mut Self>, rows: Vec<GeofenceRow>) {
        self.as_mut().geofence_begin_reset_model();
        self.as_mut().rust_mut().rows = rows;
        self.as_mut().geofence_end_reset_model();
    }
}

// =====================================================================
// AlertListModel
// =====================================================================

const MAX_ALERTS: usize = 200;

#[derive(Clone, Debug, PartialEq)]
pub struct AlertRow {
    pub track_label: String,
    pub geofence_name: String,
    pub alert_type: String, // "ENTER" or "EXIT"
    pub timestamp: String,
}

#[derive(Default)]
pub struct AlertListModelRust {
    rows: Vec<AlertRow>,
}

impl qobject::AlertListModel {
    fn alert_row_count(&self, _parent: &QModelIndex) -> i32 {
        self.rows.len() as i32
    }

    fn alert_data(&self, index: &QModelIndex, role: i32) -> QVariant {
        let role = AlertRoles { repr: role };
        let Some(alert) = self.rows.get(index.row() as usize) else {
            return QVariant::default();
        };

        match role {
            AlertRoles::TrackLabel => QVariant::from(&QString::from(alert.track_label.as_str())),
            AlertRoles::GeofenceName => QVariant::from(&QString::from(alert.geofence_name.as_str())),
            AlertRoles::AlertType => QVariant::from(&QString::from(alert.alert_type.as_str())),
            AlertRoles::Timestamp => QVariant::from(&QString::from(alert.timestamp.as_str())),
            _ => QVariant::default(),
        }
    }

    fn alert_role_names(&self) -> QHash_i32_QByteArray {
        let mut hash = QHash_i32_QByteArray::default();
        hash.insert(AlertRoles::TrackLabel.repr, "trackLabel".into());
        hash.insert(AlertRoles::GeofenceName.repr, "geofenceName".into());
        hash.insert(AlertRoles::AlertType.repr, "alertType".into());
        hash.insert(AlertRoles::Timestamp.repr, "timestamp".into());
        hash
    }

    /// Prepends a new alert (most recent first), trimming to MAX_ALERTS.
    pub fn push_front(mut self: Pin<&mut Self>, row: AlertRow) {
        unsafe {
            self.as_mut()
                .alert_begin_insert_rows(&QModelIndex::default(), 0, 0);
            self.as_mut().rust_mut().rows.insert(0, row);
            self.as_mut().alert_end_insert_rows();
        }

        if self.rows.len() > MAX_ALERTS {
            let last = self.rows.len() as i32 - 1;
            unsafe {
                self.as_mut()
                    .alert_begin_remove_rows(&QModelIndex::default(), last, last);
                self.as_mut().rust_mut().rows.truncate(MAX_ALERTS);
                self.as_mut().alert_end_remove_rows();
            }
        }
    }
}

// =====================================================================
// BackendClient
// =====================================================================

pub struct BackendClientRust {
    track_model: *mut qobject::TrackListModel,
    geofence_model: *mut qobject::GeofenceListModel,
    alert_model: *mut qobject::AlertListModel,
    runtime: Option<tokio::runtime::Runtime>,
}

impl Default for BackendClientRust {
    fn default() -> Self {
        Self {
            track_model: std::ptr::null_mut(),
            geofence_model: std::ptr::null_mut(),
            alert_model: std::ptr::null_mut(),
            runtime: None,
        }
    }
}

impl qobject::BackendClient {
    pub fn load_initial_tracks(self: Pin<&mut Self>) {
        let qt_thread = self.qt_thread();
        self.ensure_runtime().spawn(async move {
            match backend_client::fetch_initial_tracks().await {
                Ok(rows) => {
                    for row in rows {
                        let _ = qt_thread.queue(move |this| {
                            with_track_model(this, |model| model.upsert_track(row.clone()));
                        });
                    }
                }
                Err(err) => tracing::warn!("failed to load initial tracks: {err:#}"),
            }
        });
    }

    pub fn load_geofences(self: Pin<&mut Self>) {
        let qt_thread = self.qt_thread();
        self.ensure_runtime().spawn(async move {
            match backend_client::fetch_geofences().await {
                Ok(fences) => {
                    let rows: Vec<GeofenceRow> = fences.iter().map(dto_to_geofence_row).collect();
                    let _ = qt_thread.queue(move |this| {
                        with_geofence_model(this, |model| model.set_geofences(rows));
                    });
                }
                Err(err) => tracing::warn!("failed to load geofences: {err:#}"),
            }
        });
    }

    pub fn start_live_feed(self: Pin<&mut Self>) {
        let qt_thread_upsert = self.qt_thread();
        let qt_thread_remove = self.qt_thread();
        let qt_thread_alert = self.qt_thread();

        self.ensure_runtime().spawn(async move {
            let handlers = LiveFeedHandlers {
                on_track_upsert: Box::new(move |row| {
                    let _ = qt_thread_upsert.queue(move |this| {
                        with_track_model(this, |model| model.upsert_track(row.clone()));
                    });
                }),
                on_track_remove: Box::new(move |id| {
                    let _ = qt_thread_remove.queue(move |this| {
                        with_track_model(this, |model| model.remove_track(&id));
                    });
                }),
                on_alert: Box::new(move |alert| {
                    let row = AlertRow {
                        track_label: alert.track_label.unwrap_or(alert.track_id),
                        geofence_name: alert.geofence_name.unwrap_or(alert.geofence_id),
                        alert_type: alert.kind,
                        timestamp: alert.timestamp,
                    };
                    let _ = qt_thread_alert.queue(move |this| {
                        with_alert_model(this, |model| model.push_front(row.clone()));
                    });
                }),
            };
            backend_client::run_live_feed(handlers).await;
        });
    }

    pub fn search(self: Pin<&mut Self>, query: &QString) {
        let qt_thread = self.qt_thread();
        let query = String::from(query);
        self.ensure_runtime().spawn(async move {
            match backend_client::search_tracks(&query).await {
                Ok(rows) => {
                    let _ = qt_thread.queue(move |this| {
                        let ptr = *this.track_model();
                        if let Some(model) = unsafe { ptr.as_mut() } {
                            for row in rows {
                                unsafe { Pin::new_unchecked(&mut *model) }.upsert_track(row);
                            }
                        }
                    });
                }
                Err(err) => tracing::warn!("search failed: {err:#}"),
            }
        });
    }

    pub fn create_polygon_geofence(self: Pin<&mut Self>, name: &QString, flat_coords: &QList_QVariant) {
        let name = String::from(name);
        let values: Vec<f64> = flat_coords.iter().filter_map(|v| v.value::<f64>()).collect();
        let polygon: Vec<crate::api_types::GeoPointDto> = values
            .chunks_exact(2)
            .map(|pair| crate::api_types::GeoPointDto { lat: pair[0], lon: pair[1] })
            .collect();

        let dto = GeofenceDto {
            id: None,
            name,
            shape: "POLYGON".to_string(),
            center: None,
            radius_meters: None,
            polygon: Some(polygon),
            applies_to: None,
        };

        self.ensure_runtime().spawn(async move {
            if let Err(err) = backend_client::create_geofence(&dto).await {
                tracing::warn!("failed to create geofence: {err:#}");
            }
        });
    }
}

impl qobject::BackendClient {
    fn ensure_runtime(self: Pin<&mut Self>) -> tokio::runtime::Handle {
        let mut this = self.rust_mut();
        if this.runtime.is_none() {
            this.runtime = Some(
                tokio::runtime::Builder::new_multi_thread()
                    .enable_all()
                    .worker_threads(2)
                    .build()
                    .expect("failed to build tokio runtime"),
            );
        }
        this.runtime.as_ref().unwrap().handle().clone()
    }
}

/// Safety: only ever called from inside a `qt_thread().queue(...)` closure,
/// which runs on the Qt GUI thread — the same thread QML uses when it set the
/// `trackModel` qproperty, so the pointer is valid and exclusively accessed here.
fn with_track_model(this: Pin<&mut qobject::BackendClient>, f: impl FnOnce(Pin<&mut qobject::TrackListModel>)) {
    let ptr = *this.track_model();
    if let Some(model) = unsafe { ptr.as_mut() } {
        f(unsafe { Pin::new_unchecked(model) });
    }
}

fn with_geofence_model(
    this: Pin<&mut qobject::BackendClient>,
    f: impl FnOnce(Pin<&mut qobject::GeofenceListModel>),
) {
    let ptr = *this.geofence_model();
    if let Some(model) = unsafe { ptr.as_mut() } {
        f(unsafe { Pin::new_unchecked(model) });
    }
}

fn with_alert_model(this: Pin<&mut qobject::BackendClient>, f: impl FnOnce(Pin<&mut qobject::AlertListModel>)) {
    let ptr = *this.alert_model();
    if let Some(model) = unsafe { ptr.as_mut() } {
        f(unsafe { Pin::new_unchecked(model) });
    }
}

fn dto_to_geofence_row(dto: &GeofenceDto) -> GeofenceRow {
    let (center_lat, center_lon, radius) = match &dto.center {
        Some(c) => (c.lat, c.lon, dto.radius_meters.unwrap_or(0.0)),
        None => (0.0, 0.0, 0.0),
    };
    let polygon_json = dto
        .polygon
        .as_ref()
        .map(|points| {
            let coords: Vec<[f64; 2]> = points.iter().map(|p| [p.lat, p.lon]).collect();
            serde_json::to_string(&coords).unwrap_or_default()
        })
        .unwrap_or_default();

    GeofenceRow {
        id: dto.id.clone().unwrap_or_default(),
        name: dto.name.clone(),
        shape: dto.shape.clone(),
        center_lat,
        center_lon,
        radius_meters: radius,
        polygon_json,
    }
}
