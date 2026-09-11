package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;
import se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment;
import se.anders.tunerstudio.pidautotune.dataset.DatasetEvent;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.LogChannelDefinition;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Conservative, read-only relative-gain model.
 *
 * This model intentionally refuses to produce values until the dataset readiness gate passes.
 * It uses repeated event consistency and steady-state activity to suggest bounded relative changes;
 * it does not claim to identify an exact physical plant model.
 */
public final class ConservativePidRecommendationEngine {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");

    public PidRecommendation evaluate(
            List<DatasetEvent> rows,
            DatasetAssessment assessment,
            RecommendationSettings settings) {
        if (settings == null) settings = RecommendationSettings.defaults();
        String bestGroup = assessment == null ? "" : assessment.getBestGroup();
        List<DatasetEvent> transients = matchingTransients(rows, bestGroup);
        String operatingGroup = transients.isEmpty() ? "" : transients.get(0).getOperatingGroup();
        List<DatasetEvent> steady = matchingSteady(rows, operatingGroup);
        TuneSnapshot current = transients.isEmpty() ? firstIncludedSnapshot(rows) : transients.get(0).getTuneSnapshot();
        ResponseSummary response = summarize(transients, steady);

        String observations = buildObservations(response, transients, steady);
        boolean datasetReady = assessment != null
                && DatasetAssessment.CONSERVATIVE_READY.equals(assessment.getLevel())
                && transients.size() >= 3
                && response.getGoodTransientCount() >= 2
                && !steady.isEmpty()
                && current != null
                && current.isComplete();

        if (!datasetReady) {
            String readiness = assessment == null
                    ? "No dataset assessment is available."
                    : assessment.getSummary();
            return new PidRecommendation(
                    PidRecommendation.BLOCKED_DATASET,
                    readiness,
                    "No gain values were calculated. The model remains in diagnostic-preview mode until the existing dataset gate reports Suitable for conservative recommendation.\n\n"
                            + observations,
                    bestGroup,
                    Double.NaN,
                    false,
                    current,
                    current,
                    0.0, 0.0, 0.0,
                    "Dataset gate has not passed.",
                    "Dataset gate has not passed.",
                    "Dataset gate has not passed.",
                    "No predicted gain effect is shown while blocked.",
                    "No predicted gain effect is shown while blocked.",
                    "No predicted gain effect is shown while blocked.",
                    response);
        }

        double confidence = confidence(transients, response);
        if (confidence < settings.getMinimumConfidencePercent()) {
            return new PidRecommendation(
                    PidRecommendation.BLOCKED_CONFIDENCE,
                    "The dataset gate passed, but response consistency produced only " + format(confidence)
                            + "% confidence; the configured minimum is " + format(settings.getMinimumConfidencePercent()) + "%.",
                    "No proposed gains were produced. Collect more repeatable responses or exclude an inconsistent event.\n\n"
                            + observations,
                    bestGroup,
                    confidence,
                    false,
                    current,
                    current,
                    0.0, 0.0, 0.0,
                    "Model confidence is below the configured minimum.",
                    "Model confidence is below the configured minimum.",
                    "Model confidence is below the configured minimum.",
                    "No predicted gain effect is shown while blocked.",
                    "No predicted gain effect is shown while blocked.",
                    "No predicted gain effect is shown while blocked.",
                    response);
        }

        Adjustment p = pAdjustment(response, settings);
        Adjustment i = iAdjustment(response, settings);
        Adjustment d = dAdjustment(response, settings);
        double proposedP = applyPercent(current.getP(), p.percent);
        double proposedI = applyPercent(current.getI(), i.percent);
        double proposedD = applyPercent(current.getD(), d.percent);
        TuneSnapshot proposed = new TuneSnapshot(
                proposedP, proposedI, proposedD,
                "Read-only conservative candidate", true);
        boolean changed = Math.abs(p.percent) > 0.0001 || Math.abs(i.percent) > 0.0001 || Math.abs(d.percent) > 0.0001;
        String status = changed ? PidRecommendation.READY : PidRecommendation.NO_CHANGE;
        String summary = changed
                ? "A bounded read-only candidate was calculated from " + transients.size()
                    + " compatible transient responses and " + steady.size() + " compatible steady hold(s)."
                : "The measured response does not justify a bounded gain change under the current conservative rules.";

        return new PidRecommendation(
                status,
                summary,
                "This is a relative recommendation, not an exact plant identification. Apply values manually only after review, then record a fresh validation log before making another change. The plugin cannot write or burn ECU settings.\n\n"
                        + observations,
                bestGroup,
                confidence,
                true,
                current,
                proposed,
                p.percent, i.percent, d.percent,
                p.reason, i.reason, d.reason,
                p.effect, i.effect, d.effect,
                response);
    }

