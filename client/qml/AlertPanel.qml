import QtQuick
import QtQuick.Layouts
import QtQuick.Controls
import com.rustqtc3 1.0

Rectangle {
    id: root
    property alias model: alertListView.model
    color: "#262b33"
    radius: 6
    border.color: "#3a4150"

    ColumnLayout {
        anchors.fill: parent
        anchors.margins: 10
        spacing: 6

        Label {
            text: "Alerts"
            font.bold: true
            color: "white"
        }

        ListView {
            id: alertListView
            Layout.fillWidth: true
            Layout.fillHeight: true
            clip: true
            delegate: Rectangle {
                width: ListView.view.width
                height: 44
                color: index % 2 === 0 ? "#2c323c" : "transparent"
                radius: 4

                ColumnLayout {
                    anchors.fill: parent
                    anchors.margins: 6
                    spacing: 0

                    Label {
                        text: (model.alertType === "ENTER" ? "⬆ Entered " : "⬇ Exited ") + model.geofenceName
                        color: model.alertType === "ENTER" ? "#ff8a65" : "#90caf9"
                        font.pixelSize: 12
                    }
                    Label {
                        text: model.trackLabel
                        color: "#c5cbd3"
                        font.pixelSize: 11
                    }
                }
            }
        }
    }
}
