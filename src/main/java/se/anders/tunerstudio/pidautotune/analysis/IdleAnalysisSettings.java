package se.anders.tunerstudio.pidautotune.analysis;

/** Idle-state limits read from the active tune, with conservative EPICEFI defaults as fallback. */
public final class IdleAnalysisSettings {
    private final double tpsThreshold;
    private final double maxVehicleSpeed;
    private final double rpmUpperLimit;
    private final double rpmDeadZone;
    private final double correctionMinimum;
    private final double correctionMaximum;
    private final double minimumRegionSeconds;
    private final double responseSettlingBandRpm;
    private final String captureProfileName;

    public IdleAnalysisSettings(
            double tpsThreshold,
            double maxVehicleSpeed,
            double rpmUpperLimit,
            double rpmDeadZone,
            double correctionMinimum,
            double correctionMaximum,
            double minimumRegionSeconds) {
        this(tpsThreshold, maxVehicleSpeed, rpmUpperLimit, rpmDeadZone,
                correctionMinimum, correctionMaximum, minimumRegionSeconds,
                Double.NaN, "Tune-derived");
    }

    public IdleAnalysisSettings(
            double tpsThreshold,
            double maxVehicleSpeed,
            double rpmUpperLimit,
            double rpmDeadZone,
            double correctionMinimum,
            double correctionMaximum,
            double minimumRegionSeconds,
            double responseSettlingBandRpm,
            String captureProfileName) {
        this.tpsThreshold = tpsThreshold;
        this.maxVehicleSpeed = maxVehicleSpeed;
        this.rpmUpperLimit = rpmUpperLimit;
        this.rpmDeadZone = rpmDeadZone;
        this.correctionMinimum = correctionMinimum;
        this.correctionMaximum = correctionMaximum;
        this.minimumRegionSeconds = minimumRegionSeconds;
        this.responseSettlingBandRpm = responseSettlingBandRpm;
        this.captureProfileName = captureProfileName == null || captureProfileName.trim().isEmpty()
                ? "Tune-derived" : captureProfileName.trim();
    }

    public static IdleAnalysisSettings defaults() {
        return new IdleAnalysisSettings(2.0, 15.0, 200.0, 10.0, -10.0, 10.0, 2.0);
    }

    public IdleAnalysisSettings withLiveCaptureOverrides(double settlingBandRpm, String profileName) {
        return new IdleAnalysisSettings(tpsThreshold, maxVehicleSpeed, rpmUpperLimit, rpmDeadZone,
                correctionMinimum, correctionMaximum, minimumRegionSeconds,
                settlingBandRpm, profileName);
    }

    public double getTpsThreshold() { return tpsThreshold; }
    public double getMaxVehicleSpeed() { return maxVehicleSpeed; }
    public double getRpmUpperLimit() { return rpmUpperLimit; }
    public double getRpmDeadZone() { return rpmDeadZone; }
    public double getCorrectionMinimum() { return correctionMinimum; }
    public double getCorrectionMaximum() { return correctionMaximum; }
    public double getMinimumRegionSeconds() { return minimumRegionSeconds; }
    public String getCaptureProfileName() { return captureProfileName; }
    public boolean hasLiveCaptureSettlingBand() { return finite(responseSettlingBandRpm); }
    public double getResponseSettlingBandRpm() {
        return hasLiveCaptureSettlingBand() ? responseSettlingBandRpm : Math.max(25.0, rpmDeadZone);
    }

    /** Minimum plausible running speed used to reject engine-stop samples from idle analysis. */
    public double getEngineRunningMinimumRpm() { return 500.0; }

    /** Adjacent target change treated as a discrete operating-condition change. */
    public double getTargetStepThresholdRpm() { return 25.0; }

    /** Fraction of target below which a response contains a severe RPM excursion. */
    public double getSevereRpmRatio() { return 0.60; }

    /** Distance from either configured correction limit treated as saturation. */
    public double getCorrectionLimitTolerance() { return 0.15; }

    /** Limit-time fraction above which a response is rejected for gain identification. */
    public double getMaximumCorrectionLimitFraction() { return 0.30; }

    public double getSettlingRequiredSeconds() { return 2.0; }
    public double getPostSettlementGuardSeconds() { return 0.5; }
    public double getFanPreTriggerSeconds() { return 5.0; }
    public double getFanPostTriggerSeconds() { return 12.0; }

    /** Target must stay inside this range before a period is treated as steady rather than a return-target ramp. */
    public double getSteadyTargetRangeRpm() { return Math.max(5.0, rpmDeadZone); }

    /** Time for which target stability must be demonstrated before a return-target ramp is considered complete. */
    public double getTargetStableRequiredSeconds() { return 1.5; }

    /** Target-to-base-target difference accepted as effectively back at the normal coolant target. */
    public double getTargetBaseToleranceRpm() { return Math.max(10.0, rpmDeadZone); }

    /** Minimum uninterrupted duration reported as a steady-idle hold. */
    public double getMinimumSteadyHoldSeconds() { return 8.0; }

    /** Sustained mean RPM error that starts a load-disturbance phase. */
    public double getDisturbanceMeanErrorRpm() { return Math.max(75.0, rpmDeadZone * 4.0); }

    /** Duration over which a disturbance must remain in one direction before it is accepted. */
    public double getDisturbanceConfirmationSeconds() { return 0.75; }

    /** Pre-trigger context shown for an automatically detected load disturbance. */
    public double getDisturbancePreTriggerSeconds() { return 3.0; }

    /** Duration of stable error required to declare recovery from a disturbance. */
    public double getDisturbanceRecoverySeconds() { return 2.0; }

    public double getRecoveryMeanAbsoluteErrorRpm() { return Math.max(35.0, rpmDeadZone * 2.5); }
    public double getRecoveryPercentileErrorRpm() { return Math.max(80.0, rpmDeadZone * 5.0); }

    /** Steady-idle quality thresholds. They grade data; they do not change ECU settings. */
    public double getGoodSteadyMeanAbsoluteErrorRpm() { return 30.0; }
    public double getUsableSteadyMeanAbsoluteErrorRpm() { return 50.0; }
    public double getGoodSteadyRpmStandardDeviation() { return 30.0; }
    public double getUsableSteadyRpmStandardDeviation() { return 50.0; }
    public double getMaximumSteadyTargetDriftRpm() { return 20.0; }
    public double getGoodSteadyDurationSeconds() { return 15.0; }

    /** Output-direction activity is measured after averaging into these short time buckets. */
    public double getCorrectionActivityBucketSeconds() { return 0.05; }
    public double getCorrectionDirectionDeadband() { return 0.25; }
    public double getHighCorrectionReversalRatePerSecond() { return 5.0; }
    public double getHighDerivativeSpan() { return 300.0; }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
