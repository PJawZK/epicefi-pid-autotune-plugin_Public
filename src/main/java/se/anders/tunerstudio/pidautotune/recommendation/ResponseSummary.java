package se.anders.tunerstudio.pidautotune.recommendation;

/** Aggregate response and steady-state measurements used by the conservative model. */
public final class ResponseSummary {
    private final int transientCount;
    private final int goodTransientCount;
    private final int steadyCount;
    private final double medianDelaySeconds;
    private final double medianSettlingSeconds;
    private final double medianOppositeOvershootRatio;
    private final double medianDecayRatio;
    private final double medianOscillationHz;
    private final double transientConsistencyPercent;
    private final double medianTransientLimitPercent;
    private final double medianSteadySignedErrorRpm;
    private final double medianSteadyMeanAbsoluteErrorRpm;
    private final double medianSteadyRpmStandardDeviation;
    private final double medianSteadyCorrectionReversalsPerSecond;
    private final double medianSteadyDerivativeSpan;
    private final double medianSteadyIntegralSpan;

    public ResponseSummary(
            int transientCount,
            int goodTransientCount,
            int steadyCount,
            double medianDelaySeconds,
            double medianSettlingSeconds,
            double medianOppositeOvershootRatio,
            double medianDecayRatio,
            double medianOscillationHz,
            double transientConsistencyPercent,
            double medianTransientLimitPercent,
            double medianSteadySignedErrorRpm,
            double medianSteadyMeanAbsoluteErrorRpm,
            double medianSteadyRpmStandardDeviation,
            double medianSteadyCorrectionReversalsPerSecond,
            double medianSteadyDerivativeSpan,
            double medianSteadyIntegralSpan) {
        this.transientCount = transientCount;
        this.goodTransientCount = goodTransientCount;
        this.steadyCount = steadyCount;
        this.medianDelaySeconds = medianDelaySeconds;
        this.medianSettlingSeconds = medianSettlingSeconds;
        this.medianOppositeOvershootRatio = medianOppositeOvershootRatio;
        this.medianDecayRatio = medianDecayRatio;
        this.medianOscillationHz = medianOscillationHz;
        this.transientConsistencyPercent = transientConsistencyPercent;
        this.medianTransientLimitPercent = medianTransientLimitPercent;
        this.medianSteadySignedErrorRpm = medianSteadySignedErrorRpm;
        this.medianSteadyMeanAbsoluteErrorRpm = medianSteadyMeanAbsoluteErrorRpm;
        this.medianSteadyRpmStandardDeviation = medianSteadyRpmStandardDeviation;
        this.medianSteadyCorrectionReversalsPerSecond = medianSteadyCorrectionReversalsPerSecond;
        this.medianSteadyDerivativeSpan = medianSteadyDerivativeSpan;
        this.medianSteadyIntegralSpan = medianSteadyIntegralSpan;
    }

    public static ResponseSummary empty() {
        return new ResponseSummary(0, 0, 0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    public int getTransientCount() { return transientCount; }
    public int getGoodTransientCount() { return goodTransientCount; }
    public int getSteadyCount() { return steadyCount; }
    public double getMedianDelaySeconds() { return medianDelaySeconds; }
    public double getMedianSettlingSeconds() { return medianSettlingSeconds; }
    public double getMedianOppositeOvershootRatio() { return medianOppositeOvershootRatio; }
    public double getMedianDecayRatio() { return medianDecayRatio; }
    public double getMedianOscillationHz() { return medianOscillationHz; }
    public double getTransientConsistencyPercent() { return transientConsistencyPercent; }
    public double getMedianTransientLimitPercent() { return medianTransientLimitPercent; }
    public double getMedianSteadySignedErrorRpm() { return medianSteadySignedErrorRpm; }
    public double getMedianSteadyMeanAbsoluteErrorRpm() { return medianSteadyMeanAbsoluteErrorRpm; }
    public double getMedianSteadyRpmStandardDeviation() { return medianSteadyRpmStandardDeviation; }
    public double getMedianSteadyCorrectionReversalsPerSecond() { return medianSteadyCorrectionReversalsPerSecond; }
    public double getMedianSteadyDerivativeSpan() { return medianSteadyDerivativeSpan; }
    public double getMedianSteadyIntegralSpan() { return medianSteadyIntegralSpan; }
}
