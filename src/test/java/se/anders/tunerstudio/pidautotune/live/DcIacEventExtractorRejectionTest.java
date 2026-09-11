package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacEventExtractorRejectionTest {
    private static final DcIacEventSettings SETTINGS = new DcIacEventSettings(0.0, 100.0);

    @Test
    public void rejectsTargetChangeInsideResponseWindow() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.1, 70.0, 12.1, 1.0, 0.0);
        feed(extractor, 0.82, 1.20, 55.0, 54.0, 85.0, 12.1, 1.0, 0.0);
        extractor.process(sample(1.22, 60.0, 55.0, 88.0, 12.1, 1.0, 0.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("TARGET_CHANGED", event.getResultCode());
    }

    @Test
    public void rejectsFaultInsideResponseWindow() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.1, 70.0, 12.1, 2.0, 0.0);
        feed(extractor, 0.82, 1.20, 55.0, 54.0, 85.0, 12.1, 2.0, 0.0);
        extractor.process(sample(1.22, 55.0, 54.2, 84.0, 12.1, 2.0, 3.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("DC_IAC_FAULT", event.getResultCode());
    }

    @Test
    public void persistentStepToTravelLimitIsRejected() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.1, 70.0, 12.1, 3.0, 0.0);
        feed(extractor, 0.82, 1.10, 100.0, 60.0, 89.0, 12.1, 3.0, 0.0);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("TARGET_NEAR_LIMIT", event.getResultCode());
    }

    @Test
    public void unstableVoltageRejectsStep() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.1, 70.0, 12.0, 4.0, 0.0);
        feed(extractor, 0.82, 1.20, 55.0, 54.0, 85.0, 12.0, 4.0, 0.0);
        extractor.process(sample(1.22, 55.0, 54.2, 84.0, 13.0, 4.0, 0.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("VOLTAGE_UNSTABLE", event.getResultCode());
    }

    @Test
    public void nonzeroFaultBlocksStableHoldEvidence() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 2.5, 55.0, 55.0, 80.0, 12.1, 5.0, 2.0);
        assertTrue(extractor.getEvents().isEmpty());
        assertTrue(extractor.getReadiness().contains("dcIdleFaultCode"));
    }

    @Test
    public void observedPhysicalMotorLimitRejectsAtNinetyPercent() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.0, 70.0, 12.4, 6.0, 0.0);
        feed(extractor, 0.82, 1.10, 45.0, 42.0, 90.0, 12.4, 6.0, 0.0);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("DUTY_SATURATED", event.getResultCode());
        assertEquals(90.0, SETTINGS.getSaturationDutyPercent(), 0.0001);
    }

    @Test
    public void backwardResetCounterChangeRejectsResponseContinuity() {
        DcIacEventExtractor extractor = new DcIacEventExtractor();
        feed(extractor, 0.0, 0.8, 40.0, 40.0, 70.0, 12.4, 3.0, 0.0);
        feed(extractor, 0.82, 1.18, 45.0, 43.0, 80.0, 12.4, 3.0, 0.0);
        extractor.process(sample(1.20, 45.0, 43.5, 78.0, 12.4, 1.0, 0.0), SETTINGS, false);

        DcIacEvent event = last(extractor.getEvents());
        assertEquals(DcIacEvent.Status.REJECTED, event.getStatus());
        assertEquals("PID_RESET", event.getResultCode());
    }

    private static void feed(
            DcIacEventExtractor extractor,
            double start,
            double end,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            double fault) {
        for (double time = start; time <= end + 0.000001; time += 0.02) {
            extractor.process(sample(time, target, actual, duty, vbatt, reset, fault), SETTINGS, false);
        }
    }

    private static ProfileLiveSample sample(
            double time,
            double target,
            double actual,
            double duty,
            double vbatt,
            double reset,
            double fault) {
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
        values.put("dcIdleFaultCode", fault);
        values.put("VBatt", vbatt);

        long captured = Math.max(1L, (long) (time * 1000000000.0) + 1L);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String name : values.keySet()) updates.put(name, Long.valueOf(captured));
        return new ProfileLiveSample(time, captured, values, updates);
    }

    private static DcIacEvent last(List<DcIacEvent> events) {
        assertFalse(events.isEmpty());
        return events.get(events.size() - 1);
    }
}
