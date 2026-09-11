package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import se.anders.tunerstudio.pidautotune.controller.IdlePidRamWriteCoordinator;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveAttempt;
import se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureSettings;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureState;
import se.anders.tunerstudio.pidautotune.live.LiveChannel;
import se.anders.tunerstudio.pidautotune.live.LiveComparisonResult;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendationEngine;
import se.anders.tunerstudio.pidautotune.live.LiveGuidedCaptureEngine;
import se.anders.tunerstudio.pidautotune.live.LiveMotionMode;
import se.anders.tunerstudio.pidautotune.live.LiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.LiveReadiness;
import se.anders.tunerstudio.pidautotune.live.LiveSample;
import se.anders.tunerstudio.pidautotune.live.LiveSessionSummary;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Guided autonomous RAM-only Idle Closed Loop PID tuner.
 *
 * This panel intentionally has no Burn/persist operation. It reuses the existing live-capture,
 * evidence, recommendation, and comparison engines as the authority. Only the audited P/I/D
 * controller parameters are ever written, one gain at a time, with pre-write conflict detection,
 * definition-aware range/precision checks, exact readback, and verified rollback.
 */
public final class AutonomousIdleTuningPanel extends JPanel {
    private static final Font TITLE = new Font("Dialog", Font.BOLD, 20);
    private static final Font H2 = new Font("Dialog", Font.BOLD, 13);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 11);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 9);
    private static final int SAMPLE_PERIOD_MS = 50;
    private static final int DEFAULT_MAX_WRITES = 8;
    private static final int MAX_COMPARISON_ATTEMPTS = 4;

    enum Stage {
        STOPPED("Stopped"),
        BASELINE("Capture baseline"),
        CANDIDATE("Test RAM candidate"),
        COMPLETE("Complete"),
        RESTORED("Original gains restored"),
        ERROR("Stopped after error");
        final String label;
        Stage(String label) { this.label = label; }
    }

    private final LiveGuidedCaptureEngine captureEngine = new LiveGuidedCaptureEngine();
    private final LiveExperimentalRecommendationEngine recommendationEngine = new LiveExperimentalRecommendationEngine();
    private final Timer sampleTimer;

    private final JLabel stageValue = new JLabel("Stopped");
    private final JLabel instructionTitle = new JLabel("CONNECT AND ARM RAM TUNING");
    private final JTextArea instructionBody = readOnlyArea(3);
    private final JLabel writeStatus = new JLabel("No RAM write active");
    private final JLabel liveRpm = new JLabel("RPM —");
    private final JLabel liveTarget = new JLabel("Target —");
    private final JLabel liveError = new JLabel("Error —");
    private final JLabel livePosition = new JLabel("Idle Pos —");
    private final JLabel liveCapture = new JLabel("Capture Stopped");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JComboBox<LiveCaptureProfile> profileCombo = new JComboBox<LiveCaptureProfile>(
            new LiveCaptureProfile[]{LiveCaptureProfile.INITIAL_ROUGH, LiveCaptureProfile.STANDARD, LiveCaptureProfile.FINE});
    private final JComboBox<LiveMotionMode> motionModeCombo = new JComboBox<LiveMotionMode>(LiveMotionMode.values());
    private final JCheckBox stationaryConfirmation = new JCheckBox("Vehicle will remain stationary for this session");
    private final JCheckBox armWrites = new JCheckBox("Enable audited RAM-only Idle P / I / D writes — never Burn");
    private final JTextField minimumCltField = new JTextField("70", 5);
    private final JTextField maximumCltField = new JTextField("110", 5);
    private final JTextField revMinimumField = new JTextField("1700", 5);
    private final JTextField revMaximumField = new JTextField("2300", 5);
    private final JTextField maxWritesField = new JTextField(String.valueOf(DEFAULT_MAX_WRITES), 4);

    private final JLabel originalGains = new JLabel("—");
    private final JLabel bestGains = new JLabel("—");
    private final JLabel candidateGains = new JLabel("—");
    private final JLabel candidateCount = new JLabel("0 / " + DEFAULT_MAX_WRITES);
    private final JLabel attemptCount = new JLabel("0 accepted / 0 rejected");
    private final JLabel comparisonState = new JLabel("—");
    private final JLabel recommendationState = new JLabel("—");
    private final JTextArea readinessText = readOnlyArea(9);
    private final JTextArea historyText = readOnlyArea(12);
    private final JScrollPane readinessScroll = new JScrollPane(readinessText);
    private final JScrollPane historyScroll = new JScrollPane(historyText);

    private final JButton startButton = new JButton("Start Autonomous Tuning");
    private final JButton stopKeepButton = new JButton("Stop + Keep Current RAM");
    private final JButton restoreOriginalButton = new JButton("Restore Original P/I/D");

    private final JPanel headerCard = new JPanel(new BorderLayout(10, 0));
    private final JPanel heroCard = new JPanel(new BorderLayout(10, 8));
    private final JPanel liveStrip = new JPanel(new GridLayout(1, 5, 6, 0));
    private final JPanel setupCard = new JPanel();
    private final JPanel stateCard = new JPanel();
    private final JPanel footer = new JPanel(new BorderLayout(8, 0));
    private final JLabel footerText = new JLabel("Next: connect TunerStudio and arm the audited RAM-only write path.");

    private ControllerAccess controllerAccess;
    private ControllerParameterServer parameterServer;
    private IdlePidRamWriteCoordinator writeCoordinator;
    private LiveOutputSubscription subscription;
    private String activeConfiguration;
    private LiveCaptureSettings activeSettings;
    private Stage stage = Stage.STOPPED;
    private TuneSnapshot originalSnapshot;
    private TuneSnapshot bestSnapshot;
    private TuneSnapshot currentSnapshot;
    private LiveBaselineSnapshot baseline;
    private LiveExperimentalRecommendation recommendation;
    private LiveComparisonResult comparison;
    private LiveSample latestSample;
    private int lastAttemptCount;
    private int writeCount;
    private int maxWrites = DEFAULT_MAX_WRITES;
    private String lastGain = "";
    private double lastPercent;
    private boolean halfStepUsed;
    private boolean running;

    public AutonomousIdleTuningPanel() {
        super(new BorderLayout(0, 8));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        buildUi();
        wireActions();
        sampleTimer = new Timer(SAMPLE_PERIOD_MS, e -> sampleLiveValues());
        sampleTimer.setCoalesce(true);
        profileCombo.setSelectedItem(LiveCaptureProfile.INITIAL_ROUGH);
        motionModeCombo.setSelectedItem(LiveMotionMode.USE_LIVE_VSS);
        instructionBody.setText("The plugin will collect a qualified baseline, calculate one conservative gain change, write it to ECU RAM, verify readback, ask you for the same rev-and-release maneuver, compare the result, then keep or roll back automatically. Nothing is burned.");
        applyTheme();
        updateControls();
    }

    public void connect(ControllerAccess access) {
        if (running) stopAndRestoreBest("Controller connection changed while autonomous tuning was active.");
        controllerAccess = access;
        parameterServer = access == null ? null : access.getControllerParameterServer();
        writeCoordinator = parameterServer == null ? null : new IdlePidRamWriteCoordinator(parameterServer);
        reloadConfigurations();
        appendHistory(access == null ? "TunerStudio controller API unavailable." : "TunerStudio controller API connected.");
        refreshUi();
    }

    public void disconnect() {
        if (running) stopAndRestoreBest("TunerStudio disconnected; best-effort RAM rollback was attempted before release.");
        else stopSampling();
        controllerAccess = null;
        parameterServer = null;
        writeCoordinator = null;
        configurationCombo.removeAllItems();
        refreshUi();
    }

    public boolean isRunning() { return running; }
    public String getStageLabel() { return stage.label; }
    public TuneSnapshot getOriginalSnapshot() { return originalSnapshot; }
    public TuneSnapshot getBestSnapshot() { return bestSnapshot; }
    public TuneSnapshot getCurrentSnapshot() { return currentSnapshot; }
    public int getWriteCount() { return writeCount; }

    private void buildUi() {
        JPanel titleBox = new JPanel(new GridLayout(0, 1, 0, 1));
        titleBox.setOpaque(false);
        JLabel title = new JLabel("Idle Closed Loop — Autonomous RAM Tuning");
        title.setFont(TITLE);
        JLabel sub = new JLabel("Same evidence authority, but candidate apply/readback/rollback are automated. No Burn.");
        sub.setFont(SMALL);
        titleBox.add(title); titleBox.add(sub);
        titleBox.putClientProperty("title", title);
        titleBox.putClientProperty("sub", sub);
        stageValue.setFont(H2);
        headerCard.add(titleBox, BorderLayout.WEST);
        headerCard.add(stageValue, BorderLayout.EAST);
        headerCard.setBorder(new EmptyBorder(2, 4, 2, 4));
        add(headerCard, BorderLayout.NORTH);

        instructionTitle.setFont(new Font("Dialog", Font.BOLD, 16));
        instructionBody.setFont(BODY);
        JPanel heroText = new JPanel(new BorderLayout(0, 4)); heroText.setOpaque(false);
        heroText.add(instructionTitle, BorderLayout.NORTH); heroText.add(instructionBody, BorderLayout.CENTER);
        writeStatus.setFont(new Font("Dialog", Font.BOLD, 10));
        heroCard.add(heroText, BorderLayout.CENTER);
        heroCard.add(writeStatus, BorderLayout.SOUTH);
        heroCard.setBorder(compoundCardBorder());

        liveRpm.setHorizontalAlignment(JLabel.CENTER);
        liveTarget.setHorizontalAlignment(JLabel.CENTER);
        liveError.setHorizontalAlignment(JLabel.CENTER);
        livePosition.setHorizontalAlignment(JLabel.CENTER);
        liveCapture.setHorizontalAlignment(JLabel.CENTER);
        liveStrip.add(metricCell(liveRpm)); liveStrip.add(metricCell(liveTarget)); liveStrip.add(metricCell(liveError));
        liveStrip.add(metricCell(livePosition)); liveStrip.add(metricCell(liveCapture));
        liveStrip.setBorder(new EmptyBorder(0, 0, 0, 0));

        setupCard.setLayout(new BoxLayout(setupCard, BoxLayout.Y_AXIS));
        setupCard.setBorder(compoundCardBorder());
        setupCard.add(sectionTitle("Setup"));
        setupCard.add(fieldRow("ECU configuration", configurationCombo));
        setupCard.add(fieldRow("Capture profile", profileCombo));
        setupCard.add(fieldRow("Stationary basis", motionModeCombo));
        setupCard.add(fieldRow("Minimum CLT °C", minimumCltField));
        setupCard.add(fieldRow("Maximum CLT °C", maximumCltField));
        setupCard.add(fieldRow("Preferred rev min", revMinimumField));
        setupCard.add(fieldRow("Preferred rev max", revMaximumField));
        setupCard.add(fieldRow("Maximum candidate writes", maxWritesField));
        setupCard.add(Box.createVerticalStrut(4));
        setupCard.add(stationaryConfirmation);
        setupCard.add(Box.createVerticalStrut(3));
        setupCard.add(armWrites);

        stateCard.setLayout(new BoxLayout(stateCard, BoxLayout.Y_AXIS));
        stateCard.setBorder(compoundCardBorder());
        stateCard.add(sectionTitle("Verified tuning state"));
        stateCard.add(valueRow("Original gains", originalGains));
        stateCard.add(valueRow("Best verified RAM gains", bestGains));
        stateCard.add(valueRow("Candidate", candidateGains));
        stateCard.add(valueRow("Candidate writes", candidateCount));
        stateCard.add(valueRow("Current capture", attemptCount));
        stateCard.add(valueRow("Recommendation", recommendationState));
        stateCard.add(valueRow("Comparison", comparisonState));

        JPanel right = new JPanel(new BorderLayout(0, 8)); right.setOpaque(false);
        right.add(setupCard, BorderLayout.NORTH); right.add(stateCard, BorderLayout.CENTER);
        right.setPreferredSize(new Dimension(330, 520));

        readinessText.setBorder(new EmptyBorder(6, 6, 6, 6));
        historyText.setBorder(new EmptyBorder(6, 6, 6, 6));
        StableScrollSupport.install(readinessScroll);
        StableScrollSupport.install(historyScroll);
        JSplitPane evidenceSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, readinessScroll, historyScroll);
        evidenceSplit.setResizeWeight(0.42); evidenceSplit.setDividerLocation(220); evidenceSplit.setBorder(null);

        JPanel centerLeft = new JPanel(new BorderLayout(0, 8)); centerLeft.setOpaque(false);
        centerLeft.add(heroCard, BorderLayout.NORTH);
        centerLeft.add(evidenceSplit, BorderLayout.CENTER);
        centerLeft.add(liveStrip, BorderLayout.SOUTH);

        JSplitPane bodySplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerLeft, right);
        bodySplit.setResizeWeight(1.0); bodySplit.setDividerLocation(690); bodySplit.setDividerSize(7); bodySplit.setBorder(null);
        add(bodySplit, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 7, 0)); buttons.setOpaque(false);
        buttons.add(restoreOriginalButton); buttons.add(stopKeepButton); buttons.add(startButton);
        footerText.setFont(new Font("Dialog", Font.BOLD, 10));
        footer.add(footerText, BorderLayout.CENTER); footer.add(buttons, BorderLayout.EAST);
        footer.setBorder(new EmptyBorder(5, 4, 1, 4));
        add(footer, BorderLayout.SOUTH);
        StableScrollSupport.installRecursively(this);
    }

    private void wireActions() {
        startButton.addActionListener(e -> startAutonomousTuning());
        stopKeepButton.addActionListener(e -> stopKeepCurrent("Stopped by user; current verified RAM gains retained."));
        restoreOriginalButton.addActionListener(e -> restoreOriginalFromUi());
        motionModeCombo.addActionListener(e -> updateControls());
        armWrites.addActionListener(e -> {
            if (running && !armWrites.isSelected()) stopAndRestoreBest("RAM-write authorization was removed during the session.");
            updateControls();
        });
    }

    private void startAutonomousTuning() {
        if (running) return;
        try {
            if (controllerAccess == null || parameterServer == null || writeCoordinator == null) {
                throw new IllegalStateException("TunerStudio controller API is not connected.");
            }
            if (!armWrites.isSelected()) throw new IllegalStateException("Enable audited RAM-only Idle P/I/D writes first.");
            LiveMotionMode mode = selectedMotionMode();
            if (mode == LiveMotionMode.MANUAL_STATIONARY && !stationaryConfirmation.isSelected()) {
                throw new IllegalStateException("Manual stationary mode requires the stationary confirmation.");
            }

            activeConfiguration = selectedConfiguration();
            maxWrites = parseInt(maxWritesField, "Maximum candidate writes", 1, 20);
            activeSettings = buildSettings(activeConfiguration);
            originalSnapshot = writeCoordinator.readGains(activeConfiguration, "Autonomous-session original gains");
            bestSnapshot = originalSnapshot;
            currentSnapshot = originalSnapshot;
            baseline = null; recommendation = null; comparison = null; latestSample = null;
            writeCount = 0; lastAttemptCount = 0; halfStepUsed = false; lastGain = ""; lastPercent = 0.0;

            subscription = new LiveOutputSubscription(controllerAccess);
            LiveSubscriptionReport report = subscription.start(activeConfiguration);
            if (!report.isUsable()) {
                subscription.stop(); subscription = null;
                throw new IllegalStateException("Required live channels missing: " + join(report.getMissingRequired()));
            }

            captureEngine.resetSession(); captureEngine.start();
            stage = Stage.BASELINE; running = true; sampleTimer.start();
            appendHistory("Autonomous session started on " + activeConfiguration + ". Original " + originalSnapshot.toSignature() + ".");
            if (!report.getMissingOptional().isEmpty()) appendHistory("Optional channels unavailable: " + join(report.getMissingOptional()) + ".");
            appendHistory("Write authority is limited to idleRpmPid_pFactor / iFactor / dFactor. No Burn operation exists here.");
            refreshUi();
        } catch (Exception ex) {
            failStart(ex);
        }
    }

    private LiveCaptureSettings buildSettings(String configurationName) throws Exception {
        double tpsThreshold = requiredScalar(configurationName, "idlePidDeactivationTpsThreshold");
        double maxIdleVss = requiredScalar(configurationName, "maxIdleVss");
        double rpmUpperLimit = requiredScalar(configurationName, "idlePidRpmUpperLimit");
        double rpmDeadZone = requiredScalar(configurationName, "idlePidRpmDeadZone");
        double correctionMinimum = requiredScalar(configurationName, "idleRpmPid_minValue");
        double correctionMaximum = requiredScalar(configurationName, "idleRpmPid_maxValue");
        double minClt = parseDouble(minimumCltField, "Minimum CLT");
        double maxClt = parseDouble(maximumCltField, "Maximum CLT");
        double revMin = parseDouble(revMinimumField, "Preferred rev minimum");
        double revMax = parseDouble(revMaximumField, "Preferred rev maximum");
        if (minClt >= maxClt) throw new IllegalArgumentException("Minimum CLT must be below maximum CLT.");
        if (revMin < 1000.0 || revMax <= revMin) throw new IllegalArgumentException("Preferred rev range is invalid.");
        LiveCaptureProfile profile = selectedProfile();
        LiveMotionMode motion = selectedMotionMode();
        return new LiveCaptureSettings(tpsThreshold, maxIdleVss, rpmUpperLimit, rpmDeadZone,
                correctionMinimum, correctionMaximum, minClt, maxClt, revMin, revMax,
                profile.getBaselineBandRpm(), profile.getSettlingBandRpm(), profile.getExitHysteresisRpm(),
                profile.getObservationSeconds(), profile.getRecoveryTimeoutSeconds(), profile, motion,
                stationaryConfirmation.isSelected());
    }

    private void sampleLiveValues() {
        if (!running || subscription == null || activeSettings == null) return;
        try {
            latestSample = subscription.snapshot();
            captureEngine.process(latestSample, activeSettings, System.nanoTime());
            ensureGainsStillExpected();
            processCompletedAttempts();
            refreshUi();
        } catch (Exception ex) {
            handleFatalSessionError("Autonomous live cycle failed", ex);
        }
    }

    private void ensureGainsStillExpected() throws Exception {
        if (writeCoordinator == null || currentSnapshot == null || !currentSnapshot.isComplete()) return;
        if (!writeCoordinator.matchesCurrent(activeConfiguration, currentSnapshot)) {
            TuneSnapshot actual = writeCoordinator.readGains(activeConfiguration, "Unexpected external change");
            throw new IllegalStateException("Idle PID gains changed outside the autonomous tuner. Expected "
                    + currentSnapshot.toSignature() + " but ECU now reports " + actual.toSignature() + ".");
        }
    }

    private void processCompletedAttempts() throws Exception {
        List<LiveAttempt> attempts = captureEngine.getAttempts();
        if (attempts.size() <= lastAttemptCount) return;
        for (int i = lastAttemptCount; i < attempts.size(); i++) {
            LiveAttempt a = attempts.get(i);
            appendHistory(a.isAccepted()
                    ? "Attempt " + a.getNumber() + " accepted — " + a.getQuality() + "; settle " + one(a.getSettlingSeconds()) + " s; MAE " + one(a.getMeanAbsoluteError()) + " RPM."
                    : "Attempt " + a.getNumber() + " rejected [" + a.getResultCode() + "] — " + a.getReason());
        }
        lastAttemptCount = attempts.size();
        if (stage == Stage.BASELINE) evaluateBaseline();
        else if (stage == Stage.CANDIDATE) evaluateCandidate();
    }

    private void evaluateBaseline() throws Exception {
        currentSnapshot = writeCoordinator.readGains(activeConfiguration, "Baseline readback");
        bestSnapshot = currentSnapshot;
        recommendation = recommendationEngine.evaluate(captureEngine.getAttempts(), currentSnapshot);
        if (LiveExperimentalRecommendation.CANDIDATE.equals(recommendation.getStatus())) {
            baseline = new LiveBaselineSnapshot(currentSnapshot, recommendation.getSessionSummary(),
                    activeSettings.getCaptureProfile(), activeSettings.getMotionMode(),
                    "Autonomous baseline before candidate " + (writeCount + 1));
            halfStepUsed = false;
            applyRecommendation(recommendation.getChangedGain(), recommendation.getChangePercent(), recommendation.getReason());
        } else if (LiveExperimentalRecommendation.NO_CHANGE.equals(recommendation.getStatus())) {
            stopKeepCurrent("No further one-gain change is supported by the qualified baseline evidence.");
        }
    }

    private void evaluateCandidate() throws Exception {
        LiveSessionSummary summary = LiveSessionSummary.from(captureEngine.getAttempts());
        comparison = recommendationEngine.compare(baseline, summary, currentSnapshot,
                activeSettings.getCaptureProfile(), activeSettings.getMotionMode());

        if (LiveComparisonResult.IMPROVED.equals(comparison.getStatus())) {
            appendHistory("Candidate improved the measured response" + scoreSuffix(comparison) + ". Promoting verified RAM gains.");
            bestSnapshot = currentSnapshot;
            baseline = new LiveBaselineSnapshot(bestSnapshot, summary,
                    activeSettings.getCaptureProfile(), activeSettings.getMotionMode(),
                    "Promoted autonomous baseline after candidate " + writeCount);
            halfStepUsed = false;
            if (writeCount >= maxWrites) {
                stopKeepCurrent("Maximum candidate-write count reached; best verified RAM gains retained.");
                return;
            }
            recommendation = recommendationEngine.evaluate(captureEngine.getAttempts(), bestSnapshot);
            if (LiveExperimentalRecommendation.CANDIDATE.equals(recommendation.getStatus())) {
                applyRecommendation(recommendation.getChangedGain(), recommendation.getChangePercent(), recommendation.getReason());
            } else if (LiveExperimentalRecommendation.NO_CHANGE.equals(recommendation.getStatus())) {
                stopKeepCurrent("Converged: no further one-gain change is supported.");
            } else {
                beginFreshBaseline("Promoted gains need more repeated evidence before another candidate.");
            }
            return;
        }

        if (LiveComparisonResult.WORSE.equals(comparison.getStatus())) {
            appendHistory("Candidate was worse" + scoreSuffix(comparison) + ". Restoring previous verified baseline.");
            restoreBestBaseline();
            if (!halfStepUsed && Math.abs(lastPercent) >= 4.0 && writeCount < maxWrites) {
                halfStepUsed = true;
                double smaller = lastPercent * 0.5;
                appendHistory("Trying one smaller step for " + lastGain + ": " + signed(smaller) + "%.");
                applyScaledCandidate(bestSnapshot, lastGain, smaller, "Half-step retry after worse first candidate");
            } else {
                stopKeepCurrent("Worse candidate rejected; previous best baseline restored and retained in RAM.");
            }
            return;
        }

        if (LiveComparisonResult.INCOMPATIBLE.equals(comparison.getStatus())
                || LiveComparisonResult.SAME_GAINS.equals(comparison.getStatus())) {
            restoreBestBaseline();
            stopKeepCurrent("Candidate test could not be used: " + comparison.getSummary() + " Previous best restored.");
            return;
        }

        if ((LiveComparisonResult.MIXED.equals(comparison.getStatus())
                || LiveComparisonResult.INCONCLUSIVE.equals(comparison.getStatus()))
                && summary.getAcceptedCount() >= MAX_COMPARISON_ATTEMPTS) {
            appendHistory("Candidate remained " + comparison.getStatus() + " after " + summary.getAcceptedCount()
                    + " accepted attempts. Restoring previous best rather than guessing.");
            restoreBestBaseline();
            stopKeepCurrent("More evidence is needed before another candidate; previous best retained in RAM.");
        }
    }

    private void applyRecommendation(String gain, double percent, String reason) throws Exception {
        if (writeCount >= maxWrites) { stopKeepCurrent("Maximum candidate-write count reached."); return; }
        lastGain = gain == null ? "" : gain.trim(); lastPercent = percent;
        applyScaledCandidate(bestSnapshot, lastGain, percent, reason);
    }

    private void applyScaledCandidate(TuneSnapshot base, String gain, double percent, String reason) throws Exception {
        if (gain == null || gain.isEmpty() || "None".equals(gain)) { stopKeepCurrent("No actionable single gain was identified."); return; }
        if (!armWrites.isSelected()) throw new IllegalStateException("RAM-write authorization is no longer enabled.");
        TuneSnapshot proposed = scaledCandidate(base, gain, percent);
        candidateGains.setText(gain + " " + signed(percent) + "% → " + proposed.toSignature());
        appendHistory("Candidate " + (writeCount + 1) + ": " + gain + " " + signed(percent) + "% from "
                + base.toSignature() + ". " + safe(reason));
        IdlePidRamWriteCoordinator.WriteResult result = writeCoordinator.applySingleGainCandidate(activeConfiguration, base, proposed);
        currentSnapshot = result.getAfter();
        writeCount++;
        appendHistory(result.getMessage() + " Verified readback: " + currentSnapshot.toSignature() + ".");
        stage = Stage.CANDIDATE;
        captureEngine.resetSession(); captureEngine.start(); lastAttemptCount = 0; comparison = null;
        refreshUi();
    }

    private void beginFreshBaseline(String why) {
        currentSnapshot = bestSnapshot;
        stage = Stage.BASELINE; captureEngine.resetSession(); captureEngine.start(); lastAttemptCount = 0;
        appendHistory(why); refreshUi();
    }

    private void restoreBestBaseline() throws Exception {
        TuneSnapshot target = baseline != null && baseline.getGains() != null && baseline.getGains().isComplete()
                ? baseline.getGains() : bestSnapshot;
        if (target == null || !target.isComplete()) return;
        IdlePidRamWriteCoordinator.WriteResult result = writeCoordinator.restore(activeConfiguration, target);
        currentSnapshot = result.getAfter(); bestSnapshot = currentSnapshot;
        appendHistory(result.getMessage() + " Best-baseline readback: " + currentSnapshot.toSignature() + ".");
    }

    private void restoreOriginalFromUi() {
        if (originalSnapshot == null || !originalSnapshot.isComplete()) { appendHistory("No original P/I/D snapshot is available."); return; }
        try {
            if (writeCoordinator == null || activeConfiguration == null) throw new IllegalStateException("No active controller configuration is available.");
            stopSampling(); running = false;
            IdlePidRamWriteCoordinator.WriteResult result = writeCoordinator.restore(activeConfiguration, originalSnapshot);
            currentSnapshot = result.getAfter(); bestSnapshot = currentSnapshot; stage = Stage.RESTORED;
            appendHistory(result.getMessage() + " Original readback: " + currentSnapshot.toSignature() + ".");
        } catch (Exception ex) {
            stage = Stage.ERROR; appendHistory("Original-gain restore failed: " + message(ex));
        }
        refreshUi();
    }

    private void stopAndRestoreBest(String reason) {
        try {
            if (writeCoordinator != null && activeConfiguration != null && bestSnapshot != null && bestSnapshot.isComplete()) {
                IdlePidRamWriteCoordinator.WriteResult result = writeCoordinator.restore(activeConfiguration, bestSnapshot);
                currentSnapshot = result.getAfter();
                appendHistory("Safety rollback: " + currentSnapshot.toSignature() + ".");
            }
        } catch (Exception ex) {
            appendHistory("Safety rollback could not be verified: " + message(ex));
            stage = Stage.ERROR;
        } finally {
            stopSampling(); running = false; appendHistory(reason);
            if (stage != Stage.ERROR) stage = Stage.COMPLETE;
            refreshUi();
        }
    }

    private void stopKeepCurrent(String reason) {
        stopSampling(); running = false;
        if (stage != Stage.ERROR && stage != Stage.RESTORED) stage = Stage.COMPLETE;
        appendHistory(reason + " No Burn command was sent."); refreshUi();
    }

    private void handleFatalSessionError(String context, Exception ex) {
        appendHistory(context + ": " + message(ex));
        try {
            if (writeCoordinator != null && activeConfiguration != null && bestSnapshot != null && bestSnapshot.isComplete()) {
                IdlePidRamWriteCoordinator.WriteResult result = writeCoordinator.restore(activeConfiguration, bestSnapshot);
                currentSnapshot = result.getAfter(); appendHistory("Safety rollback restored best verified gains: " + currentSnapshot.toSignature() + ".");
            }
        } catch (Exception rollbackFailure) {
            appendHistory("Safety rollback also failed: " + message(rollbackFailure));
        }
        stopSampling(); running = false; stage = Stage.ERROR; refreshUi();
    }

    private void failStart(Exception ex) {
        stopSampling(); running = false; stage = Stage.ERROR;
        appendHistory("Autonomous tuning did not start: " + message(ex)); refreshUi();
    }

    private void stopSampling() {
        sampleTimer.stop(); captureEngine.stop();
        if (subscription != null) { subscription.stop(); subscription = null; }
    }

    private void refreshUi() {
        setLabelText(stageValue, stage.label + (running ? "  •  write " + writeCount + "/" + maxWrites : ""));
        setLabelText(originalGains, signature(originalSnapshot));
        setLabelText(bestGains, signature(bestSnapshot));
        setLabelText(candidateCount, writeCount + " / " + maxWrites);
        setLabelText(attemptCount, captureEngine.getAcceptedCount() + " accepted / " + captureEngine.getRejectedCount() + " rejected");
        setLabelText(recommendationState, recommendation == null ? "—" : recommendation.getStatus());
        setLabelText(comparisonState, comparison == null ? "—" : comparison.getStatus() + scoreSuffix(comparison));
        updateInstruction(); updateReadiness(); updateLiveStrip(); updateControls();
    }

    private void updateInstruction() {
        if (!running) {
            if (stage == Stage.COMPLETE) {
                instructionTitle.setText("BEST VERIFIED GAINS REMAIN IN ECU RAM");
                instructionBody.setText("Autonomous tuning has stopped. These values are still temporary RAM settings. The plugin did not Burn them. You may restore the original P/I/D here, or persist the final tune separately in TunerStudio only after you are satisfied with the result.");
                footerText.setText("Next: review the result, then either restore original gains or persist separately in TunerStudio.");
            } else if (stage == Stage.RESTORED) {
                instructionTitle.setText("ORIGINAL P/I/D RESTORED");
                instructionBody.setText("The original session gains were written back to ECU RAM and verified. No Burn command was sent.");
                footerText.setText("Next: start a new autonomous session when conditions are ready.");
            } else if (stage == Stage.ERROR) {
                instructionTitle.setText("AUTONOMOUS TUNING STOPPED");
                instructionBody.setText("Read the tuning history for the exact reason. Do not assume the ECU contains any particular candidate unless the readback line says it was verified.");
                footerText.setText("Next: resolve the error and confirm ECU gains before restarting.");
            } else {
                instructionTitle.setText("CONNECT AND ARM RAM TUNING");
                instructionBody.setText("Choose the capture conditions, confirm the vehicle is stationary when required, then explicitly arm RAM-only P/I/D writes. The tuner will never Burn settings.");
                footerText.setText("Next: arm RAM-only writes, then start autonomous tuning.");
            }
            writeStatus.setText("No active autonomous RAM write cycle");
            return;
        }

        LiveCaptureState captureState = captureEngine.getState();
        instructionTitle.setText(stage == Stage.BASELINE ? "COLLECT A QUALIFIED BASELINE" : "TEST THE VERIFIED RAM CANDIDATE");
        instructionBody.setText(captureEngine.getInstruction());
        writeStatus.setText(stage == Stage.CANDIDATE
                ? "Candidate is active in ECU RAM and readback-verified — perform only the requested maneuver"
                : "Baseline gains are readback-verified — no candidate has been applied yet");
        footerText.setText(captureState == LiveCaptureState.READY_TO_REV
                ? "Next: raise RPM smoothly into the requested range, then release normally."
                : "Next: follow the instruction above; the tuner will apply/compare/rollback automatically when evidence qualifies.");
    }

    private void updateReadiness() {
        if (!running) { setAreaText(readinessText, "No live capture active.\n\nThe tuner will show each readiness gate here after Start."); return; }
        StringBuilder b = new StringBuilder();
        LiveReadiness r = captureEngine.getReadiness();
        for (Map.Entry<String,String> e : r.getRows().entrySet()) b.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        if (recommendation != null) {
            b.append("\nRecommendation: ").append(recommendation.getStatus());
            if (!recommendation.getReason().isEmpty()) b.append(" — ").append(recommendation.getReason());
        }
        if (comparison != null) {
            b.append("\nComparison: ").append(comparison.getStatus()).append(scoreSuffix(comparison));
            if (!comparison.getSummary().isEmpty()) b.append(" — ").append(comparison.getSummary());
        }
        setAreaText(readinessText, b.toString().trim());
    }

    private void updateLiveStrip() {
        if (latestSample == null) {
            setLabelText(liveRpm, "RPM —"); setLabelText(liveTarget, "Target —"); setLabelText(liveError, "Error —"); setLabelText(livePosition, "Idle Pos —");
            setLabelText(liveCapture, "Capture " + displayState(captureEngine.getState())); return;
        }
        double rpm = latestSample.get(LiveChannel.RPM), target = latestSample.get(LiveChannel.IDLE_TARGET);
        setLabelText(liveRpm, "RPM " + whole(rpm)); setLabelText(liveTarget, "Target " + whole(target));
        setLabelText(liveError, "Error " + (finite(rpm) && finite(target) ? signedWhole(rpm - target) : "—"));
        setLabelText(livePosition, "Idle Pos " + one(latestSample.get(LiveChannel.CURRENT_IDLE_POSITION)));
        setLabelText(liveCapture, "Capture " + displayState(captureEngine.getState()));
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && parameterServer != null;
        boolean manual = selectedMotionMode() == LiveMotionMode.MANUAL_STATIONARY;
        configurationCombo.setEnabled(connected && !running);
        profileCombo.setEnabled(!running); motionModeCombo.setEnabled(!running);
        stationaryConfirmation.setEnabled(!running && manual);
        minimumCltField.setEnabled(!running); maximumCltField.setEnabled(!running);
        revMinimumField.setEnabled(!running); revMaximumField.setEnabled(!running); maxWritesField.setEnabled(!running);
        startButton.setEnabled(connected && !running && armWrites.isSelected());
        stopKeepButton.setEnabled(running);
        restoreOriginalButton.setEnabled(!running && originalSnapshot != null && originalSnapshot.isComplete()
                && writeCoordinator != null && activeConfiguration != null);
    }

    private void reloadConfigurations() {
        configurationCombo.removeAllItems();
        if (controllerAccess == null) return;
        String[] names = controllerAccess.getEcuConfigurationNames();
        if (names != null) for (String n : names) if (n != null && !n.trim().isEmpty()) configurationCombo.addItem(n.trim());
    }

    private double requiredScalar(String configurationName, String name) throws Exception {
        ControllerParameter p = parameterServer.getControllerParameter(configurationName, name);
        if (p == null) throw new IllegalStateException(name + " is missing from the active ECU definition.");
        if (!ControllerParameter.PARAM_CLASS_SCALAR.equals(p.getParamClass())) throw new IllegalStateException(name + " is not a scalar parameter.");
        return p.getScalarValue();
    }

    private String selectedConfiguration() {
        Object o = configurationCombo.getSelectedItem(); String s = o == null ? "" : o.toString().trim();
        if (s.isEmpty()) throw new IllegalStateException("No ECU configuration is selected."); return s;
    }
    private LiveCaptureProfile selectedProfile() {
        Object o = profileCombo.getSelectedItem(); return o instanceof LiveCaptureProfile ? (LiveCaptureProfile)o : LiveCaptureProfile.INITIAL_ROUGH;
    }
    private LiveMotionMode selectedMotionMode() {
        Object o = motionModeCombo.getSelectedItem(); return o instanceof LiveMotionMode ? (LiveMotionMode)o : LiveMotionMode.USE_LIVE_VSS;
    }

    private static TuneSnapshot scaledCandidate(TuneSnapshot base, String gain, double percent) {
        double p = base.getP(), i = base.getI(), d = base.getD();
        if ("P".equals(gain)) p = applyPercent(p, percent);
        else if ("I".equals(gain)) i = applyPercent(i, percent);
        else if ("D".equals(gain)) d = applyPercent(d, percent);
        else throw new IllegalArgumentException("Unsupported gain " + gain + ".");
        return new TuneSnapshot(p, i, d, "Autonomous RAM candidate", true);
    }
    private static double applyPercent(double value, double percent) { return Math.round(value * (1.0 + percent / 100.0) * 10000.0) / 10000.0; }

    private static double parseDouble(JTextField f, String label) {
        try { double v = Double.parseDouble(f.getText().trim()); if (!finite(v)) throw new NumberFormatException(); return v; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " must be a finite number."); }
    }
    private static int parseInt(JTextField f, String label, int min, int max) {
        try { int v = Integer.parseInt(f.getText().trim()); if (v < min || v > max) throw new NumberFormatException(); return v; }
        catch (NumberFormatException ex) { throw new IllegalArgumentException(label + " must be between " + min + " and " + max + "."); }
    }

    private void appendHistory(String line) {
        boolean follow = StableScrollSupport.isAtBottom(historyScroll, 12);
        int keep = StableScrollSupport.verticalPosition(historyScroll);
        String t = new SimpleDateFormat("HH:mm:ss").format(new Date());
        if (historyText.getText().length() > 0) historyText.append("\n");
        historyText.append(t + "  " + line);
        if (follow) StableScrollSupport.scrollToBottomLater(historyScroll, keep);
        else StableScrollSupport.setVerticalPosition(historyScroll, keep);
    }

    private static void setAreaText(JTextArea area, String value) {
        String next = value == null ? "" : value;
        if (!next.equals(area.getText())) area.setText(next);
    }

    private static void setLabelText(JLabel label, String value) {
        String next = value == null ? "" : value;
        if (!next.equals(label.getText())) label.setText(next);
    }

    public void applyTheme() {
        setBackground(UiTheme.bg()); headerCard.setBackground(UiTheme.bg()); footer.setBackground(UiTheme.bg());
        Object title = ((JPanel)headerCard.getComponent(0)).getClientProperty("title");
        Object sub = ((JPanel)headerCard.getComponent(0)).getClientProperty("sub");
        if (title instanceof JLabel) ((JLabel)title).setForeground(UiTheme.text());
        if (sub instanceof JLabel) ((JLabel)sub).setForeground(UiTheme.muted());
        stageValue.setForeground(UiTheme.blue()); footerText.setForeground(UiTheme.text());
        heroCard.setBackground(UiTheme.focusBg()); heroCard.setBorder(compoundCardBorder());
        instructionTitle.setForeground(UiTheme.focusText()); instructionBody.setBackground(UiTheme.focusBg()); instructionBody.setForeground(UiTheme.focusText());
        writeStatus.setForeground(UiTheme.focusMuted());
        liveStrip.setBackground(UiTheme.bg());
        for (Component c : liveStrip.getComponents()) { c.setBackground(UiTheme.card()); c.setForeground(UiTheme.text()); }
        themeCard(setupCard); themeCard(stateCard);
        readinessText.setBackground(UiTheme.card()); readinessText.setForeground(UiTheme.text());
        historyText.setBackground(UiTheme.card()); historyText.setForeground(UiTheme.text());
        stylePrimary(startButton); styleSecondary(stopKeepButton); styleSecondary(restoreOriginalButton);
        armWrites.setBackground(UiTheme.card()); armWrites.setForeground(UiTheme.text());
        stationaryConfirmation.setBackground(UiTheme.card()); stationaryConfirmation.setForeground(UiTheme.text());
        StableScrollSupport.installRecursively(this);
        repaint();
    }

    private void themeCard(JPanel p) {
        p.setBackground(UiTheme.card()); p.setForeground(UiTheme.text()); p.setBorder(compoundCardBorder());
        for (Component c : p.getComponents()) {
            c.setForeground(c instanceof JLabel ? UiTheme.text() : c.getForeground());
            if (c instanceof JPanel) themeSimple((JPanel)c);
            else if (c instanceof JTextField || c instanceof JComboBox) { c.setBackground(UiTheme.card()); c.setForeground(UiTheme.text()); }
        }
    }
    private void themeSimple(JPanel p) {
        p.setBackground(UiTheme.card());
        for (Component c : p.getComponents()) {
            if (c instanceof JLabel) c.setForeground(UiTheme.text());
            if (c instanceof JTextField || c instanceof JComboBox) { c.setBackground(UiTheme.card()); c.setForeground(UiTheme.text()); }
        }
    }

    private static JPanel metricCell(JLabel value) {
        JPanel p = new JPanel(new BorderLayout()); p.add(value, BorderLayout.CENTER);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(7, 4, 7, 4)));
        value.setFont(new Font("Dialog", Font.BOLD, 10)); return p;
    }
    private static JLabel sectionTitle(String text) { JLabel l = new JLabel(text); l.setFont(H2); l.setAlignmentX(Component.LEFT_ALIGNMENT); l.setBorder(new EmptyBorder(0,0,6,0)); return l; }
    private static JPanel fieldRow(String label, Component field) {
        JPanel p = new JPanel(new BorderLayout(7,0)); p.setOpaque(false); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,31));
        JLabel l = new JLabel(label); l.setFont(SMALL); l.setPreferredSize(new Dimension(128,26)); p.add(l, BorderLayout.WEST); p.add(field, BorderLayout.CENTER); return p;
    }
    private static JPanel valueRow(String label, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(7,0)); p.setOpaque(false); p.setMaximumSize(new Dimension(Integer.MAX_VALUE,31));
        JLabel l = new JLabel(label); l.setFont(SMALL); l.setPreferredSize(new Dimension(138,26)); value.setFont(new Font("Dialog",Font.BOLD,10));
        p.add(l, BorderLayout.WEST); p.add(value, BorderLayout.CENTER); return p;
    }
    private static JTextArea readOnlyArea(int rows) {
        JTextArea a = new JTextArea(rows,20); a.setEditable(false); a.setFocusable(false); a.setLineWrap(true); a.setWrapStyleWord(true); return a;
    }
    private static Border compoundCardBorder() {
        return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(9,9,9,9));
    }
    private static void stylePrimary(JButton b) {
        b.setFont(new Font("Dialog",Font.BOLD,10)); b.setFocusPainted(false); b.setOpaque(true);
        b.setBackground(UiTheme.amber()); b.setForeground(Color.BLACK);
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.amberDark()), new EmptyBorder(6,11,6,11)));
    }
    private static void styleSecondary(JButton b) {
        b.setFont(new Font("Dialog",Font.BOLD,10)); b.setFocusPainted(false); b.setOpaque(true);
        b.setBackground(UiTheme.button()); b.setForeground(UiTheme.text());
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.buttonBorder()), new EmptyBorder(6,10,6,10)));
    }

    private static String signature(TuneSnapshot s) { return s == null || !s.isComplete() ? "—" : s.toSignature(); }
    private static String scoreSuffix(LiveComparisonResult r) { return r == null || !finite(r.getScorePercent()) ? "" : " (" + one(r.getScorePercent()) + "%)"; }
    private static String signed(double v) { return (v >= 0 ? "+" : "") + one(v); }
    private static String one(double v) { return finite(v) ? String.format(Locale.ROOT,"%.1f",v) : "—"; }
    private static String whole(double v) { return finite(v) ? String.valueOf((int)Math.round(v)) : "—"; }
    private static String signedWhole(double v) { return finite(v) ? ((v >= 0 ? "+" : "") + (int)Math.round(v)) : "—"; }
    private static boolean finite(double v) { return !Double.isNaN(v) && !Double.isInfinite(v); }
    private static String safe(String s) { return s == null ? "" : s; }
    private static String message(Throwable t) { if (t == null) return "Unknown error"; String s=t.getMessage(); return s==null||s.trim().isEmpty()?t.getClass().getSimpleName():s; }
    private static String join(List<String> values) { if (values==null||values.isEmpty()) return "none"; StringBuilder b=new StringBuilder(); for(String s:values){if(b.length()>0)b.append(", ");b.append(s);} return b.toString(); }
    private static String displayState(LiveCaptureState s) {
        if (s == null) return "—";
        String n = s.name().toLowerCase(Locale.ROOT).replace('_',' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
}
