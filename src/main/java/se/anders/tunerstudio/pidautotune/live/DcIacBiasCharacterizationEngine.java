package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Rolling static DC-IAC bias characterization.
 *
 * 0.5.21 changes the contract from "quality gate decides whether collection exists" to
 * "collection continues and each complete window is graded GOOD / OK / LOW". Only GOOD/OK
 * windows may feed a tuning recommendation; LOW windows remain visible evidence/context.
 */
public final class DcIacBiasCharacterizationEngine {
    private static final String TARGET = "dcIdleTarget";
    private static final String ACTUAL = "idlePositionSensor";
    private static final String DUTY = "dcIdleDutyCycle";
    private static final String PID_OUTPUT = "dcIdlePositionStatus_output";
    private static final String I_TERM = "dcIdlePositionStatus_iTerm";
    private static final String RESET = "dcIdlePositionStatus_resetCounter";
    private static final String FAULT = "dcIdleFaultCode";
    private static final String VBATT = "VBatt";

    private static final String[] REQUIRED = {
            TARGET, ACTUAL, DUTY, PID_OUTPUT, I_TERM, RESET, FAULT, VBATT
    };

    private final List<ProfileLiveSample> buffer = new ArrayList<ProfileLiveSample>();
    private double lastReset = Double.NaN;
    private int sequence;
    private String readiness = "Waiting for samples";

    public void reset() {
        buffer.clear();
        lastReset = Double.NaN;
        sequence = 0;
        readiness = "Waiting for samples";
    }

    public void stop() {
        buffer.clear();
        readiness = "Stopped";
    }

    public String getReadiness() { return readiness; }

