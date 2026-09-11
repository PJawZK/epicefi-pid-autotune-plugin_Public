package se.anders.tunerstudio.pidautotune.recommendation;

/** Read-only proposal for one existing dcIdleBiasValues knot. */
public final class DcIacBiasKnotProposal {
    public static final String COLLECT_MORE="COLLECT_MORE", NO_CHANGE="NO_CHANGE", READY_KNOT="READY_KNOT", INCONSISTENT="INCONSISTENT";
    private final int knotIndex; private final double position,currentValue,proposedValue,proposedCorrection; private final int supportingRegions;
    private final double confidencePercent; private final String status,detail;
    public DcIacBiasKnotProposal(int knotIndex,double position,double currentValue,double proposedValue,double proposedCorrection,
            int supportingRegions,double confidencePercent,String status,String detail){this.knotIndex=knotIndex;this.position=position;
        this.currentValue=currentValue;this.proposedValue=proposedValue;this.proposedCorrection=proposedCorrection;this.supportingRegions=supportingRegions;
        this.confidencePercent=confidencePercent;this.status=status==null?"":status;this.detail=detail==null?"":detail;}
    public int getKnotIndex(){return knotIndex;} public double getPosition(){return position;} public double getCurrentValue(){return currentValue;}
    public double getProposedValue(){return proposedValue;} public double getProposedCorrection(){return proposedCorrection;}
    public int getSupportingRegions(){return supportingRegions;} public double getConfidencePercent(){return confidencePercent;}
    public String getStatus(){return status;} public String getDetail(){return detail;} public boolean isReady(){return READY_KNOT.equals(status);}
}
