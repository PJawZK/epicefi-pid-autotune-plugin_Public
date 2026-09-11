package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.live.DcIacBiasCharacterizationSettings;
import se.anders.tunerstudio.pidautotune.live.DcIacBiasEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Conservative read-only M4A local-bias recommendation engine.
 *
 * It deliberately recommends only a local operating-point correction. It does not project one
 * local observation into unobserved bias-table knots and it cannot write ECU settings.
 */
public final class DcIacBiasRecommendationEngine {
    public List<DcIacBiasRecommendation> evaluate(
            List<DcIacBiasEvidence> evidence,
            DcIacBiasCharacterizationSettings settings) {
        if (settings == null) settings = new DcIacBiasCharacterizationSettings();
        List<DcIacBiasEvidence> sorted = new ArrayList<DcIacBiasEvidence>();
        if (evidence != null) sorted.addAll(evidence);
        Collections.sort(sorted, new Comparator<DcIacBiasEvidence>() {
            @Override public int compare(DcIacBiasEvidence a, DcIacBiasEvidence b) {
                return Double.compare(a.getTarget(), b.getTarget());
            }
        });

        List<Group> groups = new ArrayList<Group>();
        for (DcIacBiasEvidence row : sorted) {
            Group best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Group group : groups) {
                if (!group.segment.equals(row.getCurveSegment())) continue;
                double distance = Math.abs(group.center() - row.getTarget());
                if (distance <= settings.getLocalGroupingTolerance() && distance < bestDistance) {
                    best = group;
                    bestDistance = distance;
                }
            }
            if (best == null) {
                best = new Group(row.getCurveSegment());
                groups.add(best);
            }
            best.rows.add(row);
        }

        List<DcIacBiasRecommendation> result = new ArrayList<DcIacBiasRecommendation>();
        for (Group group : groups) result.add(summarize(group, settings));
        Collections.sort(result, new Comparator<DcIacBiasRecommendation>() {
            @Override public int compare(DcIacBiasRecommendation a, DcIacBiasRecommendation b) {
                return Double.compare(a.getCenterTarget(), b.getCenterTarget());
            }
        });
        return result;
    }

    private static DcIacBiasRecommendation summarize(
            Group group,
            DcIacBiasCharacterizationSettings settings) {
        List<Double> targets = new ArrayList<Double>();
        List<Double> current = new ArrayList<Double>();
        List<Double> required = new ArrayList<Double>();
        List<Double> corrections = new ArrayList<Double>();
        for (DcIacBiasEvidence row : group.rows) {
            targets.add(Double.valueOf(row.getTarget()));
            current.add(Double.valueOf(row.getConfiguredBias()));
            required.add(Double.valueOf(row.getRequiredBias()));
            corrections.add(Double.valueOf(row.getCorrection()));
        }

        double center = median(targets);
        double currentBias = median(current);
        double requiredBias = median(required);
        double rawCorrection = median(corrections);
        double mad = medianAbsoluteDeviation(corrections, rawCorrection);
        int count = group.rows.size();

        String status;
        double proposedCorrection = 0.0;
        String detail;
        if (count < settings.getMinimumEvidenceWindowsPerRecommendation()) {
            status = DcIacBiasRecommendation.COLLECT_MORE;
            detail = "Only " + count + " non-overlapping equilibrium window(s) support this local region; collect at least "
                    + settings.getMinimumEvidenceWindowsPerRecommendation() + " before a bias correction is proposed.";
        } else if (!finite(mad) || mad > settings.getMaximumCorrectionMad()) {
            status = DcIacBiasRecommendation.INCONSISTENT;
            detail = "Repeated equilibrium windows disagree by too much for a conservative local bias proposal.";
        } else if (!finite(rawCorrection) || Math.abs(rawCorrection) < settings.getMinimumActionableCorrection()) {
            status = DcIacBiasRecommendation.NO_CHANGE;
            detail = "Repeated equilibrium evidence does not justify a material local feed-forward change.";
        } else {
            status = DcIacBiasRecommendation.READY_LOCAL_BIAS;
            proposedCorrection = clamp(rawCorrection,
                    -settings.getMaximumCorrectionPerPass(), settings.getMaximumCorrectionPerPass());
            detail = "Repeated settled windows support a local feed-forward correction. This is an operating-point recommendation only; it must not be projected into unobserved bias-table knots automatically.";
        }

        double confidence = confidence(count, mad, settings);
        double proposedLocalBias = finite(currentBias) ? currentBias + proposedCorrection : Double.NaN;
        return new DcIacBiasRecommendation(
                status, group.segment, count, center,
                currentBias, requiredBias, rawCorrection,
                proposedCorrection, proposedLocalBias,
                mad, confidence, detail);
    }

    private static double confidence(int count, double mad, DcIacBiasCharacterizationSettings settings) {
        if (count <= 0) return 0.0;
        double value = 55.0 + Math.min(35.0, (count - 1) * 12.0);
        if (finite(mad)) value -= Math.min(35.0, mad / Math.max(0.01, settings.getMaximumCorrectionMad()) * 25.0);
        if (count < settings.getMinimumEvidenceWindowsPerRecommendation()) value = Math.min(value, 45.0);
        return clamp(value, 0.0, 95.0);
    }

    private static double medianAbsoluteDeviation(List<Double> values, double center) {
        if (values.isEmpty() || !finite(center)) return Double.NaN;
        List<Double> deviations = new ArrayList<Double>();
        for (Double value : values) deviations.add(Double.valueOf(Math.abs(value.doubleValue() - center)));
        return median(deviations);
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>(values);
        Collections.sort(copy);
        int middle = copy.size() / 2;
        if ((copy.size() & 1) == 1) return copy.get(middle).doubleValue();
        return (copy.get(middle - 1).doubleValue() + copy.get(middle).doubleValue()) * 0.5;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Group {
        private final String segment;
        private final List<DcIacBiasEvidence> rows = new ArrayList<DcIacBiasEvidence>();
        private Group(String segment) { this.segment = segment == null ? "" : segment; }
        private double center() {
            if (rows.isEmpty()) return Double.NaN;
            double sum = 0.0;
            for (DcIacBiasEvidence row : rows) sum += row.getTarget();
            return sum / rows.size();
        }
    }
}
