import QtQuick
import QtQuick.Shapes

// Simple topdown plane silhouette: nose pointing up (0 degrees = north),
// rotated by the item's own `rotation` property to match track heading.
Shape {
    id: root
    property color fillColor: "#2d8cff"
    preferredRendererType: Shape.CurveRenderer

    ShapePath {
        strokeWidth: 1
        strokeColor: "white"
        fillColor: root.fillColor

        // Coordinates drawn in a 24x24 box, nose at top (12,0).
        startX: 12; startY: 0
        PathLine { x: 15; y: 8 }
        PathLine { x: 24; y: 15 }
        PathLine { x: 24; y: 18 }
        PathLine { x: 15; y: 15 }
        PathLine { x: 14; y: 22 }
        PathLine { x: 18; y: 24 }
        PathLine { x: 18; y: 24 }
        PathLine { x: 12; y: 23 }
        PathLine { x: 6; y: 24 }
        PathLine { x: 6; y: 24 }
        PathLine { x: 10; y: 22 }
        PathLine { x: 9; y: 15 }
        PathLine { x: 0; y: 18 }
        PathLine { x: 0; y: 15 }
        PathLine { x: 9; y: 8 }
        PathLine { x: 12; y: 0 }
    }
}
