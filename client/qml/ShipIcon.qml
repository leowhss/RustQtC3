import QtQuick
import QtQuick.Shapes

// Simple topdown vessel silhouette: pointed bow at top (0 degrees = north),
// blunt stern at bottom, rotated by the item's own `rotation` property to
// match track heading (course over ground).
Shape {
    id: root
    property color fillColor: "#2dd881"
    preferredRendererType: Shape.CurveRenderer

    ShapePath {
        strokeWidth: 1
        strokeColor: "white"
        fillColor: root.fillColor

        // Coordinates drawn in a 16x24 box, bow at top (8,0), stern at bottom.
        startX: 8; startY: 0
        PathLine { x: 14; y: 9 }
        PathLine { x: 14; y: 20 }
        PathLine { x: 12; y: 24 }
        PathLine { x: 4; y: 24 }
        PathLine { x: 2; y: 20 }
        PathLine { x: 2; y: 9 }
        PathLine { x: 8; y: 0 }
    }
}
