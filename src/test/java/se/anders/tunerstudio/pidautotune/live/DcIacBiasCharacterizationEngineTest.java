package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class DcIacBiasCharacterizationEngineTest {
    private static final DcIacEventSettings EVENT_SETTINGS = new DcIacEventSettings(0.0, 100.0);
    private static final DcIacBiasCharacterizationSettings SETTINGS = new DcIacBiasCharacterizationSettings();
    private static final DcIacBiasSettingsAccess.Snapshot BIAS = new DcIacBiasSettingsAccess.Snapshot(
            new double[] { 18.0, 30.0, 45.0 }, new double[] { 35.0, 35.0, 35.0 }, -30.0, 30.0);

    @Test
    public void waitsPastFirstHoldUntilIntegratorSettles() {
        DcIacBiasCharacterizationEngine engine = new DcIacBiasCharacterizationEngine();
        DcIacBiasEvidence accepted = null;
        for (int i = 0; i <= 650; i++) {
            double t = i * 0.02;
            double iTerm = t < 6.0 ? -5.0 + 0.5 * t : -2.0;
            DcIacBiasEvidence row = engine.process(sample(t, 25.0, 25.0, 35.0, iTerm, 13.6, 1.0),
                    EVENT_SETTINGS, SETTINGS, BIAS, false);
            if (row != null) accepted = row;
        }
        assertTrue(accepted != null);
        // The first normal M2 hold would exist at ~2 s. M4A must continue waiting until
        // the rolling I-term trend is essentially settled before producing bias evidence.
        assertTrue(accepted.getEndSeconds() >= 9.0);
        assertEquals(-2.0, accepted.getCorrection(), 0.15);
        assertEquals(35.0, accepted.getObservedFeedForward(), 0.05);
        assertEquals(33.0, accepted.getRequiredBias(), 0.15);
    }

    @Test
    public void resetDiscontinuityRestartsRollingWindow() {
        DcIacBiasCharacterizationEngine engine = new DcIacBiasCharacterizationEngine();
        DcIacBiasEvidence accepted = null;
        for (int i = 0; i <= 600; i++) {
            double t = i * 0.02;
            double reset = t < 3.0 ? 2.0 : 1.0;
            DcIacBiasEvidence row = engine.process(sample(t, 25.0, 25.0, 35.0, -2.0, 13.6, reset),
                    EVENT_SETTINGS, SETTINGS, BIAS, false);
            if (row != null) { accepted = row; break; }
        }
        assertTrue(accepted != null);
        assertTrue(accepted.getStartSeconds() >= 3.0 - 0.05);
    }

    @Test
    public void runtimeFeedForwardMismatchIsRetainedAndGraded() {
        DcIacBiasCharacterizationEngine engine = new DcIacBiasCharacterizationEngine();
        DcIacBiasEvidence accepted = null;
        for (int i = 0; i <= 400; i++) {
            double t = i * 0.02;
            ProfileLiveSample sample = sample(t, 25.0, 25.0, 37.0, -2.0, 13.6, 1.0);
            DcIacBiasEvidence row = engine.process(sample, EVENT_SETTINGS, SETTINGS, BIAS, false);
            if (row != null) accepted = row;
        }
        assertNotNull(accepted);
        assertEquals(DcIacBiasEvidence.QUALITY_OK, accepted.getQuality());
        assertTrue(accepted.isRecommendationEligible());
        assertTrue(accepted.getQualityDetail().contains("feed-forward mapping mismatch"));
    }

    @Test
    public void integralNearConfiguredLimitIsRetainedAndGraded() {
        DcIacBiasCharacterizationEngine engine = new DcIacBiasCharacterizationEngine();
        DcIacBiasEvidence accepted = null;
        for (int i = 0; i <= 400; i++) {
            double t = i * 0.02;
            DcIacBiasEvidence row = engine.process(sample(t, 25.0, 25.0, 35.0, 29.0, 13.6, 1.0),
                    EVENT_SETTINGS, SETTINGS, BIAS, false);
            if (row != null) accepted = row;
        }
        assertNotNull(accepted);
        assertEquals(DcIacBiasEvidence.QUALITY_OK, accepted.getQuality());
        assertTrue(accepted.isRecommendationEligible());
        assertTrue(accepted.getQualityDetail().contains("I term near configured limit"));
    }

    private static ProfileLiveSample sample(
            double time,
            double target,
            double actual,
            double feedForward,
            double iTerm,
            double vbatt,
            double reset) {
        double error = target - actual;
        double pTerm = error * 5.0;
        double pidOutput = pTerm + iTerm;
        double duty = feedForward + pidOutput;

        Map<String, Double> values = new HashMap<String, Double>();
        values.put("dcIdleTarget", target);
        values.put("idlePositionSensor", actual);
        values.put("dcIdleDutyCycle", duty);
        values.put("dcIdlePositionStatus_output", pidOutput);
        values.put("dcIdlePositionStatus_iTerm", iTerm);
        values.put("dcIdlePositionStatus_resetCounter", reset);
        values.put("dcIdleFaultCode", 0.0);
        values.put("VBatt", vbatt);

        long captured = Math.max(1L, (long) (time * 1000000000.0) + 1L);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String name : values.keySet()) updates.put(name, Long.valueOf(captured));
        return new ProfileLiveSample(time, captured, values, updates);
    }
}
