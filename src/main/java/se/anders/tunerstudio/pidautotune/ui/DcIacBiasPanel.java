package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasCharacterizationEngine;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasCharacterizationSettings;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacEventSettings;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.live.ProfileBackgroundSampler;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveSample;
import se.anders.tunerstudio.pidautotune.profile.DcIacPositionProfile;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasRecommendationEngine;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Read-only DC-IAC M4A static feed-forward/bias characterization workspace. */
public final class DcIacBiasPanel extends JPanel {
    private static final long SAMPLE_PERIOD_MS = 20L;

    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel stateValue = new JLabel("Not connected");
    private final JLabel readinessValue = new JLabel("Stopped");
    private final JLabel configValue = new JLabel("—");
    private final JLabel liveValue = new JLabel("—");
    private final JLabel summaryValue = new JLabel("No M4A equilibrium evidence yet");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JButton refreshButton = new JButton("Refresh configurations");
    private final JButton startButton = new JButton("Start M4A capture");
    private final JButton stopButton = new JButton("Stop");
    private final JButton clearButton = new JButton("Clear evidence");
    private final JButton exportButton = new JButton("Export M4A CSV...");

    private final DefaultTableModel evidenceModel = new DefaultTableModel(
            new Object[] {
                    "#", "Target %", "Actual %", "Err %", "Current bias %", "Required bias %",
                    "Correction %", "I term %", "Target slope", "Actual slope", "I slope",
                    "VBatt spread", "Hz", "Segment"
            }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable evidenceTable = new JTable(evidenceModel);

    private final DefaultTableModel recommendationModel = new DefaultTableModel(
            new Object[] {
                    "Segment", "Windows", "Center %", "Current bias %", "Required bias %",
                    "Raw corr %", "Proposed corr %", "Suggested local bias %", "MAD", "Confidence %", "Status", "Detail"
            }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable recommendationTable = new JTable(recommendationModel);
    private final JTextArea guidance = new JTextArea();
    private final Timer uiTimer;

    private final Object evidenceLock = new Object();
    private final List<DcIacBiasEvidence> evidence = new ArrayList<DcIacBiasEvidence>();
    private final DcIacBiasCharacterizationEngine engine = new DcIacBiasCharacterizationEngine();
    private final DcIacBiasCharacterizationSettings characterizationSettings = new DcIacBiasCharacterizationSettings();
    private final DcIacBiasRecommendationEngine recommendationEngine = new DcIacBiasRecommendationEngine();

    private ControllerAccess controllerAccess;
    private ProfileLiveOutputSubscription subscription;
    private ProfileBackgroundSampler sampler;
    private DcIacSettingsAccess settingsAccess;
    private DcIacBiasSettingsAccess biasAccess;
    private volatile DcIacSettingsAccess.Snapshot settingsSnapshot;
    private volatile DcIacBiasSettingsAccess.Snapshot biasSnapshot;
    private volatile DcIacEventSettings eventSettings;
    private volatile ProfileLiveSample latestSample;
    private volatile String samplerError = "";
    private int displayedEvidence;
    private int settingsRefreshTicks;

    public DcIacBiasPanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        wireActions();
        uiTimer = new Timer(100, event -> refreshUi());
        uiTimer.setRepeats(true);
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
        biasAccess = new DcIacBiasSettingsAccess(access);
        reloadConfigurations();
        stateValue.setText("Connected — M4A capture stopped");
        updateControls();
    }

    public void disconnect() {
        stopCaptureInternal();
        subscription = null;
        settingsAccess = null;
        biasAccess = null;
        controllerAccess = null;
        settingsSnapshot = null;
        biasSnapshot = null;
        eventSettings = null;
        latestSample = null;
        stateValue.setText("Not connected");
        readinessValue.setText("Stopped");
        liveValue.setText("—");
        updateControls();
    }

    public void setControllerSignature(String signature) {
        if (signature == null || signature.trim().isEmpty()) return;
        signatureValue.setText(signature.trim());
        signatureValue.setToolTipText(signature.trim());
    }

    private void buildUi() {
        JLabel title = new JLabel("DC IAC — M4A static bias characterization");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        JLabel safety = new JLabel("Read-only recommendation preview; no ECU writes, burns, or actuator commands");
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
        addRow(status, c, "Equilibrium readiness", readinessValue);
        addRow(status, c, "Bias curve / I limits", configValue);
        addRow(status, c, "Live target / actual / duty / PID / I / VBatt", liveValue);
        addRow(status, c, "M4A summary", summaryValue);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        configurationCombo.setPrototypeDisplayValue("MEGA144H7EPIC-Volvo940Turbo");
        controls.add(new JLabel("ECU configuration:"));
        controls.add(configurationCombo);
        controls.add(refreshButton);
        controls.add(startButton);
        controls.add(stopButton);
        controls.add(clearButton);
        controls.add(exportButton);

        JPanel north = new JPanel(new BorderLayout(6, 6));
        north.add(header, BorderLayout.NORTH);
        north.add(status, BorderLayout.CENTER);
        north.add(controls, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        evidenceTable.setFillsViewportHeight(true);
        evidenceTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] evidenceWidths = { 45, 75, 75, 70, 95, 100, 90, 75, 90, 90, 80, 95, 65, 90 };
        for (int i = 0; i < evidenceWidths.length; i++) evidenceTable.getColumnModel().getColumn(i).setPreferredWidth(evidenceWidths[i]);

        recommendationTable.setFillsViewportHeight(true);
        recommendationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] recWidths = { 85, 70, 75, 95, 100, 85, 105, 120, 70, 90, 135, 520 };
        for (int i = 0; i < recWidths.length; i++) recommendationTable.getColumnModel().getColumn(i).setPreferredWidth(recWidths[i]);

        JPanel evidencePanel = new JPanel(new BorderLayout());
        evidencePanel.add(new JLabel("Accepted non-overlapping equilibrium windows"), BorderLayout.NORTH);
        evidencePanel.add(new JScrollPane(evidenceTable), BorderLayout.CENTER);
        JPanel recommendationPanel = new JPanel(new BorderLayout());
        recommendationPanel.add(new JLabel("Local bias recommendation preview"), BorderLayout.NORTH);
        recommendationPanel.add(new JScrollPane(recommendationTable), BorderLayout.CENTER);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, evidencePanel, recommendationPanel);
        split.setResizeWeight(0.55);
        add(split, BorderLayout.CENTER);

        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setRows(6);
        guidance.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        guidance.setText(
                "M4A characterizes the static DC-IAC feed-forward/bias requirement before PID recommendation work. It uses rolling 5-second source-update windows rather than M2's first two-second hold and requires target, valve position and I term to be settled, low VBatt spread, no fault/reset/saturation, and runtime feed-forward (duty - PID output) to agree with the configured bias curve. " +
                "Accepted windows are non-overlapping. Repeated consistent windows near the same operating point may produce READY_LOCAL_BIAS, which is only a local bias correction preview. M4A deliberately does not project one observed local correction into unobserved dcIdleBiasBins/dcIdleBiasValues knots. " +
                "Do not change P/I/D or the bias curve while M4A capture is running. Any detected configuration change stops capture. There are still no ECU write, burn, automatic apply, or actuator-command paths.");
        add(guidance, BorderLayout.SOUTH);
    }

    private void wireActions() {
        refreshButton.addActionListener(event -> reloadConfigurations());
        startButton.addActionListener(event -> startCapture());
        stopButton.addActionListener(event -> stopCapture("Stopped by user"));
        clearButton.addActionListener(event -> clearEvidence());
        exportButton.addActionListener(event -> exportCsv());
        configurationCombo.addActionListener(event -> {
            if (sampler != null && sampler.isRunning()) stopCapture("Configuration changed — restart M4A capture");
        });
    }

    private void startCapture() {
        if (subscription == null || settingsAccess == null || biasAccess == null) return;
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || configuration.trim().isEmpty()) {
            stateValue.setText("No ECU configuration selected");
            return;
        }
        try {
            DcIacSettingsAccess.Snapshot settings = settingsAccess.read(configuration);
            DcIacBiasSettingsAccess.Snapshot bias = biasAccess.read(configuration);
            if (!(settings.getMaximumPosition() > settings.getMinimumPosition())) {
                stateValue.setText("Blocked — invalid configured DC-IAC travel limits");
                return;
            }
            LiveSubscriptionReport report = subscription.start(configuration, DcIacPositionProfile.DEFINITION);
            if (!report.isUsable() || !subscription.isSubscribed("VBatt")) {
                subscription.stop();
                stateValue.setText(!report.isUsable()
                        ? "Blocked — required DC-IAC live mapping missing"
                        : "Blocked — VBatt is required for M4A evidence");
                return;
            }

            settingsSnapshot = settings;
            biasSnapshot = bias;
            eventSettings = new DcIacEventSettings(settings.getMinimumPosition(), settings.getMaximumPosition());
            engine.reset();
            latestSample = null;
            samplerError = "";
            settingsRefreshTicks = 0;
            clearEvidence();
            updateConfigurationLabel();

            sampler = new ProfileBackgroundSampler(subscription, SAMPLE_PERIOD_MS);
            sampler.start(new ProfileBackgroundSampler.Listener() {
                @Override public void onSample(ProfileLiveSample sample) { processBackgroundSample(sample); }
                @Override public void onError(Throwable error) { samplerError = safeMessage(error); }
            });
            stateValue.setText(settings.isMotorControlPaused()
                    ? "M4A running — evidence blocked by shared motor-pause flag"
                    : "M4A rolling equilibrium capture running");
            uiTimer.start();
        } catch (Exception ex) {
            if (subscription != null) subscription.stop();
            stateValue.setText("M4A start failed: " + safeMessage(ex));
        }
        updateControls();
    }

    private void processBackgroundSample(ProfileLiveSample sample) {
        DcIacSettingsAccess.Snapshot settings = settingsSnapshot;
        DcIacBiasSettingsAccess.Snapshot bias = biasSnapshot;
        DcIacEventSettings localEventSettings = eventSettings;
        if (sample == null || settings == null || bias == null || localEventSettings == null) return;
        latestSample = sample;
        DcIacBiasEvidence row = engine.process(
                sample, localEventSettings, characterizationSettings, bias, settings.isMotorControlPaused());
        if (row != null) {
            synchronized (evidenceLock) { evidence.add(row); }
        }
    }

    private void refreshUi() {
        if (samplerError != null && !samplerError.isEmpty()) stateValue.setText("M4A sampler error: " + samplerError);
        readinessValue.setText(engine.getReadiness());
        ProfileLiveSample sample = latestSample;
        if (sample != null) {
            liveValue.setText("target " + value(sample.get("dcIdleTarget"))
                    + "% / actual " + value(sample.get("idlePositionSensor"))
                    + "% / duty " + value(sample.get("dcIdleDutyCycle"))
                    + "% / PID " + value(sample.get("dcIdlePositionStatus_output"))
                    + "% / I " + value(sample.get("dcIdlePositionStatus_iTerm"))
                    + "% / " + value(sample.get("VBatt")) + " V");
        }

        List<DcIacBiasEvidence> copy = evidenceCopy();
        List<DcIacBiasRecommendation> recommendations = recommendationEngine.evaluate(copy, characterizationSettings);
        int ready = 0;
        for (DcIacBiasRecommendation recommendation : recommendations) if (recommendation.isReady()) ready++;
        summaryValue.setText("equilibrium windows " + copy.size() + " / local regions "
                + recommendations.size() + " / ready local bias previews " + ready);
        refreshTables(copy, recommendations);

        if (sampler != null && sampler.isRunning() && ++settingsRefreshTicks >= 20) {
            settingsRefreshTicks = 0;
            refreshConfigurationSnapshot();
        }
        updateControls();
    }

    private void refreshTables(List<DcIacBiasEvidence> copy, List<DcIacBiasRecommendation> recommendations) {
        if (copy.size() != displayedEvidence) {
            evidenceModel.setRowCount(0);
            for (DcIacBiasEvidence row : copy) {
                evidenceModel.addRow(new Object[] {
                        Integer.valueOf(row.getSequence()), value(row.getTarget()), value(row.getActual()), value(row.getSteadyError()),
                        value(row.getConfiguredBias()), value(row.getRequiredBias()), value(row.getCorrection()), value(row.getMeanITerm()),
                        value(row.getTargetSlope()), value(row.getActualSlope()), value(row.getITermSlope()),
                        value(row.getBatterySpread()), value(row.getSampleRateHz()), row.getCurveSegment()
                });
            }
            displayedEvidence = copy.size();
            if (displayedEvidence > 0) evidenceTable.scrollRectToVisible(evidenceTable.getCellRect(displayedEvidence - 1, 0, true));
        }

        recommendationModel.setRowCount(0);
        for (DcIacBiasRecommendation row : recommendations) {
            recommendationModel.addRow(new Object[] {
                    row.getCurveSegment(), Integer.valueOf(row.getEvidenceWindows()), value(row.getCenterTarget()),
                    value(row.getCurrentBias()), value(row.getRequiredBias()), value(row.getRawCorrection()),
                    value(row.getProposedCorrection()), value(row.getProposedLocalBias()), value(row.getCorrectionMad()),
                    value(row.getConfidencePercent()), row.getStatus(), row.getDetail()
            });
        }
    }

    private void refreshConfigurationSnapshot() {
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || settingsAccess == null || biasAccess == null
                || settingsSnapshot == null || biasSnapshot == null) return;
        try {
            DcIacSettingsAccess.Snapshot settings = settingsAccess.read(configuration);
            DcIacBiasSettingsAccess.Snapshot bias = biasAccess.read(configuration);
            if (configurationChanged(settingsSnapshot, settings, biasSnapshot, bias)) {
                stopCaptureInternal();
                stateValue.setText("M4A stopped — DC-IAC PID/travel/bias configuration changed during capture");
                readinessValue.setText("Restart capture with the new configuration snapshot");
                return;
            }
            settingsSnapshot = settings;
            biasSnapshot = bias;
            updateConfigurationLabel();
        } catch (Exception ex) {
            stateValue.setText("Settings refresh failed: " + safeMessage(ex));
        }
    }

    private static boolean configurationChanged(
            DcIacSettingsAccess.Snapshot oldSettings,
            DcIacSettingsAccess.Snapshot newSettings,
            DcIacBiasSettingsAccess.Snapshot oldBias,
            DcIacBiasSettingsAccess.Snapshot newBias) {
        if (Math.abs(oldSettings.getMinimumPosition() - newSettings.getMinimumPosition()) > 0.0001) return true;
        if (Math.abs(oldSettings.getMaximumPosition() - newSettings.getMaximumPosition()) > 0.0001) return true;
        if (oldSettings.isMotorControlPauseKnown() != newSettings.isMotorControlPauseKnown()) return true;
        if (oldSettings.isMotorControlPaused() != newSettings.isMotorControlPaused()) return true;
        if (oldSettings.arePidGainsKnown() != newSettings.arePidGainsKnown()) return true;
        if (oldSettings.arePidGainsKnown()) {
            if (Math.abs(oldSettings.getPFactor() - newSettings.getPFactor()) > 0.000001) return true;
            if (Math.abs(oldSettings.getIFactor() - newSettings.getIFactor()) > 0.000001) return true;
            if (Math.abs(oldSettings.getDFactor() - newSettings.getDFactor()) > 0.000001) return true;
        }
        if (!Arrays.equals(oldBias.getBins(), newBias.getBins())) return true;
        if (!Arrays.equals(oldBias.getValues(), newBias.getValues())) return true;
        return false;
    }

    private void stopCapture(String reason) {
        stopCaptureInternal();
        stateValue.setText(reason);
        readinessValue.setText("Stopped");
        liveValue.setText("—");
        updateControls();
    }

    private void stopCaptureInternal() {
        if (uiTimer != null && uiTimer.isRunning()) uiTimer.stop();
        if (sampler != null) sampler.stop();
        sampler = null;
        if (subscription != null) subscription.stop();
        engine.stop();
    }

    private void clearEvidence() {
        synchronized (evidenceLock) { evidence.clear(); }
        displayedEvidence = 0;
        evidenceModel.setRowCount(0);
        recommendationModel.setRowCount(0);
        summaryValue.setText("No M4A equilibrium evidence yet");
    }

    private List<DcIacBiasEvidence> evidenceCopy() {
        synchronized (evidenceLock) { return new ArrayList<DcIacBiasEvidence>(evidence); }
    }

    private void exportCsv() {
        List<DcIacBiasEvidence> copy = evidenceCopy();
        if (copy.isEmpty()) {
            stateValue.setText("Nothing to export — no M4A equilibrium evidence captured");
            return;
        }
        List<DcIacBiasRecommendation> recommendations = recommendationEngine.evaluate(copy, characterizationSettings);
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export DC-IAC M4A bias characterization");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        chooser.setSelectedFile(new File("dc-iac-m4a-bias-characterization.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.US).endsWith(".csv")) file = new File(file.getParentFile(), file.getName() + ".csv");
        try {
            BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
            try {
                writer.write("record_type,sequence_or_segment,target_or_center_pct,actual_pct,steady_error_pct,current_bias_pct,required_bias_pct,correction_or_raw_pct,proposed_correction_pct,suggested_local_bias_pct,mean_duty_pct,mean_pid_output_pct,mean_i_term_pct,target_slope_pct_per_s,actual_slope_pct_per_s,i_term_slope_pct_per_s,actual_span_pct,battery_spread_v,sample_rate_hz,evidence_windows,correction_mad,confidence_pct,status_or_segment,detail\n");
                for (DcIacBiasEvidence row : copy) {
                    writer.write("EVIDENCE,");
                    writer.write(Integer.toString(row.getSequence())); writer.write(',');
                    writer.write(number(row.getTarget())); writer.write(',');
                    writer.write(number(row.getActual())); writer.write(',');
                    writer.write(number(row.getSteadyError())); writer.write(',');
                    writer.write(number(row.getConfiguredBias())); writer.write(',');
                    writer.write(number(row.getRequiredBias())); writer.write(',');
                    writer.write(number(row.getCorrection())); writer.write(",,,");
                    writer.write(number(row.getMeanDuty())); writer.write(',');
                    writer.write(number(row.getMeanPidOutput())); writer.write(',');
                    writer.write(number(row.getMeanITerm())); writer.write(',');
                    writer.write(number(row.getTargetSlope())); writer.write(',');
                    writer.write(number(row.getActualSlope())); writer.write(',');
                    writer.write(number(row.getITermSlope())); writer.write(',');
                    writer.write(number(row.getActualSpan())); writer.write(',');
                    writer.write(number(row.getBatterySpread())); writer.write(',');
                    writer.write(number(row.getSampleRateHz())); writer.write(",,,,,");
                    writer.write(csv(row.getCurveSegment())); writer.write(',');
                    writer.write(csv("configured_ff=" + number(row.getConfiguredBias()) + "; observed_ff=" + number(row.getObservedFeedForward()))); writer.write('\n');
                }
                for (DcIacBiasRecommendation row : recommendations) {
                    writer.write("RECOMMENDATION,");
                    writer.write(csv(row.getCurveSegment())); writer.write(',');
                    writer.write(number(row.getCenterTarget())); writer.write(",,,");
                    writer.write(number(row.getCurrentBias())); writer.write(',');
                    writer.write(number(row.getRequiredBias())); writer.write(',');
                    writer.write(number(row.getRawCorrection())); writer.write(',');
                    writer.write(number(row.getProposedCorrection())); writer.write(',');
                    writer.write(number(row.getProposedLocalBias())); writer.write(",,,,,,,,,");
                    writer.write(Integer.toString(row.getEvidenceWindows())); writer.write(',');
                    writer.write(number(row.getCorrectionMad())); writer.write(',');
                    writer.write(number(row.getConfidencePercent())); writer.write(',');
                    writer.write(csv(row.getStatus())); writer.write(',');
                    writer.write(csv(row.getDetail())); writer.write('\n');
                }
            } finally { writer.close(); }
            stateValue.setText("Exported M4A CSV: " + file.getAbsolutePath());
        } catch (Exception ex) {
            stateValue.setText("M4A CSV export failed: " + safeMessage(ex));
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
        DcIacBiasSettingsAccess.Snapshot bias = biasSnapshot;
        if (bias == null) {
            configValue.setText("—");
            return;
        }
        String iLimits = bias.areIntegralLimitsKnown()
                ? value(bias.getITermMinimum()) + "…" + value(bias.getITermMaximum()) + "% I"
                : "I limits unavailable";
        configValue.setText(bias.size() + " bias knots / " + iLimits + " / no writes");
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && subscription != null;
        boolean running = sampler != null && sampler.isRunning();
        configurationCombo.setEnabled(connected && !running);
        refreshButton.setEnabled(connected && !running);
        startButton.setEnabled(connected && !running && configurationCombo.getItemCount() > 0);
        stopButton.setEnabled(running);
        clearButton.setEnabled(!running);
        exportButton.setEnabled(!evidenceCopy().isEmpty());
    }

    private static void addRow(JPanel panel, GridBagConstraints c, String label, JLabel value) {
        c.gridx = 0; c.weightx = 0.0; c.fill = GridBagConstraints.NONE;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1; c.weightx = 1.0; c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, c);
        c.gridy++;
    }

    private static String value(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "—";
        return String.format(Locale.US, "%.3f", value);
    }

    private static String number(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        return String.format(Locale.US, "%.9f", value);
    }

    private static String csv(String value) {
        if (value == null) return "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "Unknown error";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
