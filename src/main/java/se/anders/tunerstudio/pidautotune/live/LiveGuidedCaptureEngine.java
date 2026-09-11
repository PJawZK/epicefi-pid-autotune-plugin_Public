package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Read-only guided return-to-idle state machine.
 * It observes live values, gives immediate instructions, and never writes controller parameters.
 */
public final class LiveGuidedCaptureEngine {
    private LiveCaptureState state = LiveCaptureState.STOPPED;
    private String instruction = "Start the live observer when the engine and TunerStudio are connected.";
    private String lastResult = "No live attempts yet.";
    private LiveReadiness readiness = new LiveReadiness();
    private final List<LiveAttempt> attempts = new ArrayList<LiveAttempt>();
    private final List<LiveSample> attemptSamples = new ArrayList<LiveSample>();
    private final List<LiveSample> baselineSamples = new ArrayList<LiveSample>();

    private double baselineStableSince = Double.NaN;
    private double fanLastChanged = Double.NaN;
    private double targetLastChanged = Double.NaN;
    private double lastFan = Double.NaN;
    private double lastTarget = Double.NaN;
    private double attemptPeakRpm = Double.NaN;
    private double attemptFan = Double.NaN;
    private double attemptTarget = Double.NaN;
    private double triggerSeconds = Double.NaN;
    private double settlingBandSince = Double.NaN;
    private double settledSeconds = Double.NaN;
    private double dfcoInactiveSince = Double.NaN;
    private boolean approachDfcoWasActive;
    private boolean lastApproachDfco;
    private int attemptNumber;
    private String eventMarker = "";
    private String resultCode = "";

    public void start() {
        resetCurrentAttempt();
        baselineSamples.clear();
        state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
        instruction = "Waiting for stable closed-loop idle.";
        lastResult = attempts.isEmpty() ? "No live attempts yet." : lastResult;
        mark("OBSERVER_STARTED", "");
    }

    public void stop() {
        state = LiveCaptureState.STOPPED;
        resetCurrentAttempt();
        baselineSamples.clear();
        instruction = "Live observer stopped.";
        mark("OBSERVER_STOPPED", "");
    }

    public void resetSession() {
        attempts.clear();
        attemptNumber = 0;
        lastResult = "No live attempts yet.";
        if (state != LiveCaptureState.STOPPED) {
            state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
            instruction = "Session reset. Waiting for stable closed-loop idle.";
        }
        resetCurrentAttempt();
        baselineSamples.clear();
        mark("SESSION_RESET", "");
    }

    public void abortCurrentAttempt(String reason) {
        if (isAttemptActive()) {
            reject("USER_ABORTED", reason == null || reason.trim().isEmpty() ? "Attempt aborted by user." : reason, null);
        }
    }

    public void process(LiveSample sample, LiveCaptureSettings settings, long nowNanos) {
        if (sample == null || settings == null || state == LiveCaptureState.STOPPED) return;
        eventMarker = "";
        resultCode = "";
        updateConditionHistory(sample);
        updateBaselineHistory(sample, settings);
        readiness = evaluateReadiness(sample, settings, nowNanos);

        String freshnessCode = attemptFreshnessInvalidCode(sample, settings, nowNanos);
        if (isAttemptActive() && freshnessCode != null) {
            reject(freshnessCode, reasonForCode(freshnessCode, settings), sample);
            return;
        }
        if (state == LiveCaptureState.READY_TO_REV && freshnessCode != null) {
            state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
            baselineStableSince = Double.NaN;
            instruction = "Live data became missing or stale before the rev. Waiting for fresh telemetry.";
            mark("READY_CANCELLED", freshnessCode);
            return;
        }

        switch (state) {
            case WAITING_FOR_STABLE_IDLE:
                processWaitingForStable(sample, settings);
                break;
            case READY_TO_REV:
                processReady(sample, settings);
                break;
            case REV_IN_PROGRESS:
                processRev(sample, settings);
                break;
            case WAITING_FOR_IDLE_CONTROL:
                processWaitingForIdle(sample, settings);
                break;
            case MEASURING_RECOVERY:
                processRecovery(sample, settings);
                break;
            case CONFIRMING_STABLE_IDLE:
                processStableConfirmation(sample, settings);
                break;
            default:
                break;
        }
    }

    private void processWaitingForStable(LiveSample sample, LiveCaptureSettings settings) {
        if (readiness.isReady()) {
            if (!isFinite(baselineStableSince)) baselineStableSince = sample.getTimeSeconds();
            double elapsed = sample.getTimeSeconds() - baselineStableSince;
            if (elapsed >= settings.getBaselineRequiredSeconds()) {
                state = LiveCaptureState.READY_TO_REV;
                mark("READY_TO_REV", "READY");
                instruction = "Ready. Raise RPM smoothly to " + whole(settings.getPreferredRevMinimum()) + "–"
                        + whole(settings.getPreferredRevMaximum()) + " RPM, then release the throttle normally. "
                        + "Brief DFCO during overrun is expected and will not be measured.";
            } else {
                instruction = "Stable idle detected. Hold untouched for "
                        + one(Math.max(0.0, settings.getBaselineRequiredSeconds() - elapsed)) + " more seconds.";
            }
        } else {
            baselineStableSince = Double.NaN;
            instruction = "Waiting for stable idle: " + firstNotReady(readiness) + ".";
        }
    }

