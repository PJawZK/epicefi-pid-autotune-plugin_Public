/*
 * Decompiled with CFR 0.152.
 */
package se.anders.tunerstudio.pidautotune.recommendation;

public final class DcIacBiasBinPlacementProposal {
    public static final String READY_MOVE = "READY_MOVE";
    public static final String ALIGNED = "ALIGNED";
    public static final String COLLECT_MORE = "COLLECT_MORE";
    public static final String BLOCKED = "BLOCKED";
    private final int knotIndex;
    private final double currentBin;
    private final double proposedBin;
    private final double currentValue;
    private final double proposedValue;
    private final double evidenceTarget;
    private final int evidenceWindows;
    private final double confidencePercent;
    private final String status;
    private final String detail;

    public DcIacBiasBinPlacementProposal(int n, double d, double d2, double d3, double d4, double d5, int n2, double d6, String string, String string2) {
        this.knotIndex = n;
        this.currentBin = d;
        this.proposedBin = d2;
        this.currentValue = d3;
        this.proposedValue = d4;
        this.evidenceTarget = d5;
        this.evidenceWindows = n2;
        this.confidencePercent = d6;
        this.status = string;
        this.detail = string2;
    }

    public int getKnotIndex() {
        return this.knotIndex;
    }

    public double getCurrentBin() {
        return this.currentBin;
    }

    public double getProposedBin() {
        return this.proposedBin;
    }

    public double getCurrentValue() {
        return this.currentValue;
    }

    public double getProposedValue() {
        return this.proposedValue;
    }

    public double getEvidenceTarget() {
        return this.evidenceTarget;
    }

    public int getEvidenceWindows() {
        return this.evidenceWindows;
    }

    public double getConfidencePercent() {
        return this.confidencePercent;
    }

    public String getStatus() {
        return this.status;
    }

    public String getDetail() {
        return this.detail;
    }

    public boolean isReady() {
        return READY_MOVE.equals(this.status);
    }
}

