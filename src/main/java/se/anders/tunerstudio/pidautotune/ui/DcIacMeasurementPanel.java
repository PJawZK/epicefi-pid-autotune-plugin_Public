package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacEvent;
import se.anders.tunerstudio.pidautotune.live.DcIacEventExtractor;
import se.anders.tunerstudio.pidautotune.live.DcIacEventSettings;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurement;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurementEngine;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurementSettings;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.live.ProfileBackgroundSampler;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveSample;
import se.anders.tunerstudio.pidautotune.profile.DcIacPositionProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
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
import java.util.List;
import java.util.Locale;

/** Read-only DC-IAC M3 measurement workspace. */
public final class DcIacMeasurementPanel extends JPanel {
    private static final long SAMPLE_PERIOD_MS = 20L;

    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel stateValue = new JLabel("Not connected");
    private final JLabel readinessValue = new JLabel("Stopped");
    private final JLabel configValue = new JLabel("—");
    private final JLabel liveValue = new JLabel("—");
    private final JLabel summaryValue = new JLabel("No M3 measurements yet");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JButton refreshButton = new JButton("Refresh configurations");
    private final JButton startButton = new JButton("Start M3 capture");
    private final JButton stopButton = new JButton("Stop");
    private final JButton clearButton = new JButton("Clear measurements");
    private final JButton exportButton = new JButton("Export M3 CSV...");

    private final DefaultTableModel model = new DefaultTableModel(
            new Object[] {
                    "#", "Type", "M3", "Samples", "Hz", "From %", "To %", "Step %", "Center %",
                    "Pre span %", "Pre slope %/s", "Initial I", "Edge est %", "Quant", "Bias eq",
                    "Delay s", "Rise/Fall s", "Settle s", "Overshoot %step", "SS err %", "MAE %",
                    "Max err %", "Peak duty %", "Reversals", "Result", "Evidence flags", "Detail"
            }, 0) {
        @Override public boolean isCellEditable(int row, int column) { return false; }
    };
    private final JTable table = new JTable(model);
    private final JTextArea guidance = new JTextArea();
    private final Timer uiTimer;

    private final Object measurementsLock = new Object();
    private final List<DcIacMeasurement> measurements = new ArrayList<DcIacMeasurement>();
    private final DcIacEventExtractor extractor = new DcIacEventExtractor();
    private final DcIacMeasurementEngine measurementEngine = new DcIacMeasurementEngine();
    private volatile DcIacMeasurementSettings measurementSettings = new DcIacMeasurementSettings();

    private ControllerAccess controllerAccess;
    private ProfileLiveOutputSubscription subscription;
    private ProfileBackgroundSampler sampler;
    private DcIacSettingsAccess settingsAccess;
    private volatile DcIacEventSettings eventSettings;
    private volatile DcIacSettingsAccess.Snapshot settingsSnapshot;
    private volatile ProfileLiveSample latestSample;
    private volatile String latestReadiness = "Stopped";
    private volatile String latestTransition = "No M2 event yet";
    private volatile String samplerError = "";
    private volatile int observedEvents;
    private volatile int acceptedEvents;
    private volatile int rejectedEvents;
    private int extractorEventCount;
    private int displayedMeasurements;

    public DcIacMeasurementPanel() {
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
        reloadConfigurations();
        stateValue.setText("Connected — M3 capture stopped");
        updateControls();
    }

