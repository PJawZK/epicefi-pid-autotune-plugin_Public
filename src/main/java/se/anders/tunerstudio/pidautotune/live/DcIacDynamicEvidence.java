package se.anders.tunerstudio.pidautotune.live;

/**
 * M3 measurement plus the operating context required by the integrated DC-IAC tuner.
 *
 * M3 itself remains the measurement authority. This wrapper prevents the recommendation
 * layer from mixing engine-off/running data, hiding source-resolution limitations,
 * treating fan-load events as pure setpoint experiments, or accepting process-value
 * excursions beyond configured travel as quantitative identification evidence.
 */
public final class DcIacDynamicEvidence {
    public enum Tier { QUANTITATIVE, SOURCE_LIMITED_SUPPORT, CONTEXT_ONLY }
    private final DcIacMeasurement measurement;
    private final DcIacOperatingContext operatingContext;
    private final double meanRpm;
    private final double meanCoolant;
    private final double idlingFraction;
    private final DcIacStepContext stepContext;
    private final Tier tier;

    public DcIacDynamicEvidence(DcIacMeasurement measurement, DcIacEvent event) {
        this(measurement, event, Double.NaN, Double.NaN);
    }

    public DcIacDynamicEvidence(DcIacMeasurement measurement, DcIacEvent event,
            double minimumPosition, double maximumPosition) {
        if (measurement == null) throw new IllegalArgumentException("measurement cannot be null");
        if (event == null) throw new IllegalArgumentException("event cannot be null");
        this.measurement = measurement;
        this.operatingContext = DcIacOperatingContext.classify(event.getSamples());
        this.meanRpm = DcIacOperatingContext.meanRpm(event.getSamples());
        this.meanCoolant = DcIacOperatingContext.meanCoolant(event.getSamples());
        this.idlingFraction = DcIacOperatingContext.idlingFraction(event.getSamples());
        this.stepContext = DcIacStepContext.analyze(event.getSamples(), minimumPosition, maximumPosition);
        this.tier = classifyTier(measurement, stepContext);
    }

    public DcIacDynamicEvidence(DcIacMeasurement measurement, DcIacOperatingContext operatingContext,
            double meanRpm, double meanCoolant, double idlingFraction) {
        if (measurement == null) throw new IllegalArgumentException("measurement cannot be null");
        this.measurement = measurement;
        this.operatingContext = operatingContext == null ? DcIacOperatingContext.UNKNOWN : operatingContext;
        this.meanRpm = meanRpm;
        this.meanCoolant = meanCoolant;
        this.idlingFraction = idlingFraction;
        this.stepContext = DcIacStepContext.analyze(null, Double.NaN, Double.NaN);
        this.tier = classifyTier(measurement, stepContext);
    }

    public DcIacMeasurement getMeasurement() { return measurement; }
    public DcIacOperatingContext getOperatingContext() { return operatingContext; }
    public double getMeanRpm() { return meanRpm; }
    public double getMeanCoolant() { return meanCoolant; }
    public double getIdlingFraction() { return idlingFraction; }
    public DcIacStepContext getStepContext() { return stepContext; }
    public DcIacStepContext.Source getStepSource() { return stepContext.getSource(); }
    public boolean isFanIdleAdderStep() { return stepContext.isFanIdleAdderStep(); }
    public boolean isProcessValueOutsideTravel() { return stepContext.isProcessValueOutsideTravel(); }
    public Tier getTier() { return tier; }
    public boolean isOpening() { return measurement.getEventType() == DcIacEvent.Type.OPENING_STEP; }
    public boolean isClosing() { return measurement.getEventType() == DcIacEvent.Type.CLOSING_STEP; }

    public String getCombinedEvidenceFlags() {
        String measurementFlags = measurement.getEvidenceFlags();
        String contextFlags = stepContext.evidenceFlags();
        if (measurementFlags == null || measurementFlags.trim().isEmpty()) return contextFlags;
        if (contextFlags == null || contextFlags.trim().isEmpty()) return measurementFlags;
        return measurementFlags + ";" + contextFlags;
    }

    public boolean isSafeLocalStep() {
        if (!measurement.isValid() || (!isOpening() && !isClosing())) return false;
        if (stepContext.isFanIdleAdderStep() || stepContext.isProcessValueOutsideTravel()) return false;
        if (!finite(measurement.getStepMagnitude()) || measurement.getStepMagnitude() <= 0.0 || measurement.getStepMagnitude() > 4.50) return false;
        if (measurement.isPredictedSaturationRisk()) return false;
        String flags = measurement.getEvidenceFlags();
        return flags == null || (!flags.contains("OBSERVED_PHYSICAL_DUTY_LIMIT")
                && !flags.contains("PREDICTED_COMMAND_EDGE_SATURATION")
                && !flags.contains("STEP_TOO_LARGE_FOR_LOCAL_ID"));
    }

    private static Tier classifyTier(DcIacMeasurement measurement, DcIacStepContext context) {
        if (context != null && (context.isFanIdleAdderStep() || context.isProcessValueOutsideTravel())) return Tier.CONTEXT_ONLY;
        if (measurement.isDynamicQuantitative()) return Tier.QUANTITATIVE;
        if (!measurement.isValid()) return Tier.CONTEXT_ONLY;
        String code = measurement.getResultCode();
        if (("STEP_MEASURED_TIMING_LIMITED".equals(code) || "STEP_MEASURED_TIMING_UNAVAILABLE".equals(code))
                && finite(measurement.getStepMagnitude()) && measurement.getStepMagnitude() <= 4.50
                && !measurement.isPredictedSaturationRisk()) return Tier.SOURCE_LIMITED_SUPPORT;
        return Tier.CONTEXT_ONLY;
    }
    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
}
