package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacEvent;
import se.anders.tunerstudio.pidautotune.live.DcIacEventExtractor;
import se.anders.tunerstudio.pidautotune.live.DcIacEventSettings;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveSample;
import se.anders.tunerstudio.pidautotune.profile.DcIacPositionProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Locale;

/** Read-only M2 event extraction UI for the DC-IAC inner position loop. */
public final class DcIacEventPanel extends JPanel {
    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel stateValue = new JLabel("Not connected");
    private final JLabel readinessValue = new JLabel("Stopped");
    private final JLabel configValue = new JLabel("—");
    private final JLabel liveValue = new JLabel("—");
    private final JLabel eventValue = new JLabel("No M2 event yet");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JButton refreshButton = new JButton("Refresh configurations");
    private final JButton startButton = new JButton("Start M2 capture");
    private final JButton stopButton = new JButton("Stop");
    private final JButton clearButton = new JButton("Clear events");

    private final DefaultTableModel eventModel = new DefaultTableModel(
            new Object[] { "#", "Type", "Status", "Start s", "Duration s", "From %", "To %", "Samples", "Result", "Detail" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable eventTable = new JTable(eventModel);
    private final JTextArea guidance = new JTextArea();
    private final Timer captureTimer;

    private final DcIacEventExtractor extractor = new DcIacEventExtractor();
    private ControllerAccess controllerAccess;
    private ProfileLiveOutputSubscription subscription;
    private DcIacSettingsAccess settingsAccess;
    private DcIacEventSettings settings;
    private DcIacSettingsAccess.Snapshot settingsSnapshot;
    private int timerTicks;
    private int displayedEventCount;

    public DcIacEventPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        wireActions();
        captureTimer = new Timer(20, event -> captureTick());
        captureTimer.setRepeats(true);
        updateControls();
    }

    public void connect(ControllerAccess access, String signature) {
        disconnect();
        controllerAccess = access;
        if (signature != null && !signature.trim().isEmpty()) setControllerSignature(signature);
        if (access == null) {
            stateValue.setText("Controller API unavailable");
            updateControls();
            return;
        }
        subscription = new ProfileLiveOutputSubscription(access);
        settingsAccess = new DcIacSettingsAccess(access);
        reloadConfigurations();
        stateValue.setText("Connected — M2 capture stopped");
        updateControls();
    }

    public void disconnect() {
        captureTimerStopSafe();
        if (subscription != null) subscription.stop();
        subscription = null;
        settingsAccess = null;
        settings = null;
        settingsSnapshot = null;
        controllerAccess = null;
        extractor.stop();
        stateValue.setText("Not connected");
        readinessValue.setText("Stopped");
        configValue.setText("—");
        liveValue.setText("—");
        updateControls();
    }

    public void setControllerSignature(String signature) {
        if (signature == null || signature.trim().isEmpty()) return;
        signatureValue.setText(signature.trim());
        signatureValue.setToolTipText(signature.trim());
    }

    private void buildUi() {
        JLabel title = new JLabel("DC IAC — M2 event extraction");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        JLabel safety = new JLabel("Read-only: extraction only; no PID/bias recommendations and no ECU writes");
        JPanel header = new JPanel(new BorderLayout(8, 8));
        header.add(title, BorderLayout.WEST);
        header.add(safety, BorderLayout.EAST);

        JPanel status = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.gridy = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(2, 4, 2, 8);
        addRow(status, c, "Controller signature", signatureValue);
        addRow(status, c, "Capture state", stateValue);
        addRow(status, c, "Tuning-evidence readiness", readinessValue);
        addRow(status, c, "Configured DC-IAC travel / optional shared pause", configValue);
        addRow(status, c, "Live target / actual / duty / VBatt", liveValue);
        addRow(status, c, "Last M2 transition", eventValue);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        configurationCombo.setPrototypeDisplayValue("mainController — long configuration name");
        controls.add(new JLabel("ECU configuration:"));
        controls.add(configurationCombo);
        controls.add(refreshButton);
        controls.add(startButton);
        controls.add(stopButton);
        controls.add(clearButton);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(status, BorderLayout.CENTER);
        north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        eventTable.setFillsViewportHeight(true);
        eventTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = { 45, 115, 80, 75, 85, 75, 75, 70, 175, 520 };
        for (int i = 0; i < widths.length; i++) eventTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        add(new JScrollPane(eventTable), BorderLayout.CENTER);

        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setRows(5);
        guidance.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        guidance.setText(
                "M2 detects three DC-IAC evidence classes only: stable target holds, persistent opening steps, and persistent closing steps. " +
                "A target change must follow a stable plateau and persist before it becomes a real step. Single-record target excursions are retained as rejected transient candidates. " +
                "Evidence is rejected for stale/missing DC-IAC channels, VBatt outside 10.0–16.5 V, excessive voltage movement, DC-IAC fault, PID reset, target-limit proximity, target changes inside a response window, or |dcIdleDutyCycle| >= 99%. " +
                "The firmware's shared pauseEtbControl setting is optional context only for this H-bridge DC-IAC workflow: if exposed and true it blocks evidence, but its absence never blocks M2 startup. " +
                "No response scoring or setting recommendation exists in M2.");
        add(guidance, BorderLayout.SOUTH);
    }

    private void wireActions() {
        refreshButton.addActionListener(event -> reloadConfigurations());
        startButton.addActionListener(event -> startCapture());
        stopButton.addActionListener(event -> stopCapture("Stopped by user"));
        clearButton.addActionListener(event -> {
            extractor.reset();
            displayedEventCount = 0;
            eventModel.setRowCount(0);
            eventValue.setText("No M2 event yet");
        });
        configurationCombo.addActionListener(event -> {
            if (subscription != null && subscription.isStarted()) {
                stopCapture("Configuration changed — restart M2 capture");
            }
        });
    }

    private void startCapture() {
        if (subscription == null || settingsAccess == null) return;
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || configuration.trim().isEmpty()) {
            stateValue.setText("No ECU configuration selected");
            return;
        }
        try {
            settingsSnapshot = settingsAccess.read(configuration);
            if (!(settingsSnapshot.getMaximumPosition() > settingsSnapshot.getMinimumPosition())) {
                stateValue.setText("Blocked — invalid configured DC-IAC travel limits");
                return;
            }
            settings = new DcIacEventSettings(settingsSnapshot.getMinimumPosition(), settingsSnapshot.getMaximumPosition());
            LiveSubscriptionReport report = subscription.start(configuration, DcIacPositionProfile.DEFINITION);
            if (!report.isUsable() || !subscription.isSubscribed("VBatt")) {
                subscription.stop();
                stateValue.setText(!report.isUsable()
                        ? "Blocked — required DC-IAC live mapping missing"
                        : "Blocked — VBatt is required for M2 tuning evidence");
                return;
            }
            extractor.reset();
            eventModel.setRowCount(0);
            displayedEventCount = 0;
            timerTicks = 0;
            updateConfigurationLabel();
            stateValue.setText(settingsSnapshot.isMotorControlPaused()
                    ? "M2 running — evidence blocked by shared motor-pause flag"
                    : "M2 capture running");
            captureTimer.start();
        } catch (Exception ex) {
            if (subscription != null) subscription.stop();
            stateValue.setText("M2 start failed: " + safeMessage(ex));
        }
        updateControls();
    }

