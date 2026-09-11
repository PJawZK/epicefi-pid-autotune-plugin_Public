package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

/** Immutable result from the read-only conservative recommendation model. */
public final class PidRecommendation {
    public static final String BLOCKED_DATASET = "Blocked — dataset not ready";
    public static final String BLOCKED_CONFIDENCE = "Blocked — model confidence too low";
    public static final String READY = "Conservative candidate available";
    public static final String NO_CHANGE = "No conservative gain change indicated";

    private final String status;
    private final String summary;
    private final String details;
    private final String bestGroup;
    private final double confidencePercent;
    private final boolean proposalAvailable;
    private final TuneSnapshot current;
    private final TuneSnapshot proposed;
    private final double pChangePercent;
    private final double iChangePercent;
    private final double dChangePercent;
    private final String pReason;
    private final String iReason;
    private final String dReason;
    private final String pEffect;
    private final String iEffect;
    private final String dEffect;
    private final ResponseSummary responseSummary;

    public PidRecommendation(
            String status,
            String summary,
            String details,
            String bestGroup,
            double confidencePercent,
            boolean proposalAvailable,
            TuneSnapshot current,
            TuneSnapshot proposed,
            double pChangePercent,
            double iChangePercent,
            double dChangePercent,
            String pReason,
            String iReason,
            String dReason,
            String pEffect,
            String iEffect,
            String dEffect,
            ResponseSummary responseSummary) {
        this.status = safe(status);
        this.summary = safe(summary);
        this.details = safe(details);
        this.bestGroup = safe(bestGroup);
        this.confidencePercent = confidencePercent;
        this.proposalAvailable = proposalAvailable;
        this.current = current;
        this.proposed = proposed;
        this.pChangePercent = pChangePercent;
        this.iChangePercent = iChangePercent;
        this.dChangePercent = dChangePercent;
        this.pReason = safe(pReason);
        this.iReason = safe(iReason);
        this.dReason = safe(dReason);
        this.pEffect = safe(pEffect);
        this.iEffect = safe(iEffect);
        this.dEffect = safe(dEffect);
        this.responseSummary = responseSummary == null ? ResponseSummary.empty() : responseSummary;
    }

    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public String getBestGroup() { return bestGroup; }
    public double getConfidencePercent() { return confidencePercent; }
    public boolean isProposalAvailable() { return proposalAvailable; }
    public TuneSnapshot getCurrent() { return current; }
    public TuneSnapshot getProposed() { return proposed; }
    public double getPChangePercent() { return pChangePercent; }
    public double getIChangePercent() { return iChangePercent; }
    public double getDChangePercent() { return dChangePercent; }
    public String getPReason() { return pReason; }
    public String getIReason() { return iReason; }
    public String getDReason() { return dReason; }
    public String getPEffect() { return pEffect; }
    public String getIEffect() { return iEffect; }
    public String getDEffect() { return dEffect; }
    public ResponseSummary getResponseSummary() { return responseSummary; }

    private static String safe(String value) { return value == null ? "" : value; }
}
