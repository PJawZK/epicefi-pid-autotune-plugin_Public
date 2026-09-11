package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable snapshot of one complete live-observer run in an iterative tuning session. */
public final class LiveIterationRecord {
    private final int iterationNumber;
    private final long startedMillis;
    private final long endedMillis;
    private final TuneSnapshot gains;
    private final LiveSessionSummary summary;
    private final LiveCaptureProfile profile;
    private final LiveMotionMode motionMode;
    private final String configurationIdentity;
    private final LiveCaptureSettings captureSettings;
    private final LiveExperimentalRecommendation recommendation;
    private final LiveComparisonResult originalComparison;
    private final LiveComparisonResult previousComparison;
    private final String decision;
    private final String archiveReason;
    private final List<LiveAttempt> attempts;
    private final List<LiveSessionRecord> samples;

    public LiveIterationRecord(
            int iterationNumber,
            long startedMillis,
            long endedMillis,
            TuneSnapshot gains,
            LiveSessionSummary summary,
            LiveCaptureProfile profile,
            LiveMotionMode motionMode,
            LiveExperimentalRecommendation recommendation,
            LiveComparisonResult originalComparison,
            LiveComparisonResult previousComparison,
            String decision,
            String archiveReason,
            List<LiveAttempt> attempts,
            List<LiveSessionRecord> samples) {
        this(iterationNumber, startedMillis, endedMillis, gains, summary, profile, motionMode, "", null,
                recommendation, originalComparison, previousComparison, decision, archiveReason, attempts, samples);
    }

    public LiveIterationRecord(
            int iterationNumber,
            long startedMillis,
            long endedMillis,
            TuneSnapshot gains,
            LiveSessionSummary summary,
            LiveCaptureProfile profile,
            LiveMotionMode motionMode,
            String configurationIdentity,
            LiveExperimentalRecommendation recommendation,
            LiveComparisonResult originalComparison,
            LiveComparisonResult previousComparison,
            String decision,
            String archiveReason,
            List<LiveAttempt> attempts,
            List<LiveSessionRecord> samples) {
        this(iterationNumber, startedMillis, endedMillis, gains, summary, profile, motionMode,
                configurationIdentity, null, recommendation, originalComparison, previousComparison,
                decision, archiveReason, attempts, samples);
    }

    public LiveIterationRecord(
            int iterationNumber,
            long startedMillis,
            long endedMillis,
            TuneSnapshot gains,
            LiveSessionSummary summary,
            LiveCaptureProfile profile,
            LiveMotionMode motionMode,
            String configurationIdentity,
            LiveCaptureSettings captureSettings,
            LiveExperimentalRecommendation recommendation,
            LiveComparisonResult originalComparison,
            LiveComparisonResult previousComparison,
            String decision,
            String archiveReason,
            List<LiveAttempt> attempts,
            List<LiveSessionRecord> samples) {
        this.iterationNumber = iterationNumber;
        this.startedMillis = startedMillis;
        this.endedMillis = endedMillis;
        this.gains = gains;
        this.summary = summary == null ? LiveSessionSummary.empty() : summary;
        this.profile = profile == null ? LiveCaptureProfile.CUSTOM : profile;
        this.motionMode = motionMode == null ? LiveMotionMode.USE_LIVE_VSS : motionMode;
        this.configurationIdentity = normalizeConfiguration(configurationIdentity);
        this.captureSettings = captureSettings;
        this.recommendation = recommendation;
        this.originalComparison = originalComparison;
        this.previousComparison = previousComparison;
        this.decision = safe(decision);
        this.archiveReason = safe(archiveReason);
        this.attempts = immutableCopy(attempts);
        this.samples = immutableRecordCopy(samples);
    }

    public int getIterationNumber() { return iterationNumber; }
    public long getStartedMillis() { return startedMillis; }
    public long getEndedMillis() { return endedMillis; }
    public TuneSnapshot getGains() { return gains; }
    public LiveSessionSummary getSummary() { return summary; }
    public LiveCaptureProfile getProfile() { return profile; }
    public LiveMotionMode getMotionMode() { return motionMode; }
    public String getConfigurationIdentity() { return configurationIdentity; }
    public LiveCaptureSettings getCaptureSettings() { return captureSettings; }
    public LiveExperimentalRecommendation getRecommendation() { return recommendation; }
    public LiveComparisonResult getOriginalComparison() { return originalComparison; }
    public LiveComparisonResult getPreviousComparison() { return previousComparison; }
    public String getDecision() { return decision; }
    public String getArchiveReason() { return archiveReason; }
    public List<LiveAttempt> getAttempts() { return attempts; }
    public List<LiveSessionRecord> getSamples() { return samples; }

    public LiveBaselineSnapshot asBaseline(String label) {
        return new LiveBaselineSnapshot(gains, summary, profile, motionMode, label, configurationIdentity, captureSettings);
    }

    private static List<LiveAttempt> immutableCopy(List<LiveAttempt> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<LiveAttempt>(values));
    }

    private static List<LiveSessionRecord> immutableRecordCopy(List<LiveSessionRecord> values) {
        if (values == null || values.isEmpty()) return Collections.emptyList();
        return Collections.unmodifiableList(new ArrayList<LiveSessionRecord>(values));
    }

    private static String normalizeConfiguration(String value) {
        return value == null ? "" : value.trim();
    }

    private static String safe(String value) { return value == null ? "" : value; }
}