    public DcIacBiasEvidence process(
            ProfileLiveSample sample,
            DcIacEventSettings eventSettings,
            DcIacBiasCharacterizationSettings settings,
            DcIacBiasSettingsAccess.Snapshot bias,
            boolean motorControlPaused) {
        if (sample == null || eventSettings == null || settings == null || bias == null) return null;

        double reset = sample.get(RESET);
        boolean resetChanged = finite(reset) && finite(lastReset) && Math.abs(reset - lastReset) > 0.0001;
        if (finite(reset)) lastReset = reset;
        if (resetChanged) {
            buffer.clear();
            readiness = "LOW quality context: PID reset/reinitialization observed; collection continues with a new window";
            return null;
        }

        String unavailable = unavailableIssue(sample, eventSettings);
        if (unavailable != null) {
            // We cannot calculate a window without the required channels, but the session does not stop.
            buffer.clear();
            readiness = "LOW quality / unavailable: " + unavailable + "; capture remains running";
            return null;
        }

        buffer.add(sample);
        trim(sample.getTimeSeconds(), settings.getWindowSeconds() + 1.0);

        if (buffer.size() < 2 || duration(buffer) < settings.getWindowSeconds()) {
            readiness = "Collecting rolling bias window — quality will be graded, not used as a stop gate";
            return null;
        }

        List<ProfileLiveSample> window = recent(sample.getTimeSeconds(), settings.getWindowSeconds());
        List<Point> actualSeries = series(window, ACTUAL);
        List<Point> targetSeries = series(window, TARGET);
        List<Point> dutySeries = series(window, DUTY);
        List<Point> pidSeries = series(window, PID_OUTPUT);
        List<Point> iSeries = series(window, I_TERM);
        List<Point> vbattSeries = series(window, VBATT);

        if (actualSeries.size() < Math.max(2, settings.getMinimumDistinctSamples() / 4)
                || durationPoints(actualSeries) < settings.getMinimumWindowDurationSeconds() * 0.75) {
            readiness = "LOW quality: source updates are sparse; collection continues until a measurable window exists";
            return null;
        }

        double targetMean = mean(targetSeries);
        double actualMean = mean(actualSeries);
        double targetSpan = span(targetSeries);
        double targetSlope = slope(targetSeries);
        double actualSpan = span(actualSeries);
        double actualSlope = slope(actualSeries);
        double iSlope = slope(iSeries);
        double steadyError = targetMean - actualMean;
        double batterySpread = span(vbattSeries);
        double meanDuty = mean(dutySeries);
        double meanPid = mean(pidSeries);
        double meanI = mean(iSeries);
        double configuredBias = bias.interpolate(targetMean);
        double observedFeedForward = meanDifference(window, DUTY, PID_OUTPUT);
        double feedForwardMappingError = Math.abs(observedFeedForward - configuredBias);
        double requiredBias = configuredBias + meanPid;
        double dutyAgreement = finite(meanDuty) && finite(requiredBias) ? Math.abs(requiredBias - meanDuty) : Double.NaN;
        double correction = requiredBias - configuredBias;

        List<String> strict = new ArrayList<String>();
        List<String> severe = new ArrayList<String>();

        if (motorControlPaused) severe.add("shared motor-pause active");
        double fault = sample.get(FAULT);
        if (!finite(fault) || Math.abs(fault) > 0.0001) severe.add("DC-IAC fault active/unavailable");

        double low = eventSettings.getMinimumTarget() + eventSettings.getTargetLimitMargin();
        double high = eventSettings.getMaximumTarget() - eventSettings.getTargetLimitMargin();
        if (!finite(targetMean) || targetMean <= low || targetMean >= high) severe.add("target near configured travel limit");
        if (!finite(meanDuty) || Math.abs(meanDuty) >= eventSettings.getSaturationDutyPercent()) severe.add("motor duty at/near saturation guard");

        if (!finite(targetSpan) || targetSpan > settings.getMaximumTargetSpan()) strict.add("target span/motion high");
        if (!finite(targetSlope) || Math.abs(targetSlope) > settings.getMaximumTargetSlopePerSecond()) strict.add("target slope high");
        if (!finite(actualSpan) || actualSpan > settings.getMaximumActualSpan()) strict.add("actual-position span high");
        if (!finite(actualSlope) || Math.abs(actualSlope) > settings.getMaximumActualSlopePerSecond()) strict.add("actual-position slope high");
        if (!finite(iSlope) || Math.abs(iSlope) > settings.getMaximumITermSlopePerSecond()) strict.add("I-term still moving");
        if (!finite(steadyError) || Math.abs(steadyError) > settings.getMaximumAbsoluteSteadyError()) strict.add("steady error above preferred band");
        if (!finite(batterySpread) || batterySpread > settings.getMaximumBatterySpread()) strict.add("battery spread above preferred band");

        if (!finite(observedFeedForward) || !finite(configuredBias) || !finite(requiredBias)) {
            severe.add("feed-forward estimate unavailable");
        } else if (feedForwardMappingError > Math.max(2.0, settings.getMaximumFeedForwardMappingError() * 4.0)) {
            severe.add("runtime feed-forward mapping disagrees strongly with configured bias");
        } else if (feedForwardMappingError > settings.getMaximumFeedForwardMappingError()) {
            strict.add("feed-forward mapping mismatch");
        }

        if (!finite(dutyAgreement)) {
            severe.add("bias+PID versus total-duty cross-check unavailable");
        } else if (dutyAgreement > Math.max(2.0, settings.getMaximumFeedForwardMappingError() * 4.0)) {
            severe.add("bias+PID versus total-duty cross-check disagrees strongly");
        } else if (dutyAgreement > settings.getMaximumFeedForwardMappingError()) {
            strict.add("bias+PID versus total-duty cross-check outside preferred band");
        }

        if (bias.areIntegralLimitsKnown()) {
            if (meanI <= bias.getITermMinimum() + settings.getIntegralLimitMargin()
                    || meanI >= bias.getITermMaximum() - settings.getIntegralLimitMargin()) {
                strict.add("I term near configured limit");
            }
        }

        String quality;
        boolean eligible;
        if (!severe.isEmpty()) {
            quality = DcIacBiasEvidence.QUALITY_LOW;
            eligible = false;
        } else if (strict.isEmpty()) {
            quality = DcIacBiasEvidence.QUALITY_GOOD;
            eligible = true;
        } else if (strict.size() <= 4) {
            // Imperfect starting tunes are allowed to contribute. Cross-window convergence remains
            // the second recommendation layer, so one ugly window cannot directly move a knot.
            quality = DcIacBiasEvidence.QUALITY_OK;
            eligible = true;
        } else {
            quality = DcIacBiasEvidence.QUALITY_LOW;
            eligible = false;
        }

        String detail = qualityDetail(quality, strict, severe);
        DcIacBiasEvidence evidence = new DcIacBiasEvidence(
                ++sequence,
                firstTime(actualSeries), lastTime(actualSeries), actualSeries.size(), sampleRate(actualSeries),
                targetMean, actualMean, steadyError,
                meanDuty, meanPid, meanI,
                configuredBias, observedFeedForward, requiredBias, correction,
                targetSlope, actualSlope, iSlope, actualSpan, batterySpread,
                bias.segmentLabel(targetMean), quality, eligible, detail);

        buffer.clear();
        buffer.add(sample);
        readiness = "Captured " + quality + " quality bias window at " + one(targetMean)
                + "% — collection continues" + (eligible ? "; usable for convergence" : "; context only");
        return evidence;
    }

