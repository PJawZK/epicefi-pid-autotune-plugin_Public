package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * M2-only DC-IAC event extractor.
 *
 * Detects stable target holds and persistent opening/closing target steps.
 * It deliberately does not calculate response metrics or recommend settings.
 */
public final class DcIacEventExtractor {
    private static final int MAX_EVENTS = 250;
    private static final String[] M2_REQUIRED_CHANNELS = {
            "dcIdleTarget",
            "idlePositionSensor",
            "dcIdleDutyCycle",
            "dcIdlePositionStatus_pTerm",
            "dcIdlePositionStatus_iTerm",
            "dcIdlePositionStatus_dTerm",
            "dcIdlePositionStatus_output",
            "dcIdlePositionStatus_error",
            "dcIdlePositionStatus_resetCounter",
            "dcIdleFaultCode",
            "VBatt"
    };

    private final List<DcIacEvent> events = new ArrayList<DcIacEvent>();
    private final List<ProfileLiveSample> stableSamples = new ArrayList<ProfileLiveSample>();
    private final List<ProfileLiveSample> candidateSamples = new ArrayList<ProfileLiveSample>();
    private final List<ProfileLiveSample> activeSamples = new ArrayList<ProfileLiveSample>();

    private double stableTarget = Double.NaN;
    private double stableSince = Double.NaN;
    private boolean holdEmitted;

    private boolean candidateActive;
    private double candidateStart = Double.NaN;
    private double candidateFrom = Double.NaN;
    private double candidateTo = Double.NaN;

    private boolean stepActive;
    private DcIacEvent.Type stepType;
    private double stepStart = Double.NaN;
    private double stepFrom = Double.NaN;
    private double stepTo = Double.NaN;
    private double stepResetCounter = Double.NaN;

    private double lastObservedReset = Double.NaN;
    private int sequence;
    private String readiness = "Waiting for samples";
    private String lastTransition = "No M2 event yet";

    public void reset() {
        events.clear();
        sequence = 0;
        clearDetectionState();
        readiness = "Waiting for samples";
        lastTransition = "No M2 event yet";
    }

    public void stop() {
        clearDetectionState();
        readiness = "Stopped";
    }

