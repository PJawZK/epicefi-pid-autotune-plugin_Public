package se.anders.tunerstudio.pidautotune.live;

import java.util.List;

/**
 * M4A equilibrium evidence plus operating context for the integrated tuner.
 *
 * The underlying DcIacBiasEvidence remains the static measurement authority. This wrapper keeps
 * engine-off, running-idle and other-running evidence separate and records useful context without
 * changing M4A's validated measurement contract.
 */
public final class DcIacStaticEvidence {
    private final DcIacBiasEvidence evidence;
    private final DcIacOperatingContext operatingContext;
    private final double meanRpm;
    private final double meanCoolant;
    private final double idlingFraction;

    public DcIacStaticEvidence(DcIacBiasEvidence evidence, List<ProfileLiveSample> contextSamples) {
        if (evidence == null) throw new IllegalArgumentException("evidence cannot be null");
        this.evidence = evidence;
        this.operatingContext = DcIacOperatingContext.classify(contextSamples);
        this.meanRpm = DcIacOperatingContext.meanRpm(contextSamples);
        this.meanCoolant = DcIacOperatingContext.meanCoolant(contextSamples);
        this.idlingFraction = DcIacOperatingContext.idlingFraction(contextSamples);
    }

    public DcIacStaticEvidence(
            DcIacBiasEvidence evidence,
            DcIacOperatingContext operatingContext,
            double meanRpm,
            double meanCoolant,
            double idlingFraction) {
        if (evidence == null) throw new IllegalArgumentException("evidence cannot be null");
        this.evidence = evidence;
        this.operatingContext = operatingContext == null ? DcIacOperatingContext.UNKNOWN : operatingContext;
        this.meanRpm = meanRpm;
        this.meanCoolant = meanCoolant;
        this.idlingFraction = idlingFraction;
    }

    public DcIacBiasEvidence getEvidence() { return evidence; }
    public DcIacOperatingContext getOperatingContext() { return operatingContext; }
    public double getMeanRpm() { return meanRpm; }
    public double getMeanCoolant() { return meanCoolant; }
    public double getIdlingFraction() { return idlingFraction; }

    public double getTarget() { return evidence.getTarget(); }
    public double getCorrection() { return evidence.getCorrection(); }
    public double getConfiguredBias() { return evidence.getConfiguredBias(); }
    public double getRequiredBias() { return evidence.getRequiredBias(); }
    public double getEndSeconds() { return evidence.getEndSeconds(); }
    public String getCurveSegment() { return evidence.getCurveSegment(); }
}
