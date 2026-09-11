package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only live-session model.
 *
 * It deliberately changes at most one gain family per candidate and caps confidence because
 * the TunerStudio live callback is lower-rate than the companion MSL log.
 */
public final class LiveExperimentalRecommendationEngine {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");

    public LiveExperimentalRecommendation evaluate(List<LiveAttempt> attempts, TuneSnapshot current) {
        LiveSessionSummary summary = LiveSessionSummary.from(attempts);
        if (current == null || !current.isComplete()) {
            return new LiveExperimentalRecommendation(
                    LiveExperimentalRecommendation.BLOCKED_GAINS,
                    "The current P/I/D values could not be captured when the observer started.",
                    observations(summary) + "\n\nNo candidate was calculated.",
                    Double.NaN, false, current, current, "", 0.0,
                    "Current gains are incomplete.",
                    "No predicted effect is available.", summary);
        }
        if (!summary.hasMinimumExperimentalData()) {
            return new LiveExperimentalRecommendation(
                    LiveExperimentalRecommendation.WAITING,
                    "Collect at least two accepted attempts, including one Good attempt, for an experimental preview.",
                    observations(summary) + "\n\nThis is a deliberately lower gate than the validated offline recommendation, but it still requires repeated data.",
                    confidence(summary), false, current, current, "", 0.0,
                    "Not enough repeated live responses.",
                    "No predicted effect is available.", summary);
        }

        double confidence = confidence(summary);
        Decision decision = chooseSingleGain(summary);
        if (decision.percent == 0.0) {
            return new LiveExperimentalRecommendation(
                    LiveExperimentalRecommendation.NO_CHANGE,
                    "The live measurements do not justify a bounded one-gain experimental step.",
                    observations(summary) + "\n\nKeep the present gains and use the companion MSL for higher-rate inspection.",
                    confidence, true, current, current, "None", 0.0,
                    decision.reason, decision.effect, summary);
        }

        TuneSnapshot proposed = changed(current, decision.gain, decision.percent);
        return new LiveExperimentalRecommendation(
                LiveExperimentalRecommendation.CANDIDATE,
                "A read-only one-gain test candidate was calculated from " + summary.getAcceptedCount()
                        + " accepted live attempts (" + summary.getGoodCount() + " Good).",
                "Experimental preview only. Change the value manually in TunerStudio, do not burn it yet, store this session as the baseline, and capture a fresh comparison session. "
                        + "The plugin cannot write ECU RAM or flash.\n\n" + observations(summary)
                        + "\n\nLive sampling is intentionally treated as lower-rate evidence. Keep the matching MSL log for higher-rate verification of correction saturation and derivative activity.",
                confidence, true, current, proposed, decision.gain, decision.percent,
                decision.reason, decision.effect, summary);
    }

    public LiveComparisonResult compare(LiveBaselineSnapshot baseline,
                                        LiveSessionSummary current,
                                        TuneSnapshot currentGains,
                                        LiveCaptureProfile currentProfile,
                                        LiveMotionMode currentMotionMode) {
        return compare(baseline, current, currentGains, currentProfile, currentMotionMode, "", null);
    }

    public LiveComparisonResult compare(LiveBaselineSnapshot baseline,
                                        LiveSessionSummary current,
                                        TuneSnapshot currentGains,
                                        LiveCaptureProfile currentProfile,
                                        LiveMotionMode currentMotionMode,
                                        String currentConfigurationIdentity) {
        return compare(baseline, current, currentGains, currentProfile, currentMotionMode,
                currentConfigurationIdentity, null);
    }