    private void processReady(LiveSample sample, LiveCaptureSettings settings) {
        double rpm = sample.get(LiveChannel.RPM);
        double target = sample.get(LiveChannel.IDLE_TARGET);
        double tps = sample.get(LiveChannel.TPS);
        if (tps > settings.getTpsThreshold() || rpm > target + 300.0) {
            attemptNumber++;
            attemptPeakRpm = rpm;
            attemptFan = sample.get(LiveChannel.FAN1);
            attemptTarget = target;
            attemptSamples.clear();
            approachDfcoWasActive = false;
            lastApproachDfco = false;
            dfcoInactiveSince = Double.NaN;
            state = LiveCaptureState.REV_IN_PROGRESS;
            mark("ATTEMPT_STARTED", "REV_DETECTED");
            instruction = "Rev detected. Reach the preferred range, then release the throttle normally. "
                    + "Do not feather the throttle to avoid DFCO.";
            return;
        }
        if (!readiness.isReady()) {
            state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
            baselineStableSince = Double.NaN;
            instruction = "Conditions changed before the rev. Waiting again: " + firstNotReady(readiness) + ".";
        }
    }

    private void processRev(LiveSample sample, LiveCaptureSettings settings) {
        updatePeak(sample);
        String invalidCode = attemptInvalidCode(sample, settings, false);
        if (invalidCode != null) {
            reject(invalidCode, reasonForCode(invalidCode, settings), sample);
            return;
        }
        double tps = sample.get(LiveChannel.TPS);
        double rpm = sample.get(LiveChannel.RPM);
        boolean rpmHasStartedFalling = isFinite(attemptPeakRpm) && isFinite(rpm) && attemptPeakRpm - rpm >= 20.0;
        if (tps <= settings.getTpsThreshold() && rpmHasStartedFalling) {
            state = LiveCaptureState.WAITING_FOR_IDLE_CONTROL;
            dfcoInactiveSince = Double.NaN;
            lastApproachDfco = isDfco(sample);
            approachDfcoWasActive = lastApproachDfco;
            mark("THROTTLE_RELEASED", "APPROACH_STARTED");
            instruction = lastApproachDfco
                    ? "Throttle released. DFCO is active during overrun — expected. Waiting for fuel and idle control to return."
                    : "Throttle released. Waiting for closed-loop idle control to re-engage.";
        } else {
            instruction = attemptPeakRpm < settings.getPreferredRevMinimum()
                    ? "Continue the smooth rev to at least " + whole(settings.getPreferredRevMinimum()) + " RPM, then release normally."
                    : "Preferred rev reached. Release the throttle normally; brief DFCO is expected.";
        }
    }

    private void processWaitingForIdle(LiveSample sample, LiveCaptureSettings settings) {
        updatePeak(sample);
        String invalidCode = attemptInvalidCode(sample, settings, false);
        if (invalidCode != null) {
            reject(invalidCode, reasonForCode(invalidCode, settings), sample);
            return;
        }
        if (sample.get(LiveChannel.TPS) > settings.getTpsThreshold()) {
            reject("THROTTLE_REOPENED", "Throttle reopened before idle control engaged.", sample);
            return;
        }

        boolean dfco = isDfco(sample);
        if (dfco) {
            approachDfcoWasActive = true;
            dfcoInactiveSince = Double.NaN;
            if (!lastApproachDfco) mark("DFCO_STARTED", "DFCO_APPROACH");
            lastApproachDfco = true;
            instruction = "DFCO active during overrun — expected. PID measurement has not started.";
            return;
        }
        if (lastApproachDfco) mark("DFCO_ENDED", "DFCO_APPROACH_COMPLETE");
        lastApproachDfco = false;
        if (!isFinite(dfcoInactiveSince)) dfcoInactiveSince = sample.getTimeSeconds();

        double inactiveTime = sample.getTimeSeconds() - dfcoInactiveSince;
        if (inactiveTime < settings.getDfcoInactiveRequiredSeconds()) {
            instruction = "DFCO ended. Waiting " + one(settings.getDfcoInactiveRequiredSeconds() - inactiveTime)
                    + " s before measuring idle control.";
            return;
        }

        double rpm = sample.get(LiveChannel.RPM);
        double target = sample.get(LiveChannel.IDLE_TARGET);
        if (isTrue(sample, LiveChannel.CLOSED_LOOP_ACTIVE)
                && rpm <= target + settings.getRpmUpperLimit()) {
            triggerSeconds = sample.getTimeSeconds();
            attemptTarget = target;
            attemptSamples.clear();
            attemptSamples.add(sample);
            state = LiveCaptureState.MEASURING_RECOVERY;
            mark("PID_MEASUREMENT_STARTED", approachDfcoWasActive ? "MEASURE_AFTER_DFCO" : "MEASURE_NO_DFCO");
            instruction = "DFCO is inactive and idle control is engaged. Measuring PID recovery — do not touch any controls.";
        } else if (!isTrue(sample, LiveChannel.CLOSED_LOOP_ACTIVE)) {
            instruction = "DFCO ended — waiting for closed-loop idle control to engage.";
        } else {
            instruction = "Idle control is active; waiting for RPM to enter the ECU idle-control range.";
        }
    }