    public void disconnect() {
        stopCaptureInternal();
        subscription = null;
        settingsAccess = null;
        controllerAccess = null;
        eventSettings = null;
        settingsSnapshot = null;
        measurementSettings = new DcIacMeasurementSettings();
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
        JLabel title = new JLabel("DC IAC — M3 response measurement");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        JLabel safety = new JLabel("Read-only measurement/evidence grading; no gain/bias recommendations and no ECU writes");
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
        addRow(status, c, "M2 evidence readiness", readinessValue);
        addRow(status, c, "Configured DC-IAC travel / PID / optional shared pause", configValue);
        addRow(status, c, "Live target / actual / duty / VBatt", liveValue);
        addRow(status, c, "M3 summary", summaryValue);

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

        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {
                45, 105, 70, 65, 65, 70, 70, 70, 75,
                85, 105, 75, 85, 60, 65,
                75, 90, 75, 110, 80, 75, 80, 90, 75, 200, 260, 620
        };
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        add(new JScrollPane(table), BorderLayout.CENTER);

        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setRows(7);
        guidance.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        guidance.setText(
                "M3.3 measures only M2-accepted DC-IAC events and separates valid observation from quantitative identification evidence. Source timing/Hz come from distinct TunerStudio channel updates, not the 20 ms scheduler. " +
                "For local PID identification, prefer deliberate 3–4% target steps at one operating region and avoid crossing steep bias-curve segments. Steps above 4.5% remain useful context but are flagged STEP_TOO_LARGE_FOR_LOCAL_ID. " +
                "Firmware physically clamps DC-motor command to ±90%. When configured P/I/D are readable, M3.3 estimates the first command-edge P+D setpoint kick at the nominal 500 Hz motor loop. A predicted clamp hit is flagged even if the slower plugin stream never sampled the transient. Absence of the flag does not prove a hidden feed-forward transient could not occur. " +
                "Stable holds now track target slope, actual-position slope and I-term slope. A normal two-second M2 hold may become HOLD_EQUILIBRIUM_CANDIDATE only when all are settled; it is still candidate evidence, not final bias authority. Longer/repeated equilibrium evidence is required before any later bias recommendation. " +
                "Initial I term, step magnitude, operating center, observed feed-forward (duty minus PID output), predicted command-edge duty and evidence flags are exported. M4 must later use only dynamic_quantitative=true rows for gain math. No recommendation or ECU-write logic is enabled here.");
        add(guidance, BorderLayout.SOUTH);
    }