    public LiveComparisonResult compare(LiveBaselineSnapshot baseline,
                                        LiveSessionSummary current,
                                        TuneSnapshot currentGains,
                                        LiveCaptureProfile currentProfile,
                                        LiveMotionMode currentMotionMode,
                                        String currentConfigurationIdentity,
                                        LiveCaptureSettings currentCaptureSettings) {
        if (baseline == null) {
            return new LiveComparisonResult(LiveComparisonResult.NO_BASELINE,
                    "Store a completed live session as the baseline before changing gains.", "", Double.NaN);
        }
        if (!sameConfigurationIdentity(baseline.getConfigurationIdentity(), currentConfigurationIdentity)) {
            return new LiveComparisonResult(LiveComparisonResult.INCOMPATIBLE,
                    "The sessions are not directly comparable: ECU configuration differs (baseline "
                            + displayConfiguration(baseline.getConfigurationIdentity()) + ", current "
                            + displayConfiguration(currentConfigurationIdentity) + ").",
                    comparisonHeader(baseline, currentGains), Double.NaN);
        }
        List<String> captureDifferences = captureSettingsDifferences(baseline.getCaptureSettings(), currentCaptureSettings);
        if (!captureDifferences.isEmpty()) {
            return new LiveComparisonResult(LiveComparisonResult.INCOMPATIBLE,
                    "The sessions are not directly comparable: " + join(captureDifferences) + ".",
                    comparisonHeader(baseline, currentGains), Double.NaN);
        }
        if (current == null || current.getAcceptedCount() < 2) {
            return new LiveComparisonResult(LiveComparisonResult.WAITING,
                    "Collect at least two accepted attempts with the test gains before comparing.",
                    comparisonHeader(baseline, currentGains), Double.NaN);
        }
        if (currentGains == null || !currentGains.isComplete()) {
            return new LiveComparisonResult(LiveComparisonResult.INCONCLUSIVE,
                    "The comparison-session gains are unavailable.", comparisonHeader(baseline, currentGains), Double.NaN);
        }
        if (sameGains(baseline.getGains(), currentGains)) {
            return new LiveComparisonResult(LiveComparisonResult.SAME_GAINS,
                    "The current session still uses the baseline gains. Change the proposed value manually, restart the observer, and capture the comparison attempts.",
                    comparisonHeader(baseline, currentGains), 0.0);
        }

        List<String> compatibility = new ArrayList<String>();
        LiveSessionSummary base = baseline.getSummary();
        if (finite(base.getMedianTargetRpm()) && finite(current.getMedianTargetRpm())
                && Math.abs(base.getMedianTargetRpm() - current.getMedianTargetRpm()) > 75.0) {
            compatibility.add("idle target differs by more than 75 RPM");
        }
        if (finite(base.getMedianCoolant()) && finite(current.getMedianCoolant())
                && Math.abs(base.getMedianCoolant() - current.getMedianCoolant()) > 20.0) {
            compatibility.add("coolant temperature differs by more than 20°C");
        }
        if (base.isFanOn() != current.isFanOn()) compatibility.add("fan state differs");
        if (baseline.getProfile() != currentProfile) compatibility.add("capture profile differs");
        if (baseline.getMotionMode() != currentMotionMode) compatibility.add("motion basis differs");
        if (!compatibility.isEmpty()) {
            return new LiveComparisonResult(LiveComparisonResult.INCOMPATIBLE,
                    "The sessions are not directly comparable: " + join(compatibility) + ".",
                    comparisonDetails(base, current, baseline.getGains(), currentGains), Double.NaN);
        }

        List<Double> improvements = new ArrayList<Double>();
        addImprovement(improvements, base.getMedianSettlingSeconds(), current.getMedianSettlingSeconds(), 0.5);
        addImprovement(improvements, base.getMedianMeanAbsoluteErrorRpm(), current.getMedianMeanAbsoluteErrorRpm(), 5.0);
        addImprovement(improvements, base.getMedianRpmStandardDeviation(), current.getMedianRpmStandardDeviation(), 5.0);
        addImprovement(improvements, Math.max(base.getMedianOvershootRpm(), base.getMedianUndershootRpm()),
                Math.max(current.getMedianOvershootRpm(), current.getMedianUndershootRpm()), 10.0);
        addImprovement(improvements, base.getMedianCorrectionLimitPercent(), current.getMedianCorrectionLimitPercent(), 5.0);
        addImprovement(improvements, base.getMedianCorrectionReversalsPerSecond(), current.getMedianCorrectionReversalsPerSecond(), 0.5);
        addImprovement(improvements, base.getMedianDTermSpan(), current.getMedianDTermSpan(), 25.0);
        if (improvements.size() < 3) {
            return new LiveComparisonResult(LiveComparisonResult.INCONCLUSIVE,
                    "Too few matching metrics are available for a before/after result.",
                    comparisonDetails(base, current, baseline.getGains(), currentGains), Double.NaN);
        }
        double score = median(improvements);
        int majorWorse = 0;
        for (Double improvement : improvements) if (improvement.doubleValue() < -15.0) majorWorse++;
        String status;
        String summary;
        if (score >= 8.0 && majorWorse == 0) {
            status = LiveComparisonResult.IMPROVED;
            summary = "The median comparison score improved by " + format(score) + "% without a major measured regression.";
        } else if (score <= -8.0 || majorWorse >= 2) {
            status = LiveComparisonResult.WORSE;
            summary = "The test gains worsened the aggregate response; restore the baseline values before another iteration.";
        } else {
            status = LiveComparisonResult.MIXED;
            summary = "Some measurements improved while others did not. Review the metric table before keeping or reverting the test value.";
        }
        return new LiveComparisonResult(status, summary,
                comparisonDetails(base, current, baseline.getGains(), currentGains), score);
    }