    private void processRecovery(LiveSample sample, LiveCaptureSettings settings) {
        attemptSamples.add(sample);
        String invalidCode = attemptInvalidCode(sample, settings, true);
        if (invalidCode != null) {
            reject(invalidCode, reasonForCode(invalidCode, settings), sample);
            return;
        }
        double elapsed = sample.getTimeSeconds() - triggerSeconds;
        if (elapsed > settings.getMaximumRecoverySeconds()) {
            reject("RECOVERY_TIMEOUT", "RPM did not enter the selected settling band within "
                    + one(settings.getMaximumRecoverySeconds()) + " seconds.", sample);
            return;
        }
        double error = rollingRecoveryError(settings);
        if (isFinite(error) && error <= settings.getSettlingBandRpm()) {
            if (!isFinite(settlingBandSince)) {
                settlingBandSince = sample.getTimeSeconds();
                mark("SETTLING_BAND_ENTERED", "SETTLING_CANDIDATE");
            }
            double inside = sample.getTimeSeconds() - settlingBandSince;
            if (inside >= settings.getSettlingRequiredSeconds()) {
                settledSeconds = settlingBandSince;
                state = LiveCaptureState.CONFIRMING_STABLE_IDLE;
                mark("STABLE_CONFIRMATION_STARTED", "SETTLED");
                instruction = "Recovery entered the selected ±" + whole(settings.getSettlingBandRpm())
                        + " RPM band. Keep the engine untouched while stable idle is confirmed.";
            } else {
                instruction = "RPM average is inside the selected ±" + whole(settings.getSettlingBandRpm())
                        + " RPM band. Hold untouched for "
                        + one(settings.getSettlingRequiredSeconds() - inside) + " more seconds.";
            }
        } else {
            settlingBandSince = Double.NaN;
            instruction = "Measuring recovery. Current rolling error " + one(error) + " RPM; selected settling band ±"
                    + whole(settings.getSettlingBandRpm()) + " RPM.";
        }
    }

    private void processStableConfirmation(LiveSample sample, LiveCaptureSettings settings) {
        attemptSamples.add(sample);
        String invalidCode = attemptInvalidCode(sample, settings, true);
        if (invalidCode != null) {
            reject(invalidCode, reasonForCode(invalidCode, settings), sample);
            return;
        }
        double error = rollingRecoveryError(settings);
        double exitBand = settings.getSettlingBandRpm() + settings.getExitHysteresisRpm();
        if (!isFinite(error) || error > exitBand) {
            settlingBandSince = Double.NaN;
            settledSeconds = Double.NaN;
            state = LiveCaptureState.MEASURING_RECOVERY;
            mark("SETTLING_BAND_EXITED", "RPM_HYSTERESIS_EXIT");
            instruction = "RPM rolling error exceeded the ±" + whole(exitBand)
                    + " RPM exit band. Continuing recovery measurement.";
            return;
        }
        double stable = sample.getTimeSeconds() - settledSeconds;
        if (stable >= settings.getStableObservationSeconds()) {
            accept(sample, settings, stable);
        } else {
            String bandDetail = error > settings.getSettlingBandRpm()
                    ? " (inside the hysteresis guard)" : "";
            instruction = "Settled" + bandDetail + ". Keep untouched idle for "
                    + one(Math.max(0.0, settings.getStableObservationSeconds() - stable))
                    + " more seconds.";
        }
    }

