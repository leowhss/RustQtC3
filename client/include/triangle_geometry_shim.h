#pragma once

#include <QtQuick3D/QQuick3DGeometry>

// Thin helpers avoiding QQuick3DGeometry's nested enum types (PrimitiveType,
// Attribute::Semantic, Attribute::ComponentType) at the cxx FFI boundary —
// cxx can bind free functions taking plain ints more simply than nested C++
// enums for this one-off smoke test. Parameters are typed as the generated
// TriangleGeometry (not the QQuick3DGeometry base) because cxx's Rust-side
// Pin<&mut T> doesn't understand C++ upcasting automatically; C++ itself
// upcasts for free inside the function body since TriangleGeometry is-a
// QQuick3DGeometry.
class TriangleGeometry;

inline void triangleGeometrySetPrimitiveTypeTriangles(TriangleGeometry &geometry) {
    reinterpret_cast<QQuick3DGeometry &>(geometry).setPrimitiveType(QQuick3DGeometry::PrimitiveType::Triangles);
}

inline void triangleGeometryAddPositionAttribute(TriangleGeometry &geometry, int offset) {
    reinterpret_cast<QQuick3DGeometry &>(geometry).addAttribute(QQuick3DGeometry::Attribute::PositionSemantic, offset,
                           QQuick3DGeometry::Attribute::F32Type);
}
