package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;

public final class DcIacOperatingContextTest {
    @Test public void separatesEngineOffAndRunningIdle() {
        assertEquals(DcIacOperatingContext.ENGINE_OFF, DcIacOperatingContext.classify(samples(0.0, 0.0)));
        assertEquals(DcIacOperatingContext.RUNNING_IDLE, DcIacOperatingContext.classify(samples(900.0, 1.0)));
        assertEquals(DcIacOperatingContext.RUNNING_OTHER, DcIacOperatingContext.classify(samples(1500.0, 0.0)));
    }
    private static List<ProfileLiveSample> samples(double rpm, double idling) {
        List<ProfileLiveSample> result = new ArrayList<ProfileLiveSample>();
        for (int i = 0; i < 10; i++) {
            Map<String, Double> values = new HashMap<String, Double>();
            values.put("RPMValue", rpm); values.put("isIdling", idling); values.put("coolant", 80.0);
            long nanos = (i + 1L) * 20000000L;
            Map<String, Long> updates = new HashMap<String, Long>();
            for (String key : values.keySet()) updates.put(key, Long.valueOf(nanos));
            result.add(new ProfileLiveSample(i * 0.02, nanos, values, updates));
        }
        return result;
    }
}
