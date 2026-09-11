package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Rolling read-only diagnostics derived from the live DC-IAC stream. */
public final class DcIacLiveDiagnostics {
    private static final double HANDOFF_WINDOW_SECONDS = 1.0;
    private static final double I_LAW_WINDOW_SECONDS = 2.0;
    private static final double HANDOFF_OK_MEDIAN_ABS_PERCENT = 0.10;
    private static final double KEEP_HOLDING_I_SLOPE = 0.03;

    private DcIacLiveDiagnostics() { }

    public static Result analyze(List<ProfileLiveSample> samples, double iFactor) {
        if (samples == null || samples.isEmpty()) return Result.empty();
        double end = samples.get(samples.size() - 1).getTimeSeconds();

        List<Double> handoffErrors = new ArrayList<Double>();
        List<TimedValue> iSeries = new ArrayList<TimedValue>();
        double errorSum = 0.0;
        int errorCount = 0;
        double latestFan = Double.NaN;

        for (ProfileLiveSample sample : samples) {
            if (sample == null) continue;
            double age = end - sample.getTimeSeconds();
            if (age <= HANDOFF_WINDOW_SECONDS) {
                double outer = sample.get("currentIdlePosition");
                double target = sample.get("dcIdleTarget");
                if (finite(outer) && finite(target)) handoffErrors.add(Double.valueOf(Math.abs(outer - target)));
            }
            if (age <= I_LAW_WINDOW_SECONDS) {
                double target = sample.get("dcIdleTarget");
                double actual = sample.get("idlePositionSensor");
                if (finite(target) && finite(actual)) {
                    errorSum += target - actual;
                    errorCount++;
                }
                double i = sample.get("dcIdlePositionStatus_iTerm");
                double update = sample.getUpdateTimeSeconds("dcIdlePositionStatus_iTerm");
                if (finite(i) && finite(update)) addDistinct(iSeries, update, i);
            }
            double fan = sample.get("fan1On");
            if (finite(fan)) latestFan = fan;
        }

        double medianHandoff = median(handoffErrors);
        double meanError = errorCount == 0 ? Double.NaN : errorSum / errorCount;
        double expectedISlope = finite(iFactor) && finite(meanError) ? iFactor * meanError : Double.NaN;
        double measuredISlope = slope(iSeries);
        boolean keepHolding = (finite(expectedISlope) && Math.abs(expectedISlope) > KEEP_HOLDING_I_SLOPE)
                || (finite(measuredISlope) && Math.abs(measuredISlope) > KEEP_HOLDING_I_SLOPE);
        boolean handoffOk = finite(medianHandoff) && medianHandoff <= HANDOFF_OK_MEDIAN_ABS_PERCENT;

        return new Result(medianHandoff, handoffOk, meanError, expectedISlope, measuredISlope, keepHolding, latestFan);
    }

    public static final class Result {
        private final double medianHandoffAbsError;
        private final boolean handoffOk;
        private final double meanPositionError;
        private final double expectedITermSlope;
        private final double measuredITermSlope;
        private final boolean keepHolding;
        private final double fan1State;

        private Result(double medianHandoffAbsError, boolean handoffOk, double meanPositionError,
                double expectedITermSlope, double measuredITermSlope, boolean keepHolding, double fan1State) {
            this.medianHandoffAbsError = medianHandoffAbsError;
            this.handoffOk = handoffOk;
            this.meanPositionError = meanPositionError;
            this.expectedITermSlope = expectedITermSlope;
            this.measuredITermSlope = measuredITermSlope;
            this.keepHolding = keepHolding;
            this.fan1State = fan1State;
        }

        private static Result empty() {
            return new Result(Double.NaN, false, Double.NaN, Double.NaN, Double.NaN, false, Double.NaN);
        }

        public double getMedianHandoffAbsError() { return medianHandoffAbsError; }
        public boolean isHandoffAvailable() { return finite(medianHandoffAbsError); }
        public boolean isHandoffOk() { return handoffOk; }
        public double getMeanPositionError() { return meanPositionError; }
        public double getExpectedITermSlope() { return expectedITermSlope; }
        public double getMeasuredITermSlope() { return measuredITermSlope; }
        public boolean shouldKeepHolding() { return keepHolding; }
        public boolean isFan1Available() { return finite(fan1State); }
        public boolean isFan1On() { return finite(fan1State) && fan1State > 0.5; }
    }

    private static void addDistinct(List<TimedValue> series, double time, double value) {
        if (!series.isEmpty() && Math.abs(series.get(series.size() - 1).time - time) < 0.000001) {
            series.set(series.size() - 1, new TimedValue(time, value));
        } else {
            series.add(new TimedValue(time, value));
        }
    }

    private static double slope(List<TimedValue> values) {
        if (values == null || values.size() < 2) return Double.NaN;
        double meanT = 0.0, meanV = 0.0;
        for (TimedValue p : values) { meanT += p.time; meanV += p.value; }
        meanT /= values.size(); meanV /= values.size();
        double numerator = 0.0, denominator = 0.0;
        for (TimedValue p : values) {
            double dt = p.time - meanT;
            numerator += dt * (p.value - meanV);
            denominator += dt * dt;
        }
        return denominator <= 0.0 ? Double.NaN : numerator / denominator;
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>(values);
        Collections.sort(copy, new Comparator<Double>() {
            @Override public int compare(Double a, Double b) { return Double.compare(a.doubleValue(), b.doubleValue()); }
        });
        int n = copy.size();
        return (n & 1) == 1 ? copy.get(n / 2).doubleValue()
                : 0.5 * (copy.get(n / 2 - 1).doubleValue() + copy.get(n / 2).doubleValue());
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class TimedValue {
        final double time;
        final double value;
        TimedValue(double time, double value) { this.time = time; this.value = value; }
    }
}
