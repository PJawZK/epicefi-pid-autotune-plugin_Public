/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.efiAnalytics.plugin.ecu.ControllerAccess
 *  se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult
 *  se.anders.tunerstudio.pidautotune.analysis.IdleEvent
 *  se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment
 *  se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot
 *  se.anders.tunerstudio.pidautotune.live.LiveAttempt
 *  se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot
 *  se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile
 *  se.anders.tunerstudio.pidautotune.live.LiveCaptureState
 *  se.anders.tunerstudio.pidautotune.live.LiveChannel
 *  se.anders.tunerstudio.pidautotune.live.LiveComparisonResult
 *  se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation
 *  se.anders.tunerstudio.pidautotune.live.LiveMotionMode
 *  se.anders.tunerstudio.pidautotune.live.LiveReadiness
 *  se.anders.tunerstudio.pidautotune.live.LiveSample
 *  se.anders.tunerstudio.pidautotune.live.LiveSessionSummary
 *  se.anders.tunerstudio.pidautotune.ui.PidAutotunePanel
 *  se.anders.tunerstudio.pidautotune.ui.PidAutotuneWorkspacePanel
 */
package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTabbedPaneUI;
import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult;
import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;
import se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveAttempt;
import se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureState;
import se.anders.tunerstudio.pidautotune.live.LiveChannel;
import se.anders.tunerstudio.pidautotune.live.LiveComparisonResult;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveMotionMode;
import se.anders.tunerstudio.pidautotune.live.LiveReadiness;
import se.anders.tunerstudio.pidautotune.live.LiveSample;
import se.anders.tunerstudio.pidautotune.live.LiveSessionSummary;
import se.anders.tunerstudio.pidautotune.ui.IdleWorkflowAdapter;
import se.anders.tunerstudio.pidautotune.ui.PidAutotunePanel;
import se.anders.tunerstudio.pidautotune.ui.PidAutotuneWorkspacePanel;
import se.anders.tunerstudio.pidautotune.ui.UiTheme;