    private static List<DatasetEvent> matchingTransients(List<DatasetEvent> rows, String bestGroup) {
        List<DatasetEvent> result = new ArrayList<DatasetEvent>();
        if (rows == null || bestGroup == null || bestGroup.isEmpty()) return result;
        for (DatasetEvent row : rows) {
            if (row.isIncluded() && row.isRecommendationGateEligible()
                    && row.getEvent().isFutureGainEligible()
                    && bestGroup.equals(row.getResponseGroup())) result.add(row);
        }
        return result;
    }

    private static List<DatasetEvent> matchingSteady(List<DatasetEvent> rows, String operatingGroup) {
        List<DatasetEvent> result = new ArrayList<DatasetEvent>();
        if (rows == null || operatingGroup == null || operatingGroup.isEmpty()) return result;
        for (DatasetEvent row : rows) {
            if (row.isIncluded() && row.isRecommendationGateEligible()
                    && row.getEvent().isSteadyHold()
                    && operatingGroup.equals(row.getOperatingGroup())) result.add(row);
        }
        return result;
    }

    private static TuneSnapshot firstIncludedSnapshot(List<DatasetEvent> rows) {
        if (rows == null) return null;
        for (DatasetEvent row : rows) {
            if (row.isIncluded() && row.getTuneSnapshot().isComplete()) return row.getTuneSnapshot();
        }
        return null;
    }

    private static ResponseSummary summarize(List<DatasetEvent> transients, List<DatasetEvent> steady) {
        List<Double> delays = new ArrayList<Double>();
        List<Double> settling = new ArrayList<Double>();
        List<Double> overshootRatios = new ArrayList<Double>();
        List<Double> decayRatios = new ArrayList<Double>();
        List<Double> oscillation = new ArrayList<Double>();
        List<Double> limit = new ArrayList<Double>();
        int good = 0;
        for (DatasetEvent row : transients) {
            IdleEvent event = row.getEvent();
            if ("Good for analysis".equals(event.getQuality())) good++;
            EventResponse response = measureResponse(row);
            addFinite(delays, response.delaySeconds);
            addFinite(settling, event.isSettlingApplicable() ? event.getSettlingSeconds() : Double.NaN);
            addFinite(overshootRatios, response.oppositeOvershootRatio);
            addFinite(decayRatios, response.decayRatio);
            addFinite(oscillation, response.oscillationHz);
            addFinite(limit, event.getCorrectionLimitPercent());
        }

        List<Double> signedError = new ArrayList<Double>();
        List<Double> mae = new ArrayList<Double>();
        List<Double> std = new ArrayList<Double>();
        List<Double> reversals = new ArrayList<Double>();
        List<Double> dSpan = new ArrayList<Double>();
        List<Double> iSpan = new ArrayList<Double>();
        for (DatasetEvent row : steady) {
            IdleEvent event = row.getEvent();
            addFinite(signedError, event.getMeanSignedError());
            addFinite(mae, event.getMeanAbsoluteError());
            addFinite(std, event.getRpmStandardDeviation());
            addFinite(reversals, event.getCorrectionReversalsPerSecond());
            addFinite(dSpan, span(event.getDTermMinimum(), event.getDTermMaximum()));
            addFinite(iSpan, span(event.getITermMinimum(), event.getITermMaximum()));
        }

        double consistency = consistencyPercent(delays, settling, overshootRatios);
        return new ResponseSummary(
                transients.size(), good, steady.size(),
                median(delays), median(settling), median(overshootRatios), median(decayRatios), median(oscillation),
                consistency, median(limit), median(signedError), median(mae), median(std), median(reversals),
                median(dSpan), median(iSpan));
    }

