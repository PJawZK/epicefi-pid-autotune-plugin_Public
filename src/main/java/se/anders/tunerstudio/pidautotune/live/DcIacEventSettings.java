package se.anders.tunerstudio.pidautotune.live;

/** Conservative M2 event-extraction thresholds. No recommendation thresholds live here. */
public final class DcIacEventSettings {
    private final double minimumTarget;
    private final double maximumTarget;
    private final double channelFreshnessSeconds;
    private final double minimumBatteryVoltage;
    private final double maximumBatteryVoltage;
    private final double maximumBatterySpread;
    private final double targetLimitMargin;
    private final double targetTolerance;
    private final double minimumStepSize;
    private final double preStepStableSeconds;
    private final double stepConfirmationSeconds;
    private final double stepCaptureSeconds;
    private final double stableHoldSeconds;
    private final double maximumStableHoldError;
    private final double saturationDutyPercent;

    public DcIacEventSettings(double minimumTarget, double maximumTarget) {
        this(minimumTarget, maximumTarget,
                1.0, 10.0, 16.5, 0.75,
                3.0, 0.50, 3.0,
                0.50, 0.15, 2.0,
                2.0, 2.0, 90.0);
    }

    public DcIacEventSettings(
            double minimumTarget,
            double maximumTarget,
            double channelFreshnessSeconds,
            double minimumBatteryVoltage,
            double maximumBatteryVoltage,
            double maximumBatterySpread,
            double targetLimitMargin,
            double targetTolerance,
            double minimumStepSize,
            double preStepStableSeconds,
            double stepConfirmationSeconds,
            double stepCaptureSeconds,
            double stableHoldSeconds,
            double maximumStableHoldError,
            double saturationDutyPercent) {
        this.minimumTarget = minimumTarget;
        this.maximumTarget = maximumTarget;
        this.channelFreshnessSeconds = channelFreshnessSeconds;
        this.minimumBatteryVoltage = minimumBatteryVoltage;
        this.maximumBatteryVoltage = maximumBatteryVoltage;
        this.maximumBatterySpread = maximumBatterySpread;
        this.targetLimitMargin = targetLimitMargin;
        this.targetTolerance = targetTolerance;
        this.minimumStepSize = minimumStepSize;
        this.preStepStableSeconds = preStepStableSeconds;
        this.stepConfirmationSeconds = stepConfirmationSeconds;
        this.stepCaptureSeconds = stepCaptureSeconds;
        this.stableHoldSeconds = stableHoldSeconds;
        this.maximumStableHoldError = maximumStableHoldError;
        this.saturationDutyPercent = saturationDutyPercent;
    }

    public double getMinimumTarget() { return minimumTarget; }
    public double getMaximumTarget() { return maximumTarget; }
    public double getChannelFreshnessSeconds() { return channelFreshnessSeconds; }
    public double getMinimumBatteryVoltage() { return minimumBatteryVoltage; }
    public double getMaximumBatteryVoltage() { return maximumBatteryVoltage; }
    public double getMaximumBatterySpread() { return maximumBatterySpread; }
    public double getTargetLimitMargin() { return targetLimitMargin; }
    public double getTargetTolerance() { return targetTolerance; }
    public double getMinimumStepSize() { return minimumStepSize; }
    public double getPreStepStableSeconds() { return preStepStableSeconds; }
    public double getStepConfirmationSeconds() { return stepConfirmationSeconds; }
    public double getStepCaptureSeconds() { return stepCaptureSeconds; }
    public double getStableHoldSeconds() { return stableHoldSeconds; }
    public double getMaximumStableHoldError() { return maximumStableHoldError; }
    public double getSaturationDutyPercent() { return saturationDutyPercent; }
}