public final class IdleGuidedPanel
extends JPanel {
    private static final Font TITLE = new Font("Dialog", 1, 20);
    private static final Font H1 = new Font("Dialog", 1, 23);
    private static final Font H2 = new Font("Dialog", 1, 13);
    private static final Font H3 = new Font("Dialog", 1, 11);
    private static final Font BODY = new Font("Dialog", 0, 11);
    private static final Font SMALL = new Font("Dialog", 0, 9);
    private static final Font VALUE = new Font("Dialog", 1, 15);
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final DecimalFormat THREE = new DecimalFormat("0.000");
    private final IdleWorkflowAdapter adapter = new IdleWorkflowAdapter();
    private final PidAutotunePanel legacy = this.adapter.legacyPanel();
    private final JTabbedPane mainTabs = this.adapter.mainTabs();
    private final JTabbedPane liveTabs = this.adapter.liveTabs();
    private final CardLayout bodyCards = new CardLayout();
    private final JPanel body = new JPanel(this.bodyCards);
    private final JPanel tuneView = new JPanel(new BorderLayout(0, 7));
    private final JPanel secondaryView = new JPanel(new BorderLayout(0, 7));
    private final JPanel workflowCenter = new JPanel(new BorderLayout(8, 0));
    private final JPanel heroHolder = new JPanel(new BorderLayout());
    private final SidePanel sidePanel = new SidePanel();
    private final WorkflowStrip workflowStrip = new WorkflowStrip();
    private final MetricStrip liveStrip = new MetricStrip();
    private final JPanel footer = new JPanel(new BorderLayout(8, 0));
    private final JLabel nextLabel = new JLabel("Next: Start a baseline capture when the ECU and configuration are ready.");
    private final JPanel footerButtons = new JPanel(new FlowLayout(2, 6, 0));
    private final JButton primaryAction = new JButton("Start Baseline Capture");
    private final JButton secondaryAction = new JButton("Stop");
    private final JButton tertiaryAction = new JButton("Abort Attempt");
    private final JPanel secondaryHeader = new JPanel(new BorderLayout(8, 0));
    private final JLabel secondaryTitle = new JLabel("Sessions & Logs");
    private final JLabel secondarySub = new JLabel("History, log analysis and dataset qualification");
    private final JPanel secondaryChoices = new JPanel(new FlowLayout(0, 5, 0));
    private final CardLayout secondaryCards = new CardLayout();
    private final JPanel secondaryBody = new JPanel(this.secondaryCards);
    private final JPanel legacyHolder = new JPanel(new BorderLayout());
    private final CaptureSettingsPanel captureSettingsPanel = new CaptureSettingsPanel(false);
    private final Timer refreshTimer;
    private DestinationListener destinationListener;
    private Task task = Task.TUNE_IDLE;
    private SecondaryMode secondaryMode = SecondaryMode.ITERATIONS;
    private Phase phase = Phase.READINESS;
    private Focus focus = Focus.RECOVERY;
    private boolean focusManual;
    private boolean candidateReviewRequested;
    private boolean robustnessRequested;
    private String lastFingerprint = "";
    private String actionMessage = "";

    public IdleGuidedPanel() {
        super(new BorderLayout(0, 7));
        this.setBorder(BorderFactory.createEmptyBorder(7, 7, 7, 7));
        for (Component component : this.legacy.getComponents()) {
            if (component == this.mainTabs) continue;
            component.setVisible(false);
        }
        this.mainTabs.setUI(new HiddenTabbedPaneUI());
        this.mainTabs.setBorder(null);
        this.mainTabs.setOpaque(false);
        if (this.liveTabs != null) {
            this.liveTabs.setUI(new HiddenTabbedPaneUI());
            this.liveTabs.setBorder(null);
        }
        this.legacy.setBorder(null);
        this.buildTuneView();
        this.buildSecondaryView();
        this.body.add((Component)this.tuneView, "TUNE");
        this.body.add((Component)this.secondaryView, "SECONDARY");
        this.add((Component)this.body, "Center");
        this.buildPersistentSouth();
        this.selectTask(Task.TUNE_IDLE);
        this.refreshTimer = new Timer(400, actionEvent -> this.refresh());
        this.refreshTimer.setRepeats(true);
        this.refreshTimer.start();
        this.applyTheme();
    }

    public void setDestinationListener(DestinationListener destinationListener) {
        this.destinationListener = destinationListener;
    }

    public void connect(ControllerAccess controllerAccess, String string) {
        this.adapter.connect(controllerAccess, string);
        this.refresh();
    }

    public void disconnect() {
        this.adapter.disconnect();
        this.refresh();
    }

    public void setControllerSignature(String string) {
        this.adapter.setControllerSignature(string);
    }

    public void selectTask(Task task) {
        if (task == null) {
            return;
        }
        this.task = task;
        if (this.task == Task.TUNE_IDLE) {
            this.bodyCards.show(this.body, "TUNE");
        } else {
            this.bodyCards.show(this.body, "SECONDARY");
            if (this.task == Task.SESSIONS_LOGS) {
                this.configureSecondaryForSessions();
            } else {
                this.configureSecondaryForSetup();
            }
        }
        this.lastFingerprint = "";
        this.refresh();
        this.applyTheme();
    }

    public Task getTask() {
        return this.task;
    }

    private void buildTuneView() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 6));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel(new GridLayout(0, 1, 0, 1));
        jPanel2.setOpaque(false);
        JLabel jLabel = new JLabel("Idle Closed Loop PID");
        jLabel.setFont(TITLE);
        JLabel jLabel2 = new JLabel("One guided workflow \u2022 capture, evidence, candidate iteration and robustness");
        jLabel2.setFont(BODY);
        jPanel2.add(jLabel);
        jPanel2.add(jLabel2);
        jPanel.add((Component)jPanel2, "North");
        jPanel.add((Component)this.workflowStrip, "South");
        jPanel.putClientProperty("title", jLabel);
        jPanel.putClientProperty("sub", jLabel2);
        this.tuneView.add((Component)jPanel, "North");
        this.heroHolder.setOpaque(false);
        this.sidePanel.setPreferredSize(new Dimension(330, 470));
        this.workflowCenter.setOpaque(false);
        this.workflowCenter.add((Component)this.heroHolder, "Center");
        this.workflowCenter.add((Component)this.sidePanel, "East");
        this.tuneView.add((Component)this.workflowCenter, "Center");
    }

    private void buildSecondaryView() {
        JPanel jPanel = new JPanel(new GridLayout(0, 1, 0, 1));
        jPanel.setOpaque(false);
        this.secondaryTitle.setFont(TITLE);
        this.secondarySub.setFont(BODY);
        jPanel.add(this.secondaryTitle);
        jPanel.add(this.secondarySub);
        this.secondaryHeader.setOpaque(false);
        this.secondaryHeader.add((Component)jPanel, "West");
        this.secondaryHeader.add((Component)this.secondaryChoices, "South");
        this.secondaryView.add((Component)this.secondaryHeader, "North");
        this.legacyHolder.setOpaque(false);
        this.legacyHolder.add((Component)this.legacy, "Center");
        this.secondaryBody.add((Component)this.legacyHolder, "LEGACY");
        this.secondaryBody.add((Component)this.captureSettingsPanel, "SETTINGS");
        this.secondaryView.add((Component)this.secondaryBody, "Center");
    }

    private void buildPersistentSouth() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 5));
        jPanel.setOpaque(false);
        jPanel.add((Component)this.liveStrip, "North");
        this.footer.setBorder(BorderFactory.createEmptyBorder(5, 7, 5, 7));
        JLabel jLabel = new JLabel("\u25cf");
        jLabel.setFont(new Font("Dialog", 1, 11));
        this.footer.putClientProperty("dot", jLabel);
        this.nextLabel.setFont(BODY);
        this.footer.add((Component)jLabel, "West");
        this.footer.add((Component)this.nextLabel, "Center");
        this.primaryAction.setFont(new Font("Dialog", 1, 11));
        this.secondaryAction.setFont(new Font("Dialog", 1, 10));
        this.tertiaryAction.setFont(new Font("Dialog", 1, 10));
        this.footerButtons.setOpaque(false);
        this.footerButtons.add(this.tertiaryAction);
        this.footerButtons.add(this.secondaryAction);
        this.footerButtons.add(this.primaryAction);
        this.footer.add((Component)this.footerButtons, "East");
        jPanel.add((Component)this.footer, "South");
        this.add((Component)jPanel, "South");
        this.primaryAction.addActionListener(actionEvent -> this.performPrimary());
        this.secondaryAction.addActionListener(actionEvent -> this.performSecondary());
        this.tertiaryAction.addActionListener(actionEvent -> this.performTertiary());
    }

    private void refresh() {
        String string;
        this.updateLiveStrip();
        if (this.task != Task.TUNE_IDLE) {
            this.footer.setVisible(false);
            this.liveStrip.setVisible(true);
            this.captureSettingsPanel.refreshFromAdapter(false);
            return;
        }
        this.footer.setVisible(true);
        Phase phase = this.determinePhase();
        if (phase != this.phase) {
            this.phase = phase;
            this.lastFingerprint = "";
        }
        LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
        if (!this.focusManual && liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable()) {
            string = IdleGuidedPanel.safe(liveExperimentalRecommendation.getChangedGain()).toUpperCase();
            if (string.startsWith("P")) {
                this.focus = Focus.RECOVERY;
            } else if (string.startsWith("I")) {
                this.focus = Focus.STEADY;
            } else if (string.startsWith("D")) {
                this.focus = Focus.DAMPING;
            }
        }
        if (!(string = this.fingerprint()).equals(this.lastFingerprint)) {
            this.lastFingerprint = string;
            this.rebuildTunePhase();
        }
        this.updateActionButtons();
        this.workflowStrip.setActive(this.phase.ordinal());
        this.applyTheme();
    }

    private Phase determinePhase() {
        LiveBaselineSnapshot liveBaselineSnapshot = this.adapter.baseline();
        LiveSessionSummary liveSessionSummary = this.adapter.sessionSummary();
        if (liveBaselineSnapshot == null) {
            if (this.adapter.isRunning() || liveSessionSummary.getAcceptedCount() > 0 || liveSessionSummary.getGoodCount() > 0) {
                return Phase.BASELINE;
            }
            return Phase.READINESS;
        }
        if (this.robustnessRequested) {
            return Phase.ROBUSTNESS;
        }
        TuneSnapshot tuneSnapshot = this.adapter.sessionGains();
        boolean bl = tuneSnapshot != null && tuneSnapshot.isComplete() && liveBaselineSnapshot.getGains() != null && liveBaselineSnapshot.getGains().isComplete() && !IdleGuidedPanel.sameTune(tuneSnapshot, liveBaselineSnapshot.getGains());
        LiveComparisonResult liveComparisonResult = this.adapter.liveComparison();
        if (bl && liveSessionSummary.getAcceptedCount() >= 2 && this.comparisonReady(liveComparisonResult)) {
            return Phase.COMPARE;
        }
        if (this.candidateReviewRequested || bl || this.adapter.candidateWasCopied(this.adapter.liveRecommendation())) {
            return Phase.CANDIDATE;
        }
        return Phase.EVIDENCE;
    }

    private String fingerprint() {
        LiveBaselineSnapshot liveBaselineSnapshot;
        LiveComparisonResult liveComparisonResult;
        LiveExperimentalRecommendation liveExperimentalRecommendation;
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append((Object)this.task).append('|').append((Object)this.phase).append('|').append((Object)this.focus).append('|').append(this.focusManual).append('|').append(this.adapter.state()).append('|').append(this.adapter.acceptedCount()).append('|').append(this.adapter.goodCount()).append('|').append(this.adapter.rejectedCount()).append('|').append(this.adapter.instruction()).append('|').append(this.adapter.lastResult()).append('|').append(this.adapter.connectionText()).append('|').append(this.adapter.mappingText());
        LiveReadiness liveReadiness = this.adapter.readiness();
        if (liveReadiness != null) {
            stringBuilder.append('|').append(liveReadiness.isReady()).append('|').append(liveReadiness.getRows());
        }
        if ((liveExperimentalRecommendation = this.adapter.liveRecommendation()) != null) {
            stringBuilder.append('|').append(liveExperimentalRecommendation.getStatus()).append('|').append(liveExperimentalRecommendation.getChangedGain()).append('|').append(liveExperimentalRecommendation.getConfidencePercent()).append('|').append(liveExperimentalRecommendation.isProposalAvailable()).append('|').append(IdleGuidedPanel.signature(liveExperimentalRecommendation.getCurrent())).append('|').append(IdleGuidedPanel.signature(liveExperimentalRecommendation.getProposed())).append('|').append(this.adapter.candidateWasCopied(liveExperimentalRecommendation));
        }
        if ((liveComparisonResult = this.adapter.liveComparison()) != null) {
            stringBuilder.append('|').append(liveComparisonResult.getStatus()).append('|').append(liveComparisonResult.getSummary()).append('|').append(liveComparisonResult.getScorePercent());
        }
        if ((liveBaselineSnapshot = this.adapter.baseline()) != null) {
            stringBuilder.append('|').append(liveBaselineSnapshot.getLabel()).append('|').append(IdleGuidedPanel.signature(liveBaselineSnapshot.getGains()));
        }
        stringBuilder.append('|').append(IdleGuidedPanel.signature(this.adapter.sessionGains())).append('|').append(this.actionMessage).append('|').append(this.robustnessRequested);
        return stringBuilder.toString();
    }

    private void rebuildTunePhase() {
        this.heroHolder.removeAll();
        switch (this.phase.ordinal()) {
            case 0: {
                this.heroHolder.add((Component)this.buildReadinessHero(), "Center");
                break;
            }
            case 1: {
                this.heroHolder.add((Component)this.buildBaselineHero(), "Center");
                break;
            }
            case 2: {
                this.heroHolder.add((Component)this.buildEvidenceHero(), "Center");
                break;
            }
            case 3: {
                this.heroHolder.add((Component)this.buildCandidateHero(), "Center");
                break;
            }
            case 4: {
                this.heroHolder.add((Component)this.buildCompareHero(), "Center");
                break;
            }
            case 5: {
                this.heroHolder.add((Component)this.buildRobustnessHero(), "Center");
            }
        }
        this.sidePanel.refresh(this.phase);
        this.heroHolder.revalidate();
        this.heroHolder.repaint();
    }

    private JComponent buildReadinessHero() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = this.vertical();
        jPanel2.add(this.checkRow(this.statusLooksConnected(), "TunerStudio / ECU connection", this.adapter.connectionText(), "Setup & Diagnostics", () -> this.requestSetup(SecondaryMode.MAPPING)));
        jPanel2.add(this.checkRow(this.statusLooksMapped(), "Idle controller mapping", this.adapter.mappingText(), "Controller Mapping", () -> this.requestSetup(SecondaryMode.MAPPING)));
        TuneSnapshot tuneSnapshot = this.adapter.sessionGains();
        boolean bl = tuneSnapshot != null && tuneSnapshot.isComplete();
        jPanel2.add(this.checkRow(bl, "Current P / I / D", bl ? IdleGuidedPanel.signature(tuneSnapshot) + " \u2022 captured at observer start" : "\u2014 / \u2014 / \u2014 \u2022 controller readback is captured when the live observer starts", null, null));
        jPanel2.add(this.checkRow(false, "Operating conditions", "Live coolant, TPS, closed-loop idle, fan/target stability and stationary checks begin after the observer starts.", "Capture Settings", () -> this.requestSetup(SecondaryMode.CAPTURE_SETTINGS)));
        jPanel.add((Component)jPanel2, "Center");
        jPanel.add((Component)this.note("The current engine requires the live observer to subscribe before all readiness gates can be evaluated. Starting the baseline capture does not change ECU gains."), "South");
        return this.hero("CHECK READINESS", "Prepare the ECU, required signals and operating state before recording the reference session.", jPanel);
    }

    private JComponent buildBaselineHero() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = this.vertical();
        jPanel2.add(this.stepRow("1", "Wait for stable idle", "Keep the engine untouched while the existing readiness gates evaluate channels, CLT, TPS, closed-loop idle, fan/target stability and stationary state."));
        jPanel2.add(this.stepRow("2", "Raise RPM when the tuner says Ready", "Use the configured preferred rev range. One smooth rev is better than repeated blips."));
        jPanel2.add(this.stepRow("3", "Release normally", "Brief DFCO after release is expected. Do not touch the throttle while idle control re-engages and the recovery is measured."));
        jPanel2.add(this.stepRow("4", "Repeat until the evidence is usable", "Accepted and rejected attempts remain qualified by the existing engine. Rejected attempts keep their reason."));
        jPanel.add((Component)jPanel2, "Center");
        jPanel.add((Component)this.captureStateBand(), "South");
        return this.hero(this.baselineHeadline(), this.adapter.instruction(), jPanel);
    }

    private JComponent buildEvidenceHero() {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel(new FlowLayout(0, 5, 0));
        jPanel2.setOpaque(false);
        JLabel jLabel = new JLabel("Expert focus:");
        jLabel.setFont(H3);
        jPanel2.add(jLabel);
        ButtonGroup buttonGroup = new ButtonGroup();
        JToggleButton jToggleButton = this.focusButton("Recovery response", Focus.RECOVERY);
        JToggleButton jToggleButton2 = this.focusButton("Steady idle", Focus.STEADY);
        JToggleButton jToggleButton3 = this.focusButton("Damping", Focus.DAMPING);
        buttonGroup.add(jToggleButton);
        buttonGroup.add(jToggleButton2);
        buttonGroup.add(jToggleButton3);
        jPanel2.add(jToggleButton);
        jPanel2.add(jToggleButton2);
        jPanel2.add(jToggleButton3);
        jPanel.add((Component)jPanel2, "North");
        LiveSessionSummary liveSessionSummary = this.baselineOrCurrentSummary();
        JPanel jPanel3 = new JPanel(new GridLayout(1, 3, 7, 0));
        jPanel3.setOpaque(false);
        jPanel3.add(this.findingCard("Recovery response", Focus.RECOVERY, IdleGuidedPanel.pair("Settling", IdleGuidedPanel.metric(liveSessionSummary.getMedianSettlingSeconds(), " s")), IdleGuidedPanel.pair("Overshoot", IdleGuidedPanel.metric(liveSessionSummary.getMedianOvershootRpm(), " rpm")), IdleGuidedPanel.pair("Opposite excursion", IdleGuidedPanel.metric(liveSessionSummary.getMedianUndershootRpm(), " rpm")), IdleGuidedPanel.pair("Accepted / Good", liveSessionSummary.getAcceptedCount() + " / " + liveSessionSummary.getGoodCount())));
        jPanel3.add(this.findingCard("Steady idle", Focus.STEADY, IdleGuidedPanel.pair("Signed RPM error", IdleGuidedPanel.metric(liveSessionSummary.getMedianSignedErrorRpm(), " rpm")), IdleGuidedPanel.pair("Mean abs error", IdleGuidedPanel.metric(liveSessionSummary.getMedianMeanAbsoluteErrorRpm(), " rpm")), IdleGuidedPanel.pair("RPM std dev", IdleGuidedPanel.metric(liveSessionSummary.getMedianRpmStandardDeviation(), " rpm")), IdleGuidedPanel.pair("I-term span", IdleGuidedPanel.metric(liveSessionSummary.getMedianITermSpan(), " %"))));
        jPanel3.add(this.findingCard("Damping", Focus.DAMPING, IdleGuidedPanel.pair("Correction reversals", IdleGuidedPanel.metric(liveSessionSummary.getMedianCorrectionReversalsPerSecond(), " /s")), IdleGuidedPanel.pair("D-term span", IdleGuidedPanel.metric(liveSessionSummary.getMedianDTermSpan(), " %")), IdleGuidedPanel.pair("P-term span", IdleGuidedPanel.metric(liveSessionSummary.getMedianPTermSpan(), " %")), IdleGuidedPanel.pair("Oscillation frequency", "Log-analysis only")));
        jPanel.add((Component)jPanel3, "Center");
        jPanel.add((Component)this.readinessBand(), "South");
        return this.hero("REVIEW THE EVIDENCE", "P, I and D findings share the same qualified session, but retain different evidence requirements. Select a focus without entering a fixed P \u2192 I \u2192 D sequence.", jPanel);
    }

    private JComponent buildCandidateHero() {
        LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        if (liveExperimentalRecommendation == null || !liveExperimentalRecommendation.isProposalAvailable()) {
            jPanel.add((Component)this.note("No supported live candidate is available. Return to Evidence and collect the specific evidence requested by the engine."), "Center");
            return this.hero("CANDIDATE NOT READY", "The existing one-gain recommendation gate remains authoritative.", jPanel);
        }
        JPanel jPanel2 = new JPanel(new GridLayout(1, 3, 7, 0));
        jPanel2.setOpaque(false);
        jPanel2.add(this.gainCard("Baseline gains", this.adapter.baseline() == null ? null : this.adapter.baseline().getGains(), "Reference state"));
        jPanel2.add(this.gainCard("Confirmed current gains", this.adapter.sessionGains(), this.currentGainConfirmation(liveExperimentalRecommendation)));
        jPanel2.add(this.gainCard("Proposed candidate", liveExperimentalRecommendation.getProposed(), IdleGuidedPanel.safe(liveExperimentalRecommendation.getChangedGain()) + " only \u2022 " + IdleGuidedPanel.signed(liveExperimentalRecommendation.getChangePercent()) + "%"));
        jPanel.add((Component)jPanel2, "North");
        JPanel jPanel3 = this.vertical();
        jPanel3.add(this.longDetail("Supporting evidence", IdleGuidedPanel.safe(liveExperimentalRecommendation.getReason())));
        jPanel3.add(this.longDetail("Expected effect", IdleGuidedPanel.safe(liveExperimentalRecommendation.getExpectedEffect())));
        jPanel3.add(this.detailLine("Confidence", IdleGuidedPanel.percent(liveExperimentalRecommendation.getConfidencePercent())));
        jPanel3.add(this.detailLine("Qualification", IdleGuidedPanel.safe(liveExperimentalRecommendation.getStatus())));
        jPanel3.add(this.longDetail("Copy state", this.adapter.candidateWasCopied(liveExperimentalRecommendation) ? "Copied to clipboard only \u2014 this does not mean the ECU was changed." : "Not copied. Copying is optional; manual entry in TunerStudio is still required."));
        jPanel.add((Component)jPanel3, "Center");
        jPanel.add((Component)this.note("After entering the candidate in TunerStudio RAM, use Capture candidate test. The observer captures current controller gains at start; that captured snapshot is the comparison authority."), "South");
        return this.hero("REVIEW AND TEST ONE CANDIDATE", "The engine changes one gain per iteration. Proposed, copied and confirmed-current states are deliberately kept separate.", jPanel);
    }

    private JComponent buildCompareHero() {
        LiveBaselineSnapshot liveBaselineSnapshot = this.adapter.baseline();
        LiveSessionSummary liveSessionSummary = liveBaselineSnapshot == null ? LiveSessionSummary.empty() : liveBaselineSnapshot.getSummary();
        LiveSessionSummary liveSessionSummary2 = this.adapter.sessionSummary();
        LiveComparisonResult liveComparisonResult = this.adapter.liveComparison();
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setOpaque(false);
        JPanel jPanel2 = new JPanel(new GridLayout(0, 3, 1, 1));
        jPanel2.setOpaque(false);
        this.addCompareRow(jPanel2, "Metric", "Baseline", "Candidate", true);
        this.addCompareRow(jPanel2, "Settling", IdleGuidedPanel.metric(liveSessionSummary.getMedianSettlingSeconds(), " s"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianSettlingSeconds(), " s"), false);
        this.addCompareRow(jPanel2, "Overshoot", IdleGuidedPanel.metric(liveSessionSummary.getMedianOvershootRpm(), " rpm"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianOvershootRpm(), " rpm"), false);
        this.addCompareRow(jPanel2, "Signed error", IdleGuidedPanel.metric(liveSessionSummary.getMedianSignedErrorRpm(), " rpm"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianSignedErrorRpm(), " rpm"), false);
        this.addCompareRow(jPanel2, "RPM std dev", IdleGuidedPanel.metric(liveSessionSummary.getMedianRpmStandardDeviation(), " rpm"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianRpmStandardDeviation(), " rpm"), false);
        this.addCompareRow(jPanel2, "Correction reversals", IdleGuidedPanel.metric(liveSessionSummary.getMedianCorrectionReversalsPerSecond(), " /s"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianCorrectionReversalsPerSecond(), " /s"), false);
        this.addCompareRow(jPanel2, "D-term span", IdleGuidedPanel.metric(liveSessionSummary.getMedianDTermSpan(), " %"), IdleGuidedPanel.metric(liveSessionSummary2.getMedianDTermSpan(), " %"), false);
        jPanel.add((Component)jPanel2, "Center");
        JPanel jPanel3 = this.vertical();
        jPanel3.add(this.detailLine("Outcome", liveComparisonResult == null ? "Inconclusive / not calculated" : IdleGuidedPanel.safe(liveComparisonResult.getStatus())));
        jPanel3.add(this.detailLine("Comparison summary", liveComparisonResult == null ? "No comparable candidate result yet." : IdleGuidedPanel.safe(liveComparisonResult.getSummary())));
        jPanel3.add(this.detailLine("Comparability", liveComparisonResult == null ? "Not established" : this.comparisonMeaning(liveComparisonResult)));
        jPanel.add((Component)jPanel3, "South");
        return this.hero("COMPARE AND DECIDE", "Every candidate iteration returns to baseline-versus-candidate comparison before another gain change.", jPanel);
    }

    private JComponent buildRobustnessHero() {
        LiveBaselineSnapshot liveBaselineSnapshot = this.adapter.baseline();
        LiveSessionSummary liveSessionSummary = liveBaselineSnapshot == null ? this.adapter.sessionSummary() : liveBaselineSnapshot.getSummary();
        LoadEvidence loadEvidence = this.loadEvidence();
        JPanel jPanel = new JPanel(new GridLayout(1, 3, 7, 0));
        jPanel.setOpaque(false);
        jPanel.add(this.robustCard("Return-to-idle", "Evidence present", IdleGuidedPanel.pair("Accepted attempts", Integer.toString(liveSessionSummary.getAcceptedCount())), IdleGuidedPanel.pair("Settling", IdleGuidedPanel.metric(liveSessionSummary.getMedianSettlingSeconds(), " s")), IdleGuidedPanel.pair("Overshoot", IdleGuidedPanel.metric(liveSessionSummary.getMedianOvershootRpm(), " rpm")), IdleGuidedPanel.pair("Meaning", "Review against your own target; no unsupported pass/fail threshold is invented.")));
        jPanel.add(this.robustCard("Steady idle", IdleGuidedPanel.finite(liveSessionSummary.getMedianSignedErrorRpm()) ? "Evidence present" : "Not evaluated", IdleGuidedPanel.pair("Signed error", IdleGuidedPanel.metric(liveSessionSummary.getMedianSignedErrorRpm(), " rpm")), IdleGuidedPanel.pair("RPM std dev", IdleGuidedPanel.metric(liveSessionSummary.getMedianRpmStandardDeviation(), " rpm")), IdleGuidedPanel.pair("I-term span", IdleGuidedPanel.metric(liveSessionSummary.getMedianITermSpan(), " %")), IdleGuidedPanel.pair("Meaning", "Retains the live steady-state evidence; use logs for deeper steady classification.")));
        jPanel.add(this.robustCard("Load recovery", loadEvidence.count > 0 ? "Log evidence present" : "More evidence needed", IdleGuidedPanel.pair("Classified events", Integer.toString(loadEvidence.count)), IdleGuidedPanel.pair("Last event", loadEvidence.lastType), IdleGuidedPanel.pair("Settling", loadEvidence.lastSettling), IdleGuidedPanel.pair("Authority", "IdleLogAnalyzer fan/load classification; no unsupported live load classifier is claimed.")));
        return this.hero("VALIDATE ROBUSTNESS", "Return-to-idle, steady idle and load recovery are reviewed together while keeping their evidence sources distinct.", jPanel);
    }

    private JComponent hero(String string, String string2, JComponent jComponent) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 8));
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(12, 14, 12, 14)));
        JPanel jPanel2 = new JPanel(new BorderLayout(0, 3));
        jPanel2.setOpaque(false);
        JLabel jLabel = new JLabel(this.phaseLabel());
        jLabel.setFont(new Font("Dialog", 1, 9));
        JLabel jLabel2 = new JLabel(string);
        jLabel2.setFont(H1);
        JTextArea jTextArea = IdleGuidedPanel.textArea(2);
        jTextArea.setText(string2);
        jTextArea.setFont(new Font("Dialog", 0, 12));
        jPanel2.add((Component)jLabel, "North");
        JPanel jPanel3 = new JPanel(new BorderLayout(0, 2));
        jPanel3.setOpaque(false);
        jPanel3.add((Component)jLabel2, "North");
        jPanel3.add((Component)jTextArea, "Center");
        jPanel2.add((Component)jPanel3, "Center");
        jPanel.add((Component)jPanel2, "North");
        JScrollPane jScrollPane = new JScrollPane(jComponent);
        jScrollPane.setBorder(null);
        jScrollPane.setHorizontalScrollBarPolicy(31);
        jScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        jPanel.add((Component)jScrollPane, "Center");
        jPanel.putClientProperty("eyebrow", jLabel);
        jPanel.putClientProperty("headline", jLabel2);
        jPanel.putClientProperty("subtitle", jTextArea);
        jPanel.putClientProperty("content", jComponent);
        this.applyHeroTheme(jPanel);
        return jPanel;
    }

    private JPanel captureStateBand() {
        boolean bl;
        LiveCaptureState liveCaptureState = this.adapter.state();
        LiveAttempt liveAttempt = this.adapter.lastAttempt();
        JPanel jPanel = new JPanel(new BorderLayout(10, 0));
        jPanel.setBorder(BorderFactory.createEmptyBorder(9, 10, 9, 10));
        JLabel jLabel = new JLabel("\u25cf");
        jLabel.setFont(new Font("Dialog", 1, 18));
        JPanel jPanel2 = new JPanel(new GridLayout(0, 1, 0, 1));
        jPanel2.setOpaque(false);
        JLabel jLabel2 = new JLabel(this.baselineHeadline());
        jLabel2.setFont(new Font("Dialog", 1, 12));
        JLabel jLabel3 = new JLabel(this.captureGuidance(liveCaptureState, liveAttempt));
        jLabel3.setFont(SMALL);
        jPanel2.add(jLabel2);
        jPanel2.add(jLabel3);
        JLabel jLabel4 = new JLabel(this.adapter.acceptedCount() + " accepted / " + this.adapter.rejectedCount() + " rejected");
        jLabel4.setFont(H2);
        jPanel.add((Component)jLabel, "West");
        jPanel.add((Component)jPanel2, "Center");
        jPanel.add((Component)jLabel4, "East");
        boolean bl2 = bl = liveAttempt != null && !liveAttempt.isAccepted() && liveCaptureState == LiveCaptureState.WAITING_FOR_STABLE_IDLE;
        Color color = bl ? UiTheme.softRed() : (liveCaptureState == LiveCaptureState.READY_TO_REV ? UiTheme.softGreen() : UiTheme.softAmber());
        Color color3 = bl ? UiTheme.red() : (liveCaptureState == LiveCaptureState.READY_TO_REV ? UiTheme.green() : UiTheme.focusAmber());
        jPanel.setBackground(color);
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(color3), new EmptyBorder(9, 10, 9, 10)));
        jLabel.setForeground(color3);
        jLabel2.setForeground(UiTheme.text());
        jLabel3.setForeground(UiTheme.muted());
        jLabel4.setForeground(UiTheme.blue());
        return jPanel;
    }

    private JPanel readinessBand() {
        String string;
        String string2;
        boolean bl;
        LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
        LiveSessionSummary liveSessionSummary = this.baselineOrCurrentSummary();
        boolean bl2 = bl = liveExperimentalRecommendation != null && (liveExperimentalRecommendation.isProposalAvailable() || "NO_CHANGE".equalsIgnoreCase(IdleGuidedPanel.safe(liveExperimentalRecommendation.getStatus())));
        if (bl) {
            string2 = "READY TO RECOMMEND";
            string = liveExperimentalRecommendation.isProposalAvailable() ? "Engine-supported focus: " + IdleGuidedPanel.safe(liveExperimentalRecommendation.getChangedGain()) + ". Review the one-gain candidate before manual testing." : IdleGuidedPanel.safe(liveExperimentalRecommendation.getSummary());
        } else if (liveSessionSummary.getAcceptedCount() < 2) {
            string2 = "MORE EVIDENCE NEEDED";
            string = "Collect at least two accepted attempts before a live candidate can be considered.";
        } else if (liveSessionSummary.getGoodCount() < 1) {
            string2 = "MORE EVIDENCE NEEDED";
            string = "At least one accepted attempt must meet the existing Good-quality gate.";
        } else if (liveExperimentalRecommendation != null) {
            string2 = "MORE EVIDENCE NEEDED";
            string = IdleGuidedPanel.safe(liveExperimentalRecommendation.getSummary());
        } else {
            string2 = "MORE EVIDENCE NEEDED";
            string = "The live recommendation engine has not produced a qualified result yet.";
        }
        JPanel jPanel = new JPanel(new BorderLayout(8, 0));
        jPanel.setBackground(bl ? UiTheme.softGreen() : UiTheme.softAmber());
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bl ? UiTheme.green() : UiTheme.focusAmber()), new EmptyBorder(8, 10, 8, 10)));
        JLabel jLabel = new JLabel(string2);
        jLabel.setFont(H2);
        jLabel.setForeground(UiTheme.text());
        JLabel jLabel2 = new JLabel(string);
        jLabel2.setFont(SMALL);
        jLabel2.setForeground(UiTheme.muted());
        JPanel jPanel2 = new JPanel(new GridLayout(0, 1));
        jPanel2.setOpaque(false);
        jPanel2.add(jLabel);
        jPanel2.add(jLabel2);
        jPanel.add((Component)jPanel2, "Center");
        return jPanel;
    }

    private JToggleButton focusButton(String string, Focus focus) {
        JToggleButton jToggleButton = new JToggleButton(string, this.focus == focus);
        jToggleButton.setFont(new Font("Dialog", 1, 9));
        jToggleButton.setFocusPainted(false);
        jToggleButton.addActionListener(actionEvent -> {
            this.focus = focus;
            this.focusManual = true;
            this.lastFingerprint = "";
            this.refresh();
        });
        IdleGuidedPanel.styleChoice(jToggleButton, jToggleButton.isSelected());
        return jToggleButton;
    }

    private JPanel findingCard(String string, Focus focus, Pair ... pairArray) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 6));
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(this.focus == focus ? UiTheme.blue() : UiTheme.border()), new EmptyBorder(9, 9, 9, 9)));
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(H2);
        jLabel.setForeground(UiTheme.text());
        jPanel.add((Component)jLabel, "North");
        JPanel jPanel2 = this.vertical();
        for (Pair pair : pairArray) {
            jPanel2.add(this.detailLine(pair.name, pair.value));
        }
        jPanel.add((Component)jPanel2, "Center");
        jPanel.setBackground(this.focus == focus ? UiTheme.softBlue() : UiTheme.card());
        return jPanel;
    }

    private JPanel gainCard(String string, TuneSnapshot tuneSnapshot, String string2) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 7));
        jPanel.setBackground(UiTheme.card());
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(9, 9, 9, 9)));
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(H2);
        jLabel.setForeground(UiTheme.text());
        jPanel.add((Component)jLabel, "North");
        JPanel jPanel2 = new JPanel(new GridLayout(1, 3, 5, 0));
        jPanel2.setOpaque(false);
        jPanel2.add(this.gainCell("P", IdleGuidedPanel.tuneValue(tuneSnapshot, 'P')));
        jPanel2.add(this.gainCell("I", IdleGuidedPanel.tuneValue(tuneSnapshot, 'I')));
        jPanel2.add(this.gainCell("D", IdleGuidedPanel.tuneValue(tuneSnapshot, 'D')));
        jPanel.add((Component)jPanel2, "Center");
        JTextArea jTextArea = IdleGuidedPanel.textArea(2);
        jTextArea.setText(string2 == null ? "\u2014" : string2);
        jTextArea.setFont(SMALL);
        jTextArea.setBackground(UiTheme.card());
        jTextArea.setForeground(UiTheme.muted());
        jPanel.add((Component)jTextArea, "South");
        return jPanel;
    }

    private JPanel gainCell(String string, String string2) {
        JPanel jPanel = new JPanel(new GridLayout(0, 1, 0, 1));
        jPanel.setOpaque(false);
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(SMALL);
        jLabel.setForeground(UiTheme.muted());
        JLabel jLabel2 = new JLabel(string2);
        jLabel2.setFont(new Font("Dialog", 1, 14));
        jLabel2.setForeground(UiTheme.blue());
        jPanel.add(jLabel);
        jPanel.add(jLabel2);
        return jPanel;
    }

    private JPanel robustCard(String string, String string2, Pair ... pairArray) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 5));
        jPanel.setBackground(UiTheme.card());
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(9, 9, 9, 9)));
        JPanel jPanel2 = new JPanel(new BorderLayout());
        jPanel2.setOpaque(false);
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(H2);
        jLabel.setForeground(UiTheme.text());
        JLabel jLabel2 = new JLabel(string2);
        jLabel2.setFont(SMALL);
        jLabel2.setForeground(string2.contains("present") ? UiTheme.green() : UiTheme.focusAmber());
        jPanel2.add((Component)jLabel, "West");
        jPanel2.add((Component)jLabel2, "East");
        jPanel.add((Component)jPanel2, "North");
        JPanel jPanel3 = this.vertical();
        for (Pair pair : pairArray) {
            jPanel3.add(this.detailLine(pair.name, pair.value));
        }
        jPanel.add((Component)jPanel3, "Center");
        return jPanel;
    }

    private JPanel checkRow(boolean bl, String string, String string2, String string3, Runnable runnable) {
        JPanel jPanel = new JPanel(new BorderLayout(8, 0));
        jPanel.setOpaque(false);
        jPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        jPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        JLabel jLabel = new JLabel(bl ? "\u25cf" : "\u25cb");
        jLabel.setFont(new Font("Dialog", 1, 13));
        jLabel.setForeground(bl ? UiTheme.green() : UiTheme.focusAmber());
        JPanel jPanel2 = new JPanel(new GridLayout(0, 1));
        jPanel2.setOpaque(false);
        JLabel jLabel2 = new JLabel(string);
        jLabel2.setFont(H2);
        jLabel2.setForeground(UiTheme.text());
        JLabel jLabel3 = new JLabel(string2);
        jLabel3.setFont(SMALL);
        jLabel3.setForeground(UiTheme.muted());
        jPanel2.add(jLabel2);
        jPanel2.add(jLabel3);
        jPanel.add((Component)jLabel, "West");
        jPanel.add((Component)jPanel2, "Center");
        if (string3 != null && runnable != null) {
            JButton jButton = new JButton(string3);
            jButton.setFont(SMALL);
            jButton.setPreferredSize(new Dimension(116, 28));
            jButton.addActionListener(actionEvent -> runnable.run());
            IdleGuidedPanel.styleSecondary(jButton);
            jPanel.add((Component)jButton, "East");
        }
        return jPanel;
    }

    private JPanel stepRow(String string, String string2, String string3) {
        JPanel jPanel = new JPanel(new BorderLayout(9, 0));
        jPanel.setOpaque(false);
        jPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 0));
        JLabel jLabel = new JLabel(string, 0);
        jLabel.setFont(H2);
        jLabel.setPreferredSize(new Dimension(28, 28));
        jLabel.setOpaque(true);
        jLabel.setBackground(UiTheme.softBlue());
        jLabel.setForeground(UiTheme.blue());
        jLabel.setBorder(BorderFactory.createLineBorder(UiTheme.border()));
        JPanel jPanel2 = new JPanel(new GridLayout(0, 1));
        jPanel2.setOpaque(false);
        JLabel jLabel2 = new JLabel(string2);
        jLabel2.setFont(H2);
        jLabel2.setForeground(UiTheme.text());
        JLabel jLabel3 = new JLabel(string3);
        jLabel3.setFont(SMALL);
        jLabel3.setForeground(UiTheme.muted());
        jPanel2.add(jLabel2);
        jPanel2.add(jLabel3);
        jPanel.add((Component)jLabel, "West");
        jPanel.add((Component)jPanel2, "Center");
        return jPanel;
    }

    private JPanel note(String string) {
        JPanel jPanel = new JPanel(new BorderLayout());
        jPanel.setBackground(UiTheme.softBlue());
        jPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(7, 9, 7, 9)));
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(SMALL);
        jLabel.setForeground(UiTheme.muted());
        jPanel.add(jLabel);
        return jPanel;
    }

    private JPanel longDetail(String string, String string2) {
        JPanel jPanel = new JPanel(new BorderLayout(0, 2));
        jPanel.setOpaque(false);
        jPanel.setBorder(BorderFactory.createEmptyBorder(3, 0, 5, 0));
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(SMALL);
        jLabel.setForeground(UiTheme.muted());
        JTextArea jTextArea = IdleGuidedPanel.textArea(2);
        jTextArea.setText(string2 == null ? "\u2014" : string2);
        jTextArea.setFont(new Font("Dialog", 1, 9));
        jTextArea.setBackground(UiTheme.card());
        jTextArea.setForeground(UiTheme.text());
        jPanel.add((Component)jLabel, "North");
        jPanel.add((Component)jTextArea, "Center");
        return jPanel;
    }

    private JPanel detailLine(String string, String string2) {
        JPanel jPanel = new JPanel(new BorderLayout(8, 0));
        jPanel.setOpaque(false);
        jPanel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(SMALL);
        jLabel.setForeground(UiTheme.muted());
        JLabel jLabel2 = new JLabel(string2 == null ? "\u2014" : string2);
        jLabel2.setFont(new Font("Dialog", 1, 9));
        jLabel2.setForeground(UiTheme.text());
        jPanel.add((Component)jLabel, "West");
        jPanel.add((Component)jLabel2, "East");
        return jPanel;
    }

    private JPanel vertical() {
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new BoxLayout(jPanel, 1));
        jPanel.setOpaque(false);
        return jPanel;
    }

    private void addCompareRow(JPanel jPanel, String string, String string2, String string3, boolean bl) {
        JLabel jLabel = this.compareCell(string, bl, false);
        JLabel jLabel2 = this.compareCell(string2, bl, false);
        JLabel jLabel3 = this.compareCell(string3, bl, true);
        jPanel.add(jLabel);
        jPanel.add(jLabel2);
        jPanel.add(jLabel3);
    }

    private JLabel compareCell(String string, boolean bl, boolean bl2) {
        JLabel jLabel = new JLabel(string);
        jLabel.setFont(new Font("Dialog", bl ? 1 : 0, 10));
        jLabel.setOpaque(true);
        jLabel.setBackground(bl2 ? UiTheme.softBlue() : (bl ? UiTheme.tableHeader() : UiTheme.card()));
        jLabel.setForeground(bl2 ? UiTheme.blue() : UiTheme.text());
        jLabel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(6, 7, 6, 7)));
        return jLabel;
    }

    private void updateActionButtons() {
        this.tertiaryAction.setText("Abort Attempt");
        this.tertiaryAction.setVisible(false);
        this.secondaryAction.setVisible(false);
        this.primaryAction.setVisible(true);
        switch (this.phase.ordinal()) {
            case 0: {
                this.primaryAction.setText("Start Baseline Capture");
                this.primaryAction.setEnabled(this.adapter.canStart());
                this.nextLabel.setText(this.adapter.canStart() ? "Next: Start the observer; live readiness gates will then become active." : "Blocked: TunerStudio/controller subscription or configuration is not ready. Open Setup & Diagnostics.");
                break;
            }
            case 1: {
                this.tertiaryAction.setVisible(this.adapter.canAbort());
                this.tertiaryAction.setEnabled(this.adapter.canAbort());
                this.secondaryAction.setVisible(this.adapter.canStop());
                this.secondaryAction.setText("Stop");
                this.secondaryAction.setEnabled(this.adapter.canStop());
                if (this.adapter.canUseSessionAsBaseline()) {
                    this.primaryAction.setText("Use Session as Baseline");
                    this.primaryAction.setEnabled(true);
                    this.nextLabel.setText("Next: Store this qualified session as the in-memory reference. No ECU values are changed.");
                    break;
                }
                if (!this.adapter.isRunning()) {
                    this.primaryAction.setText("Start / Resume Baseline Capture");
                    this.primaryAction.setEnabled(this.adapter.canStart());
                    this.nextLabel.setText("Next: Start capture and repeat the guided maneuver until the baseline gate is met.");
                    break;
                }
                this.primaryAction.setText("Capture Running");
                this.primaryAction.setEnabled(false);
                this.nextLabel.setText("Now: " + this.adapter.instruction());
                break;
            }
            case 2: {
                LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
                if (liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable() && this.adapter.isRunning()) {
                    this.primaryAction.setText("Stop Baseline Capture");
                    this.primaryAction.setEnabled(this.adapter.canStop());
                    this.tertiaryAction.setVisible(this.adapter.canAbort());
                    this.tertiaryAction.setEnabled(this.adapter.canAbort());
                    this.nextLabel.setText("Next: Stop the qualified baseline observer before reviewing/copying a candidate. The evidence stays in this session.");
                    break;
                }
                if (liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable()) {
                    this.primaryAction.setText("Review Candidate");
                    this.primaryAction.setEnabled(true);
                    this.nextLabel.setText("Next: Review the one-gain candidate and supporting evidence before manual testing.");
                    break;
                }
                if (!this.adapter.isRunning()) {
                    this.primaryAction.setText("Collect More Evidence");
                    this.primaryAction.setEnabled(this.adapter.canStart());
                    this.nextLabel.setText("Next: Capture more comparable evidence; the engine will keep its existing quality gates.");
                    break;
                }
                this.primaryAction.setText("Capture Running");
                this.primaryAction.setEnabled(false);
                this.secondaryAction.setVisible(this.adapter.canStop());
                this.secondaryAction.setText("Stop");
                this.secondaryAction.setEnabled(this.adapter.canStop());
                this.tertiaryAction.setVisible(this.adapter.canAbort());
                this.tertiaryAction.setEnabled(this.adapter.canAbort());
                this.nextLabel.setText("Now: Perform another clean maneuver when the observer reports Ready.");
                break;
            }
            case 3: {
                LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
                boolean bl = this.adapter.candidateWasCopied(liveExperimentalRecommendation);
                boolean bl2 = this.adapter.isRunning();
                if (bl2) {
                    this.primaryAction.setText("Candidate Capture Running");
                    this.primaryAction.setEnabled(false);
                    this.secondaryAction.setVisible(this.adapter.canStop());
                    this.secondaryAction.setText("Stop");
                    this.secondaryAction.setEnabled(this.adapter.canStop());
                    this.tertiaryAction.setVisible(this.adapter.canAbort());
                    this.tertiaryAction.setEnabled(this.adapter.canAbort());
                    this.nextLabel.setText("Now: " + this.adapter.instruction());
                    break;
                }
                if (!bl) {
                    this.primaryAction.setText("Copy Candidate Gains");
                    this.primaryAction.setEnabled(liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable());
                    this.secondaryAction.setVisible(true);
                    this.secondaryAction.setText("Capture Candidate Test");
                    this.secondaryAction.setEnabled(this.adapter.canStart());
                    this.nextLabel.setText("Next: Copy or manually enter the candidate in TunerStudio RAM, then capture a candidate test. Copying does not apply it.");
                    break;
                }
                this.primaryAction.setText("Capture Candidate Test");
                this.primaryAction.setEnabled(this.adapter.canStart());
                this.secondaryAction.setVisible(true);
                this.secondaryAction.setText("Copy Candidate Again");
                this.secondaryAction.setEnabled(true);
                this.nextLabel.setText("Next: After manual TunerStudio entry, start candidate capture. The observer will capture current gains for comparison.");
                break;
            }
            case 4: {
                String string;
                LiveComparisonResult liveComparisonResult = this.adapter.liveComparison();
                String string2 = string = liveComparisonResult == null ? "" : IdleGuidedPanel.safe(liveComparisonResult.getStatus()).toUpperCase();
                if (this.adapter.isRunning()) {
                    this.primaryAction.setText("Stop Candidate Capture");
                    this.primaryAction.setEnabled(this.adapter.canStop());
                    this.nextLabel.setText("Next: Stop the candidate observer to freeze this iteration before deciding what to keep.");
                    this.tertiaryAction.setVisible(this.adapter.canAbort());
                    this.tertiaryAction.setEnabled(this.adapter.canAbort());
                } else if ("IMPROVED".equals(string) && this.adapter.canKeepAsBaseline()) {
                    this.primaryAction.setText("Keep as Baseline");
                    this.primaryAction.setEnabled(true);
                    this.nextLabel.setText("Next: Promote this tested session to the plugin reference, then validate robustness.");
                } else {
                    this.primaryAction.setText("Collect More Evidence");
                    this.primaryAction.setEnabled(this.adapter.canStart());
                    this.nextLabel.setText("Next: Collect more comparable evidence or copy rollback gains. The comparison outcome is retained in history.");
                }
                this.secondaryAction.setVisible(this.adapter.canCopyRollback());
                this.secondaryAction.setText("Copy Rollback Gains");
                this.secondaryAction.setEnabled(this.adapter.canCopyRollback());
                if (this.adapter.isRunning() || !"IMPROVED".equals(string) || !this.adapter.canKeepAsBaseline()) break;
                this.tertiaryAction.setVisible(true);
                this.tertiaryAction.setText("Validate Robustness");
                this.tertiaryAction.setEnabled(true);
                break;
            }
            case 5: {
                LoadEvidence loadEvidence = this.loadEvidence();
                if (loadEvidence.count == 0) {
                    this.primaryAction.setText("Open Sessions & Logs");
                    this.primaryAction.setEnabled(true);
                    this.nextLabel.setText("Next: Import/analyse a log containing a classified fan/load step. Live load-step classification is not claimed.");
                } else {
                    this.primaryAction.setText("Return to Evidence");
                    this.primaryAction.setEnabled(true);
                    this.nextLabel.setText("Robustness evidence is grouped here; interpret each result according to its own source and acceptance criteria.");
                }
                this.secondaryAction.setVisible(true);
                this.secondaryAction.setText("Setup & Diagnostics");
                this.secondaryAction.setEnabled(true);
            }
        }
        IdleGuidedPanel.stylePrimary(this.primaryAction);
        IdleGuidedPanel.styleSecondary(this.secondaryAction);
        IdleGuidedPanel.styleSecondary(this.tertiaryAction);
    }

    private void performPrimary() {
        this.actionMessage = "";
        switch (this.phase.ordinal()) {
            case 0: {
                this.adapter.startObserver();
                break;
            }
            case 1: {
                if (this.adapter.canUseSessionAsBaseline()) {
                    this.adapter.useSessionAsBaseline();
                    this.candidateReviewRequested = false;
                    break;
                }
                if (this.adapter.isRunning()) break;
                this.adapter.startObserver();
                break;
            }
            case 2: {
                LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
                if (liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable() && this.adapter.isRunning()) {
                    if (!this.adapter.canStop()) break;
                    this.adapter.stopObserver();
                    break;
                }
                if (liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable()) {
                    this.candidateReviewRequested = true;
                    break;
                }
                if (this.adapter.isRunning()) break;
                this.adapter.startObserver();
                break;
            }
            case 3: {
                LiveExperimentalRecommendation liveExperimentalRecommendation = this.adapter.liveRecommendation();
                if (this.adapter.candidateWasCopied(liveExperimentalRecommendation)) {
                    this.adapter.startObserver();
                    break;
                }
                try {
                    if (!this.adapter.copyCandidateGains()) break;
                    this.actionMessage = "Candidate gains copied to clipboard only \u2014 not applied.";
                }
                catch (Exception exception) {
                    this.actionMessage = "Clipboard copy failed: " + exception.getMessage();
                }
                break;
            }
            case 4: {
                String string;
                LiveComparisonResult liveComparisonResult = this.adapter.liveComparison();
                String string2 = string = liveComparisonResult == null ? "" : IdleGuidedPanel.safe(liveComparisonResult.getStatus()).toUpperCase();
                if (this.adapter.isRunning()) {
                    if (!this.adapter.canStop()) break;
                    this.adapter.stopObserver();
                    break;
                }
                if ("IMPROVED".equals(string) && this.adapter.canKeepAsBaseline()) {
                    this.adapter.keepAsBaseline();
                    this.robustnessRequested = true;
                    this.candidateReviewRequested = false;
                    break;
                }
                this.adapter.startObserver();
                break;
            }
            case 5: {
                if (this.loadEvidence().count == 0) {
                    this.requestDestination(Task.SESSIONS_LOGS);
                    break;
                }
                this.robustnessRequested = false;
                this.candidateReviewRequested = false;
            }
        }
        this.lastFingerprint = "";
        this.refresh();
    }

    private void performSecondary() {
        this.actionMessage = "";
        switch (this.phase.ordinal()) {
            case 1: 
            case 2: {
                if (!this.adapter.canStop()) break;
                this.adapter.stopObserver();
                break;
            }
            case 3: {
                if (this.adapter.isRunning()) {
                    if (!this.adapter.canStop()) break;
                    this.adapter.stopObserver();
                    break;
                }
                try {
                    this.adapter.copyCandidateGains();
                    this.actionMessage = "Candidate gains copied to clipboard only \u2014 not applied.";
                }
                catch (Exception exception) {
                    this.actionMessage = "Clipboard copy failed: " + exception.getMessage();
                }
                break;
            }
            case 4: {
                if (!this.adapter.canCopyRollback()) break;
                this.adapter.copyRollbackGains();
                this.actionMessage = "Rollback gains copied only \u2014 ECU settings were not restored.";
                break;
            }
            case 5: {
                this.requestDestination(Task.SETUP_DIAGNOSTICS);
                break;
            }
        }
        this.lastFingerprint = "";
        this.refresh();
    }

    private void performTertiary() {
        this.actionMessage = "";
        if (this.phase == Phase.COMPARE && "Validate Robustness".equals(this.tertiaryAction.getText())) {
            this.robustnessRequested = true;
        } else if (this.adapter.canAbort()) {
            this.adapter.abortAttempt();
        }
        this.lastFingerprint = "";
        this.refresh();
    }

    private void configureSecondaryForSessions() {
        this.secondaryTitle.setText("Sessions & Logs");
        this.secondarySub.setText("Session/iteration history, log analysis, event qualification, datasets and saved comparisons");
        this.secondaryChoices.removeAll();
        this.addSecondaryChoice("Live Attempts", SecondaryMode.LIVE_ATTEMPTS);
        this.addSecondaryChoice("Current Comparison", SecondaryMode.CURRENT_COMPARISON);
        this.addSecondaryChoice("Iteration History", SecondaryMode.ITERATIONS);
        this.addSecondaryChoice("Log Analysis", SecondaryMode.LOG_ANALYSIS);
        this.addSecondaryChoice("Dataset", SecondaryMode.DATASET);
        this.addSecondaryChoice("Offline Recommendation", SecondaryMode.OFFLINE_RECOMMENDATION);
        if (this.secondaryMode != SecondaryMode.LIVE_ATTEMPTS && this.secondaryMode != SecondaryMode.CURRENT_COMPARISON && this.secondaryMode != SecondaryMode.ITERATIONS && this.secondaryMode != SecondaryMode.LOG_ANALYSIS && this.secondaryMode != SecondaryMode.DATASET && this.secondaryMode != SecondaryMode.OFFLINE_RECOMMENDATION) {
            this.secondaryMode = SecondaryMode.ITERATIONS;
        }
        this.showSecondaryMode(this.secondaryMode);
    }

    private void configureSecondaryForSetup() {
        this.secondaryTitle.setText("Setup & Diagnostics");
        this.secondarySub.setText("Controller mapping, live capture settings and raw diagnostics");
        this.secondaryChoices.removeAll();
        this.addSecondaryChoice("Controller Mapping", SecondaryMode.MAPPING);
        this.addSecondaryChoice("Capture Settings", SecondaryMode.CAPTURE_SETTINGS);
        this.addSecondaryChoice("Raw Diagnostics", SecondaryMode.DIAGNOSTICS);
        if (this.secondaryMode != SecondaryMode.MAPPING && this.secondaryMode != SecondaryMode.CAPTURE_SETTINGS && this.secondaryMode != SecondaryMode.DIAGNOSTICS) {
            this.secondaryMode = SecondaryMode.MAPPING;
        }
        this.showSecondaryMode(this.secondaryMode);
    }

    private void addSecondaryChoice(String string, SecondaryMode secondaryMode) {
        JButton jButton = new JButton(string);
        jButton.setFont(new Font("Dialog", 1, 9));
        jButton.addActionListener(actionEvent -> {
            this.secondaryMode = secondaryMode;
            this.showSecondaryMode(secondaryMode);
            this.applyTheme();
        });
        jButton.putClientProperty("secondary.mode", (Object)secondaryMode);
        this.secondaryChoices.add(jButton);
    }

    private void showSecondaryMode(SecondaryMode secondaryMode) {
        this.secondaryMode = secondaryMode;
        if (secondaryMode == SecondaryMode.CAPTURE_SETTINGS) {
            this.captureSettingsPanel.refreshFromAdapter(true);
            this.secondaryCards.show(this.secondaryBody, "SETTINGS");
        } else {
            this.secondaryCards.show(this.secondaryBody, "LEGACY");
            switch (secondaryMode.ordinal()) {
                case 0: {
                    this.adapter.showLegacy(1, 0);
                    break;
                }
                case 1: {
                    this.adapter.showLegacy(1, 1);
                    break;
                }
                case 2: {
                    this.adapter.showLegacy(1, 2);
                    break;
                }
                case 3: {
                    this.adapter.showLegacy(2, -1);
                    break;
                }
                case 4: {
                    this.adapter.showLegacy(3, -1);
                    break;
                }
                case 5: {
                    this.adapter.showLegacy(4, -1);
                    break;
                }
                case 6: {
                    this.adapter.showLegacy(0, -1);
                    break;
                }
                case 8: {
                    this.adapter.showLegacy(5, -1);
                    break;
                }
            }
        }
    }

    private void requestSetup(SecondaryMode secondaryMode) {
        this.secondaryMode = secondaryMode;
        this.requestDestination(Task.SETUP_DIAGNOSTICS);
    }

    private void requestDestination(Task task) {
        if (this.destinationListener != null) {
            this.destinationListener.open(task);
        } else {
            this.selectTask(task);
        }
    }

    private void updateLiveStrip() {
        LiveSample liveSample = this.adapter.latestSample();
        String string = IdleGuidedPanel.displayState(this.adapter.state());
        if (liveSample == null) {
            this.liveStrip.setMetrics(new Metric("RPM", "\u2014"), new Metric("Target", "\u2014"), new Metric("RPM Error", "\u2014"), new Metric("Idle Position", "\u2014"), new Metric("Capture State", string));
        } else {
            double d = liveSample.get(LiveChannel.RPM);
            double d2 = liveSample.get(LiveChannel.IDLE_TARGET);
            String string2 = IdleGuidedPanel.finite(d) && IdleGuidedPanel.finite(d2) ? IdleGuidedPanel.signedOne(d - d2) + " rpm" : "\u2014";
            this.liveStrip.setMetrics(new Metric("RPM", IdleGuidedPanel.sample(liveSample, LiveChannel.RPM, " rpm")), new Metric("Target", IdleGuidedPanel.sample(liveSample, LiveChannel.IDLE_TARGET, " rpm")), new Metric("RPM Error", string2), new Metric("Idle Position", IdleGuidedPanel.sample(liveSample, LiveChannel.CURRENT_IDLE_POSITION, " %")), new Metric("Capture State", string));
        }
    }

    private LiveSessionSummary baselineOrCurrentSummary() {
        LiveBaselineSnapshot liveBaselineSnapshot = this.adapter.baseline();
        return liveBaselineSnapshot != null && liveBaselineSnapshot.getSummary() != null ? liveBaselineSnapshot.getSummary() : this.adapter.sessionSummary();
    }

    private String baselineHeadline() {
        switch (this.adapter.state()) {
            case WAITING_FOR_STABLE_IDLE: {
                return "WAITING FOR STABLE IDLE";
            }
            case READY_TO_REV: {
                return "READY FOR MANEUVER";
            }
            case REV_IN_PROGRESS: {
                return "REV IN PROGRESS \u2014 RELEASE NORMALLY";
            }
            case WAITING_FOR_IDLE_CONTROL: {
                return "THROTTLE RELEASED \u2014 WAIT";
            }
            case MEASURING_RECOVERY: {
                return "MEASURING RECOVERY \u2014 DO NOT INTERVENE";
            }
            case CONFIRMING_STABLE_IDLE: {
                return "CONFIRMING STABLE IDLE \u2014 KEEP UNTOUCHED";
            }
        }
        return this.adapter.canUseSessionAsBaseline() ? "BASELINE EVIDENCE READY" : "BASELINE CAPTURE STOPPED";
    }

    private String captureGuidance(LiveCaptureState liveCaptureState, LiveAttempt liveAttempt) {
        if (liveAttempt != null && !liveAttempt.isAccepted() && liveCaptureState == LiveCaptureState.WAITING_FOR_STABLE_IDLE) {
            return "Rejected: " + IdleGuidedPanel.safe(liveAttempt.getReason()) + "  \u2022  " + this.correctionFor(liveAttempt);
        }
        return this.adapter.instruction();
    }

    private String correctionFor(LiveAttempt liveAttempt) {
        String string = (IdleGuidedPanel.safe(liveAttempt.getReason()) + " " + IdleGuidedPanel.safe(liveAttempt.getResultCode())).toLowerCase();
        if (string.contains("peak") || string.contains("rev")) {
            return "Use the configured rev range and one smooth release.";
        }
        if (string.contains("tps") || string.contains("driver") || string.contains("throttle")) {
            return "Keep the throttle untouched after release.";
        }
        if (string.contains("vss") || string.contains("motion") || string.contains("stationary")) {
            return "Check the stationary/VSS basis in Capture Settings.";
        }
        if (string.contains("fan") || string.contains("target")) {
            return "Wait until load and target are stable, then repeat.";
        }
        if (string.contains("timeout") || string.contains("settle")) {
            return "Allow the full recovery window; inspect evidence if this repeats.";
        }
        return "Return to stable idle, correct the listed cause, then repeat.";
    }

    private String currentGainConfirmation(LiveExperimentalRecommendation liveExperimentalRecommendation) {
        TuneSnapshot tuneSnapshot = this.adapter.sessionGains();
        if (tuneSnapshot == null || !tuneSnapshot.isComplete()) {
            return "Not captured";
        }
        if (liveExperimentalRecommendation != null && liveExperimentalRecommendation.getProposed() != null && liveExperimentalRecommendation.getProposed().isComplete()) {
            if (IdleGuidedPanel.sameTune(tuneSnapshot, liveExperimentalRecommendation.getProposed())) {
                return "Captured at observer start \u2022 matches proposed candidate";
            }
            if (this.adapter.baseline() != null && IdleGuidedPanel.sameTune(tuneSnapshot, this.adapter.baseline().getGains())) {
                return "Captured at observer start \u2022 still matches baseline";
            }
            return "Captured at observer start \u2022 differs from proposed candidate";
        }
        return IdleGuidedPanel.safe(tuneSnapshot.describeSource());
    }

    private LoadEvidence loadEvidence() {
        IdleAnalysisResult idleAnalysisResult = this.adapter.latestLogAnalysis();
        if (idleAnalysisResult == null || idleAnalysisResult.getEvents() == null) {
            return new LoadEvidence(0, "No classified log loaded", "\u2014");
        }
        int n = 0;
        IdleEvent idleEvent = null;
        for (IdleEvent idleEvent2 : idleAnalysisResult.getEvents()) {
            String string = IdleGuidedPanel.safe(idleEvent2.getType()).toLowerCase();
            if (!string.contains("fan") && !string.contains("load")) continue;
            ++n;
            idleEvent = idleEvent2;
        }
        return new LoadEvidence(n, idleEvent == null ? "\u2014" : IdleGuidedPanel.safe(idleEvent.getType()), idleEvent == null ? "\u2014" : IdleGuidedPanel.metric(idleEvent.getSettlingSeconds(), " s"));
    }

    private boolean statusLooksConnected() {
        String string = this.adapter.connectionText().toLowerCase();
        return string.contains("connected") || string.contains("ready") || string.contains("live");
    }

    private boolean statusLooksMapped() {
        String string = this.adapter.mappingText().toLowerCase();
        return !string.contains("not checked") && !string.contains("missing") && !string.contains("unavailable") && !string.contains("0 /");
    }

    private String phaseLabel() {
        switch (this.phase.ordinal()) {
            case 0: {
                return "1 / CHECK READINESS";
            }
            case 1: {
                return "2 / CAPTURE BASELINE";
            }
            case 2: {
                return "3 / REVIEW EVIDENCE";
            }
            case 3: {
                return "4 / REVIEW + TEST CANDIDATE";
            }
            case 4: {
                return "5 / COMPARE + DECIDE";
            }
            case 5: {
                return "6 / VALIDATE ROBUSTNESS";
            }
        }
        return "IDLE TUNING";
    }

    private boolean comparisonReady(LiveComparisonResult liveComparisonResult) {
        if (liveComparisonResult == null) {
            return false;
        }
        String string = IdleGuidedPanel.safe(liveComparisonResult.getStatus()).toUpperCase();
        return !string.isEmpty() && !"WAITING".equals(string) && !"NO_BASELINE".equals(string) && !"SAME_GAINS".equals(string);
    }

    private String comparisonMeaning(LiveComparisonResult liveComparisonResult) {
        String string = IdleGuidedPanel.safe(liveComparisonResult.getStatus()).toUpperCase();
        if ("INCOMPATIBLE".equals(string)) {
            return "Incompatible conditions \u2014 do not treat as a valid gain comparison.";
        }
        if ("INCONCLUSIVE".equals(string)) {
            return "Comparable enough to evaluate, but evidence does not support a clear decision.";
        }
        return IdleGuidedPanel.safe(liveComparisonResult.getDetails());
    }

    private static boolean sameTune(TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2) {
        return tuneSnapshot != null && tuneSnapshot2 != null && tuneSnapshot.isComplete() && tuneSnapshot2.isComplete() && IdleGuidedPanel.close(tuneSnapshot.getP(), tuneSnapshot2.getP()) && IdleGuidedPanel.close(tuneSnapshot.getI(), tuneSnapshot2.getI()) && IdleGuidedPanel.close(tuneSnapshot.getD(), tuneSnapshot2.getD());
    }

    private static boolean close(double d, double d2) {
        return Math.abs(d - d2) <= 1.0E-9 * Math.max(1.0, Math.max(Math.abs(d), Math.abs(d2)));
    }

    private static String signature(TuneSnapshot tuneSnapshot) {
        return tuneSnapshot == null ? "\u2014" : tuneSnapshot.toSignature();
    }

    private static String tuneValue(TuneSnapshot tuneSnapshot, char c) {
        if (tuneSnapshot == null || !tuneSnapshot.isComplete()) {
            return "\u2014";
        }
        double d = c == 'P' ? tuneSnapshot.getP() : (c == 'I' ? tuneSnapshot.getI() : tuneSnapshot.getD());
        return THREE.format(d);
    }

    private static String metric(double d, String string) {
        return IdleGuidedPanel.finite(d) ? ONE.format(d) + string : "\u2014";
    }

    private static String percent(double d) {
        return IdleGuidedPanel.finite(d) ? ONE.format(d) + "%" : "\u2014";
    }

    private static String signed(double d) {
        return IdleGuidedPanel.finite(d) ? (d >= 0.0 ? "+" : "") + ONE.format(d) : "\u2014";
    }

    private static String signedOne(double d) {
        return IdleGuidedPanel.finite(d) ? (d >= 0.0 ? "+" : "") + ONE.format(d) : "\u2014";
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static String safe(String string) {
        return string == null || string.trim().isEmpty() ? "\u2014" : string.trim();
    }

    private static String sample(LiveSample liveSample, LiveChannel liveChannel, String string) {
        double d = liveSample.get(liveChannel);
        return IdleGuidedPanel.finite(d) ? ONE.format(d) + string : "\u2014";
    }

    private static String displayState(LiveCaptureState liveCaptureState) {
        if (liveCaptureState == null) {
            return "\u2014";
        }
        return liveCaptureState.name().toLowerCase().replace('_', ' ');
    }

    private static Pair pair(String string, String string2) {
        return new Pair(string, string2);
    }

    private static JTextArea textArea(int n) {
        JTextArea jTextArea = new JTextArea(n, 20);
        jTextArea.setLineWrap(true);
        jTextArea.setWrapStyleWord(true);
        jTextArea.setEditable(false);
        jTextArea.setFocusable(false);
        jTextArea.setOpaque(true);
        jTextArea.setBorder(null);
        return jTextArea;
    }

    private void applyHeroTheme(Container container) {
        container.setBackground(UiTheme.card());
        for (Component component : container.getComponents()) {
            if (component instanceof JLabel) {
                Object object = container instanceof JComponent ? ((JComponent)container).getClientProperty("eyebrow") : null;
                ((JLabel)component).setForeground(component == object ? UiTheme.focusBlue() : UiTheme.text());
            }
            if (component instanceof JTextArea) {
                component.setBackground(UiTheme.card());
                component.setForeground(UiTheme.focusMuted());
            }
            if (!(component instanceof Container)) continue;
            this.applyHeroTheme((Container)component);
        }
    }

    public void applyTheme() {
        Object object;
        this.setBackground(UiTheme.bg());
        this.body.setBackground(UiTheme.bg());
        this.tuneView.setBackground(UiTheme.bg());
        this.secondaryView.setBackground(UiTheme.bg());
        this.workflowCenter.setBackground(UiTheme.bg());
        this.heroHolder.setBackground(UiTheme.bg());
        this.workflowStrip.applyTheme();
        this.sidePanel.applyTheme();
        this.liveStrip.applyTheme();
        if (this.tuneView.getComponentCount() > 0 && this.tuneView.getComponent(0) instanceof JPanel) {
            object = (JPanel)this.tuneView.getComponent(0);
            ((JComponent)object).setBackground(UiTheme.bg());
            Object componentArray = ((JComponent)object).getClientProperty("title");
            Object object2 = ((JComponent)object).getClientProperty("sub");
            if (componentArray instanceof JLabel) {
                ((JLabel)componentArray).setForeground(UiTheme.text());
            }
            if (object2 instanceof JLabel) {
                ((JLabel)object2).setForeground(UiTheme.muted());
            }
        }
        this.footer.setBackground(UiTheme.card());
        this.footer.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(5, 7, 5, 7)));
        object = this.footer.getClientProperty("dot");
        if (object instanceof JLabel) {
            ((JLabel)object).setForeground(UiTheme.blue());
        }
        this.nextLabel.setForeground(UiTheme.text());
        this.secondaryHeader.setBackground(UiTheme.bg());
        this.secondaryTitle.setForeground(UiTheme.text());
        this.secondarySub.setForeground(UiTheme.muted());
        this.secondaryChoices.setBackground(UiTheme.bg());
        this.secondaryBody.setBackground(UiTheme.bg());
        this.legacyHolder.setBackground(UiTheme.bg());
        for (Component component : this.secondaryChoices.getComponents()) {
            if (!(component instanceof AbstractButton)) continue;
            SecondaryMode secondaryMode = (SecondaryMode)((Object)((JComponent)component).getClientProperty("secondary.mode"));
            IdleGuidedPanel.styleChoice((AbstractButton)component, secondaryMode == this.secondaryMode);
        }
        this.captureSettingsPanel.applyTheme();
        PidAutotuneWorkspacePanel.applyLegacyTheme((Component)this.legacy);
        IdleGuidedPanel.stylePrimary(this.primaryAction);
        IdleGuidedPanel.styleSecondary(this.secondaryAction);
        IdleGuidedPanel.styleSecondary(this.tertiaryAction);
        this.revalidate();
        this.repaint();
    }

    private static void stylePrimary(AbstractButton abstractButton) {
        abstractButton.setOpaque(true);
        abstractButton.setFocusPainted(false);
        abstractButton.setBackground(abstractButton.isEnabled() ? UiTheme.amber() : UiTheme.disabled());
        abstractButton.setForeground(abstractButton.isEnabled() ? new Color(28, 28, 24) : UiTheme.taskDisabledText());
        abstractButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(abstractButton.isEnabled() ? UiTheme.amberDark() : UiTheme.border()), new EmptyBorder(6, 11, 6, 11)));
    }

    private static void styleSecondary(AbstractButton abstractButton) {
        abstractButton.setOpaque(true);
        abstractButton.setFocusPainted(false);
        abstractButton.setBackground(abstractButton.isEnabled() ? UiTheme.button() : UiTheme.disabled());
        abstractButton.setForeground(abstractButton.isEnabled() ? UiTheme.text() : UiTheme.taskDisabledText());
        abstractButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.buttonBorder()), new EmptyBorder(5, 9, 5, 9)));
    }

    private static void styleChoice(AbstractButton abstractButton, boolean bl) {
        abstractButton.setOpaque(true);
        abstractButton.setFocusPainted(false);
        abstractButton.setBackground(bl ? UiTheme.softBlue() : UiTheme.button());
        abstractButton.setForeground(bl ? UiTheme.blue() : UiTheme.text());
        abstractButton.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(bl ? UiTheme.blue() : UiTheme.buttonBorder()), new EmptyBorder(4, 8, 4, 8)));
    }

    private static enum SecondaryMode {
        LIVE_ATTEMPTS,
        CURRENT_COMPARISON,
        ITERATIONS,
        LOG_ANALYSIS,
        DATASET,
        OFFLINE_RECOMMENDATION,
        MAPPING,
        CAPTURE_SETTINGS,
        DIAGNOSTICS;

    }

    public static enum Task {
        TUNE_IDLE,
        SESSIONS_LOGS,
        SETUP_DIAGNOSTICS;

    }

    private static final class LoadEvidence {
        final int count;
        final String lastType;
        final String lastSettling;

        LoadEvidence(int n, String string, String string2) {
            this.count = n;
            this.lastType = string;
            this.lastSettling = string2;
        }
    }

    private static final class Pair {
        final String name;
        final String value;

        Pair(String string, String string2) {
            this.name = string;
            this.value = string2;
        }
    }

    private final class SidePanel
    extends JPanel {
        private final JPanel head;
        private final JLabel title;
        private final JPanel toggleBar;
        private final JToggleButton evidenceToggle;
        private final JToggleButton settingsToggle;
        private final CardLayout cards;
        private final JPanel content;
        private final JPanel evidence;
        private final CaptureSettingsPanel quickSettings;

        SidePanel() {
            super(new BorderLayout(0, 7));
            this.head = new JPanel(new BorderLayout());
            this.title = new JLabel("Details & Evidence");
            this.toggleBar = new JPanel(new FlowLayout(2, 4, 0));
            this.evidenceToggle = new JToggleButton("Evidence", true);
            this.settingsToggle = new JToggleButton("Capture Settings");
            this.cards = new CardLayout();
            this.content = new JPanel(this.cards);
            this.evidence = new JPanel();
            this.quickSettings = new CaptureSettingsPanel(true);
            this.title.setFont(H2);
            this.head.setOpaque(false);
            this.toggleBar.setOpaque(false);
            ButtonGroup buttonGroup = new ButtonGroup();
            buttonGroup.add(this.evidenceToggle);
            buttonGroup.add(this.settingsToggle);
            this.toggleBar.add(this.evidenceToggle);
            this.toggleBar.add(this.settingsToggle);
            this.head.add((Component)this.title, "West");
            this.head.add((Component)this.toggleBar, "East");
            this.add((Component)this.head, "North");
            this.evidence.setLayout(new BoxLayout(this.evidence, 1));
            JScrollPane jScrollPane = new JScrollPane(this.evidence);
            jScrollPane.setBorder(null);
            jScrollPane.getVerticalScrollBar().setUnitIncrement(14);
            this.content.add((Component)jScrollPane, "EVIDENCE");
            this.content.add((Component)this.quickSettings, "SETTINGS");
            this.add((Component)this.content, "Center");
            this.evidenceToggle.addActionListener(actionEvent -> {
                this.cards.show(this.content, "EVIDENCE");
                this.applyTheme();
            });
            this.settingsToggle.addActionListener(actionEvent -> {
                this.quickSettings.refreshFromAdapter(false);
                this.cards.show(this.content, "SETTINGS");
                this.applyTheme();
            });
            this.setBorder(new EmptyBorder(9, 10, 9, 10));
        }

        void refresh(Phase phase) {
            this.evidence.removeAll();
            for (JComponent jComponent : this.evidenceComponents(phase)) {
                this.evidence.add(jComponent);
            }
            this.quickSettings.refreshFromAdapter(false);
            this.evidence.revalidate();
            this.evidence.repaint();
        }

        List<JComponent> evidenceComponents(Phase phase) {
            Object object;
            ArrayList<JComponent> arrayList = new ArrayList<JComponent>();
            LiveSessionSummary liveSessionSummary = IdleGuidedPanel.this.adapter.sessionSummary();
            LiveExperimentalRecommendation liveExperimentalRecommendation = IdleGuidedPanel.this.adapter.liveRecommendation();
            DatasetAssessment datasetAssessment = IdleGuidedPanel.this.adapter.datasetAssessment();
            if (!IdleGuidedPanel.this.actionMessage.isEmpty()) {
                arrayList.add(this.sideSection("Last action", IdleGuidedPanel.pair("Status", IdleGuidedPanel.this.actionMessage)));
            }
            if (phase == Phase.READINESS || phase == Phase.BASELINE) {
                Object object2;
                LiveReadiness liveReadiness = IdleGuidedPanel.this.adapter.readiness();
                arrayList.add(this.sideSection("Connection / mapping", IdleGuidedPanel.pair("Connection", IdleGuidedPanel.this.adapter.connectionText()), IdleGuidedPanel.pair("Mapping", IdleGuidedPanel.this.adapter.mappingText()), IdleGuidedPanel.pair("Current gains", IdleGuidedPanel.signature(IdleGuidedPanel.this.adapter.sessionGains()))));
                if (liveReadiness != null && !liveReadiness.getRows().isEmpty()) {
                    ArrayList<Pair> readinessPairs = new ArrayList<Pair>();
                    for (Map.Entry entry : liveReadiness.getRows().entrySet()) {
                        readinessPairs.add(IdleGuidedPanel.pair((String)entry.getKey(), (String)entry.getValue()));
                    }
                    arrayList.add(this.sideSection("Live readiness", readinessPairs.toArray(new Pair[readinessPairs.size()])));
                }
                LiveAttempt lastAttempt = IdleGuidedPanel.this.adapter.lastAttempt();
                if (lastAttempt != null) {
                    arrayList.add(this.sideSection("Last attempt", IdleGuidedPanel.pair("Accepted", lastAttempt.isAccepted() ? "Yes" : "No"), IdleGuidedPanel.pair("Quality", lastAttempt.getQuality()), IdleGuidedPanel.pair("Reason", lastAttempt.getReason())));
                }
            } else if (phase == Phase.EVIDENCE) {
                arrayList.add(this.sideSection("Live recommendation gate", IdleGuidedPanel.pair("Accepted / Good", liveSessionSummary.getAcceptedCount() + " / " + liveSessionSummary.getGoodCount()), IdleGuidedPanel.pair("Status", liveExperimentalRecommendation == null ? "Waiting" : liveExperimentalRecommendation.getStatus()), IdleGuidedPanel.pair("Confidence", liveExperimentalRecommendation == null ? "\u2014" : IdleGuidedPanel.percent(liveExperimentalRecommendation.getConfidencePercent())), IdleGuidedPanel.pair("Suggested focus", liveExperimentalRecommendation != null && liveExperimentalRecommendation.isProposalAvailable() ? liveExperimentalRecommendation.getChangedGain() : "No automatic focus")));
                if (datasetAssessment != null) {
                    arrayList.add(this.sideSection("Offline dataset", IdleGuidedPanel.pair("Readiness", datasetAssessment.getLevel()), IdleGuidedPanel.pair("Included events", Integer.toString(datasetAssessment.getIncludedEvents())), IdleGuidedPanel.pair("Strongest group", datasetAssessment.getBestGroup())));
                }
            } else if (phase == Phase.CANDIDATE) {
                arrayList.add(this.sideSection("Candidate qualification", IdleGuidedPanel.pair("Status", liveExperimentalRecommendation == null ? "\u2014" : liveExperimentalRecommendation.getStatus()), IdleGuidedPanel.pair("Confidence", liveExperimentalRecommendation == null ? "\u2014" : IdleGuidedPanel.percent(liveExperimentalRecommendation.getConfidencePercent())), IdleGuidedPanel.pair("Changed gain", liveExperimentalRecommendation == null ? "\u2014" : liveExperimentalRecommendation.getChangedGain()), IdleGuidedPanel.pair("Copied", liveExperimentalRecommendation != null && IdleGuidedPanel.this.adapter.candidateWasCopied(liveExperimentalRecommendation) ? "Clipboard only" : "No")));
                arrayList.add(this.sideSection("Confirmation", IdleGuidedPanel.pair("Captured gains", IdleGuidedPanel.signature(IdleGuidedPanel.this.adapter.sessionGains())), IdleGuidedPanel.pair("Source", IdleGuidedPanel.this.adapter.sessionGains() == null ? "\u2014" : IdleGuidedPanel.this.adapter.sessionGains().describeSource())));
            } else if (phase == Phase.COMPARE) {
                LiveComparisonResult comparison = IdleGuidedPanel.this.adapter.liveComparison();
                arrayList.add(this.sideSection("Comparison", IdleGuidedPanel.pair("Outcome", comparison == null ? "\u2014" : comparison.getStatus()), IdleGuidedPanel.pair("Summary", comparison == null ? "\u2014" : comparison.getSummary()), IdleGuidedPanel.pair("Rollback", IdleGuidedPanel.this.adapter.rollbackBaseline() == null ? "Not stored" : IdleGuidedPanel.signature(IdleGuidedPanel.this.adapter.rollbackBaseline().getGains()))));
                arrayList.add(this.sideSection("Important", IdleGuidedPanel.pair("Keep as baseline", "Changes plugin reference state only."), IdleGuidedPanel.pair("Copy rollback", "Copies gains only; does not restore ECU settings.")));
            } else if (phase == Phase.ROBUSTNESS) {
                LoadEvidence loadEvidence = IdleGuidedPanel.this.loadEvidence();
                arrayList.add(this.sideSection("Evidence sources", IdleGuidedPanel.pair("Return-to-idle", "Live qualified attempts"), IdleGuidedPanel.pair("Steady idle", "Live summary / logs"), IdleGuidedPanel.pair("Load recovery", "Log analysis authority")));
                arrayList.add(this.sideSection("Load recovery", IdleGuidedPanel.pair("Classified events", Integer.toString(loadEvidence.count)), IdleGuidedPanel.pair("Last event", loadEvidence.lastType)));
            }
            JButton setupButton = new JButton("Open Setup & Diagnostics");
            setupButton.setFont(SMALL);
            setupButton.addActionListener(actionEvent -> IdleGuidedPanel.this.requestDestination(Task.SETUP_DIAGNOSTICS));
            IdleGuidedPanel.styleSecondary(setupButton);
            arrayList.add(setupButton);
            return arrayList;
        }

        JPanel sideSection(String string, Pair ... pairArray) {
            JPanel jPanel = new JPanel(new BorderLayout(0, 4));
            jPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 7, 0));
            jPanel.setOpaque(false);
            JLabel jLabel = new JLabel(string);
            jLabel.setFont(H3);
            jLabel.setForeground(UiTheme.text());
            jPanel.add((Component)jLabel, "North");
            JPanel jPanel2 = IdleGuidedPanel.this.vertical();
            for (Pair pair : pairArray) {
                jPanel2.add(IdleGuidedPanel.this.detailLine(pair.name, pair.value));
            }
            jPanel.add((Component)jPanel2, "Center");
            return jPanel;
        }

        void applyTheme() {
            this.setBackground(UiTheme.card());
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(9, 10, 9, 10)));
            this.head.setBackground(UiTheme.card());
            this.title.setForeground(UiTheme.text());
            this.toggleBar.setBackground(UiTheme.card());
            this.content.setBackground(UiTheme.card());
            this.evidence.setBackground(UiTheme.card());
            IdleGuidedPanel.styleChoice(this.evidenceToggle, this.evidenceToggle.isSelected());
            IdleGuidedPanel.styleChoice(this.settingsToggle, this.settingsToggle.isSelected());
            this.quickSettings.applyTheme();
        }
    }

    private static final class WorkflowStrip
    extends JComponent {
        private int active;
        private final String[] names = new String[]{"Readiness", "Baseline", "Evidence", "Candidate", "Compare", "Robustness"};

        void setActive(int n) {
            this.active = Math.max(0, Math.min(5, n));
            this.repaint();
        }

        WorkflowStrip() {
            this.setPreferredSize(new Dimension(500, 54));
        }

        void applyTheme() {
            this.setBackground(UiTheme.bg());
            this.setForeground(UiTheme.text());
            this.repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D graphics2D = (Graphics2D)graphics.create();
            graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = this.getWidth();
            int n2 = 17;
            int n3 = 32;
            int n4 = n - 32;
            int n5 = (n4 - n3) / 5;
            graphics2D.setColor(UiTheme.border());
            graphics2D.drawLine(n3, n2, n4, n2);
            for (int i = 0; i < 6; ++i) {
                boolean bl;
                int n6 = n3 + i * n5;
                boolean bl2 = i < this.active;
                boolean bl3 = bl = i == this.active;
                graphics2D.setColor(bl2 ? UiTheme.green() : (bl ? UiTheme.blue() : UiTheme.panel()));
                graphics2D.fillOval(n6 - 10, n2 - 10, 20, 20);
                graphics2D.setColor(bl2 || bl ? Color.WHITE : UiTheme.muted());
                String string = bl2 ? "\u2713" : Integer.toString(i + 1);
                FontMetrics fontMetrics = graphics2D.getFontMetrics(new Font("Dialog", 1, 9));
                graphics2D.setFont(new Font("Dialog", 1, 9));
                graphics2D.drawString(string, n6 - fontMetrics.stringWidth(string) / 2, n2 + 4);
                graphics2D.setColor(bl ? UiTheme.text() : UiTheme.muted());
                graphics2D.setFont(new Font("Dialog", bl ? 1 : 0, 9));
                String string2 = this.names[i];
                FontMetrics fontMetrics2 = graphics2D.getFontMetrics();
                graphics2D.drawString(string2, n6 - fontMetrics2.stringWidth(string2) / 2, n2 + 28);
            }
            graphics2D.dispose();
        }
    }

    private static final class MetricStrip
    extends JPanel {
        private Metric[] metrics = new Metric[0];

        MetricStrip() {
            super(new GridLayout(1, 1, 0, 0));
            this.setBorder(new EmptyBorder(5, 7, 5, 7));
        }

        void setMetrics(Metric ... metricArray) {
            this.metrics = metricArray == null ? new Metric[]{} : metricArray;
            this.removeAll();
            this.setLayout(new GridLayout(1, Math.max(1, this.metrics.length), 0, 0));
            for (Metric metric : this.metrics) {
                this.add(new MetricCell(metric));
            }
            this.applyTheme();
            this.revalidate();
            this.repaint();
        }

        void applyTheme() {
            this.setBackground(UiTheme.card());
            this.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.border()), new EmptyBorder(5, 7, 5, 7)));
            for (Component component : this.getComponents()) {
                if (!(component instanceof MetricCell)) continue;
                ((MetricCell)component).applyTheme();
            }
        }
    }

    private final class CaptureSettingsPanel
    extends JPanel {
        private final boolean quick;
        private final JComboBox<LiveCaptureProfile> profile;
        private final JComboBox<LiveMotionMode> motion;
        private final JCheckBox stationary;
        private final JTextField minClt;
        private final JTextField maxClt;
        private final JTextField revMin;
        private final JTextField revMax;
        private final JTextField baselineBand;
        private final JTextField settlingBand;
        private final JTextField exitHys;
        private final JTextField observation;
        private final JTextField timeout;
        private final JButton apply;
        private final JButton full;
        private final JLabel status;

        CaptureSettingsPanel(boolean bl) {
            super(new BorderLayout(0, 7));
            this.profile = new JComboBox<LiveCaptureProfile>(LiveCaptureProfile.values());
            this.motion = new JComboBox<LiveMotionMode>(LiveMotionMode.values());
            this.stationary = new JCheckBox("Manually confirm stationary");
            this.minClt = new JTextField(5);
            this.maxClt = new JTextField(5);
            this.revMin = new JTextField(5);
            this.revMax = new JTextField(5);
            this.baselineBand = new JTextField(5);
            this.settlingBand = new JTextField(5);
            this.exitHys = new JTextField(5);
            this.observation = new JTextField(5);
            this.timeout = new JTextField(5);
            this.apply = new JButton("Apply Capture Settings");
            this.full = new JButton("Open Full Setup");
            this.status = new JLabel(" ");
            this.quick = bl;
            JPanel jPanel = new JPanel(new GridBagLayout());
            jPanel.setOpaque(false);
            GridBagConstraints gridBagConstraints = new GridBagConstraints();
            gridBagConstraints.insets = new Insets(3, 3, 3, 3);
            gridBagConstraints.fill = 2;
            gridBagConstraints.weightx = 0.0;
            int n = 0;
            n = this.addField(jPanel, gridBagConstraints, n, "Capture profile", this.profile);
            n = this.addField(jPanel, gridBagConstraints, n, "Motion basis", this.motion);
            n = this.addField(jPanel, gridBagConstraints, n, "Preferred rev min", this.revMin);
            n = this.addField(jPanel, gridBagConstraints, n, "Preferred rev max", this.revMax);
            if (!bl) {
                n = this.addField(jPanel, gridBagConstraints, n, "Minimum CLT", this.minClt);
                n = this.addField(jPanel, gridBagConstraints, n, "Maximum CLT", this.maxClt);
                n = this.addField(jPanel, gridBagConstraints, n, "Baseline band", this.baselineBand);
                n = this.addField(jPanel, gridBagConstraints, n, "Settling band", this.settlingBand);
                n = this.addField(jPanel, gridBagConstraints, n, "Exit hysteresis", this.exitHys);
                n = this.addField(jPanel, gridBagConstraints, n, "Stable observation", this.observation);
                n = this.addField(jPanel, gridBagConstraints, n, "Recovery timeout", this.timeout);
            }
            gridBagConstraints.gridx = 0;
            gridBagConstraints.gridy = n;
            gridBagConstraints.gridwidth = 2;
            gridBagConstraints.weightx = 1.0;
            jPanel.add((Component)this.stationary, gridBagConstraints);
            JPanel jPanel2 = new JPanel(new FlowLayout(0, 5, 0));
            jPanel2.setOpaque(false);
            jPanel2.add(this.apply);
            if (bl) {
                jPanel2.add(this.full);
            }
            this.status.setFont(SMALL);
            JPanel jPanel3 = new JPanel(new BorderLayout(0, 4));
            jPanel3.setOpaque(false);
            jPanel3.add((Component)jPanel2, "North");
            jPanel3.add((Component)this.status, "South");
            this.add((Component)jPanel, "North");
            this.add((Component)jPanel3, "South");
            this.apply.addActionListener(actionEvent -> this.applySettings());
            this.full.addActionListener(actionEvent -> IdleGuidedPanel.this.requestSetup(SecondaryMode.CAPTURE_SETTINGS));
        }

        int addField(JPanel jPanel, GridBagConstraints gridBagConstraints, int n, String string, JComponent jComponent) {
            gridBagConstraints.gridwidth = 1;
            gridBagConstraints.gridy = n;
            gridBagConstraints.gridx = 0;
            gridBagConstraints.weightx = 0.0;
            JLabel jLabel = new JLabel(string);
            jLabel.setFont(SMALL);
            jPanel.add((Component)jLabel, gridBagConstraints);
            gridBagConstraints.gridx = 1;
            gridBagConstraints.weightx = 1.0;
            jPanel.add((Component)jComponent, gridBagConstraints);
            jComponent.putClientProperty("settings.label", jLabel);
            return n + 1;
        }

        void refreshFromAdapter(boolean bl) {
            if (!bl && this.editorFocused()) {
                return;
            }
            IdleWorkflowAdapter.CaptureSettingsSnapshot captureSettingsSnapshot = IdleGuidedPanel.this.adapter.captureSettings();
            if (captureSettingsSnapshot == null) {
                return;
            }
            this.profile.setSelectedItem(captureSettingsSnapshot.profile);
            this.motion.setSelectedItem(captureSettingsSnapshot.motionMode);
            this.stationary.setSelected(captureSettingsSnapshot.stationaryConfirmed);
            this.minClt.setText(captureSettingsSnapshot.minClt);
            this.maxClt.setText(captureSettingsSnapshot.maxClt);
            this.revMin.setText(captureSettingsSnapshot.revMin);
            this.revMax.setText(captureSettingsSnapshot.revMax);
            this.baselineBand.setText(captureSettingsSnapshot.baselineBand);
            this.settlingBand.setText(captureSettingsSnapshot.settlingBand);
            this.exitHys.setText(captureSettingsSnapshot.exitHysteresis);
            this.observation.setText(captureSettingsSnapshot.observationSeconds);
            this.timeout.setText(captureSettingsSnapshot.recoveryTimeout);
            this.apply.setEnabled(!IdleGuidedPanel.this.adapter.isRunning());
            this.status.setText(IdleGuidedPanel.this.adapter.isRunning() ? "Stop the observer before editing capture settings." : "These settings affect capture qualification only; they do not write ECU gains.");
        }

        boolean editorFocused() {
            if (this.profile.hasFocus() || this.motion.hasFocus() || this.stationary.hasFocus()) {
                return true;
            }
            for (JTextField jTextField : new JTextField[]{this.minClt, this.maxClt, this.revMin, this.revMax, this.baselineBand, this.settlingBand, this.exitHys, this.observation, this.timeout}) {
                if (!jTextField.hasFocus()) continue;
                return true;
            }
            return false;
        }

        void applySettings() {
            IdleWorkflowAdapter.CaptureSettingsSnapshot captureSettingsSnapshot = IdleGuidedPanel.this.adapter.captureSettings();
            IdleWorkflowAdapter.CaptureSettingsSnapshot captureSettingsSnapshot2 = new IdleWorkflowAdapter.CaptureSettingsSnapshot((LiveCaptureProfile)this.profile.getSelectedItem(), (LiveMotionMode)this.motion.getSelectedItem(), this.stationary.isSelected(), this.quick ? captureSettingsSnapshot.minClt : this.minClt.getText(), this.quick ? captureSettingsSnapshot.maxClt : this.maxClt.getText(), this.revMin.getText(), this.revMax.getText(), this.quick ? captureSettingsSnapshot.baselineBand : this.baselineBand.getText(), this.quick ? captureSettingsSnapshot.settlingBand : this.settlingBand.getText(), this.quick ? captureSettingsSnapshot.exitHysteresis : this.exitHys.getText(), this.quick ? captureSettingsSnapshot.observationSeconds : this.observation.getText(), this.quick ? captureSettingsSnapshot.recoveryTimeout : this.timeout.getText());
            this.status.setText(IdleGuidedPanel.this.adapter.applyCaptureSettings(captureSettingsSnapshot2));
            IdleGuidedPanel.this.lastFingerprint = "";
            IdleGuidedPanel.this.refresh();
        }

        void applyTheme() {
            Object object;
            this.setBackground(UiTheme.card());
            this.profile.setBackground(UiTheme.card());
            this.profile.setForeground(UiTheme.text());
            this.motion.setBackground(UiTheme.card());
            this.motion.setForeground(UiTheme.text());
            this.stationary.setBackground(UiTheme.card());
            this.stationary.setForeground(UiTheme.text());
            for (JTextField jComponent : new JTextField[]{this.minClt, this.maxClt, this.revMin, this.revMax, this.baselineBand, this.settlingBand, this.exitHys, this.observation, this.timeout}) {
                jComponent.setBackground(UiTheme.card());
                jComponent.setForeground(UiTheme.text());
                jComponent.setBorder(BorderFactory.createLineBorder(UiTheme.border()));
                object = jComponent.getClientProperty("settings.label");
                if (!(object instanceof JLabel)) continue;
                ((JLabel)object).setForeground(UiTheme.muted());
            }
            for (JComponent jComponent : new JComponent[]{this.profile, this.motion}) {
                object = jComponent.getClientProperty("settings.label");
                if (!(object instanceof JLabel)) continue;
                ((JLabel)object).setForeground(UiTheme.muted());
            }
            this.status.setForeground(UiTheme.muted());
            IdleGuidedPanel.stylePrimary(this.apply);
            IdleGuidedPanel.styleSecondary(this.full);
        }
    }

    private static enum Phase {
        READINESS,
        BASELINE,
        EVIDENCE,
        CANDIDATE,
        COMPARE,
        ROBUSTNESS;

    }

    private static enum Focus {
        RECOVERY,
        STEADY,
        DAMPING;

    }

    private static final class HiddenTabbedPaneUI
    extends BasicTabbedPaneUI {
        private HiddenTabbedPaneUI() {
        }

        @Override
        protected int calculateTabAreaHeight(int n, int n2, int n3) {
            return 0;
        }

        @Override
        protected int calculateTabAreaWidth(int n, int n2, int n3) {
            return 0;
        }

        @Override
        protected void paintTabArea(Graphics graphics, int n, int n2) {
        }
    }

    public static interface DestinationListener {
        public void open(Task var1);
    }

    private static final class Metric {
        final String name;
        final String value;

        Metric(String string, String string2) {
            this.name = string;
            this.value = string2;
        }
    }

    private static final class MetricCell
    extends JPanel {
        private final JLabel name = new JLabel();
        private final JLabel value = new JLabel();

        MetricCell(Metric metric) {
            super(new GridLayout(0, 1, 0, 1));
            this.name.setText(metric.name);
            this.name.setFont(SMALL);
            this.value.setText(metric.value);
            this.value.setFont(VALUE);
            this.add(this.name);
            this.add(this.value);
            this.setBorder(new EmptyBorder(1, 8, 1, 8));
        }

        void applyTheme() {
            this.setBackground(UiTheme.card());
            this.name.setForeground(UiTheme.muted());
            this.value.setForeground(UiTheme.blue());
        }
    }
}