    private LiveReadiness evaluateReadiness(LiveSample sample, LiveCaptureSettings settings, long nowNanos) {
        LiveReadiness result = new LiveReadiness();
        boolean channels = true;
        for (LiveChannel channel : LiveChannel.values()) {
            if (channel.isRequired() && (!sample.has(channel)
                    || !sample.isFresh(channel, nowNanos, settings.getChannelFreshnessSeconds()))) {
                channels = false;
            }
        }
        result.add("Live channels", channels, channels ? "fresh" : "required channel missing or stale");

        double rpm = sample.get(LiveChannel.RPM);
        result.add("Engine running", isFinite(rpm) && rpm >= settings.getEngineRunningMinimumRpm(),
                isFinite(rpm) ? whole(rpm) + " RPM" : "RPM unavailable");

        double clt = sample.get(LiveChannel.CLT);
        boolean cltOk = isFinite(clt) && clt >= settings.getMinimumCoolant() && clt <= settings.getMaximumCoolant();
        result.add("Coolant range", cltOk,
                isFinite(clt) ? one(clt) + "°C; required " + one(settings.getMinimumCoolant()) + "–" + one(settings.getMaximumCoolant()) + "°C" : "CLT unavailable");

        double tps = sample.get(LiveChannel.TPS);
        boolean tpsOk = isFinite(tps) && tps <= settings.getTpsThreshold();
        result.add("Throttle closed", tpsOk,
                isFinite(tps) ? one(tps) + "%; threshold " + one(settings.getTpsThreshold()) + "%" : "TPS unavailable");

        boolean closedLoop = isTrue(sample, LiveChannel.CLOSED_LOOP_ACTIVE);
        result.add("Closed-loop idle", closedLoop, closedLoop ? "active" : "inactive");

        boolean dfco = isDfco(sample);
        result.add("DFCO", !dfco,
                sample.has(LiveChannel.DFCO)
                        ? (dfco ? "active; baseline waits, but DFCO is allowed after throttle release before PID measurement" : "inactive")
                        : "channel unavailable; approach-phase DFCO cannot be confirmed");

        double fan = sample.get(LiveChannel.FAN1);
        boolean fanStable = isFinite(fan) && isFinite(fanLastChanged)
                && sample.getTimeSeconds() - fanLastChanged >= 3.0;
        result.add("Fan state", fanStable,
                isFinite(fan) ? (fan >= 0.5 ? "on" : "off") + (fanStable ? " and stable" : "; waiting for 3 s stability") : "fan channel unavailable");

        double target = sample.get(LiveChannel.IDLE_TARGET);
        boolean targetStable = isFinite(target) && isFinite(targetLastChanged)
                && sample.getTimeSeconds() - targetLastChanged >= 2.0;
        result.add("Idle target", targetStable,
                isFinite(target) ? whole(target) + " RPM" + (targetStable ? " and stable" : "; waiting for 2 s stability") : "target unavailable");

        boolean motionOk;
        String motionDetail;
        if (settings.getMotionMode() == LiveMotionMode.MANUAL_STATIONARY) {
            motionOk = settings.isManualStationaryConfirmed();
            motionDetail = motionOk ? "manually confirmed stationary" : "tick the stationary confirmation";
        } else {
            double vss = sample.get(LiveChannel.VEHICLE_SPEED);
            boolean vssFresh = sample.has(LiveChannel.VEHICLE_SPEED)
                    && sample.isFresh(LiveChannel.VEHICLE_SPEED, nowNanos, settings.getChannelFreshnessSeconds());
            motionOk = vssFresh && Math.abs(vss) <= settings.getStationaryVssLimit();
            motionDetail = vssFresh ? one(vss) + " km/h; stationary limit " + one(settings.getStationaryVssLimit()) : "VSS missing or stale";
        }
        result.add("Vehicle stationary", motionOk, motionDetail);

        BaselineStats stats = baselineStats();
        boolean rpmStable = isFinite(stats.meanSignedError)
                && Math.abs(stats.meanSignedError) <= settings.getBaselineErrorBandRpm()
                && isFinite(stats.rpmDeviation)
                && stats.rpmDeviation <= settings.getBaselineErrorBandRpm();
        result.add("Idle RPM stable", rpmStable,
                isFinite(stats.meanSignedError)
                        ? "rolling mean " + one(stats.meanSignedError) + " RPM; deviation " + one(stats.rpmDeviation)
                        + " RPM; selected band ±" + whole(settings.getBaselineErrorBandRpm())
                        : "collecting the 1 s rolling RPM window");
        return result;
    }

    private void updateConditionHistory(LiveSample sample) {
        double fan = sample.get(LiveChannel.FAN1);
        if (isFinite(fan)) {
            if (!isFinite(lastFan) || booleanChanged(lastFan, fan)) fanLastChanged = sample.getTimeSeconds();
            lastFan = fan;
        }
        double target = sample.get(LiveChannel.IDLE_TARGET);
        if (isFinite(target)) {
            if (!isFinite(lastTarget) || Math.abs(target - lastTarget) > 25.0) targetLastChanged = sample.getTimeSeconds();
            lastTarget = target;
        }
    }

    private void updateBaselineHistory(LiveSample sample, LiveCaptureSettings settings) {
        baselineSamples.add(sample);
        double oldest = sample.getTimeSeconds() - settings.getBaselineRollingWindowSeconds();
        while (!baselineSamples.isEmpty() && baselineSamples.get(0).getTimeSeconds() < oldest) {
            baselineSamples.remove(0);
        }
    }

