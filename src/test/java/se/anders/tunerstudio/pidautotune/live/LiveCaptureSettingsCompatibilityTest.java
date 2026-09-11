package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class LiveCaptureSettingsCompatibilityTest {
    @Test
    public void differentCustomSettlingBandIsIncompatibleEvenWhenBothProfilesAreCustom() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();
        LiveCaptureSettings baselineSettings = settings(60.0, 20.0, 10.0);
        LiveCaptureSettings currentSettings = settings(45.0, 20.0, 10.0);
        LiveBaselineSnapshot baseline = baseline(baselineSettings);

        LiveComparisonResult result = engine.compare(
                baseline,
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A",
                currentSettings);

        assertEquals(LiveComparisonResult.INCOMPATIBLE, result.getStatus());
        assertTrue(result.getSummary().contains("settling band differs"));
    }

    @Test
    public void differentCustomObservationTimeIsAlsoIncompatible() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();
        LiveBaselineSnapshot baseline = baseline(settings(60.0, 20.0, 10.0));

        LiveComparisonResult result = engine.compare(
                baseline,
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A",
                settings(60.0, 20.0, 14.0));

        assertEquals(LiveComparisonResult.INCOMPATIBLE, result.getStatus());
        assertTrue(result.getSummary().contains("stable observation time differs"));
    }

    @Test
    public void identicalCustomCaptureContractContinuesToNormalReadiness() {
        LiveExperimentalRecommendationEngine engine = new LiveExperimentalRecommendationEngine();
        LiveCaptureSettings settings = settings(60.0, 20.0, 10.0);

        LiveComparisonResult result = engine.compare(
                baseline(settings),
                LiveSessionSummary.empty(),
                gains(2.0, 1.0, 1.0),
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A",
                settings);

        assertEquals(LiveComparisonResult.WAITING, result.getStatus());
        assertTrue(settings.comparisonDifferences(settings).isEmpty());
    }

    @Test
    public void archivedIterationPreservesEffectiveCaptureSettingsInBaseline() {
        LiveCaptureSettings settings = settings(55.0, 25.0, 12.0);
        LiveIterationRecord iteration = new LiveIterationRecord(
                2,
                1000L,
                2000L,
                gains(1.0, 2.0, 3.0),
                LiveSessionSummary.empty(),
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                "config-A",
                settings,
                null,
                null,
                null,
                "Inconclusive",
                "test",
                Collections.<LiveAttempt>emptyList(),
                Collections.<LiveSessionRecord>emptyList());

        assertSame(settings, iteration.getCaptureSettings());
        assertSame(settings, iteration.asBaseline("previous").getCaptureSettings());
    }

    private static LiveBaselineSnapshot baseline(LiveCaptureSettings settings) {
        return new LiveBaselineSnapshot(
                gains(1.0, 1.0, 1.0),
                LiveSessionSummary.empty(),
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                "baseline",
                "config-A",
                settings);
    }

    private static LiveCaptureSettings settings(double settlingBand,
                                                double exitHysteresis,
                                                double observationSeconds) {
        return new LiveCaptureSettings(
                1.0,
                1.0,
                300.0,
                50.0,
                -20.0,
                20.0,
                70.0,
                100.0,
                1400.0,
                2200.0,
                100.0,
                settlingBand,
                exitHysteresis,
                observationSeconds,
                30.0,
                LiveCaptureProfile.CUSTOM,
                LiveMotionMode.USE_LIVE_VSS,
                false);
    }

    private static TuneSnapshot gains(double p, double i, double d) {
        return new TuneSnapshot(p, i, d, "test", false);
    }
}
