/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.efiAnalytics.plugin.ecu.ControllerAccess
 *  se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult
 *  se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment
 *  se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot
 *  se.anders.tunerstudio.pidautotune.live.LiveAttempt
 *  se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot
 *  se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile
 *  se.anders.tunerstudio.pidautotune.live.LiveCaptureState
 *  se.anders.tunerstudio.pidautotune.live.LiveComparisonResult
 *  se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation
 *  se.anders.tunerstudio.pidautotune.live.LiveGuidedCaptureEngine
 *  se.anders.tunerstudio.pidautotune.live.LiveIterationRecord
 *  se.anders.tunerstudio.pidautotune.live.LiveMotionMode
 *  se.anders.tunerstudio.pidautotune.live.LiveReadiness
 *  se.anders.tunerstudio.pidautotune.live.LiveSample
 *  se.anders.tunerstudio.pidautotune.live.LiveSessionSummary
 *  se.anders.tunerstudio.pidautotune.recommendation.PidRecommendation
 *  se.anders.tunerstudio.pidautotune.ui.LiveCapturePanel
 *  se.anders.tunerstudio.pidautotune.ui.PidAutotunePanel
 */
package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import java.awt.Component;
import java.awt.Container;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult;
import se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveAttempt;
import se.anders.tunerstudio.pidautotune.live.LiveBaselineSnapshot;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureProfile;
import se.anders.tunerstudio.pidautotune.live.LiveCaptureState;
import se.anders.tunerstudio.pidautotune.live.LiveComparisonResult;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveGuidedCaptureEngine;
import se.anders.tunerstudio.pidautotune.live.LiveIterationRecord;
import se.anders.tunerstudio.pidautotune.live.LiveMotionMode;
import se.anders.tunerstudio.pidautotune.live.LiveReadiness;
import se.anders.tunerstudio.pidautotune.live.LiveSample;
import se.anders.tunerstudio.pidautotune.live.LiveSessionSummary;
import se.anders.tunerstudio.pidautotune.recommendation.PidRecommendation;
import se.anders.tunerstudio.pidautotune.ui.LiveCapturePanel;
import se.anders.tunerstudio.pidautotune.ui.PidAutotunePanel;

final class IdleWorkflowAdapter {
    private final PidAutotunePanel legacy = new PidAutotunePanel();
    private final LiveCapturePanel live = IdleWorkflowAdapter.field(this.legacy, "liveCapturePanel", LiveCapturePanel.class);
    private final LiveGuidedCaptureEngine engine = IdleWorkflowAdapter.field(this.live, "engine", LiveGuidedCaptureEngine.class);
    private final JTabbedPane mainTabs = IdleWorkflowAdapter.firstDirectTabs((Container)this.legacy);
    private final JTabbedPane liveTabs = IdleWorkflowAdapter.firstDescendantTabs((Container)this.live);
    private final JLabel connectionValue = IdleWorkflowAdapter.field(this.legacy, "connectionValue", JLabel.class);
    private final JLabel mappingValue = IdleWorkflowAdapter.field(this.legacy, "mappingValue", JLabel.class);
    private final JButton startButton = IdleWorkflowAdapter.field(this.live, "startButton", JButton.class);
    private final JButton stopButton = IdleWorkflowAdapter.field(this.live, "stopButton", JButton.class);
    private final JButton abortButton = IdleWorkflowAdapter.field(this.live, "abortButton", JButton.class);
    private final JButton resetButton = IdleWorkflowAdapter.field(this.live, "resetButton", JButton.class);
    private final JButton useSessionAsBaselineButton = IdleWorkflowAdapter.field(this.live, "setBaselineButton", JButton.class);
    private final JButton keepAsBaselineButton = IdleWorkflowAdapter.field(this.live, "keepAsBaselineButton", JButton.class);
    private final JButton testNextCandidateButton = IdleWorkflowAdapter.field(this.live, "testNextCandidateButton", JButton.class);
    private final JButton copyRollbackButton = IdleWorkflowAdapter.field(this.live, "copyRollbackButton", JButton.class);
    private final JComboBox<LiveCaptureProfile> captureProfileCombo = IdleWorkflowAdapter.field(this.live, "captureProfileCombo", JComboBox.class);
    private final JComboBox<LiveMotionMode> motionModeCombo = IdleWorkflowAdapter.field(this.live, "motionModeCombo", JComboBox.class);
    private final JCheckBox manualStationaryCheck = IdleWorkflowAdapter.field(this.live, "manualStationaryCheck", JCheckBox.class);
    private final JTextField minimumCltField = IdleWorkflowAdapter.field(this.live, "minimumCltField", JTextField.class);
    private final JTextField maximumCltField = IdleWorkflowAdapter.field(this.live, "maximumCltField", JTextField.class);
    private final JTextField preferredRevMinimumField = IdleWorkflowAdapter.field(this.live, "preferredRevMinimumField", JTextField.class);
    private final JTextField preferredRevMaximumField = IdleWorkflowAdapter.field(this.live, "preferredRevMaximumField", JTextField.class);
    private final JTextField baselineBandField = IdleWorkflowAdapter.field(this.live, "baselineBandField", JTextField.class);
    private final JTextField settlingBandField = IdleWorkflowAdapter.field(this.live, "settlingBandField", JTextField.class);
    private final JTextField exitHysteresisField = IdleWorkflowAdapter.field(this.live, "exitHysteresisField", JTextField.class);
    private final JTextField observationSecondsField = IdleWorkflowAdapter.field(this.live, "observationSecondsField", JTextField.class);
    private final JTextField recoveryTimeoutField = IdleWorkflowAdapter.field(this.live, "recoveryTimeoutField", JTextField.class);
    private String copiedCandidateSignature;
    private String copiedRollbackSignature;

