package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacEventExtractorTest {
    private static final DcIacEventSettings SETTINGS = new DcIacEventSettings(0.0, 100.0);

    @Test
    public void acceptsStableHold() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 2.4, 0.02, 55.0, 55.3, 78.4, 12.05, 4.0, false);

        List<DcIacEvent> events = extractor.getEvents();
        assertEquals(1, count(events, DcIacEvent.Type.STABLE_HOLD, DcIacEvent.Status.ACCEPTED));
    }

    @Test
    public void acceptsOpeningAndClosingSteps() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 0.02, 40.0, 40.1, 70.0, 12.1, 1.0, false);
        feed(extractor, 0.82, 3.1, 0.02, 55.0, 54.8, 82.0, 12.1, 1.0, false);
        feed(extractor, 3.12, 3.9, 0.02, 55.0, 55.0, 80.0, 12.1, 1.0, false);
        feed(extractor, 3.92, 6.2, 0.02, 35.0, 35.2, 65.0, 12.1, 1.0, false);

        List<DcIacEvent> events = extractor.getEvents();
        assertEquals(1, count(events, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED));
        assertEquals(1, count(events, DcIacEvent.Type.CLOSING_STEP, DcIacEvent.Status.ACCEPTED));
    }

    @Test
    public void rejectsSingleSampleTargetGlitch() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 0.02, 40.0, 40.1, 70.0, 12.1, 2.0, false);
        extractor.process(sample(0.82, 0.0, 40.1, 70.0, 12.1, 2.0), SETTINGS, false);
        extractor.process(sample(0.84, 40.0, 40.1, 70.0, 12.1, 2.0), SETTINGS, false);

        List<DcIacEvent> events = extractor.getEvents();
        assertEquals(1, events.size());
        assertEquals(DcIacEvent.Status.REJECTED, events.get(0).getStatus());
        assertEquals("TARGET_NOT_PERSISTENT", events.get(0).getResultCode());
        assertFalse(hasAcceptedStep(events));
    }

    @Test
    public void lowVoltageCannotBecomeEvidence() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 3.0, 0.02, 55.0, 20.0, 0.0, 0.01, 3.0, false);
        assertTrue(extractor.getEvents().isEmpty());
        assertTrue(extractor.getReadiness().contains("below 10.0 V"));
    }

    @Test
    public void rejectsResetInsideStepWindow() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 0.02, 40.0, 40.1, 70.0, 12.1, 5.0, false);
        feed(extractor, 0.82, 1.2, 0.02, 55.0, 54.0, 85.0, 12.1, 5.0, false);
        extractor.process(sample(1.22, 55.0, 54.2, 84.0, 12.1, 6.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("PID_RESET", event.getResultCode());
    }

    @Test
    public void rejectsSaturatedDutyInsideStepWindow() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 0.02, 40.0, 40.1, 70.0, 12.1, 7.0, false);
        feed(extractor, 0.82, 1.2, 0.02, 55.0, 50.0, 85.0, 12.1, 7.0, false);
        extractor.process(sample(1.22, 55.0, 51.0, 147.0, 12.1, 7.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("DUTY_SATURATED", event.getResultCode());
    }

    @Test
    public void exposedSharedMotorPauseBlocksEvidence() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 2.5, 0.02, 55.0, 55.0, 80.0, 12.1, 8.0, true);
        assertTrue(extractor.getEvents().isEmpty());
        assertTrue(extractor.getReadiness().contains("shared motor-pause"));
    }

    private static void feed(
            DcIacEventExtractor extractor,
            double start,
            double end,
            double step,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            boolean paused) {
        for (double time = start; time <= end + 0.000001; time += step) {
            extractor.process(sample(time, target, actual, duty, vbatt, reset), SETTINGS, paused);
        }
    }

    private static ProfileLiveSample sample(
            double time,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset) {
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("dcIdleTarget", target);
        values.put("idlePositionSensor", actual);
        values.put("dcIdleDutyCycle", duty);
        values.put("dcIdlePositionStatus_pTerm", (target - actual) * 5.0);
        values.put("dcIdlePositionStatus_iTerm", 0.0);
        values.put("dcIdlePositionStatus_dTerm", 0.0);
        values.put("dcIdlePositionStatus_output", (target - actual) * 5.0);
        values.put("dcIdlePositionStatus_error", target - actual);
        values.put("dcIdlePositionStatus_resetCounter", reset);
        values.put("dcIdleFaultCode", 0.0);
        values.put("VBatt", vbatt);

        long captured = Math.max(1L, (long) (time * 1000000000.0) + 1L);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String name : values.keySet()) updates.put(name, Long.valueOf(captured));
        return new ProfileLiveSample(time, captured, values, updates);
    }

    private static int count(List<DcIacEvent> events, DcIacEvent.Type type, DcIacEvent.Status status) {
        int count = 0;
        for (DcIacEvent event : events) {
            if (event.getType() == type && event.getStatus() == status) count++;
        }
        return count;
    }

    private static boolean hasAcceptedStep(List<DcIacEvent> events) {
        for (DcIacEvent event : events) {
            if (event.isAccepted() && event.getType() != DcIacEvent.Type.STABLE_HOLD) return true;
        }
        return false;
    }

    private static DcIacEvent last(List<DcIacEvent> events) {
        assertFalse(events.isEmpty());
        return events.get(events.size() - 1);
    }
}