    private void wireActions() {
        refreshButton.addActionListener(event -> reloadConfigurations());
        startButton.addActionListener(event -> startCapture());
        stopButton.addActionListener(event -> stopCapture("Stopped by user"));
        clearButton.addActionListener(event -> clearMeasurements());
        exportButton.addActionListener(event -> exportCsv());
        configurationCombo.addActionListener(event -> {
            if (sampler != null && sampler.isRunning()) stopCapture("Configuration changed — restart M3 capture");
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
            DcIacSettingsAccess.Snapshot snapshot = settingsAccess.read(configuration);
            if (!(snapshot.getMaximumPosition() > snapshot.getMinimumPosition())) {
                stateValue.setText("Blocked — invalid configured DC-IAC travel limits");
                return;
            }
            DcIacEventSettings newEventSettings = new DcIacEventSettings(
                    snapshot.getMinimumPosition(), snapshot.getMaximumPosition());
            DcIacMeasurementSettings newMeasurementSettings = new DcIacMeasurementSettings(
                    snapshot.getPFactor(), snapshot.getIFactor(), snapshot.getDFactor());
            LiveSubscriptionReport report = subscription.start(configuration, DcIacPositionProfile.DEFINITION);
            if (!report.isUsable() || !subscription.isSubscribed("VBatt")) {
                subscription.stop();
                stateValue.setText(!report.isUsable()
                        ? "Blocked — required DC-IAC live mapping missing"
                        : "Blocked — VBatt is required for M3 measurement evidence");
                return;
            }

            settingsSnapshot = snapshot;
            eventSettings = newEventSettings;
            measurementSettings = newMeasurementSettings;
            extractor.reset();
            extractorEventCount = 0;
            observedEvents = 0;
            acceptedEvents = 0;
            rejectedEvents = 0;
            latestSample = null;
            latestReadiness = "Waiting for samples";
            latestTransition = "No M2 event yet";
            samplerError = "";
            clearMeasurements();
            updateConfigurationLabel();

            sampler = new ProfileBackgroundSampler(subscription, SAMPLE_PERIOD_MS);
            sampler.start(new ProfileBackgroundSampler.Listener() {
                @Override
                public void onSample(ProfileLiveSample sample) {
                    processBackgroundSample(sample);
                }

                @Override
                public void onError(Throwable error) {
                    samplerError = safeMessage(error);
                }
            });
            if (snapshot.isMotorControlPaused()) {
                stateValue.setText("M3 running — evidence blocked by shared motor-pause flag");
            } else if (!snapshot.arePidGainsKnown()) {
                stateValue.setText("M3 running — PID gains unavailable; saturation prediction cannot clear quantitative evidence");
            } else {
                stateValue.setText("M3.3 background capture running at nominal 50 Hz");
            }
            uiTimer.start();
        } catch (Exception ex) {
            if (subscription != null) subscription.stop();
            stateValue.setText("M3 start failed: " + safeMessage(ex));
        }
        updateControls();
    }

    private void processBackgroundSample(ProfileLiveSample sample) {
        DcIacEventSettings localSettings = eventSettings;
        DcIacSettingsAccess.Snapshot localSnapshot = settingsSnapshot;
        DcIacMeasurementSettings localMeasurementSettings = measurementSettings;
        if (sample == null || localSettings == null || localSnapshot == null || localMeasurementSettings == null) return;

        extractor.process(sample, localSettings, localSnapshot.isMotorControlPaused());
        latestSample = sample;
        latestReadiness = extractor.getReadiness();
        latestTransition = extractor.getLastTransition();

        List<DcIacEvent> events = extractor.getEvents();
        if (events.size() <= extractorEventCount) return;
        for (int i = extractorEventCount; i < events.size(); i++) {
            DcIacEvent event = events.get(i);
            observedEvents++;
            if (event.isAccepted()) {
                acceptedEvents++;
                DcIacMeasurement measurement = measurementEngine.measure(event, localMeasurementSettings);
                synchronized (measurementsLock) {
                    measurements.add(measurement);
                }
            } else {
                rejectedEvents++;
            }
        }
        extractorEventCount = events.size();
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
        extractor.stop();
    }

    private void refreshUi() {
        if (samplerError != null && !samplerError.isEmpty()) {
            stateValue.setText("M3 sampler error: " + samplerError);
        }
        readinessValue.setText(latestReadiness + " — " + latestTransition);
        ProfileLiveSample sample = latestSample;
        if (sample != null) {
            liveValue.setText("target " + value(sample.get("dcIdleTarget"))
                    + "% / actual " + value(sample.get("idlePositionSensor"))
                    + "% / duty " + value(sample.get("dcIdleDutyCycle"))
                    + "% / " + value(sample.get("VBatt")) + " V");
        }

        List<DcIacMeasurement> copy = measurementCopy();
        int valid = 0;
        int quantitative = 0;
        int biasCandidates = 0;
        for (DcIacMeasurement measurement : copy) {
            if (measurement.isValid()) valid++;
            if (measurement.isDynamicQuantitative()) quantitative++;
            if (measurement.isBiasEquilibriumCandidate()) biasCandidates++;
        }
        summaryValue.setText("M2 events " + observedEvents + " (accepted " + acceptedEvents
                + ", rejected " + rejectedEvents + ") / M3 measurements " + copy.size()
                + " (valid " + valid + ", quantitative steps " + quantitative
                + ", bias candidates " + biasCandidates + ", invalid " + (copy.size() - valid) + ")");
        refreshTable(copy);
        updateControls();
    }

    private void refreshTable(List<DcIacMeasurement> copy) {
        if (copy.size() == displayedMeasurements) return;
        model.setRowCount(0);
        for (DcIacMeasurement m : copy) {
            model.addRow(new Object[] {
                    Integer.valueOf(m.getEventSequence()),
                    type(m.getEventType()),
                    m.getValidity().name(),
                    Integer.valueOf(m.getSampleCount()),
                    value(m.getSampleRateHz()),
                    value(m.getFromTarget()),
                    value(m.getToTarget()),
                    value(m.getStepMagnitude()),
                    value(m.getOperatingCenterTarget()),
                    value(m.getPreStepActualSpan()),
                    value(m.getPreStepSlopePerSecond()),
                    value(m.getInitialITerm()),
                    value(m.getPredictedCommandEdgeDuty()),
                    yesNo(m.isDynamicQuantitative()),
                    yesNo(m.isBiasEquilibriumCandidate()),
                    value(m.getResponseDelaySeconds()),
                    value(m.getRiseOrFallSeconds()),
                    value(m.getSettlingSeconds()),
                    value(m.getOvershootPercentOfStep()),
                    value(m.getSteadyStateError()),
                    value(m.getMeanAbsoluteError()),
                    value(m.getMaximumAbsoluteError()),
                    value(m.getPeakAbsoluteDuty()),
                    Integer.valueOf(m.getDutyReversals()),
                    m.getResultCode(),
                    m.getEvidenceFlags(),
                    m.getDetail()
            });
        }
        displayedMeasurements = copy.size();
        if (displayedMeasurements > 0) {
            int row = displayedMeasurements - 1;
            table.scrollRectToVisible(table.getCellRect(row, 0, true));
        }
    }

    private void refreshConfigurationSnapshot() {
        String configuration = (String) configurationCombo.getSelectedItem();
        if (configuration == null || settingsAccess == null || settingsSnapshot == null) return;
        try {
            DcIacSettingsAccess.Snapshot updated = settingsAccess.read(configuration);
            boolean limitsChanged = Math.abs(updated.getMinimumPosition() - settingsSnapshot.getMinimumPosition()) > 0.0001
                    || Math.abs(updated.getMaximumPosition() - settingsSnapshot.getMaximumPosition()) > 0.0001;
            settingsSnapshot = updated;
            measurementSettings = new DcIacMeasurementSettings(
                    updated.getPFactor(), updated.getIFactor(), updated.getDFactor());
            if (limitsChanged) {
                eventSettings = new DcIacEventSettings(updated.getMinimumPosition(), updated.getMaximumPosition());
                extractor.stop();
                stateValue.setText("M3 running — travel limits changed; M2 baseline restarted");
            }
            updateConfigurationLabel();
        } catch (Exception ex) {
            stateValue.setText("Settings refresh failed: " + safeMessage(ex));
        }
    }

    private void clearMeasurements() {
        synchronized (measurementsLock) {
            measurements.clear();
        }
        displayedMeasurements = 0;
        model.setRowCount(0);
        summaryValue.setText("No M3 measurements yet");
    }

    private List<DcIacMeasurement> measurementCopy() {
        synchronized (measurementsLock) {
            return new ArrayList<DcIacMeasurement>(measurements);
        }
    }

    private void exportCsv() {
        List<DcIacMeasurement> copy = measurementCopy();
        if (copy.isEmpty()) {
            stateValue.setText("Nothing to export — no M3 measurements captured");
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export DC-IAC M3 measurements");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files", "csv"));
        chooser.setSelectedFile(new File("dc-iac-m3-measurements.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.US).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        try {
            BufferedWriter writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8);
            try {
                writer.write("event_sequence,event_type,validity,result_code,sample_count,duration_s,from_target_pct,to_target_pct,sample_rate_hz,prestep_actual_span_pct,prestep_slope_pct_per_s,initial_actual_pct,final_actual_pct,response_delay_s,rise_fall_10_90_s,settling_s,overshoot_pct_of_step,steady_state_error_pct,mean_absolute_error_pct,maximum_absolute_error_pct,peak_absolute_duty_pct,duty_reversals,p_term_span,i_term_span,d_term_span,battery_spread_v,actual_jitter_stddev_pct,duty_stddev_pct,mean_pid_output,mean_i_term,step_magnitude_pct,operating_center_target_pct,initial_i_term,target_slope_pct_per_s,actual_slope_pct_per_s,i_term_slope_per_s,mean_observed_feed_forward_pct,predicted_command_edge_duty_pct,predicted_saturation_risk,dynamic_quantitative,bias_equilibrium_candidate,evidence_flags,detail\n");
                for (DcIacMeasurement m : copy) {
                    writer.write(Integer.toString(m.getEventSequence())); writer.write(',');
                    writer.write(m.getEventType().name()); writer.write(',');
                    writer.write(m.getValidity().name()); writer.write(',');
                    writer.write(csv(m.getResultCode())); writer.write(',');
                    writer.write(Integer.toString(m.getSampleCount())); writer.write(',');
                    writer.write(number(m.getDurationSeconds())); writer.write(',');
                    writer.write(number(m.getFromTarget())); writer.write(',');
                    writer.write(number(m.getToTarget())); writer.write(',');
                    writer.write(number(m.getSampleRateHz())); writer.write(',');
                    writer.write(number(m.getPreStepActualSpan())); writer.write(',');
                    writer.write(number(m.getPreStepSlopePerSecond())); writer.write(',');
                    writer.write(number(m.getInitialActual())); writer.write(',');
                    writer.write(number(m.getFinalActual())); writer.write(',');
                    writer.write(number(m.getResponseDelaySeconds())); writer.write(',');
                    writer.write(number(m.getRiseOrFallSeconds())); writer.write(',');
                    writer.write(number(m.getSettlingSeconds())); writer.write(',');
                    writer.write(number(m.getOvershootPercentOfStep())); writer.write(',');
                    writer.write(number(m.getSteadyStateError())); writer.write(',');
                    writer.write(number(m.getMeanAbsoluteError())); writer.write(',');
                    writer.write(number(m.getMaximumAbsoluteError())); writer.write(',');
                    writer.write(number(m.getPeakAbsoluteDuty())); writer.write(',');
                    writer.write(Integer.toString(m.getDutyReversals())); writer.write(',');
                    writer.write(number(m.getPTermSpan())); writer.write(',');
                    writer.write(number(m.getITermSpan())); writer.write(',');
                    writer.write(number(m.getDTermSpan())); writer.write(',');
                    writer.write(number(m.getBatterySpread())); writer.write(',');
                    writer.write(number(m.getActualJitterStdDev())); writer.write(',');
                    writer.write(number(m.getDutyStdDev())); writer.write(',');
                    writer.write(number(m.getMeanPidOutput())); writer.write(',');
                    writer.write(number(m.getMeanITerm())); writer.write(',');
                    writer.write(number(m.getStepMagnitude())); writer.write(',');
                    writer.write(number(m.getOperatingCenterTarget())); writer.write(',');
                    writer.write(number(m.getInitialITerm())); writer.write(',');
                    writer.write(number(m.getTargetSlopePerSecond())); writer.write(',');
                    writer.write(number(m.getActualSlopePerSecond())); writer.write(',');
                    writer.write(number(m.getITermSlopePerSecond())); writer.write(',');
                    writer.write(number(m.getMeanObservedFeedForward())); writer.write(',');
                    writer.write(number(m.getPredictedCommandEdgeDuty())); writer.write(',');
                    writer.write(Boolean.toString(m.isPredictedSaturationRisk())); writer.write(',');
                    writer.write(Boolean.toString(m.isDynamicQuantitative())); writer.write(',');
                    writer.write(Boolean.toString(m.isBiasEquilibriumCandidate())); writer.write(',');
                    writer.write(csv(m.getEvidenceFlags())); writer.write(',');
                    writer.write(csv(m.getDetail())); writer.write('\n');
                }
            } finally {
                writer.close();
            }
            stateValue.setText("Exported M3 CSV: " + file.getAbsolutePath());
        } catch (Exception ex) {
            stateValue.setText("M3 CSV export failed: " + safeMessage(ex));
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
        DcIacSettingsAccess.Snapshot snapshot = settingsSnapshot;
        if (snapshot == null) {
            configValue.setText("—");
            return;
        }
        String pause = snapshot.isMotorControlPauseKnown()
                ? String.valueOf(snapshot.isMotorControlPaused())
                : "unavailable (not required)";
        String pid = snapshot.arePidGainsKnown()
                ? "P=" + value(snapshot.getPFactor()) + " I=" + value(snapshot.getIFactor()) + " D=" + value(snapshot.getDFactor())
                : "P/I/D unavailable";
        configValue.setText(value(snapshot.getMinimumPosition()) + "–" + value(snapshot.getMaximumPosition())
                + "% / " + pid + " / motor limit ±90% / shared pause=" + pause);
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && subscription != null;
        boolean running = sampler != null && sampler.isRunning();
        configurationCombo.setEnabled(connected && !running);
        refreshButton.setEnabled(connected && !running);
        startButton.setEnabled(connected && !running && configurationCombo.getItemCount() > 0);
        stopButton.setEnabled(running);
        clearButton.setEnabled(!running);
        exportButton.setEnabled(!measurementCopy().isEmpty());
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

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
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
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private static String safeMessage(Throwable error) {
        if (error == null) return "Unknown error";
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
