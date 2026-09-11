package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Aggregate measurements from the accepted attempts in one live capture session. */
public final class LiveSessionSummary {
    private final int acceptedCount;
    private final int goodCount;
    private final double medianTargetRpm;
    private final double medianCoolant;
    private final boolean fanOn;
    private final double medianSettlingSeconds;
    private final double medianOvershootRpm;
    private final double medianUndershootRpm;
    private final double medianSignedErrorRpm;
    private final double medianMeanAbsoluteErrorRpm;
    private final double medianRpmStandardDeviation;
    private final double medianCorrectionLimitPercent;
    private final double medianCorrectionReversalsPerSecond;
    private final double medianPTermSpan;
    private final double medianITermSpan;
    private final double medianDTermSpan;
    private final double repeatabilityPercent;

    public LiveSessionSummary(
            int acceptedCount,
            int goodCount,
            double medianTargetRpm,
            double medianCoolant,
            boolean fanOn,
            double medianSettlingSeconds,
            double medianOvershootRpm,
            double medianUndershootRpm,
            double medianSignedErrorRpm,
            double medianMeanAbsoluteErrorRpm,
            double medianRpmStandardDeviation,
            double medianCorrectionLimitPercent,
            double medianCorrectionReversalsPerSecond,
            double medianPTermSpan,
            double medianITermSpan,
            double medianDTermSpan,
            double repeatabilityPercent) {
        this.acceptedCount = acceptedCount;
        this.goodCount = goodCount;
        this.medianTargetRpm = medianTargetRpm;
        this.medianCoolant = medianCoolant;
        this.fanOn = fanOn;
        this.medianSettlingSeconds = medianSettlingSeconds;
        this.medianOvershootRpm = medianOvershootRpm;
        this.medianUndershootRpm = medianUndershootRpm;
        this.medianSignedErrorRpm = medianSignedErrorRpm;
        this.medianMeanAbsoluteErrorRpm = medianMeanAbsoluteErrorRpm;
        this.medianRpmStandardDeviation = medianRpmStandardDeviation;
        this.medianCorrectionLimitPercent = medianCorrectionLimitPercent;
        this.medianCorrectionReversalsPerSecond = medianCorrectionReversalsPerSecond;
        this.medianPTermSpan = medianPTermSpan;
        this.medianITermSpan = medianITermSpan;
        this.medianDTermSpan = medianDTermSpan;
        this.repeatabilityPercent = repeatabilityPercent;
    }

    public static LiveSessionSummary from(List<LiveAttempt> attempts) {
        List<Double> target = new ArrayList<Double>();
        List<Double> coolant = new ArrayList<Double>();
        List<Double> settling = new ArrayList<Double>();
        List<Double> overshoot = new ArrayList<Double>();
        List<Double> undershoot = new ArrayList<Double>();
        List<Double> signed = new ArrayList<Double>();
        List<Double> mae = new ArrayList<Double>();
        List<Double> deviation = new ArrayList<Double>();
        List<Double> limit = new ArrayList<Double>();
        List<Double> reversals = new ArrayList<Double>();
        List<Double> pSpan = new ArrayList<Double>();
        List<Double> iSpan = new ArrayList<Double>();
        List<Double> dSpan = new ArrayList<Double>();
        int accepted = 0;
        int good = 0;
        int fanOnCount = 0;
        if (attempts != null) {
            for (LiveAttempt attempt : attempts) {
                if (attempt == null || !attempt.isAccepted()) continue;
                accepted++;
                if (attempt.getQuality().contains("Good for analysis")) good++;
                if (attempt.isFanOn()) fanOnCount++;
                add(target, attempt.getTargetRpm());
                add(coolant, attempt.getMeanCoolant());
                add(settling, attempt.getSettlingSeconds());
                add(overshoot, attempt.getOvershootRpm());
                add(undershoot, attempt.getUndershootRpm());
                add(signed, attempt.getMeanSignedError());
                add(mae, attempt.getMeanAbsoluteError());
                add(deviation, attempt.getRpmStandardDeviation());
                add(limit, attempt.getCorrectionLimitPercent());
                add(reversals, attempt.getCorrectionReversalsPerSecond());
                add(pSpan, attempt.getPTermSpan());
                add(iSpan, attempt.getITermSpan());
                add(dSpan, attempt.getDTermSpan());
            }
        }
        double repeatability = repeatability(settling, mae, overshoot, undershoot);
        return new LiveSessionSummary(
                accepted, good, median(target), median(coolant), fanOnCount > accepted / 2,
                median(settling), median(overshoot), median(undershoot), median(signed),
                median(mae), median(deviation), median(limit), median(reversals),
                median(pSpan), median(iSpan), median(dSpan), repeatability);
    }

    public static LiveSessionSummary empty() {
        return new LiveSessionSummary(0, 0, Double.NaN, Double.NaN, false,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, 0.0);
    }

    public int getAcceptedCount() { return acceptedCount; }
    public int getGoodCount() { return goodCount; }
    public double getMedianTargetRpm() { return medianTargetRpm; }
    public double getMedianCoolant() { return medianCoolant; }
    public boolean isFanOn() { return fanOn; }
    public double getMedianSettlingSeconds() { return medianSettlingSeconds; }
    public double getMedianOvershootRpm() { return medianOvershootRpm; }
    public double getMedianUndershootRpm() { return medianUndershootRpm; }
    public double getMedianSignedErrorRpm() { return medianSignedErrorRpm; }
    public double getMedianMeanAbsoluteErrorRpm() { return medianMeanAbsoluteErrorRpm; }
    public double getMedianRpmStandardDeviation() { return medianRpmStandardDeviation; }
    public double getMedianCorrectionLimitPercent() { return medianCorrectionLimitPercent; }
    public double getMedianCorrectionReversalsPerSecond() { return medianCorrectionReversalsPerSecond; }
    public double getMedianPTermSpan() { return medianPTermSpan; }
    public double getMedianITermSpan() { return medianITermSpan; }
    public double getMedianDTermSpan() { return medianDTermSpan; }
    public double getRepeatabilityPercent() { return repeatabilityPercent; }

    public boolean hasMinimumExperimentalData() {
        return acceptedCount >= 2 && goodCount >= 1;
    }

    private static double repeatability(List<Double> settling, List<Double> mae,
                                        List<Double> overshoot, List<Double> undershoot) {
        double penalty = 0.0;
        penalty += Math.min(30.0, relativeSpread(settling) * 30.0);
        penalty += Math.min(30.0, relativeSpread(mae) * 30.0);
        penalty += Math.min(20.0, relativeSpread(overshoot) * 20.0);
        penalty += Math.min(20.0, relativeSpread(undershoot) * 20.0);
        return Math.max(0.0, Math.min(100.0, 100.0 - penalty));
    }

    private static double relativeSpread(List<Double> values) {
        if (values == null || values.size() < 2) return 0.0;
        double med = median(values);
        if (!finite(med)) return 1.0;
        List<Double> deviation = new ArrayList<Double>();
        for (Double value : values) deviation.add(Double.valueOf(Math.abs(value.doubleValue() - med)));
        return median(deviation) / Math.max(1.0, Math.abs(med));
    }

    private static void add(List<Double> values, double value) {
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

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
