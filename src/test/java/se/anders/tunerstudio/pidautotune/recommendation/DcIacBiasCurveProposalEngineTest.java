package se.anders.tunerstudio.pidautotune.recommendation;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;

public final class DcIacBiasCurveProposalEngineTest {
    @Test public void projectsOffKnotEvidenceThroughSurroundingSegment() {
        DcIacBiasSettingsAccess.Snapshot bias = new DcIacBiasSettingsAccess.Snapshot(
                new double[] {18.0, 30.0, 45.0}, new double[] {38.0, 35.0, 35.0}, -30.0, 30.0);
        List<DcIacStaticRecommendation> recs = new ArrayList<DcIacStaticRecommendation>();
        recs.add(new DcIacStaticRecommendation(DcIacStaticRecommendation.READY_LOCAL_BIAS,
                DcIacOperatingContext.RUNNING_IDLE, "18-30%", 4, 29.8,
                35.0, 32.0, -3.0, -3.0, 32.0,
                0.05, 0.10, 0.01, 90.0, "test"));
        List<DcIacBiasKnotProposal> proposals = new DcIacBiasCurveProposalEngine().evaluate(bias, recs);

        // 0.5.21+ projects an actual settled target through both surrounding knots instead of
        // requiring the observation to land within 0.75 %-point of one exact firmware knot.
        // The small influence on the 18% knot quantizes to its already-active value, so it is
        // supported and converged (NO_CHANGE), not unobserved (COLLECT_MORE).
        assertEquals(DcIacBiasKnotProposal.NO_CHANGE, proposals.get(0).getStatus());
        assertEquals(DcIacBiasKnotProposal.READY_KNOT, proposals.get(1).getStatus());
        assertEquals(32.0, proposals.get(1).getProposedValue(), 0.001);
        assertEquals(DcIacBiasKnotProposal.COLLECT_MORE, proposals.get(2).getStatus());
    }
}
