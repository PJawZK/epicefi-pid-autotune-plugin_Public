package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import se.anders.tunerstudio.pidautotune.PidAutotunePlugin;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveAttempt;
import se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveComparisonResult;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendationEngine;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureSettings;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureState;
import se.anders.tunerstudio.pidautotune.live.LiveChannel;
import se.anders.tunerstudio.pidautotune.live.LiveGuidedCaptureEngine;
import se.anders.tunerstudio.pidautotune.live.LiveIterationRecord;
import se.anders.tunerstudio.pidautotune.live.LiveMotionMode;
import se.anders.tunerstudio.pidautotune.live.LiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.LiveReadiness;
import se.anders.tunerstudio.pidautotune.live.LiveSample;
import se.anders.tunerstudio.pidautotune.live.LiveSessionRecord;
import se.anders.tunerstudio.pidautotune.live.LiveSessionSummary;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.model.LiveAttemptTableModel;
import se.anders.tunerstudio.pidautotune.model.LiveIterationTableModel;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableColumnModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Read-only live observer and guided return-to-idle capture UI. */
public final class LiveCapturePanel extends JPanel {
    public interface SessionExportListener {
        void sessionExported(File file, boolean addToDataset);
    }

    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final DecimalFormat THREE = new DecimalFormat("0.000");
    private static final int SAMPLE_PERIOD_MS = 50;

    private final SessionExportListener exportListener;
    private final LiveGuidedCaptureEngine engine = new LiveGuidedCaptureEngine();
    private final LiveExperimentalRecommendationEngine liveRecommendationEngine = new LiveExperimentalRecommendationEngine();
    private final LiveAttemptTableModel attemptModel = new LiveAttemptTableModel();
    private final JTable attemptTable = new JTable(attemptModel);
    private final LiveIterationTableModel iterationModel = new LiveIterationTableModel();
    private final JTable iterationTable = new JTable(iterationModel);
    private final LiveTracePanel tracePanel = new LiveTracePanel();
    private final List<LiveSample> sessionSamples = new ArrayList<LiveSample>();
    private final List<LiveSessionRecord> sessionRecords = new ArrayList<LiveSessionRecord>();
    private final List<LiveIterationRecord> tuningHistory = new ArrayList<LiveIterationRecord>();
    private final Timer sampleTimer;

    private final JLabel subscriptionValue = new JLabel("Stopped");
    private final JLabel stateValue = new JLabel("Stopped");
    private final JLabel liveValuesValue = new JLabel("—");
    private final JLabel attemptCountsValue = new JLabel("0 accepted / 0 rejected");
    private final JLabel instructionValue = new JLabel("Start the live observer when the engine and TunerStudio are connected.");
    private final JLabel lastResultValue = new JLabel("No live attempts yet.");
    private final JTextArea readinessText = new JTextArea();
    private final JTextArea sessionNotes = new JTextArea();
    private final JLabel liveRecommendationStatusValue = new JLabel(LiveExperimentalRecommendation.WAITING);
    private final JLabel liveRecommendationConfidenceValue = new JLabel("—");
    private final JLabel liveCurrentGainsValue = new JLabel("Not captured");
    private final JLabel liveProposedGainsValue = new JLabel("—");
    private final JLabel liveChangedGainValue = new JLabel("—");
    private final JLabel liveBaselineValue = new JLabel("No active baseline stored");
    private final JLabel liveOriginalBaselineValue = new JLabel("No rollback baseline stored");
    private final JLabel historyStatusValue = new JLabel("No tuning iterations archived");
    private final JLabel liveComparisonValue = new JLabel(LiveComparisonResult.NO_BASELINE);
    private final JTextArea liveRecommendationDetails = new JTextArea();
    private final JTextArea liveComparisonDetails = new JTextArea();
    private final JTextArea iterationDetails = new JTextArea();

    private final JComboBox<LiveMotionMode> motionModeCombo = new JComboBox<LiveMotionMode>(LiveMotionMode.values());
    private final JComboBox<LiveCaptureProfile> captureProfileCombo = new JComboBox<LiveCaptureProfile>(LiveCaptureProfile.values());
    private final JCheckBox manualStationaryCheck = new JCheckBox("I confirm the vehicle will remain stationary for this live session");
    private final JTextField minimumCltField = new JTextField("70", 6);
    private final JTextField maximumCltField = new JTextField("110", 6);
    private final JTextField preferredRevMinimumField = new JTextField("1700", 6);
    private final JTextField preferredRevMaximumField = new JTextField("2300", 6);
    private final JTextField baselineBandField = new JTextField("100", 5);
    private final JTextField settlingBandField = new JTextField("60", 5);
    private final JTextField exitHysteresisField = new JTextField("20", 5);
    private final JTextField observationSecondsField = new JTextField("10", 5);
    private final JTextField recoveryTimeoutField = new JTextField("30", 5);

    private final JButton startButton = new JButton("Start live observer");
    private final JButton stopButton = new JButton("Stop");
    private final JButton abortButton = new JButton("Abort current attempt");
    private final JButton resetButton = new JButton("Reset session");
    private final JButton copyButton = new JButton("Copy live summary");
    private final JButton exportButton = new JButton("Save session CSV…");
    private final JButton exportAndAddButton = new JButton("Save + add to dataset…");
    private final JButton setBaselineButton = new JButton("Use session as baseline");
    private final JButton clearBaselineButton = new JButton("Clear baseline");
    private final JButton copyLiveRecommendationButton = new JButton("Copy recommendation + comparison");
    private final JButton keepAsBaselineButton = new JButton("Keep as new baseline");
    private final JButton testNextCandidateButton = new JButton("Test next candidate");
    private final JButton copyRollbackButton = new JButton("Copy rollback gains");
    private final JButton exportHistoryButton = new JButton("Save tuning history CSV…");
    private final JButton clearHistoryButton = new JButton("Clear history");

    private ControllerAccess controllerAccess;
    private ControllerParameterServer parameterServer;
    private LiveOutputSubscription subscription;
    private String configurationName;
    private LiveCaptureSettings activeSettings;
    private TuneSnapshot sessionTuneSnapshot = TuneSnapshot.unknown("Live observer not started");
    private LiveBaselineSnapshot baselineSnapshot;
    private LiveBaselineSnapshot originalBaselineSnapshot;
    private LiveExperimentalRecommendation latestLiveRecommendation;
    private LiveComparisonResult latestLiveComparison;
    private String subscriptionDetails = "";
    private int currentIterationNumber;
    private long sessionStartedWallClockMillis;
    private boolean currentIterationArchived = true;
    private boolean exportInProgress;
    private final LiveRecommendationRefreshTracker recommendationRefreshTracker = new LiveRecommendationRefreshTracker();

    private static final class SessionCsvSnapshot {
        private final String motionBasis;
        private final String captureProfile;
        private final int iterationNumber;
        private final long startedWallClockMillis;
        private final String gainsAtStart;
        private final String recommendationStatus;
        private final String proposedGains;
        private final String changedGain;
        private final String baselineGains;
        private final String comparisonStatus;
        private final LiveCaptureSettings settings;
        private final List<LiveSessionRecord> records;

        private SessionCsvSnapshot(String motionBasis, String captureProfile, int iterationNumber,
                                   long startedWallClockMillis, String gainsAtStart,
                                   String recommendationStatus, String proposedGains, String changedGain,
                                   String baselineGains, String comparisonStatus, LiveCaptureSettings settings,
                                   List<LiveSessionRecord> records) {
            this.motionBasis = motionBasis;
            this.captureProfile = captureProfile;
            this.iterationNumber = iterationNumber;
            this.startedWallClockMillis = startedWallClockMillis;
            this.gainsAtStart = gainsAtStart;
            this.recommendationStatus = recommendationStatus;
            this.proposedGains = proposedGains;
            this.changedGain = changedGain;
            this.baselineGains = baselineGains;
            this.comparisonStatus = comparisonStatus;
            this.settings = settings;
            this.records = new ArrayList<LiveSessionRecord>(records);
        }
    }

    private static final class TuningHistoryCsvSnapshot {
        private final String originalRollbackGains;
        private final String activeBaselineGains;
        private final List<LiveIterationRecord> iterations;

        private TuningHistoryCsvSnapshot(String originalRollbackGains, String activeBaselineGains,
                                         List<LiveIterationRecord> iterations) {
            this.originalRollbackGains = originalRollbackGains;
            this.activeBaselineGains = activeBaselineGains;
            this.iterations = new ArrayList<LiveIterationRecord>(iterations);
        }
    }

    public LiveCapturePanel(SessionExportListener exportListener) {
        super(new BorderLayout(6, 6));
        this.exportListener = exportListener;
        buildUi();
        captureProfileCombo.setSelectedItem(LiveCaptureProfile.INITIAL_ROUGH);
        applyCaptureProfile(LiveCaptureProfile.INITIAL_ROUGH);
        wireActions();
        sampleTimer = new Timer(SAMPLE_PERIOD_MS, event -> sampleLiveValues());
        sampleTimer.setCoalesce(true);
        updateControls();
    }

    public void connect(ControllerAccess controllerAccess) {
        disconnect();
        this.controllerAccess = controllerAccess;
        this.parameterServer = controllerAccess == null ? null : controllerAccess.getControllerParameterServer();
        this.subscription = controllerAccess == null ? null : new LiveOutputSubscription(controllerAccess);
        subscriptionValue.setText(controllerAccess == null ? "TunerStudio API unavailable" : "Ready to subscribe");
        updateControls();
    }

    public void setConfigurationName(String configurationName) {
        String normalized = configurationName == null ? "" : configurationName.trim();
        boolean changed = !normalized.equals(this.configurationName == null ? "" : this.configurationName);
        if (changed && isRunning()) {
            stopLiveObserver("ECU configuration changed; live observer stopped.");
        }
        this.configurationName = normalized;
        if (changed) updateLiveRecommendationUi();
        updateControls();
    }

    public void disconnect() {
        if (isRunning()) stopLiveObserver("Live observer stopped because TunerStudio disconnected.");
        else archiveCurrentIteration("TunerStudio disconnected");
        if (sampleTimer != null) sampleTimer.stop();
        if (subscription != null) subscription.stop();
        engine.stop();
        controllerAccess = null;
        parameterServer = null;
        subscription = null;
        subscriptionValue.setText("Stopped");
        updateControls();
    }

