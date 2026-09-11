package se.anders.tunerstudio.pidautotune.dataset;

import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;

/** A detected event exposed to the user for manual dataset inclusion or exclusion. */
public final class DatasetEvent {
    private final AnalyzedLog analyzedLog;
    private final IdleEvent event;
    private final TuneSnapshot tuneSnapshot;
    private final EventIntegrity integrity;
    private final boolean selectable;
    private final boolean recommendationGateEligible;
    private final String eligibility;
    private final String operatingGroup;
    private final String responseGroup;
    private boolean included;

    public DatasetEvent(AnalyzedLog analyzedLog, IdleEvent event) {
        if (analyzedLog == null) throw new IllegalArgumentException("Analyzed log cannot be null");
        if (event == null) throw new IllegalArgumentException("Event cannot be null");
        this.analyzedLog = analyzedLog;
        this.event = event;
        this.tuneSnapshot = analyzedLog.getTuneSnapshot();
        this.integrity = EventIntegrity.assess(analyzedLog, event);
        this.selectable = isQualityEligible(event) && (event.isSteadyHold() || event.isFutureGainEligible());
        this.recommendationGateEligible = selectable
                && tuneSnapshot.isComplete()
                && !integrity.isRecommendationBlocked();
        this.included = recommendationGateEligible;
        this.eligibility = buildEligibility(event, selectable, recommendationGateEligible, tuneSnapshot, integrity);
        this.operatingGroup = buildOperatingGroup(event, tuneSnapshot.toSignature(), integrity.getMotionClass());
        this.responseGroup = buildResponseGroup(event, operatingGroup);
    }

    public AnalyzedLog getAnalyzedLog() { return analyzedLog; }
    public IdleEvent getEvent() { return event; }
    public TuneSnapshot getTuneSnapshot() { return tuneSnapshot; }
    public EventIntegrity getIntegrity() { return integrity; }
    public boolean isSelectable() { return selectable; }
    public boolean isRecommendationGateEligible() { return recommendationGateEligible; }
    public boolean isIncluded() { return included; }
    public void setIncluded(boolean included) { this.included = selectable && included; }
    public String getEligibility() { return eligibility; }
    public String getOperatingGroup() { return operatingGroup; }
    public String getResponseGroup() { return responseGroup; }

    private static boolean isQualityEligible(IdleEvent event) {
        return "Good for analysis".equals(event.getQuality()) || "Usable".equals(event.getQuality());
    }

    private static String buildEligibility(
            IdleEvent event,
            boolean selectable,
            boolean gateEligible,
            TuneSnapshot snapshot,
            EventIntegrity integrity) {
        if (selectable && gateEligible && event.isFutureGainEligible()) {
            return "Eligible transient candidate; manual inclusion is allowed and the event may satisfy the readiness gate.";
        }
        if (selectable && gateEligible && event.isSteadyHold()) {
            return "Eligible steady-state validation window; manual inclusion is allowed and the event may satisfy the readiness gate.";
        }
        if (selectable && !snapshot.isComplete()) {
            return "Manual inclusion is allowed for diagnostics, but this log has an incomplete P/I/D snapshot and cannot satisfy the readiness gate.";
        }
        if (selectable && integrity.isRecommendationBlocked()) {
            return "Manual inclusion is allowed for diagnostics, but the selected VSS/motion basis does not satisfy the readiness gate.";
        }
        if ("Context only".equals(event.getAnalysisUse()) || "Overview only".equals(event.getQuality())) {
            return "Context row only; cannot be included in a recommendation dataset.";
        }
        if ("Rejected".equals(event.getQuality()) || "Poor".equals(event.getQuality())) {
            return "Quality gate failed: " + event.getQuality() + ".";
        }
        return "This row is not eligible for future gain calculations.";
    }

    private static String buildOperatingGroup(IdleEvent event, String tuneSignature, String motionClass) {
        int target = bucket(event.getTargetRpm(), 50.0);
        String coolant = coolantBand(event.getCoolantMedian());
        String fan = normalizeFan(event.getFanState());
        return target + " RPM / " + coolant + " / Fan " + fan + " / "
                + normalizeMotion(motionClass) + " / " + safe(tuneSignature);
    }

    private static String buildResponseGroup(IdleEvent event, String operatingGroup) {
        return responseClass(event) + " / " + operatingGroup;
    }

    private static String responseClass(IdleEvent event) {
        String type = event.getType();
        if (type == null) return "Other response";
        if (type.startsWith("Return to idle")) return "Return to idle";
        if (type.startsWith("Fan 1 switched")) return "Fan load step";
        if (type.startsWith("Low-RPM")) return "Low-RPM disturbance";
        if (type.startsWith("High-RPM")) return "High-RPM disturbance";
        return type;
    }

    private static int bucket(double value, double size) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return 0;
        return (int) Math.round(value / size) * (int) size;
    }

    private static String coolantBand(double coolant) {
        if (Double.isNaN(coolant) || Double.isInfinite(coolant)) return "CLT unknown";
        int lower = (int) Math.floor(coolant / 20.0) * 20;
        int upper = lower + 20;
        return lower + "–" + upper + "°C";
    }

    private static String normalizeFan(String fan) {
        if (fan == null || fan.trim().isEmpty()) return "unknown";
        return fan.trim().toLowerCase();
    }

    private static String normalizeMotion(String motion) {
        if (motion == null || motion.trim().isEmpty()) return "motion unknown";
        String normalized = motion.trim();
        if ("Near-stationary".equals(normalized)) return "Stationary";
        return normalized;
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "P/I/D unknown" : value.trim();
    }
}