    private static String unavailableIssue(ProfileLiveSample sample, DcIacEventSettings settings) {
        for (String name : REQUIRED) {
            if (!sample.has(name) || sample.getAgeSeconds(name) > settings.getChannelFreshnessSeconds()) {
                return "required live channel " + name + " missing/stale";
            }
        }
        double voltage = sample.get(VBATT);
        if (!finite(voltage)) return "battery voltage unavailable";
        return null;
    }

    private static String qualityDetail(String quality, List<String> strict, List<String> severe) {
        StringBuilder b = new StringBuilder();
        b.append("Evidence quality ").append(quality).append(". ");
        if (!severe.isEmpty()) {
            b.append("Context retained but excluded from recommendations: ").append(join(severe)).append('.');
        } else if (!strict.isEmpty()) {
            b.append("Captured despite non-ideal conditions: ").append(join(strict)).append(". Repeated convergence decides whether it can support a correction.");
        } else {
            b.append("All preferred equilibrium checks passed.");
        }
        return b.toString();
    }

    private static String join(List<String> values) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) b.append(", ");
            b.append(values.get(i));
        }
        return b.toString();
    }

    private void trim(double now, double keepSeconds) {
        while (buffer.size() > 1 && now - buffer.get(0).getTimeSeconds() > keepSeconds) buffer.remove(0);
    }

    private List<ProfileLiveSample> recent(double now, double seconds) {
        double start = now - seconds;
        List<ProfileLiveSample> result = new ArrayList<ProfileLiveSample>();
        for (ProfileLiveSample sample : buffer) if (sample.getTimeSeconds() >= start) result.add(sample);
        return result;
    }

    private static List<Point> series(List<ProfileLiveSample> samples, String name) {
        List<Point> result = new ArrayList<Point>();
        double last = Double.NaN;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get(name);
            double time = sample.getUpdateTimeSeconds(name);
            if (!finite(value) || !finite(time)) continue;
            if (finite(last) && Math.abs(time - last) < 1e-9) continue;
            result.add(new Point(time, value));
            last = time;
        }
        Collections.sort(result, new Comparator<Point>() {
            @Override public int compare(Point a, Point b) { return Double.compare(a.time, b.time); }
        });
        return result;
    }

    private static double meanDifference(List<ProfileLiveSample> samples, String first, String second) {
        double sum = 0.0;
        int count = 0;
        double lastFirstUpdate = Double.NaN;
        for (ProfileLiveSample sample : samples) {
            double a = sample.get(first);
            double b = sample.get(second);
            double update = sample.getUpdateTimeSeconds(first);
            if (!finite(a) || !finite(b) || !finite(update)) continue;
            if (finite(lastFirstUpdate) && Math.abs(update - lastFirstUpdate) < 1e-9) continue;
            sum += a - b;
            count++;
            lastFirstUpdate = update;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double duration(List<ProfileLiveSample> samples) {
        if (samples.size() < 2) return 0.0;
        return samples.get(samples.size() - 1).getTimeSeconds() - samples.get(0).getTimeSeconds();
    }

    private static double durationPoints(List<Point> points) {
        return points.size() < 2 ? 0.0 : points.get(points.size() - 1).time - points.get(0).time;
    }

    private static double firstTime(List<Point> points) { return points.isEmpty() ? Double.NaN : points.get(0).time; }
    private static double lastTime(List<Point> points) { return points.isEmpty() ? Double.NaN : points.get(points.size() - 1).time; }

    private static double sampleRate(List<Point> points) {
        double d = durationPoints(points);
        return points.size() >= 2 && d > 0.0 ? (points.size() - 1) / d : Double.NaN;
    }

    private static double mean(List<Point> points) {
        if (points.isEmpty()) return Double.NaN;
        double sum = 0.0;
        for (Point point : points) sum += point.value;
        return sum / points.size();
    }

    private static double span(List<Point> points) {
        if (points.isEmpty()) return Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (Point point : points) {
            min = Math.min(min, point.value);
            max = Math.max(max, point.value);
        }
        return max - min;
    }

    private static double slope(List<Point> points) {
        if (points.size() < 2) return Double.NaN;
        double origin = points.get(0).time;
        double sumT = 0.0, sumV = 0.0, sumTT = 0.0, sumTV = 0.0;
        int count = 0;
        for (Point point : points) {
            double t = point.time - origin;
            sumT += t;
            sumV += point.value;
            sumTT += t * t;
            sumTV += t * point.value;
            count++;
        }
        double denominator = count * sumTT - sumT * sumT;
        return Math.abs(denominator) < 1e-12 ? Double.NaN : (count * sumTV - sumT * sumV) / denominator;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String one(double value) {
        return String.format(java.util.Locale.US, "%.1f", value);
    }

    private static final class Point {
        private final double time;
        private final double value;
        private Point(double time, double value) { this.time = time; this.value = value; }
    }
}
