package se.anders.tunerstudio.pidautotune.recommendation;

import se.anders.tunerstudio.pidautotune.live.DcIacDynamicEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurement;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Guided-only DC-IAC PID evaluator.
 *
 * Unlike the autonomous pipeline, Guided task order is user-authoritative.  An unresolved
 * feed-forward/bias region is therefore an advisory confidence penalty, not a hard PID gate.
 * The dynamic-response rules remain conservative and the result always records the observed
 * bias state so the UI can recommend the related Bias Curve task when it may contaminate the
 * interpretation.
 */
public final class DcIacGuidedPidRecommendationEngine {
    private static final double LOCAL_CENTER_TOLERANCE = 2.50;
    private static final double STATIC_MATCH_TOLERANCE = 2.00;
    private static final double MAX_INITIAL_I_SPAN = 2.50;
    private static final double HIGH_OVERSHOOT = 25.0;
    private static final double SEVERE_OVERSHOOT = 45.0;
    private static final double CLEAN_OVERSHOOT = 10.0;
    private static final double HIGH_SS_ERROR = .40;
    private static final double SLOW_SETTLING = .25;
    private static final double EDGE_DUTY_CAUTION = 80.0;
    private static final double NOMINAL_LOOP_SECONDS = .002;
    private static final int MIN_LOCAL_STEPS = 4;
    private static final int MIN_OPENING = 2;
    private static final int MIN_CLOSING = 2;
    private static final int MIN_QUANTITATIVE_FOR_INCREASE = 3;

    public List<DcIacPidRecommendation> evaluate(List<DcIacDynamicEvidence> evidence,
                                                  List<DcIacStaticRecommendation> statics,
                                                  double p, double i, double d) {
        List<DcIacDynamicEvidence> usable = new ArrayList<DcIacDynamicEvidence>();
        if (evidence != null) for (DcIacDynamicEvidence row : evidence) if (row != null && row.isSafeLocalStep()) usable.add(row);
        Collections.sort(usable, new Comparator<DcIacDynamicEvidence>() {
            @Override public int compare(DcIacDynamicEvidence a, DcIacDynamicEvidence b) {
                int c = a.getOperatingContext().compareTo(b.getOperatingContext());
                return c != 0 ? c : Double.compare(a.getMeasurement().getOperatingCenterTarget(), b.getMeasurement().getOperatingCenterTarget());
            }
        });

        List<Group> groups = new ArrayList<Group>();
        for (DcIacDynamicEvidence row : usable) {
            Group best = null; double bestDistance = Double.POSITIVE_INFINITY;
            double center = row.getMeasurement().getOperatingCenterTarget();
            for (Group group : groups) {
                if (group.context != row.getOperatingContext()) continue;
                double distance = Math.abs(group.center() - center);
                if (distance <= LOCAL_CENTER_TOLERANCE && distance < bestDistance) { best = group; bestDistance = distance; }
            }
            if (best == null) { best = new Group(row.getOperatingContext()); groups.add(best); }
            best.rows.add(row);
        }

        List<DcIacPidRecommendation> result = new ArrayList<DcIacPidRecommendation>();
        for (Group group : groups) result.add(summarize(group, statics, p, i, d));
        Collections.sort(result, new Comparator<DcIacPidRecommendation>() {
            @Override public int compare(DcIacPidRecommendation a, DcIacPidRecommendation b) {
                int c = a.getOperatingContext().compareTo(b.getOperatingContext());
                return c != 0 ? c : Double.compare(a.getCenterTarget(), b.getCenterTarget());
            }
        });
        return result;
    }