    private String attemptFreshnessInvalidCode(LiveSample sample, LiveCaptureSettings settings, long nowNanos) {
        for (LiveChannel channel : LiveChannel.values()) {
            if (channel.isRequired() && (!sample.has(channel)
                    || !sample.isFresh(channel, nowNanos, settings.getChannelFreshnessSeconds()))) {
                return "REQUIRED_TELEMETRY_MISSING_OR_STALE";
            }
        }
        if (settings.getMotionMode() == LiveMotionMode.USE_LIVE_VSS
                && (!sample.has(LiveChannel.VEHICLE_SPEED)
                || !sample.isFresh(LiveChannel.VEHICLE_SPEED, nowNanos, settings.getChannelFreshnessSeconds()))) {
            return "VSS_MISSING_OR_STALE";
        }
        return null;
    }

    private String attemptInvalidCode(LiveSample sample, LiveCaptureSettings settings, boolean measuredRecovery) {
        double rpm = sample.get(LiveChannel.RPM);
        if (!isFinite(rpm) || rpm < settings.getEngineRunningMinimumRpm()) return "ENGINE_STOPPED";
        if (sample.get(LiveChannel.TPS) > settings.getTpsThreshold() && state != LiveCaptureState.REV_IN_PROGRESS) {
            return "THROTTLE_REOPENED";
        }
        if (measuredRecovery && !isTrue(sample, LiveChannel.CLOSED_LOOP_ACTIVE)) return "CLOSED_LOOP_DISENGAGED";
        if (measuredRecovery && isDfco(sample)) return "DFCO_DURING_RECOVERY";
        if (isFinite(attemptFan) && isFinite(sample.get(LiveChannel.FAN1))
                && booleanChanged(attemptFan, sample.get(LiveChannel.FAN1))) return "FAN_STATE_CHANGED";
        double clt = sample.get(LiveChannel.CLT);
        if (!isFinite(clt) || clt < settings.getMinimumCoolant() || clt > settings.getMaximumCoolant()) {
            return "CLT_OUT_OF_RANGE";
        }
        if (settings.getMotionMode() == LiveMotionMode.MANUAL_STATIONARY) {
            if (!settings.isManualStationaryConfirmed()) return "STATIONARY_CONFIRMATION_REMOVED";
        } else {
            double vss = sample.get(LiveChannel.VEHICLE_SPEED);
            if (!isFinite(vss) || Math.abs(vss) > settings.getStationaryVssLimit()) return "VSS_OUT_OF_RANGE";
        }
        return null;
    }

    private static String reasonForCode(String code, LiveCaptureSettings settings) {
        if ("REQUIRED_TELEMETRY_MISSING_OR_STALE".equals(code)) return "A required live channel became missing or stale during the active attempt.";
        if ("VSS_MISSING_OR_STALE".equals(code)) return "Verified live VSS became missing or stale during the active attempt.";
        if ("ENGINE_STOPPED".equals(code)) return "Engine stopped or RPM became invalid.";
        if ("THROTTLE_REOPENED".equals(code)) return "Throttle opened during the measured recovery.";
        if ("CLOSED_LOOP_DISENGAGED".equals(code)) return "Closed-loop idle disengaged after PID measurement had started.";
        if ("DFCO_DURING_RECOVERY".equals(code)) return "DFCO reactivated after PID measurement had started. Approach-phase DFCO is allowed; measured recovery cannot include fuel cut.";
        if ("FAN_STATE_CHANGED".equals(code)) return "Fan state changed during the attempt.";
        if ("CLT_OUT_OF_RANGE".equals(code)) return "Coolant temperature left the selected test range.";
        if ("STATIONARY_CONFIRMATION_REMOVED".equals(code)) return "Manual stationary confirmation was removed.";
        if ("VSS_OUT_OF_RANGE".equals(code)) return "Vehicle speed left the stationary limit.";
        if ("RECOVERY_TIMEOUT".equals(code)) return "RPM did not settle within " + one(settings.getMaximumRecoverySeconds()) + " seconds.";
        return code == null ? "Attempt became invalid." : code;
    }

    private void accept(LiveSample finalSample, LiveCaptureSettings settings, double stableObservation) {
        Metrics metrics = calculateMetrics(settings);
        boolean preferredPeak = attemptPeakRpm >= settings.getPreferredRevMinimum()
                && attemptPeakRpm <= settings.getPreferredRevMaximum();
        Quality quality = grade(metrics, preferredPeak, settings);
        LiveAttempt attempt = new LiveAttempt(
                attemptNumber, true, quality.label, quality.code, quality.reason,
                triggerSeconds, attemptPeakRpm, metrics.targetRpm, metrics.settlingSeconds,
                metrics.overshootRpm, metrics.undershootRpm, metrics.meanSignedError,
                metrics.meanAbsoluteError, metrics.rpmStandardDeviation,
                metrics.correctionLimitPercent, metrics.correctionReversalsPerSecond,
                metrics.pTermSpan, metrics.iTermSpan, metrics.dTermSpan,
                metrics.meanCoolant, metrics.fanOn, stableObservation);
        attempts.add(attempt);
        lastResult = "Attempt " + attemptNumber + " accepted — " + quality.label + ". Settling "
                + one(metrics.settlingSeconds) + " s; hold " + one(stableObservation) + " s; MAE "
                + one(metrics.meanAbsoluteError) + " RPM.";
        state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
        instruction = "Attempt accepted. Keep idle untouched while the next baseline is confirmed.";
        mark("ATTEMPT_ACCEPTED", quality.code);
        resetCurrentAttemptPreservingMarker();
    }

