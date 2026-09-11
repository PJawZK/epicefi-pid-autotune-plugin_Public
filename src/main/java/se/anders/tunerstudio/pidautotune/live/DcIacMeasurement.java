package se.anders.tunerstudio.pidautotune.live;

/** Immutable M3 measurement result derived from one accepted M2 event. */
public final class DcIacMeasurement {
    public enum Validity { VALID, INVALID }

    private final int eventSequence;
    private final DcIacEvent.Type eventType;
    private final Validity validity;
    private final String resultCode;
    private final String detail;
    private final int sampleCount;
    private final double durationSeconds;
    private final double fromTarget;
    private final double toTarget;
    private final double sampleRateHz;
    private final double preStepActualSpan;
    private final double preStepSlopePerSecond;
    private final double initialActual;
    private final double finalActual;
    private final double responseDelaySeconds;
    private final double riseOrFallSeconds;
    private final double settlingSeconds;
    private final double overshootPercentOfStep;
    private final double steadyStateError;
    private final double meanAbsoluteError;
    private final double maximumAbsoluteError;
    private final double peakAbsoluteDuty;
    private final int dutyReversals;
    private final double pTermSpan;
    private final double iTermSpan;
    private final double dTermSpan;
    private final double batterySpread;
    private final double actualJitterStdDev;
    private final double dutyStdDev;
    private final double meanPidOutput;
    private final double meanITerm;

    // M3.3 evidence-quality context. These are observations/classification aids,
    // not tuning recommendations.
    private final double stepMagnitude;
    private final double operatingCenterTarget;
    private final double initialITerm;
    private final double targetSlopePerSecond;
    private final double actualSlopePerSecond;
    private final double iTermSlopePerSecond;
    private final double meanObservedFeedForward;
    private final double predictedCommandEdgeDuty;
    private final boolean predictedSaturationRisk;
    private final boolean dynamicQuantitative;
    private final boolean biasEquilibriumCandidate;
    private final String evidenceFlags;

    public DcIacMeasurement(
            int eventSequence,
            DcIacEvent.Type eventType,
            Validity validity,
            String resultCode,
            String detail,
            int sampleCount,
            double durationSeconds,
            double fromTarget,
            double toTarget,
            double sampleRateHz,
            double preStepActualSpan,
            double preStepSlopePerSecond,
            double initialActual,
            double finalActual,
            double responseDelaySeconds,
            double riseOrFallSeconds,
            double settlingSeconds,
            double overshootPercentOfStep,
            double steadyStateError,
            double meanAbsoluteError,
            double maximumAbsoluteError,
            double peakAbsoluteDuty,
            int dutyReversals,
            double pTermSpan,
            double iTermSpan,
            double dTermSpan,
            double batterySpread,
            double actualJitterStdDev,
            double dutyStdDev,
            double meanPidOutput,
            double meanITerm) {
        this(eventSequence, eventType, validity, resultCode, detail,
                sampleCount, durationSeconds, fromTarget, toTarget, sampleRateHz,
                preStepActualSpan, preStepSlopePerSecond, initialActual, finalActual,
                responseDelaySeconds, riseOrFallSeconds, settlingSeconds, overshootPercentOfStep,
                steadyStateError, meanAbsoluteError, maximumAbsoluteError, peakAbsoluteDuty,
                dutyReversals, pTermSpan, iTermSpan, dTermSpan, batterySpread,
                actualJitterStdDev, dutyStdDev, meanPidOutput, meanITerm,
                Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                false, false, false, "");
    }