    private static DcIacPidRecommendation summarize(Group group, List<DcIacStaticRecommendation> statics,
                                                     double p, double i, double d) {
        double center = group.center();
        int quantitative = 0, limited = 0, opening = 0, closing = 0, reversalEvents = 0;
        List<Double> over = new ArrayList<Double>(), qOver = new ArrayList<Double>(), ss = new ArrayList<Double>(),
                settle = new ArrayList<Double>(), rise = new ArrayList<Double>(), peak = new ArrayList<Double>(),
                initialI = new ArrayList<Double>(), edge = new ArrayList<Double>();
        for (DcIacDynamicEvidence row : group.rows) {
            DcIacMeasurement m = row.getMeasurement();
            if (row.getTier() == DcIacDynamicEvidence.Tier.QUANTITATIVE) quantitative++;
            if (row.getTier() == DcIacDynamicEvidence.Tier.SOURCE_LIMITED_SUPPORT) limited++;
            if (row.isOpening()) opening++;
            if (row.isClosing()) closing++;
            add(over, Math.abs(m.getOvershootPercentOfStep()));
            if (row.getTier() == DcIacDynamicEvidence.Tier.QUANTITATIVE) {
                add(qOver, Math.abs(m.getOvershootPercentOfStep())); add(settle, m.getSettlingSeconds()); add(rise, m.getRiseOrFallSeconds());
            }
            add(ss, Math.abs(m.getSteadyStateError())); add(peak, m.getPeakAbsoluteDuty());
            add(initialI, m.getInitialITerm()); add(edge, Math.abs(m.getPredictedCommandEdgeDuty()));
            if (m.getDutyReversals() >= 2) reversalEvents++;
        }

        double medOver = median(over), medQOver = median(qOver), medSs = median(ss), medSettle = median(settle),
                medRise = median(rise), medPeak = median(peak), medInitialI = medianAbs(initialI),
                initialISpan = range(initialI), maxEdge = max(edge);
        double kick = finite(p) && Math.abs(p) > 1e-9 && finite(d) ? Math.abs(d / NOMINAL_LOOP_SECONDS / p) : Double.NaN;

        DcIacStaticRecommendation bias = nearestStatic(statics, group.context, center);
        String biasStatus = bias == null ? "MISSING" : bias.getStatus();
        boolean biasAdvisory = bias == null || !bias.isBiasReadyForPid();
        String biasNote = biasAdvisory ? biasAdvisoryText(bias) : "";
        String status, action = "None", detail; double pc = 0, ic = 0, dc = 0;

        if (group.context != DcIacOperatingContext.RUNNING_IDLE) {
            status = DcIacPidRecommendation.BLOCKED_CONTEXT;
            detail = "Dynamic evidence is retained, but this region is not running-idle evidence. Guided task order is unrestricted; this particular operating context is not suitable for an idle-position gain recommendation.";
        } else if (!finite(p) || !finite(i) || !finite(d)) {
            status = DcIacPidRecommendation.COLLECT_MORE;
            detail = "Configured DC-IAC P/I/D values are unavailable, so the measured response can be shown but a numeric gain candidate cannot yet be calculated." + biasNote;
        } else if (group.rows.size() < MIN_LOCAL_STEPS || opening < MIN_OPENING || closing < MIN_CLOSING) {
            status = DcIacPidRecommendation.COLLECT_MORE;
            detail = "Collect at least four safe local steps, ideally two opening and two closing, around the same operating center. Bias state does not block this capture." + biasNote;
        } else if (finite(initialISpan) && initialISpan > MAX_INITIAL_I_SPAN) {
            status = DcIacPidRecommendation.INCONSISTENT;
            detail = "Initial I state varies substantially across the local steps. A PID result is still retained, but another comparable cycle would make attribution cleaner." + biasNote;
        } else {
            boolean harmful = finite(medOver) && medOver >= HIGH_OVERSHOOT;
            boolean severe = finite(medOver) && medOver >= SEVERE_OVERSHOOT;
            boolean reversal = reversalEvents >= Math.max(2, group.rows.size() / 2);
            boolean edgeCaution = finite(maxEdge) && maxEdge >= EDGE_DUTY_CAUTION;
            boolean dKickHigh = finite(kick) && kick >= .50 && d > 0;
            boolean allLimited = quantitative == 0 && limited > 0;

            if (dKickHigh && (harmful || reversal || edgeCaution)) {
                status = DcIacPidRecommendation.READY_PID; dc = severe || edgeCaution ? -20 : -15; action = "Reduce D";
                detail = "Overshoot/reversal/edge-duty evidence coincides with substantial D-on-error setpoint kick. Change D only, then repeat the same local test." + biasNote;
            } else if (harmful || reversal) {
                status = DcIacPidRecommendation.READY_PID; pc = severe ? -15 : -10; action = "Reduce P";
                detail = "Repeated safe local steps show enough underdamping to justify a bounded P-only reduction." + biasNote;
            } else if (allLimited) {
                status = DcIacPidRecommendation.SOURCE_LIMITED;
                detail = "Safe local events are repeatable but source-rate limited. They can support reductions when harmful behavior is observed, but absence of observed peaks should not be used to justify increasing a gain." + biasNote;
            } else if (quantitative < MIN_QUANTITATIVE_FOR_INCREASE) {
                status = DcIacPidRecommendation.COLLECT_MORE;
                detail = "More source-resolved quantitative steps would improve confidence before a gain increase. The current response data is retained." + biasNote;
            } else if (finite(medQOver) && medQOver <= CLEAN_OVERSHOOT && finite(medSettle) && medSettle >= SLOW_SETTLING && (!finite(maxEdge) || maxEdge < 70)) {
                status = DcIacPidRecommendation.READY_PID; pc = 5; action = "Increase P";
                detail = "Resolved local responses are clean but comparatively slow with ample predicted duty headroom. A small P-only increase is proposed." + biasNote;
            } else if (finite(medSs) && medSs >= HIGH_SS_ERROR && finite(medQOver) && medQOver <= CLEAN_OVERSHOOT) {
                status = DcIacPidRecommendation.READY_PID; ic = 10; action = "Increase I";
                detail = "Resolved responses are clean but retain repeatable position error, so a bounded I-only increase is proposed. Because steady error is especially sensitive to feed-forward quality, treat the Bias Curve advisory below as important context rather than a prerequisite." + biasNote;
            } else {
                status = DcIacPidRecommendation.NO_CHANGE;
                detail = "Current local P/I/D behavior does not justify a bounded one-axis change under the measured response rules." + biasNote;
            }
        }

        double proposedP = apply(p, pc), proposedI = apply(i, ic), proposedD = apply(d, dc);
        double confidence = confidence(status, group.rows.size(), quantitative, opening, closing, medOver, initialISpan, bias, biasAdvisory);
        return new DcIacPidRecommendation(status, group.context, center, group.rows.size(), quantitative, limited,
                opening, closing, biasStatus, p, i, d, proposedP, proposedI, proposedD, pc, ic, dc,
                medOver, medSs, medSettle, medRise, medPeak, medInitialI, kick, confidence, action, detail);
    }

