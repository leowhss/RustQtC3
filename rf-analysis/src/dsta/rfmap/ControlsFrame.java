package dsta.rfmap;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.TitledBorder;

/**
 * Separate top-level window holding the TX/RX point fields, propagation parameters, and results -
 * kept apart from {@link MapApp}'s WorldWindowGLJPanel window (see {@link MapApp} class comment
 * for why).
 *
 * <p>Numeric parameters use plain {@link JTextField}s rather than {@link javax.swing.JSpinner}:
 * on this environment JSpinner's editor does not paint its value/arrows at all (reproduced even
 * in a standalone Swing app with no WorldWind/JOGL involved), while JTextField renders correctly.
 */
public class ControlsFrame extends JFrame {

    private final MapApp mapApp;

    private final JTextField txLonField = new JTextField(8);
    private final JTextField txLatField = new JTextField(8);
    private final JTextField rxLonField = new JTextField(8);
    private final JTextField rxLatField = new JTextField(8);
    private final JTextField txHeightField = new JTextField("3.0", 6);
    private final JTextField rxHeightField = new JTextField("3.0", 6);
    private final JTextField freqField = new JTextField("800", 6);
    private final JTextField percentField = new JTextField("1", 6);
    private final JTextArea resultArea = new JTextArea(18, 30);
    private final JLabel statusLabel = new JLabel("<html>Click on the terrain<br>to set TX, click again for RX.</html>");

    public ControlsFrame(MapApp mapApp) {
        super("RF Analysis - Points & Results");
        this.mapApp = mapApp;
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        resultArea.setEditable(false);
        resultArea.setLineWrap(true);
        resultArea.setWrapStyleWord(true);

        setLayout(new BorderLayout());
        add(buildForm(), BorderLayout.NORTH);
        add(buildResultsPanel(), BorderLayout.CENTER);

        setSize(420, 760);
    }

    private JPanel buildForm() {
        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(BorderFactory.createTitledBorder("Points & Parameters"));
        GridBagConstraints gbcLabel = new GridBagConstraints();
        gbcLabel.gridx = 0;
        gbcLabel.anchor = GridBagConstraints.WEST;
        gbcLabel.insets = new Insets(3, 6, 3, 8);
        GridBagConstraints gbcField = new GridBagConstraints();
        gbcField.gridx = 1;
        gbcField.weightx = 1.0;
        gbcField.fill = GridBagConstraints.HORIZONTAL;
        gbcField.insets = new Insets(3, 0, 3, 6);

        Object[][] rows = {
                {"TX lon:", txLonField}, {"TX lat:", txLatField},
                {"RX lon:", rxLonField}, {"RX lat:", rxLatField},
                {"TX height (m):", txHeightField}, {"RX height (m):", rxHeightField},
                {"Freq (MHz):", freqField}, {"% time (1-50):", percentField}
        };
        for (int row = 0; row < rows.length; row++) {
            gbcLabel.gridy = row;
            gbcField.gridy = row;
            form.add(new JLabel((String) rows[row][0]), gbcLabel);
            form.add((Component) rows[row][1], gbcField);
        }

        JButton computeBtn = new JButton("Compute from fields");
        computeBtn.addActionListener(e -> {
            try {
                double txLon = Double.parseDouble(txLonField.getText().trim());
                double txLat = Double.parseDouble(txLatField.getText().trim());
                double rxLon = Double.parseDouble(rxLonField.getText().trim());
                double rxLat = Double.parseDouble(rxLatField.getText().trim());
                mapApp.setPointsFromFields(txLon, txLat, rxLon, rxLat);
            } catch (NumberFormatException ex) {
                setStatus("Enter valid TX and RX lon/lat first.");
            }
        });

        JButton clearBtn = new JButton("Clear points");
        clearBtn.addActionListener(e -> {
            syncFieldsFromPoints(null, null, null, null);
            mapApp.clearPoints();
        });

        JButton resetCameraBtn = new JButton("Reset camera");
        resetCameraBtn.addActionListener(e -> mapApp.resetCamera());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttons.add(computeBtn);
        buttons.add(clearBtn);
        buttons.add(resetCameraBtn);

        JPanel top = new JPanel(new BorderLayout());
        top.add(form, BorderLayout.NORTH);
        top.add(buttons, BorderLayout.CENTER);
        top.add(statusLabel, BorderLayout.SOUTH);
        return top;
    }

    private JPanel buildResultsPanel() {
        JPanel resultsPanel = new JPanel(new BorderLayout());
        resultsPanel.setBorder(new TitledBorder("Results"));
        resultsPanel.add(new JScrollPane(resultArea), BorderLayout.CENTER);
        return resultsPanel;
    }

    public void syncFieldsFromPoints(Double txLon, Double txLat, Double rxLon, Double rxLat) {
        txLonField.setText(txLon == null ? "" : String.format("%.6f", txLon));
        txLatField.setText(txLat == null ? "" : String.format("%.6f", txLat));
        rxLonField.setText(rxLon == null ? "" : String.format("%.6f", rxLon));
        rxLatField.setText(rxLat == null ? "" : String.format("%.6f", rxLat));
    }

    public void setStatus(String text) {
        statusLabel.setText("<html>" + text.replace("\n", "<br>") + "</html>");
    }

    public void setResults(String text) {
        resultArea.setText(text);
    }

    public void clearResults() {
        resultArea.setText("");
    }

    private double parseOrDefault(JTextField field, double defaultValue) {
        try {
            return Double.parseDouble(field.getText().trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    public double getTxHeight() {
        return parseOrDefault(txHeightField, 3.0);
    }

    public double getRxHeight() {
        return parseOrDefault(rxHeightField, 3.0);
    }

    public double getFrequency() {
        return parseOrDefault(freqField, 800.0);
    }

    public double getPercentTime() {
        double v = parseOrDefault(percentField, 1.0);
        return Math.max(1.0, Math.min(50.0, v));
    }
}
