package se.anders.tunerstudio.pidautotune.live;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Immutable snapshot of profile-driven live output-channel values. */
public final class ProfileLiveSample {
    private final double timeSeconds;
    private final long capturedNanos;
    private final Map<String, Double> values;
    private final Map<String, Long> updateNanos;

    public ProfileLiveSample(
            double timeSeconds,
            long capturedNanos,
            Map<String, Double> values,
            Map<String, Long> updateNanos) {
        this.timeSeconds = timeSeconds;
        this.capturedNanos = capturedNanos;
        this.values = Collections.unmodifiableMap(new HashMap<String, Double>(values));
        this.updateNanos = Collections.unmodifiableMap(new HashMap<String, Long>(updateNanos));
    }

    public double getTimeSeconds() { return timeSeconds; }

    public boolean has(String name) {
        return values.containsKey(name);
    }

    public double get(String name) {
        Double value = values.get(name);
        return value == null ? Double.NaN : value.doubleValue();
    }

    public double getAgeSeconds(String name) {
        Long updated = updateNanos.get(name);
        if (updated == null) return Double.POSITIVE_INFINITY;
        return Math.max(0.0, (capturedNanos - updated.longValue()) / 1000000000.0);
    }

    /**
     * Returns the channel's last source-update time on the same monotonic timebase
     * as getTimeSeconds(). This lets measurement code distinguish real ECU/TunerStudio
     * updates from repeated scheduler snapshots of an unchanged value.
     */
    public double getUpdateTimeSeconds(String name) {
        Long updated = updateNanos.get(name);
        if (updated == null) return Double.NaN;
        return timeSeconds - (capturedNanos - updated.longValue()) / 1000000000.0;
    }
}
