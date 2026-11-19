import QtQuick
import QtQuick.Layouts
import QtQuick.Controls

Rectangle {
    id: root
    property var track: null
    color: "#262b33"
    radius: 6
    border.color: "#3a4150"

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 10
        spacing: 4

        Label {
            text: "Track Detail"
            font.bold: true
            color: "white"
        }

        Label {
            visible: root.track === null
            text: "Select a track on the map"
            color: "#8a93a3"
        }

        GridLayout {
            visible: root.track !== null
            columns: 2
            Layout.fillWidth: true
            rowSpacing: 2

            Label { text: "ID"; color: "#8a93a3" }
            Label { text: root.track ? root.track.trackId : ""; color: "white" }

            Label { text: "Name"; color: "#8a93a3" }
            Label { text: root.track ? root.track.label : ""; color: "white" }

            Label { text: "Type"; color: "#8a93a3" }
            Label { text: root.track ? root.track.kind : ""; color: "white" }

            Label { text: "Lat/Lon"; color: "#8a93a3" }
            Label {
                text: root.track ? root.track.lat.toFixed(4) + ", " + root.track.lon.toFixed(4) : ""
                color: "white"
            }

            Label { text: "Heading"; color: "#8a93a3" }
            Label { text: root.track ? root.track.heading.toFixed(0) + "°" : ""; color: "white" }

            Label { text: "Speed"; color: "#8a93a3" }
            Label { text: root.track ? root.track.speed.toFixed(1) : ""; color: "white" }

            Label {
                visible: root.track && root.track.kind === "AIR"
                text: "Altitude"; color: "#8a93a3"
            }
            Label {
                visible: root.track && root.track.kind === "AIR"
                text: root.track ? root.track.altitude.toFixed(0) + " m" : ""
                color: "white"
            }
        }
    }
}
