package se.anders.tunerstudio.pidautotune.recommendation;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasCharacterizationSettings;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasEvidence;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class DcIacBiasRecommendationEngineTest {
    private final DcIacBiasRecommendationEngine engine = new DcIacBiasRecommendationEngine();
    private final DcIacBiasCharacterizationSettings settings = new DcIacBiasCharacterizationSettings();

    @Test
    public void repeatedConsistentWindowsProduceLocalReadOnlyRecommendation() {
        List<DcIacBiasRecommendation> rows = engine.evaluate(Arrays.asList(
                evidence(1, 23.0, 36.8, 30.9),
                evidence(2, 23.3, 36.7, 30.8),
                evidence(3, 22.9, 36.9, 31.0)), settings);
        DcIacBiasRecommendation row = rows.get(0);
        assertEquals(DcIacBiasRecommendation.READY_LOCAL_BIAS, row.getStatus());
        assertTrue(row.isReady());
        assertTrue(row.getProposedCorrection() < -5.0);
        assertTrue(row.getProposedCorrection() >= -settings.getMaximumCorrectionPerPass() - 0.0001);
        assertTrue(row.getConfidencePercent() > 50.0);
    }

    @Test
    public void singleWindowRequestsMoreEvidence() {
        DcIacBiasRecommendation row = engine.evaluate(Arrays.asList(
                evidence(1, 23.0, 36.8, 31.0)), settings).get(0);
        assertEquals(DcIacBiasRecommendation.COLLECT_MORE, row.getStatus());
        assertFalse(row.isReady());
    }

    @Test
    public void inconsistentCorrectionsAreBlocked() {
        DcIacBiasRecommendation row = engine.evaluate(Arrays.asList(
                evidence(1, 23.0, 36.8, 30.0),
                evidence(2, 23.2, 36.8, 36.5),
                evidence(3, 22.9, 36.9, 32.0)), settings).get(0);
        assertEquals(DcIacBiasRecommendation.INCONSISTENT, row.getStatus());
        assertFalse(row.isReady());
    }

    @Test
    public void tinyRepeatedCorrectionProducesNoChange() {
        DcIacBiasRecommendation row = engine.evaluate(Arrays.asList(
                evidence(1, 23.0, 36.8, 36.55),
                evidence(2, 23.2, 36.75, 36.50)), settings).get(0);
        assertEquals(DcIacBiasRecommendation.NO_CHANGE, row.getStatus());
        assertEquals(0.0, row.getProposedCorrection(), 0.0001);
    }

    private static DcIacBiasEvidence evidence(
            int sequence, double target, double currentBias, double requiredBias) {
        return new DcIacBiasEvidence(
                sequence, sequence * 10.0, sequence * 10.0 + 5.0,
                240, 48.0,
                target, target, 0.0,
                requiredBias, requiredBias - currentBias, requiredBias - currentBias,
                currentBias, currentBias, requiredBias, requiredBias - currentBias,
                0.0, 0.0, 0.0, 0.2, 0.05,
                "18↔30%");
    }
}
