package se.anders.tunerstudio.pidautotune.live;

/** Preset tolerance profiles for live guided idle capture. */
public enum LiveCaptureProfile {
    INITIAL_ROUGH("Initial / rough", 200.0, 100.0, 30.0, 10.0, 35.0,
            "Wide bands intended for an unstable starting tune. Accepted events remain clearly labelled as coarse-quality unless they also meet stricter criteria."),
    STANDARD("Standard", 100.0, 60.0, 20.0, 10.0, 30.0,
            "General-purpose capture bands for an idle that already returns reasonably close to target."),
    FINE("Fine", 50.0, 30.0, 15.0, 12.0, 25.0,
            "Strict final-refinement bands. Use only after the idle is already reasonably controlled."),
    CUSTOM("Custom", Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
            "Use the manually entered readiness, settling, hysteresis, observation, and recovery-time values.");

    private final String label;
    private final double baselineBandRpm;
    private final double settlingBandRpm;
    private final double exitHysteresisRpm;
    private final double observationSeconds;
    private final double recoveryTimeoutSeconds;
    private final String description;

    LiveCaptureProfile(String label, double baselineBandRpm, double settlingBandRpm,
                       double exitHysteresisRpm, double observationSeconds,
                       double recoveryTimeoutSeconds, String description) {
        this.label = label;
        this.baselineBandRpm = baselineBandRpm;
        this.settlingBandRpm = settlingBandRpm;
        this.exitHysteresisRpm = exitHysteresisRpm;
        this.observationSeconds = observationSeconds;
        this.recoveryTimeoutSeconds = recoveryTimeoutSeconds;
        this.description = description;
    }

    public double getBaselineBandRpm() { return baselineBandRpm; }
    public double getSettlingBandRpm() { return settlingBandRpm; }
    public double getExitHysteresisRpm() { return exitHysteresisRpm; }
    public double getObservationSeconds() { return observationSeconds; }
    public double getRecoveryTimeoutSeconds() { return recoveryTimeoutSeconds; }
    public String getDescription() { return description; }

    @Override public String toString() { return label; }
}
