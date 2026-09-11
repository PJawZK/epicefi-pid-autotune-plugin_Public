package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.live.DcIacBiasEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import se.anders.tunerstudio.pidautotune.live.DcIacStaticEvidence;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Cross-window convergence for collected static DC-IAC evidence. */
public final class DcIacStaticRecommendationEngine {
    private static final double LOCAL_TOLERANCE = 1.50;
    private static final double MAX_MAD = 0.35;
    private static final double MAX_RANGE = 0.60;
    private static final double MAX_TREND_PER_SECOND = 0.03;
    private static final double MIN_ACTIONABLE = 0.50; // half of the 1-duty quantum: below this, nearest writable integer cannot change
    private static final double MAX_CORRECTION_PER_PASS = 6.0;
    private static final int MIN_USABLE_WINDOWS = 2;
    private static final int GOOD_WINDOW_COUNT = 3;
    private static final int MAX_CONVERGENCE_WINDOWS = 5;

    public List<DcIacStaticRecommendation> evaluate(List<DcIacStaticEvidence> evidence) {
        List<DcIacStaticEvidence> sorted = new ArrayList<DcIacStaticEvidence>();
        if (evidence != null) {
            for (DcIacStaticEvidence row : evidence) {
                if (row != null && row.getEvidence() != null && row.getEvidence().isRecommendationEligible()) {
                    sorted.add(row);
                }
            }
        }
        Collections.sort(sorted, new Comparator<DcIacStaticEvidence>() {
            @Override public int compare(DcIacStaticEvidence a, DcIacStaticEvidence b) {
                int c = a.getOperatingContext().compareTo(b.getOperatingContext());
                if (c != 0) return c;
                c = a.getCurveSegment().compareTo(b.getCurveSegment());
                return c != 0 ? c : Double.compare(a.getTarget(), b.getTarget());
            }
        });

        List<Group> groups = new ArrayList<Group>();
        for (DcIacStaticEvidence row : sorted) {
            Group best = null;
            double bestDistance = Double.POSITIVE_INFINITY;
            for (Group group : groups) {
                if (group.context != row.getOperatingContext() || !group.segment.equals(row.getCurveSegment())) continue;
                double d = Math.abs(group.center() - row.getTarget());
                if (d <= LOCAL_TOLERANCE && d < bestDistance) {
                    best = group;
                    bestDistance = d;
                }
            }
            if (best == null) {
                best = new Group(row.getOperatingContext(), row.getCurveSegment());
                groups.add(best);
            }
            best.rows.add(row);
        }

        List<DcIacStaticRecommendation> result = new ArrayList<DcIacStaticRecommendation>();
        for (Group group : groups) result.add(summarize(group));
        Collections.sort(result, new Comparator<DcIacStaticRecommendation>() {
            @Override public int compare(DcIacStaticRecommendation a, DcIacStaticRecommendation b) {
                int c = a.getOperatingContext().compareTo(b.getOperatingContext());
                return c != 0 ? c : Double.compare(a.getCenterTarget(), b.getCenterTarget());
            }
        });
        return result;
    }