    IdleWorkflowAdapter() {
    }

    PidAutotunePanel legacyPanel() {
        return this.legacy;
    }

    JTabbedPane mainTabs() {
        return this.mainTabs;
    }

    JTabbedPane liveTabs() {
        return this.liveTabs;
    }

    void connect(ControllerAccess controllerAccess, String string) {
        this.legacy.connect(controllerAccess, string);
    }

    void disconnect() {
        this.legacy.disconnect();
    }

    void setControllerSignature(String string) {
        this.legacy.setControllerSignature(string);
    }

    String connectionText() {
        return IdleWorkflowAdapter.clean(this.connectionValue.getText(), "Not initialized");
    }

    String mappingText() {
        return IdleWorkflowAdapter.clean(this.mappingValue.getText(), "Not checked");
    }

    LiveCaptureState state() {
        return this.engine.getState();
    }

    String instruction() {
        return IdleWorkflowAdapter.clean(this.engine.getInstruction(), "No live instruction available.");
    }

    String lastResult() {
        return IdleWorkflowAdapter.clean(this.engine.getLastResult(), "No live attempts yet.");
    }

    String resultCode() {
        return IdleWorkflowAdapter.clean(this.engine.getResultCode(), "");
    }

    LiveReadiness readiness() {
        return this.engine.getReadiness();
    }

    List<LiveAttempt> attempts() {
        return this.engine.getAttempts();
    }

    int acceptedCount() {
        return this.engine.getAcceptedCount();
    }

    int goodCount() {
        return this.engine.getGoodCount();
    }

    int rejectedCount() {
        return this.engine.getRejectedCount();
    }

    LiveSessionSummary sessionSummary() {
        return LiveSessionSummary.from((List)this.engine.getAttempts());
    }

    boolean isRunning() {
        return this.engine.getState() != LiveCaptureState.STOPPED;
    }

    LiveAttempt lastAttempt() {
        List list = this.engine.getAttempts();
        return list == null || list.isEmpty() ? null : (LiveAttempt)list.get(list.size() - 1);
    }

    LiveSample latestSample() {
        List list = IdleWorkflowAdapter.field(this.live, "sessionSamples", List.class);
        return list == null || list.isEmpty() ? null : (LiveSample)list.get(list.size() - 1);
    }

    TuneSnapshot sessionGains() {
        return IdleWorkflowAdapter.field(this.live, "sessionTuneSnapshot", TuneSnapshot.class);
    }

    LiveBaselineSnapshot baseline() {
        return IdleWorkflowAdapter.field(this.live, "baselineSnapshot", LiveBaselineSnapshot.class);
    }

    LiveBaselineSnapshot rollbackBaseline() {
        return IdleWorkflowAdapter.field(this.live, "originalBaselineSnapshot", LiveBaselineSnapshot.class);
    }

    LiveExperimentalRecommendation liveRecommendation() {
        return IdleWorkflowAdapter.field(this.live, "latestLiveRecommendation", LiveExperimentalRecommendation.class);
    }

    LiveComparisonResult liveComparison() {
        return IdleWorkflowAdapter.field(this.live, "latestLiveComparison", LiveComparisonResult.class);
    }