    private static Decision chooseSingleGain(LiveSessionSummary summary) {
        boolean derivativeActive = finite(summary.getMedianDTermSpan()) && summary.getMedianDTermSpan() > 300.0;
        derivativeActive = derivativeActive || (finite(summary.getMedianCorrectionReversalsPerSecond())
                && summary.getMedianCorrectionReversalsPerSecond() > 1.5);
        derivativeActive = derivativeActive || (finite(summary.getMedianCorrectionLimitPercent())
                && summary.getMedianCorrectionLimitPercent() > 60.0);
        if (derivativeActive) {
            return new Decision("D", -20.0,
                    "The accepted responses show a large derivative-term span and/or rapid idle-correction reversals. This is the clearest single-gain issue in the live data.",
                    "Reducing D should reduce output chatter and noise amplification. Validate that settling and overshoot do not worsen before burning any value.");
        }

        double excursion = Math.max(summary.getMedianOvershootRpm(), summary.getMedianUndershootRpm());
        if (finite(excursion) && excursion > 120.0) {
            return new Decision("P", -10.0,
                    "Repeated responses cross far past target without the derivative-activity rule being dominant.",
                    "Reducing P should reduce aggressive drive and overshoot, with a possible small increase in settling time.");
        }
        if (finite(summary.getMedianSettlingSeconds()) && summary.getMedianSettlingSeconds() > 10.0
                && (!finite(excursion) || excursion < 80.0)) {
            return new Decision("P", 8.0,
                    "Repeated responses settle slowly without a large opposite-side excursion or dominant derivative warning.",
                    "Increasing P slightly should speed recovery; the small step limits added overshoot risk.");
        }
        if (finite(summary.getMedianSignedErrorRpm()) && Math.abs(summary.getMedianSignedErrorRpm()) > 25.0
                && (!finite(summary.getMedianITermSpan()) || summary.getMedianITermSpan() < 18.0)) {
            return new Decision("I", 8.0,
                    "The recovered idle retains a persistent signed error while the integral term is not spanning its full clamp.",
                    "Increasing I slightly should remove steady error faster; watch for added overshoot.");
        }
        return new Decision("None", 0.0,
                "No one-gain change is clearly supported by the live-only measurements.",
                "Keep all gains unchanged and inspect the companion MSL before another test.");
    }

    private static double confidence(LiveSessionSummary summary) {
        double value = 30.0;
        value += Math.min(30.0, summary.getAcceptedCount() * 5.0);
        value += Math.min(20.0, summary.getGoodCount() * 5.0);
        value += summary.getRepeatabilityPercent() * 0.20;
        if (finite(summary.getMedianCorrectionLimitPercent()) && summary.getMedianCorrectionLimitPercent() > 50.0) value -= 8.0;
        // Live callback data is useful for iteration but lower-rate than the companion MSL.
        return clamp(value, 0.0, 70.0);
    }

    private static TuneSnapshot changed(TuneSnapshot current, String gain, double percent) {
        double p = current.getP();
        double i = current.getI();
        double d = current.getD();
        if ("P".equals(gain)) p = applyPercent(p, percent);
        if ("I".equals(gain)) i = applyPercent(i, percent);
        if ("D".equals(gain)) d = applyPercent(d, percent);
        return new TuneSnapshot(p, i, d, "Read-only live experimental candidate", true);
    }

    private static String observations(LiveSessionSummary s) {
        return "Live-session medians: " + s.getAcceptedCount() + " accepted (" + s.getGoodCount() + " Good); "
                + "target " + value(s.getMedianTargetRpm(), " RPM") + "; CLT " + value(s.getMedianCoolant(), "°C")
                + "; settling " + value(s.getMedianSettlingSeconds(), " s")
                + "; MAE " + value(s.getMedianMeanAbsoluteErrorRpm(), " RPM")
                + "; RPM deviation " + value(s.getMedianRpmStandardDeviation(), " RPM")
                + "; correction limits " + value(s.getMedianCorrectionLimitPercent(), "%")
                + "; correction reversals " + value(s.getMedianCorrectionReversalsPerSecond(), "/s")
                + "; I-term span " + value(s.getMedianITermSpan(), "")
                + "; D-term span " + value(s.getMedianDTermSpan(), "")
                + "; repeatability " + value(s.getRepeatabilityPercent(), "%") + ".";
    }

    private static String comparisonHeader(LiveBaselineSnapshot baseline, TuneSnapshot current) {
        return "Baseline gains: " + signature(baseline == null ? null : baseline.getGains())
                + "\nCurrent gains: " + signature(current);
    }

