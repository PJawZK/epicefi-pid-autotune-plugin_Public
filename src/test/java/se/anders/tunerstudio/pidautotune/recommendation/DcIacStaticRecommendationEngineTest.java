package se.anders.tunerstudio.pidautotune.recommendation;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import se.anders.tunerstudio.pidautotune.live.DcIacStaticEvidence;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DcIacStaticRecommendationEngineTest {
    @Test public void driftingFiveSecondWindowsAreNotConverged() {
        DcIacStaticRecommendation rec = new DcIacStaticRecommendationEngine().evaluate(rows(-5.0, -5.4, -5.8, -6.2)).get(0);
        assertEquals(DcIacStaticRecommendation.NOT_CONVERGED, rec.getStatus());
        assertTrue(Math.abs(rec.getCorrectionTrendPerSecond()) > 0.03);
    }
    @Test public void recentConvergedSuffixCanDropOlderDrift() {
        DcIacStaticRecommendation rec = new DcIacStaticRecommendationEngine().evaluate(rows(-5.0, -5.5, -6.00, -6.08, -6.04)).get(0);
        assertEquals(DcIacStaticRecommendation.READY_LOCAL_BIAS, rec.getStatus());
        assertEquals(-6.04, rec.getRawCorrection(), 0.08);
    }
    @Test public void smallConvergedResidualClearsPidDependency() {
        DcIacStaticRecommendation rec = new DcIacStaticRecommendationEngine().evaluate(rows(0.10, 0.15, 0.08)).get(0);
        assertEquals(DcIacStaticRecommendation.NO_CHANGE, rec.getStatus());
        assertTrue(rec.isBiasReadyForPid());
    }
    private static List<DcIacStaticEvidence> rows(double... correction) {
        List<DcIacStaticEvidence> result = new ArrayList<DcIacStaticEvidence>();
        for (int i = 0; i < correction.length; i++) {
            double c = correction[i], end = 10.0 + i * 5.0;
            DcIacBiasEvidence e = new DcIacBiasEvidence(i + 1, end - 5.0, end, 250, 50.0,
                    25.0, 25.0, 0.0, 35.0 + c, c, c, 35.0, 35.0, 35.0 + c, c,
                    0.0, 0.0, 0.0, 0.1, 0.05, "18-30%");
            result.add(new DcIacStaticEvidence(e, DcIacOperatingContext.RUNNING_IDLE, 900.0, 80.0, 1.0));
        }
        return result;
    }
}
