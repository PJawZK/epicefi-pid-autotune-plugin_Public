package se.anders.tunerstudio.pidautotune.live;

/** One non-overlapping rolling window used for static DC-IAC bias characterization. */
public final class DcIacBiasEvidence {
    public static final String QUALITY_GOOD = "GOOD";
    public static final String QUALITY_OK = "OK";
    public static final String QUALITY_LOW = "LOW";

    private final int sequence;
    private final double startSeconds;
    private final double endSeconds;
    private final int sampleCount;
    private final double sampleRateHz;
    private final double target;
    private final double actual;
    private final double steadyError;
    private final double meanDuty;
    private final double meanPidOutput;
    private final double meanITerm;
    private final double configuredBias;
    private final double observedFeedForward;
    private final double requiredBias;
    private final double correction;
    private final double targetSlope;
    private final double actualSlope;
    private final double iTermSlope;
    private final double actualSpan;
    private final double batterySpread;
    private final String curveSegment;
    private final String quality;
    private final boolean recommendationEligible;
    private final String qualityDetail;

    /** Legacy constructor: previously every emitted window was implicitly fully qualified. */
    public DcIacBiasEvidence(
            int sequence,
            double startSeconds,
            double endSeconds,
            int sampleCount,
            double sampleRateHz,
            double target,
            double actual,
            double steadyError,
            double meanDuty,
            double meanPidOutput,
            double meanITerm,
            double configuredBias,
            double observedFeedForward,
            double requiredBias,
            double correction,
            double targetSlope,
            double actualSlope,
            double iTermSlope,
            double actualSpan,
            double batterySpread,
            String curveSegment) {
        this(sequence, startSeconds, endSeconds, sampleCount, sampleRateHz,
                target, actual, steadyError, meanDuty, meanPidOutput, meanITerm,
                configuredBias, observedFeedForward, requiredBias, correction,
                targetSlope, actualSlope, iTermSlope, actualSpan, batterySpread,
                curveSegment, QUALITY_GOOD, true, "Legacy qualified equilibrium window");
    }

    public DcIacBiasEvidence(
            int sequence,
            double startSeconds,
            double endSeconds,
            int sampleCount,
            double sampleRateHz,
            double target,
            double actual,
            double steadyError,
            double meanDuty,
            double meanPidOutput,
            double meanITerm,
            double configuredBias,
            double observedFeedForward,
            double requiredBias,
            double correction,
            double targetSlope,
            double actualSlope,
            double iTermSlope,
            double actualSpan,
            double batterySpread,
            String curveSegment,
            String quality,
            boolean recommendationEligible,
            String qualityDetail) {
        this.sequence = sequence;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.sampleCount = sampleCount;
        this.sampleRateHz = sampleRateHz;
        this.target = target;
        this.actual = actual;
        this.steadyError = steadyError;
        this.meanDuty = meanDuty;
        this.meanPidOutput = meanPidOutput;
        this.meanITerm = meanITerm;
        this.configuredBias = configuredBias;
        this.observedFeedForward = observedFeedForward;
        this.requiredBias = requiredBias;
        this.correction = correction;
        this.targetSlope = targetSlope;
        this.actualSlope = actualSlope;
        this.iTermSlope = iTermSlope;
        this.actualSpan = actualSpan;
        this.batterySpread = batterySpread;
        this.curveSegment = curveSegment == null ? "" : curveSegment;
        this.quality = normalizeQuality(quality);
        this.recommendationEligible = recommendationEligible;
        this.qualityDetail = qualityDetail == null ? "" : qualityDetail;
    }

    private static String normalizeQuality(String value) {
        if (QUALITY_GOOD.equalsIgnoreCase(value)) return QUALITY_GOOD;
        if (QUALITY_OK.equalsIgnoreCase(value)) return QUALITY_OK;
        return QUALITY_LOW;
    }

    public int getSequence() { return sequence; }
    public double getStartSeconds() { return startSeconds; }
    public double getEndSeconds() { return endSeconds; }
    public double getDurationSeconds() { return endSeconds - startSeconds; }
    public int getSampleCount() { return sampleCount; }
    public double getSampleRateHz() { return sampleRateHz; }
    public double getTarget() { return target; }
    public double getActual() { return actual; }
    public double getSteadyError() { return steadyError; }
    public double getMeanDuty() { return meanDuty; }
    public double getMeanPidOutput() { return meanPidOutput; }
    public double getMeanITerm() { return meanITerm; }
    public double getConfiguredBias() { return configuredBias; }
    public double getObservedFeedForward() { return observedFeedForward; }
    public double getRequiredBias() { return requiredBias; }
    public double getCorrection() { return correction; }
    public double getTargetSlope() { return targetSlope; }
    public double getActualSlope() { return actualSlope; }
    public double getITermSlope() { return iTermSlope; }
    public double getActualSpan() { return actualSpan; }
    public double getBatterySpread() { return batterySpread; }
    public String getCurveSegment() { return curveSegment; }
    public String getQuality() { return quality; }
    public String getQualityDetail() { return qualityDetail; }
    public boolean isRecommendationEligible() { return recommendationEligible; }
    public boolean isGoodQuality() { return QUALITY_GOOD.equals(quality); }
    public boolean isOkQuality() { return QUALITY_OK.equals(quality); }
    public boolean isLowQuality() { return QUALITY_LOW.equals(quality); }
}
