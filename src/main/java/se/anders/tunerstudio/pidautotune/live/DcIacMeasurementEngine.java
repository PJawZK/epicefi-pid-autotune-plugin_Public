package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Measurement-only M3 engine for accepted M2 DC-IAC events.
 *
 * M3 never recommends gains or writes controller settings. It adds a second
 * validity/evidence layer above M2 and measures only from read-only live evidence.
 *
 * M3.3 keeps source-update-aware timing from M3.2 and adds evidence-quality
 * classification discovered from real Mega144H7 logs: static holds must show
 * target/PV/I-term equilibrium before becoming bias candidates, dynamic steps
 * carry initial-I and operating-point context, and a P+D command-edge model can
 * flag likely physical motor saturation that a ~40-50 Hz plugin stream may miss.
 */
public final class DcIacMeasurementEngine {
    private static final String TARGET = "dcIdleTarget";
    private static final String ACTUAL = "idlePositionSensor";
    private static final String DUTY = "dcIdleDutyCycle";
    private static final String P_TERM = "dcIdlePositionStatus_pTerm";
    private static final String I_TERM = "dcIdlePositionStatus_iTerm";
    private static final String D_TERM = "dcIdlePositionStatus_dTerm";
    private static final String PID_OUTPUT = "dcIdlePositionStatus_output";
    private static final String RESET = "dcIdlePositionStatus_resetCounter";
    private static final String VBATT = "VBatt";

    public DcIacMeasurement measure(DcIacEvent event, DcIacMeasurementSettings settings) {
        if (event == null) throw new IllegalArgumentException("event cannot be null");
        if (settings == null) throw new IllegalArgumentException("settings cannot be null");
        if (!event.isAccepted()) {
            return invalid(event, "M2_EVENT_REJECTED",
                    "M3 only measures M2-accepted events.", Double.NaN, Double.NaN, Double.NaN, 0);
        }
        if (hasDiscontinuity(event.getSamples(), RESET)) {
            return invalid(event, "PID_RESET_DISCONTINUITY",
                    "PID reset/reinitialization counter changed inside retained evidence; continuity is invalid regardless of counter direction.",
                    sampleRate(series(event.getSamples(), ACTUAL)), Double.NaN, Double.NaN,
                    series(event.getSamples(), ACTUAL).size());
        }
        if (event.getType() == DcIacEvent.Type.STABLE_HOLD) return measureHold(event, settings);
        return measureStep(event, settings);
    }