    private static EventResponse measureResponse(DatasetEvent row) {
        IdleLogData data = row.getAnalyzedLog().getData();
        IdleEvent event = row.getEvent();
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        if (time == null || rpm == null || target == null) return EventResponse.empty();
        int maximum = Math.min(time.length, Math.min(rpm.length, target.length)) - 1;
        int start = clamp(event.getTriggerIndex(), 0, maximum);
        int end = clamp(event.getEndIndex(), start, maximum);
        double initialError = medianError(time, rpm, target, start, end, 0.25);
        double initialAbs = Math.abs(initialError);
        double threshold = Math.max(15.0, Math.min(75.0, initialAbs * 0.15));
        double delay = Double.NaN;
        for (int index = start; index <= end; index++) {
            double smoothed = smoothedAbsoluteError(time, rpm, target, index, start, end, 0.15);
            if (finite(smoothed) && smoothed <= Math.max(0.0, initialAbs - threshold)) {
                delay = Math.max(0.0, time[index] - time[start]);
                break;
            }
        }
        double oppositePeak = initialError < 0.0 ? event.getOvershootRpm() : event.getUndershootRpm();
        double overshootRatio = initialAbs >= 20.0 ? oppositePeak / initialAbs : Double.NaN;
        double decay = decayRatio(time, rpm, target, start, end);
        double frequency = oscillationFrequency(time, rpm, target, start, end, event);
        return new EventResponse(delay, overshootRatio, decay, frequency);
    }

    private static double medianError(double[] time, double[] rpm, double[] target, int start, int end, double seconds) {
        List<Double> values = new ArrayList<Double>();
        double stop = time[start] + seconds;
        for (int index = start; index <= end && time[index] <= stop; index++) {
            if (finite(rpm[index]) && finite(target[index])) values.add(Double.valueOf(rpm[index] - target[index]));
        }
        return median(values);
    }