    private Quality grade(Metrics metrics, boolean preferredPeak, LiveCaptureSettings settings) {
        boolean fine = preferredPeak
                && metrics.settlingSeconds <= 12.0
                && metrics.meanAbsoluteError <= 30.0
                && metrics.correctionLimitPercent <= settings.getMaximumGoodCorrectionLimitPercent();
        if (fine) {
            return new Quality("Fine / Good for analysis", "ACCEPT_FINE",
                    "Complete recovery met the strict fine-tuning quality criteria.");
        }
        boolean standard = preferredPeak
                && metrics.settlingSeconds <= 20.0
                && metrics.meanAbsoluteError <= 65.0
                && metrics.correctionLimitPercent <= 50.0;
        if (standard) {
            return new Quality("Standard / Good for analysis", "ACCEPT_STANDARD",
                    "Complete recovery is suitable for normal PID analysis but did not meet every fine-tuning criterion.");
        }
        List<String> reasons = new ArrayList<String>();
        if (!preferredPeak) reasons.add("peak RPM was outside the preferred range");
        if (metrics.settlingSeconds > 20.0) reasons.add("settling exceeded 20 s");
        if (metrics.meanAbsoluteError > 65.0) reasons.add("mean RPM error remained high");
        if (metrics.correctionLimitPercent > 50.0) reasons.add("correction spent " + one(metrics.correctionLimitPercent) + "% at a configured limit");
        String reason = reasons.isEmpty()
                ? "Complete recovery accepted with the selected wide capture tolerance. Use it for coarse initial tuning, not final refinement."
                : join(reasons) + ". Accepted as coarse initial-tune data because the full recovery and observation completed.";
        return new Quality("Initial-tune / Usable", "ACCEPT_INITIAL", reason);
    }

    private void reject(String code, String reason, LiveSample sample) {
        double target = isFinite(attemptTarget) ? attemptTarget
                : sample == null ? Double.NaN : sample.get(LiveChannel.IDLE_TARGET);
        double trigger = triggerSeconds;
        LiveAttempt attempt = new LiveAttempt(
                attemptNumber <= 0 ? ++attemptNumber : attemptNumber,
                false, "Rejected", code, reason, trigger, attemptPeakRpm, target,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, false, Double.NaN);
        attempts.add(attempt);
        lastResult = "Attempt " + attempt.getNumber() + " rejected [" + code + "] — " + reason;
        state = LiveCaptureState.WAITING_FOR_STABLE_IDLE;
        instruction = "Attempt rejected. Wait for stable conditions and try again; the live session can continue.";
        mark("ATTEMPT_REJECTED", code);
        resetCurrentAttemptPreservingMarker();
    }