    private DcIacMeasurement measureHold(DcIacEvent event, DcIacMeasurementSettings settings) {
        List<ProfileLiveSample> samples = event.getSamples();
        List<TimedValue> actualSeries = series(samples, ACTUAL);
        double rate = sampleRate(actualSeries);
        if (actualSeries.size() < settings.getMinimumSamples()) {
            return invalid(event, "INSUFFICIENT_SAMPLES",
                    "Accepted hold has too few distinct process-value source updates for M3 measurement.",
                    rate, Double.NaN, Double.NaN, actualSeries.size());
        }

        List<TimedValue> targetSeries = series(samples, TARGET);
        List<TimedValue> dutySeries = series(samples, DUTY);
        List<TimedValue> pSeries = series(samples, P_TERM);
        List<TimedValue> iSeries = series(samples, I_TERM);
        List<TimedValue> dSeries = series(samples, D_TERM);
        List<TimedValue> pidSeries = series(samples, PID_OUTPUT);
        List<TimedValue> vbattSeries = series(samples, VBATT);

        double target = mean(targetSeries);
        double actual = mean(actualSeries);
        double ssError = finite(target) && finite(actual) ? target - actual : Double.NaN;
        double mae = meanAbsoluteError(actualSeries, target);
        double maxError = maximumAbsoluteError(actualSeries, target);
        double peakDuty = peakAbsolute(dutySeries);
        double actualJitter = stdDev(actualSeries);
        double dutyJitter = stdDev(dutySeries);
        double pidMean = mean(pidSeries);
        double iMean = mean(iSeries);
        double batterySpread = span(vbattSeries);
        double targetSlope = slope(targetSeries);
        double actualSlope = slope(actualSeries);
        double iSlope = slope(iSeries);
        double actualSpan = span(actualSeries);
        double observedFeedForward = meanDifference(samples, DUTY, PID_OUTPUT,
                Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);

        List<String> flags = new ArrayList<String>();
        if (!finite(targetSlope) || Math.abs(targetSlope) > settings.getMaximumEquilibriumTargetSlopePerSecond()) {
            flags.add("TARGET_MOVING");
        }
        if (!finite(actualSlope) || Math.abs(actualSlope) > settings.getMaximumEquilibriumActualSlopePerSecond()) {
            flags.add("ACTUAL_MOVING");
        }
        if (!finite(iSlope) || Math.abs(iSlope) > settings.getMaximumEquilibriumITermSlopePerSecond()) {
            flags.add("I_TERM_MOVING");
        }
        if (!finite(actualSpan) || actualSpan > settings.getMaximumEquilibriumActualSpan()) {
            flags.add("ACTUAL_SPAN_HIGH");
        }
        if (event.getDurationSeconds() < settings.getMinimumEquilibriumCandidateDurationSeconds()) {
            flags.add("HOLD_TOO_SHORT");
        }

        boolean equilibriumCandidate = flags.isEmpty();
        String resultCode = equilibriumCandidate
                ? "HOLD_EQUILIBRIUM_CANDIDATE"
                : "HOLD_MEASURED_NOT_EQUILIBRIUM";
        String detail = equilibriumCandidate
                ? "M2 hold is position/target/I-term settled enough to be a static-bias equilibrium candidate. A normal two-second hold is still candidate evidence only; final bias learning must aggregate longer/repeated evidence."
                : "Hold remains valid M3 observation, but target, process value or I term is still moving enough that it must not be treated as static-bias equilibrium evidence.";

        return new DcIacMeasurement(
                event.getSequence(), event.getType(), DcIacMeasurement.Validity.VALID,
                resultCode, detail,
                actualSeries.size(), event.getDurationSeconds(), event.getFromTarget(), event.getToTarget(), rate,
                Double.NaN, Double.NaN,
                actual, actual,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                ssError, mae, maxError, peakDuty,
                dutyReversals(dutySeries, settings.getDutyReversalDeadband()),
                span(pSeries), span(iSeries), span(dSeries), batterySpread,
                actualJitter, dutyJitter, pidMean, iMean,
                0.0, target, iMean,
                targetSlope, actualSlope, iSlope, observedFeedForward, Double.NaN,
                false, false, equilibriumCandidate, joinFlags(flags));
    }