    private void buildUi() {
        JTextArea explanation = new JTextArea(
                "Live guided capture watches EPICEFI output channels in real time and tells you exactly when an attempt is ready, measuring, accepted, or rejected. " +
                "Choose Initial/Rough, Standard, Fine, or custom RPM tolerances to match the starting quality of the idle. Brief DFCO after throttle release is expected and excluded before PID measurement begins. " +
                "It never changes ECU RAM or flash. After repeated accepted attempts it can show a one-gain experimental candidate and compare a later manual test session against an in-memory baseline. Keep TunerStudio logging normally for the original high-rate ECU record.");
        explanation.setEditable(false);
        explanation.setLineWrap(true);
        explanation.setWrapStyleWord(true);
        explanation.setOpaque(false);
        explanation.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

        JPanel settings = buildSettingsPanel();
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(explanation, BorderLayout.NORTH);
        north.add(settings, BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        instructionValue.setFont(instructionValue.getFont().deriveFont(Font.BOLD, instructionValue.getFont().getSize2D() + 2.0f));
        instructionValue.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        instructionValue.setToolTipText("Current instruction from the live guided-capture state machine.");

        readinessText.setEditable(false);
        readinessText.setLineWrap(true);
        readinessText.setWrapStyleWord(true);
        readinessText.setRows(10);
        readinessText.setToolTipText("Current readiness checks. Every required condition must be ready before the plugin asks for a rev.");

        sessionNotes.setEditable(false);
        sessionNotes.setLineWrap(true);
        sessionNotes.setWrapStyleWord(true);
        sessionNotes.setRows(6);
        sessionNotes.setText("No live session has been started.");
        sessionNotes.setToolTipText("Subscription details, last accepted/rejected result, and session safety notes.");

        attemptTable.setFillsViewportHeight(true);
        attemptTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        attemptTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        configureAttemptColumns();

        iterationTable.setFillsViewportHeight(true);
        iterationTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        iterationTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        configureIterationColumns();

        JPanel right = new JPanel(new BorderLayout(4, 4));
        right.add(instructionValue, BorderLayout.NORTH);
        JSplitPane details = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(readinessText), new JScrollPane(attemptTable));
        details.setResizeWeight(0.40);
        details.setDividerLocation(190);
        details.setBorder(null);
        JTabbedPane liveTabs = new JTabbedPane();
        liveTabs.addTab("Capture status", details);
        liveTabs.addTab("Recommendation + comparison", buildLiveRecommendationPanel());
        liveTabs.addTab("Iteration history", buildIterationHistoryPanel());
        right.add(liveTabs, BorderLayout.CENTER);
        right.add(new JScrollPane(sessionNotes), BorderLayout.SOUTH);

        JSplitPane center = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tracePanel, right);
        center.setResizeWeight(0.56);
        center.setDividerLocation(560);
        center.setBorder(null);
        add(center, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(startButton);
        buttons.add(stopButton);
        buttons.add(abortButton);
        buttons.add(resetButton);
        buttons.add(copyButton);
        buttons.add(exportButton);
        buttons.add(exportAndAddButton);
        add(buttons, BorderLayout.SOUTH);
    }

    private JPanel buildLiveRecommendationPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel summary = new JPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        addReadOnlyRow(summary, c, 0, "Preview status", liveRecommendationStatusValue,
                "Experimental live-only candidate status. This never writes ECU values.");
        addReadOnlyRow(summary, c, 1, "Live confidence", liveRecommendationConfidenceValue,
                "Confidence is capped at 70% because the live callback rate is lower than the companion MSL log.");
        addReadOnlyRow(summary, c, 2, "Current gains", liveCurrentGainsValue,
                "P/I/D snapshot captured when the current live observer session started.");
        addReadOnlyRow(summary, c, 3, "Proposed test gains", liveProposedGainsValue,
                "Read-only one-gain candidate. Enter it manually in TunerStudio only after reviewing the explanation.");
        addReadOnlyRow(summary, c, 4, "Changed gain", liveChangedGainValue,
                "The live experimental model changes at most one gain family per iteration.");
        addReadOnlyRow(summary, c, 5, "Active baseline", liveBaselineValue,
                "Baseline used for the next before/after comparison. It may be advanced after a useful iteration.");
        addReadOnlyRow(summary, c, 6, "Rollback baseline", liveOriginalBaselineValue,
                "The first stored baseline is retained as the original rollback reference until baselines are cleared.");
        addReadOnlyRow(summary, c, 7, "Comparison result", liveComparisonValue,
                "Improved, Mixed, Worse, or Inconclusive comparison against the active baseline.");
        panel.add(summary, BorderLayout.NORTH);

