package se.anders.tunerstudio.pidautotune.live;

/** Conservative read-only M4A thresholds for static DC-IAC bias characterization. */
public final class DcIacBiasCharacterizationSettings {
    private final double windowSeconds = 5.0;
    private final double minimumWindowDurationSeconds = 4.8;
    private final int minimumDistinctSamples = 80;
    private final double maximumTargetSpan = 0.50;
    private final double maximumTargetSlopePerSecond = 0.05;
    private final double maximumActualSpan = 0.50;
    private final double maximumActualSlopePerSecond = 0.10;
    private final double maximumITermSlopePerSecond = 0.10;
    private final double maximumAbsoluteSteadyError = 0.30;
    private final double maximumBatterySpread = 0.25;
    private final double maximumFeedForwardMappingError = 0.50;
    private final double integralLimitMargin = 2.0;
    private final double localGroupingTolerance = 1.50;
    private final int minimumEvidenceWindowsPerRecommendation = 2;
    private final double maximumCorrectionMad = 0.75;
    private final double minimumActionableCorrection = 0.50;
    private final double maximumCorrectionPerPass = 6.0;

    public double getWindowSeconds() { return windowSeconds; }
    public double getMinimumWindowDurationSeconds() { return minimumWindowDurationSeconds; }
    public int getMinimumDistinctSamples() { return minimumDistinctSamples; }
    public double getMaximumTargetSpan() { return maximumTargetSpan; }
    public double getMaximumTargetSlopePerSecond() { return maximumTargetSlopePerSecond; }
    public double getMaximumActualSpan() { return maximumActualSpan; }
    public double getMaximumActualSlopePerSecond() { return maximumActualSlopePerSecond; }
    public double getMaximumITermSlopePerSecond() { return maximumITermSlopePerSecond; }
    public double getMaximumAbsoluteSteadyError() { return maximumAbsoluteSteadyError; }
    public double getMaximumBatterySpread() { return maximumBatterySpread; }
    public double getMaximumFeedForwardMappingError() { return maximumFeedForwardMappingError; }
    public double getIntegralLimitMargin() { return integralLimitMargin; }
    public double getLocalGroupingTolerance() { return localGroupingTolerance; }
    public int getMinimumEvidenceWindowsPerRecommendation() { return minimumEvidenceWindowsPerRecommendation; }
    public double getMaximumCorrectionMad() { return maximumCorrectionMad; }
    public double getMinimumActionableCorrection() { return minimumActionableCorrection; }
    public double getMaximumCorrectionPerPass() { return maximumCorrectionPerPass; }
}