    List<LiveIterationRecord> tuningHistory() {
        List list = IdleWorkflowAdapter.field(this.live, "tuningHistory", List.class);
        return list == null ? Collections.emptyList() : new ArrayList(list);
    }

    DatasetAssessment datasetAssessment() {
        return IdleWorkflowAdapter.field(this.legacy, "latestDatasetAssessment", DatasetAssessment.class);
    }

    PidRecommendation offlineRecommendation() {
        return IdleWorkflowAdapter.field(this.legacy, "latestRecommendation", PidRecommendation.class);
    }

    IdleAnalysisResult latestLogAnalysis() {
        return IdleWorkflowAdapter.field(this.legacy, "latestAnalysis", IdleAnalysisResult.class);
    }

    boolean canStart() {
        return this.startButton.isEnabled();
    }

    boolean canStop() {
        return this.stopButton.isEnabled();
    }

    boolean canAbort() {
        return this.abortButton.isEnabled();
    }

    boolean canReset() {
        return this.resetButton.isEnabled();
    }

    boolean canUseSessionAsBaseline() {
        return this.useSessionAsBaselineButton.isEnabled();
    }

    boolean canKeepAsBaseline() {
        return this.keepAsBaselineButton.isEnabled();
    }

    boolean canPrepareNextCandidate() {
        return this.testNextCandidateButton.isEnabled();
    }

    boolean canCopyRollback() {
        return this.copyRollbackButton.isEnabled();
    }

    void startObserver() {
        IdleWorkflowAdapter.click(this.startButton);
    }

    void stopObserver() {
        IdleWorkflowAdapter.click(this.stopButton);
    }

    void abortAttempt() {
        IdleWorkflowAdapter.click(this.abortButton);
    }

    void resetSession() {
        IdleWorkflowAdapter.click(this.resetButton);
    }

    void useSessionAsBaseline() {
        IdleWorkflowAdapter.click(this.useSessionAsBaselineButton);
    }

    void keepAsBaseline() {
        IdleWorkflowAdapter.click(this.keepAsBaselineButton);
    }

    void prepareNextCandidateLegacy() {
        IdleWorkflowAdapter.click(this.testNextCandidateButton);
    }

    void copyRollbackGains() {
        LiveBaselineSnapshot liveBaselineSnapshot = this.rollbackBaseline();
        if (liveBaselineSnapshot != null && liveBaselineSnapshot.getGains() != null && liveBaselineSnapshot.getGains().isComplete()) {
            this.copiedRollbackSignature = liveBaselineSnapshot.getGains().toSignature();
        }
        IdleWorkflowAdapter.click(this.copyRollbackButton);
    }