    public void process(ProfileLiveSample sample, DcIacEventSettings settings, boolean motorControlPaused) {
        if (sample == null || settings == null) return;

        double reset = sample.get("dcIdlePositionStatus_resetCounter");
        boolean resetChanged = finite(reset) && finite(lastObservedReset)
                && Math.abs(reset - lastObservedReset) > 0.0001;
        if (finite(reset)) lastObservedReset = reset;

        if (stepActive) {
            activeSamples.add(sample);
            String issue = eventIssue(sample, settings, motorControlPaused);
            if (resetChanged || (finite(stepResetCounter) && finite(reset)
                    && Math.abs(reset - stepResetCounter) > 0.0001)) {
                finishStep(DcIacEvent.Status.REJECTED, "PID_RESET",
                        "PID reset counter changed during the response window.", sample);
                return;
            }
            if (issue != null) {
                finishStep(DcIacEvent.Status.REJECTED, issue, detailFor(issue, settings), sample);
                return;
            }
            if (Math.abs(sample.get("dcIdleTarget") - stepTo) > settings.getTargetTolerance()) {
                finishStep(DcIacEvent.Status.REJECTED, "TARGET_CHANGED",
                        "Target changed again before the response window completed.", sample);
                return;
            }
            if (batterySpread(activeSamples) > settings.getMaximumBatterySpread()) {
                finishStep(DcIacEvent.Status.REJECTED, "VOLTAGE_UNSTABLE",
                        "Battery voltage changed by more than " + one(settings.getMaximumBatterySpread())
                                + " V during the event.", sample);
                return;
            }
            readiness = "Capturing " + display(stepType) + " response";
            if (sample.getTimeSeconds() - stepStart >= settings.getStepCaptureSeconds()) {
                finishStep(DcIacEvent.Status.ACCEPTED, "STEP_ACCEPTED",
                        "Persistent target step completed the M2 response window.", sample);
            }
            return;
        }

        if (candidateActive) {
            candidateSamples.add(sample);
            double target = sample.get("dcIdleTarget");
            if (!finite(target)) {
                rejectCandidate("TARGET_UNAVAILABLE", "Target became unavailable during step confirmation.", sample);
                return;
            }
            if (Math.abs(target - candidateFrom) <= settings.getTargetTolerance()) {
                rejectCandidate("TARGET_NOT_PERSISTENT",
                        "Target returned to the previous plateau before the confirmation time; transient/glitch rejected.", sample);
                return;
            }
            if (Math.abs(target - candidateTo) > settings.getTargetTolerance()) {
                rejectCandidate("TARGET_CHANGED_DURING_CONFIRMATION",
                        "Target moved to another value before the candidate step was confirmed.", sample);
                return;
            }
            readiness = "Confirming target step persistence";
            if (sample.getTimeSeconds() - candidateStart >= settings.getStepConfirmationSeconds()) {
                String issue = validateCandidate(settings, motorControlPaused);
                if (issue != null) {
                    rejectCandidate(issue, detailFor(issue, settings), sample);
                    return;
                }
                promoteCandidateToStep(sample);
            }
            return;
        }

        if (resetChanged) {
            restartStableBaseline(sample);
            readiness = "PID reset observed; rebuilding stable target baseline";
            return;
        }

        double target = sample.get("dcIdleTarget");
        if (!finite(stableTarget)) {
            String baselineIssue = eventIssue(sample, settings, motorControlPaused);
            if (baselineIssue != null) {
                clearStableOnly();
                readiness = detailFor(baselineIssue, settings);
                return;
            }
            restartStableBaseline(sample);
            readiness = "Building stable target baseline";
            return;
        }

        if (!finite(target)) {
            clearStableOnly();
            readiness = "DC-IAC target is unavailable.";
            return;
        }

        double stableDuration = sample.getTimeSeconds() - stableSince;
        double delta = target - stableTarget;
        if (Math.abs(delta) > settings.getTargetTolerance()) {
            if (stableDuration >= settings.getPreStepStableSeconds()
                    && Math.abs(delta) >= settings.getMinimumStepSize()) {
                startCandidate(sample);
                readiness = "Confirming target step persistence";
                return;
            }

            String movedIssue = eventIssue(sample, settings, motorControlPaused);
            if (movedIssue != null) {
                clearStableOnly();
                readiness = detailFor(movedIssue, settings);
                return;
            }
            restartStableBaseline(sample);
            readiness = "Target moved before a valid pre-step plateau; rebuilding baseline";
            return;
        }

        String baselineIssue = eventIssue(sample, settings, motorControlPaused);
        if (baselineIssue != null) {
            clearStableOnly();
            readiness = detailFor(baselineIssue, settings);
            return;
        }

        stableSamples.add(sample);
        trimStableSamples(sample.getTimeSeconds(), settings.getStableHoldSeconds() + 0.25);
        readiness = stableDuration >= settings.getPreStepStableSeconds()
                ? "Ready for a persistent target step"
                : "Building stable target baseline";

        if (!holdEmitted && stableDuration >= settings.getStableHoldSeconds()) {
            List<ProfileLiveSample> hold = recentStableWindow(sample.getTimeSeconds(), settings.getStableHoldSeconds());
            if (holdDuration(hold) >= settings.getStableHoldSeconds() * 0.95
                    && batterySpread(hold) <= settings.getMaximumBatterySpread()
                    && maximumAbsoluteError(hold) <= settings.getMaximumStableHoldError()) {
                addEvent(new DcIacEvent(
                        ++sequence,
                        DcIacEvent.Type.STABLE_HOLD,
                        DcIacEvent.Status.ACCEPTED,
                        hold.get(0).getTimeSeconds(),
                        hold.get(hold.size() - 1).getTimeSeconds(),
                        mean(hold, "dcIdleTarget"),
                        mean(hold, "dcIdleTarget"),
                        "HOLD_ACCEPTED",
                        "Stable target hold passed M2 readiness and stability gates.",
                        hold));
                holdEmitted = true;
                lastTransition = "Accepted stable hold at " + one(target) + "%";
            } else {
                readiness = "Target stable; waiting for position/voltage stability";
            }
        }
    }

    public List<DcIacEvent> getEvents() {
        return Collections.unmodifiableList(new ArrayList<DcIacEvent>(events));
    }

    public String getReadiness() { return readiness; }
    public String getLastTransition() { return lastTransition; }
    public boolean isCapturingStep() { return stepActive || candidateActive; }

    private void startCandidate(ProfileLiveSample sample) {
        candidateActive = true;
        candidateStart = sample.getTimeSeconds();
        candidateFrom = stableTarget;
        candidateTo = sample.get("dcIdleTarget");
        candidateSamples.clear();
        candidateSamples.addAll(stableSamples);
        candidateSamples.add(sample);
        holdEmitted = false;
    }

