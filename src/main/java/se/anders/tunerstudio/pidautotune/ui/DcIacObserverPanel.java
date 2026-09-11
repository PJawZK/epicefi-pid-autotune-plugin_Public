package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveSample;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition;
import se.anders.tunerstudio.pidautotune.profile.DcIacPositionProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * M1 read-only observer for EpicEFI's feedback DC idle-valve position loop.
 *
 * This panel intentionally performs no event extraction and no recommendations.
 * Its only job is to prove that profile-driven live mapping, freshness, controller
 * terms, target/feedback, H-bridge duty, reset state, and fault state are coherent.
 */
public final class DcIacObserverPanel extends JPanel {
    private static final double REQUIRED_STALE_SECONDS = 1.0;
    private static final DecimalFormat VALUE = new DecimalFormat("0.###");

    private final ControllerProfileDefinition profile = DcIacPositionProfile.DEFINITION;
    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel observerStateValue = new JLabel("Not connected");
    private final JLabel mappingStateValue = new JLabel("Not checked");
    private final JLabel targetValue = new JLabel("—");
    private final JLabel actualValue = new JLabel("—");
    private final JLabel errorValue = new JLabel("—");
    private final JLabel dutyValue = new JLabel("—");
    private final JLabel pidTermsValue = new JLabel("—");
    private final JLabel faultValue = new JLabel("—");
    private final JLabel resetValue = new JLabel("—");
    private final JLabel batteryValue = new JLabel("—");
    private final JLabel eventValue = new JLabel("No reset observed");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JButton refreshConfigurationsButton = new JButton("Refresh configurations");
    private final JButton startButton = new JButton("Start observer");
    private final JButton stopButton = new JButton("Stop observer");