    private static double smoothedAbsoluteError(
            double[] time, double[] rpm, double[] target, int center, int start, int end, double widthSeconds) {
        double half = widthSeconds / 2.0;
        double from = time[center] - half;
        double to = time[center] + half;
        double sum = 0.0;
        int count = 0;
        for (int index = center; index >= start && time[index] >= from; index--) {
            if (finite(rpm[index]) && finite(target[index])) { sum += Math.abs(rpm[index] - target[index]); count++; }
        }
        for (int index = center + 1; index <= end && time[index] <= to; index++) {
            if (finite(rpm[index]) && finite(target[index])) { sum += Math.abs(rpm[index] - target[index]); count++; }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double decayRatio(double[] time, double[] rpm, double[] target, int start, int end) {
        double duration = time[end] - time[start];
        if (!finite(duration) || duration < 2.0) return Double.NaN;
        double firstStart = time[start] + Math.min(0.5, duration * 0.10);
        double middle = time[start] + duration * 0.50;
        double finalStart = time[start] + duration * 0.70;
        double firstPeak = peakAbsoluteError(time, rpm, target, start, end, firstStart, middle);
        double finalPeak = peakAbsoluteError(time, rpm, target, start, end, finalStart, time[end]);
        if (!finite(firstPeak) || firstPeak < 10.0 || !finite(finalPeak)) return Double.NaN;
        return Math.max(0.0, finalPeak / firstPeak);
    }

    private static double peakAbsoluteError(
            double[] time, double[] rpm, double[] target, int start, int end, double from, double to) {
        double peak = Double.NaN;
        for (int index = start; index <= end; index++) {
            if (time[index] < from || time[index] > to || !finite(rpm[index]) || !finite(target[index])) continue;
            double value = Math.abs(rpm[index] - target[index]);
            if (!finite(peak) || value > peak) peak = value;
        }
        return peak;
    }

    private static double oscillationFrequency(
            double[] time, double[] rpm, double[] target, int start, int end, IdleEvent event) {
        double analysisStart = time[start] + 1.0;
        if (event.isSettlingApplicable() && finite(event.getSettlingSeconds())) {
            analysisStart = Math.min(time[end] - 1.0, time[start] + event.getSettlingSeconds());
        }
        if (time[end] - analysisStart < 2.0) return Double.NaN;
        List<Double> bucketTimes = new ArrayList<Double>();
        List<Double> bucketErrors = new ArrayList<Double>();
        double bucketStart = analysisStart;
        double sum = 0.0;
        int count = 0;
        for (int index = start; index <= end; index++) {
            if (time[index] < analysisStart) continue;
            while (time[index] >= bucketStart + 0.20) {
                if (count > 0) {
                    bucketTimes.add(Double.valueOf(bucketStart + 0.10));
                    bucketErrors.add(Double.valueOf(sum / count));
                }
                bucketStart += 0.20;
                sum = 0.0;
                count = 0;
            }
            if (finite(rpm[index]) && finite(target[index])) { sum += rpm[index] - target[index]; count++; }
        }
        if (count > 0) {
            bucketTimes.add(Double.valueOf(bucketStart + 0.10));
            bucketErrors.add(Double.valueOf(sum / count));
        }
        if (bucketErrors.size() < 5) return Double.NaN;
        double mean = mean(bucketErrors);
        double deadband = 7.5;
        int previousSign = 0;
        int crossings = 0;
        for (Double boxed : bucketErrors) {
            double value = boxed.doubleValue() - mean;
            int sign = value > deadband ? 1 : (value < -deadband ? -1 : 0);
            if (sign != 0 && previousSign != 0 && sign != previousSign) crossings++;
            if (sign != 0) previousSign = sign;
        }
        double duration = bucketTimes.get(bucketTimes.size() - 1).doubleValue() - bucketTimes.get(0).doubleValue();
        return duration > 0.0 ? crossings / (2.0 * duration) : Double.NaN;
    }

    private static double confidence(List<DatasetEvent> transients, ResponseSummary summary) {
        double value = 92.0;
        value -= Math.max(0, 4 - transients.size()) * 6.0;
        value -= Math.max(0, transients.size() - summary.getGoodTransientCount()) * 5.0;
        value -= Math.max(0.0, 100.0 - summary.getTransientConsistencyPercent()) * 0.35;
        if (finite(summary.getMedianTransientLimitPercent())) {
            if (summary.getMedianTransientLimitPercent() > 20.0) value -= 15.0;
            else if (summary.getMedianTransientLimitPercent() > 10.0) value -= 7.0;
        }
        return clamp(value, 0.0, 100.0);
    }

    private static Adjustment pAdjustment(ResponseSummary summary, RecommendationSettings settings) {
        boolean oscillatory = finite(summary.getMedianOppositeOvershootRatio())
                && summary.getMedianOppositeOvershootRatio() > 0.35;
        oscillatory = oscillatory || (finite(summary.getMedianDecayRatio()) && summary.getMedianDecayRatio() > 0.80);
        oscillatory = oscillatory || (finite(summary.getMedianOscillationHz()) && summary.getMedianOscillationHz() > 0.80);
        boolean slow = finite(summary.getMedianSettlingSeconds()) && summary.getMedianSettlingSeconds() > 6.0
                && (!finite(summary.getMedianOppositeOvershootRatio()) || summary.getMedianOppositeOvershootRatio() < 0.25)
                && (!finite(summary.getMedianDecayRatio()) || summary.getMedianDecayRatio() < 0.65);
        if (oscillatory) {
            double change = -Math.min(10.0, settings.getMaximumPChangePercent());
            return new Adjustment(change,
                    "Repeated responses show excess opposite-side overshoot, weak decay, or a sustained oscillation signature.",
                    "Lower P should reduce overshoot and oscillatory drive, with a possible small increase in response time.");
        }
        if (slow) {
            double change = Math.min(10.0, settings.getMaximumPChangePercent());
            return new Adjustment(change,
                    "Responses settle slowly without a strong overshoot or weak-decay warning.",
                    "Higher P should improve response speed, while the configured change cap limits added overshoot risk.");
        }
        return new Adjustment(0.0,
                "The repeated transient responses do not justify a conservative P change.",
                "P remains unchanged, so proportional response speed and overshoot tendency are not intentionally altered.");
    }

    private static Adjustment iAdjustment(ResponseSummary summary, RecommendationSettings settings) {
        double signed = summary.getMedianSteadySignedErrorRpm();
        if (finite(signed) && Math.abs(signed) > 20.0
                && (!finite(summary.getMedianTransientLimitPercent()) || summary.getMedianTransientLimitPercent() < 10.0)) {
            double change = Math.min(10.0, settings.getMaximumIChangePercent());
            return new Adjustment(change,
                    "Compatible steady holds retain a persistent signed RPM error without substantial output saturation.",
                    "Higher I should remove steady error faster, but may add overshoot if increased again before validation.");
        }
        boolean clamped = finite(summary.getMedianSteadyIntegralSpan()) && summary.getMedianSteadyIntegralSpan() >= 18.0;
        boolean overshoot = finite(summary.getMedianOppositeOvershootRatio()) && summary.getMedianOppositeOvershootRatio() > 0.35;
        if (clamped && overshoot) {
            double change = -Math.min(7.5, settings.getMaximumIChangePercent());
            return new Adjustment(change,
                    "The integral term spans nearly its full reported clamp while repeated responses cross well past target.",
                    "Lower I should reduce accumulated overshoot and recovery time, with slower correction of persistent bias.");
        }
        return new Adjustment(0.0,
                "Steady signed error and transient overshoot do not justify a conservative I change.",
                "I remains unchanged, so long-term error removal is not intentionally altered.");
    }

    private static Adjustment dAdjustment(ResponseSummary summary, RecommendationSettings settings) {
        boolean noisy = finite(summary.getMedianSteadyCorrectionReversalsPerSecond())
                && summary.getMedianSteadyCorrectionReversalsPerSecond() > 5.0;
        noisy = noisy || (finite(summary.getMedianSteadyDerivativeSpan()) && summary.getMedianSteadyDerivativeSpan() > 300.0);
        if (noisy) {
            double change = -Math.min(20.0, settings.getMaximumDChangePercent());
            return new Adjustment(change,
                    "Compatible steady holds show rapid correction reversals or a very large reported derivative-term span.",
                    "Lower D should reduce output chatter and noise amplification; validate that overshoot does not worsen.");
        }
        boolean needsDamping = finite(summary.getMedianOppositeOvershootRatio())
                && summary.getMedianOppositeOvershootRatio() > 0.30
                && (!finite(summary.getMedianSteadyCorrectionReversalsPerSecond())
                    || summary.getMedianSteadyCorrectionReversalsPerSecond() < 2.0)
                && (!finite(summary.getMedianSteadyDerivativeSpan()) || summary.getMedianSteadyDerivativeSpan() < 150.0);
        if (needsDamping) {
            double change = Math.min(10.0, settings.getMaximumDChangePercent());
            return new Adjustment(change,
                    "Repeated responses overshoot target while steady-state derivative activity remains quiet.",
                    "Higher D may add damping and reduce overshoot, but can amplify sensor noise if increased further.");
        }
        return new Adjustment(0.0,
                "Steady derivative activity and transient damping do not justify a conservative D change.",
                "D remains unchanged, so damping and noise sensitivity are not intentionally altered.");
    }

    private static String buildObservations(
            ResponseSummary response, List<DatasetEvent> transients, List<DatasetEvent> steady) {
        StringBuilder text = new StringBuilder();
        text.append("Model input: ").append(transients.size()).append(" compatible transient event(s), including ")
                .append(response.getGoodTransientCount()).append(" Good event(s), and ")
                .append(steady.size()).append(" compatible steady hold(s).\n");
        text.append("Transient medians: delay ").append(formatOrUnavailable(response.getMedianDelaySeconds(), " s"))
                .append("; settling ").append(formatOrUnavailable(response.getMedianSettlingSeconds(), " s"))
                .append("; opposite-side overshoot ").append(formatPercent(response.getMedianOppositeOvershootRatio()))
                .append("; decay ratio ").append(formatOrUnavailable(response.getMedianDecayRatio(), ""))
                .append("; residual oscillation ").append(formatOrUnavailable(response.getMedianOscillationHz(), " Hz"))
                .append("; consistency ").append(format(response.getTransientConsistencyPercent())).append("%.\n");
        text.append("Steady medians: signed error ").append(formatOrUnavailable(response.getMedianSteadySignedErrorRpm(), " RPM"))
                .append("; mean absolute error ").append(formatOrUnavailable(response.getMedianSteadyMeanAbsoluteErrorRpm(), " RPM"))
                .append("; RPM standard deviation ").append(formatOrUnavailable(response.getMedianSteadyRpmStandardDeviation(), " RPM"))
                .append("; correction reversals ").append(formatOrUnavailable(response.getMedianSteadyCorrectionReversalsPerSecond(), "/s"))
                .append("; D-term span ").append(formatOrUnavailable(response.getMedianSteadyDerivativeSpan(), ""))
                .append("; I-term span ").append(formatOrUnavailable(response.getMedianSteadyIntegralSpan(), ""))
                .append(".\n");
        text.append("These measurements are diagnostic inputs; proposed gains are shown only after both the dataset and confidence gates pass.");
        return text.toString();
    }

    private static double consistencyPercent(List<Double> delay, List<Double> settling, List<Double> overshoot) {
        double penalty = 0.0;
        penalty += Math.min(35.0, robustRelativeSpread(delay) * 35.0);
        penalty += Math.min(35.0, robustRelativeSpread(settling) * 35.0);
        penalty += Math.min(30.0, robustRelativeSpread(overshoot) * 30.0);
        return clamp(100.0 - penalty, 0.0, 100.0);
    }

    private static double robustRelativeSpread(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        double med = median(values);
        if (!finite(med)) return 1.0;
        List<Double> deviations = new ArrayList<Double>();
        for (Double value : values) deviations.add(Double.valueOf(Math.abs(value.doubleValue() - med)));
        double mad = median(deviations);
        return mad / Math.max(0.05, Math.abs(med));
    }

    private static double applyPercent(double value, double percent) {
        if (!finite(value)) return value;
        double changed = value * (1.0 + percent / 100.0);
        return Math.round(changed * 10000.0) / 10000.0;
    }

    private static double span(double minimum, double maximum) {
        return finite(minimum) && finite(maximum) ? Math.abs(maximum - minimum) : Double.NaN;
    }

    private static void addFinite(List<Double> values, double value) {
        if (finite(value)) values.add(Double.valueOf(value));
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> sorted = new ArrayList<Double>(values);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        if ((sorted.size() & 1) == 1) return sorted.get(middle).doubleValue();
        return (sorted.get(middle - 1).doubleValue() + sorted.get(middle).doubleValue()) / 2.0;
    }

    private static double mean(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        double sum = 0.0;
        for (Double value : values) sum += value.doubleValue();
        return sum / values.size();
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String format(double value) {
        if (!finite(value)) return "Unavailable";
        synchronized (ONE) { return ONE.format(value); }
    }

    private static String formatOrUnavailable(double value, String suffix) {
        return finite(value) ? format(value) + suffix : "Unavailable";
    }

    private static String formatPercent(double ratio) {
        return finite(ratio) ? format(ratio * 100.0) + "%" : "Unavailable";
    }

    private static final class EventResponse {
        private final double delaySeconds;
        private final double oppositeOvershootRatio;
        private final double decayRatio;
        private final double oscillationHz;

        private EventResponse(double delaySeconds, double oppositeOvershootRatio, double decayRatio, double oscillationHz) {
            this.delaySeconds = delaySeconds;
            this.oppositeOvershootRatio = oppositeOvershootRatio;
            this.decayRatio = decayRatio;
            this.oscillationHz = oscillationHz;
        }

        private static EventResponse empty() {
            return new EventResponse(Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        }
    }

    private static final class Adjustment {
        private final double percent;
        private final String reason;
        private final String effect;

        private Adjustment(double percent, String reason, String effect) {
            this.percent = percent;
            this.reason = reason;
            this.effect = effect;
        }
    }
}