    private Metrics calculateMetrics(LiveCaptureSettings settings) {
        Metrics metrics = new Metrics();
        double targetSum = 0.0;
        double rpmSum = 0.0;
        double signedErrorSum = 0.0;
        double absErrorSum = 0.0;
        double coolantSum = 0.0;
        double maxOver = 0.0;
        double maxUnder = 0.0;
        double pMinimum = Double.NaN;
        double pMaximum = Double.NaN;
        double iMinimum = Double.NaN;
        double iMaximum = Double.NaN;
        double dMinimum = Double.NaN;
        double dMaximum = Double.NaN;
        int count = 0;
        int coolantCount = 0;
        int fanOnCount = 0;
        int correctionCount = 0;
        int atLimit = 0;
        int directionChanges = 0;
        int previousDirection = 0;
        double previousCorrection = Double.NaN;
        double firstTime = Double.NaN;
        double lastTime = Double.NaN;
        for (LiveSample sample : attemptSamples) {
            double rpm = sample.get(LiveChannel.RPM);
            double target = sample.get(LiveChannel.IDLE_TARGET);
            if (isFinite(rpm) && isFinite(target)) {
                targetSum += target;
                rpmSum += rpm;
                double signed = rpm - target;
                signedErrorSum += signed;
                absErrorSum += Math.abs(signed);
                maxOver = Math.max(maxOver, signed);
                maxUnder = Math.max(maxUnder, -signed);
                count++;
            }
            double coolant = sample.get(LiveChannel.CLT);
            if (isFinite(coolant)) { coolantSum += coolant; coolantCount++; }
            if (isTrue(sample, LiveChannel.FAN1)) fanOnCount++;
            pMinimum = minimum(pMinimum, sample.get(LiveChannel.P_TERM));
            pMaximum = maximum(pMaximum, sample.get(LiveChannel.P_TERM));
            iMinimum = minimum(iMinimum, sample.get(LiveChannel.I_TERM));
            iMaximum = maximum(iMaximum, sample.get(LiveChannel.I_TERM));
            dMinimum = minimum(dMinimum, sample.get(LiveChannel.D_TERM));
            dMaximum = maximum(dMaximum, sample.get(LiveChannel.D_TERM));
            double correction = sample.getCorrection();
            if (isFinite(correction)) {
                correctionCount++;
                if (Math.abs(correction - settings.getCorrectionMinimum()) <= settings.getCorrectionLimitTolerance()
                        || Math.abs(correction - settings.getCorrectionMaximum()) <= settings.getCorrectionLimitTolerance()) {
                    atLimit++;
                }
                if (isFinite(previousCorrection)) {
                    double delta = correction - previousCorrection;
                    int direction = delta > 0.5 ? 1 : (delta < -0.5 ? -1 : 0);
                    if (direction != 0 && previousDirection != 0 && direction != previousDirection) directionChanges++;
                    if (direction != 0) previousDirection = direction;
                }
                previousCorrection = correction;
            }
            if (!isFinite(firstTime)) firstTime = sample.getTimeSeconds();
            lastTime = sample.getTimeSeconds();
        }
        metrics.targetRpm = count == 0 ? attemptTarget : targetSum / count;
        metrics.meanSignedError = count == 0 ? Double.NaN : signedErrorSum / count;
        metrics.meanAbsoluteError = count == 0 ? Double.NaN : absErrorSum / count;
        metrics.overshootRpm = maxOver;
        metrics.undershootRpm = maxUnder;
        if (count > 0) {
            double meanRpm = rpmSum / count;
            double squares = 0.0;
            int rpmCount = 0;
            for (LiveSample sample : attemptSamples) {
                double rpm = sample.get(LiveChannel.RPM);
                if (isFinite(rpm)) { squares += (rpm - meanRpm) * (rpm - meanRpm); rpmCount++; }
            }
            metrics.rpmStandardDeviation = rpmCount == 0 ? Double.NaN : Math.sqrt(squares / rpmCount);
        }
        metrics.settlingSeconds = isFinite(settledSeconds) && isFinite(triggerSeconds)
                ? Math.max(0.0, settledSeconds - triggerSeconds) : Double.NaN;
        metrics.correctionLimitPercent = correctionCount == 0 ? Double.NaN : atLimit * 100.0 / correctionCount;
        double duration = isFinite(firstTime) && isFinite(lastTime) ? lastTime - firstTime : Double.NaN;
        metrics.correctionReversalsPerSecond = isFinite(duration) && duration > 0.0 ? directionChanges / duration : Double.NaN;
        metrics.pTermSpan = span(pMinimum, pMaximum);
        metrics.iTermSpan = span(iMinimum, iMaximum);
        metrics.dTermSpan = span(dMinimum, dMaximum);
        metrics.meanCoolant = coolantCount == 0 ? Double.NaN : coolantSum / coolantCount;
        metrics.fanOn = !attemptSamples.isEmpty() && fanOnCount > attemptSamples.size() / 2;
        return metrics;
    }

    private static double minimum(double current, double value) {
        if (!isFinite(value)) return current;
        return !isFinite(current) || value < current ? value : current;
    }

    private static double maximum(double current, double value) {
        if (!isFinite(value)) return current;
        return !isFinite(current) || value > current ? value : current;
    }

    private static double span(double minimum, double maximum) {
        return isFinite(minimum) && isFinite(maximum) ? Math.abs(maximum - minimum) : Double.NaN;
    }