    private DcIacMeasurement measureStep(DcIacEvent event, DcIacMeasurementSettings settings) {
        List<ProfileLiveSample> all = event.getSamples();
        List<TimedValue> actualAll = series(all, ACTUAL);
        double rate = sampleRate(actualAll);
        if (actualAll.size() < settings.getMinimumSamples()) {
            return invalid(event, "INSUFFICIENT_SAMPLES",
                    "Accepted step has too few distinct process-value source updates for M3 measurement.",
                    rate, Double.NaN, Double.NaN, actualAll.size());
        }

        double eventDirection = event.getToTarget() >= event.getFromTarget() ? 1.0 : -1.0;
        double stepStart = resolveSourceStepStart(all, event, eventDirection);
        if (!finite(stepStart)) stepStart = event.getStartSeconds();

        List<TimedValue> pre = window(actualAll,
                stepStart - settings.getPreStepWindowSeconds(), stepStart, false);
        List<TimedValue> post = window(actualAll, stepStart, Double.POSITIVE_INFINITY, true);
        if (pre.size() < 5 || duration(pre) < settings.getMinimumPreStepDurationSeconds()) {
            return invalid(event, "PRESTEP_WINDOW_INSUFFICIENT",
                    "Not enough distinct pre-step process-value source history was retained for measurement validation.",
                    rate, span(pre), slope(pre), actualAll.size());
        }
        if (post.size() < 2) {
            return invalid(event, "RESPONSE_WINDOW_INSUFFICIENT",
                    "Not enough distinct post-step process-value source updates were retained for dynamic measurement.",
                    rate, span(pre), slope(pre), actualAll.size());
        }

        double preSpan = span(pre);
        double preSlope = slope(pre);
        if (!finite(preSpan) || preSpan > settings.getMaximumPreStepActualSpan()) {
            return invalid(event, "PRESTEP_NOT_SETTLED",
                    "Process value span before the target step exceeds the M3 settled-state gate.",
                    rate, preSpan, preSlope, actualAll.size());
        }
        if (!finite(preSlope) || Math.abs(preSlope) > settings.getMaximumPreStepSlopePerSecond()) {
            return invalid(event, "PRESTEP_NOT_SETTLED",
                    "Process value slope before the target step exceeds the M3 settled-state gate.",
                    rate, preSpan, preSlope, actualAll.size());
        }

        double initialActual = mean(pre);
        double target = event.getToTarget();
        List<TimedValue> targetSeries = series(all, TARGET);
        List<TimedValue> preTargetSeries = window(targetSeries,
                stepStart - settings.getPreStepWindowSeconds(), stepStart, false);
        double localPreTarget = mean(preTargetSeries);
        if (!finite(localPreTarget)) localPreTarget = event.getFromTarget();
        double commandStep = target - localPreTarget;
        double stepMagnitude = Math.abs(commandStep);
        double operatingCenter = (localPreTarget + target) * 0.5;
        double direction = commandStep >= 0.0 ? 1.0 : -1.0;
        if (direction != eventDirection) direction = eventDirection;
        double requiredTravel = direction * (target - initialActual);
        if (!finite(requiredTravel) || requiredTravel <= 0.0) {
            return invalid(event, "INITIAL_CONDITION_INCOHERENT",
                    "Initial measured position is not on the expected side of the new target.",
                    rate, preSpan, preSlope, actualAll.size());
        }

        double maximumProgress = 0.0;
        for (TimedValue point : post) {
            maximumProgress = Math.max(maximumProgress, direction * (point.value - initialActual));
        }
        if (maximumProgress < requiredTravel * settings.getMinimumResponseFraction()) {
            return invalid(event, "RESPONSE_TOO_SMALL",
                    "Measured position did not move far enough toward the new target for dynamic timing metrics.",
                    rate, preSpan, preSlope, actualAll.size());
        }

        List<TimedValue> response = new ArrayList<TimedValue>();
        if (!pre.isEmpty()) response.add(pre.get(pre.size() - 1));
        response.addAll(post);

        double delayTime = crossingTimeInterpolated(response, initialActual, direction,
                requiredTravel * settings.getDelayFraction());
        double lowTime = crossingTimeInterpolated(response, initialActual, direction,
                requiredTravel * settings.getRiseLowFraction());
        double highTime = crossingTimeInterpolated(response, initialActual, direction,
                requiredTravel * settings.getRiseHighFraction());
        double delay = finite(delayTime) ? Math.max(0.0, delayTime - stepStart) : Double.NaN;
        double rise = finite(lowTime) && finite(highTime) && highTime >= lowTime
                ? highTime - lowTime : Double.NaN;

        double settlingTolerance = Math.max(
                settings.getSettlingAbsoluteTolerance(),
                stepMagnitude * settings.getSettlingFractionOfStep());
        double settling = settlingTime(post, stepStart, target, settlingTolerance,
                settings.getSettlingMinimumRemainingSeconds());

        double overshoot = overshootPercent(post, target, direction, stepMagnitude);
        List<TimedValue> steady = trailingWindow(post, settings.getSteadyWindowSeconds());
        double finalActual = mean(steady);
        double ssError = finite(finalActual) ? target - finalActual : Double.NaN;
        double mae = meanAbsoluteError(post, target);
        double maxError = maximumAbsoluteError(response, target);

        // TunerStudio output-channel callbacks for one ECU/runtime update are not
        // guaranteed to share exactly the same callback timestamp. Include the
        // immediately preceding distinct update for transient channels so a command-
        // boundary spike is not discarded solely because that callback arrived a few
        // milliseconds before the target callback.
        List<TimedValue> dutyAll = series(all, DUTY);
        List<TimedValue> pAll = series(all, P_TERM);
        List<TimedValue> iAll = series(all, I_TERM);
        List<TimedValue> dAll = series(all, D_TERM);
        List<TimedValue> pidAll = series(all, PID_OUTPUT);
        List<TimedValue> dutyResponse = responseWindow(dutyAll, stepStart);
        List<TimedValue> pResponse = responseWindow(pAll, stepStart);
        List<TimedValue> iResponse = responseWindow(iAll, stepStart);
        List<TimedValue> dResponse = responseWindow(dAll, stepStart);
        List<TimedValue> pidResponse = responseWindow(pidAll, stepStart);
        List<TimedValue> vbattResponse = responseWindow(series(all, VBATT), stepStart);

        List<TimedValue> preI = window(iAll,
                stepStart - settings.getPreStepWindowSeconds(), stepStart, false);
        List<TimedValue> preIRecent = trailingWindow(preI, 0.20);
        double initialITerm = mean(preIRecent);
        double iSlope = slope(preI);
        double targetSlope = slope(preTargetSeries);
        double observedFeedForward = meanDifference(all, DUTY, PID_OUTPUT,
                stepStart - settings.getPreStepWindowSeconds(), stepStart);

        double processPeriod = medianInterval(post);
        double transientPeriod = maxFinite(
                processPeriod,
                medianInterval(dutyResponse),
                medianInterval(pResponse),
                medianInterval(dResponse));
        boolean timingUnavailable = !finite(rise);
        boolean sourceLimited = finite(rise) && finite(transientPeriod)
                && rise <= transientPeriod * settings.getTimingResolutionMultiplier();

        // Firmware computes D from error, so an abrupt setpoint change creates a
        // command-edge derivative kick before the plant can move. The plugin stream
        // can miss that ~2 ms cycle. This estimate intentionally excludes unknown
        // feed-forward change: crossing the limit is sufficient evidence of risk,
        // while staying below it is NOT proof that saturation could not have occurred.
        double preDuty = lastBefore(dutyAll, stepStart);
        double predictedEdgeDuty = Double.NaN;
        boolean predictedSaturationRisk = false;
        boolean saturationPredictionAvailable = settings.arePidGainsKnown()
                && finite(preDuty) && settings.getNominalControllerLoopSeconds() > 0.0;
        if (saturationPredictionAvailable) {
            double setpointKick = settings.getPFactor() * commandStep
                    + settings.getDFactor() / settings.getNominalControllerLoopSeconds() * commandStep;
            predictedEdgeDuty = preDuty + setpointKick;
            predictedSaturationRisk = Math.abs(predictedEdgeDuty) >= settings.getPhysicalMotorDutyLimitPercent();
        }

        List<String> flags = new ArrayList<String>();
        if (timingUnavailable) flags.add("TIMING_UNAVAILABLE");
        else if (sourceLimited) flags.add("SOURCE_RESOLUTION_LIMITED");
        if (!saturationPredictionAvailable) flags.add("SATURATION_PREDICTION_UNAVAILABLE");
        if (predictedSaturationRisk) flags.add("PREDICTED_COMMAND_EDGE_SATURATION");
        if (stepMagnitude > settings.getMaximumQuantitativeStepSize()) flags.add("STEP_TOO_LARGE_FOR_LOCAL_ID");
        if (peakAbsolute(dutyResponse) >= settings.getPhysicalMotorDutyLimitPercent()) flags.add("OBSERVED_PHYSICAL_DUTY_LIMIT");

        String resultCode;
        String detail;
        if (timingUnavailable) {
            resultCode = "STEP_MEASURED_TIMING_UNAVAILABLE";
            detail = "Step remains valid, but 10-90% timing could not be resolved from distinct process-value source updates; transient timing/peak/span metrics are not quantitative M4 evidence.";
        } else if (sourceLimited) {
            resultCode = "STEP_MEASURED_TIMING_LIMITED";
            detail = "Step remains valid, but the 10-90% response spans fewer than about three relevant source-update intervals; timing, overshoot, peak duty and P/I/D spans are source-resolution-limited observed bounds.";
        } else if (finite(settling)) {
            resultCode = "STEP_MEASURED";
            detail = "Dynamic response metrics measured from a settled pre-step state with sufficient distinct source-update resolution.";
        } else {
            resultCode = "STEP_MEASURED";
            detail = "Dynamic response measured with sufficient source-update resolution; response did not remain inside the settling band during the retained window.";
        }

        boolean dynamicQuantitative = "STEP_MEASURED".equals(resultCode)
                && saturationPredictionAvailable
                && !predictedSaturationRisk
                && stepMagnitude <= settings.getMaximumQuantitativeStepSize()
                && peakAbsolute(dutyResponse) < settings.getPhysicalMotorDutyLimitPercent();

        if (predictedSaturationRisk) {
            detail += " P+D command-edge estimate reaches/exceeds the firmware's physical 90% motor clamp, so this response is not quantitative gain-identification evidence even if the plugin stream did not sample the peak.";
        }
        if (stepMagnitude > settings.getMaximumQuantitativeStepSize()) {
            detail += " Command step is larger than the local-identification guard; retain it as response context but do not combine it with small local steps for gain identification.";
        }
        if (!saturationPredictionAvailable) {
            detail += " Configured P/I/D gains were unavailable, so command-edge saturation risk could not be cleared for quantitative use.";
        }

        return new DcIacMeasurement(
                event.getSequence(), event.getType(), DcIacMeasurement.Validity.VALID,
                resultCode, detail,
                actualAll.size(), event.getDurationSeconds(), event.getFromTarget(), event.getToTarget(), rate,
                preSpan, preSlope,
                initialActual, finalActual,
                delay, rise, settling, overshoot,
                ssError, mae, maxError, peakAbsolute(dutyResponse),
                dutyReversals(dutyResponse, settings.getDutyReversalDeadband()),
                span(pResponse), span(iResponse), span(dResponse), span(vbattResponse),
                stdDev(steady), stdDev(dutyResponse), mean(pidResponse), mean(iResponse),
                stepMagnitude, operatingCenter, initialITerm,
                targetSlope, preSlope, iSlope, observedFeedForward, predictedEdgeDuty,
                predictedSaturationRisk, dynamicQuantitative, false, joinFlags(flags));
    }

