package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Projects converged local bias corrections onto the existing firmware knots.
 *
 * 0.5.21 removes the old requirement that one measured region must land within 0.75 %-point
 * of an exact knot. Each observation is instead treated as an interpolation equation between
 * the two surrounding firmware knots, so the actual settled target can be used directly.
 */
public final class DcIacBiasCurveProposalEngine {
    private static final double MAX_CORRECTION_PER_PASS = 6.0;
    private static final double RIDGE = 0.04;

    public List<DcIacBiasKnotProposal> evaluate(
            DcIacBiasSettingsAccess.Snapshot bias,
            List<DcIacStaticRecommendation> recommendations) {
        List<DcIacBiasKnotProposal> result = new ArrayList<DcIacBiasKnotProposal>();
        if (bias == null) return result;

        List<DcIacStaticRecommendation> usable = new ArrayList<DcIacStaticRecommendation>();
        if (recommendations != null) {
            for (DcIacStaticRecommendation rec : recommendations) {
                if (rec == null || rec.getOperatingContext() != DcIacOperatingContext.RUNNING_IDLE) continue;
                if (DcIacStaticRecommendation.READY_LOCAL_BIAS.equals(rec.getStatus())
                        || DcIacStaticRecommendation.NO_CHANGE.equals(rec.getStatus())) {
                    usable.add(rec);
                }
            }
        }

        final int n = bias.size();
        if (n == 0) return result;
        double[][] normal = new double[n][n];
        double[] rhs = new double[n];
        double[] supportWeight = new double[n];
        int[] supportCount = new int[n];
        double[] confidenceWeight = new double[n];

        for (int i = 0; i < n; i++) normal[i][i] += RIDGE;

        for (DcIacStaticRecommendation rec : usable) {
            double target = rec.getCenterTarget();
            if (!finite(target)) continue;
            int[] bracket = bracket(bias, target);
            if (bracket == null) continue;
            int lo = bracket[0];
            int hi = bracket[1];
            double loPos = bias.getBin(lo);
            double hiPos = bias.getBin(hi);
            double span = hiPos - loPos;
            double alpha = hi == lo || Math.abs(span) < 1e-9 ? 0.0 : clamp((target - loPos) / span, 0.0, 1.0);
            double aLo = hi == lo ? 1.0 : 1.0 - alpha;
            double aHi = hi == lo ? 0.0 : alpha;
            double correction = DcIacStaticRecommendation.NO_CHANGE.equals(rec.getStatus())
                    ? 0.0 : rec.getProposedCorrection();
            if (!finite(correction)) continue;
            double confidence = clamp(rec.getConfidencePercent(), 10.0, 95.0) / 100.0;
            double evidenceBoost = Math.min(1.6, 0.75 + 0.15 * Math.max(1, rec.getEvidenceWindows()));
            double w = Math.max(0.08, confidence * evidenceBoost);

            accumulate(normal, rhs, lo, lo, aLo, aLo, correction, w);
            if (hi != lo) {
                accumulate(normal, rhs, lo, hi, aLo, aHi, correction, w);
                accumulate(normal, rhs, hi, lo, aHi, aLo, correction, w);
                accumulate(normal, rhs, hi, hi, aHi, aHi, correction, w);
            }

            if (Math.abs(aLo) > 1e-9) {
                supportWeight[lo] += w * Math.abs(aLo);
                supportCount[lo]++;
                confidenceWeight[lo] += w * Math.abs(aLo) * rec.getConfidencePercent();
            }
            if (hi != lo && Math.abs(aHi) > 1e-9) {
                supportWeight[hi] += w * Math.abs(aHi);
                supportCount[hi]++;
                confidenceWeight[hi] += w * Math.abs(aHi) * rec.getConfidencePercent();
            }
        }

        double[] solved = solve(normal, rhs);
        for (int i = 0; i < n; i++) {
            double position = bias.getBin(i);
            double current = bias.getValue(i);
            if (supportCount[i] == 0 || supportWeight[i] <= 1e-9 || solved == null || !finite(solved[i])) {
                result.add(new DcIacBiasKnotProposal(
                        i, position, current, current, 0.0, 0, 0.0,
                        DcIacBiasKnotProposal.COLLECT_MORE,
                        "Evidence quality LOW for this knot: no measured running-idle region currently influences it. Collection may continue anywhere in the curve; exact knot targeting is not required."));
                continue;
            }

            double raw = solved[i];
            double boundedCorrection = clamp(raw, -MAX_CORRECTION_PER_PASS, MAX_CORRECTION_PER_PASS);
            int decimals = bias.getValueDecimalPlaces();
            double quantum = bias.getValueQuantum();
            double mathematical = current + boundedCorrection;
            double proposed = quantize(mathematical, decimals);
            double correction = proposed - current;
            if (sameAtPrecision(proposed, current, decimals)) {
                proposed = current;
                correction = 0.0;
            }

            double averageConfidence = confidenceWeight[i] / supportWeight[i];
            String quality = supportCount[i] >= 2 && averageConfidence >= 72.0 ? "GOOD" : "OK";
            double confidence = clamp(averageConfidence
                    + Math.min(8.0, supportCount[i] * 2.0)
                    - ("OK".equals(quality) ? 8.0 : 0.0), 20.0, 95.0);
            String status = correction == 0.0
                    ? DcIacBiasKnotProposal.NO_CHANGE
                    : DcIacBiasKnotProposal.READY_KNOT;
            String detail;
            if (correction == 0.0) {
                detail = "Evidence quality " + quality + ": the segment-fit mathematical target is "
                        + fmt(mathematical, Math.max(decimals + 2, 2)) + ", but the active ECU definition can write this bias only in "
                        + fmt(quantum, decimals) + "-duty increments. The nearest writable value is already "
                        + fmt(current, decimals) + "; treat this region as converged at controller resolution rather than chasing an exact sensor position.";
            } else {
                detail = "Evidence quality " + quality + ": actual settled targets were projected through the surrounding firmware segment. "
                        + "Mathematical target " + fmt(mathematical, Math.max(decimals + 2, 2))
                        + " is quantized to the controller-supported value " + fmt(proposed, decimals)
                        + " (quantum " + fmt(quantum, decimals) + "). Apply that writable value, then recheck.";
            }
            result.add(new DcIacBiasKnotProposal(
                    i, position, current, proposed, correction,
                    supportCount[i], confidence, status, detail));
        }
        return result;
    }

