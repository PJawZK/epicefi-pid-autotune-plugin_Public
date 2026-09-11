package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

/** In-memory baseline used for a manual before/after live comparison. */
public final class LiveBaselineSnapshot {
    private final TuneSnapshot gains;
    private final LiveSessionSummary summary;
    private final LiveCaptureProfile profile;
    private final LiveMotionMode motionMode;
    private final String label;
    private final String configurationIdentity;
    private final LiveCaptureSettings captureSettings;

    public LiveBaselineSnapshot(TuneSnapshot gains, LiveSessionSummary summary,
                                LiveCaptureProfile profile, LiveMotionMode motionMode,
                                String label) {
        this(gains, summary, profile, motionMode, label, "", null);
    }

    public LiveBaselineSnapshot(TuneSnapshot gains, LiveSessionSummary summary,
                                LiveCaptureProfile profile, LiveMotionMode motionMode,
                                String label, String configurationIdentity) {
        this(gains, summary, profile, motionMode, label, configurationIdentity, null);
    }

    public LiveBaselineSnapshot(TuneSnapshot gains, LiveSessionSummary summary,
                                LiveCaptureProfile profile, LiveMotionMode motionMode,
                                String label, String configurationIdentity,
                                LiveCaptureSettings captureSettings) {
        this.gains = gains;
        this.summary = summary == null ? LiveSessionSummary.empty() : summary;
        this.profile = profile == null ? LiveCaptureProfile.CUSTOM : profile;
        this.motionMode = motionMode == null ? LiveMotionMode.USE_LIVE_VSS : motionMode;
        this.label = label == null ? "Live baseline" : label;
        this.configurationIdentity = normalizeConfiguration(configurationIdentity);
        this.captureSettings = captureSettings;
    }

    public TuneSnapshot getGains() { return gains; }
    public LiveSessionSummary getSummary() { return summary; }
    public LiveCaptureProfile getProfile() { return profile; }
    public LiveMotionMode getMotionMode() { return motionMode; }
    public String getLabel() { return label; }
    public String getConfigurationIdentity() { return configurationIdentity; }
    public LiveCaptureSettings getCaptureSettings() { return captureSettings; }

    public boolean isForConfiguration(String identity) {
        String normalized = normalizeConfiguration(identity);
        return !configurationIdentity.isEmpty() && configurationIdentity.equals(normalized);
    }

    private static String normalizeConfiguration(String value) {
        return value == null ? "" : value.trim();
    }
}
