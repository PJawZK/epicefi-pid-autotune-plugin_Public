package se.anders.tunerstudio.pidautotune.session;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public final class DcIacIterationComparisonEngineTest {
    @Test public void detectsImprovedComparableIteration() {
        DcIacTuningSessionSummary base = summary(1, 26.0, 4.0, 25.0, 0.60, 60.0, 3.0);
        DcIacTuningSessionSummary candidate = summary(2, 26.5, 2.5, 12.0, 0.30, 48.0, 1.5);
        assertEquals(DcIacIterationComparison.IMPROVED, new DcIacIterationComparisonEngine().compare(base, candidate).getStatus());
    }
    @Test public void rejectsDifferentOperatingCenter() {
        DcIacTuningSessionSummary base = summary(1, 24.0, 4.0, 25.0, 0.60, 60.0, 3.0);
        DcIacTuningSessionSummary candidate = summary(2, 30.0, 2.5, 12.0, 0.30, 48.0, 1.5);
        assertEquals(DcIacIterationComparison.NOT_COMPARABLE, new DcIacIterationComparisonEngine().compare(base, candidate).getStatus());
    }
    private static DcIacTuningSessionSummary summary(int sequence, double center, double bias, double overshoot, double ss, double peak, double initialI) {
        return new DcIacTuningSessionSummary(sequence, "cfg", 5.0, 0.5, 0.01,
                3, 4, 4, 0, center, 3.0, bias, overshoot, ss, peak, initialI, "NO_CHANGE", "READY_PID");
    }
}
