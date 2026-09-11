package se.anders.tunerstudio.pidautotune.live;

/** Conservative measurement-only thresholds and read-only controller context for DC-IAC M3. */
public final class DcIacMeasurementSettings {
    private final double preStepWindowSeconds = 0.40;
    private final double minimumPreStepDurationSeconds = 0.25;
    private final double maximumPreStepActualSpan = 1.50;
    private final double maximumPreStepSlopePerSecond = 2.50;
    private final double minimumResponseFraction = 0.20;
    private final double delayFraction = 0.05;
    private final double riseLowFraction = 0.10;
    private final double riseHighFraction = 0.90;
    private final double settlingAbsoluteTolerance = 0.50;
    private final double settlingFractionOfStep = 0.05;
    private final double settlingMinimumRemainingSeconds = 0.30;
    private final double steadyWindowSeconds = 0.40;
    private final double dutyReversalDeadband = 3.0;

    // Require roughly three distinct source-update intervals across 10-90 response
    // before treating fast transient timing/peak/span metrics as quantitatively resolved.
    private final double timingResolutionMultiplier = 3.0;
    private final int minimumSamples = 20;

    // M3.3 evidence-quality rules derived from real Mega144H7 DC-IAC validation.
    // These classify evidence only; they do not recommend settings.
    private final double maximumQuantitativeStepSize = 4.50;
    private final double physicalMotorDutyLimitPercent = 90.0;
    private final double nominalControllerLoopSeconds = 0.002; // DC-motor loop nominally 500 Hz.

    // A normal two-second M2 hold can become only an equilibrium candidate.
    // Final static-bias learning must later aggregate longer/repeated evidence.
    private final double minimumEquilibriumCandidateDurationSeconds = 1.80;
    private final double maximumEquilibriumTargetSlopePerSecond = 0.05;
    private final double maximumEquilibriumActualSlopePerSecond = 0.10;
    private final double maximumEquilibriumITermSlopePerSecond = 0.10;
    private final double maximumEquilibriumActualSpan = 0.50;

    private final double pFactor;
    private final double iFactor;
    private final double dFactor;

    public DcIacMeasurementSettings() {
        this(Double.NaN, Double.NaN, Double.NaN);
    }

    public DcIacMeasurementSettings(double pFactor, double iFactor, double dFactor) {
        this.pFactor = pFactor;
        this.iFactor = iFactor;
        this.dFactor = dFactor;
    }

    public double getPreStepWindowSeconds() { return preStepWindowSeconds; }
    public double getMinimumPreStepDurationSeconds() { return minimumPreStepDurationSeconds; }
    public double getMaximumPreStepActualSpan() { return maximumPreStepActualSpan; }
    public double getMaximumPreStepSlopePerSecond() { return maximumPreStepSlopePerSecond; }
    public double getMinimumResponseFraction() { return minimumResponseFraction; }
    public double getDelayFraction() { return delayFraction; }
    public double getRiseLowFraction() { return riseLowFraction; }
    public double getRiseHighFraction() { return riseHighFraction; }
    public double getSettlingAbsoluteTolerance() { return settlingAbsoluteTolerance; }
    public double getSettlingFractionOfStep() { return settlingFractionOfStep; }
    public double getSettlingMinimumRemainingSeconds() { return settlingMinimumRemainingSeconds; }
    public double getSteadyWindowSeconds() { return steadyWindowSeconds; }
    public double getDutyReversalDeadband() { return dutyReversalDeadband; }
    public double getTimingResolutionMultiplier() { return timingResolutionMultiplier; }
    public int getMinimumSamples() { return minimumSamples; }

    public double getMaximumQuantitativeStepSize() { return maximumQuantitativeStepSize; }
    public double getPhysicalMotorDutyLimitPercent() { return physicalMotorDutyLimitPercent; }
    public double getNominalControllerLoopSeconds() { return nominalControllerLoopSeconds; }
    public double getMinimumEquilibriumCandidateDurationSeconds() { return minimumEquilibriumCandidateDurationSeconds; }
    public double getMaximumEquilibriumTargetSlopePerSecond() { return maximumEquilibriumTargetSlopePerSecond; }
    public double getMaximumEquilibriumActualSlopePerSecond() { return maximumEquilibriumActualSlopePerSecond; }
    public double getMaximumEquilibriumITermSlopePerSecond() { return maximumEquilibriumITermSlopePerSecond; }
    public double getMaximumEquilibriumActualSpan() { return maximumEquilibriumActualSpan; }

    public boolean arePidGainsKnown() {
        return finite(pFactor) && finite(iFactor) && finite(dFactor);
    }

    public double getPFactor() { return pFactor; }
    public double getIFactor() { return iFactor; }
    public double getDFactor() { return dFactor; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
