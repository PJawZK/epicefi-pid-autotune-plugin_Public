package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Validated settings used by the live guided-capture state machine. */
public final class LiveCaptureSettings {
    private static final double COMPARISON_EPSILON = 0.000001;

    private final double tpsThreshold;
    private final double maxIdleVss;
    private final double rpmUpperLimit;
    private final double rpmDeadZone;
    private final double correctionMinimum;
    private final double correctionMaximum;
    private final double minimumCoolant;
    private final double maximumCoolant;
    private final double preferredRevMinimum;
    private final double preferredRevMaximum;
    private final double baselineErrorBandRpm;
    private final double settlingBandRpm;
    private final double exitHysteresisRpm;
    private final double stableObservationSeconds;
    private final double maximumRecoverySeconds;
    private final LiveCaptureProfile captureProfile;
    private final LiveMotionMode motionMode;
    private final boolean manualStationaryConfirmed;

    public LiveCaptureSettings(
            double tpsThreshold,
            double maxIdleVss,
            double rpmUpperLimit,
            double rpmDeadZone,
            double correctionMinimum,
            double correctionMaximum,
            double minimumCoolant,
            double maximumCoolant,
            double preferredRevMinimum,
            double preferredRevMaximum,
            double baselineErrorBandRpm,
            double settlingBandRpm,
            double exitHysteresisRpm,
            double stableObservationSeconds,
            double maximumRecoverySeconds,
            LiveCaptureProfile captureProfile,
            LiveMotionMode motionMode,
            boolean manualStationaryConfirmed) {
        this.tpsThreshold = tpsThreshold;
        this.maxIdleVss = maxIdleVss;
        this.rpmUpperLimit = rpmUpperLimit;
        this.rpmDeadZone = rpmDeadZone;
        this.correctionMinimum = correctionMinimum;
        this.correctionMaximum = correctionMaximum;
        this.minimumCoolant = minimumCoolant;
        this.maximumCoolant = maximumCoolant;
        this.preferredRevMinimum = preferredRevMinimum;
        this.preferredRevMaximum = preferredRevMaximum;
        this.baselineErrorBandRpm = baselineErrorBandRpm;
        this.settlingBandRpm = settlingBandRpm;
        this.exitHysteresisRpm = exitHysteresisRpm;
        this.stableObservationSeconds = stableObservationSeconds;
        this.maximumRecoverySeconds = maximumRecoverySeconds;
        this.captureProfile = captureProfile == null ? LiveCaptureProfile.CUSTOM : captureProfile;
        this.motionMode = motionMode == null ? LiveMotionMode.USE_LIVE_VSS : motionMode;
        this.manualStationaryConfirmed = manualStationaryConfirmed;
    }

    public double getTpsThreshold() { return tpsThreshold; }
    public double getMaxIdleVss() { return maxIdleVss; }
    public double getRpmUpperLimit() { return rpmUpperLimit; }
    public double getRpmDeadZone() { return rpmDeadZone; }
    public double getCorrectionMinimum() { return correctionMinimum; }
    public double getCorrectionMaximum() { return correctionMaximum; }
    public double getMinimumCoolant() { return minimumCoolant; }
    public double getMaximumCoolant() { return maximumCoolant; }
    public double getPreferredRevMinimum() { return preferredRevMinimum; }
    public double getPreferredRevMaximum() { return preferredRevMaximum; }
    public double getBaselineErrorBandRpm() { return baselineErrorBandRpm; }
    public double getSettlingBandRpm() { return settlingBandRpm; }
    public double getExitHysteresisRpm() { return exitHysteresisRpm; }
    public double getStableObservationSeconds() { return stableObservationSeconds; }
    public double getMaximumRecoverySeconds() { return maximumRecoverySeconds; }
    public LiveCaptureProfile getCaptureProfile() { return captureProfile; }
    public LiveMotionMode getMotionMode() { return motionMode; }
    public boolean isManualStationaryConfirmed() { return manualStationaryConfirmed; }

