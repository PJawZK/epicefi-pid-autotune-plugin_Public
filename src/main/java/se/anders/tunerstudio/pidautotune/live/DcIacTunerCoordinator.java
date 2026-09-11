package se.anders.tunerstudio.pidautotune.live;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacBiasSettingsAccess;
import se.anders.tunerstudio.pidautotune.controller.DcIacSettingsAccess;
import se.anders.tunerstudio.pidautotune.profile.DcIacPositionProfile;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasCurveProposalEngine;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasKnotProposal;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacPidRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacPidRecommendationEngine;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendationEngine;
import se.anders.tunerstudio.pidautotune.session.DcIacIterationComparison;
import se.anders.tunerstudio.pidautotune.session.DcIacIterationComparisonEngine;
import se.anders.tunerstudio.pidautotune.session.DcIacTuningSessionSummary;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** State/capture coordinator for the integrated read-only DC-IAC tuner. */
public final class DcIacTunerCoordinator {
    private static final long SAMPLE_MS = 20L;
    private final Object lock = new Object();
    private final ControllerAccess controllerAccess;
    private final ProfileLiveOutputSubscription subscription;
    private final DcIacSettingsAccess settingsAccess;
    private final DcIacBiasSettingsAccess biasAccess;
    private final DcIacBiasCharacterizationEngine biasEngine = new DcIacBiasCharacterizationEngine();
    private final DcIacBiasCharacterizationSettings biasSettings = new DcIacBiasCharacterizationSettings();
    private final DcIacStaticRecommendationEngine staticEngine = new DcIacStaticRecommendationEngine();
    private final DcIacBiasCurveProposalEngine knotEngine = new DcIacBiasCurveProposalEngine();
    private final DcIacEventExtractor extractor = new DcIacEventExtractor();
    private final DcIacMeasurementEngine measurementEngine = new DcIacMeasurementEngine();
    private final DcIacPidRecommendationEngine pidEngine = new DcIacPidRecommendationEngine();
    private final DcIacIterationComparisonEngine comparisonEngine = new DcIacIterationComparisonEngine();
    private final List<DcIacStaticEvidence> statics = new ArrayList<DcIacStaticEvidence>();
    private final List<DcIacDynamicEvidence> dynamics = new ArrayList<DcIacDynamicEvidence>();
    private final List<ProfileLiveSample> context = new ArrayList<ProfileLiveSample>();
    private final List<DcIacTuningSessionSummary> sessions = new ArrayList<DcIacTuningSessionSummary>();

    private ProfileBackgroundSampler sampler;
    private DcIacSettingsAccess.Snapshot settings;
    private DcIacBiasSettingsAccess.Snapshot bias;
    private DcIacEventSettings eventSettings;
    private DcIacMeasurementSettings measurementSettings;
    private ProfileLiveSample latest;
    private String configuration = "";
    private String status = "Stopped";
    private String readiness = "Stopped";
    private String error = "";
    private int eventCount;
    private int sessionSequence;

