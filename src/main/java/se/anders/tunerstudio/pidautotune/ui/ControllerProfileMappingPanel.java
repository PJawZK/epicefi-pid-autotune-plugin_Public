package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.controller.ControllerProfileDataAccess;
import se.anders.tunerstudio.pidautotune.model.DiagnosticRow;
import se.anders.tunerstudio.pidautotune.model.DiagnosticTableModel;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingWorker;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Collections;
import java.util.List;

/** Generic read-only mapping view for one ControllerProfileDefinition. */
public final class ControllerProfileMappingPanel extends JPanel {
    private final ControllerProfileDefinition profile;
    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel statusValue = new JLabel("Not connected");
    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JButton refreshButton = new JButton("Refresh mapping");
    private final DiagnosticTableModel model = new DiagnosticTableModel();
    private final JTable table = new JTable(model);
    private ControllerProfileDataAccess dataAccess;
    private boolean busy;

    public ControllerProfileMappingPanel(ControllerProfileDefinition profile) {
        super(new BorderLayout(8, 8));
        if (profile == null) throw new IllegalArgumentException("profile cannot be null");
        this.profile = profile;
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        wireActions();
        updateControls();
    }

    public void connect(ControllerAccess access, String signature) {
        disconnect();
        if (signature != null && !signature.trim().isEmpty()) setControllerSignature(signature);
        if (access == null) {
            statusValue.setText("Controller API unavailable");
            updateControls();
            return;
        }
        dataAccess = new ControllerProfileDataAccess(access);
        reloadConfigurationNames();
        refreshMapping();
    }

    public void disconnect() {
        dataAccess = null;
        busy = false;
        model.setRows(Collections.<DiagnosticRow>emptyList());
        configurationCombo.removeAllItems();
        statusValue.setText("Not connected");
        updateControls();
    }

    public void setControllerSignature(String signature) {
        if (signature == null || signature.trim().isEmpty()) return;
        signatureValue.setText(signature.trim());
        signatureValue.setToolTipText(signature.trim());
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel(profile.getDisplayName() + " — definition mapping");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        JLabel safety = new JLabel("Read-only definition inspection");
        header.add(title, BorderLayout.WEST);
        header.add(safety, BorderLayout.EAST);

        JPanel info = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        info.add(new JLabel("Signature:"));
        info.add(signatureValue);
        info.add(new JLabel("Status:"));
        info.add(statusValue);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        configurationCombo.setPrototypeDisplayValue("mainController — long configuration name");
        controls.add(new JLabel("ECU configuration:"));
        controls.add(configurationCombo);
        controls.add(refreshButton);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(header, BorderLayout.NORTH);
        north.add(info, BorderLayout.CENTER);
        north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        configureColumns(table);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JTextArea guidance = new JTextArea(
                "M1 uses this page to prove that the active TunerStudio definition exposes the exact settings and output channels declared by the controller profile. " +
                "For DC IAC this includes P/I/D, output and integral limits, target-position limits, the bias/feed-forward curve, position-sensor calibration, jam/fault guards, and the live inner-loop channels. Nothing on this page writes to the ECU.");
        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setRows(4);
        guidance.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(guidance, BorderLayout.SOUTH);
    }

    private void wireActions() {
        refreshButton.addActionListener(event -> refreshMapping());
        configurationCombo.addActionListener(event -> {
            if (!busy && dataAccess != null) refreshMapping();
        });
    }

    private void reloadConfigurationNames() {
        if (dataAccess == null) return;
        String previous = (String) configurationCombo.getSelectedItem();
        configurationCombo.removeAllItems();
        for (String name : dataAccess.getConfigurationNames()) configurationCombo.addItem(name);
        if (previous != null) configurationCombo.setSelectedItem(previous);
        if (configurationCombo.getSelectedIndex() < 0 && configurationCombo.getItemCount() > 0) {
            configurationCombo.setSelectedIndex(0);
        }
        updateControls();
    }

    private void refreshMapping() {
        if (dataAccess == null || busy) return;
        final String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || configuration.trim().isEmpty()) {
            model.setRows(Collections.<DiagnosticRow>emptyList());
            statusValue.setText("No ECU configuration selected");
            return;
        }

        busy = true;
        statusValue.setText("Reading " + configuration + " …");
        updateControls();
        new SwingWorker<List<DiagnosticRow>, Void>() {
            @Override
            protected List<DiagnosticRow> doInBackground() {
                return dataAccess.loadProfileMapping(configuration, profile);
            }

            @Override
            protected void done() {
                try {
                    List<DiagnosticRow> rows = get();
                    model.setRows(rows);
                    int missingRequired = 0;
                    int readErrors = 0;
                    for (DiagnosticRow row : rows) {
                        if ("Required mapping missing".equals(row.getStatus())) missingRequired++;
                        if ("Read error".equals(row.getStatus())) readErrors++;
                    }
                    if (readErrors > 0) {
                        statusValue.setText(readErrors + " definition read error(s); inspect table");
                    } else if (missingRequired > 0) {
                        statusValue.setText(missingRequired + " required mapping(s) missing");
                    } else {
                        statusValue.setText("All required profile mappings resolved");
                    }
                } catch (Exception ex) {
                    model.setRows(Collections.<DiagnosticRow>emptyList());
                    statusValue.setText("Mapping read failed: " + safeMessage(ex));
                } finally {
                    busy = false;
                    updateControls();
                }
            }
        }.execute();
    }

    private void updateControls() {
        boolean connected = dataAccess != null;
        configurationCombo.setEnabled(connected && !busy);
        refreshButton.setEnabled(connected && !busy && configurationCombo.getItemCount() > 0);
    }

    private static void configureColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {120, 260, 180, 80, 100, 100, 120, 650, 180};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "Unknown error";
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty() ? ex.getClass().getSimpleName() : message;
    }
}