    private static DcIacMeasurement invalid(
            DcIacEvent event,
            String code,
            String detail,
            double rate,
            double preSpan,
            double preSlope,
            int sampleCount) {
        return new DcIacMeasurement(
                event.getSequence(), event.getType(), DcIacMeasurement.Validity.INVALID,
                code, detail,
                sampleCount, event.getDurationSeconds(), event.getFromTarget(), event.getToTarget(), rate,
                preSpan, preSlope,
                Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                0,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN,
                Double.NaN, Double.NaN, Double.NaN, Double.NaN);
    }

    private static double resolveSourceStepStart(
            List<ProfileLiveSample> samples,
            DcIacEvent event,
            double direction) {
        double magnitude = Math.abs(event.getToTarget() - event.getFromTarget());
        if (!(magnitude > 0.0)) return event.getStartSeconds();
        double required = magnitude * 0.50;
        double best = Double.NaN;
        for (ProfileLiveSample sample : samples) {
            double target = sample.get(TARGET);
            if (!finite(target)) continue;
            if (direction * (target - event.getFromTarget()) < required) continue;
            double updateTime = sample.getUpdateTimeSeconds(TARGET);
            if (!finite(updateTime)) continue;
            if (updateTime < event.getStartSeconds() - 0.25) continue;
            if (!finite(best) || updateTime < best) best = updateTime;
        }
        return best;
    }