    private static String biasAdvisoryText(DcIacStaticRecommendation bias) {
        if (bias == null) return "\n\nRelated-setting advisory: Bias Curve has not been characterized near this PID region. PID results are still reported, but feed-forward error can alter I-term and steady-error behavior; tune/verify Bias Curve later if you want cleaner attribution.";
        if (bias.isActionableBiasChange()) return "\n\nRelated-setting advisory: Bias Curve currently supports a local feed-forward correction near " + one(bias.getCenterTarget()) + "%. PID results are still reported, but this can shift I-term/steady-error behavior. Suggested follow-up: tune Bias Curve and then repeat the same PID test for a cleaner comparison.";
        return "\n\nRelated-setting advisory: Bias Curve is not yet verified near this PID region (" + bias.getStatus() + "). This does not block Guided PID tuning; it only lowers confidence because feed-forward state may influence the measured result.";
    }

    private static DcIacStaticRecommendation nearestStatic(List<DcIacStaticRecommendation> rows, DcIacOperatingContext context, double target) {
        DcIacStaticRecommendation best = null; double bestDistance = Double.POSITIVE_INFINITY;
        if (rows == null) return null;
        for (DcIacStaticRecommendation row : rows) {
            if (row.getOperatingContext() != context) continue;
            double distance = Math.abs(row.getCenterTarget() - target);
            if (distance <= STATIC_MATCH_TOLERANCE && distance < bestDistance) { best = row; bestDistance = distance; }
        }
        return best;
    }

