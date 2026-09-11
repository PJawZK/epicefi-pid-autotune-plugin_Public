package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;

/** Converged local static-bias recommendation for the integrated DC-IAC tuner. */
public final class DcIacStaticRecommendation {
    public static final String COLLECT_MORE = "COLLECT_MORE";
    public static final String NOT_CONVERGED = "NOT_CONVERGED";
    public static final String INCONSISTENT = "INCONSISTENT";
    public static final String NO_CHANGE = "NO_CHANGE";
    public static final String READY_LOCAL_BIAS = "READY_LOCAL_BIAS";
    private final String status; private final DcIacOperatingContext operatingContext; private final String curveSegment;
    private final int evidenceWindows; private final double centerTarget; private final double currentBias; private final double requiredBias;
    private final double rawCorrection; private final double proposedCorrection; private final double proposedLocalBias;
    private final double correctionMad; private final double correctionRange; private final double correctionTrendPerSecond;
    private final double confidencePercent; private final String detail;
    public DcIacStaticRecommendation(String status, DcIacOperatingContext operatingContext, String curveSegment, int evidenceWindows,
            double centerTarget, double currentBias, double requiredBias, double rawCorrection, double proposedCorrection,
            double proposedLocalBias, double correctionMad, double correctionRange, double correctionTrendPerSecond,
            double confidencePercent, String detail) {
        this.status=status==null?"":status; this.operatingContext=operatingContext==null?DcIacOperatingContext.UNKNOWN:operatingContext;
        this.curveSegment=curveSegment==null?"":curveSegment; this.evidenceWindows=evidenceWindows; this.centerTarget=centerTarget;
        this.currentBias=currentBias; this.requiredBias=requiredBias; this.rawCorrection=rawCorrection; this.proposedCorrection=proposedCorrection;
        this.proposedLocalBias=proposedLocalBias; this.correctionMad=correctionMad; this.correctionRange=correctionRange;
        this.correctionTrendPerSecond=correctionTrendPerSecond; this.confidencePercent=confidencePercent; this.detail=detail==null?"":detail;
    }
    public String getStatus(){return status;} public DcIacOperatingContext getOperatingContext(){return operatingContext;}
    public String getCurveSegment(){return curveSegment;} public int getEvidenceWindows(){return evidenceWindows;}
    public double getCenterTarget(){return centerTarget;} public double getCurrentBias(){return currentBias;}
    public double getRequiredBias(){return requiredBias;} public double getRawCorrection(){return rawCorrection;}
    public double getProposedCorrection(){return proposedCorrection;} public double getProposedLocalBias(){return proposedLocalBias;}
    public double getCorrectionMad(){return correctionMad;} public double getCorrectionRange(){return correctionRange;}
    public double getCorrectionTrendPerSecond(){return correctionTrendPerSecond;} public double getConfidencePercent(){return confidencePercent;}
    public String getDetail(){return detail;} public boolean isBiasReadyForPid(){return NO_CHANGE.equals(status);}
    public boolean isActionableBiasChange(){return READY_LOCAL_BIAS.equals(status);}
}