    private void stopCapture(String reason) {
        captureTimerStopSafe();
        if (subscription != null) subscription.stop();
        extractor.stop();
        stateValue.setText(reason);
        readinessValue.setText("Stopped");
        liveValue.setText("—");
        updateControls();
    }

    private void captureTick() {
        if (subscription == null || !subscription.isStarted() || settings == null || settingsSnapshot == null) return;
        ProfileLiveSample sample = subscription.snapshot();
        extractor.process(sample, settings, settingsSnapshot.isMotorControlPaused());
        timerTicks++;

        if (timerTicks % 50 == 0) refreshConfigurationSnapshot();
        if (timerTicks % 5 == 0) {
            readinessValue.setText(extractor.getReadiness());
            eventValue.setText(extractor.getLastTransition());
            liveValue.setText("target " + value(sample.get("dcIdleTarget"))
                    + "% / actual " + value(sample.get("idlePositionSensor"))
                    + "% / duty " + value(sample.get("dcIdleDutyCycle"))
                    + "% / " + value(sample.get("VBatt")) + " V");
            refreshEventTable();
        }
    }

    private void refreshConfigurationSnapshot() {
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || settingsAccess == null) return;
        try {
            DcIacSettingsAccess.Snapshot updated = settingsAccess.read(configuration);
            boolean limitsChanged = Math.abs(updated.getMinimumPosition() - settingsSnapshot.getMinimumPosition()) > 0.0001
                    || Math.abs(updated.getMaximumPosition() - settingsSnapshot.getMaximumPosition()) > 0.0001;
            settingsSnapshot = updated;
            if (limitsChanged) {
                settings = new DcIacEventSettings(updated.getMinimumPosition(), updated.getMaximumPosition());
                extractor.stop();
                stateValue.setText("M2 capture running — travel limits changed; event baseline restarted");
            } else if (updated.isMotorControlPaused()) {
                stateValue.setText("M2 running — evidence blocked by shared motor-pause flag");
            } else {
                stateValue.setText("M2 capture running");
            }
            updateConfigurationLabel();
        } catch (Exception ex) {
            stateValue.setText("Settings refresh failed: " + safeMessage(ex));
        }
    }

    private void refreshEventTable() {
        List<DcIacEvent> events = extractor.getEvents();
        if (events.size() == displayedEventCount) return;
        eventModel.setRowCount(0);
        for (DcIacEvent event : events) {
            eventModel.addRow(new Object[] {
                    Integer.valueOf(event.getSequence()),
                    type(event.getType()),
                    event.getStatus().name(),
                    value(event.getStartSeconds()),
                    value(event.getDurationSeconds()),
                    value(event.getFromTarget()),
                    value(event.getToTarget()),
                    Integer.valueOf(event.getSamples().size()),
                    event.getResultCode(),
                    event.getDetail()
            });
        }
        displayedEventCount = events.size();
        if (displayedEventCount > 0) {
            int row = displayedEventCount - 1;
            eventTable.scrollRectToVisible(eventTable.getCellRect(row, 0, true));
        }
    }

    private void reloadConfigurations() {
        if (controllerAccess == null) return;
        String previous = (String) configurationCombo.getSelectedItem();
        String[] names = controllerAccess.getEcuConfigurationNames();
        configurationCombo.removeAllItems();
        if (names != null) for (String name : names) configurationCombo.addItem(name);
        if (previous != null) configurationCombo.setSelectedItem(previous);
        if (configurationCombo.getSelectedIndex() < 0 && configurationCombo.getItemCount() > 0) configurationCombo.setSelectedIndex(0);
        updateControls();
    }

    private void updateConfigurationLabel() {
        if (settingsSnapshot == null) {
            configValue.setText("—");
            return;
        }
        String pause = settingsSnapshot.isMotorControlPauseKnown()
                ? String.valueOf(settingsSnapshot.isMotorControlPaused())
                : "unavailable (not required)";
        configValue.setText(value(settingsSnapshot.getMinimumPosition()) + "–" + value(settingsSnapshot.getMaximumPosition())
                + "% / shared pause=" + pause);
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && subscription != null;
        boolean running = connected && subscription.isStarted();
        configurationCombo.setEnabled(connected && !running);
        refreshButton.setEnabled(connected && !running);
        startButton.setEnabled(connected && !running && configurationCombo.getItemCount() > 0);
        stopButton.setEnabled(running);
        clearButton.setEnabled(!running || !extractor.isCapturingStep());
    }

    private void captureTimerStopSafe() {
        if (captureTimer != null && captureTimer.isRunning()) captureTimer.stop();
    }

    private static void addRow(JPanel panel, GridBagConstraints c, String label, JLabel value) {
        c.gridx = 0;
        c.weightx = 0.0;
        c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1.0;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, c);
        c.gridy++;
    }

    private static String type(DcIacEvent.Type type) {
        if (type == DcIacEvent.Type.STABLE_HOLD) return "Stable hold";
        if (type == DcIacEvent.Type.OPENING_STEP) return "Opening step";
        return "Closing step";
    }

    private static String value(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "—";
        return String.format(Locale.US, "%.3f", value);
    }

    private static String safeMessage(Exception ex) {
        return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
    }
}
