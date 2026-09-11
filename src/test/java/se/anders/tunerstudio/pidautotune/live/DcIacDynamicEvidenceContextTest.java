package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacDynamicEvidenceContextTest {
    @Test
    public void fanAdderStepIsContextOnlyEvenWhenM3IsQuantitative() {
        DcIacEvent event = event(true, false);
        DcIacDynamicEvidence evidence = new DcIacDynamicEvidence(measurement(true), event, 0.0, 100.0);
        assertTrue(evidence.isFanIdleAdderStep());
        assertEquals(DcIacDynamicEvidence.Tier.CONTEXT_ONLY, evidence.getTier());
        assertFalse(evidence.isSafeLocalStep());
        assertTrue(evidence.getCombinedEvidenceFlags().contains("FAN1_IDLE_ADDER_STEP"));
    }

    @Test
    public void pvOutsideTravelIsContextOnlyEvenWhenM3IsQuantitative() {
        DcIacEvent event = event(false, true);
        DcIacDynamicEvidence evidence = new DcIacDynamicEvidence(measurement(true), event, 0.0, 100.0);
        assertTrue(evidence.isProcessValueOutsideTravel());
        assertEquals(DcIacDynamicEvidence.Tier.CONTEXT_ONLY, evidence.getTier());
        assertFalse(evidence.isSafeLocalStep());
        assertTrue(evidence.getCombinedEvidenceFlags().contains("PV_OUTSIDE_TRAVEL"));
    }

    private static DcIacMeasurement measurement(boolean quantitative) {
        return new DcIacMeasurement(1, DcIacEvent.Type.OPENING_STEP, DcIacMeasurement.Validity.VALID,
                "STEP_MEASURED", "test", 100, 2.0, 18.0, 22.0, 50.0,
                0.2, 0.1, 18.0, 22.0, 0.02, 0.08, 0.30, 10.0,
                0.05, 0.2, 4.0, 55.0, 0, 10.0, 1.0, 2.0, 0.1,
                0.1, 1.0, 0.0, 0.2, 4.0, 20.0, 0.2, 0.0, 0.0, 0.0,
                35.0, 60.0, false, quantitative, false, "");
    }

    private static DcIacEvent event(boolean fanTransition, boolean outsideTravel) {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (int i = 0; i <= 20; i++) {
            double t = i * 0.1;
            double fan = fanTransition && i >= 10 ? 1.0 : 0.0;
            double actual = outsideTravel && i == 12 ? -1.0 : 18.0 + Math.min(4.0, i * 0.25);
            samples.add(sample(t, i < 10 ? 18.0 : 22.0, actual, fan));
        }
        return new DcIacEvent(1, DcIacEvent.Type.OPENING_STEP, DcIacEvent.Status.ACCEPTED,
                1.0, 2.0, 18.0, 22.0, "STEP_ACCEPTED", "test", samples);
    }

    private static ProfileLiveSample sample(double t, double target, double actual, double fan) {
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("dcIdleTarget", Double.valueOf(target));
        values.put("idlePositionSensor", Double.valueOf(actual));
        values.put("fan1On", Double.valueOf(fan));
        values.put("RPMValue", Double.valueOf(900.0));
        values.put("isIdling", Double.valueOf(1.0));
        long nanos = (long) (t * 1000000000.0);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String key : values.keySet()) updates.put(key, Long.valueOf(nanos));
        return new ProfileLiveSample(t, nanos, values, updates);
    }
}
