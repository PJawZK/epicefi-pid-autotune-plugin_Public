package se.anders.tunerstudio.pidautotune.live;

/** Before/after assessment for two compatible live sessions. */
public final class LiveComparisonResult {
    public static final String NO_BASELINE = "No baseline stored";
    public static final String WAITING = "Waiting for comparison data";
    public static final String INCOMPATIBLE = "Inconclusive — conditions differ";
    public static final String SAME_GAINS = "Baseline only — gains unchanged";
    public static final String IMPROVED = "Improved";
    public static final String MIXED = "Mixed result";
    public static final String WORSE = "Worse";
    public static final String INCONCLUSIVE = "Inconclusive";

    private final String status;
    private final String summary;
    private final String details;
    private final double scorePercent;

    public LiveComparisonResult(String status, String summary, String details, double scorePercent) {
        this.status = status == null ? "" : status;
        this.summary = summary == null ? "" : summary;
        this.details = details == null ? "" : details;
        this.scorePercent = scorePercent;
    }

    public String getStatus() { return status; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public double getScorePercent() { return scorePercent; }
}