    public DcIacMeasurement(
            int eventSequence,
            DcIacEvent.Type eventType,
            Validity validity,
            String resultCode,
            String detail,
            int sampleCount,
            double durationSeconds,
            double fromTarget,
            double toTarget,
            double sampleRateHz,
            double preStepActualSpan,
            double preStepSlopePerSecond,
            double initialActual,
            double finalActual,
            double responseDelaySeconds,
            double riseOrFallSeconds,
            double settlingSeconds,
            double overshootPercentOfStep,
            double steadyStateError,
            double meanAbsoluteError,
            double maximumAbsoluteError,
            double peakAbsoluteDuty,
            int dutyReversals,
            double pTermSpan,
            double iTermSpan,
            double dTermSpan,
            double batterySpread,
            double actualJitterStdDev,
            double dutyStdDev,
            double meanPidOutput,
            double meanITerm,
            double stepMagnitude,
            double operatingCenterTarget,
            double initialITerm,
            double targetSlopePerSecond,
            double actualSlopePerSecond,
            double iTermSlopePerSecond,
            double meanObservedFeedForward,
            double predictedCommandEdgeDuty,
            boolean predictedSaturationRisk,
            boolean dynamicQuantitative,
            boolean biasEquilibriumCandidate,
            String evidenceFlags) {
        this.eventSequence = eventSequence;
        this.eventType = eventType;
        this.validity = validity;
        this.resultCode = resultCode == null ? "" : resultCode;
        this.detail = detail == null ? "" : detail;
        this.sampleCount = sampleCount;
        this.durationSeconds = durationSeconds;
        this.fromTarget = fromTarget;
        this.toTarget = toTarget;
        this.sampleRateHz = sampleRateHz;
        this.preStepActualSpan = preStepActualSpan;
        this.preStepSlopePerSecond = preStepSlopePerSecond;
        this.initialActual = initialActual;
        this.finalActual = finalActual;
        this.responseDelaySeconds = responseDelaySeconds;
        this.riseOrFallSeconds = riseOrFallSeconds;
        this.settlingSeconds = settlingSeconds;
        this.overshootPercentOfStep = overshootPercentOfStep;
        this.steadyStateError = steadyStateError;
        this.meanAbsoluteError = meanAbsoluteError;
        this.maximumAbsoluteError = maximumAbsoluteError;
        this.peakAbsoluteDuty = peakAbsoluteDuty;
        this.dutyReversals = dutyReversals;
        this.pTermSpan = pTermSpan;
        this.iTermSpan = iTermSpan;
        this.dTermSpan = dTermSpan;
        this.batterySpread = batterySpread;
        this.actualJitterStdDev = actualJitterStdDev;
        this.dutyStdDev = dutyStdDev;
        this.meanPidOutput = meanPidOutput;
        this.meanITerm = meanITerm;
        this.stepMagnitude = stepMagnitude;
        this.operatingCenterTarget = operatingCenterTarget;
        this.initialITerm = initialITerm;
        this.targetSlopePerSecond = targetSlopePerSecond;
        this.actualSlopePerSecond = actualSlopePerSecond;
        this.iTermSlopePerSecond = iTermSlopePerSecond;
        this.meanObservedFeedForward = meanObservedFeedForward;
        this.predictedCommandEdgeDuty = predictedCommandEdgeDuty;
        this.predictedSaturationRisk = predictedSaturationRisk;
        this.dynamicQuantitative = dynamicQuantitative;
        this.biasEquilibriumCandidate = biasEquilibriumCandidate;
        this.evidenceFlags = evidenceFlags == null ? "" : evidenceFlags;
    }

    public int getEventSequence() { return eventSequence; }
    public DcIacEvent.Type getEventType() { return eventType; }
    public Validity getValidity() { return validity; }
    public boolean isValid() { return validity == Validity.VALID; }
    public String getResultCode() { return resultCode; }
    public String getDetail() { return detail; }
    public int getSampleCount() { return sampleCount; }
    public double getDurationSeconds() { return durationSeconds; }
    public double getFromTarget() { return fromTarget; }
    public double getToTarget() { return toTarget; }
    public double getSampleRateHz() { return sampleRateHz; }
    public double getPreStepActualSpan() { return preStepActualSpan; }
    public double getPreStepSlopePerSecond() { return preStepSlopePerSecond; }
    public double getInitialActual() { return initialActual; }
    public double getFinalActual() { return finalActual; }
    public double getResponseDelaySeconds() { return responseDelaySeconds; }
    public double getRiseOrFallSeconds() { return riseOrFallSeconds; }
    public double getSettlingSeconds() { return settlingSeconds; }
    public double getOvershootPercentOfStep() { return overshootPercentOfStep; }
    public double getSteadyStateError() { return steadyStateError; }
    public double getMeanAbsoluteError() { return meanAbsoluteError; }
    public double getMaximumAbsoluteError() { return maximumAbsoluteError; }
    public double getPeakAbsoluteDuty() { return peakAbsoluteDuty; }
    public int getDutyReversals() { return dutyReversals; }
    public double getPTermSpan() { return pTermSpan; }
    public double getITermSpan() { return iTermSpan; }
    public double getDTermSpan() { return dTermSpan; }
    public double getBatterySpread() { return batterySpread; }
    public double getActualJitterStdDev() { return actualJitterStdDev; }
    public double getDutyStdDev() { return dutyStdDev; }
    public double getMeanPidOutput() { return meanPidOutput; }
    public double getMeanITerm() { return meanITerm; }

    public double getStepMagnitude() { return stepMagnitude; }
    public double getOperatingCenterTarget() { return operatingCenterTarget; }
    public double getInitialITerm() { return initialITerm; }
    public double getTargetSlopePerSecond() { return targetSlopePerSecond; }
    public double getActualSlopePerSecond() { return actualSlopePerSecond; }
    public double getITermSlopePerSecond() { return iTermSlopePerSecond; }
    public double getMeanObservedFeedForward() { return meanObservedFeedForward; }
    public double getPredictedCommandEdgeDuty() { return predictedCommandEdgeDuty; }
    public boolean isPredictedSaturationRisk() { return predictedSaturationRisk; }
    public boolean isDynamicQuantitative() { return dynamicQuantitative; }
    public boolean isBiasEquilibriumCandidate() { return biasEquilibriumCandidate; }
    public String getEvidenceFlags() { return evidenceFlags; }
}
