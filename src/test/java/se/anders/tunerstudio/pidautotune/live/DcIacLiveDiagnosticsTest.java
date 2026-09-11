package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacLiveDiagnosticsTest {
    @Test
    public void validatesTargetHandoffAndILawWhileStillIntegrating() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (int i = 0; i <= 100; i++) {
            double t = i * 0.02;
            double target = 18.0;
            double actual = 17.9;
            double iTerm = 2.0 + 0.05 * t;
            samples.add(sample(t, target, target + 0.002, actual, iTerm, 0.0));
        }
        DcIacLiveDiagnostics.Result result = DcIacLiveDiagnostics.analyze(samples, 0.5);
        assertTrue(result.isHandoffAvailable());
        assertTrue(result.isHandoffOk());
        assertTrue(Math.abs(result.getMedianHandoffAbsError() - 0.002) < 0.001);
        assertTrue(Math.abs(result.getExpectedITermSlope() - 0.05) < 0.005);
        assertTrue(Math.abs(result.getMeasuredITermSlope() - 0.05) < 0.005);
        assertTrue(result.shouldKeepHolding());
        assertTrue(result.isFan1Available());
        assertFalse(result.isFan1On());
    }

    @Test
    public void quietSettledWindowDoesNotAskToKeepHolding() {
        List<ProfileLiveSample> samples = new ArrayList<ProfileLiveSample>();
        for (int i = 0; i <= 100; i++) {
            double t = i * 0.02;
            samples.add(sample(t, 30.0, 30.001, 29.99, -4.0, 1.0));
        }
        DcIacLiveDiagnostics.Result result = DcIacLiveDiagnostics.analyze(samples, 0.5);
        assertTrue(result.isHandoffOk());
        assertFalse(result.shouldKeepHolding());
        assertTrue(result.isFan1On());
    }

    private static ProfileLiveSample sample(double t, double target, double outer, double actual, double iTerm, double fan) {
        Map<String, Double> values = new HashMap<String, Double>();
        values.put("dcIdleTarget", Double.valueOf(target));
        values.put("currentIdlePosition", Double.valueOf(outer));
        values.put("idlePositionSensor", Double.valueOf(actual));
        values.put("dcIdlePositionStatus_iTerm", Double.valueOf(iTerm));
        values.put("fan1On", Double.valueOf(fan));
        long nanos = (long) (t * 1000000000.0);
        Map<String, Long> updates = new HashMap<String, Long>();
        for (String key : values.keySet()) updates.put(key, Long.valueOf(nanos));
        return new ProfileLiveSample(t, nanos, values, updates);
    }
}
