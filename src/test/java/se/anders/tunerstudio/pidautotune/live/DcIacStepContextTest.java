package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacStepContextTest {
    @Test
    public void fanTransitionIsLoadDisturbanceSource() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        samples.add(sample(0.0, 18.0, 18.0, 0.0));
        samples.add(sample(0.1, 24.0, 21.0, 1.0));
        samples.add(sample(0.2, 24.0, 24.2, 1.0));
        DcIacStepContext context = DcIacStepContext.analyze(samples, 0.0, 100.0);
        assertEquals(DcIacStepContext.Source.FAN1_IDLE_ADDER, context.getSource());
        assertTrue(context.isFanIdleAdderStep());
        assertTrue(context.evidenceFlags().contains("FAN1_IDLE_ADDER_STEP"));
    }

    @Test
    public void stableKnownFanStateLeavesCommandSource() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        samples.add(sample(0.0, 18.0, 18.0, 0.0));
        samples.add(sample(0.1, 22.0, 20.0, 0.0));
        samples.add(sample(0.2, 22.0, 22.0, 0.0));
        DcIacStepContext context = DcIacStepContext.analyze(samples, 0.0, 100.0);
        assertEquals(DcIacStepContext.Source.COMMAND, context.getSource());
        assertFalse(context.isFanIdleAdderStep());
    }

    @Test
    public void processValueBeyondTravelToleranceIsFlagged() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        samples.add(sample(0.0, 2.0, 2.0, 0.0));
        samples.add(sample(0.1, 2.0, -0.8, 0.0));
        DcIacStepContext context = DcIacStepContext.analyze(samples, 0.0, 100.0);
        assertTrue(context.isProcessValueOutsideTravel());
        assertTrue(context.evidenceFlags().contains("PV_OUTSIDE_TRAVEL"));
    }

    @Test
    public void smallCalibrationEdgeNoiseDoesNotTripTravelGuard() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        samples.add(sample(0.0, 1.0, -0.2, 0.0));
        samples.add(sample(0.1, 1.0, 0.1, 0.0));
        DcIacStepContext context = DcIacStepContext.analyze(samples, 0.0, 100.0);
        assertFalse(context.isProcessValueOutsideTravel());
    }

    private static ProfileLiveSample sample(double t, double target, double actual, double fan) {
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("dcIdleTarget", Double.valueOf(target));
        values.put("idlePositionSensor", Double.valueOf(actual));
        values.put("fan1On", Double.valueOf(fan));
        long nanos = (long) (t * 1000000000.0);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String key : values.keySet()) updates.put(key, Long.valueOf(nanos));
        return new ProfileLiveSample(t, nanos, values, updates);
    }
}