    private static String comparisonDetails(LiveSessionSummary base, LiveSessionSummary current,
                                            TuneSnapshot baselineGains, TuneSnapshot currentGains) {
        StringBuilder text = new StringBuilder();
        text.append("Baseline gains: ").append(signature(baselineGains)).append('\n');
        text.append("Current gains: ").append(signature(currentGains)).append("\n\n");
        text.append("Metric\tBaseline\tCurrent\tChange\n");
        appendMetric(text, "Settling s", base.getMedianSettlingSeconds(), current.getMedianSettlingSeconds(), 0.5);
        appendMetric(text, "Mean absolute error RPM", base.getMedianMeanAbsoluteErrorRpm(), current.getMedianMeanAbsoluteErrorRpm(), 5.0);
        appendMetric(text, "RPM deviation", base.getMedianRpmStandardDeviation(), current.getMedianRpmStandardDeviation(), 5.0);
        appendMetric(text, "Largest excursion RPM", Math.max(base.getMedianOvershootRpm(), base.getMedianUndershootRpm()),
                Math.max(current.getMedianOvershootRpm(), current.getMedianUndershootRpm()), 10.0);
        appendMetric(text, "Correction limit %", base.getMedianCorrectionLimitPercent(), current.getMedianCorrectionLimitPercent(), 5.0);
        appendMetric(text, "Correction reversals /s", base.getMedianCorrectionReversalsPerSecond(), current.getMedianCorrectionReversalsPerSecond(), 0.5);
        appendMetric(text, "D-term span", base.getMedianDTermSpan(), current.getMedianDTermSpan(), 25.0);
        text.append("\nPositive change percentages mean the current session improved because lower is better for these metrics.");
        return text.toString();
    }

    private static void appendMetric(StringBuilder text, String label, double baseline, double current, double floor) {
        text.append(label).append('\t').append(value(baseline, "")).append('\t').append(value(current, "")).append('\t');
        if (finite(baseline) && finite(current)) text.append(format(improvement(baseline, current, floor))).append('%');
        else text.append("Unavailable");
        text.append('\n');
    }

    private static void addImprovement(List<Double> values, double baseline, double current, double floor) {
        if (finite(baseline) && finite(current)) values.add(Double.valueOf(improvement(baseline, current, floor)));
    }

    private static double improvement(double baseline, double current, double floor) {
        return (baseline - current) / Math.max(floor, Math.abs(baseline)) * 100.0;
    }

    private static List<String> captureSettingsDifferences(LiveCaptureSettings baseline,
                                                              LiveCaptureSettings current) {
        if (baseline == null && current == null) return Collections.emptyList();
        if (baseline == null || current == null) {
            return Collections.singletonList("effective capture settings are unavailable for one session");
        }
        return baseline.comparisonDifferences(current);
    }

    private static boolean sameConfigurationIdentity(String left, String right) {
        String normalizedLeft = normalizeConfiguration(left);
        String normalizedRight = normalizeConfiguration(right);
        if (normalizedLeft.isEmpty() || normalizedRight.isEmpty()) {
            return normalizedLeft.isEmpty() && normalizedRight.isEmpty();
        }
        return normalizedLeft.equals(normalizedRight);
    }

    private static String displayConfiguration(String value) {
        String normalized = normalizeConfiguration(value);
        return normalized.isEmpty() ? "unidentified" : "'" + normalized + "'";
    }

    private static String normalizeConfiguration(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean sameGains(TuneSnapshot left, TuneSnapshot right) {
        if (left == null || right == null || !left.isComplete() || !right.isComplete()) return false;
        return Math.abs(left.getP() - right.getP()) < 0.00005
                && Math.abs(left.getI() - right.getI()) < 0.00005
                && Math.abs(left.getD() - right.getD()) < 0.00005;
    }

    private static double applyPercent(double value, double percent) {
        double result = value * (1.0 + percent / 100.0);
        return Math.round(result * 10000.0) / 10000.0;
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle).doubleValue();
        return (sorted.get(middle - 1).doubleValue() + sorted.get(middle).doubleValue()) / 2.0;
    }

    private static String signature(TuneSnapshot snapshot) {
        return snapshot == null ? "Unavailable" : snapshot.toSignature();
    }

    private static String value(double value, String suffix) {
        return finite(value) ? format(value) + suffix : "Unavailable";
    }

    private static String format(double value) {
        if (!finite(value)) return "Unavailable";
        synchronized (ONE) { return ONE.format(value); }
    }

    private static String join(List<String> values) {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) text.append(i == values.size() - 1 ? " and " : ", ");
            text.append(values.get(i));
        }
        return text.toString();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Decision {
        private final String gain;
        private final double percent;
        private final String reason;
        private final String effect;

        private Decision(String gain, double percent, String reason, String effect) {
            this.gain = gain;
            this.percent = percent;
            this.reason = reason;
            this.effect = effect;
        }
    }
}
