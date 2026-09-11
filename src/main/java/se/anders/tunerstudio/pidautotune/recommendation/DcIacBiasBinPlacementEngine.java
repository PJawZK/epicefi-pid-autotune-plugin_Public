/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext
 *  se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendation
 */
package se.anders.tunerstudio.pidautotune.recommendation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import se.anders.tunerstudio.pidautotune.controller.DcIacRamWriteCoordinator;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasBinPlacementProposal;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendation;

public final class DcIacBiasBinPlacementEngine {
    static final int MIN_WINDOWS = 3;
    static final double MIN_CONFIDENCE = 80.0;
    static final double MIN_MOVE = 1.0;
    static final double MAX_MOVE = 3.0;
    static final double MIN_NEIGHBOR_SPACING = 1.5;
    static final double PROTECTED_REGION_RADIUS = 1.0;
    static final double SAME_REGION_RADIUS = 0.75;
    static final double MAX_MAD = 0.35;
    static final double MAX_RANGE = 0.6;
    static final double MAX_TREND = 0.03;

    public DcIacBiasBinPlacementProposal evaluate(DcIacRamWriteCoordinator.BiasSnapshot biasSnapshot, List<DcIacStaticRecommendation> list, Set<Integer> set) {
        if (biasSnapshot == null || biasSnapshot.size() < 3) {
            return DcIacBiasBinPlacementEngine.blocked(-1, Double.NaN, "At least three paired bias points are required before an interior bin can be relocated.");
        }
        List<DcIacStaticRecommendation> list2 = list == null ? Collections.<DcIacStaticRecommendation>emptyList() : list;
        DcIacBiasBinPlacementProposal dcIacBiasBinPlacementProposal = null;
        DcIacBiasBinPlacementProposal dcIacBiasBinPlacementProposal2 = null;
        double d = Double.NEGATIVE_INFINITY;
        for (DcIacStaticRecommendation dcIacStaticRecommendation : list2) {
            if (dcIacStaticRecommendation == null || dcIacStaticRecommendation.getOperatingContext() != DcIacOperatingContext.RUNNING_IDLE) continue;
            DcIacBiasBinPlacementProposal dcIacBiasBinPlacementProposal3 = this.evaluateOne(biasSnapshot, list2, dcIacStaticRecommendation, set);
            if (dcIacBiasBinPlacementProposal3.isReady()) {
                double d2 = Math.abs(dcIacBiasBinPlacementProposal3.getProposedBin() - dcIacBiasBinPlacementProposal3.getCurrentBin());
                double d3 = dcIacBiasBinPlacementProposal3.getConfidencePercent() + Math.min(10.0, (double)dcIacBiasBinPlacementProposal3.getEvidenceWindows() * 2.0) + d2 * 3.0;
                if (dcIacBiasBinPlacementProposal != null && !(d3 > d)) continue;
                dcIacBiasBinPlacementProposal = dcIacBiasBinPlacementProposal3;
                d = d3;
                continue;
            }
            if (dcIacBiasBinPlacementProposal2 != null && DcIacBiasBinPlacementEngine.diagnosticRank(dcIacBiasBinPlacementProposal3) <= DcIacBiasBinPlacementEngine.diagnosticRank(dcIacBiasBinPlacementProposal2)) continue;
            dcIacBiasBinPlacementProposal2 = dcIacBiasBinPlacementProposal3;
        }
        return dcIacBiasBinPlacementProposal != null ? dcIacBiasBinPlacementProposal : dcIacBiasBinPlacementProposal2;
    }