    boolean copyCandidateGains() {
        LiveExperimentalRecommendation liveExperimentalRecommendation = this.liveRecommendation();
        if (liveExperimentalRecommendation == null || !liveExperimentalRecommendation.isProposalAvailable() || liveExperimentalRecommendation.getProposed() == null || !liveExperimentalRecommendation.getProposed().isComplete()) {
            return false;
        }
        String string = liveExperimentalRecommendation.getProposed().toSignature();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(string), null);
        this.copiedCandidateSignature = string;
        return true;
    }

    boolean candidateWasCopied(LiveExperimentalRecommendation liveExperimentalRecommendation) {
        return liveExperimentalRecommendation != null && liveExperimentalRecommendation.getProposed() != null && this.copiedCandidateSignature != null && this.copiedCandidateSignature.equals(liveExperimentalRecommendation.getProposed().toSignature());
    }

    String copiedRollbackSignature() {
        return this.copiedRollbackSignature;
    }

    CaptureSettingsSnapshot captureSettings() {
        return new CaptureSettingsSnapshot((LiveCaptureProfile)this.captureProfileCombo.getSelectedItem(), (LiveMotionMode)this.motionModeCombo.getSelectedItem(), this.manualStationaryCheck.isSelected(), this.minimumCltField.getText(), this.maximumCltField.getText(), this.preferredRevMinimumField.getText(), this.preferredRevMaximumField.getText(), this.baselineBandField.getText(), this.settlingBandField.getText(), this.exitHysteresisField.getText(), this.observationSecondsField.getText(), this.recoveryTimeoutField.getText());
    }

    String applyCaptureSettings(CaptureSettingsSnapshot captureSettingsSnapshot) {
        if (captureSettingsSnapshot == null) {
            return "No settings supplied.";
        }
        if (this.isRunning()) {
            return "Stop the live observer before changing capture settings.";
        }
        if (captureSettingsSnapshot.profile != null) {
            this.captureProfileCombo.setSelectedItem(captureSettingsSnapshot.profile);
        }
        if (captureSettingsSnapshot.motionMode != null) {
            this.motionModeCombo.setSelectedItem(captureSettingsSnapshot.motionMode);
        }
        this.manualStationaryCheck.setSelected(captureSettingsSnapshot.stationaryConfirmed);
        this.minimumCltField.setText(captureSettingsSnapshot.minClt);
        this.maximumCltField.setText(captureSettingsSnapshot.maxClt);
        this.preferredRevMinimumField.setText(captureSettingsSnapshot.revMin);
        this.preferredRevMaximumField.setText(captureSettingsSnapshot.revMax);
        this.baselineBandField.setText(captureSettingsSnapshot.baselineBand);
        this.settlingBandField.setText(captureSettingsSnapshot.settlingBand);
        this.exitHysteresisField.setText(captureSettingsSnapshot.exitHysteresis);
        this.observationSecondsField.setText(captureSettingsSnapshot.observationSeconds);
        this.recoveryTimeoutField.setText(captureSettingsSnapshot.recoveryTimeout);
        return "Capture settings updated in the retained live-capture controls. They will be validated by the existing engine when capture starts.";
    }

    void showLegacy(int n, int n2) {
        if (n >= 0 && n < this.mainTabs.getTabCount()) {
            this.mainTabs.setSelectedIndex(n);
        }
        if (n2 >= 0 && this.liveTabs != null && n2 < this.liveTabs.getTabCount()) {
            this.liveTabs.setSelectedIndex(n2);
        }
    }

    private static void click(JButton jButton) {
        if (jButton != null && jButton.isEnabled()) {
            jButton.doClick();
        }
    }

    private static String clean(String string, String string2) {
        if (string == null || string.trim().isEmpty()) {
            return string2;
        }
        return string.trim();
    }

    private static <T> T field(Object object, String string, Class<T> clazz) {
        if (object == null) {
            return null;
        }
        try {
            Field field = object.getClass().getDeclaredField(string);
            field.setAccessible(true);
            Object object2 = field.get(object);
            if (object2 == null) {
                return null;
            }
            if (!clazz.isInstance(object2)) {
                throw new IllegalStateException("Field " + string + " was " + object2.getClass().getName() + ", expected " + clazz.getName());
            }
            return clazz.cast(object2);
        }
        catch (Exception exception) {
            throw new IllegalStateException("Idle workflow binding failed for field '" + string + "' on " + object.getClass().getName(), exception);
        }
    }

    private static JTabbedPane firstDirectTabs(Container container) {
        for (Component component : container.getComponents()) {
            if (!(component instanceof JTabbedPane)) continue;
            return (JTabbedPane)component;
        }
        throw new IllegalStateException("Idle primary tab container not found");
    }

    private static JTabbedPane firstDescendantTabs(Container container) {
        for (Component component : container.getComponents()) {
            if (component instanceof JTabbedPane) {
                return (JTabbedPane)component;
            }
            if (!(component instanceof Container)) continue;
            try {
                return IdleWorkflowAdapter.firstDescendantTabs((Container)component);
            }
            catch (IllegalStateException illegalStateException) {
                // empty catch block
            }
        }
        throw new IllegalStateException("Idle live tab container not found");
    }

    static final class CaptureSettingsSnapshot {
        final LiveCaptureProfile profile;
        final LiveMotionMode motionMode;
        final boolean stationaryConfirmed;
        final String minClt;
        final String maxClt;
        final String revMin;
        final String revMax;
        final String baselineBand;
        final String settlingBand;
        final String exitHysteresis;
        final String observationSeconds;
        final String recoveryTimeout;

        CaptureSettingsSnapshot(LiveCaptureProfile liveCaptureProfile, LiveMotionMode liveMotionMode, boolean bl, String string, String string2, String string3, String string4, String string5, String string6, String string7, String string8, String string9) {
            this.profile = liveCaptureProfile;
            this.motionMode = liveMotionMode;
            this.stationaryConfirmed = bl;
            this.minClt = string;
            this.maxClt = string2;
            this.revMin = string3;
            this.revMax = string4;
            this.baselineBand = string5;
            this.settlingBand = string6;
            this.exitHysteresis = string7;
            this.observationSeconds = string8;
            this.recoveryTimeout = string9;
        }
    }
}