        liveRecommendationDetails.setEditable(false);
        liveRecommendationDetails.setLineWrap(true);
        liveRecommendationDetails.setWrapStyleWord(true);
        liveRecommendationDetails.setText("Collect at least two accepted live attempts, including one Good attempt.");
        liveRecommendationDetails.setToolTipText("Experimental candidate, evidence, reason, and expected effect.");
        liveComparisonDetails.setEditable(false);
        liveComparisonDetails.setLineWrap(true);
        liveComparisonDetails.setWrapStyleWord(true);
        liveComparisonDetails.setText("Store a completed session as the baseline before changing gains.");
        liveComparisonDetails.setToolTipText("Before/after metric comparison. Positive percentages mean improvement because lower is better.");
        JSplitPane textSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(liveRecommendationDetails), new JScrollPane(liveComparisonDetails));
        textSplit.setResizeWeight(0.52);
        textSplit.setDividerLocation(185);
        textSplit.setBorder(null);
        panel.add(textSplit, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        setBaselineButton.setToolTipText(
                "Store the accepted attempts and the gains captured at observer start as the in-memory baseline. No ECU value is changed.");
        clearBaselineButton.setToolTipText("Remove the in-memory baseline only. It does not alter the ECU or saved files.");
        copyLiveRecommendationButton.setToolTipText(
                "Copy the experimental candidate and before/after comparison. Copying never applies the values.");
        buttons.add(setBaselineButton);
        buttons.add(clearBaselineButton);
        buttons.add(copyLiveRecommendationButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildIterationHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        historyStatusValue.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
        historyStatusValue.setToolTipText(
                "Each stopped observer run is archived with its gains, attempt summary, candidate, and comparisons.");
        panel.add(historyStatusValue, BorderLayout.NORTH);

        iterationDetails.setEditable(false);
        iterationDetails.setLineWrap(true);
        iterationDetails.setWrapStyleWord(true);
        iterationDetails.setText("Stop a live observer session to archive its gain stage here.");
        iterationDetails.setToolTipText(
                "Selected iteration details, including comparisons against the original and previous gain stages.");
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(iterationTable), new JScrollPane(iterationDetails));
        split.setResizeWeight(0.58);
        split.setDividerLocation(220);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        keepAsBaselineButton.setToolTipText(
                "Archive the current stage and use it as the active baseline for the next manual test. The original rollback baseline is retained.");
        testNextCandidateButton.setToolTipText(
                "Archive the current stage, make it the active baseline, and copy the next read-only candidate for manual entry in TunerStudio.");
        copyRollbackButton.setToolTipText(
                "Copy the first stored baseline gains so they remain available as a manual rollback reference.");
        exportHistoryButton.setToolTipText(
                "Save every archived iteration and its full live samples, wall-clock timestamps, gains, attempts, and comparisons to one CSV file.");
        clearHistoryButton.setToolTipText(
                "Clear only the in-memory iteration history. ECU values and saved files are not changed.");
        buttons.add(keepAsBaselineButton);
        buttons.add(testNextCandidateButton);
        buttons.add(copyRollbackButton);
        buttons.add(exportHistoryButton);
        buttons.add(clearHistoryButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildSettingsPanel() {
        JPanel outer = new JPanel(new BorderLayout(4, 4));
        JPanel rows = new JPanel(new GridBagLayout());
        GridBagConstraints c = constraints();
        addReadOnlyRow(rows, c, 0, "Subscription", subscriptionValue,
                "Shows whether the plugin is subscribed to the active TunerStudio output channels.");
        addReadOnlyRow(rows, c, 1, "Capture state", stateValue,
                "Current guided-capture state, from waiting for stable idle through recovery confirmation.");
        addReadOnlyRow(rows, c, 2, "Live values", liveValuesValue,
                "Current RPM, target, TPS, CLT, fan state, VSS basis, and derived idle correction.");
        addReadOnlyRow(rows, c, 3, "Attempt count", attemptCountsValue,
                "Accepted, Good, and rejected attempts in the current in-memory live session.");
        addReadOnlyRow(rows, c, 4, "Last result", lastResultValue,
                "Immediate result and reason for the most recently completed or rejected attempt.");

        JPanel editable = new JPanel(new GridLayout(0, 1, 0, 2));
        JPanel rowOne = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addControl(rowOne, "Capture profile", captureProfileCombo,
                "Initial/Rough uses wide bands for an unstable starting tune; Standard is general purpose; Fine is for final refinement; Custom keeps manual values.");
        addControl(rowOne, "Motion basis", motionModeCombo,
                "Choose live VSS or explicitly confirm a stationary session when VSS is unavailable.");
        editable.add(rowOne);

        JPanel rowTwo = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        manualStationaryCheck.setToolTipText(
                "Required only when the motion basis is manual stationary confirmation. Untick it to block or abort capture immediately.");
        rowTwo.add(manualStationaryCheck);
        addControl(rowTwo, "Minimum CLT", minimumCltField,
                "Lowest coolant temperature accepted for the live session. Range: -40 to 150°C.");
        addControl(rowTwo, "Maximum CLT", maximumCltField,
                "Highest coolant temperature accepted for the live session. Range: -40 to 150°C.");
        editable.add(rowTwo);

        JPanel rowThree = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addControl(rowThree, "Preferred rev minimum", preferredRevMinimumField,
                "Lower preferred peak RPM for a Good attempt. The engine is never controlled by the plugin.");
        addControl(rowThree, "Preferred rev maximum", preferredRevMaximumField,
                "Upper preferred peak RPM for a Good attempt. Stay within a safe range for the engine.");
        addControl(rowThree, "Baseline band", baselineBandField,
                "Rolling mean RPM error allowed before a test begins. Wider values let an unstable starting tune enter guided capture. Range: 20 to 400 RPM.");
        addControl(rowThree, "Settling band", settlingBandField,
                "Rolling mean RPM error accepted as recovered. This is a capture tolerance, not a claim that the final idle is good. Range: 20 to 300 RPM.");
        editable.add(rowThree);

        JPanel rowFour = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        addControl(rowFour, "Exit hysteresis", exitHysteresisField,
                "Additional RPM beyond the settling band required before a settled attempt is reset. Prevents single noisy samples from repeatedly breaking confirmation. Range: 0 to 150 RPM.");
        addControl(rowFour, "Stable observation", observationSecondsField,
                "Continuous untouched recovered-idle time required before an attempt is accepted. Range: 5 to 30 seconds.");
        addControl(rowFour, "Recovery timeout", recoveryTimeoutField,
                "Maximum time after PID measurement begins for RPM to enter the selected settling band. Range: 10 to 60 seconds.");
        editable.add(rowFour);

        outer.add(rows, BorderLayout.NORTH);
        outer.add(editable, BorderLayout.SOUTH);
        return outer;
    }

    private void wireActions() {
        captureProfileCombo.addActionListener(event -> {
            LiveCaptureProfile profile = (LiveCaptureProfile) captureProfileCombo.getSelectedItem();
            captureProfileCombo.setToolTipText(profile == null ? "" : profile.getDescription());
            if (profile != null && profile != LiveCaptureProfile.CUSTOM && !isRunning()) applyCaptureProfile(profile);
            updateControls();
        });
        motionModeCombo.addActionListener(event -> {
            LiveMotionMode mode = (LiveMotionMode) motionModeCombo.getSelectedItem();
            motionModeCombo.setToolTipText(mode == null ? "" : mode.getDescription());
            manualStationaryCheck.setEnabled(mode == LiveMotionMode.MANUAL_STATIONARY && !isRunning());
            updateControls();
        });
        manualStationaryCheck.addActionListener(event -> {
            if (activeSettings != null && activeSettings.getMotionMode() == LiveMotionMode.MANUAL_STATIONARY) {
                activeSettings = activeSettings.withManualStationaryConfirmed(manualStationaryCheck.isSelected());
            }
            updateControls();
        });
        startButton.addActionListener(event -> startLiveObserver());
        stopButton.addActionListener(event -> stopLiveObserver("Live observer stopped by user."));
        abortButton.addActionListener(event -> engine.abortCurrentAttempt("Attempt aborted by user."));
        resetButton.addActionListener(event -> resetLiveSession(isRunning()));
        copyButton.addActionListener(event -> copySummary());
        exportButton.addActionListener(event -> exportSession(false));
        exportAndAddButton.addActionListener(event -> exportSession(true));
        setBaselineButton.addActionListener(event -> storeCurrentSessionAsBaseline());
        clearBaselineButton.addActionListener(event -> {
            baselineSnapshot = null;
            updateLiveRecommendationUi();
            sessionNotes.setText("Cleared the in-memory live baseline. No ECU values or saved files were changed.\n\n" + buildSessionNotes());
        });
        copyLiveRecommendationButton.addActionListener(event -> copyLiveRecommendationAndComparison());
        keepAsBaselineButton.addActionListener(event -> keepCurrentAsNewBaseline(false));
        testNextCandidateButton.addActionListener(event -> keepCurrentAsNewBaseline(true));
        copyRollbackButton.addActionListener(event -> copyRollbackReference());
        exportHistoryButton.addActionListener(event -> exportTuningHistory());
        clearHistoryButton.addActionListener(event -> clearTuningHistory());
        iterationTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateIterationDetails();
        });
    }

    private void resetLiveSession(boolean observerRunning) {
        int previousIterationNumber = currentIterationNumber;
        archiveCurrentIteration("Session reset");
        engine.resetSession();
        recommendationRefreshTracker.invalidate();
        sessionSamples.clear();
        sessionRecords.clear();
        tracePanel.clear();
        if (observerRunning) {
            currentIterationNumber = Math.max(previousIterationNumber + 1, nextIterationNumber());
            sessionStartedWallClockMillis = System.currentTimeMillis();
            currentIterationArchived = false;
        } else {
            currentIterationArchived = true;
        }
        attemptModel.setRows(engine.getAttempts());
        updateUi(null);
        updateIterationHistoryUi();
    }

    private void startLiveObserver() {
        if (controllerAccess == null || subscription == null) {
            sessionNotes.setText("TunerStudio controller API is not available.");
            return;
        }
        if (configurationName == null || configurationName.trim().isEmpty()) {
            sessionNotes.setText("No active ECU configuration is selected.");
            return;
        }
        try {
            activeSettings = readSettingsFromUiAndTune();
            sessionTuneSnapshot = captureCurrentGains();
            LiveSubscriptionReport report = subscription.start(configurationName);
            subscriptionDetails = buildSubscriptionDetails(report);
            if (!report.isUsable()) {
                subscription.stop();
                subscriptionValue.setText("Required channels missing");
                sessionNotes.setText(subscriptionDetails);
                updateControls();
                return;
            }
            archiveCurrentIteration("Superseded by a new observer run");
            sessionSamples.clear();
            sessionRecords.clear();
            tracePanel.clear();
            engine.resetSession();
            currentIterationNumber = nextIterationNumber();
            sessionStartedWallClockMillis = System.currentTimeMillis();
            currentIterationArchived = false;
            engine.start();
            sampleTimer.start();
            subscriptionValue.setText("Live — " + report.getSubscribed().size() + " channels");
            sessionNotes.setText(subscriptionDetails + "\n\nLive observer started with "
                    + sessionTuneSnapshot.toSignature() + ". Do not change gains until this session is stopped. No ECU values were changed by the plugin.");
            updateLiveRecommendationUi();
            updateControls();
        } catch (Exception ex) {
            if (subscription != null) subscription.stop();
            subscriptionValue.setText("Start failed");
            sessionNotes.setText("Unable to start live subscriptions: " + safeMessage(ex));
            updateControls();
        }
    }

    private void stopLiveObserver(String reason) {
        sampleTimer.stop();
        if (subscription != null) subscription.stop();
        archiveCurrentIteration(reason == null ? "Observer stopped" : reason);
        engine.stop();
        subscriptionValue.setText("Stopped");
        stateValue.setText("Stopped");
        if (reason != null && !reason.isEmpty()) sessionNotes.setText(reason + "\n\n" + subscriptionDetails);
        updateControls();
    }

    private void sampleLiveValues() {
        if (subscription == null || !subscription.isStarted() || activeSettings == null) return;
        LiveSample sample = subscription.snapshot();
        if (activeSettings.getMotionMode() == LiveMotionMode.MANUAL_STATIONARY) {
            activeSettings = activeSettings.withManualStationaryConfirmed(manualStationaryCheck.isSelected());
        }
        sessionSamples.add(sample);
        engine.process(sample, activeSettings, System.nanoTime());
        sessionRecords.add(new LiveSessionRecord(sample, engine.getState(), engine.getCurrentAttemptNumber(),
                engine.getEventMarker(), engine.getResultCode(), activeSettings.getCaptureProfile(),
                System.currentTimeMillis(), currentIterationNumber, sessionTuneSnapshot));
        updateUi(sample);
    }

    private void updateUi(LiveSample sample) {
        stateValue.setText(formatState(engine.getState()));
        instructionValue.setText(engine.getInstruction());
        lastResultValue.setText(engine.getLastResult());
        attemptCountsValue.setText(engine.getAcceptedCount() + " accepted (" + engine.getGoodCount()
                + " Good) / " + engine.getRejectedCount() + " rejected");
        attemptModel.setRows(engine.getAttempts());
        readinessText.setText(formatReadiness(engine.getReadiness()));
        readinessText.setCaretPosition(0);
        if (sample != null) liveValuesValue.setText(formatLiveValues(sample, activeSettings));
        tracePanel.setSamples(displaySamples());
        refreshLiveRecommendationUiIfAttemptsChanged();
        sessionNotes.setText(buildSessionNotes());
        updateControls();
    }

    private TuneSnapshot captureCurrentGains() {
        return new TuneSnapshot(
                readScalar("idleRpmPid_pFactor", Double.NaN),
                readScalar("idleRpmPid_iFactor", Double.NaN),
                readScalar("idleRpmPid_dFactor", Double.NaN),
                "Captured when live observer started", false);
    }

    private int nextIterationNumber() {
        int maximum = 0;
        for (LiveIterationRecord record : tuningHistory) {
            if (record != null) maximum = Math.max(maximum, record.getIterationNumber());
        }
        return maximum + 1;
    }

    private void archiveCurrentIteration(String reason) {
        if (currentIterationArchived || currentIterationNumber <= 0
                || (sessionSamples.isEmpty() && engine.getAttempts().isEmpty())) return;
        LiveSessionSummary summary = LiveSessionSummary.from(engine.getAttempts());
        LiveExperimentalRecommendation recommendation = applyIterationHistoryGuard(
                liveRecommendationEngine.evaluate(engine.getAttempts(), sessionTuneSnapshot), summary);
        LiveCaptureProfile profile = activeSettings == null
                ? (LiveCaptureProfile) captureProfileCombo.getSelectedItem()
                : activeSettings.getCaptureProfile();
        LiveMotionMode motion = activeSettings == null
                ? (LiveMotionMode) motionModeCombo.getSelectedItem()
                : activeSettings.getMotionMode();
        LiveComparisonResult originalComparison = originalBaselineSnapshot == null ? null
                : liveRecommendationEngine.compare(originalBaselineSnapshot, summary, sessionTuneSnapshot, profile, motion,
                        configurationName, activeSettings);
        LiveComparisonResult previousComparison = null;
        if (!tuningHistory.isEmpty()) {
            LiveIterationRecord previous = tuningHistory.get(tuningHistory.size() - 1);
            previousComparison = liveRecommendationEngine.compare(
                    previous.asBaseline("Previous iteration"), summary, sessionTuneSnapshot, profile, motion,
                    configurationName, activeSettings);
        }
        String decision = iterationDecision(summary, originalComparison, previousComparison);
        tuningHistory.add(new LiveIterationRecord(
                currentIterationNumber,
                sessionStartedWallClockMillis > 0L ? sessionStartedWallClockMillis : System.currentTimeMillis(),
                System.currentTimeMillis(),
                sessionTuneSnapshot,
                summary,
                profile,
                motion,
                configurationName,
                activeSettings,
                recommendation,
                originalComparison,
                previousComparison,
                decision,
                reason,
                new ArrayList<LiveAttempt>(engine.getAttempts()),
                new ArrayList<LiveSessionRecord>(sessionRecords)));
        currentIterationArchived = true;
        updateIterationHistoryUi();
    }

    private static String iterationDecision(LiveSessionSummary summary,
                                            LiveComparisonResult original,
                                            LiveComparisonResult previous) {
        if (summary == null || summary.getAcceptedCount() < 2) return "Insufficient data";
        LiveComparisonResult result = previous != null ? previous : original;
        if (result == null) return "Baseline candidate";
        if (LiveComparisonResult.IMPROVED.equals(result.getStatus())) return "Keep / continue";
        if (LiveComparisonResult.WORSE.equals(result.getStatus())) return "Rollback advised";
        if (LiveComparisonResult.MIXED.equals(result.getStatus())) return "Review before keeping";
        if (LiveComparisonResult.SAME_GAINS.equals(result.getStatus())) return "Baseline / repeated gains";
        if (LiveComparisonResult.INCOMPATIBLE.equals(result.getStatus())) return "Conditions differ";
        return "Inconclusive";
    }

    private LiveExperimentalRecommendation applyIterationHistoryGuard(
            LiveExperimentalRecommendation recommendation, LiveSessionSummary currentSummary) {
        if (recommendation == null || !recommendation.isProposalAvailable()
                || !"D".equals(recommendation.getChangedGain())
                || recommendation.getChangePercent() >= 0.0
                || sessionTuneSnapshot == null || !sessionTuneSnapshot.isComplete()) return recommendation;

        LiveIterationRecord previous = null;
        for (int i = tuningHistory.size() - 1; i >= 0; i--) {
            LiveIterationRecord candidate = tuningHistory.get(i);
            if (candidate == null || !sameConfigurationIdentity(candidate.getConfigurationIdentity(), configurationName)) continue;
            TuneSnapshot gains = candidate.getGains();
            if (gains == null || !gains.isComplete()) continue;
            if (Math.abs(gains.getP() - sessionTuneSnapshot.getP()) < 0.00005
                    && Math.abs(gains.getI() - sessionTuneSnapshot.getI()) < 0.00005
                    && gains.getD() > sessionTuneSnapshot.getD() + 0.00005) {
                previous = candidate;
                break;
            }
        }
        if (previous == null) return recommendation;

        LiveSessionSummary before = previous.getSummary();
        double dActivityImprovement = averageFinite(
                improvement(before.getMedianDTermSpan(), currentSummary.getMedianDTermSpan(), 25.0),
                improvement(before.getMedianCorrectionReversalsPerSecond(),
                        currentSummary.getMedianCorrectionReversalsPerSecond(), 0.5));
        double rpmImprovement = averageFinite(
                improvement(before.getMedianSettlingSeconds(), currentSummary.getMedianSettlingSeconds(), 0.5),
                improvement(before.getMedianMeanAbsoluteErrorRpm(), currentSummary.getMedianMeanAbsoluteErrorRpm(), 5.0),
                improvement(before.getMedianRpmStandardDeviation(), currentSummary.getMedianRpmStandardDeviation(), 5.0));

        String reason = null;
        if (LiveSample.isFinite(rpmImprovement) && rpmImprovement < -8.0) {
            reason = "The latest D reduction made the aggregate RPM response measurably worse than the preceding gain stage.";
        } else if (LiveSample.isFinite(dActivityImprovement) && dActivityImprovement < 5.0
                && LiveSample.isFinite(rpmImprovement) && rpmImprovement < 3.0) {
            reason = "The latest D reduction produced less than 5% additional output-activity improvement and no meaningful RPM-response improvement. Diminishing returns have been reached.";
        }
        if (reason == null) return recommendation;

        return new LiveExperimentalRecommendation(
                LiveExperimentalRecommendation.NO_CHANGE,
                "Iteration history stopped a further D reduction.",
                recommendation.getDetails() + "\n\nHistory guard: compared with iteration "
                        + previous.getIterationNumber() + ", D/output activity changed "
                        + historyPercent(dActivityImprovement) + " and RPM behaviour changed "
                        + historyPercent(rpmImprovement) + ".",
                recommendation.getConfidencePercent(), true,
                sessionTuneSnapshot, sessionTuneSnapshot, "None", 0.0,
                reason,
                "Keep the present gains, review the original and previous-stage comparisons, and move to another tuning cause only when the evidence supports it.",
                currentSummary);
    }

    private static double improvement(double before, double current, double floor) {
        if (!LiveSample.isFinite(before) || !LiveSample.isFinite(current)) return Double.NaN;
        return (before - current) / Math.max(floor, Math.abs(before)) * 100.0;
    }

    private static double averageFinite(double... values) {
        double total = 0.0;
        int count = 0;
        if (values != null) {
            for (double value : values) {
                if (LiveSample.isFinite(value)) { total += value; count++; }
            }
        }
        return count == 0 ? Double.NaN : total / count;
    }

    private static String historyPercent(double value) {
        return LiveSample.isFinite(value) ? (value > 0.0 ? "+" : "") + ONE.format(value) + "%" : "unavailable";
    }

    private void updateIterationHistoryUi() {
        iterationModel.setRows(tuningHistory);
        historyStatusValue.setText(tuningHistory.isEmpty()
                ? "No tuning iterations archived"
                : tuningHistory.size() + " tuning iteration(s) archived; active iteration "
                    + (currentIterationNumber <= 0 ? "none" : String.valueOf(currentIterationNumber)));
        if (!tuningHistory.isEmpty() && iterationTable.getSelectedRow() < 0) {
            int row = tuningHistory.size() - 1;
            iterationTable.setRowSelectionInterval(row, row);
        }
        updateIterationDetails();
    }

    private void updateIterationDetails() {
        LiveIterationRecord record = iterationModel.getRow(iterationTable.getSelectedRow());
        if (record == null) {
            iterationDetails.setText("Stop a live observer session to archive its gain stage here.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("Iteration ").append(record.getIterationNumber()).append(" — ")
                .append(record.getGains() == null ? "Gains unavailable" : record.getGains().toSignature()).append('\n');
        text.append("Configuration: ").append(displayConfiguration(record.getConfigurationIdentity())).append('\n');
        text.append("Started: ").append(formatWallClock(record.getStartedMillis()))
                .append("; ended: ").append(formatWallClock(record.getEndedMillis())).append('\n');
        text.append("Attempts: ").append(record.getSummary().getAcceptedCount()).append(" accepted, ")
                .append(record.getSummary().getGoodCount()).append(" Good; decision: ")
                .append(record.getDecision()).append('\n');
        text.append("Archive reason: ").append(record.getArchiveReason()).append("\n\n");
        LiveExperimentalRecommendation recommendation = record.getRecommendation();
        if (recommendation != null) {
            text.append("Candidate: ").append(recommendation.getStatus());
            if (recommendation.isProposalAvailable()) text.append(" — ")
                    .append(formatTune(recommendation.getProposed())).append(" (")
                    .append(recommendation.getChangedGain()).append(' ')
                    .append(signedPercent(recommendation.getChangePercent())).append(')');
            text.append("\nReason: ").append(recommendation.getReason()).append("\n\n");
        }
        appendComparison(text, "Against original rollback baseline", record.getOriginalComparison());
        appendComparison(text, "Against previous iteration", record.getPreviousComparison());
        text.append("\nSummary medians: settling ").append(formatMetric(record.getSummary().getMedianSettlingSeconds(), " s"))
                .append("; MAE ").append(formatMetric(record.getSummary().getMedianMeanAbsoluteErrorRpm(), " RPM"))
                .append("; RPM deviation ").append(formatMetric(record.getSummary().getMedianRpmStandardDeviation(), " RPM"))
                .append("; correction limits ").append(formatMetric(record.getSummary().getMedianCorrectionLimitPercent(), "%"))
                .append("; reversals ").append(formatMetric(record.getSummary().getMedianCorrectionReversalsPerSecond(), "/s"))
                .append("; D span ").append(formatMetric(record.getSummary().getMedianDTermSpan(), ""));
        iterationDetails.setText(text.toString());
        iterationDetails.setCaretPosition(0);
    }

    private static void appendComparison(StringBuilder text, String label, LiveComparisonResult result) {
        text.append(label).append(": ");
        if (result == null) text.append("Unavailable\n");
        else text.append(result.getStatus()).append(" — ").append(result.getSummary()).append('\n');
    }

    private static String formatMetric(double value, String suffix) {
        return LiveSample.isFinite(value) ? ONE.format(value) + suffix : "unavailable";
    }

    private static String formatWallClock(long millis) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(new Date(millis));
    }

    private void refreshLiveRecommendationUiIfAttemptsChanged() {
        if (recommendationRefreshTracker.shouldRefresh(engine.getAttempts().size())) {
            updateLiveRecommendationUi();
        }
    }

    private void updateLiveRecommendationUi() {
        LiveSessionSummary summary = LiveSessionSummary.from(engine.getAttempts());
        latestLiveRecommendation = applyIterationHistoryGuard(
                liveRecommendationEngine.evaluate(engine.getAttempts(), sessionTuneSnapshot), summary);
        LiveCaptureProfile profile = activeSettings == null
                ? (LiveCaptureProfile) captureProfileCombo.getSelectedItem()
                : activeSettings.getCaptureProfile();
        LiveMotionMode motion = activeSettings == null
                ? (LiveMotionMode) motionModeCombo.getSelectedItem()
                : activeSettings.getMotionMode();
        latestLiveComparison = liveRecommendationEngine.compare(
                baselineSnapshot, summary, sessionTuneSnapshot, profile, motion, configurationName, activeSettings);

        liveRecommendationStatusValue.setText(latestLiveRecommendation.getStatus());
        liveRecommendationConfidenceValue.setText(
                LiveSample.isFinite(latestLiveRecommendation.getConfidencePercent())
                        ? ONE.format(latestLiveRecommendation.getConfidencePercent()) + "% (live-only cap 70%)"
                        : "—");
        liveCurrentGainsValue.setText(formatTune(sessionTuneSnapshot));
        liveProposedGainsValue.setText(
                latestLiveRecommendation.getProposed() == null
                        ? "—" : formatTune(latestLiveRecommendation.getProposed()));
        liveChangedGainValue.setText(latestLiveRecommendation.isProposalAvailable()
                ? latestLiveRecommendation.getChangedGain() + " "
                    + signedPercent(latestLiveRecommendation.getChangePercent())
                : "—");
        liveRecommendationDetails.setText(
                latestLiveRecommendation.getSummary() + "\n\nReason: "
                        + latestLiveRecommendation.getReason() + "\n\nExpected effect: "
                        + latestLiveRecommendation.getExpectedEffect() + "\n\n"
                        + latestLiveRecommendation.getDetails());
        liveRecommendationDetails.setCaretPosition(0);

        if (baselineSnapshot == null) {
            liveBaselineValue.setText("No active baseline stored");
        } else {
            liveBaselineValue.setText(baselineSnapshot.getGains().toSignature() + " — "
                    + baselineSnapshot.getSummary().getAcceptedCount() + " accepted attempts");
        }
        if (originalBaselineSnapshot == null) {
            liveOriginalBaselineValue.setText("No rollback baseline stored");
        } else {
            liveOriginalBaselineValue.setText(originalBaselineSnapshot.getGains().toSignature());
        }
        liveComparisonValue.setText(latestLiveComparison.getStatus());
        liveComparisonDetails.setText(latestLiveComparison.getSummary()
                + (latestLiveComparison.getDetails().isEmpty() ? "" : "\n\n" + latestLiveComparison.getDetails()));
        liveComparisonDetails.setCaretPosition(0);
        recommendationRefreshTracker.markRefreshed(engine.getAttempts().size());
    }

    private void storeCurrentSessionAsBaseline() {
        LiveSessionSummary summary = LiveSessionSummary.from(engine.getAttempts());
        if (summary.getAcceptedCount() < 2) {
            sessionNotes.setText("A live baseline requires at least two accepted attempts. No ECU values were changed.\n\n"
                    + buildSessionNotes());
            return;
        }
        if (sessionTuneSnapshot == null || !sessionTuneSnapshot.isComplete()) {
            sessionNotes.setText("Cannot store the baseline because the session P/I/D snapshot is incomplete.\n\n"
                    + buildSessionNotes());
            return;
        }
        LiveCaptureProfile profile = activeSettings == null
                ? (LiveCaptureProfile) captureProfileCombo.getSelectedItem()
                : activeSettings.getCaptureProfile();
        LiveMotionMode motion = activeSettings == null
                ? (LiveMotionMode) motionModeCombo.getSelectedItem()
                : activeSettings.getMotionMode();
        baselineSnapshot = new LiveBaselineSnapshot(
                sessionTuneSnapshot, summary, profile, motion,
                "Stored " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()),
                configurationName, activeSettings);
        if (originalBaselineSnapshot == null || !originalBaselineSnapshot.isForConfiguration(configurationName)) {
            originalBaselineSnapshot = baselineSnapshot;
        }
        if (!isRunning()) archiveCurrentIteration("Stored as baseline");
        updateLiveRecommendationUi();
        sessionNotes.setText("Stored this live session as the in-memory baseline using "
                + sessionTuneSnapshot.toSignature()
                + ". Change the proposed gain manually, stop and restart the observer so the new gains are captured, then record at least two accepted attempts. No ECU value was changed by the plugin.\n\n"
                + buildSessionNotes());
    }

    private void keepCurrentAsNewBaseline(boolean copyNextCandidate) {
        LiveSessionSummary summary = LiveSessionSummary.from(engine.getAttempts());
        if (isRunning()) {
            sessionNotes.setText("Stop the live observer before advancing the tuning baseline. No ECU values were changed.\n\n" + buildSessionNotes());
            return;
        }
        if (summary.getAcceptedCount() < 2 || sessionTuneSnapshot == null || !sessionTuneSnapshot.isComplete()) {
            sessionNotes.setText("A new active baseline requires a stopped session with at least two accepted attempts and complete P/I/D values.\n\n" + buildSessionNotes());
            return;
        }
        archiveCurrentIteration(copyNextCandidate ? "Prepared next candidate" : "Kept as new baseline");
        LiveCaptureProfile profile = activeSettings == null
                ? (LiveCaptureProfile) captureProfileCombo.getSelectedItem()
                : activeSettings.getCaptureProfile();
        LiveMotionMode motion = activeSettings == null
                ? (LiveMotionMode) motionModeCombo.getSelectedItem()
                : activeSettings.getMotionMode();
        baselineSnapshot = new LiveBaselineSnapshot(sessionTuneSnapshot, summary, profile, motion,
                "Active iteration " + currentIterationNumber, configurationName, activeSettings);
        if (originalBaselineSnapshot == null || !originalBaselineSnapshot.isForConfiguration(configurationName)) {
            originalBaselineSnapshot = baselineSnapshot;
        }
        String action = "Stored iteration " + currentIterationNumber + " as the active baseline.";
        if (copyNextCandidate) {
            if (!hasActionableCandidate(latestLiveRecommendation)) {
                sessionNotes.setText(action + " No further candidate is currently supported by the data. No ECU value was changed.\n\n" + buildSessionNotes());
            } else {
                String candidate = latestLiveRecommendation.getProposed().toSignature();
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(candidate), null);
                sessionNotes.setText(action + " Copied the next read-only candidate " + candidate
                        + ". Enter it manually in TunerStudio RAM, then press Start live observer for the next iteration. Do not burn it yet.\n\n"
                        + buildSessionNotes());
            }
        } else {
            sessionNotes.setText(action + " The original rollback baseline was retained. No ECU value was changed.\n\n" + buildSessionNotes());
        }
        updateLiveRecommendationUi();
        updateControls();
    }

    private void copyRollbackReference() {
        if (originalBaselineSnapshot == null || originalBaselineSnapshot.getGains() == null
                || !originalBaselineSnapshot.getGains().isComplete()) return;
        if (!originalBaselineSnapshot.isForConfiguration(configurationName)) {
            sessionNotes.setText("Rollback blocked: the original baseline belongs to ECU configuration "
                    + displayConfiguration(originalBaselineSnapshot.getConfigurationIdentity())
                    + " while the active configuration is " + displayConfiguration(configurationName)
                    + ". Store a baseline for the active configuration before using rollback/reference actions.\n\n"
                    + buildSessionNotes());
            return;
        }
        String text = originalBaselineSnapshot.getGains().toSignature();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        sessionNotes.setText("Copied the original rollback gains: " + text
                + ". The plugin did not change the ECU.\n\n" + buildSessionNotes());
    }

    private void clearTuningHistory() {
        tuningHistory.clear();
        iterationModel.setRows(tuningHistory);
        iterationTable.clearSelection();
        iterationDetails.setText("Iteration history cleared. Baselines were retained.");
        historyStatusValue.setText("No tuning iterations archived");
        updateLiveRecommendationUi();
        sessionNotes.setText("Cleared the in-memory tuning iteration history. Active and rollback baselines were retained. No ECU values or saved files were changed.\n\n"
                + buildSessionNotes());
        updateControls();
    }

    private void exportTuningHistory() {
        if (exportInProgress) return;
        if (!isRunning()) archiveCurrentIteration("Tuning history export");
        if (tuningHistory.isEmpty()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Save complete live tuning history CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("pid-live-tuning-history-"
                + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            file = new File(file.getParentFile(), file.getName() + ".csv");
        }
        final File exportFile = file;
        final TuningHistoryCsvSnapshot snapshot = captureTuningHistoryCsvSnapshot();
        exportInProgress = true;
        sessionNotes.setText("Saving " + snapshot.iterations.size() + " tuning iteration(s) to "
                + exportFile.getAbsolutePath() + "…");
        updateControls();
        new CsvExportWorker(new CsvExportWorker.Task() {
            @Override
            public void run() throws Exception {
                writeTuningHistoryCsv(exportFile, snapshot);
            }
        }, new CsvExportWorker.Completion() {
            @Override
            public void succeeded() {
                exportInProgress = false;
                sessionNotes.setText("Saved " + snapshot.iterations.size() + " tuning iteration(s) with full live samples to "
                        + exportFile.getAbsolutePath() + ". No ECU values were changed.\n\n" + buildSessionNotes());
                updateControls();
            }

            @Override
            public void failed(Throwable failure) {
                exportInProgress = false;
                sessionNotes.setText("Unable to save tuning history: " + safeMessage(failure));
                updateControls();
            }
        }).execute();
    }

    private void writeTuningHistoryCsv(File file, TuningHistoryCsvSnapshot snapshot) throws Exception {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            writer.println("# EPICEFI PID Autotune complete live tuning history " + PidAutotunePlugin.VERSION);
            writer.println("# Original rollback gains: " + snapshot.originalRollbackGains);
            writer.println("# Active baseline gains: " + snapshot.activeBaselineGains);
            writer.println("Record Type,Iteration,Configuration,Wall Clock,Elapsed s,P,I,D,Accepted,Good,Attempt,Accepted Attempt,Quality,Result Code,Capture State,Event Marker,RPM,Target RPM,Base Idle,Commanded Idle,P Term,I Term,D Term,PID Output,TPS,CLT,MAP,Battery,VSS,Fan,DFCO,Settling s,MAE RPM,RPM Deviation,Correction Limit %,Reversals /s,D Span,Candidate,vs Original,vs Previous,Decision,Notes");
            for (LiveIterationRecord iteration : snapshot.iterations) {
                LiveSessionSummary summary = iteration.getSummary();
                LiveExperimentalRecommendation recommendation = iteration.getRecommendation();
                writer.println(csvRow(
                        "SUMMARY", String.valueOf(iteration.getIterationNumber()), iteration.getConfigurationIdentity(), formatWallClock(iteration.getStartedMillis()), "",
                        tuneValue(iteration.getGains(), 'P'), tuneValue(iteration.getGains(), 'I'), tuneValue(iteration.getGains(), 'D'),
                        String.valueOf(summary.getAcceptedCount()), String.valueOf(summary.getGoodCount()), "", "", "", "", "", "",
                        "", formatCsv(summary.getMedianTargetRpm()), "", "", "", "", "", "", "",
                        formatCsv(summary.getMedianCoolant()), "", "", "", String.valueOf(summary.isFanOn()), "",
                        formatCsv(summary.getMedianSettlingSeconds()), formatCsv(summary.getMedianMeanAbsoluteErrorRpm()),
                        formatCsv(summary.getMedianRpmStandardDeviation()), formatCsv(summary.getMedianCorrectionLimitPercent()),
                        formatCsv(summary.getMedianCorrectionReversalsPerSecond()), formatCsv(summary.getMedianDTermSpan()),
                        recommendation == null || recommendation.getProposed() == null ? "" : recommendation.getProposed().toSignature(),
                        comparisonStatus(iteration.getOriginalComparison()), comparisonStatus(iteration.getPreviousComparison()),
                        iteration.getDecision(), iteration.getArchiveReason()));
                for (LiveAttempt attempt : iteration.getAttempts()) {
                    writer.println(csvRow(
                            "ATTEMPT", String.valueOf(iteration.getIterationNumber()), iteration.getConfigurationIdentity(), "", formatCsv(attempt.getTriggerSeconds()),
                            tuneValue(iteration.getGains(), 'P'), tuneValue(iteration.getGains(), 'I'), tuneValue(iteration.getGains(), 'D'),
                            "", "", String.valueOf(attempt.getNumber()), String.valueOf(attempt.isAccepted()), attempt.getQuality(),
                            attempt.getResultCode(), "", "", formatCsv(attempt.getPeakRpm()), formatCsv(attempt.getTargetRpm()),
                            "", "", "", "", "", "", "", formatCsv(attempt.getMeanCoolant()), "", "", "",
                            String.valueOf(attempt.isFanOn()), "", formatCsv(attempt.getSettlingSeconds()),
                            formatCsv(attempt.getMeanAbsoluteError()), formatCsv(attempt.getRpmStandardDeviation()),
                            formatCsv(attempt.getCorrectionLimitPercent()), formatCsv(attempt.getCorrectionReversalsPerSecond()),
                            formatCsv(attempt.getDTermSpan()), "", comparisonStatus(iteration.getOriginalComparison()),
                            comparisonStatus(iteration.getPreviousComparison()), iteration.getDecision(), attempt.getReason()));
                }
                for (LiveSessionRecord record : iteration.getSamples()) {
                    LiveSample sample = record.getSample();
                    writer.println(csvRow(
                            "SAMPLE", String.valueOf(iteration.getIterationNumber()), iteration.getConfigurationIdentity(), formatWallClock(record.getWallClockMillis()),
                            formatCsv(sample.getTimeSeconds()), tuneValue(record.getGains(), 'P'), tuneValue(record.getGains(), 'I'), tuneValue(record.getGains(), 'D'),
                            "", "", String.valueOf(record.getAttemptNumber()), "", "", record.getResultCode(),
                            formatState(record.getState()), record.getEventMarker(), formatCsv(sample.get(LiveChannel.RPM)),
                            formatCsv(sample.get(LiveChannel.IDLE_TARGET)), formatCsv(sample.get(LiveChannel.BASE_IDLE_POSITION)),
                            formatCsv(sample.get(LiveChannel.CURRENT_IDLE_POSITION)), formatCsv(sample.get(LiveChannel.P_TERM)),
                            formatCsv(sample.get(LiveChannel.I_TERM)), formatCsv(sample.get(LiveChannel.D_TERM)),
                            formatCsv(sample.get(LiveChannel.PID_OUTPUT)), formatCsv(sample.get(LiveChannel.TPS)),
                            formatCsv(sample.get(LiveChannel.CLT)), formatCsv(sample.get(LiveChannel.MAP)),
                            formatCsv(sample.get(LiveChannel.BATTERY)), formatCsv(sample.get(LiveChannel.VEHICLE_SPEED)),
                            formatCsv(sample.get(LiveChannel.FAN1)), formatCsv(sample.get(LiveChannel.DFCO)),
                            "", "", "", "", "", "", "", "", "", iteration.getDecision(), ""));
                }
            }
        } finally {
            writer.close();
        }
    }

    private static String csvRow(String... values) {
        StringBuilder row = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) row.append(',');
            row.append(csvText(values[i]));
        }
        return row.toString();
    }

    private static String tuneValue(TuneSnapshot tune, char gain) {
        if (tune == null || !tune.isComplete()) return "";
        if (gain == 'P') return formatCsv(tune.getP());
        if (gain == 'I') return formatCsv(tune.getI());
        return formatCsv(tune.getD());
    }

    private static String comparisonStatus(LiveComparisonResult result) {
        return result == null ? "" : result.getStatus();
    }

    private void copyLiveRecommendationAndComparison() {
        if (latestLiveRecommendation == null || latestLiveComparison == null) return;
        StringBuilder text = new StringBuilder();
        text.append("EPICEFI PID Autotune live experimental preview ")
                .append(PidAutotunePlugin.VERSION).append('\n');
        text.append("Status: ").append(latestLiveRecommendation.getStatus()).append('\n');
        text.append("Confidence: ")
                .append(LiveSample.isFinite(latestLiveRecommendation.getConfidencePercent())
                        ? ONE.format(latestLiveRecommendation.getConfidencePercent()) + "%" : "Unavailable")
                .append(" (live-only confidence is capped at 70%)\n");
        text.append("Current gains: ").append(formatTune(sessionTuneSnapshot)).append('\n');
        text.append("Proposed gains: ").append(formatTune(latestLiveRecommendation.getProposed())).append('\n');
        text.append("Changed gain: ").append(latestLiveRecommendation.getChangedGain()).append(' ')
                .append(signedPercent(latestLiveRecommendation.getChangePercent())).append('\n');
        text.append("Reason: ").append(latestLiveRecommendation.getReason()).append('\n');
        text.append("Expected effect: ").append(latestLiveRecommendation.getExpectedEffect()).append("\n\n");
        text.append(latestLiveRecommendation.getDetails()).append("\n\n");
        text.append("Before/after comparison: ").append(latestLiveComparison.getStatus()).append('\n');
        text.append(latestLiveComparison.getSummary()).append('\n');
        text.append(latestLiveComparison.getDetails()).append("\n\n");
        text.append("Safety: read-only preview. Values must be entered manually. Do not burn a test value before the comparison result is reviewed.");
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        sessionNotes.setText("Copied the live experimental recommendation and comparison. No ECU value was changed.\n\n"
                + buildSessionNotes());
    }

    private static boolean sameConfigurationIdentity(String left, String right) {
        String normalizedLeft = normalizeConfiguration(left);
        String normalizedRight = normalizeConfiguration(right);
        return !normalizedLeft.isEmpty() && normalizedLeft.equals(normalizedRight);
    }

    private static String displayConfiguration(String value) {
        String normalized = normalizeConfiguration(value);
        return normalized.isEmpty() ? "unidentified" : "'" + normalized + "'";
    }

    private static String normalizeConfiguration(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean hasActionableCandidate(LiveExperimentalRecommendation recommendation) {
        if (recommendation == null || !recommendation.isProposalAvailable()
                || recommendation.getProposed() == null || recommendation.getCurrent() == null
                || !recommendation.getProposed().isComplete() || !recommendation.getCurrent().isComplete()) return false;
        if ("None".equalsIgnoreCase(recommendation.getChangedGain())) return false;
        return Math.abs(recommendation.getProposed().getP() - recommendation.getCurrent().getP()) > 0.00005
                || Math.abs(recommendation.getProposed().getI() - recommendation.getCurrent().getI()) > 0.00005
                || Math.abs(recommendation.getProposed().getD() - recommendation.getCurrent().getD()) > 0.00005;
    }

    private static String formatTune(TuneSnapshot snapshot) {
        return snapshot == null ? "Unavailable" : snapshot.toSignature();
    }

    private static String signedPercent(double value) {
        if (!LiveSample.isFinite(value)) return "—";
        return (value > 0.0 ? "+" : "") + ONE.format(value) + "%";
    }

    private LiveCaptureSettings readSettingsFromUiAndTune() {
        double minimumClt = parseRange(minimumCltField, "Minimum CLT", -40.0, 150.0);
        double maximumClt = parseRange(maximumCltField, "Maximum CLT", -40.0, 150.0);
        if (minimumClt >= maximumClt) throw new IllegalArgumentException("Minimum CLT must be lower than maximum CLT.");
        double revMinimum = parseRange(preferredRevMinimumField, "Preferred rev minimum", 1000.0, 5000.0);
        double revMaximum = parseRange(preferredRevMaximumField, "Preferred rev maximum", 1200.0, 6000.0);
        if (revMinimum >= revMaximum) throw new IllegalArgumentException("Preferred rev minimum must be lower than maximum.");
        double baselineBand = parseRange(baselineBandField, "Baseline band", 20.0, 400.0);
        double settlingBand = parseRange(settlingBandField, "Settling band", 20.0, 300.0);
        if (settlingBand > baselineBand) throw new IllegalArgumentException("Settling band must not be wider than the baseline band.");
        double exitHysteresis = parseRange(exitHysteresisField, "Exit hysteresis", 0.0, 150.0);
        double observation = parseRange(observationSecondsField, "Stable observation", 5.0, 30.0);
        double recoveryTimeout = parseRange(recoveryTimeoutField, "Recovery timeout", 10.0, 60.0);
        LiveCaptureProfile profile = resolveCaptureProfile(
                (LiveCaptureProfile) captureProfileCombo.getSelectedItem(), baselineBand, settlingBand,
                exitHysteresis, observation, recoveryTimeout);
        if (profile == LiveCaptureProfile.CUSTOM && captureProfileCombo.getSelectedItem() != LiveCaptureProfile.CUSTOM) {
            captureProfileCombo.setSelectedItem(LiveCaptureProfile.CUSTOM);
        }
        LiveMotionMode mode = (LiveMotionMode) motionModeCombo.getSelectedItem();
        if (mode == LiveMotionMode.MANUAL_STATIONARY && !manualStationaryCheck.isSelected()) {
            throw new IllegalArgumentException("Manual stationary mode requires the stationary confirmation checkbox.");
        }
        return new LiveCaptureSettings(
                readScalar("idlePidDeactivationTpsThreshold", 2.0),
                readScalar("maxIdleVss", 15.0),
                readScalar("idlePidRpmUpperLimit", 200.0),
                readScalar("idlePidRpmDeadZone", 10.0),
                readScalar("idleRpmPid_minValue", -10.0),
                readScalar("idleRpmPid_maxValue", 10.0),
                minimumClt, maximumClt, revMinimum, revMaximum,
                baselineBand, settlingBand, exitHysteresis, observation, recoveryTimeout,
                profile, mode, manualStationaryCheck.isSelected());
    }

    private static LiveCaptureProfile resolveCaptureProfile(
            LiveCaptureProfile selected, double baseline, double settling, double hysteresis,
            double observation, double timeout) {
        if (selected == null || selected == LiveCaptureProfile.CUSTOM) return LiveCaptureProfile.CUSTOM;
        if (Math.abs(selected.getBaselineBandRpm() - baseline) > 0.01
                || Math.abs(selected.getSettlingBandRpm() - settling) > 0.01
                || Math.abs(selected.getExitHysteresisRpm() - hysteresis) > 0.01
                || Math.abs(selected.getObservationSeconds() - observation) > 0.01
                || Math.abs(selected.getRecoveryTimeoutSeconds() - timeout) > 0.01) {
            return LiveCaptureProfile.CUSTOM;
        }
        return selected;
    }

    private void applyCaptureProfile(LiveCaptureProfile profile) {
        if (profile == null || profile == LiveCaptureProfile.CUSTOM) return;
        baselineBandField.setText(String.valueOf((int) Math.round(profile.getBaselineBandRpm())));
        settlingBandField.setText(String.valueOf((int) Math.round(profile.getSettlingBandRpm())));
        exitHysteresisField.setText(String.valueOf((int) Math.round(profile.getExitHysteresisRpm())));
        observationSecondsField.setText(String.valueOf((int) Math.round(profile.getObservationSeconds())));
        recoveryTimeoutField.setText(String.valueOf((int) Math.round(profile.getRecoveryTimeoutSeconds())));
    }

    private double readScalar(String parameterName, double fallback) {
        if (parameterServer == null || configurationName == null) return fallback;
        try {
            ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, parameterName);
            if (parameter == null) return fallback;
            double value = parameter.getScalarValue();
            return LiveSample.isFinite(value) ? value : fallback;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private SessionCsvSnapshot captureSessionCsvSnapshot() {
        LiveExperimentalRecommendation recommendation = latestLiveRecommendation;
        String baselineGains = baselineSnapshot == null || baselineSnapshot.getGains() == null
                ? "" : baselineSnapshot.getGains().toSignature();
        return new SessionCsvSnapshot(
                String.valueOf(motionModeCombo.getSelectedItem()),
                String.valueOf(activeSettings == null ? captureProfileCombo.getSelectedItem() : activeSettings.getCaptureProfile()),
                currentIterationNumber,
                sessionStartedWallClockMillis,
                formatTune(sessionTuneSnapshot),
                recommendation == null ? "" : recommendation.getStatus(),
                recommendation == null ? "" : formatTune(recommendation.getProposed()),
                recommendation == null ? "" : recommendation.getChangedGain() + " " + signedPercent(recommendation.getChangePercent()),
                baselineGains,
                latestLiveComparison == null ? "" : latestLiveComparison.getStatus(),
                activeSettings,
                sessionRecords);
    }

    private TuningHistoryCsvSnapshot captureTuningHistoryCsvSnapshot() {
        String original = originalBaselineSnapshot == null || originalBaselineSnapshot.getGains() == null
                ? "none" : originalBaselineSnapshot.getGains().toSignature();
        String active = baselineSnapshot == null || baselineSnapshot.getGains() == null
                ? "none" : baselineSnapshot.getGains().toSignature();
        return new TuningHistoryCsvSnapshot(original, active, tuningHistory);
    }

    private void exportSession(final boolean addToDataset) {
        if (sessionSamples.isEmpty() || exportInProgress) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(addToDataset ? "Save live session and add it to the dataset" : "Save live session CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("pid-live-session-" + new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss").format(new Date()) + ".csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (!file.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) file = new File(file.getParentFile(), file.getName() + ".csv");
        final File exportFile = file;
        final SessionCsvSnapshot snapshot = captureSessionCsvSnapshot();
        exportInProgress = true;
        sessionNotes.setText("Saving " + snapshot.records.size() + " live samples to " + exportFile.getAbsolutePath() + "…");
        updateControls();
        new CsvExportWorker(new CsvExportWorker.Task() {
            @Override
            public void run() throws Exception {
                writeSessionCsv(exportFile, snapshot);
            }
        }, new CsvExportWorker.Completion() {
            @Override
            public void succeeded() {
                exportInProgress = false;
                try {
                    sessionNotes.setText("Saved " + snapshot.records.size() + " live samples to " + exportFile.getAbsolutePath()
                            + ".\nNo ECU values were changed.");
                    if (exportListener != null) exportListener.sessionExported(exportFile, addToDataset);
                } catch (Exception ex) {
                    sessionNotes.setText("Saved the live-session CSV, but the post-export action failed: " + safeMessage(ex));
                } finally {
                    updateControls();
                }
            }

            @Override
            public void failed(Throwable failure) {
                exportInProgress = false;
                sessionNotes.setText("Unable to save live session: " + safeMessage(failure));
                updateControls();
            }
        }).execute();
    }

    private void writeSessionCsv(File file, SessionCsvSnapshot snapshot) throws Exception {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8));
        try {
            writer.println("# EPICEFI PID Autotune live guided-capture session " + PidAutotunePlugin.VERSION);
            writer.println("# Motion basis: " + snapshot.motionBasis);
            writer.println("# Capture profile: " + snapshot.captureProfile);
            writer.println("# Live iteration: " + snapshot.iterationNumber);
            writer.println("# Session started wall clock: " + formatWallClock(snapshot.startedWallClockMillis));
            writer.println("# PID gains at observer start: " + snapshot.gainsAtStart);
            if (!snapshot.recommendationStatus.isEmpty()) {
                writer.println("# Experimental preview status: " + snapshot.recommendationStatus);
                writer.println("# Experimental proposed gains: " + snapshot.proposedGains);
                writer.println("# Experimental changed gain: " + snapshot.changedGain);
            }
            if (!snapshot.baselineGains.isEmpty()) {
                writer.println("# Stored live baseline gains: " + snapshot.baselineGains);
            }
            if (!snapshot.comparisonStatus.isEmpty()) {
                writer.println("# Live comparison result: " + snapshot.comparisonStatus);
            }
            if (snapshot.settings != null) {
                writer.println("# Capture tolerances: baseline ±" + formatCsv(snapshot.settings.getBaselineErrorBandRpm())
                        + " RPM; settling ±" + formatCsv(snapshot.settings.getSettlingBandRpm())
                        + " RPM; exit hysteresis " + formatCsv(snapshot.settings.getExitHysteresisRpm())
                        + " RPM; observation " + formatCsv(snapshot.settings.getStableObservationSeconds())
                        + " s; recovery timeout " + formatCsv(snapshot.settings.getMaximumRecoverySeconds()) + " s");
                writer.println("# DFCO rule: allowed before PID measurement; rejected only if it reactivates after measured recovery begins.");
            }
            writer.println("Time,Wall Clock,Iteration,P Gain,I Gain,D Gain,RPM,Idle: Target RPM,idle: base value,Idle: Position,Idle: Closed loop active,Idle: idling,idleStatus_pTerm,idleStatus_iTerm,idleStatus_dTerm,idleStatus_output,TPS,CLT,MAP,Batt V,Vehicle Speed,fan1On,dfcoActive,Capture State,Attempt Number,Event Marker,Result Code,Capture Profile");
            writer.println("s,ISO-8601,count,,,,RPM,RPM,%,%,bool,bool,,,,,%,°C,kPa,V,kph,bool,bool,text,count,text,text,text");
            for (LiveSessionRecord record : snapshot.records) {
                LiveSample sample = record.getSample();
                writer.print(formatCsv(sample.getTimeSeconds()));
                writer.print(','); writer.print(csvText(formatWallClock(record.getWallClockMillis())));
                writer.print(','); writer.print(record.getIterationNumber());
                writer.print(','); writer.print(tuneValue(record.getGains(), 'P'));
                writer.print(','); writer.print(tuneValue(record.getGains(), 'I'));
                writer.print(','); writer.print(tuneValue(record.getGains(), 'D'));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.RPM)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.IDLE_TARGET)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.BASE_IDLE_POSITION)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.CURRENT_IDLE_POSITION)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.CLOSED_LOOP_ACTIVE)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.IDLING)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.P_TERM)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.I_TERM)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.D_TERM)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.PID_OUTPUT)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.TPS)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.CLT)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.MAP)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.BATTERY)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.VEHICLE_SPEED)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.FAN1)));
                writer.print(','); writer.print(formatCsv(sample.get(LiveChannel.DFCO)));
                writer.print(','); writer.print(csvText(formatState(record.getState())));
                writer.print(','); writer.print(record.getAttemptNumber());
                writer.print(','); writer.print(csvText(record.getEventMarker()));
                writer.print(','); writer.print(csvText(record.getResultCode()));
                writer.print(','); writer.println(csvText(String.valueOf(record.getProfile())));
            }
        } finally {
            writer.close();
        }
    }

    private void copySummary() {
        if (sessionSamples.isEmpty() && engine.getAttempts().isEmpty()) return;
        StringBuilder text = new StringBuilder();
        text.append("EPICEFI PID Autotune live session ").append(PidAutotunePlugin.VERSION).append('\n');
        text.append("Configuration: ").append(configurationName).append('\n');
        text.append("Motion basis: ").append(motionModeCombo.getSelectedItem()).append('\n');
        text.append("Capture profile: ").append(activeSettings == null ? captureProfileCombo.getSelectedItem() : activeSettings.getCaptureProfile()).append('\n');
        text.append("PID gains at observer start: ").append(formatTune(sessionTuneSnapshot)).append('\n');
        if (latestLiveRecommendation != null) {
            text.append("Experimental preview: ").append(latestLiveRecommendation.getStatus()).append("; proposed ")
                    .append(formatTune(latestLiveRecommendation.getProposed())).append("; changed ")
                    .append(latestLiveRecommendation.getChangedGain()).append(' ')
                    .append(signedPercent(latestLiveRecommendation.getChangePercent())).append('\n');
        }
        if (baselineSnapshot != null) text.append("Stored baseline: ").append(baselineSnapshot.getGains().toSignature()).append('\n');
        if (latestLiveComparison != null) text.append("Comparison: ").append(latestLiveComparison.getStatus()).append('\n');
        if (activeSettings != null) {
            text.append("Capture tolerances: baseline ±").append(ONE.format(activeSettings.getBaselineErrorBandRpm()))
                    .append(" RPM; settling ±").append(ONE.format(activeSettings.getSettlingBandRpm()))
                    .append(" RPM; exit hysteresis ").append(ONE.format(activeSettings.getExitHysteresisRpm()))
                    .append(" RPM; observation ").append(ONE.format(activeSettings.getStableObservationSeconds()))
                    .append(" s; recovery timeout ").append(ONE.format(activeSettings.getMaximumRecoverySeconds())).append(" s\n");
            text.append("DFCO rule: allowed during throttle-release overrun before PID measurement; rejected only if active after measurement starts.\n");
        }
        text.append("Samples: ").append(sessionSamples.size()).append('\n');
        text.append("Attempts: ").append(engine.getAcceptedCount()).append(" accepted (")
                .append(engine.getGoodCount()).append(" Good), ").append(engine.getRejectedCount()).append(" rejected\n");
        text.append("Last result: ").append(engine.getLastResult()).append("\n\n");
        text.append("#\tResult\tQuality\tCode\tTrigger s\tPeak RPM\tTarget RPM\tSettling s\tMAE RPM\tRPM deviation\tOvershoot RPM\tUndershoot RPM\tLimit %\tReversals/s\tD span\tReason\n");
        for (LiveAttempt attempt : engine.getAttempts()) {
            text.append(attempt.getNumber()).append('\t')
                    .append(attempt.isAccepted() ? "Accepted" : "Rejected").append('\t')
                    .append(attempt.getQuality()).append('\t')
                    .append(attempt.getResultCode()).append('\t')
                    .append(formatCsv(attempt.getTriggerSeconds())).append('\t')
                    .append(formatCsv(attempt.getPeakRpm())).append('\t')
                    .append(formatCsv(attempt.getTargetRpm())).append('\t')
                    .append(formatCsv(attempt.getSettlingSeconds())).append('\t')
                    .append(formatCsv(attempt.getMeanAbsoluteError())).append('\t')
                    .append(formatCsv(attempt.getRpmStandardDeviation())).append('\t')
                    .append(formatCsv(attempt.getOvershootRpm())).append('\t')
                    .append(formatCsv(attempt.getUndershootRpm())).append('\t')
                    .append(formatCsv(attempt.getCorrectionLimitPercent())).append('\t')
                    .append(formatCsv(attempt.getCorrectionReversalsPerSecond())).append('\t')
                    .append(formatCsv(attempt.getDTermSpan())).append('\t')
                    .append(attempt.getReason()).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text.toString()), null);
        sessionNotes.setText("Copied live-session summary. No ECU values were changed.\n\n" + buildSessionNotes());
    }

    private List<LiveSample> displaySamples() {
        if (sessionSamples.size() <= 2000) return new ArrayList<LiveSample>(sessionSamples);
        return new ArrayList<LiveSample>(sessionSamples.subList(sessionSamples.size() - 2000, sessionSamples.size()));
    }

    private String buildSessionNotes() {
        StringBuilder text = new StringBuilder();
        text.append(subscriptionDetails);
        text.append("\n\n").append(engine.getLastResult());
        text.append("\n\nThe plugin is read-only. It does not change gains, idle output, RAM, or flash.");
        if (latestLiveRecommendation != null) {
            text.append("\nExperimental preview: ").append(latestLiveRecommendation.getStatus());
            if (latestLiveRecommendation.isProposalAvailable()) {
                text.append(" — ").append(latestLiveRecommendation.getChangedGain()).append(' ')
                        .append(signedPercent(latestLiveRecommendation.getChangePercent())).append("; ")
                        .append(formatTune(latestLiveRecommendation.getProposed()));
            }
        }
        if (latestLiveComparison != null) text.append("\nComparison: ").append(latestLiveComparison.getStatus()).append('.');
        if (!sessionSamples.isEmpty()) text.append("\nLive samples retained in memory: ").append(sessionSamples.size()).append('.');
        return text.toString().trim();
    }

    private static String buildSubscriptionDetails(LiveSubscriptionReport report) {
        StringBuilder text = new StringBuilder();
        text.append("Subscribed live channels: ").append(report.getSubscribed().size()).append('.');
        if (!report.getMissingRequired().isEmpty()) text.append("\nMissing required: ").append(report.getMissingRequired());
        if (!report.getMissingOptional().isEmpty()) text.append("\nMissing optional: ").append(report.getMissingOptional());
        text.append("\nOptional missing channels reduce diagnostics but do not necessarily block guided capture.");
        return text.toString();
    }

    private static String formatReadiness(LiveReadiness readiness) {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, String> row : readiness.getRows().entrySet()) {
            text.append(row.getKey()).append(": ").append(row.getValue()).append('\n');
        }
        return text.toString().trim();
    }

    private static String formatLiveValues(LiveSample sample, LiveCaptureSettings settings) {
        String motion;
        if (settings != null && settings.getMotionMode() == LiveMotionMode.MANUAL_STATIONARY) {
            motion = settings.isManualStationaryConfirmed() ? "stationary confirmed" : "stationary unconfirmed";
        } else {
            motion = sample.has(LiveChannel.VEHICLE_SPEED) ? ONE.format(sample.get(LiveChannel.VEHICLE_SPEED)) + " km/h" : "VSS unavailable";
        }
        return "RPM " + format(sample.get(LiveChannel.RPM))
                + " / target " + format(sample.get(LiveChannel.IDLE_TARGET))
                + "; TPS " + format(sample.get(LiveChannel.TPS)) + "%"
                + "; CLT " + format(sample.get(LiveChannel.CLT)) + "°C"
                + "; fan " + (sample.has(LiveChannel.FAN1) && sample.get(LiveChannel.FAN1) >= 0.5 ? "on" : "off")
                + "; DFCO " + (sample.has(LiveChannel.DFCO) && sample.get(LiveChannel.DFCO) >= 0.5 ? "active" : "inactive")
                + "; correction " + format(sample.getCorrection())
                + "; " + motion
                + (settings == null ? "" : "; " + settings.getCaptureProfile());
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && configurationName != null && !configurationName.isEmpty();
        boolean running = isRunning();
        startButton.setEnabled(connected && !running);
        stopButton.setEnabled(running);
        abortButton.setEnabled(running && isAttemptState(engine.getState()));
        resetButton.setEnabled(!running || !isAttemptState(engine.getState()));
        copyButton.setEnabled(!sessionSamples.isEmpty() || !engine.getAttempts().isEmpty());
        exportButton.setEnabled(!exportInProgress && !sessionSamples.isEmpty());
        exportAndAddButton.setEnabled(!exportInProgress && !sessionSamples.isEmpty());
        setBaselineButton.setEnabled(engine.getAcceptedCount() >= 2
                && sessionTuneSnapshot != null && sessionTuneSnapshot.isComplete());
        clearBaselineButton.setEnabled(baselineSnapshot != null || originalBaselineSnapshot != null);
        copyLiveRecommendationButton.setEnabled(latestLiveRecommendation != null);
        keepAsBaselineButton.setEnabled(!running && engine.getAcceptedCount() >= 2
                && sessionTuneSnapshot != null && sessionTuneSnapshot.isComplete());
        testNextCandidateButton.setEnabled(!running && engine.getAcceptedCount() >= 2
                && hasActionableCandidate(latestLiveRecommendation));
        copyRollbackButton.setEnabled(originalBaselineSnapshot != null
                && originalBaselineSnapshot.getGains() != null && originalBaselineSnapshot.getGains().isComplete()
                && originalBaselineSnapshot.isForConfiguration(configurationName));
        exportHistoryButton.setEnabled(!exportInProgress && (!tuningHistory.isEmpty() || (!running && !sessionSamples.isEmpty())));
        clearHistoryButton.setEnabled(!exportInProgress && !tuningHistory.isEmpty());
        captureProfileCombo.setEnabled(!running);
        motionModeCombo.setEnabled(!running);
        minimumCltField.setEnabled(!running);
        maximumCltField.setEnabled(!running);
        preferredRevMinimumField.setEnabled(!running);
        preferredRevMaximumField.setEnabled(!running);
        baselineBandField.setEnabled(!running);
        settlingBandField.setEnabled(!running);
        exitHysteresisField.setEnabled(!running);
        observationSecondsField.setEnabled(!running);
        recoveryTimeoutField.setEnabled(!running);
        LiveMotionMode mode = (LiveMotionMode) motionModeCombo.getSelectedItem();
        manualStationaryCheck.setEnabled(mode == LiveMotionMode.MANUAL_STATIONARY);
    }

    private boolean isRunning() { return subscription != null && subscription.isStarted(); }

    private static boolean isAttemptState(LiveCaptureState state) {
        return state == LiveCaptureState.REV_IN_PROGRESS
                || state == LiveCaptureState.WAITING_FOR_IDLE_CONTROL
                || state == LiveCaptureState.MEASURING_RECOVERY
                || state == LiveCaptureState.CONFIRMING_STABLE_IDLE;
    }

    private void configureAttemptColumns() {
        TableColumnModel columns = attemptTable.getColumnModel();
        int[] widths = {38, 72, 150, 160, 70, 78, 72, 76, 76, 76, 82, 82, 68, 84, 72, 360};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) columns.getColumn(i).setPreferredWidth(widths[i]);
    }

    private void configureIterationColumns() {
        TableColumnModel columns = iterationTable.getColumnModel();
        int[] widths = {38, 76, 165, 68, 55, 82, 72, 260, 145, 145, 150};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void addControl(JPanel panel, String labelText, JComponent control, String tooltip) {
        JLabel label = new JLabel(labelText + ":");
        label.setToolTipText(tooltip);
        control.setToolTipText(tooltip);
        panel.add(label);
        panel.add(control);
    }

    private static void addReadOnlyRow(JPanel panel, GridBagConstraints base, int row, String labelText, JLabel value, String tooltip) {
        GridBagConstraints left = (GridBagConstraints) base.clone();
        left.gridx = 0; left.gridy = row; left.weightx = 0.0;
        JLabel label = new JLabel(labelText + ":", SwingConstants.RIGHT);
        label.setToolTipText(tooltip);
        value.setToolTipText(tooltip);
        panel.add(label, left);
        GridBagConstraints right = (GridBagConstraints) base.clone();
        right.gridx = 1; right.gridy = row; right.weightx = 1.0;
        panel.add(value, right);
    }

    private static GridBagConstraints constraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(2, 4, 2, 4);
        return c;
    }

    private static double parseRange(JTextField field, String name, double minimum, double maximum) {
        try {
            double value = Double.parseDouble(field.getText().trim());
            if (!LiveSample.isFinite(value) || value < minimum || value > maximum) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum + ".");
        }
    }

    private static String formatState(LiveCaptureState state) {
        if (state == null) return "—";
        return state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static String format(double value) {
        return LiveSample.isFinite(value) ? ONE.format(value) : "—";
    }

    private static String formatCsv(double value) {
        if (!LiveSample.isFinite(value)) return "";
        synchronized (THREE) {
            return THREE.format(value).replace(',', '.');
        }
    }

    private static String csvText(String value) {
        String text = value == null ? "" : value;
        if (text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0) {
            return "\"" + text.replace("\"", "\"\"") + "\"";
        }
        return text;
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "Unknown error";
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