    private void promoteCandidateToStep(ProfileLiveSample sample) {
        candidateActive = false;
        stepActive = true;
        stepStart = candidateStart;
        stepFrom = candidateFrom;
        stepTo = candidateTo;
        stepType = stepTo > stepFrom ? DcIacEvent.Type.OPENING_STEP : DcIacEvent.Type.CLOSING_STEP;
        activeSamples.clear();
        activeSamples.addAll(candidateSamples);
        stepResetCounter = firstFinite(activeSamples, "dcIdlePositionStatus_resetCounter");
        candidateSamples.clear();
        readiness = "Capturing " + display(stepType) + " response";
        lastTransition = "Confirmed " + display(stepType) + " " + one(stepFrom) + "% → " + one(stepTo) + "%";
    }

    private String validateCandidate(DcIacEventSettings settings, boolean motorControlPaused) {
        double firstReset = Double.NaN;
        for (ProfileLiveSample sample : candidateSamples) {
            String issue = eventIssue(sample, settings, motorControlPaused);
            if (issue != null) return issue;
            double reset = sample.get("dcIdlePositionStatus_resetCounter");
            if (!finite(firstReset) && finite(reset)) firstReset = reset;
            if (finite(firstReset) && finite(reset) && Math.abs(reset - firstReset) > 0.0001) return "PID_RESET";
        }
        if (batterySpread(candidateSamples) > settings.getMaximumBatterySpread()) return "VOLTAGE_UNSTABLE";
        return null;
    }

    private void rejectCandidate(String code, String detail, ProfileLiveSample sample) {
        DcIacEvent.Type type = candidateTo >= candidateFrom
                ? DcIacEvent.Type.OPENING_STEP : DcIacEvent.Type.CLOSING_STEP;
        addEvent(new DcIacEvent(
                ++sequence,
                type,
                DcIacEvent.Status.REJECTED,
                candidateStart,
                sample.getTimeSeconds(),
                candidateFrom,
                candidateTo,
                code,
                detail,
                candidateSamples));
        lastTransition = "Rejected " + display(type) + ": " + code;
        candidateActive = false;
        candidateSamples.clear();
        restartStableBaseline(sample);
    }

    private void finishStep(DcIacEvent.Status status, String code, String detail, ProfileLiveSample sample) {
        addEvent(new DcIacEvent(
                ++sequence,
                stepType,
                status,
                stepStart,
                sample.getTimeSeconds(),
                stepFrom,
                stepTo,
                code,
                detail,
                activeSamples));
        lastTransition = (status == DcIacEvent.Status.ACCEPTED ? "Accepted " : "Rejected ")
                + display(stepType) + " " + one(stepFrom) + "% → " + one(stepTo) + "%: " + code;
        stepActive = false;
        activeSamples.clear();
        stepType = null;
        restartStableBaseline(sample);
    }

    private String eventIssue(ProfileLiveSample sample, DcIacEventSettings settings, boolean motorControlPaused) {
        if (motorControlPaused) return "MOTOR_CONTROL_PAUSED";
        for (String name : M2_REQUIRED_CHANNELS) {
            if (!sample.has(name) || sample.getAgeSeconds(name) > settings.getChannelFreshnessSeconds()) {
                return "CHANNEL_MISSING_OR_STALE";
            }
        }
        double voltage = sample.get("VBatt");
        if (!finite(voltage) || voltage < settings.getMinimumBatteryVoltage()) return "LOW_VOLTAGE";
        if (voltage > settings.getMaximumBatteryVoltage()) return "HIGH_VOLTAGE";
        double fault = sample.get("dcIdleFaultCode");
        if (!finite(fault) || Math.abs(fault) > 0.0001) return "DC_IAC_FAULT";
        double target = sample.get("dcIdleTarget");
        if (!finite(target)) return "TARGET_UNAVAILABLE";
        double low = settings.getMinimumTarget() + settings.getTargetLimitMargin();
        double high = settings.getMaximumTarget() - settings.getTargetLimitMargin();
        if (target <= low || target >= high) return "TARGET_NEAR_LIMIT";
        double duty = sample.get("dcIdleDutyCycle");
        if (!finite(duty)) return "DUTY_UNAVAILABLE";
        if (Math.abs(duty) >= settings.getSaturationDutyPercent()) return "DUTY_SATURATED";
        return null;
    }