    public LiveCaptureSettings withManualStationaryConfirmed(boolean confirmed) {
        return new LiveCaptureSettings(
                tpsThreshold, maxIdleVss, rpmUpperLimit, rpmDeadZone,
                correctionMinimum, correctionMaximum, minimumCoolant, maximumCoolant,
                preferredRevMinimum, preferredRevMaximum, baselineErrorBandRpm,
                settlingBandRpm, exitHysteresisRpm, stableObservationSeconds,
                maximumRecoverySeconds, captureProfile, motionMode, confirmed);
    }

    /**
     * Returns differences that can change live-attempt qualification, recovery measurement,
     * or comparison metrics. An empty list means the two sessions used the same effective
     * capture contract at controller/UI resolution.
     */
    public List<String> comparisonDifferences(LiveCaptureSettings other) {
        if (other == null) return Collections.singletonList("capture settings unavailable");
        List<String> differences = new ArrayList<String>();
        addDifference(differences, "TPS threshold", tpsThreshold, other.tpsThreshold);
        addDifference(differences, "stationary VSS limit", getStationaryVssLimit(), other.getStationaryVssLimit());
        addDifference(differences, "idle-control RPM upper limit", rpmUpperLimit, other.rpmUpperLimit);
        addDifference(differences, "idle PID RPM dead zone", rpmDeadZone, other.rpmDeadZone);
        addDifference(differences, "correction minimum", correctionMinimum, other.correctionMinimum);
        addDifference(differences, "correction maximum", correctionMaximum, other.correctionMaximum);
        addDifference(differences, "minimum coolant", minimumCoolant, other.minimumCoolant);
        addDifference(differences, "maximum coolant", maximumCoolant, other.maximumCoolant);
        addDifference(differences, "preferred rev minimum", preferredRevMinimum, other.preferredRevMinimum);
        addDifference(differences, "preferred rev maximum", preferredRevMaximum, other.preferredRevMaximum);
        addDifference(differences, "baseline error band", baselineErrorBandRpm, other.baselineErrorBandRpm);
        addDifference(differences, "settling band", settlingBandRpm, other.settlingBandRpm);
        addDifference(differences, "exit hysteresis", exitHysteresisRpm, other.exitHysteresisRpm);
        addDifference(differences, "stable observation time", stableObservationSeconds, other.stableObservationSeconds);
        addDifference(differences, "recovery timeout", maximumRecoverySeconds, other.maximumRecoverySeconds);
        if (captureProfile != other.captureProfile) differences.add("capture profile differs");
        if (motionMode != other.motionMode) differences.add("motion basis differs");
        return Collections.unmodifiableList(differences);
    }

    private static void addDifference(List<String> differences, String label, double left, double right) {
        if (!same(left, right)) differences.add(label + " differs");
    }

    private static boolean same(double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) return Double.isNaN(left) && Double.isNaN(right);
        if (Double.isInfinite(left) || Double.isInfinite(right)) return left == right;
        return Math.abs(left - right) <= COMPARISON_EPSILON;
    }

    public double getStationaryVssLimit() { return Math.min(1.0, Math.max(0.5, maxIdleVss)); }
    public double getEngineRunningMinimumRpm() { return 500.0; }
    public double getBaselineRequiredSeconds() { return 3.0; }
    public double getSettlingRequiredSeconds() { return 1.5; }
    public double getDfcoInactiveRequiredSeconds() { return 0.35; }
    public double getChannelFreshnessSeconds() { return 2.0; }
    public double getCorrectionLimitTolerance() { return 0.15; }
    public double getMaximumGoodCorrectionLimitPercent() { return 30.0; }
    public double getBaselineRollingWindowSeconds() { return 1.0; }
    public double getRecoveryRollingWindowSeconds() { return 0.35; }
}