    private static double confidence(String status, int count, int quantitative, int opening, int closing,
                                     double over, double iSpan, DcIacStaticRecommendation bias, boolean biasAdvisory) {
        double value = 45 + Math.min(24, Math.max(0, count - 3) * 6) + Math.min(18, quantitative * 4);
        if (opening >= 2 && closing >= 2) value += 8;
        if (bias != null && bias.isBiasReadyForPid()) value += Math.min(8, bias.getConfidencePercent() / 20);
        if (biasAdvisory) value -= bias != null && bias.isActionableBiasChange() ? 18 : 10;
        if (finite(iSpan)) value -= Math.min(12, iSpan / MAX_INITIAL_I_SPAN * 8);
        if (DcIacPidRecommendation.BLOCKED_CONTEXT.equals(status) || DcIacPidRecommendation.COLLECT_MORE.equals(status) || DcIacPidRecommendation.SOURCE_LIMITED.equals(status)) value = Math.min(value, 49);
        if (DcIacPidRecommendation.INCONSISTENT.equals(status)) value = Math.min(value, 35);
        if (finite(over) && over >= SEVERE_OVERSHOOT && DcIacPidRecommendation.READY_PID.equals(status)) value += 5;
        return clamp(value, 0, 95);
    }

    private static double apply(double value, double percent) { return finite(value) ? value * (1 + percent / 100) : Double.NaN; }
    private static void add(List<Double> values, double value) { if (finite(value)) values.add(Double.valueOf(value)); }
    private static double median(List<Double> values) { if (values == null || values.isEmpty()) return Double.NaN; List<Double> copy = new ArrayList<Double>(values); Collections.sort(copy); int m = copy.size()/2; return (copy.size()&1)==1 ? copy.get(m).doubleValue() : (copy.get(m-1).doubleValue()+copy.get(m).doubleValue())*.5; }
    private static double medianAbs(List<Double> values) { List<Double> abs = new ArrayList<Double>(); if (values != null) for (Double value : values) if (value != null && finite(value.doubleValue())) abs.add(Double.valueOf(Math.abs(value.doubleValue()))); return median(abs); }
    private static double range(List<Double> values) { if (values == null || values.isEmpty()) return Double.NaN; double low=Double.POSITIVE_INFINITY, high=Double.NEGATIVE_INFINITY; int n=0; for(Double value:values) if(value!=null&&finite(value.doubleValue())){low=Math.min(low,value.doubleValue());high=Math.max(high,value.doubleValue());n++;} return n==0?Double.NaN:high-low; }
    private static double max(List<Double> values) { double result=Double.NaN; if(values!=null)for(Double value:values)if(value!=null&&finite(value.doubleValue())&&(!finite(result)||value.doubleValue()>result))result=value.doubleValue(); return result; }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static String one(double value) { return String.format(java.util.Locale.US, "%.1f", value); }

    private static final class Group {
        private final DcIacOperatingContext context;
        private final List<DcIacDynamicEvidence> rows = new ArrayList<DcIacDynamicEvidence>();
        private Group(DcIacOperatingContext context) { this.context = context == null ? DcIacOperatingContext.UNKNOWN : context; }
        private double center() { double sum=0;int n=0;for(DcIacDynamicEvidence row:rows){double value=row.getMeasurement().getOperatingCenterTarget();if(finite(value)){sum+=value;n++;}}return n==0?Double.NaN:sum/n; }
    }
}
