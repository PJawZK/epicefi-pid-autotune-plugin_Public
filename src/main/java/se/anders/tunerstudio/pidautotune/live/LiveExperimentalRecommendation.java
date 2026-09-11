package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

/** Read-only, explicitly experimental candidate calculated from a live session. */
public final class LiveExperimentalRecommendation {
    public static final String WAITING = "Waiting for live data";
    public static final String BLOCKED_GAINS = "Current gains unavailable";
    public static final String CANDIDATE = "Experimental candidate available";
    public static final String NO_CHANGE = "No experimental change indicated";

    private final String status;
    private final String summary;
    private final String details;
    private final double confidencePercent;
    private final boolean proposalAvailable;
    private final TuneSnapshot current;
    private final TuneSnapshot proposed;
    private final String changedGain;
    private final double changePercent;
    private final String reason;
    private final String expectedEffect;
    private final LiveSessionSummary sessionSummary;

    public LiveExperimentalRecommendation(
            String status, String summary, String details, double confidencePercent,
            boolean proposalAvailable, TuneSnapshot current, TuneSnapshot proposed,
            String changedGain, double changePercent, String reason, String expectedEffect,
            LiveSessionSummary sessionSummary) {
        this.status = safe(status);
        this.summary = safe(summary);
        this.details = safe(details);
        this.confidencePercent = confidencePercent;
        this.proposalAvailable = proposalAvailable;
        this.current = current;
        this.proposed = proposed;
        this.changedGain = safe(changedGain);
        this.changePercent = changePercent;
        this.reason = safe(reason);
        this.expectedEffect = safe(expectedEffect);
        this.sessionSummary = sessionSummary == null ? LiveSessionSummary.empty() : sessionSummary;
    }

    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public double getConfidencePercent() { return confidencePercent; }
    public boolean isProposalAvailable() { return proposalAvailable; }
    public TuneSnapshot getCurrent() { return current; }
    public TuneSnapshot getProposed() { return proposed; }
    public String getChangedGain() { return changedGain; }
    public double getChangePercent() { return changePercent; }
    public String getReason() { return reason; }
    public String getExpectedEffect() { return expectedEffect; }
    public LiveSessionSummary getSessionSummary() { return sessionSummary; }

    private static String safe(String value) { return value == null ? "" : value; }
}
