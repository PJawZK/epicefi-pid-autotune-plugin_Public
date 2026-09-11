package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacMeasurementEngineTest {
    private static final DcIacMeasurementSettings SETTINGS = new DcIacMeasurementSettings();
    private static final DcIacMeasurementSettings KNOWN_PID = new DcIacMeasurementSettings(5.0, 0.5, 0.01);
    private final DcIacMeasurementEngine engine = new DcIacMeasurementEngine();

    @Test
    public void measuresCleanOpeningStep() {
        DcIacMeasurement measurement = engine.measure(stepEvent(1, true, false), SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals("STEP_MEASURED", measurement.getResultCode());
        assertTrue(measurement.getResponseDelaySeconds() >= 0.0);
        assertTrue(measurement.getRiseOrFallSeconds() > 0.0);
        assertTrue(measurement.getSettlingSeconds() > 0.0);
        assertTrue(Math.abs(measurement.getSteadyStateError()) < 0.2);
        assertFalse(measurement.isDynamicQuantitative());
        assertTrue(measurement.getEvidenceFlags().contains("SATURATION_PREDICTION_UNAVAILABLE"));
    }

    @Test
    public void measuresCleanClosingStep() {
        DcIacMeasurement measurement = engine.measure(stepEvent(2, false, false), SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals(DcIacEvent.Type.CLOSING_STEP, measurement.getEventType());
        assertTrue(measurement.getRiseOrFallSeconds() > 0.0);
        assertTrue(measurement.getPeakAbsoluteDuty() > 0.0);
    }

    @Test
    public void rejectsUnsettledPreStepProcessValue() {
        DcIacMeasurement measurement = engine.measure(stepEvent(3, true, true), SETTINGS);
        assertFalse(measurement.isValid());
        assertEquals("PRESTEP_NOT_SETTLED", measurement.getResultCode());
        assertTrue(measurement.getPreStepActualSpan() > SETTINGS.getMaximumPreStepActualSpan());
    }

    @Test
    public void settledStableHoldBecomesBiasEquilibriumCandidate() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.0; t <= 2.0 + 0.0001; t += 0.02) {
            double actual = 50.0 + 0.04 * Math.sin(t * 8.0);
            samples.add(sampleDetailed(t, 50.0, actual, 26.0, 13.6, 1.0, -9.0));
        }
        DcIacEvent event = new DcIacEvent(
                4, DcIacEvent.Type.STABLE_HOLD, DcIacEvent.Status.ACCEPTED,
                0.0, 2.0, 50.0, 50.0, "HOLD_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals("HOLD_EQUILIBRIUM_CANDIDATE", measurement.getResultCode());
        assertTrue(measurement.isBiasEquilibriumCandidate());
        assertTrue(measurement.getActualJitterStdDev() < 0.1);
        assertTrue(measurement.getMeanAbsoluteError() < 0.1);
        assertTrue(Math.abs(measurement.getMeanObservedFeedForward() - 35.0) < 0.25);
        assertTrue(Math.abs(measurement.getMeanITerm() + 9.0) < 0.01);
    }

    @Test
    public void movingITermPreventsBiasEquilibriumCandidate() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.0; t <= 2.0 + 0.0001; t += 0.02) {
            double iTerm = -9.0 + 0.60 * t;
            samples.add(sampleDetailed(t, 32.0, 32.0, 35.0 + iTerm, 13.6, 1.0, iTerm));
        }
        DcIacEvent event = new DcIacEvent(
                5, DcIacEvent.Type.STABLE_HOLD, DcIacEvent.Status.ACCEPTED,
                0.0, 2.0, 32.0, 32.0, "HOLD_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals("HOLD_MEASURED_NOT_EQUILIBRIUM", measurement.getResultCode());
        assertFalse(measurement.isBiasEquilibriumCandidate());
        assertTrue(measurement.getEvidenceFlags().contains("I_TERM_MOVING"));
        assertTrue(measurement.getITermSlopePerSecond() > 0.5);
    }

    @Test
    public void deduplicatesRepeatedSchedulerSnapshots() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        double sourceTime = 0.0;
        for (int i = 0; i <= 100; i++) {
            double captureTime = i * 0.02;
            if ((i & 1) == 0) sourceTime = captureTime;
            samples.add(sampleWithSourceTime(captureTime, sourceTime,
                    50.0, 50.0, 70.0, 13.6, 1.0));
        }
        DcIacEvent event = new DcIacEvent(
                6, DcIacEvent.Type.STABLE_HOLD, DcIacEvent.Status.ACCEPTED,
                0.0, 2.0, 50.0, 50.0, "HOLD_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertTrue(measurement.getSampleCount() >= 49 && measurement.getSampleCount() <= 52);
        assertTrue(measurement.getSampleRateHz() > 24.0 && measurement.getSampleRateHz() < 26.0);
    }

    @Test
    public void fastTransitionIsResolutionLimitedAndNotZero() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.50; t < 1.0 - 0.0001; t += 0.02) {
            samples.add(sample(t, 40.0, 40.0, 60.0, 13.6, 1.0));
        }
        for (double t = 1.0; t <= 3.0 + 0.0001; t += 0.02) {
            double actual = t < 1.02 - 0.0001 ? 40.0 : 55.0;
            samples.add(sample(t, 55.0, actual, 72.0, 13.6, 1.0));
        }
        DcIacEvent event = new DcIacEvent(
                7, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, 40.0, 55.0, "STEP_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals("STEP_MEASURED_TIMING_LIMITED", measurement.getResultCode());
        assertTrue(measurement.getRiseOrFallSeconds() > 0.0);
        assertTrue(measurement.getRiseOrFallSeconds() < 0.02);
        assertFalse(measurement.isDynamicQuantitative());
    }

    @Test
    public void unresolvedTenToNinetyIsExplicitlyTimingUnavailable() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.50; t < 1.0 - 0.0001; t += 0.02) {
            samples.add(sample(t, 40.0, 40.0, 60.0, 13.6, 1.0));
        }
        for (double t = 1.0; t <= 3.0 + 0.0001; t += 0.02) {
            double progress = Math.min(0.40, Math.max(0.0, (t - 1.0) / 0.20 * 0.40));
            double actual = 40.0 + 15.0 * progress;
            samples.add(sample(t, 55.0, actual, 70.0, 13.6, 1.0));
        }
        DcIacEvent event = new DcIacEvent(
                8, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, 40.0, 55.0, "STEP_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertEquals("STEP_MEASURED_TIMING_UNAVAILABLE", measurement.getResultCode());
        assertTrue(Double.isNaN(measurement.getRiseOrFallSeconds()));
    }

    @Test
    public void includesImmediatelyPrecedingDutyUpdateAtTargetBoundary() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.50; t < 0.98 - 0.0001; t += 0.02) {
            samples.add(sample(t, 40.0, 40.0, 20.0, 13.6, 1.0));
        }
        samples.add(sampleWithChannelTimes(1.00, 40.0, 40.0, 90.0, 13.6, 1.0,
                0.98, 0.98, 0.995));
        for (double t = 1.02; t <= 3.0 + 0.0001; t += 0.02) {
            double progress = Math.min(1.0, (t - 1.0) / 0.50);
            samples.add(sampleWithChannelTimes(t, 55.0, 40.0 + 15.0 * progress, 70.0, 13.6, 1.0,
                    t - 0.015, t - 0.010, t - 0.005));
        }
        DcIacEvent event = new DcIacEvent(
                9, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, 40.0, 55.0, "STEP_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, SETTINGS);
        assertTrue(measurement.isValid());
        assertTrue(measurement.getPeakAbsoluteDuty() >= 89.9);
        assertTrue(measurement.getEvidenceFlags().contains("OBSERVED_PHYSICAL_DUTY_LIMIT"));
    }

    @Test
    public void smallResolvedLocalStepCanBeQuantitative() {
        DcIacMeasurement measurement = engine.measure(
                localStepEvent(10, 26.0, 29.0, 28.0, -4.0, 0.40, 1.0, 1.0), KNOWN_PID);
        assertTrue(measurement.isValid());
        assertEquals("STEP_MEASURED", measurement.getResultCode());
        assertTrue(measurement.isDynamicQuantitative());
        assertTrue(Math.abs(measurement.getStepMagnitude() - 3.0) < 0.01);
        assertTrue(Math.abs(measurement.getOperatingCenterTarget() - 27.5) < 0.05);
        assertTrue(Math.abs(measurement.getInitialITerm() + 4.0) < 0.05);
        assertFalse(measurement.isPredictedSaturationRisk());
        assertTrue(measurement.getPredictedCommandEdgeDuty() < 90.0);
    }

    @Test
    public void predictedPDSetpointKickBlocksQuantitativeUse() {
        DcIacMeasurement measurement = engine.measure(
                localStepEvent(11, 26.0, 30.0, 55.0, -2.0, 0.40, 1.0, 1.0), KNOWN_PID);
        assertTrue(measurement.isValid());
        assertTrue(measurement.isPredictedSaturationRisk());
        assertTrue(measurement.getPredictedCommandEdgeDuty() >= 95.0 - 0.1);
        assertFalse(measurement.isDynamicQuantitative());
        assertTrue(measurement.getEvidenceFlags().contains("PREDICTED_COMMAND_EDGE_SATURATION"));
    }

    @Test
    public void largeStepIsContextButNotLocalIdentificationEvidence() {
        DcIacMeasurement measurement = engine.measure(
                localStepEvent(12, 26.0, 32.0, 15.0, -2.0, 0.55, 1.0, 1.0), KNOWN_PID);
        assertTrue(measurement.isValid());
        assertFalse(measurement.isDynamicQuantitative());
        assertTrue(measurement.getEvidenceFlags().contains("STEP_TOO_LARGE_FOR_LOCAL_ID"));
        assertTrue(measurement.getStepMagnitude() > KNOWN_PID.getMaximumQuantitativeStepSize());
    }

    @Test
    public void backwardResetCounterChangeInvalidatesContinuity() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.55; t < 1.0 - 0.0001; t += 0.02) {
            samples.add(sampleDetailed(t, 26.0, 26.0, 28.0, 13.6, 3.0, -4.0));
        }
        for (double t = 1.0; t <= 3.0 + 0.0001; t += 0.02) {
            double progress = Math.min(1.0, (t - 1.0) / 0.40);
            samples.add(sampleDetailed(t, 29.0, 26.0 + 3.0 * progress, 45.0, 13.6, 1.0, -4.0));
        }
        DcIacEvent event = new DcIacEvent(
                13, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, 26.0, 29.0, "STEP_ACCEPTED", "synthetic", samples);
        DcIacMeasurement measurement = engine.measure(event, KNOWN_PID);
        assertFalse(measurement.isValid());
        assertEquals("PID_RESET_DISCONTINUITY", measurement.getResultCode());
    }

    private static DcIacEvent stepEvent(int sequence, boolean opening, boolean unsettledPreStep) {
        double from = opening ? 40.0 : 55.0;
        double to = opening ? 55.0 : 40.0;
        double direction = opening ? 1.0 : -1.0;
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();

        for (double t = 0.55; t < 1.0 - 0.0001; t += 0.02) {
            double actual;
            if (unsettledPreStep) {
                double fraction = (t - 0.55) / 0.45;
                actual = from - direction * 5.0 + direction * 5.0 * fraction;
            } else {
                actual = from + 0.04 * Math.sin(t * 10.0);
            }
            samples.add(sample(t, from, actual, 65.0, 13.6, 2.0));
        }

        for (double t = 1.0; t <= 3.0 + 0.0001; t += 0.02) {
            double progress = Math.min(1.0, (t - 1.0) / 0.75);
            double actual = from + (to - from) * progress;
            if (progress >= 1.0) actual += 0.03 * Math.sin(t * 9.0);
            double duty = opening ? 78.0 - progress * 6.0 : 58.0 - progress * 5.0;
            samples.add(sample(t, to, actual, duty, 13.6, 2.0));
        }

        return new DcIacEvent(
                sequence,
                opening ? DcIacEvent.Type.OPENING_STEP : DcIacEvent.Type.CLOSING_STEP,
                DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, from, to, "STEP_ACCEPTED", "synthetic", samples);
    }

    private static DcIacEvent localStepEvent(
            int sequence,
            double from,
            double to,
            double preDuty,
            double initialI,
            double responseSeconds,
            double resetBefore,
            double resetAfter) {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (double t = 0.55; t < 1.0 - 0.0001; t += 0.02) {
            double actual = from + 0.02 * Math.sin(t * 7.0);
            samples.add(sampleDetailed(t, from, actual, preDuty, 13.6, resetBefore, initialI));
        }
        for (double t = 1.0; t <= 3.0 + 0.0001; t += 0.02) {
            double progress = Math.min(1.0, Math.max(0.0, (t - 1.0) / responseSeconds));
            double actual = from + (to - from) * progress;
            double duty = preDuty + (to > from ? 12.0 : -12.0) * (1.0 - Math.min(1.0, progress));
            samples.add(sampleDetailed(t, to, actual, duty, 13.6, resetAfter, initialI));
        }
        return new DcIacEvent(
                sequence,
                to >= from ? DcIacEvent.Type.OPENING_STEP : DcIacEvent.Type.CLOSING_STEP,
                DcIacEvent.Status.ACCEPTED,
                1.0, 3.0, from, to, "STEP_ACCEPTED", "synthetic", samples);
    }

    private static ProfileLiveSample sample(
            double time,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset) {
        return sampleWithSourceTime(time, time, target, actual, duty, vbatt, reset);
    }

    private static ProfileLiveSample sampleDetailed(
            double time,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            double iTerm) {
        return sampleDetailedWithChannelTimes(time, target, actual, duty, vbatt, reset, iTerm,
                time, time, time);
    }

    private static ProfileLiveSample sampleWithSourceTime(
            double captureTime,
            double sourceTime,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset) {
        return sampleWithChannelTimes(captureTime, target, actual, duty, vbatt, reset,
                sourceTime, sourceTime, sourceTime);
    }

    private static ProfileLiveSample sampleWithChannelTimes(
            double captureTime,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            double targetTime,
            double actualTime,
            double dutyTime) {
        return sampleDetailedWithChannelTimes(captureTime, target, actual, duty, vbatt, reset, 0.0,
                targetTime, actualTime, dutyTime);
    }

    private static ProfileLiveSample sampleDetailedWithChannelTimes(
            double captureTime,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            double iTerm,
            double targetTime,
            double actualTime,
            double dutyTime) {
        Map<String, Double> values = new HashMap<String, Double>();
        double error = target - actual;
        double pTerm = error * 5.0;
        values.put("dcIdleTarget", target);
        values.put("idlePositionSensor", actual);
        values.put("dcIdleDutyCycle", duty);
        values.put("dcIdlePositionStatus_pTerm", pTerm);
        values.put("dcIdlePositionStatus_iTerm", iTerm);
        values.put("dcIdlePositionStatus_dTerm", 0.0);
        values.put("dcIdlePositionStatus_output", pTerm + iTerm);
        values.put("dcIdlePositionStatus_error", error);
        values.put("dcIdlePositionStatus_resetCounter", reset);
        values.put("dcIdleFaultCode", 0.0);
        values.put("VBatt", vbatt);

        long captured = Math.max(1L, (long) (captureTime * 1000000000.0) + 1L);
        Map<String, Long> updates = new HashMap<String, Long>();
        long targetUpdated = Math.max(1L, (long) (targetTime * 1000000000.0) + 1L);
        long actualUpdated = Math.max(1L, (long) (actualTime * 1000000000.0) + 1L);
        long dutyUpdated = Math.max(1L, (long) (dutyTime * 1000000000.0) + 1L);
        for (String name : values.keySet()) updates.put(name, Long.valueOf(actualUpdated));
        updates.put("dcIdleTarget", Long.valueOf(targetUpdated));
        updates.put("idlePositionSensor", Long.valueOf(actualUpdated));
        updates.put("dcIdleDutyCycle", Long.valueOf(dutyUpdated));
        return new ProfileLiveSample(captureTime, captured, values, updates);
    }
}
