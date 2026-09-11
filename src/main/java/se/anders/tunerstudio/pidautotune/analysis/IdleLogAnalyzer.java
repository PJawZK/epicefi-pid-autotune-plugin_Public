package se.anders.tunerstudio.pidautotune.analysis;

import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.LogChannelDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Detects closed-loop idle regions, steady holds, and repeatable load transitions without changing ECU settings. */
public final class IdleLogAnalyzer {
    public IdleAnalysisResult analyze(IdleLogData data, IdleAnalysisSettings settings) {
        if (data == null) throw new IllegalArgumentException("Log data cannot be null");
        if (settings == null) settings = IdleAnalysisSettings.defaults();

        double[] time = required(data, LogChannelDefinition.TIME);
        required(data, LogChannelDefinition.RPM);
        required(data, LogChannelDefinition.IDLE_TARGET);
        int count = time.length;

        List<String> warnings = new ArrayList<String>();
        boolean[] idleMask = createIdleMask(data, settings, warnings, count);
        bridgeShortGaps(idleMask, time, 0.25);

        int duplicateTimes = countDuplicateOrReverseTimes(time);
        if (duplicateTimes > 0) {
            warnings.add(duplicateTimes + " duplicate or non-increasing timestamp intervals were retained; positive intervals determine sample rate.");
        }
        if (data.getSampleRateHz() > 0.0 && data.getSampleRateHz() < 10.0) {
            warnings.add("The median sample rate is below 10 Hz; settling and derivative behaviour may be poorly resolved.");
        }
        if (data.getSkippedRows() > 0) {
            warnings.add(data.getSkippedRows() + " non-sample rows, such as TunerStudio MARK rows, were skipped.");
        }

        double[] correction = buildCorrection(data, warnings);
        boolean derivedCorrectionUsed = data.has(LogChannelDefinition.CURRENT_IDLE_POSITION)
                && data.has(LogChannelDefinition.BASE_IDLE_POSITION);

        List<Segment> baseSegments = findSegments(idleMask, time, settings.getMinimumRegionSeconds());
        List<IdleEvent> events = new ArrayList<IdleEvent>();
        double closedLoopSeconds = 0.0;
        for (Segment baseSegment : baseSegments) {
            closedLoopSeconds += Math.max(0.0, time[baseSegment.end] - time[baseSegment.start]);

            // Known fan transitions retain a pre-trigger baseline, while response metrics begin at the trigger.
            addFanEvents(events, baseSegment, data, settings, correction);

            // Split broad regions at known operating changes, then separately identify target ramps,
            // steady holds, and sustained uncommanded RPM disturbances inside each portion.
            for (Segment segment : splitAtOperatingChanges(baseSegment, data, settings)) {
                boolean returnToIdle = segment.reason == StartReason.CLOSED_LOOP_ENTRY;
                String type = describeSegmentType(segment.reason);
                int responseEnd = returnToIdle
                        ? trimAfterSettledResponse(segment.start, segment.end, data, settings)
                        : segment.end;
                events.add(buildEvent(
                        type,
                        returnToIdle ? "Transient response" : "Context only",
                        segment.start,
                        segment.start,
                        responseEnd,
                        returnToIdle,
                        !returnToIdle,
                        returnToIdle,
                        false,
                        data,
                        settings,
                        correction));
                addPhaseEvents(events, segment, data, settings, correction);
            }
        }

        if (baseSegments.isEmpty()) {
            warnings.add("No valid closed-loop idle region longer than " + settings.getMinimumRegionSeconds() + " seconds was detected.");
        } else {
            warnings.add(baseSegments.size() + " valid closed-loop idle region(s) were detected, totalling " + roundOne(closedLoopSeconds) + " seconds.");
        }

        if (!data.has(LogChannelDefinition.TPS)) {
            warnings.add("TPS is not present, so throttle-state cross-checking is unavailable.");
        }
        if (!data.has(LogChannelDefinition.VEHICLE_SPEED)) {
            warnings.add("Vehicle speed is not present, so moving/coasting rejection relies on the ECU idle-state flag.");
        }
        if (!data.has(LogChannelDefinition.FAN1)) {
            warnings.add("Fan 1 state is not present, so fan-load disturbance events cannot be isolated.");
        }
        if (!data.has(LogChannelDefinition.IDLE_TARGET_BASE)) {
            warnings.add("Base idle target is not present; return-target ramps are detected from target stability alone.");
        }

        if (correction != null && touchesCorrectionLimit(correction, idleMask, settings)) {
            warnings.add("The derived idle correction reaches or closely approaches its configured authority limit in at least one idle sample.");
        }

        Collections.sort(events, new Comparator<IdleEvent>() {
            @Override
            public int compare(IdleEvent left, IdleEvent right) {
                int triggerCompare = Double.compare(left.getTriggerSeconds(), right.getTriggerSeconds());
                if (triggerCompare != 0) return triggerCompare;
                int useCompare = left.getAnalysisUse().compareToIgnoreCase(right.getAnalysisUse());
                return useCompare != 0 ? useCompare : left.getType().compareToIgnoreCase(right.getType());
            }
        });

        int acceptedResponses = 0;
        int steadyHolds = 0;
        int rejected = 0;
        int overview = 0;
        for (IdleEvent event : events) {
            if (event.isFutureGainEligible()) acceptedResponses++;
            if (event.isSteadyHold() && ("Good for analysis".equals(event.getQuality()) || "Usable".equals(event.getQuality()))) steadyHolds++;
            if ("Rejected".equals(event.getQuality()) || "Poor".equals(event.getQuality())) rejected++;
            if ("Overview only".equals(event.getQuality())) overview++;
        }
        warnings.add(steadyHolds + " steady-idle hold(s) are usable for stability diagnostics; "
                + acceptedResponses + " transient response event(s) are currently eligible for future gain calculations.");
        warnings.add(rejected + " event(s) are poor or rejected, and " + overview + " row(s) are context-only.");

        return new IdleAnalysisResult(events, warnings, closedLoopSeconds, derivedCorrectionUsed);
    }