    private double rollingRecoveryError(LiveCaptureSettings settings) {
        if (attemptSamples.isEmpty()) return Double.NaN;
        double end = attemptSamples.get(attemptSamples.size() - 1).getTimeSeconds();
        double start = end - settings.getRecoveryRollingWindowSeconds();
        double sum = 0.0;
        int count = 0;
        for (int i = attemptSamples.size() - 1; i >= 0; i--) {
            LiveSample sample = attemptSamples.get(i);
            if (sample.getTimeSeconds() < start) break;
            double rpm = sample.get(LiveChannel.RPM);
            double target = sample.get(LiveChannel.IDLE_TARGET);
            if (isFinite(rpm) && isFinite(target)) {
                sum += Math.abs(rpm - target);
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private BaselineStats baselineStats() {
        BaselineStats stats = new BaselineStats();
        double errorSum = 0.0;
        double rpmSum = 0.0;
        int count = 0;
        for (LiveSample sample : baselineSamples) {
            double rpm = sample.get(LiveChannel.RPM);
            double target = sample.get(LiveChannel.IDLE_TARGET);
            if (isFinite(rpm) && isFinite(target)) {
                errorSum += rpm - target;
                rpmSum += rpm;
                count++;
            }
        }
        if (count == 0) return stats;
        stats.meanSignedError = errorSum / count;
        double meanRpm = rpmSum / count;
        double squares = 0.0;
        for (LiveSample sample : baselineSamples) {
            double rpm = sample.get(LiveChannel.RPM);
            if (isFinite(rpm)) squares += (rpm - meanRpm) * (rpm - meanRpm);
        }
        stats.rpmDeviation = Math.sqrt(squares / count);
        return stats;
    }

    private void updatePeak(LiveSample sample) {
        double rpm = sample.get(LiveChannel.RPM);
        if (isFinite(rpm) && (!isFinite(attemptPeakRpm) || rpm > attemptPeakRpm)) attemptPeakRpm = rpm;
    }

    private boolean isAttemptActive() {
        return state == LiveCaptureState.REV_IN_PROGRESS
                || state == LiveCaptureState.WAITING_FOR_IDLE_CONTROL
                || state == LiveCaptureState.MEASURING_RECOVERY
                || state == LiveCaptureState.CONFIRMING_STABLE_IDLE;
    }

    private void resetCurrentAttempt() {
        baselineStableSince = Double.NaN;
        attemptPeakRpm = Double.NaN;
        attemptFan = Double.NaN;
        attemptTarget = Double.NaN;
        triggerSeconds = Double.NaN;
        settlingBandSince = Double.NaN;
        settledSeconds = Double.NaN;
        dfcoInactiveSince = Double.NaN;
        approachDfcoWasActive = false;
        lastApproachDfco = false;
        attemptSamples.clear();
    }

    private void resetCurrentAttemptPreservingMarker() {
        String marker = eventMarker;
        String code = resultCode;
        resetCurrentAttempt();
        eventMarker = marker;
        resultCode = code;
    }

    private void mark(String marker, String code) {
        eventMarker = marker == null ? "" : marker;
        resultCode = code == null ? "" : code;
    }

    public LiveCaptureState getState() { return state; }
    public String getInstruction() { return instruction; }
    public String getLastResult() { return lastResult; }
    public LiveReadiness getReadiness() { return readiness; }
    public String getEventMarker() { return eventMarker; }
    public String getResultCode() { return resultCode; }
    public int getCurrentAttemptNumber() { return attemptNumber; }
    public List<LiveAttempt> getAttempts() { return Collections.unmodifiableList(new ArrayList<LiveAttempt>(attempts)); }
    public int getAcceptedCount() {
        int count = 0;
        for (LiveAttempt attempt : attempts) if (attempt.isAccepted()) count++;
        return count;
    }
    public int getGoodCount() {
        int count = 0;
        for (LiveAttempt attempt : attempts) {
            if (attempt.isAccepted() && attempt.getQuality().contains("Good for analysis")) count++;
        }
        return count;
    }
    public int getRejectedCount() { return attempts.size() - getAcceptedCount(); }

    private static boolean isTrue(LiveSample sample, LiveChannel channel) {
        return sample.has(channel) && sample.get(channel) >= 0.5;
    }

    private static boolean isDfco(LiveSample sample) {
        return sample.has(LiveChannel.DFCO) && sample.get(LiveChannel.DFCO) >= 0.5;
    }

    private static boolean booleanChanged(double left, double right) {
        return (left >= 0.5) != (right >= 0.5);
    }

    private static String firstNotReady(LiveReadiness readiness) {
        for (Map.Entry<String, String> row : readiness.getRows().entrySet()) {
            if (row.getValue().startsWith("Not ready")) {
                return row.getKey() + " — " + row.getValue().substring("Not ready".length()).replaceFirst("^\\s*—\\s*", "");
            }
        }
        return "conditions are still stabilizing";
    }

    private static String join(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) builder.append(i == values.size() - 1 ? " and " : ", ");
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private static boolean isFinite(double value) { return LiveSample.isFinite(value); }
    private static String one(double value) { return isFinite(value) ? String.format(java.util.Locale.ROOT, "%.1f", value) : "—"; }
    private static String whole(double value) { return isFinite(value) ? String.valueOf((int) Math.round(value)) : "—"; }

    private static final class Metrics {
        private double targetRpm;
        private double settlingSeconds;
        private double overshootRpm;
        private double undershootRpm;
        private double meanSignedError;
        private double meanAbsoluteError;
        private double rpmStandardDeviation;
        private double correctionLimitPercent;
        private double correctionReversalsPerSecond;
        private double pTermSpan;
        private double iTermSpan;
        private double dTermSpan;
        private double meanCoolant;
        private boolean fanOn;
    }

    private static final class BaselineStats {
        private double meanSignedError = Double.NaN;
        private double rpmDeviation = Double.NaN;
    }

    private static final class Quality {
        private final String label;
        private final String code;
        private final String reason;
        private Quality(String label, String code, String reason) {
            this.label = label;
            this.code = code;
            this.reason = reason;
        }
    }
}
