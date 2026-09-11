package se.anders.tunerstudio.pidautotune.recommendation;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.controller.DcIacSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacDynamicEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacEvent;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurement;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;

public final class DcIacPidRecommendationEngineTest {
    private static final DcIacSettingsAccess.Snapshot SETTINGS = new DcIacSettingsAccess.Snapshot(0.0, 100.0, Boolean.FALSE, 5.0, 0.5, 0.01);

    @Test public void actionableBiasBlocksPid() {
        DcIacPidRecommendation rec = new DcIacPidRecommendationEngine().evaluate(resolvedSteps(8.0, 0.10, 0.20, 40.0, 0), one(staticRec(DcIacStaticRecommendation.READY_LOCAL_BIAS)), SETTINGS).get(0);
        assertEquals(DcIacPidRecommendation.BLOCKED_BIAS, rec.getStatus());
    }
    @Test public void severeResolvedOvershootWithLargeDKickReducesDOnly() {
        DcIacPidRecommendation rec = new DcIacPidRecommendationEngine().evaluate(resolvedSteps(50.0, 0.10, 0.12, 55.0, 2), one(staticRec(DcIacStaticRecommendation.NO_CHANGE)), SETTINGS).get(0);
        assertEquals(DcIacPidRecommendation.READY_PID, rec.getStatus());
        assertEquals(0.0, rec.getPChangePercent(), 0.0001);
        assertEquals(0.0, rec.getIChangePercent(), 0.0001);
        assertEquals(-20.0, rec.getDChangePercent(), 0.0001);
        assertEquals(0.008, rec.getProposedD(), 0.00001);
    }
    @Test public void sourceLimitedEvidenceCannotIncreaseGains() {
        DcIacPidRecommendation rec = new DcIacPidRecommendationEngine().evaluate(limitedSteps(5.0, 0.20, 45.0, 0), one(staticRec(DcIacStaticRecommendation.NO_CHANGE)), SETTINGS).get(0);
        assertEquals(DcIacPidRecommendation.SOURCE_LIMITED, rec.getStatus());
        assertEquals(5.0, rec.getProposedP(), 0.0001);
        assertEquals(0.5, rec.getProposedI(), 0.0001);
        assertEquals(0.01, rec.getProposedD(), 0.0001);
    }
    @Test public void resolvedCleanSlowResponseAllowsSmallPIncrease() {
        DcIacPidRecommendation rec = new DcIacPidRecommendationEngine().evaluate(resolvedSteps(5.0, 0.10, 0.35, 45.0, 0), one(staticRec(DcIacStaticRecommendation.NO_CHANGE)), SETTINGS).get(0);
        assertEquals(DcIacPidRecommendation.READY_PID, rec.getStatus());
        assertEquals(5.0, rec.getPChangePercent(), 0.0001);
        assertEquals(5.25, rec.getProposedP(), 0.0001);
    }

    private static List<DcIacDynamicEvidence> resolvedSteps(double overshoot, double ss, double settling, double peakDuty, int reversals) {
        List<DcIacDynamicEvidence> result = new ArrayList<DcIacDynamicEvidence>();
        result.add(dynamic(1, DcIacEvent.Type.OPENING_STEP, true, overshoot, ss, settling, peakDuty, reversals));
        result.add(dynamic(2, DcIacEvent.Type.CLOSING_STEP, true, overshoot, ss, settling, peakDuty, reversals));
        result.add(dynamic(3, DcIacEvent.Type.OPENING_STEP, true, overshoot, ss, settling, peakDuty, reversals));
        result.add(dynamic(4, DcIacEvent.Type.CLOSING_STEP, true, overshoot, ss, settling, peakDuty, reversals));
        return result;
    }
    private static List<DcIacDynamicEvidence> limitedSteps(double overshoot, double ss, double peakDuty, int reversals) {
        List<DcIacDynamicEvidence> result = new ArrayList<DcIacDynamicEvidence>();
        result.add(dynamic(1, DcIacEvent.Type.OPENING_STEP, false, overshoot, ss, Double.NaN, peakDuty, reversals));
        result.add(dynamic(2, DcIacEvent.Type.CLOSING_STEP, false, overshoot, ss, Double.NaN, peakDuty, reversals));
        result.add(dynamic(3, DcIacEvent.Type.OPENING_STEP, false, overshoot, ss, Double.NaN, peakDuty, reversals));
        result.add(dynamic(4, DcIacEvent.Type.CLOSING_STEP, false, overshoot, ss, Double.NaN, peakDuty, reversals));
        return result;
    }
    private static DcIacDynamicEvidence dynamic(int seq, DcIacEvent.Type type, boolean quantitative, double overshoot, double ss, double settling, double peakDuty, int reversals) {
        String code = quantitative ? "STEP_MEASURED" : "STEP_MEASURED_TIMING_LIMITED";
        String flags = quantitative ? "" : "SOURCE_RESOLUTION_LIMITED";
        DcIacMeasurement m = new DcIacMeasurement(seq, type, DcIacMeasurement.Validity.VALID, code, "test",
                100, 2.0, type == DcIacEvent.Type.OPENING_STEP ? 25.0 : 28.0, type == DcIacEvent.Type.OPENING_STEP ? 28.0 : 25.0, 50.0,
                0.2, 0.1, 25.0, 28.0, 0.02, quantitative ? 0.08 : 0.02, settling, overshoot,
                ss, 0.2, 3.0, peakDuty, reversals, 10.0, 1.0, 2.0, 0.1, 0.1, 1.0, 0.0, 0.2,
                3.0, 26.5, 0.2, 0.0, 0.0, 0.0, 35.0, 60.0,
                false, quantitative, false, flags);
        return new DcIacDynamicEvidence(m, DcIacOperatingContext.RUNNING_IDLE, 900.0, 80.0, 1.0);
    }
    private static DcIacStaticRecommendation staticRec(String status) {
        return new DcIacStaticRecommendation(status, DcIacOperatingContext.RUNNING_IDLE, "18-30%", 3,
                26.5, 35.0, 35.0, 0.0, 0.0, 35.0, 0.05, 0.10, 0.01, 90.0, "test");
    }
    private static List<DcIacStaticRecommendation> one(DcIacStaticRecommendation row) {
        List<DcIacStaticRecommendation> result = new ArrayList<DcIacStaticRecommendation>(); result.add(row); return result;
    }
}
