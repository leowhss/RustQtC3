//! Smoke test ONLY: verifies whether cxx-qt's #[base = ...] mechanism can
//! bind to QQuick3DGeometry (a QQuick3DObject-rooted type), which has no
//! prior art anywhere in cxx-qt. If this compiles and renders a triangle via
//! a `Geometry {}` QML element, the real sphere-mesh bridge can follow the
//! same pattern. If not, a hand-written C++ subclass is the fallback — see
//! the project plan for details. Not wired into any QML file yet; this file
//! alone is the test of whether it even compiles.
//!
//! Nested C++ enums (QQuick3DGeometry::PrimitiveType, Attribute::Semantic)
//! are avoided at the cxx FFI boundary via triangle_geometry_shim.h's plain
//! free functions, to isolate the base-class-binding question from a
//! separate (solvable either way) nested-enum FFI question.

#[cxx_qt::bridge]
mod qobject {
    unsafe extern "C++" {
        include!(<QtQuick3D/QQuick3DGeometry>);
        type QQuick3DGeometry;

        include!("cxx-qt-lib/qbytearray.h");
        type QByteArray = cxx_qt_lib::QByteArray;

        include!("cxx-qt-lib/qvector3d.h");
        type QVector3D = cxx_qt_lib::QVector3D;
    }

    unsafe extern "RustQt" {
        #[qobject]
        #[base = QQuick3DGeometry]
        #[qml_element]
        type TriangleGeometry = super::TriangleGeometryRust;
    }

    // Declares that TriangleGeometry uses a custom (here: default-arguments)
    // constructor rather than cxx-qt's auto-generated one, which otherwise
    // hardcodes a `QObject* parent` base-constructor call — incompatible
    // with QQuick3DGeometry's actual `QQuick3DObject* parent` constructor.
    impl cxx_qt::Constructor<()> for TriangleGeometry {}

    unsafe extern "RustQt" {
        #[inherit]
        fn setVertexData(self: Pin<&mut TriangleGeometry>, data: &QByteArray);
        #[inherit]
        fn setStride(self: Pin<&mut TriangleGeometry>, stride: i32);
        #[inherit]
        fn setBounds(self: Pin<&mut TriangleGeometry>, min: &QVector3D, max: &QVector3D);
    }

    unsafe extern "C++" {
        include!("triangle_geometry_shim.h");

        #[rust_name = "set_primitive_type_triangles"]
        fn triangleGeometrySetPrimitiveTypeTriangles(geometry: Pin<&mut QQuick3DGeometry>);
        #[rust_name = "add_position_attribute"]
        fn triangleGeometryAddPositionAttribute(geometry: Pin<&mut QQuick3DGeometry>, offset: i32);
    }
}

use cxx_qt::Initialize;
use cxx_qt_lib::{QByteArray, QVector3D};
use std::pin::Pin;

#[derive(Default)]
pub struct TriangleGeometryRust;

impl Initialize for qobject::TriangleGeometry {
    fn initialize(self: Pin<&mut Self>) {
        let mut this = self;

        // Three vertices (x,y,z f32 each = 12 bytes/vertex), one triangle.
        let verts: [f32; 9] = [
            0.0, 1.0, 0.0,
            -1.0, -1.0, 0.0,
            1.0, -1.0, 0.0,
        ];
        let bytes: Vec<u8> = verts.iter().flat_map(|f| f.to_le_bytes()).collect();
        let qbytes = QByteArray::from(bytes.as_slice());

        this.as_mut().setVertexData(&qbytes);
        this.as_mut().setStride(12);
        qobject::set_primitive_type_triangles(this.as_mut());
        qobject::add_position_attribute(this.as_mut(), 0);
        this.as_mut().setBounds(
            &QVector3D::new(-1.0, -1.0, 0.0),
            &QVector3D::new(1.0, 1.0, 0.0),
        );
    }
}