    private static List<TimedValue> series(List<ProfileLiveSample> samples, String name) {
        List<TimedValue> result = new ArrayList<TimedValue>();
        double lastTime = Double.NaN;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get(name);
            double updateTime = sample.getUpdateTimeSeconds(name);
            if (!finite(value) || !finite(updateTime)) continue;
            if (finite(lastTime) && Math.abs(updateTime - lastTime) < 1e-9) continue;
            result.add(new TimedValue(updateTime, value));
            lastTime = updateTime;
        }
        Collections.sort(result, new Comparator<TimedValue>() {
            @Override
            public int compare(TimedValue a, TimedValue b) {
                return Double.compare(a.time, b.time);
            }
        });
        return result;
    }

    private static List<TimedValue> window(
            List<TimedValue> points, double start, double end, boolean includeStart) {
        List<TimedValue> result = new ArrayList<TimedValue>();
        for (TimedValue point : points) {
            if (includeStart) {
                if (point.time >= start && point.time < end) result.add(point);
            } else if (point.time > start && point.time < end) {
                result.add(point);
            }
        }
        return result;
    }

    /** Returns all points at/after start plus the single immediately preceding point. */
    private static List<TimedValue> responseWindow(List<TimedValue> points, double start) {
        List<TimedValue> result = new ArrayList<TimedValue>();
        TimedValue previous = null;
        for (TimedValue point : points) {
            if (point.time < start) {
                previous = point;
            } else {
                if (result.isEmpty() && previous != null) result.add(previous);
                result.add(point);
            }
        }
        if (result.isEmpty() && previous != null) result.add(previous);
        return result;
    }

    private static List<TimedValue> trailingWindow(List<TimedValue> points, double seconds) {
        if (points.isEmpty()) return points;
        double end = points.get(points.size() - 1).time;
        double start = end - seconds;
        List<TimedValue> result = new ArrayList<TimedValue>();
        for (TimedValue point : points) if (point.time >= start) result.add(point);
        return result.isEmpty() ? points : result;
    }

    private static double crossingTimeInterpolated(
            List<TimedValue> points, double initial, double direction, double threshold) {
        if (points.isEmpty()) return Double.NaN;
        TimedValue previous = points.get(0);
        double previousProgress = direction * (previous.value - initial);
        if (previousProgress >= threshold) return previous.time;
        for (int i = 1; i < points.size(); i++) {
            TimedValue current = points.get(i);
            double currentProgress = direction * (current.value - initial);
            if (currentProgress >= threshold) {
                double deltaProgress = currentProgress - previousProgress;
                if (deltaProgress <= 1e-12 || current.time <= previous.time) return current.time;
                double fraction = (threshold - previousProgress) / deltaProgress;
                fraction = Math.max(0.0, Math.min(1.0, fraction));
                return previous.time + fraction * (current.time - previous.time);
            }
            previous = current;
            previousProgress = currentProgress;
        }
        return Double.NaN;
    }

    private static double settlingTime(
            List<TimedValue> points,
            double stepStart,
            double target,
            double tolerance,
            double minimumRemaining) {
        if (points.isEmpty()) return Double.NaN;
        double end = points.get(points.size() - 1).time;
        for (int i = 0; i < points.size(); i++) {
            TimedValue candidate = points.get(i);
            if (end - candidate.time < minimumRemaining) break;
            boolean allInside = true;
            for (int j = i; j < points.size(); j++) {
                if (Math.abs(target - points.get(j).value) > tolerance) {
                    allInside = false;
                    break;
                }
            }
            if (allInside) return Math.max(0.0, candidate.time - stepStart);
        }
        return Double.NaN;
    }

    private static double overshootPercent(
            List<TimedValue> points, double target, double direction, double commandStepMagnitude) {
        if (!(commandStepMagnitude > 0.0)) return Double.NaN;
        double overshoot = 0.0;
        for (TimedValue point : points) {
            overshoot = Math.max(overshoot, direction * (point.value - target));
        }
        return Math.max(0.0, overshoot) / commandStepMagnitude * 100.0;
    }

    private static int dutyReversals(List<TimedValue> points, double deadband) {
        int previous = 0;
        int reversals = 0;
        for (TimedValue point : points) {
            double duty = point.value;
            if (Math.abs(duty) < deadband) continue;
            int sign = duty > 0.0 ? 1 : -1;
            if (previous != 0 && sign != previous) reversals++;
            previous = sign;
        }
        return reversals;
    }

    private static boolean hasDiscontinuity(List<ProfileLiveSample> samples, String name) {
        double previous = Double.NaN;
        for (ProfileLiveSample sample : samples) {
            double value = sample.get(name);
            if (!finite(value)) continue;
            if (finite(previous) && Math.abs(value - previous) > 0.0001) return true;
            previous = value;
        }
        return false;
    }

    private static double meanDifference(
            List<ProfileLiveSample> samples,
            String a,
            String b,
            double start,
            double end) {
        double sum = 0.0;
        int count = 0;
        for (ProfileLiveSample sample : samples) {
            double time = sample.getTimeSeconds();
            if (time < start || time >= end) continue;
            double av = sample.get(a);
            double bv = sample.get(b);
            if (!finite(av) || !finite(bv)) continue;
            sum += av - bv;
            count++;
        }
        return count == 0 ? Double.NaN : sum / count;
    }

    private static double lastBefore(List<TimedValue> points, double time) {
        double value = Double.NaN;
        for (TimedValue point : points) {
            if (point.time >= time) break;
            value = point.value;
        }
        return value;
    }

    private static String joinFlags(List<String> flags) {
        if (flags == null || flags.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String flag : flags) {
            if (builder.length() > 0) builder.append(';');
            builder.append(flag);
        }
        return builder.toString();
    }

    private static double sampleRate(List<TimedValue> points) {
        double d = duration(points);
        return points.size() >= 2 && d > 0.0 ? (points.size() - 1) / d : Double.NaN;
    }

    private static double medianInterval(List<TimedValue> points) {
        if (points.size() < 2) return Double.NaN;
        List<Double> intervals = new ArrayList<Double>();
        for (int i = 1; i < points.size(); i++) {
            double delta = points.get(i).time - points.get(i - 1).time;
            if (delta > 1e-9) intervals.add(Double.valueOf(delta));
        }
        if (intervals.isEmpty()) return Double.NaN;
        Collections.sort(intervals);
        int middle = intervals.size() / 2;
        if ((intervals.size() & 1) == 1) return intervals.get(middle).doubleValue();
        return (intervals.get(middle - 1).doubleValue() + intervals.get(middle).doubleValue()) * 0.5;
    }

    private static double maxFinite(double a, double b, double c, double d) {
        double max = Double.NaN;
        if (finite(a)) max = a;
        if (finite(b)) max = finite(max) ? Math.max(max, b) : b;
        if (finite(c)) max = finite(max) ? Math.max(max, c) : c;
        if (finite(d)) max = finite(max) ? Math.max(max, d) : d;
        return max;
    }

    private static double duration(List<TimedValue> points) {
        if (points.size() < 2) return 0.0;
        return points.get(points.size() - 1).time - points.get(0).time;
    }

    private static double meanAbsoluteError(List<TimedValue> points, double target) {
        if (!finite(target) || points.isEmpty()) return Double.NaN;
        double sum = 0.0;
        for (TimedValue point : points) sum += Math.abs(target - point.value);
        return sum / points.size();
    }

    private static double maximumAbsoluteError(List<TimedValue> points, double target) {
        if (!finite(target) || points.isEmpty()) return Double.NaN;
        double max = 0.0;
        for (TimedValue point : points) max = Math.max(max, Math.abs(target - point.value));
        return max;
    }

    private static double peakAbsolute(List<TimedValue> points) {
        if (points.isEmpty()) return Double.NaN;
        double max = 0.0;
        for (TimedValue point : points) max = Math.max(max, Math.abs(point.value));
        return max;
    }

    private static double mean(List<TimedValue> points) {
        if (points.isEmpty()) return Double.NaN;
        double sum = 0.0;
        for (TimedValue point : points) sum += point.value;
        return sum / points.size();
    }

    private static double span(List<TimedValue> points) {
        if (points.isEmpty()) return Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (TimedValue point : points) {
            min = Math.min(min, point.value);
            max = Math.max(max, point.value);
        }
        return max - min;
    }

    private static double stdDev(List<TimedValue> points) {
        double mean = mean(points);
        if (!finite(mean)) return Double.NaN;
        if (points.size() < 2) return 0.0;
        double sum = 0.0;
        for (TimedValue point : points) {
            double delta = point.value - mean;
            sum += delta * delta;
        }
        return Math.sqrt(sum / points.size());
    }

    private static double slope(List<TimedValue> points) {
        if (points.size() < 2) return Double.NaN;
        double origin = points.get(0).time;
        double sumT = 0.0;
        double sumV = 0.0;
        double sumTT = 0.0;
        double sumTV = 0.0;
        int count = 0;
        for (TimedValue point : points) {
            double t = point.time - origin;
            sumT += t;
            sumV += point.value;
            sumTT += t * t;
            sumTV += t * point.value;
            count++;
        }
        double denominator = count * sumTT - sumT * sumT;
        if (Math.abs(denominator) < 1e-12) return Double.NaN;
        return (count * sumTV - sumT * sumV) / denominator;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class TimedValue {
        private final double time;
        private final double value;

        private TimedValue(double time, double value) {
            this.time = time;
            this.value = value;
        }
    }
}