    private static boolean[] createIdleMask(
            IdleLogData data,
            IdleAnalysisSettings settings,
            List<String> warnings,
            int count) {
        double[] closedLoop = data.getSeries(LogChannelDefinition.CLOSED_LOOP_ACTIVE);
        double[] idling = data.getSeries(LogChannelDefinition.IDLING);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double[] tps = data.getSeries(LogChannelDefinition.TPS);
        double[] speed = data.getSeries(LogChannelDefinition.VEHICLE_SPEED);
        double[] dfco = data.getSeries(LogChannelDefinition.DFCO);

        boolean[] mask = new boolean[count];
        if (closedLoop != null || idling != null) {
            for (int i = 0; i < count; i++) {
                boolean active = closedLoop != null ? value(closedLoop, i) > 0.5 : value(idling, i) > 0.5;
                double rpmValue = value(rpm, i);
                double targetValue = value(target, i);
                active &= finite(rpmValue) && finite(targetValue) && targetValue > 0.0;
                active &= rpmValue >= settings.getEngineRunningMinimumRpm();
                if (tps != null && finite(value(tps, i))) active &= value(tps, i) < settings.getTpsThreshold();
                if (speed != null && finite(value(speed, i))) active &= value(speed, i) <= settings.getMaxVehicleSpeed();
                if (dfco != null && finite(value(dfco, i))) active &= value(dfco, i) < 0.5;
                mask[i] = active;
            }
            warnings.add(closedLoop != null
                    ? "Idle regions use the ECU's closed-loop-active channel, cross-checked against running RPM, TPS, VSS, and DFCO."
                    : "Idle regions use the ECU's idling-state channel, cross-checked against running RPM, TPS, VSS, and DFCO.");
            return mask;
        }

        warnings.add("No ECU idle-state flag was mapped; idle regions are derived from RPM, TPS, VSS, and DFCO limits.");
        for (int i = 0; i < count; i++) {
            double rpmValue = value(rpm, i);
            double targetValue = value(target, i);
            boolean active = finite(rpmValue) && finite(targetValue)
                    && targetValue > 0.0
                    && rpmValue >= settings.getEngineRunningMinimumRpm()
                    && rpmValue <= targetValue + settings.getRpmUpperLimit();
            if (tps != null && finite(value(tps, i))) active &= value(tps, i) < settings.getTpsThreshold();
            if (speed != null && finite(value(speed, i))) active &= value(speed, i) <= settings.getMaxVehicleSpeed();
            if (dfco != null && finite(value(dfco, i))) active &= value(dfco, i) < 0.5;
            mask[i] = active;
        }
        return mask;
    }

    private static double[] buildCorrection(IdleLogData data, List<String> warnings) {
        double[] current = data.getSeries(LogChannelDefinition.CURRENT_IDLE_POSITION);
        double[] base = data.getSeries(LogChannelDefinition.BASE_IDLE_POSITION);
        double[] reported = data.getSeries(LogChannelDefinition.PID_OUTPUT);

        if (current != null && base != null) {
            int count = Math.min(current.length, base.length);
            double[] correction = new double[count];
            for (int i = 0; i < count; i++) {
                correction[i] = finite(current[i]) && finite(base[i]) ? current[i] - base[i] : Double.NaN;
            }
            if (reported != null && finiteRange(reported) < 0.0001) {
                warnings.add("The reported PID output is constant zero; correction is derived as commanded idle position minus base idle position.");
            } else {
                warnings.add("Idle correction is derived as commanded idle position minus base idle position for consistent EPICEFI analysis.");
            }
            return correction;
        }

        if (reported != null) {
            warnings.add("Base and commanded idle position are not both present; the reported PID-output channel is used directly.");
            return reported;
        }

        warnings.add("No usable idle-correction signal was mapped; correction range, activity, and saturation checks are unavailable.");
        return null;
    }

    private static void addFanEvents(
            List<IdleEvent> events,
            Segment segment,
            IdleLogData data,
            IdleAnalysisSettings settings,
            double[] correction) {
        double[] fan = data.getSeries(LogChannelDefinition.FAN1);
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        if (fan == null) return;

        for (int i = Math.max(segment.start + 1, 1); i <= segment.end; i++) {
            boolean previous = value(fan, i - 1) > 0.5;
            boolean current = value(fan, i) > 0.5;
            if (previous == current) continue;

            double transitionTime = time[i];
            int start = lowerBound(time,
                    Math.max(time[segment.start], transitionTime - settings.getFanPreTriggerSeconds()),
                    segment.start,
                    segment.end);
            int end = lowerBound(time,
                    Math.min(time[segment.end], transitionTime + settings.getFanPostTriggerSeconds()),
                    i,
                    segment.end);
            if (end <= i) continue;
            end = trimAfterSettledResponse(i, end, data, settings);
            events.add(buildEvent(
                    current ? "Fan 1 switched on" : "Fan 1 switched off",
                    "Transient response",
                    start,
                    i,
                    end,
                    true,
                    false,
                    true,
                    false,
                    data,
                    settings,
                    correction));
        }
    }

