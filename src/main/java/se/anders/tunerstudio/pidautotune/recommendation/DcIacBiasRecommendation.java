package se.anders.tunerstudio.pidautotune.recommendation;

/** Read-only local DC-IAC bias recommendation derived from repeated settled equilibrium windows. */
public final class DcIacBiasRecommendation {
    public static final String COLLECT_MORE = "COLLECT_MORE";
    public static final String INCONSISTENT = "INCONSISTENT";
    public static final String NO_CHANGE = "NO_CHANGE";
    public static final String READY_LOCAL_BIAS = "READY_LOCAL_BIAS";

    private final String status;
    private final String curveSegment;
    private final int evidenceWindows;
    private final double centerTarget;
    private final double currentBias;
    private final double requiredBias;
    private final double rawCorrection;
    private final double proposedCorrection;
    private final double proposedLocalBias;
    private final double correctionMad;
    private final double confidencePercent;
    private final String detail;

    public DcIacBiasRecommendation(
            String status,
            String curveSegment,
            int evidenceWindows,
            double centerTarget,
            double currentBias,
            double requiredBias,
            double rawCorrection,
            double proposedCorrection,
            double proposedLocalBias,
            double correctionMad,
            double confidencePercent,
            String detail) {
        this.status = status == null ? "" : status;
        this.curveSegment = curveSegment == null ? "" : curveSegment;
        this.evidenceWindows = evidenceWindows;
        this.centerTarget = centerTarget;
        this.currentBias = currentBias;
        this.requiredBias = requiredBias;
        this.rawCorrection = rawCorrection;
        this.proposedCorrection = proposedCorrection;
        this.proposedLocalBias = proposedLocalBias;
        this.correctionMad = correctionMad;
        this.confidencePercent = confidencePercent;
        this.detail = detail == null ? "" : detail;
    }

    public String getStatus() { return status; }
    public String getCurveSegment() { return curveSegment; }
    public int getEvidenceWindows() { return evidenceWindows; }
    public double getCenterTarget() { return centerTarget; }
    public double getCurrentBias() { return currentBias; }
    public double getRequiredBias() { return requiredBias; }
    public double getRawCorrection() { return rawCorrection; }
    public double getProposedCorrection() { return proposedCorrection; }
    public double getProposedLocalBias() { return proposedLocalBias; }
    public double getCorrectionMad() { return correctionMad; }
    public double getConfidencePercent() { return confidencePercent; }
    public String getDetail() { return detail; }
    public boolean isReady() { return READY_LOCAL_BIAS.equals(status); }
}
