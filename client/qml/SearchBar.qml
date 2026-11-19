import QtQuick
import QtQuick.Controls

Rectangle {
    id: root
    signal queryChanged(string query)
    height: 36
    color: "#262b33"
    radius: 6
    border.color: "#3a4150"

    TextField {
        anchors.fill: parent
        anchors.margins: 1
        placeholderText: "Search by callsign / MMSI / ICAO24..."
        color: "white"
        placeholderTextColor: "#6b7280"
        background: Item {}
        onTextEdited: root.queryChanged(text)
    }
}