    private DcIacBiasBinPlacementProposal evaluateOne(DcIacRamWriteCoordinator.BiasSnapshot biasSnapshot, List<DcIacStaticRecommendation> list, DcIacStaticRecommendation dcIacStaticRecommendation, Set<Integer> set) {
        double d = dcIacStaticRecommendation.getCenterTarget();
        if (!DcIacBiasBinPlacementEngine.finite(d)) {
            return DcIacBiasBinPlacementEngine.blocked(-1, d, "Stable target is unavailable, so bin placement cannot be evaluated.");
        }
        if (!"NO_CHANGE".equals(dcIacStaticRecommendation.getStatus())) {
            return DcIacBiasBinPlacementEngine.collect(-1, d, dcIacStaticRecommendation, "Resolve the local feed-forward correction first. Bin relocation is only considered after the existing static engine reports NO_CHANGE.");
        }
        if (dcIacStaticRecommendation.getEvidenceWindows() < 3) {
            return DcIacBiasBinPlacementEngine.collect(-1, d, dcIacStaticRecommendation, "Collect at least three converged equilibrium windows before moving a bin.");
        }
        if (dcIacStaticRecommendation.getConfidencePercent() < 80.0) {
            return DcIacBiasBinPlacementEngine.collect(-1, d, dcIacStaticRecommendation, "Bin relocation requires at least 80% confidence from the converged static evidence.");
        }
        if (!DcIacBiasBinPlacementEngine.finite(dcIacStaticRecommendation.getCorrectionMad()) || dcIacStaticRecommendation.getCorrectionMad() > 0.35 || !DcIacBiasBinPlacementEngine.finite(dcIacStaticRecommendation.getCorrectionRange()) || dcIacStaticRecommendation.getCorrectionRange() > 0.6 || !DcIacBiasBinPlacementEngine.finite(dcIacStaticRecommendation.getCorrectionTrendPerSecond()) || Math.abs(dcIacStaticRecommendation.getCorrectionTrendPerSecond()) > 0.03) {
            return DcIacBiasBinPlacementEngine.collect(-1, d, dcIacStaticRecommendation, "The local bias evidence is not stable enough for X-axis placement even though a summary is available.");
        }
        int n = DcIacBiasBinPlacementEngine.nearestInterior(biasSnapshot, d);
        if (n < 1 || n >= biasSnapshot.size() - 1) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "The nearest usable knot would be an endpoint. End bins are never moved autonomously.");
        }
        double d2 = biasSnapshot.getBin(n);
        double d3 = Math.abs(d - d2);
        if (d3 < 1.0) {
            return new DcIacBiasBinPlacementProposal(n, d2, d2, biasSnapshot.get(n), biasSnapshot.get(n), d, dcIacStaticRecommendation.getEvidenceWindows(), dcIacStaticRecommendation.getConfidencePercent(), "ALIGNED", "The nearest interior knot is already within 1.0 percentage point of the proven operating target; leave the X-axis unchanged.");
        }
        if (d3 > 3.0) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "The nearest interior knot is " + DcIacBiasBinPlacementEngine.one(d3) + " percentage points away. Autonomous placement is limited to a 3.0-point local relocation; repositioning farther than this needs deliberate curve redesign.");
        }
        if (set != null && set.contains(n)) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "This knot has already been relocated once in the current autonomous session; repeated X-axis movement is blocked to prevent hunting.");
        }
        double d4 = biasSnapshot.getBin(n - 1);
        double d5 = biasSnapshot.getBin(n + 1);
        if (d - d4 < 1.5 || d5 - d < 1.5) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "Moving this knot to the observed target would crowd a neighbour closer than 1.5 percentage points.");
        }
        DcIacStaticRecommendation dcIacStaticRecommendation2 = DcIacBiasBinPlacementEngine.protectedRegion(list, dcIacStaticRecommendation, d2);
        if (dcIacStaticRecommendation2 != null) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "The existing knot near " + DcIacBiasBinPlacementEngine.one(d2) + "% already has its own qualified running-idle evidence around " + DcIacBiasBinPlacementEngine.one(dcIacStaticRecommendation2.getCenterTarget()) + "%. Preserve that supported knot instead of stealing it for this region.");
        }
        double d6 = dcIacStaticRecommendation.getCurrentBias();
        if (!DcIacBiasBinPlacementEngine.finite(d6)) {
            return DcIacBiasBinPlacementEngine.blocked(n, d, "The configured local bias is unavailable, so a behavior-preserving point relocation cannot be formed.");
        }
        return new DcIacBiasBinPlacementProposal(n, d2, d, biasSnapshot.get(n), d6, d, dcIacStaticRecommendation.getEvidenceWindows(), dcIacStaticRecommendation.getConfidencePercent(), "READY_MOVE", "Converged NO_CHANGE evidence repeatedly centers at " + DcIacBiasBinPlacementEngine.one(d) + "%. Move interior knot " + n + " from " + DcIacBiasBinPlacementEngine.one(d2) + "% to that proven operating point and carry the already-correct local feed-forward value with it. Recheck the same target before PID tuning.");
    }

    private static DcIacStaticRecommendation protectedRegion(List<DcIacStaticRecommendation> list, DcIacStaticRecommendation dcIacStaticRecommendation, double d) {
        for (DcIacStaticRecommendation dcIacStaticRecommendation2 : list) {
            if (dcIacStaticRecommendation2 == null || dcIacStaticRecommendation2 == dcIacStaticRecommendation || dcIacStaticRecommendation2.getOperatingContext() != DcIacOperatingContext.RUNNING_IDLE || !"NO_CHANGE".equals(dcIacStaticRecommendation2.getStatus()) || dcIacStaticRecommendation2.getEvidenceWindows() < 3 || dcIacStaticRecommendation2.getConfidencePercent() < 80.0 || Math.abs(dcIacStaticRecommendation2.getCenterTarget() - dcIacStaticRecommendation.getCenterTarget()) <= 0.75 || !(Math.abs(dcIacStaticRecommendation2.getCenterTarget() - d) <= 1.0)) continue;
            return dcIacStaticRecommendation2;
        }
        return null;
    }

    private static int nearestInterior(DcIacRamWriteCoordinator.BiasSnapshot biasSnapshot, double d) {
        int n = -1;
        double d2 = Double.POSITIVE_INFINITY;
        for (int i = 1; i < biasSnapshot.size() - 1; ++i) {
            double d3 = Math.abs(biasSnapshot.getBin(i) - d);
            if (!(d3 < d2)) continue;
            d2 = d3;
            n = i;
        }
        return n;
    }

    private static int diagnosticRank(DcIacBiasBinPlacementProposal dcIacBiasBinPlacementProposal) {
        if (dcIacBiasBinPlacementProposal == null) {
            return -1;
        }
        if ("ALIGNED".equals(dcIacBiasBinPlacementProposal.getStatus())) {
            return 3;
        }
        if ("COLLECT_MORE".equals(dcIacBiasBinPlacementProposal.getStatus())) {
            return 2;
        }
        if ("BLOCKED".equals(dcIacBiasBinPlacementProposal.getStatus())) {
            return 1;
        }
        return 0;
    }

    private static DcIacBiasBinPlacementProposal collect(int n, double d, DcIacStaticRecommendation dcIacStaticRecommendation, String string) {
        return new DcIacBiasBinPlacementProposal(n, Double.NaN, Double.NaN, Double.NaN, Double.NaN, d, dcIacStaticRecommendation == null ? 0 : dcIacStaticRecommendation.getEvidenceWindows(), dcIacStaticRecommendation == null ? 0.0 : dcIacStaticRecommendation.getConfidencePercent(), "COLLECT_MORE", string);
    }

    private static DcIacBiasBinPlacementProposal blocked(int n, double d, String string) {
        return new DcIacBiasBinPlacementProposal(n, Double.NaN, Double.NaN, Double.NaN, Double.NaN, d, 0, 0.0, "BLOCKED", string);
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static String one(double d) {
        return DcIacBiasBinPlacementEngine.finite(d) ? String.format(Locale.ROOT, "%.2f", d) : "\u2014";
    }
}