    private final DefaultTableModel channelModel = new DefaultTableModel(
            new Object[] { "Channel", "Role", "Required", "Value", "Age s", "State", "Purpose" }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
        @Override public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 2 ? Boolean.class : String.class;
        }
    };
    private final JTable channelTable = new JTable(channelModel);
    private final Map<String, Integer> rowByChannel = new HashMap<String, Integer>();
    private final JTextArea guidance = new JTextArea();
    private final Timer displayTimer;

    private ControllerAccess controllerAccess;
    private ProfileLiveOutputSubscription subscription;
    private LiveSubscriptionReport subscriptionReport;
    private Double lastResetCounter;

    public DcIacObserverPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        populateChannelRows();
        wireActions();
        displayTimer = new Timer(100, event -> refreshLiveValues());
        displayTimer.setRepeats(true);
        updateControls();
    }

    public void connect(ControllerAccess access, String signature) {
        disconnect();
        controllerAccess = access;
        if (signature != null && !signature.trim().isEmpty()) setControllerSignature(signature);
        if (access == null) {
            observerStateValue.setText("Controller API unavailable");
            updateControls();
            return;
        }
        subscription = new ProfileLiveOutputSubscription(access);
        reloadConfigurationNames();
        observerStateValue.setText("Connected — observer stopped");
        updateControls();
    }

    public void disconnect() {
        displayTimerStopSafe();
        if (subscription != null) subscription.stop();
        subscription = null;
        subscriptionReport = null;
        controllerAccess = null;
        lastResetCounter = null;
        observerStateValue.setText("Not connected");
        mappingStateValue.setText("Not checked");
        clearLiveSummary();
        updateChannelStatesStopped();
        updateControls();
    }

    public void setControllerSignature(String signature) {
        if (signature == null || signature.trim().isEmpty()) return;
        signatureValue.setText(signature.trim());
        signatureValue.setToolTipText(signature.trim());
    }

    private void buildUi() {
        JPanel header = new JPanel(new BorderLayout(8, 8));
        JLabel title = new JLabel("DC IAC — M1 live observer");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        JLabel safety = new JLabel("Read-only: no actuator command, ECU write, gain proposal, or burn", SwingConstants.RIGHT);
        header.add(title, BorderLayout.WEST);
        header.add(safety, BorderLayout.EAST);

        JPanel statePanel = new JPanel(new GridBagLayout());
        GridBagConstraints c = baseConstraints();
        addRow(statePanel, c, 0, "Controller signature", signatureValue);
        addRow(statePanel, c, 1, "Observer state", observerStateValue);
        addRow(statePanel, c, 2, "Required live mapping", mappingStateValue);
        addRow(statePanel, c, 3, "Target position", targetValue);
        addRow(statePanel, c, 4, "Measured position", actualValue);
        addRow(statePanel, c, 5, "Tracking error", errorValue);
        addRow(statePanel, c, 6, "Signed H-bridge duty", dutyValue);
        addRow(statePanel, c, 7, "P / I / D terms", pidTermsValue);
        addRow(statePanel, c, 8, "DC IAC fault", faultValue);
        addRow(statePanel, c, 9, "PID reset counter", resetValue);
        addRow(statePanel, c, 10, "Battery voltage", batteryValue);
        addRow(statePanel, c, 11, "Observer note", eventValue);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        configurationCombo.setPrototypeDisplayValue("mainController — long configuration name");
        controls.add(new JLabel("ECU configuration:"));
        controls.add(configurationCombo);
        controls.add(refreshConfigurationsButton);
        controls.add(startButton);
        controls.add(stopButton);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(statePanel, BorderLayout.CENTER);
        north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        channelTable.setFillsViewportHeight(true);
        channelTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        configureColumns(channelTable);
        add(new JScrollPane(channelTable), BorderLayout.CENTER);

        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setRows(5);
        guidance.setText(
                "M1 purpose: verify the real EpicEFI DC-IAC signal path before any capture or recommendation logic exists. " +
                "The fast inner loop is dcIdleTarget → position PID + DC-idle bias → signed H-bridge duty → idlePositionSensor. " +
                "Required channels must remain fresh and dcIdleFaultCode must stay zero. A PID reset is reported as an observer discontinuity. " +
                "M2 will add stable-hold bias events and opening/closing target-step events; this M1 panel intentionally does neither.");
        guidance.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        add(guidance, BorderLayout.SOUTH);
    }

    private void populateChannelRows() {
        channelModel.setRowCount(0);
        rowByChannel.clear();
        for (ControllerProfileDefinition.Mapping mapping : profile.getOutputChannels()) {
            int row = channelModel.getRowCount();
            rowByChannel.put(mapping.getName(), Integer.valueOf(row));
            channelModel.addRow(new Object[] {
                    mapping.getName(),
                    mapping.getRole().name(),
                    Boolean.valueOf(mapping.isRequired()),
                    "—",
                    "—",
                    "Stopped",
                    mapping.getPurpose()
            });
        }
    }

    private void wireActions() {
        refreshConfigurationsButton.addActionListener(event -> reloadConfigurationNames());
        startButton.addActionListener(event -> startObserver());
        stopButton.addActionListener(event -> stopObserver("Stopped by user"));
        configurationCombo.addActionListener(event -> {
            if (subscription != null && subscription.isStarted()) {
                stopObserver("Configuration changed — restart observer");
            }
        });
    }

    private void reloadConfigurationNames() {
        if (controllerAccess == null) return;
        String previous = (String) configurationCombo.getSelectedItem();
        String[] names = controllerAccess.getEcuConfigurationNames();
        configurationCombo.removeAllItems();
        if (names != null) {
            for (String name : names) configurationCombo.addItem(name);
        }
        if (previous != null) configurationCombo.setSelectedItem(previous);
        if (configurationCombo.getSelectedIndex() < 0 && configurationCombo.getItemCount() > 0) {
            configurationCombo.setSelectedIndex(0);
        }
        updateControls();
    }

    private void startObserver() {
        if (subscription == null) {
            observerStateValue.setText("Controller API unavailable");
            return;
        }
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || configuration.trim().isEmpty()) {
            observerStateValue.setText("No ECU configuration selected");
            return;
        }
        try {
            lastResetCounter = null;
            eventValue.setText("No reset observed");
            subscriptionReport = subscription.start(configuration, profile);
            updateSubscriptionMappingState();
            observerStateValue.setText(subscriptionReport.isUsable()
                    ? "Starting — waiting for fresh required values"
                    : "Blocked — required channels are missing");
            displayTimer.start();
        } catch (Exception ex) {
            subscriptionReport = null;
            subscription.stop();
            observerStateValue.setText("Subscription failed: " + safeMessage(ex));
            mappingStateValue.setText("Subscription failed");
            updateChannelStatesStopped();
        }
        updateControls();
    }

    private void stopObserver(String reason) {
        displayTimerStopSafe();
        if (subscription != null) subscription.stop();
        subscriptionReport = null;
        lastResetCounter = null;
        observerStateValue.setText(reason);
        mappingStateValue.setText("Observer stopped");
        clearLiveSummary();
        updateChannelStatesStopped();
        updateControls();
    }

    private void refreshLiveValues() {
        if (subscription == null || !subscription.isStarted()) return;
        ProfileLiveSample sample = subscription.snapshot();
        boolean requiredWaiting = false;

        for (ControllerProfileDefinition.Mapping mapping : profile.getOutputChannels()) {
            Integer rowIndex = rowByChannel.get(mapping.getName());
            if (rowIndex == null) continue;
            int row = rowIndex.intValue();
            if (!subscription.isSubscribed(mapping.getName())) {
                channelModel.setValueAt("—", row, 3);
                channelModel.setValueAt("—", row, 4);
                channelModel.setValueAt(mapping.isRequired() ? "Missing required" : "Missing optional", row, 5);
                if (mapping.isRequired()) requiredWaiting = true;
                continue;
            }

            double age = sample.getAgeSeconds(mapping.getName());
            boolean present = sample.has(mapping.getName());
            channelModel.setValueAt(present ? format(sample.get(mapping.getName())) : "Waiting", row, 3);
            channelModel.setValueAt(Double.isInfinite(age) ? "—" : format(age), row, 4);
            String state;
            if (!present) {
                state = "Waiting";
            } else if (age > REQUIRED_STALE_SECONDS) {
                state = "Stale";
            } else {
                state = "Fresh";
            }
            channelModel.setValueAt(state, row, 5);
            if (mapping.isRequired() && (!present || age > REQUIRED_STALE_SECONDS)) requiredWaiting = true;
        }

        double target = sample.get("dcIdleTarget");
        double actual = sample.get("idlePositionSensor");
        targetValue.setText(formatPercent(target));
        actualValue.setText(formatPercent(actual));
        errorValue.setText(finite(target) && finite(actual) ? formatSigned(target - actual) + " %" : "—");
        dutyValue.setText(formatPercent(sample.get("dcIdleDutyCycle")));
        pidTermsValue.setText(
                format(sample.get("dcIdlePositionStatus_pTerm")) + " / " +
                format(sample.get("dcIdlePositionStatus_iTerm")) + " / " +
                format(sample.get("dcIdlePositionStatus_dTerm")));
        batteryValue.setText(finite(sample.get("VBatt")) ? format(sample.get("VBatt")) + " V" : "—");

        double fault = sample.get("dcIdleFaultCode");
        faultValue.setText(finite(fault) ? format(fault) : "—");
        double reset = sample.get("dcIdlePositionStatus_resetCounter");
        resetValue.setText(finite(reset) ? format(reset) : "—");
        if (finite(reset)) {
            if (lastResetCounter != null && Math.abs(reset - lastResetCounter.doubleValue()) > 0.0001) {
                eventValue.setText("PID reset observed at " + format(sample.getTimeSeconds()) + " s; continuous response data must split here.");
            }
            lastResetCounter = Double.valueOf(reset);
        }

        if (subscriptionReport != null && !subscriptionReport.isUsable()) {
            observerStateValue.setText("Blocked — missing " + subscriptionReport.getMissingRequired().size() + " required channel(s)");
        } else if (requiredWaiting) {
            observerStateValue.setText("Waiting — required channels are not all fresh");
        } else if (finite(fault) && Math.abs(fault) > 0.0001) {
            observerStateValue.setText("Blocked — DC IAC fault code " + format(fault));
        } else {
            observerStateValue.setText("Observer healthy — M1 signal path valid; recommendations disabled");
        }
    }

    private void updateSubscriptionMappingState() {
        if (subscriptionReport == null) {
            mappingStateValue.setText("Not checked");
            return;
        }
        mappingStateValue.setText(subscriptionReport.isUsable()
                ? subscriptionReport.getSubscribed().size() + " channels subscribed; all required present"
                : subscriptionReport.getMissingRequired().size() + " required missing; "
                    + subscriptionReport.getSubscribed().size() + " subscribed");
        for (ControllerProfileDefinition.Mapping mapping : profile.getOutputChannels()) {
            Integer rowIndex = rowByChannel.get(mapping.getName());
            if (rowIndex == null) continue;
            int row = rowIndex.intValue();
            if (subscription.isSubscribed(mapping.getName())) {
                channelModel.setValueAt("Subscribed", row, 5);
            } else {
                channelModel.setValueAt(mapping.isRequired() ? "Missing required" : "Missing optional", row, 5);
            }
        }
    }

    private void updateChannelStatesStopped() {
        for (int row = 0; row < channelModel.getRowCount(); row++) {
            channelModel.setValueAt("—", row, 3);
            channelModel.setValueAt("—", row, 4);
            channelModel.setValueAt("Stopped", row, 5);
        }
    }

    private void clearLiveSummary() {
        targetValue.setText("—");
        actualValue.setText("—");
        errorValue.setText("—");
        dutyValue.setText("—");
        pidTermsValue.setText("—");
        faultValue.setText("—");
        resetValue.setText("—");
        batteryValue.setText("—");
        eventValue.setText("No reset observed");
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && subscription != null;
        boolean running = connected && subscription.isStarted();
        configurationCombo.setEnabled(connected && !running);
        refreshConfigurationsButton.setEnabled(connected && !running);
        startButton.setEnabled(connected && !running && configurationCombo.getItemCount() > 0);
        stopButton.setEnabled(running);
    }

    private void displayTimerStopSafe() {
        if (displayTimer != null) displayTimer.stop();
    }

    private static void configureColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {260, 160, 80, 100, 75, 130, 560};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 4, 2, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;
        return c;
    }

    private static void addRow(JPanel panel, GridBagConstraints base, int row, String label, JLabel value) {
        GridBagConstraints left = (GridBagConstraints) base.clone();
        left.gridx = 0;
        left.gridy = row;
        left.weightx = 0.0;
        JLabel key = new JLabel(label + ":");
        panel.add(key, left);

        GridBagConstraints right = (GridBagConstraints) base.clone();
        right.gridx = 1;
        right.gridy = row;
        right.weightx = 1.0;
        panel.add(value, right);
    }

    private static String formatPercent(double value) {
        return finite(value) ? format(value) + " %" : "—";
    }

    private static String formatSigned(double value) {
        if (!finite(value)) return "—";
        String text = format(value);
        return value > 0.0 ? "+" + text : text;
    }

    private static String format(double value) {
        if (!finite(value)) return "—";
        synchronized (VALUE) {
            return VALUE.format(value);
        }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String safeMessage(Exception ex) {
        if (ex == null) return "Unknown error";
        String message = ex.getMessage();
        return message == null || message.trim().isEmpty() ? ex.getClass().getSimpleName() : message;
    }
}
