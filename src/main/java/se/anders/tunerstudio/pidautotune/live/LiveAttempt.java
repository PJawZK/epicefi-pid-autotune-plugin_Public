package se.anders.tunerstudio.pidautotune.live;

/** Completed or rejected live return-to-idle attempt. */
public final class LiveAttempt {
    private final int number;
    private final boolean accepted;
    private final String quality;
    private final String resultCode;
    private final String reason;
    private final double triggerSeconds;
    private final double peakRpm;
    private final double targetRpm;
    private final double settlingSeconds;
    private final double overshootRpm;
    private final double undershootRpm;
    private final double meanSignedError;
    private final double meanAbsoluteError;
    private final double rpmStandardDeviation;
    private final double correctionLimitPercent;
    private final double correctionReversalsPerSecond;
    private final double pTermSpan;
    private final double iTermSpan;
    private final double dTermSpan;
    private final double meanCoolant;
    private final boolean fanOn;
    private final double stableObservationSeconds;

    public LiveAttempt(
            int number,
            boolean accepted,
            String quality,
            String resultCode,
            String reason,
            double triggerSeconds,
            double peakRpm,
            double targetRpm,
            double settlingSeconds,
            double overshootRpm,
            double undershootRpm,
            double meanSignedError,
            double meanAbsoluteError,
            double rpmStandardDeviation,
            double correctionLimitPercent,
            double correctionReversalsPerSecond,
            double pTermSpan,
            double iTermSpan,
            double dTermSpan,
            double meanCoolant,
            boolean fanOn,
            double stableObservationSeconds) {
        this.number = number;
        this.accepted = accepted;
        this.quality = safe(quality);
        this.resultCode = safe(resultCode);
        this.reason = safe(reason);
        this.triggerSeconds = triggerSeconds;
        this.peakRpm = peakRpm;
        this.targetRpm = targetRpm;
        this.settlingSeconds = settlingSeconds;
        this.overshootRpm = overshootRpm;
        this.undershootRpm = undershootRpm;
        this.meanSignedError = meanSignedError;
        this.meanAbsoluteError = meanAbsoluteError;
        this.rpmStandardDeviation = rpmStandardDeviation;
        this.correctionLimitPercent = correctionLimitPercent;
        this.correctionReversalsPerSecond = correctionReversalsPerSecond;
        this.pTermSpan = pTermSpan;
        this.iTermSpan = iTermSpan;
        this.dTermSpan = dTermSpan;
        this.meanCoolant = meanCoolant;
        this.fanOn = fanOn;
        this.stableObservationSeconds = stableObservationSeconds;
    }

    public int getNumber() { return number; }
    public boolean isAccepted() { return accepted; }
    public String getQuality() { return quality; }
    public String getResultCode() { return resultCode; }
    public String getReason() { return reason; }
    public double getTriggerSeconds() { return triggerSeconds; }
    public double getPeakRpm() { return peakRpm; }
    public double getTargetRpm() { return targetRpm; }
    public double getSettlingSeconds() { return settlingSeconds; }
    public double getOvershootRpm() { return overshootRpm; }
    public double getUndershootRpm() { return undershootRpm; }
    public double getMeanSignedError() { return meanSignedError; }
    public double getMeanAbsoluteError() { return meanAbsoluteError; }
    public double getRpmStandardDeviation() { return rpmStandardDeviation; }
    public double getCorrectionLimitPercent() { return correctionLimitPercent; }
    public double getCorrectionReversalsPerSecond() { return correctionReversalsPerSecond; }
    public double getPTermSpan() { return pTermSpan; }
    public double getITermSpan() { return iTermSpan; }
    public double getDTermSpan() { return dTermSpan; }
    public double getMeanCoolant() { return meanCoolant; }
    public boolean isFanOn() { return fanOn; }
    public double getStableObservationSeconds() { return stableObservationSeconds; }

    private static String safe(String value) { return value == null ? "" : value; }
}
