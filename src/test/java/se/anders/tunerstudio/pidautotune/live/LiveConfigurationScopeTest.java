package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.ui.LiveCapturePanel;

import javax.swing.JButton;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveConfigurationScopeTest {
    @Test
    public void crossConfigurationComparisonIsRejectedBeforeEvidenceChecks() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();
        LiveBaselineSnapshot baseline = baseline("config-A");

        LiveComparisonResult result = engine.compare(
                baseline,
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "config-B");

        assertEquals(LiveComparisonResult.INCOMPATIBLE, result.getStatus());
        assertTrue(result.getSummary().contains("ECU configuration differs"));
        assertTrue(result.getSummary().contains("config-A"));
        assertTrue(result.getSummary().contains("config-B"));
    }

    @Test
    public void sameConfigurationContinuesToNormalComparisonReadiness() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();

        LiveComparisonResult result = engine.compare(
                baseline("config-A"),
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A");

        assertEquals(LiveComparisonResult.WAITING, result.getStatus());
    }

    @Test
    public void unidentifiedLegacyBaselineIsNotTrustedForNamedConfiguration() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();
        LiveBaselineSnapshot legacy = new LiveBaselineSnapshot(
                gains(1.0, 1.0, 1.0),
                LiveSessionSummary.empty(),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "legacy");

        LiveComparisonResult result = engine.compare(
                legacy,
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A");

        assertEquals(LiveComparisonResult.INCOMPATIBLE, result.getStatus());
    }

    @Test
    public void archivedIterationRetainsConfigurationWhenUsedAsBaseline() {
        LiveIterationRecord record = new LiveIterationRecord(
                4,
                1000L,
                2000L,
                gains(1.0, 2.0, 3.0),
                LiveSessionSummary.empty(),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A",
                null,
                null,
                null,
                "Inconclusive",
                "test",
                Collections.<LiveAttempt>emptyList(),
                Collections.<LiveSessionRecord>emptyList());

        assertEquals("config-A", record.getConfigurationIdentity());
        assertEquals("config-A", record.asBaseline("previous").getConfigurationIdentity());
        assertTrue(record.asBaseline("previous").isForConfiguration("config-A"));
        assertFalse(record.asBaseline("previous").isForConfiguration("config-B"));
    }

    @Test
    public void rollbackButtonIsDisabledForDifferentConfiguration() throws Exception {
        LiveCapturePanel panel = new LiveCapturePanel(null);
        setField(panel, "configurationName", "config-B");
        setField(panel, "originalBaselineSnapshot", baseline("config-A"));
        invokeUpdateControls(panel);

        JButton rollback = (JButton) getField(panel, "copyRollbackButton");
        assertFalse(rollback.isEnabled());

        setField(panel, "originalBaselineSnapshot", baseline("config-B"));
        invokeUpdateControls(panel);
        assertTrue(rollback.isEnabled());
    }

    private static LiveBaselineSnapshot baseline(String configuration) {
        return new LiveBaselineSnapshot(
                gains(1.0, 1.0, 1.0),
                LiveSessionSummary.empty(),
                LiveCaptureProfile.STANDARD,
                LiveMotionMode.USE_LIVE_VSS,
                "baseline",
                configuration);
    }

    private static TuneSnapshot gains(double p, double i, double d) {
        return new TuneSnapshot(p, i, d, "test", false);
    }

    private static void invokeUpdateControls(LiveCapturePanel panel) throws Exception {
        Method method = LiveCapturePanel.class.getDeclaredMethod("updateControls");
        method.setAccessible(true);
        method.invoke(panel);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