    private static List<Segment> splitAtOperatingChanges(
            Segment source,
            IdleLogData data,
            IdleAnalysisSettings settings) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double[] fan = data.getSeries(LogChannelDefinition.FAN1);
        List<Segment> result = new ArrayList<Segment>();
        int start = source.start;
        StartReason reason = source.reason;

        for (int i = source.start + 1; i <= source.end; i++) {
            boolean fanChanged = fan != null && finite(value(fan, i - 1)) && finite(value(fan, i))
                    && (value(fan, i - 1) > 0.5) != (value(fan, i) > 0.5);
            boolean targetStepped = finite(value(target, i - 1)) && finite(value(target, i))
                    && Math.abs(value(target, i) - value(target, i - 1)) >= settings.getTargetStepThresholdRpm();
            if (!fanChanged && !targetStepped) continue;

            int previousEnd = i - 1;
            if (previousEnd >= start && time[previousEnd] - time[start] >= settings.getMinimumRegionSeconds()) {
                result.add(new Segment(start, previousEnd, reason));
                start = i;
                if (fanChanged) reason = value(fan, i) > 0.5 ? StartReason.FAN_ON : StartReason.FAN_OFF;
                else reason = StartReason.TARGET_STEP;
            }
        }

        if (source.end >= start && time[source.end] - time[start] >= settings.getMinimumRegionSeconds()) {
            result.add(new Segment(start, source.end, reason));
        } else if (!result.isEmpty()) {
            Segment previous = result.remove(result.size() - 1);
            result.add(new Segment(previous.start, source.end, previous.reason));
        }
        if (result.isEmpty()) result.add(source);
        return result;
    }

    private static void addPhaseEvents(
            List<IdleEvent> events,
            Segment segment,
            IdleLogData data,
            IdleAnalysisSettings settings,
            double[] correction) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        int phaseStart = findTargetRampEnd(segment, data, settings);

        if (phaseStart > segment.start
                && time[phaseStart] - time[segment.start] >= 1.0) {
            events.add(buildEvent(
                    "Target-ramp entry",
                    "Context only",
                    segment.start,
                    segment.start,
                    phaseStart,
                    false,
                    true,
                    true,
                    false,
                    data,
                    settings,
                    correction));
        }

        int currentStart = phaseStart;
        while (currentStart < segment.end) {
            Disturbance disturbance = findDisturbance(currentStart, segment.end, data, settings);
            if (disturbance == null) {
                addSteadyHoldIfLongEnough(events, currentStart, segment.end, data, settings, correction);
                break;
            }

            addSteadyHoldIfLongEnough(events, currentStart, disturbance.index - 1, data, settings, correction);
            int recovery = findRecovery(disturbance.index, segment.end, data, settings);
            int disturbanceEnd = recovery < 0
                    ? segment.end
                    : lowerBound(time,
                            Math.min(time[segment.end], time[recovery] + settings.getDisturbanceRecoverySeconds()),
                            recovery,
                            segment.end);

            // A fan event already represents an immediate response at the beginning of a fan-split segment.
            boolean duplicateKnownFanResponse = (segment.reason == StartReason.FAN_ON || segment.reason == StartReason.FAN_OFF)
                    && time[disturbance.index] - time[segment.start] <= 0.5;
            if (!duplicateKnownFanResponse) {
                int windowStart = lowerBound(time,
                        Math.max(time[currentStart], time[disturbance.index] - settings.getDisturbancePreTriggerSeconds()),
                        currentStart,
                        disturbance.index);
                events.add(buildEvent(
                        disturbance.direction < 0 ? "Low-RPM load disturbance" : "High-RPM disturbance",
                        "Transient response",
                        windowStart,
                        disturbance.index,
                        disturbanceEnd,
                        true,
                        false,
                        false,
                        false,
                        data,
                        settings,
                        correction));
            }

            if (recovery < 0 || disturbanceEnd >= segment.end) break;
            currentStart = disturbanceEnd + 1;
        }
    }

    private static void addSteadyHoldIfLongEnough(
            List<IdleEvent> events,
            int start,
            int end,
            IdleLogData data,
            IdleAnalysisSettings settings,
            double[] correction) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        if (start < 0 || end < start || end >= time.length) return;
        if (time[end] - time[start] < settings.getMinimumSteadyHoldSeconds()) return;
        events.add(buildEvent(
                "Steady idle hold",
                "Steady-state diagnostics",
                start,
                start,
                end,
                false,
                false,
                false,
                true,
                data,
                settings,
                correction));
    }

    private static int findTargetRampEnd(
            Segment segment,
            IdleLogData data,
            IdleAnalysisSettings settings) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double[] baseTarget = data.getSeries(LogChannelDefinition.IDLE_TARGET_BASE);
        double required = settings.getTargetStableRequiredSeconds();

        int initialEnd = lowerBound(time,
                Math.min(time[segment.end], time[segment.start] + required),
                segment.start,
                segment.end);
        boolean initiallyStable = time[initialEnd] - time[segment.start] >= required * 0.85
                && finiteRange(target, segment.start, initialEnd) <= settings.getSteadyTargetRangeRpm()
                && targetNearBase(target, baseTarget, segment.start, initialEnd, settings.getTargetBaseToleranceRpm());
        if (initiallyStable) return segment.start;

        for (int i = segment.start; i <= segment.end; i++) {
            int end = lowerBound(time, Math.min(time[segment.end], time[i] + required), i, segment.end);
            if (time[end] - time[i] < required * 0.85) break;
            if (finiteRange(target, i, end) > settings.getSteadyTargetRangeRpm()) continue;
            if (!targetNearBase(target, baseTarget, i, end, settings.getTargetBaseToleranceRpm())) continue;
            return i;
        }
        return segment.start;
    }

    private static boolean targetNearBase(
            double[] target,
            double[] baseTarget,
            int start,
            int end,
            double tolerance) {
        if (baseTarget == null) return true;
        List<Double> differences = new ArrayList<Double>();
        for (int i = start; i <= end; i++) {
            if (finite(value(target, i)) && finite(value(baseTarget, i))) {
                differences.add(Math.abs(value(target, i) - value(baseTarget, i)));
            }
        }
        return differences.isEmpty() || median(differences) <= tolerance;
    }

    private static Disturbance findDisturbance(
            int start,
            int end,
            IdleLogData data,
            IdleAnalysisSettings settings) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double required = settings.getDisturbanceConfirmationSeconds();
        double threshold = settings.getDisturbanceMeanErrorRpm();

        for (int i = Math.max(0, start); i <= end; i++) {
            int windowEnd = lowerBound(time, Math.min(time[end], time[i] + required), i, end);
            if (time[windowEnd] - time[i] < required * 0.85) break;
            double sum = 0.0;
            int count = 0;
            for (int j = i; j <= windowEnd; j++) {
                double rpmValue = value(rpm, j);
                double targetValue = value(target, j);
                if (!finite(rpmValue) || !finite(targetValue)) continue;
                sum += rpmValue - targetValue;
                count++;
            }
            if (count == 0) continue;
            double mean = sum / count;
            if (Math.abs(mean) < threshold) continue;
            int sameDirection = 0;
            for (int j = i; j <= windowEnd; j++) {
                double rpmValue = value(rpm, j);
                double targetValue = value(target, j);
                if (!finite(rpmValue) || !finite(targetValue)) continue;
                double error = rpmValue - targetValue;
                if ((mean < 0.0 && error < 0.0) || (mean > 0.0 && error > 0.0)) sameDirection++;
            }
            if (sameDirection >= Math.ceil(count * 0.75)) return new Disturbance(i, mean < 0.0 ? -1 : 1);
        }
        return null;
    }

    private static int findRecovery(
            int start,
            int end,
            IdleLogData data,
            IdleAnalysisSettings settings) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double required = settings.getDisturbanceRecoverySeconds();

        for (int i = Math.max(0, start); i <= end; i++) {
            int windowEnd = lowerBound(time, Math.min(time[end], time[i] + required), i, end);
            if (time[windowEnd] - time[i] < required * 0.85) break;
            double absoluteSum = 0.0;
            int count = 0;
            int insidePercentileLimit = 0;
            for (int j = i; j <= windowEnd; j++) {
                double rpmValue = value(rpm, j);
                double targetValue = value(target, j);
                if (!finite(rpmValue) || !finite(targetValue)) continue;
                double absolute = Math.abs(rpmValue - targetValue);
                absoluteSum += absolute;
                count++;
                if (absolute <= settings.getRecoveryPercentileErrorRpm()) insidePercentileLimit++;
            }
            if (count == 0) continue;
            if (absoluteSum / count <= settings.getRecoveryMeanAbsoluteErrorRpm()
                    && insidePercentileLimit >= Math.ceil(count * 0.95)) return i;
        }
        return -1;
    }

    private static IdleEvent buildEvent(
            String type,
            String analysisUse,
            int start,
            int trigger,
            int end,
            boolean responseEvent,
            boolean overviewOnly,
            boolean allowTargetRamp,
            boolean steadyState,
            IdleLogData data,
            IdleAnalysisSettings settings,
            double[] correction) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        double[] fan = data.getSeries(LogChannelDefinition.FAN1);
        double[] coolant = data.getSeries(LogChannelDefinition.CLT);
        double[] currentIdle = data.getSeries(LogChannelDefinition.CURRENT_IDLE_POSITION);
        double[] baseIdle = data.getSeries(LogChannelDefinition.BASE_IDLE_POSITION);
        double[] pTerm = data.getSeries(LogChannelDefinition.P_TERM);
        double[] iTerm = data.getSeries(LogChannelDefinition.I_TERM);
        double[] dTerm = data.getSeries(LogChannelDefinition.D_TERM);

        start = Math.max(0, Math.min(start, time.length - 1));
        trigger = Math.max(start, Math.min(trigger, end));
        end = Math.max(trigger, Math.min(end, time.length - 1));
        int metricStart = responseEvent ? trigger : start;

        List<Double> targetValues = new ArrayList<Double>();
        List<Double> coolantValues = new ArrayList<Double>();
        double coolantMin = Double.POSITIVE_INFINITY;
        double coolantMax = Double.NEGATIVE_INFINITY;
        double rpmSum = 0.0;
        double rpmSquareSum = 0.0;
        int rpmCount = 0;
        double signedErrorSum = 0.0;
        double absoluteErrorSum = 0.0;
        int errorCount = 0;
        double overshoot = 0.0;
        double undershoot = 0.0;
        double minimumRpmRatio = Double.POSITIVE_INFINITY;
        double targetMin = Double.POSITIVE_INFINITY;
        double targetMax = Double.NEGATIVE_INFINITY;
        double correctionMin = Double.POSITIVE_INFINITY;
        double correctionMax = Double.NEGATIVE_INFINITY;
        int correctionCount = 0;
        int correctionLimitCount = 0;
        double commandedMin = Double.POSITIVE_INFINITY;
        double commandedMax = Double.NEGATIVE_INFINITY;
        double baseMin = Double.POSITIVE_INFINITY;
        double baseMax = Double.NEGATIVE_INFINITY;
        double pMin = Double.POSITIVE_INFINITY;
        double pMax = Double.NEGATIVE_INFINITY;
        double iMin = Double.POSITIVE_INFINITY;
        double iMax = Double.NEGATIVE_INFINITY;
        double dMin = Double.POSITIVE_INFINITY;
        double dMax = Double.NEGATIVE_INFINITY;
        double fanMin = Double.POSITIVE_INFINITY;
        double fanMax = Double.NEGATIVE_INFINITY;

        for (int i = start; i <= end; i++) {
            if (fan != null && finite(value(fan, i))) {
                fanMin = Math.min(fanMin, value(fan, i));
                fanMax = Math.max(fanMax, value(fan, i));
            }
            if (i < metricStart) continue;

            double rpmValue = value(rpm, i);
            double targetValue = value(target, i);
            if (finite(targetValue)) {
                targetValues.add(targetValue);
                targetMin = Math.min(targetMin, targetValue);
                targetMax = Math.max(targetMax, targetValue);
            }
            double coolantValue = value(coolant, i);
            if (finite(coolantValue)) {
                coolantValues.add(coolantValue);
                coolantMin = Math.min(coolantMin, coolantValue);
                coolantMax = Math.max(coolantMax, coolantValue);
            }
            if (finite(rpmValue)) {
                rpmSum += rpmValue;
                rpmSquareSum += rpmValue * rpmValue;
                rpmCount++;
            }
            if (finite(rpmValue) && finite(targetValue)) {
                double error = rpmValue - targetValue;
                signedErrorSum += error;
                absoluteErrorSum += Math.abs(error);
                errorCount++;
                overshoot = Math.max(overshoot, error);
                undershoot = Math.max(undershoot, -error);
                if (targetValue > 0.0) minimumRpmRatio = Math.min(minimumRpmRatio, rpmValue / targetValue);
            }
            if (correction != null && i < correction.length && finite(correction[i])) {
                correctionMin = Math.min(correctionMin, correction[i]);
                correctionMax = Math.max(correctionMax, correction[i]);
                correctionCount++;
                if (correction[i] <= settings.getCorrectionMinimum() + settings.getCorrectionLimitTolerance()
                        || correction[i] >= settings.getCorrectionMaximum() - settings.getCorrectionLimitTolerance()) {
                    correctionLimitCount++;
                }
            }
            commandedMin = minimum(commandedMin, value(currentIdle, i));
            commandedMax = maximum(commandedMax, value(currentIdle, i));
            baseMin = minimum(baseMin, value(baseIdle, i));
            baseMax = maximum(baseMax, value(baseIdle, i));
            pMin = minimum(pMin, value(pTerm, i));
            pMax = maximum(pMax, value(pTerm, i));
            iMin = minimum(iMin, value(iTerm, i));
            iMax = maximum(iMax, value(iTerm, i));
            dMin = minimum(dMin, value(dTerm, i));
            dMax = maximum(dMax, value(dTerm, i));
        }

        double evaluationDuration = Math.max(0.0, time[end] - time[metricStart]);
        double averageRpm = rpmCount == 0 ? Double.NaN : rpmSum / rpmCount;
        double variance = rpmCount == 0 ? Double.NaN : Math.max(0.0, rpmSquareSum / rpmCount - averageRpm * averageRpm);
        double rpmStandardDeviation = Double.isNaN(variance) ? Double.NaN : Math.sqrt(variance);
        double meanSignedError = errorCount == 0 ? Double.NaN : signedErrorSum / errorCount;
        double meanError = errorCount == 0 ? Double.NaN : absoluteErrorSum / errorCount;
        double targetDrift = targetMin == Double.POSITIVE_INFINITY ? Double.NaN : targetMax - targetMin;
        double correctionLimitPercent = correctionCount == 0
                ? Double.NaN
                : correctionLimitCount * 100.0 / correctionCount;
        double correctionReversals = calculateCorrectionReversalsPerSecond(
                time, correction, metricStart, end,
                settings.getCorrectionActivityBucketSeconds(),
                settings.getCorrectionDirectionDeadband());
        boolean engineStopsSoonAfter = engineStopsSoonAfter(data, end, 1.5);
        boolean targetChangedUnexpectedly = !allowTargetRamp
                && finite(targetDrift)
                && targetDrift >= settings.getTargetStepThresholdRpm();
        boolean severeRpmExcursion = minimumRpmRatio != Double.POSITIVE_INFINITY
                && minimumRpmRatio < settings.getSevereRpmRatio();
        boolean correctionSaturated = finite(correctionLimitPercent)
                && correctionLimitPercent > settings.getMaximumCorrectionLimitFraction() * 100.0;
        boolean settlingApplicable = responseEvent;
        double settling = settlingApplicable
                ? calculateSettlingTime(
                        time,
                        rpm,
                        target,
                        trigger,
                        end,
                        settings.getResponseSettlingBandRpm(),
                        settings.getSettlingRequiredSeconds())
                : Double.NaN;

        List<String> notes = new ArrayList<String>();
        if (overviewOnly) notes.add("Not an isolated disturbance; excluded from future gain calculations.");
        if (steadyState) notes.add("Steady-state diagnostics only; this hold cannot identify all P/I/D gains by itself.");
        if (responseEvent && settings.hasLiveCaptureSettlingBand()) {
            notes.add("Live capture profile " + settings.getCaptureProfileName() + " supplied a ±"
                    + roundOne(settings.getResponseSettlingBandRpm()) + " RPM capture band. Quality grading still reports the measured error and saturation separately.");
        }
        if (engineStopsSoonAfter) notes.add("Engine speed falls to zero within 1.5 s after the analysis window.");
        if (correctionSaturated) notes.add("Correction is at a configured limit for " + roundOne(correctionLimitPercent) + "% of evaluated samples.");
        if (severeRpmExcursion) notes.add("Minimum RPM falls below " + roundOne(settings.getSevereRpmRatio() * 100.0) + "% of target.");
        if (targetChangedUnexpectedly) notes.add("Idle target changes by more than " + roundOne(settings.getTargetStepThresholdRpm()) + " RPM during evaluation.");
        if (settlingApplicable && Double.isNaN(settling) && evaluationDuration >= 10.0) {
            notes.add("RPM does not remain inside the settling band for " + roundOne(settings.getSettlingRequiredSeconds()) + " s.");
        }
        if (finite(correctionReversals)
                && correctionReversals > settings.getHighCorrectionReversalRatePerSecond()) {
            notes.add("Command changes direction " + roundOne(correctionReversals)
                    + " times/s after " + roundOne(settings.getCorrectionActivityBucketSeconds() * 1000.0) + " ms averaging.");
        }
        double derivativeSpan = rangeOrNaN(dMin, dMax);
        if (finite(derivativeSpan) && derivativeSpan > settings.getHighDerivativeSpan()) {
            notes.add("Derivative-term span is " + roundOne(derivativeSpan) + "; inspect signal noise and D filtering before future D recommendations.");
        }
        if (finite(iMin) && finite(iMax)
                && (iMin <= settings.getCorrectionMinimum() + settings.getCorrectionLimitTolerance()
                || iMax >= settings.getCorrectionMaximum() - settings.getCorrectionLimitTolerance())) {
            notes.add("The reported I term reaches a configured integral clamp during this window.");
        }

        String quality;
        if (overviewOnly) {
            quality = "Overview only";
        } else if (steadyState) {
            if (evaluationDuration < settings.getMinimumSteadyHoldSeconds()) {
                quality = "Rejected";
                notes.add("The steady interval is shorter than " + roundOne(settings.getMinimumSteadyHoldSeconds()) + " s.");
            } else if (engineStopsSoonAfter || targetChangedUnexpectedly || severeRpmExcursion) {
                quality = "Rejected";
            } else if (correctionSaturated
                    || (finite(meanError) && meanError > settings.getUsableSteadyMeanAbsoluteErrorRpm())
                    || (finite(rpmStandardDeviation) && rpmStandardDeviation > settings.getUsableSteadyRpmStandardDeviation())
                    || (finite(targetDrift) && targetDrift > settings.getMaximumSteadyTargetDriftRpm())) {
                quality = "Poor";
            } else if (evaluationDuration >= settings.getGoodSteadyDurationSeconds()
                    && finite(meanError) && meanError <= settings.getGoodSteadyMeanAbsoluteErrorRpm()
                    && finite(rpmStandardDeviation) && rpmStandardDeviation <= settings.getGoodSteadyRpmStandardDeviation()) {
                quality = "Good for analysis";
            } else {
                quality = "Usable";
            }
        } else if (evaluationDuration < 4.0) {
            quality = "Rejected";
            notes.add("Less than 4 s of post-trigger data is available.");
        } else if (engineStopsSoonAfter || correctionSaturated || targetChangedUnexpectedly) {
            quality = "Rejected";
        } else if (severeRpmExcursion
                || (finite(meanError) && meanError > 150.0)
                || overshoot > 300.0
                || undershoot > 300.0
                || (settlingApplicable && Double.isNaN(settling) && evaluationDuration >= 10.0)) {
            quality = "Poor";
        } else if (evaluationDuration >= 10.0 && (!settlingApplicable || finite(settling))) {
            quality = "Good for analysis";
        } else {
            quality = "Usable";
        }

        return new IdleEvent(
                type,
                analysisUse,
                start,
                trigger,
                end,
                time[start],
                time[trigger],
                time[end],
                median(targetValues),
                targetDrift,
                averageRpm,
                median(coolantValues),
                normalizedMinimum(coolantMin),
                normalizedMaximum(coolantMax),
                meanSignedError,
                meanError,
                rpmStandardDeviation,
                overshoot,
                undershoot,
                settling,
                settlingApplicable,
                normalizedMinimum(correctionMin),
                normalizedMaximum(correctionMax),
                correctionLimitPercent,
                correctionReversals,
                normalizedMinimum(commandedMin),
                normalizedMaximum(commandedMax),
                normalizedMinimum(baseMin),
                normalizedMaximum(baseMax),
                normalizedMinimum(pMin),
                normalizedMaximum(pMax),
                normalizedMinimum(iMin),
                normalizedMaximum(iMax),
                normalizedMinimum(dMin),
                normalizedMaximum(dMax),
                describeFan(fanMin, fanMax),
                quality,
                joinNotes(notes));
    }

    /**
     * Ends a response before a later, separate disturbance once the original response has
     * already settled. This prevents an unrelated RPM drop at the end of a broad closed-loop
     * region from contaminating the first response metrics.
     */
    private static int trimAfterSettledResponse(
            int trigger,
            int end,
            IdleLogData data,
            IdleAnalysisSettings settings) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
        if (time == null || rpm == null || target == null || end <= trigger) return end;

        int settledStart = findSettlingStart(
                time,
                rpm,
                target,
                trigger,
                end,
                settings.getResponseSettlingBandRpm(),
                settings.getSettlingRequiredSeconds());
        if (settledStart < 0) return end;

        double searchTime = time[settledStart]
                + settings.getSettlingRequiredSeconds()
                + settings.getPostSettlementGuardSeconds();
        int searchStart = lowerBound(time, Math.min(time[end], searchTime), settledStart, end);
        Disturbance next = findDisturbance(searchStart, end, data, settings);
        if (next == null) return end;

        int trimmed = Math.max(trigger, next.index - 1);
        return time[trimmed] - time[trigger] >= 4.0 ? trimmed : end;
    }

    private static int findSettlingStart(
            double[] time,
            double[] rpm,
            double[] target,
            int trigger,
            int end,
            double threshold,
            double requiredSeconds) {
        int runStart = -1;
        for (int i = trigger; i <= end; i++) {
            double rpmValue = value(rpm, i);
            double targetValue = value(target, i);
            boolean within = finite(rpmValue) && finite(targetValue)
                    && Math.abs(rpmValue - targetValue) <= threshold;
            if (within) {
                if (runStart < 0) runStart = i;
                if (time[i] - time[runStart] >= requiredSeconds) return runStart;
            } else {
                runStart = -1;
            }
        }
        return -1;
    }

    private static List<Segment> findSegments(boolean[] mask, double[] time, double minimumSeconds) {
        List<Segment> result = new ArrayList<Segment>();
        int start = -1;
        for (int i = 0; i < mask.length; i++) {
            if (mask[i] && start < 0) start = i;
            boolean ends = start >= 0 && (!mask[i] || i == mask.length - 1);
            if (ends) {
                int end = mask[i] && i == mask.length - 1 ? i : i - 1;
                if (end >= start && time[end] - time[start] >= minimumSeconds) {
                    StartReason reason = time[start] <= time[0] + 0.25
                            ? StartReason.LOG_START
                            : StartReason.CLOSED_LOOP_ENTRY;
                    result.add(new Segment(start, end, reason));
                }
                start = -1;
            }
        }
        return result;
    }

    private static void bridgeShortGaps(boolean[] mask, double[] time, double maximumGapSeconds) {
        int index = 0;
        while (index < mask.length) {
            while (index < mask.length && mask[index]) index++;
            int gapStart = index;
            while (index < mask.length && !mask[index]) index++;
            int gapEnd = index - 1;
            if (gapStart > 0 && index < mask.length && gapEnd >= gapStart
                    && time[gapEnd] - time[gapStart] <= maximumGapSeconds) {
                for (int i = gapStart; i <= gapEnd; i++) mask[i] = true;
            }
        }
    }

    private static double calculateSettlingTime(
            double[] time,
            double[] rpm,
            double[] target,
            int trigger,
            int end,
            double threshold,
            double requiredSeconds) {
        int runStart = -1;
        for (int i = trigger; i <= end; i++) {
            double rpmValue = value(rpm, i);
            double targetValue = value(target, i);
            boolean within = finite(rpmValue) && finite(targetValue) && Math.abs(rpmValue - targetValue) <= threshold;
            if (within) {
                if (runStart < 0) runStart = i;
                if (time[i] - time[runStart] >= requiredSeconds) {
                    return Math.max(0.0, time[runStart] - time[trigger]);
                }
            } else {
                runStart = -1;
            }
        }
        return Double.NaN;
    }

    private static double calculateCorrectionReversalsPerSecond(
            double[] time,
            double[] correction,
            int start,
            int end,
            double bucketSeconds,
            double directionDeadband) {
        if (time == null || correction == null || start < 0 || end <= start) return Double.NaN;
        List<Double> averages = new ArrayList<Double>();
        double startTime = time[start];
        int currentBucket = Integer.MIN_VALUE;
        double sum = 0.0;
        int count = 0;
        for (int i = start; i <= end && i < correction.length; i++) {
            if (!finite(time[i]) || !finite(correction[i])) continue;
            int bucket = (int) Math.floor((time[i] - startTime) / Math.max(0.001, bucketSeconds));
            if (currentBucket != Integer.MIN_VALUE && bucket != currentBucket) {
                if (count > 0) averages.add(sum / count);
                sum = correction[i];
                count = 1;
                currentBucket = bucket;
            } else {
                if (currentBucket == Integer.MIN_VALUE) currentBucket = bucket;
                sum += correction[i];
                count++;
            }
        }
        if (count > 0) averages.add(sum / count);
        if (averages.size() < 3) return Double.NaN;

        int previousDirection = 0;
        int reversals = 0;
        for (int i = 1; i < averages.size(); i++) {
            double change = averages.get(i) - averages.get(i - 1);
            int direction = change > directionDeadband ? 1 : (change < -directionDeadband ? -1 : 0);
            if (direction != 0 && previousDirection != 0 && direction != previousDirection) reversals++;
            if (direction != 0) previousDirection = direction;
        }
        double duration = time[end] - time[start];
        return duration > 0.0 ? reversals / duration : Double.NaN;
    }

    private static boolean engineStopsSoonAfter(IdleLogData data, int end, double lookAheadSeconds) {
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] rpm = data.getSeries(LogChannelDefinition.RPM);
        if (time == null || rpm == null || end < 0 || end >= time.length) return false;
        double limit = time[end] + lookAheadSeconds;
        for (int i = end + 1; i < time.length && time[i] <= limit; i++) {
            if (finite(rpm[i]) && rpm[i] <= 100.0) return true;
        }
        return false;
    }

    private static boolean touchesCorrectionLimit(
            double[] correction,
            boolean[] mask,
            IdleAnalysisSettings settings) {
        for (int i = 0; i < correction.length && i < mask.length; i++) {
            if (!mask[i] || !finite(correction[i])) continue;
            if (correction[i] <= settings.getCorrectionMinimum() + settings.getCorrectionLimitTolerance()
                    || correction[i] >= settings.getCorrectionMaximum() - settings.getCorrectionLimitTolerance()) return true;
        }
        return false;
    }

    private static int countDuplicateOrReverseTimes(double[] time) {
        int count = 0;
        for (int i = 1; i < time.length; i++) {
            if (!finite(time[i]) || !finite(time[i - 1]) || time[i] <= time[i - 1]) count++;
        }
        return count;
    }

    private static double finiteRange(double[] values) {
        return finiteRange(values, 0, values == null ? -1 : values.length - 1);
    }

    private static double finiteRange(double[] values, int start, int end) {
        if (values == null || end < start) return Double.NaN;
        double min = Double.POSITIVE_INFINITY;
        double max = Double.NEGATIVE_INFINITY;
        for (int i = Math.max(0, start); i <= end && i < values.length; i++) {
            if (!finite(values[i])) continue;
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        return min == Double.POSITIVE_INFINITY ? Double.NaN : max - min;
    }

    private static int lowerBound(double[] values, double target, int start, int end) {
        int low = Math.max(0, start);
        int high = Math.min(values.length - 1, end);
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (values[middle] < target) low = middle + 1;
            else high = middle;
        }
        return low;
    }

    private static String describeSegmentType(StartReason reason) {
        switch (reason) {
            case CLOSED_LOOP_ENTRY: return "Return to idle";
            case FAN_ON: return "Closed-loop after fan on";
            case FAN_OFF: return "Closed-loop after fan off";
            case TARGET_STEP: return "Closed-loop after target step";
            case LOG_START:
            default: return "Closed-loop idle region";
        }
    }

    private static String describeFan(double minimum, double maximum) {
        if (minimum == Double.POSITIVE_INFINITY) return "Unavailable";
        if (maximum <= 0.5) return "Off";
        if (minimum > 0.5) return "On";
        return "Changed";
    }

    private static String joinNotes(List<String> notes) {
        if (notes.isEmpty()) return "";
        StringBuilder text = new StringBuilder();
        for (String note : notes) {
            if (text.length() > 0) text.append(' ');
            text.append(note);
        }
        return text.toString();
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        Collections.sort(values);
        int middle = values.size() / 2;
        if ((values.size() & 1) == 0) return (values.get(middle - 1) + values.get(middle)) * 0.5;
        return values.get(middle);
    }

    private static double[] required(IdleLogData data, LogChannelDefinition definition) {
        double[] values = data.getSeries(definition);
        if (values == null) throw new IllegalArgumentException("Required log channel is missing: " + definition.getDisplayName());
        return values;
    }

    private static double value(double[] values, int index) {
        return values == null || index < 0 || index >= values.length ? Double.NaN : values[index];
    }

    private static double minimum(double current, double candidate) {
        return finite(candidate) ? Math.min(current, candidate) : current;
    }

    private static double maximum(double current, double candidate) {
        return finite(candidate) ? Math.max(current, candidate) : current;
    }

    private static double normalizedMinimum(double value) {
        return value == Double.POSITIVE_INFINITY ? Double.NaN : value;
    }

    private static double normalizedMaximum(double value) {
        return value == Double.NEGATIVE_INFINITY ? Double.NaN : value;
    }

    private static double rangeOrNaN(double minimum, double maximum) {
        return finite(minimum) && finite(maximum) ? maximum - minimum : Double.NaN;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String roundOne(double value) {
        return String.valueOf(Math.round(value * 10.0) / 10.0);
    }

    private enum StartReason {
        LOG_START,
        CLOSED_LOOP_ENTRY,
        FAN_ON,
        FAN_OFF,
        TARGET_STEP
    }

    private static final class Segment {
        private final int start;
        private final int end;
        private final StartReason reason;

        private Segment(int start, int end, StartReason reason) {
            this.start = start;
            this.end = end;
            this.reason = reason;
        }
    }

    private static final class Disturbance {
        private final int index;
        private final int direction;

        private Disturbance(int index, int direction) {
            this.index = index;
            this.direction = direction;
        }
    }
}
