package se.anders.tunerstudio.pidautotune.live;

import java.util.EnumMap;
import java.util.Map;

/** One coalesced engineering-value sample from the live TunerStudio subscriptions. */
public final class LiveSample {
    private final double timeSeconds;
    private final Map<LiveChannel, Double> values;
    private final Map<LiveChannel, Long> updateNanos;

    public LiveSample(double timeSeconds, Map<LiveChannel, Double> values, Map<LiveChannel, Long> updateNanos) {
        this.timeSeconds = timeSeconds;
        this.values = new EnumMap<LiveChannel, Double>(values);
        this.updateNanos = new EnumMap<LiveChannel, Long>(updateNanos);
    }

    public double getTimeSeconds() { return timeSeconds; }

    public double get(LiveChannel channel) {
        Double value = values.get(channel);
        return value == null ? Double.NaN : value.doubleValue();
    }

    public boolean has(LiveChannel channel) {
        return isFinite(get(channel));
    }

    public boolean isFresh(LiveChannel channel, long nowNanos, double maximumAgeSeconds) {
        Long updated = updateNanos.get(channel);
        return updated != null && nowNanos - updated.longValue() <= (long) (maximumAgeSeconds * 1000000000.0);
    }

    public double getCorrection() {
        double base = get(LiveChannel.BASE_IDLE_POSITION);
        double current = get(LiveChannel.CURRENT_IDLE_POSITION);
        return isFinite(base) && isFinite(current) ? current - base : Double.NaN;
    }

    public static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
