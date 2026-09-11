package se.anders.tunerstudio.pidautotune.analysis;

/** One detected idle region, steady hold, or load transition and its read-only analysis metrics. */
public final class IdleEvent {
    private final String type;
    private final String analysisUse;
    private final int startIndex;
    private final int triggerIndex;
    private final int endIndex;
    private final double startSeconds;
    private final double triggerSeconds;
    private final double endSeconds;
    private final double targetRpm;
    private final double targetDriftRpm;
    private final double averageRpm;
    private final double coolantMedian;
    private final double coolantMinimum;
    private final double coolantMaximum;
    private final double meanSignedError;
    private final double meanAbsoluteError;
    private final double rpmStandardDeviation;
    private final double overshootRpm;
    private final double undershootRpm;
    private final double settlingSeconds;
    private final boolean settlingApplicable;
    private final double correctionMinimum;
    private final double correctionMaximum;
    private final double correctionLimitPercent;
    private final double correctionReversalsPerSecond;
    private final double commandedIdleMinimum;
    private final double commandedIdleMaximum;
    private final double baseIdleMinimum;
    private final double baseIdleMaximum;
    private final double pTermMinimum;
    private final double pTermMaximum;
    private final double iTermMinimum;
    private final double iTermMaximum;
    private final double dTermMinimum;
    private final double dTermMaximum;
    private final String fanState;
    private final String quality;
    private final String qualityDetails;

    public IdleEvent(
            String type,
            String analysisUse,
            int startIndex,
            int triggerIndex,
            int endIndex,
            double startSeconds,
            double triggerSeconds,
            double endSeconds,
            double targetRpm,
            double targetDriftRpm,
            double averageRpm,
            double coolantMedian,
            double coolantMinimum,
            double coolantMaximum,
            double meanSignedError,
            double meanAbsoluteError,
            double rpmStandardDeviation,
            double overshootRpm,
            double undershootRpm,
            double settlingSeconds,
            boolean settlingApplicable,
            double correctionMinimum,
            double correctionMaximum,
            double correctionLimitPercent,
            double correctionReversalsPerSecond,
            double commandedIdleMinimum,
            double commandedIdleMaximum,
            double baseIdleMinimum,
            double baseIdleMaximum,
            double pTermMinimum,
            double pTermMaximum,
            double iTermMinimum,
            double iTermMaximum,
            double dTermMinimum,
            double dTermMaximum,
            String fanState,
            String quality,
            String qualityDetails) {
        this.type = safe(type);
        this.analysisUse = safe(analysisUse);
        this.startIndex = startIndex;
        this.triggerIndex = triggerIndex;
        this.endIndex = endIndex;
        this.startSeconds = startSeconds;
        this.triggerSeconds = triggerSeconds;
        this.endSeconds = endSeconds;
        this.targetRpm = targetRpm;
        this.targetDriftRpm = targetDriftRpm;
        this.averageRpm = averageRpm;
        this.coolantMedian = coolantMedian;
        this.coolantMinimum = coolantMinimum;
        this.coolantMaximum = coolantMaximum;
        this.meanSignedError = meanSignedError;
        this.meanAbsoluteError = meanAbsoluteError;
        this.rpmStandardDeviation = rpmStandardDeviation;
        this.overshootRpm = overshootRpm;
        this.undershootRpm = undershootRpm;
        this.settlingSeconds = settlingSeconds;
        this.settlingApplicable = settlingApplicable;
        this.correctionMinimum = correctionMinimum;
        this.correctionMaximum = correctionMaximum;
        this.correctionLimitPercent = correctionLimitPercent;
        this.correctionReversalsPerSecond = correctionReversalsPerSecond;
        this.commandedIdleMinimum = commandedIdleMinimum;
        this.commandedIdleMaximum = commandedIdleMaximum;
        this.baseIdleMinimum = baseIdleMinimum;
        this.baseIdleMaximum = baseIdleMaximum;
        this.pTermMinimum = pTermMinimum;
        this.pTermMaximum = pTermMaximum;
        this.iTermMinimum = iTermMinimum;
        this.iTermMaximum = iTermMaximum;
        this.dTermMinimum = dTermMinimum;
        this.dTermMaximum = dTermMaximum;
        this.fanState = safe(fanState);
        this.quality = safe(quality);
        this.qualityDetails = safe(qualityDetails);
    }

    public String getType() { return type; }
    public String getAnalysisUse() { return analysisUse; }
    public int getStartIndex() { return startIndex; }
    public int getTriggerIndex() { return triggerIndex; }
    public int getEndIndex() { return endIndex; }
    public double getStartSeconds() { return startSeconds; }
    public double getTriggerSeconds() { return triggerSeconds; }
    public double getEndSeconds() { return endSeconds; }
    public double getDurationSeconds() { return Math.max(0.0, endSeconds - startSeconds); }
    public double getEvaluationDurationSeconds() { return Math.max(0.0, endSeconds - triggerSeconds); }
    public double getTargetRpm() { return targetRpm; }
    public double getTargetDriftRpm() { return targetDriftRpm; }
    public double getAverageRpm() { return averageRpm; }
    public double getCoolantMedian() { return coolantMedian; }
    public double getCoolantMinimum() { return coolantMinimum; }
    public double getCoolantMaximum() { return coolantMaximum; }
    public double getMeanSignedError() { return meanSignedError; }
    public double getMeanAbsoluteError() { return meanAbsoluteError; }
    public double getRpmStandardDeviation() { return rpmStandardDeviation; }
    public double getOvershootRpm() { return overshootRpm; }
    public double getUndershootRpm() { return undershootRpm; }
    public double getSettlingSeconds() { return settlingSeconds; }
    public boolean isSettlingApplicable() { return settlingApplicable; }
    public double getCorrectionMinimum() { return correctionMinimum; }
    public double getCorrectionMaximum() { return correctionMaximum; }
    public double getCorrectionLimitPercent() { return correctionLimitPercent; }
    public double getCorrectionReversalsPerSecond() { return correctionReversalsPerSecond; }
    public double getCommandedIdleMinimum() { return commandedIdleMinimum; }
    public double getCommandedIdleMaximum() { return commandedIdleMaximum; }
    public double getBaseIdleMinimum() { return baseIdleMinimum; }
    public double getBaseIdleMaximum() { return baseIdleMaximum; }
    public double getPTermMinimum() { return pTermMinimum; }
    public double getPTermMaximum() { return pTermMaximum; }
    public double getITermMinimum() { return iTermMinimum; }
    public double getITermMaximum() { return iTermMaximum; }
    public double getDTermMinimum() { return dTermMinimum; }
    public double getDTermMaximum() { return dTermMaximum; }
    public String getFanState() { return fanState; }
    public String getQuality() { return quality; }
    public String getQualityDetails() { return qualityDetails; }

    public boolean isSteadyHold() {
        return "Steady-state diagnostics".equals(analysisUse);
    }

    public boolean isFutureGainEligible() {
        return "Transient response".equals(analysisUse)
                && ("Good for analysis".equals(quality) || "Usable".equals(quality));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