    private static DcIacStaticRecommendation summarize(Group group) {
        Collections.sort(group.rows, new Comparator<DcIacStaticEvidence>() {
            @Override public int compare(DcIacStaticEvidence a, DcIacStaticEvidence b) {
                return Double.compare(a.getEndSeconds(), b.getEndSeconds());
            }
        });

        List<DcIacStaticEvidence> selected = selectConvergedSuffix(group.rows);
        List<DcIacStaticEvidence> metrics = selected == null
                ? tail(group.rows, Math.min(MIN_USABLE_WINDOWS, group.rows.size()))
                : selected;

        double center = medianMetric(metrics, 0);
        double current = medianMetric(metrics, 1);
        double required = medianMetric(metrics, 2);
        double correction = medianMetric(metrics, 3);
        double mad = mad(metrics, correction);
        double range = range(metrics);
        double trend = trend(metrics);
        int total = group.rows.size();
        int used = metrics.size();
        String evidenceQuality = qualityLabel(metrics);

        String status;
        String detail;
        double proposed = 0.0;

        if (total < MIN_USABLE_WINDOWS) {
            status = DcIacStaticRecommendation.COLLECT_MORE;
            detail = "Evidence quality LOW: one usable window is retained, but a second repeated window is needed before a bias change can be distinguished from a one-off disturbance. Collection remains active.";
        } else if (selected == null) {
            status = DcIacStaticRecommendation.NOT_CONVERGED;
            detail = "Evidence quality " + evidenceQuality + ": collected windows are retained, but their inferred bias correction is still moving. Continue collecting; no data is discarded.";
        } else if (!finite(mad) || mad > MAX_MAD) {
            status = DcIacStaticRecommendation.INCONSISTENT;
            detail = "Evidence quality " + evidenceQuality + ": repeated windows disagree too much for a conservative bias change. The observations remain retained for comparison/history.";
        } else if (!finite(correction) || Math.abs(correction) < MIN_ACTIONABLE) {
            status = DcIacStaticRecommendation.NO_CHANGE;
            detail = "Evidence quality " + evidenceQuality + ": repeated static observations converge within half of the one-duty dcIdleBiasValues quantum. Rounding to the nearest writable whole number would not change the table, so this region is accepted at controller resolution and can clear the bias dependency for PID identification.";
        } else {
            status = DcIacStaticRecommendation.READY_LOCAL_BIAS;
            proposed = clamp(correction, -MAX_CORRECTION_PER_PASS, MAX_CORRECTION_PER_PASS);
            detail = "Evidence quality " + evidenceQuality + ": repeated observations converge on a local bias correction. The exact target does not need to equal a firmware knot; the curve projector fits the surrounding segment.";
        }

        return new DcIacStaticRecommendation(
                status, group.context, group.segment, used,
                center, current, required, correction, proposed,
                finite(current) ? current + proposed : Double.NaN,
                mad, range, trend,
                confidence(status, metrics, mad, range, trend, group.context),
                detail);
    }

    private static List<DcIacStaticEvidence> selectConvergedSuffix(List<DcIacStaticEvidence> rows) {
        if (rows.size() < MIN_USABLE_WINDOWS) return null;
        int earliest = Math.max(0, rows.size() - MAX_CONVERGENCE_WINDOWS);
        for (int start = earliest; start <= rows.size() - MIN_USABLE_WINDOWS; start++) {
            List<DcIacStaticEvidence> s = new ArrayList<DcIacStaticEvidence>(rows.subList(start, rows.size()));
            double c = medianMetric(s, 3);
            double m = mad(s, c);
            double r = range(s);
            double t = trend(s);
            if (finite(m) && m <= MAX_MAD && finite(r) && r <= MAX_RANGE
                    && finite(t) && Math.abs(t) <= MAX_TREND_PER_SECOND) return s;
        }
        return null;
    }

    private static String qualityLabel(List<DcIacStaticEvidence> rows) {
        if (rows == null || rows.isEmpty()) return "LOW";
        int good = 0, ok = 0;
        for (DcIacStaticEvidence row : rows) {
            String q = row.getEvidence().getQuality();
            if (DcIacBiasEvidence.QUALITY_GOOD.equals(q)) good++;
            else if (DcIacBiasEvidence.QUALITY_OK.equals(q)) ok++;
        }
        if (rows.size() >= GOOD_WINDOW_COUNT && good >= Math.max(2, rows.size() - 1)) return "GOOD";
        if (good + ok == rows.size()) return "OK";
        return "LOW";
    }

    private static List<DcIacStaticEvidence> tail(List<DcIacStaticEvidence> rows, int count) {
        return count <= 0 ? new ArrayList<DcIacStaticEvidence>()
                : new ArrayList<DcIacStaticEvidence>(rows.subList(Math.max(0, rows.size() - count), rows.size()));
    }