    private String detailFor(String code, DcIacEventSettings settings) {
        if ("MOTOR_CONTROL_PAUSED".equals(code)) return "Optional shared motor-pause flag is active; motor-control data cannot be tuning evidence.";
        if ("CHANNEL_MISSING_OR_STALE".equals(code)) return "A required M2 live channel is missing or stale.";
        if ("LOW_VOLTAGE".equals(code)) return "Battery voltage is below " + one(settings.getMinimumBatteryVoltage()) + " V; actuator authority is not valid for tuning.";
        if ("HIGH_VOLTAGE".equals(code)) return "Battery voltage is above " + one(settings.getMaximumBatteryVoltage()) + " V.";
        if ("VOLTAGE_UNSTABLE".equals(code)) return "Battery voltage changed too much during the candidate event.";
        if ("DC_IAC_FAULT".equals(code)) return "dcIdleFaultCode is non-zero or unavailable.";
        if ("PID_RESET".equals(code)) return "PID reset counter changed inside the candidate event.";
        if ("TARGET_UNAVAILABLE".equals(code)) return "DC-IAC target is unavailable.";
        if ("TARGET_NEAR_LIMIT".equals(code)) return "Target is within " + one(settings.getTargetLimitMargin()) + "% of a configured DC-IAC travel limit.";
        if ("DUTY_UNAVAILABLE".equals(code)) return "Signed H-bridge duty is unavailable.";
        if ("DUTY_SATURATED".equals(code)) return "Signed H-bridge demand reached the M2 saturation guard (|duty| ≥ " + one(settings.getSaturationDutyPercent()) + "%).";
        return code;
    }

    private void restartStableBaseline(ProfileLiveSample sample) {
        clearStableOnly();
        double target = sample == null ? Double.NaN : sample.get("dcIdleTarget");
        if (sample != null && finite(target)) {
            stableTarget = target;
            stableSince = sample.getTimeSeconds();
            stableSamples.add(sample);
        }
    }

    private void clearStableOnly() {
        stableTarget = Double.NaN;
        stableSince = Double.NaN;
        stableSamples.clear();
        holdEmitted = false;
    }

    private void clearDetectionState() {
        clearStableOnly();
        candidateActive = false;
        candidateStart = Double.NaN;
        candidateFrom = Double.NaN;
        candidateTo = Double.NaN;
        candidateSamples.clear();
        stepActive = false;
        stepType = null;
        stepStart = Double.NaN;
        stepFrom = Double.NaN;
        stepTo = Double.NaN;
        stepResetCounter = Double.NaN;
        activeSamples.clear();
        lastObservedReset = Double.NaN;
    }

    private void addEvent(DcIacEvent event) {
        events.add(event);
        while (events.size() > MAX_EVENTS) events.remove(0);
    }

    private void trimStableSamples(double now, double keepSeconds) {
        while (stableSamples.size() > 1
                && now - stableSamples.get(0).getTimeSeconds() > keepSeconds) {
            stableSamples.remove(0);
        }
    }

    private List<ProfileLiveSample> recentStableWindow(double now, double seconds) {
        List<ProfileLiveSample> result = new ArrayList<ProfileLiveSample>();
        double start = now - seconds;
        for (ProfileLiveSample sample : stableSamples) {
            if (sample.getTimeSeconds() >= start) result.add(sample);
        }
        return result;
    }

    private static double holdDuration(List<ProfileLiveSample> samples) {
        if (samples.size() < 2) return 0.0;
        return samples.get(samples.size() - 1).getTimeSeconds() - samples.get(0).getTimeSeconds();
    }

    private static double batterySpread(List<ProfileLiveSample> samples) {
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get("VBatt");
            if (!finite(value)) continue;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return min == Double.POSITIVE_INFINITY ? Double.POSITIVE_INFINITY : max - min;
    }

    private static double maximumAbsoluteError(List<ProfileLiveSample> samples) {
        double max = 0.0;
        boolean found = false;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get("dcIdlePositionStatus_error");
            if (!finite(value)) continue;
            max = Math.max(max, Math.abs(value));
            found = true;
        }
        return found ? max : Double.POSITIVE_INFINITY;
    }

    private static double mean(List<ProfileLiveSample> samples, String name) {
        double sum = 0.0;
        int count = 0;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get(name);
            if (finite(value)) {
                sum += value;
                count++;
            }
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double firstFinite(List<ProfileLiveSample> samples, String name) {
        for (ProfileLiveSample sample : samples) {
            double value = sample.get(name);
            if (finite(value)) return value;
        }
        return Double.NaN;
    }

    private static String display(DcIacEvent.Type type) {
        if (type == DcIacEvent.Type.OPENING_STEP) return "opening step";
        if (type == DcIacEvent.Type.CLOSING_STEP) return "closing step";
        return "stable hold";
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String one(double value) {
        if (!finite(value)) return "—";
        return String.format(java.util.Locale.US, "%.1f", value);
    }
}
