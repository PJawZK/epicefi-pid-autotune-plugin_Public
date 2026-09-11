package se.anders.tunerstudio.pidautotune.live;

import java.util.List;

/**
 * Coarse operating-context classification for DC-IAC evidence.
 *
 * The inner position loop is mechanically independent of the outer idle RPM controller, but
 * actuator load can differ materially between an engine-off bench hold and a running engine.
 * Evidence from those conditions must therefore not be pooled blindly.
 */
public enum DcIacOperatingContext {
    ENGINE_OFF,
    RUNNING_IDLE,
    RUNNING_OTHER,
    UNKNOWN;

    private static final String RPM = "RPMValue";
    private static final String IDLING = "isIdling";

    public static DcIacOperatingContext classify(List<ProfileLiveSample> samples) {
        if (samples == null || samples.isEmpty()) return UNKNOWN;
        double rpmSum = 0.0;
        int rpmCount = 0;
        int idlingTrue = 0;
        int idlingCount = 0;
        for (ProfileLiveSample sample : samples) {
            if (sample == null) continue;
            double rpm = sample.get(RPM);
            if (finite(rpm)) {
                rpmSum += rpm;
                rpmCount++;
            }
            double idling = sample.get(IDLING);
            if (finite(idling)) {
                idlingCount++;
                if (idling > 0.5) idlingTrue++;
            }
        }
        if (rpmCount == 0) return UNKNOWN;
        double meanRpm = rpmSum / rpmCount;
        if (meanRpm < 150.0) return ENGINE_OFF;
        if (meanRpm >= 300.0) {
            if (idlingCount > 0 && idlingTrue >= Math.max(1, idlingCount / 2)) return RUNNING_IDLE;
            return RUNNING_OTHER;
        }
        return UNKNOWN;
    }

    public static double meanRpm(List<ProfileLiveSample> samples) {
        return mean(samples, RPM);
    }

    public static double meanCoolant(List<ProfileLiveSample> samples) {
        return mean(samples, "coolant");
    }

    public static double idlingFraction(List<ProfileLiveSample> samples) {
        if (samples == null || samples.isEmpty()) return Double.NaN;
        int count = 0;
        int active = 0;
        for (ProfileLiveSample sample : samples) {
            double value = sample == null ? Double.NaN : sample.get(IDLING);
            if (!finite(value)) continue;
            count++;
            if (value > 0.5) active++;
        }
        return count == 0 ? Double.NaN : active * 1.0 / count;
    }

    public boolean isRunning() {
        return this == RUNNING_IDLE || this == RUNNING_OTHER;
    }

    public String displayName() {
        switch (this) {
            case ENGINE_OFF: return "Engine off";
            case RUNNING_IDLE: return "Running idle";
            case RUNNING_OTHER: return "Running other";
            default: return "Unknown";
        }
    }

    private static double mean(List<ProfileLiveSample> samples, String name) {
        if (samples == null || samples.isEmpty()) return Double.NaN;
        double sum = 0.0;
        int count = 0;
        for (ProfileLiveSample sample : samples) {
            double value = sample == null ? Double.NaN : sample.get(name);
            if (!finite(value)) continue;
            sum += value;
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