    private static double medianMetric(List<DcIacStaticEvidence> rows, int which) {
        List<Double> values = new ArrayList<Double>();
        for (DcIacStaticEvidence row : rows) {
            double value = which == 0 ? row.getTarget()
                    : which == 1 ? row.getConfiguredBias()
                    : which == 2 ? row.getRequiredBias()
                    : row.getCorrection();
            values.add(Double.valueOf(value));
        }
        return median(values);
    }

    private static double mad(List<DcIacStaticEvidence> rows, double center) {
        if (!finite(center) || rows.isEmpty()) return Double.NaN;
        List<Double> values = new ArrayList<Double>();
        for (DcIacStaticEvidence row : rows) values.add(Double.valueOf(Math.abs(row.getCorrection() - center)));
        return median(values);
    }

    private static double range(List<DcIacStaticEvidence> rows) {
        if (rows.isEmpty()) return Double.NaN;
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        for (DcIacStaticEvidence row : rows) {
            min = Math.min(min, row.getCorrection());
            max = Math.max(max, row.getCorrection());
        }
        return max - min;
    }

    private static double trend(List<DcIacStaticEvidence> rows) {
        if (rows.size() < 2) return 0.0;
        double origin = rows.get(0).getEndSeconds();
        double st = 0.0, sv = 0.0, stt = 0.0, stv = 0.0;
        int n = 0;
        for (DcIacStaticEvidence row : rows) {
            double t = row.getEndSeconds() - origin;
            double v = row.getCorrection();
            st += t; sv += v; stt += t * t; stv += t * v; n++;
        }
        double den = n * stt - st * st;
        return Math.abs(den) < 1e-12 ? 0.0 : (n * stv - st * sv) / den;
    }

    private static double confidence(String status, List<DcIacStaticEvidence> rows,
                                     double mad, double range, double trend,
                                     DcIacOperatingContext context) {
        int count = rows == null ? 0 : rows.size();
        double value = count >= GOOD_WINDOW_COUNT ? 65.0 : 50.0;
        value += Math.min(20.0, Math.max(0, count - 2) * 8.0);
        if (rows != null) {
            for (DcIacStaticEvidence row : rows) {
                if (row.getEvidence().isGoodQuality()) value += 3.0;
                else if (row.getEvidence().isOkQuality()) value -= 2.0;
            }
        }
        if (finite(mad)) value -= Math.min(16.0, mad / MAX_MAD * 10.0);
        if (finite(range)) value -= Math.min(10.0, range / MAX_RANGE * 6.0);
        if (finite(trend)) value -= Math.min(12.0, Math.abs(trend) / MAX_TREND_PER_SECOND * 6.0);
        if (context == DcIacOperatingContext.RUNNING_IDLE) value += 8.0;
        else if (context == DcIacOperatingContext.ENGINE_OFF) value -= 8.0;
        if (DcIacStaticRecommendation.COLLECT_MORE.equals(status)
                || DcIacStaticRecommendation.NOT_CONVERGED.equals(status)) value = Math.min(value, 49.0);
        return clamp(value, 0.0, 95.0);
    }

    private static double median(List<Double> values) {
        if (values == null || values.isEmpty()) return Double.NaN;
        List<Double> copy = new ArrayList<Double>(values);
        Collections.sort(copy);
        int middle = copy.size() / 2;
        return (copy.size() & 1) == 1
                ? copy.get(middle).doubleValue()
                : (copy.get(middle - 1).doubleValue() + copy.get(middle).doubleValue()) * 0.5;
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class Group {
        private final DcIacOperatingContext context;
        private final String segment;
        private final List<DcIacStaticEvidence> rows = new ArrayList<DcIacStaticEvidence>();
        private Group(DcIacOperatingContext context, String segment) {
            this.context = context == null ? DcIacOperatingContext.UNKNOWN : context;
            this.segment = segment == null ? "" : segment;
        }
        private double center() {
            if (rows.isEmpty()) return Double.NaN;
            double sum = 0.0;
            for (DcIacStaticEvidence row : rows) sum += row.getTarget();
            return sum / rows.size();
        }
    }
}