    private static int[] bracket(DcIacBiasSettingsAccess.Snapshot bias, double target) {
        int n = bias.size();
        if (n == 0) return null;
        if (target <= bias.getBin(0)) return new int[] {0, 0};
        if (target >= bias.getBin(n - 1)) return new int[] {n - 1, n - 1};
        for (int i = 0; i < n - 1; i++) {
            double a = bias.getBin(i), b = bias.getBin(i + 1);
            if (target >= a && target <= b) return new int[] {i, i + 1};
        }
        return null;
    }

    private static void accumulate(double[][] normal, double[] rhs,
                                   int row, int col,
                                   double aRow, double aCol,
                                   double correction, double weight) {
        normal[row][col] += weight * aRow * aCol;
        if (row == col || Math.abs(aRow) > 1e-12) {
            // rhs is A^T W y; add once per row contribution, not once per matrix term.
            // The row diagonal call is always issued, so use it as the rhs accumulation point.
            if (row == col) rhs[row] += weight * aRow * correction;
        }
    }

    private static double[] solve(double[][] input, double[] rhsInput) {
        int n = rhsInput.length;
        double[][] a = new double[n][n + 1];
        for (int r = 0; r < n; r++) {
            System.arraycopy(input[r], 0, a[r], 0, n);
            a[r][n] = rhsInput[r];
        }
        for (int col = 0; col < n; col++) {
            int pivot = col;
            for (int r = col + 1; r < n; r++) {
                if (Math.abs(a[r][col]) > Math.abs(a[pivot][col])) pivot = r;
            }
            if (Math.abs(a[pivot][col]) < 1e-10) continue;
            if (pivot != col) {
                double[] tmp = a[pivot]; a[pivot] = a[col]; a[col] = tmp;
            }
            double div = a[col][col];
            for (int c = col; c <= n; c++) a[col][c] /= div;
            for (int r = 0; r < n; r++) {
                if (r == col) continue;
                double factor = a[r][col];
                if (Math.abs(factor) < 1e-12) continue;
                for (int c = col; c <= n; c++) a[r][c] -= factor * a[col][c];
            }
        }
        double[] out = new double[n];
        for (int i = 0; i < n; i++) out[i] = a[i][n];
        return out;
    }

    private static double quantize(double value, int decimalPlaces) {
        double factor = Math.pow(10.0, Math.max(0, decimalPlaces));
        return Math.round(value * factor) / factor;
    }

    private static boolean sameAtPrecision(double a, double b, int decimalPlaces) {
        double tolerance = 0.5 * Math.pow(10.0, -Math.max(0, decimalPlaces)) + 1e-9;
        return Math.abs(a - b) < tolerance;
    }

    private static String fmt(double value, int decimalPlaces) {
        if (!finite(value)) return "—";
        int places = Math.max(0, Math.min(6, decimalPlaces));
        return String.format(java.util.Locale.ROOT, "%." + places + "f", value);
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