    public DcIacTunerCoordinator(ControllerAccess access) {
        if (access == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        controllerAccess = access;
        subscription = new ProfileLiveOutputSubscription(access);
        settingsAccess = new DcIacSettingsAccess(access);
        biasAccess = new DcIacBiasSettingsAccess(access);
    }

    public String[] configurations() {
        String[] names = controllerAccess.getEcuConfigurationNames();
        if (names == null) return new String[0];
        Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public void start(String name) throws Exception {
        if (isRunning()) stop("Restarted");
        DcIacSettingsAccess.Snapshot newSettings = settingsAccess.read(name);
        DcIacBiasSettingsAccess.Snapshot newBias = biasAccess.read(name);
        if (!(newSettings.getMaximumPosition() > newSettings.getMinimumPosition())) throw new IllegalStateException("Invalid DC-IAC travel limits");
        LiveSubscriptionReport report = subscription.start(name, DcIacPositionProfile.DEFINITION);
        if (!report.isUsable() || !subscription.isSubscribed("VBatt")) {
            subscription.stop();
            throw new IllegalStateException("Required DC-IAC live mapping or VBatt missing");
        }
        synchronized (lock) {
            configuration = name;
            settings = newSettings;
            bias = newBias;
            eventSettings = new DcIacEventSettings(newSettings.getMinimumPosition(), newSettings.getMaximumPosition());
            measurementSettings = new DcIacMeasurementSettings(newSettings.getPFactor(), newSettings.getIFactor(), newSettings.getDFactor());
            statics.clear(); dynamics.clear(); context.clear(); latest = null;
            eventCount = 0; error = ""; status = "Integrated capture running"; readiness = "Waiting for samples";
        }
        extractor.reset();
        biasEngine.reset();
        sampler = new ProfileBackgroundSampler(subscription, SAMPLE_MS);
        sampler.start(new ProfileBackgroundSampler.Listener() {
            @Override public void onSample(ProfileLiveSample sample) { process(sample); }
            @Override public void onError(Throwable thrown) { synchronized (lock) { error = message(thrown); } }
        });
    }

    public void stop(String reason) {
        if (sampler != null) sampler.stop();
        sampler = null;
        subscription.stop();
        extractor.stop();
        biasEngine.stop();
        synchronized (lock) { status = reason == null ? "Stopped" : reason; readiness = "Stopped"; }
    }

    public void stopAndArchive() {
        stop("Stopped + archived");
        List<DcIacStaticEvidence> s = staticEvidence();
        List<DcIacDynamicEvidence> d = dynamicEvidence();
        if (s.isEmpty() && d.isEmpty()) return;
        List<DcIacStaticRecommendation> sr = staticRecommendations();
        List<DcIacPidRecommendation> pr = pidRecommendations();
        DcIacTuningSessionSummary row = DcIacTuningSessionSummary.summarize(++sessionSequence, configuration, settings, s, d, sr, pr);
        synchronized (lock) { sessions.add(row); }
    }

    public void clearCurrent() { synchronized (lock) { statics.clear(); dynamics.clear(); context.clear(); } }
    public void clearHistory() { synchronized (lock) { sessions.clear(); sessionSequence = 0; } }
    public boolean isRunning() { return sampler != null && sampler.isRunning(); }

    public String status() { synchronized (lock) { return error.isEmpty() ? status : "Sampler error: " + error; } }
    public String readiness() { synchronized (lock) { return readiness; } }
    public String configurationSummary() {
        synchronized (lock) {
            if (settings == null || bias == null) return "-";
            String pid = settings.arePidGainsKnown() ? "P=" + fmt(settings.getPFactor()) + " I=" + fmt(settings.getIFactor()) + " D=" + fmt(settings.getDFactor()) : "P/I/D unavailable";
            String fan = settings.isFan1ExtraIdleKnown()
                    ? " / fan1 adder=" + fmt(settings.getFan1ExtraIdle()) + "%" + (settings.isFan1ExtraIdleRpmTargetKnown() ? " -> " + fmt(settings.getFan1ExtraIdleRpmTarget()) + " rpm" : "")
                    : "";
            return fmt(settings.getMinimumPosition()) + "-" + fmt(settings.getMaximumPosition()) + "% / " + pid + " / bias knots=" + bias.size() + " / motor +/-90%" + fan;
        }
    }
    public String liveSummary() {
        synchronized (lock) {
            if (latest == null) return "-";
            return "target " + fmt(latest.get("dcIdleTarget")) + " / actual " + fmt(latest.get("idlePositionSensor"))
                    + " / duty " + fmt(latest.get("dcIdleDutyCycle")) + " / I " + fmt(latest.get("dcIdlePositionStatus_iTerm"))
                    + " / RPM " + fmt(latest.get("RPMValue")) + " / V " + fmt(latest.get("VBatt"));
        }
    }

    public String diagnosticsSummary() {
        List<ProfileLiveSample> copy;
        DcIacSettingsAccess.Snapshot localSettings;
        synchronized (lock) {
            copy = new ArrayList<ProfileLiveSample>(context);
            localSettings = settings;
        }
        double i = localSettings != null && localSettings.arePidGainsKnown() ? localSettings.getIFactor() : Double.NaN;
        DcIacLiveDiagnostics.Result r = DcIacLiveDiagnostics.analyze(copy, i);
        StringBuilder b = new StringBuilder();
        if (r.isHandoffAvailable()) b.append("target handoff med |d|=").append(fmt(r.getMedianHandoffAbsError())).append("% ").append(r.isHandoffOk() ? "OK" : "MISMATCH");
        else b.append("target handoff unavailable");
        b.append(" / I-law exp ").append(signed(r.getExpectedITermSlope())).append(" meas ").append(signed(r.getMeasuredITermSlope())).append(" duty/s");
        b.append(r.shouldKeepHolding() ? " -> KEEP HOLDING" : " -> locally quiet");
        if (r.isFan1Available()) b.append(" / fan1 ").append(r.isFan1On() ? "ON" : "OFF");
        return b.toString();
    }

    public List<DcIacStaticEvidence> staticEvidence() { synchronized (lock) { return new ArrayList<DcIacStaticEvidence>(statics); } }
    public List<DcIacDynamicEvidence> dynamicEvidence() { synchronized (lock) { return new ArrayList<DcIacDynamicEvidence>(dynamics); } }
    public List<DcIacTuningSessionSummary> sessions() { synchronized (lock) { return new ArrayList<DcIacTuningSessionSummary>(sessions); } }
    public List<DcIacStaticRecommendation> staticRecommendations() { return staticEngine.evaluate(staticEvidence()); }
    public List<DcIacBiasKnotProposal> knotProposals() { synchronized (lock) { return knotEngine.evaluate(bias, staticEngine.evaluate(new ArrayList<DcIacStaticEvidence>(statics))); } }
    public List<DcIacPidRecommendation> pidRecommendations() { synchronized (lock) { return pidEngine.evaluate(new ArrayList<DcIacDynamicEvidence>(dynamics), staticEngine.evaluate(new ArrayList<DcIacStaticEvidence>(statics)), settings); } }
    public DcIacIterationComparison lastComparison() {
        List<DcIacTuningSessionSummary> copy = sessions();
        if (copy.size() < 2) return null;
        return comparisonEngine.compare(copy.get(copy.size() - 2), copy.get(copy.size() - 1));
    }

    public boolean configurationStillMatches() {
        String name;
        DcIacSettingsAccess.Snapshot baselineSettings;
        DcIacBiasSettingsAccess.Snapshot baselineBias;
        synchronized (lock) { name = configuration; baselineSettings = settings; baselineBias = bias; }
        if (name.isEmpty() || baselineSettings == null || baselineBias == null) return true;
        try { return same(baselineSettings, settingsAccess.read(name)) && same(baselineBias, biasAccess.read(name)); }
        catch (Exception ex) { synchronized (lock) { error = "Configuration re-read failed: " + message(ex); } return false; }
    }

    private void process(ProfileLiveSample sample) {
        DcIacSettingsAccess.Snapshot localSettings;
        DcIacBiasSettingsAccess.Snapshot localBias;
        DcIacEventSettings localEvent;
        DcIacMeasurementSettings localMeasurement;
        synchronized (lock) {
            localSettings = settings; localBias = bias; localEvent = eventSettings; localMeasurement = measurementSettings;
            latest = sample; appendContextLocked(sample);
        }
        if (localSettings == null || localBias == null || localEvent == null || localMeasurement == null) return;
        DcIacBiasEvidence staticRow = biasEngine.process(sample, localEvent, biasSettings, localBias, localSettings.isMotorControlPaused());
        if (staticRow != null) {
            synchronized (lock) { statics.add(new DcIacStaticEvidence(staticRow, contextForLocked(staticRow.getStartSeconds(), staticRow.getEndSeconds()))); }
        }
        extractor.process(sample, localEvent, localSettings.isMotorControlPaused());
        List<DcIacEvent> events = extractor.getEvents();
        for (int index = eventCount; index < events.size(); index++) {
            DcIacEvent event = events.get(index);
            if (event.isAccepted() && event.getType() != DcIacEvent.Type.STABLE_HOLD) {
                DcIacMeasurement measurement = measurementEngine.measure(event, localMeasurement);
                synchronized (lock) {
                    dynamics.add(new DcIacDynamicEvidence(measurement, event,
                            localSettings.getMinimumPosition(), localSettings.getMaximumPosition()));
                }
            }
        }
        eventCount = events.size();
        synchronized (lock) { readiness = "Static: " + biasEngine.getReadiness() + " | Dynamic: " + extractor.getReadiness() + " / " + extractor.getLastTransition(); }
    }

    private void appendContextLocked(ProfileLiveSample sample) {
        context.add(sample);
        double cutoff = sample.getTimeSeconds() - 7.0;
        while (context.size() > 1 && context.get(0).getTimeSeconds() < cutoff) context.remove(0);
    }
    private List<ProfileLiveSample> contextForLocked(double start, double end) {
        List<ProfileLiveSample> result = new ArrayList<ProfileLiveSample>();
        for (ProfileLiveSample sample : context) if (sample.getTimeSeconds() >= start - 0.1 && sample.getTimeSeconds() <= end + 0.1) result.add(sample);
        return result;
    }

    private static boolean same(DcIacSettingsAccess.Snapshot a, DcIacSettingsAccess.Snapshot b) {
        if (a == null || b == null || !near(a.getMinimumPosition(), b.getMinimumPosition()) || !near(a.getMaximumPosition(), b.getMaximumPosition()) || a.arePidGainsKnown() != b.arePidGainsKnown()) return false;
        if (a.arePidGainsKnown() && (!near(a.getPFactor(), b.getPFactor()) || !near(a.getIFactor(), b.getIFactor()) || !near(a.getDFactor(), b.getDFactor()))) return false;
        if (a.isMotorControlPauseKnown() && b.isMotorControlPauseKnown() && a.isMotorControlPaused() != b.isMotorControlPaused()) return false;
        if (a.isFan1ExtraIdleKnown() && b.isFan1ExtraIdleKnown() && !near(a.getFan1ExtraIdle(), b.getFan1ExtraIdle())) return false;
        return !(a.isFan1ExtraIdleRpmTargetKnown() && b.isFan1ExtraIdleRpmTargetKnown())
                || near(a.getFan1ExtraIdleRpmTarget(), b.getFan1ExtraIdleRpmTarget());
    }
    private static boolean same(DcIacBiasSettingsAccess.Snapshot a, DcIacBiasSettingsAccess.Snapshot b) {
        if (a == null || b == null || a.size() != b.size() || a.areIntegralLimitsKnown() != b.areIntegralLimitsKnown()) return false;
        for (int i = 0; i < a.size(); i++) if (!near(a.getBin(i), b.getBin(i)) || !near(a.getValue(i), b.getValue(i))) return false;
        return !a.areIntegralLimitsKnown() || (near(a.getITermMinimum(), b.getITermMinimum()) && near(a.getITermMaximum(), b.getITermMaximum()));
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) <= 0.00001; }
    private static String fmt(double value) { return Double.isNaN(value) || Double.isInfinite(value) ? "-" : String.format(java.util.Locale.US, "%.3f", value); }
    private static String signed(double value) { return Double.isNaN(value) || Double.isInfinite(value) ? "-" : String.format(java.util.Locale.US, "%+.3f", value); }
    private static String message(Throwable error) { return error == null ? "Unknown" : error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(); }
}
