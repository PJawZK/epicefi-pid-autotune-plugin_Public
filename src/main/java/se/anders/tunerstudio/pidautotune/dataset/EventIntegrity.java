package se.anders.tunerstudio.pidautotune.dataset;

import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisSettings;
import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.LogChannelDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Per-event movement, idle-entry, and vehicle-speed integrity diagnostics. */
public final class EventIntegrity {
    private final String motionClass;
    private final String entryCause;
    private final String vssBasis;
    private final String vssStatus;
    private final double vssMinimum;
    private final double vssMedian;
    private final double vssMaximum;
    private final int dropoutCount;
    private final int discontinuityCount;
    private final boolean unstableVss;
    private final boolean recommendationBlocked;
    private final String details;

    private EventIntegrity(
            String motionClass,
            String entryCause,
            String vssBasis,
            String vssStatus,
            double vssMinimum,
            double vssMedian,
            double vssMaximum,
            int dropoutCount,
            int discontinuityCount,
            boolean unstableVss,
            boolean recommendationBlocked,
            String details) {
        this.motionClass = motionClass;
        this.entryCause = entryCause;
        this.vssBasis = vssBasis;
        this.vssStatus = vssStatus;
        this.vssMinimum = vssMinimum;
        this.vssMedian = vssMedian;
        this.vssMaximum = vssMaximum;
        this.dropoutCount = dropoutCount;
        this.discontinuityCount = discontinuityCount;
        this.unstableVss = unstableVss;
        this.recommendationBlocked = recommendationBlocked;
        this.details = details;
    }

    public static EventIntegrity assess(AnalyzedLog analyzedLog, IdleEvent event) {
        IdleLogData data = analyzedLog.getData();
        IdleAnalysisSettings settings = analyzedLog.getSettings();
        VssSourceMode mode = analyzedLog.getVssSourceMode();
        boolean gateRelevant = event.isFutureGainEligible() || event.isSteadyHold();
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        double[] speed = data.getSeries(LogChannelDefinition.VEHICLE_SPEED);

        String entryCause = detectEntryCause(data, event, settings, mode.usesLoggedVss());

        if (mode.isManuallyConfirmedStationary()) {
            return new EventIntegrity(
                    "Stationary — manually confirmed",
                    entryCause,
                    mode.getDisplayName(),
                    "Unavailable; stationary confirmed",
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0,
                    false,
                    false,
                    "The logged VSS channel is ignored for this complete log. The operator explicitly confirmed that the vehicle remained stationary. This manual confirmation may satisfy the motion-integrity gate, but it is kept visible in grouping and reports.");
        }

        if (mode.isMotionUnknown()) {
            return new EventIntegrity(
                    "Motion unknown",
                    entryCause,
                    mode.getDisplayName(),
                    "Unavailable; motion unknown",
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0,
                    true,
                    gateRelevant,
                    "The logged VSS channel is ignored and vehicle motion was not confirmed. Diagnostic use remains available, but transient and steady events cannot satisfy recommendation readiness.");
        }

        if (time == null || speed == null || speed.length == 0) {
            String status = mode == VssSourceMode.VERIFIED_OPERATIONAL
                    ? "Verified setting conflicts with missing channel"
                    : "Unavailable; select a VSS source";
            return new EventIntegrity(
                    "Motion unknown",
                    entryCause,
                    mode.getDisplayName(),
                    status,
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0,
                    true,
                    gateRelevant,
                    "Vehicle speed is not present, so motion and VSS continuity cannot be verified. Select a manual VSS source for this log or restore a valid VSS channel.");
        }

        SpeedSummary wholeLog = summarize(speed, 0, speed.length - 1);
        if (!wholeLog.hasValues) {
            return new EventIntegrity(
                    "Motion unknown",
                    entryCause,
                    mode.getDisplayName(),
                    "No finite VSS samples",
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0,
                    true,
                    gateRelevant,
                    "Vehicle-speed samples are present but none are finite. Select a manual VSS source for this log or restore a valid VSS channel.");
        }

        if (mode == VssSourceMode.AUTOMATIC && wholeLog.maximum - wholeLog.minimum <= 0.25) {
            boolean approximatelyZero = Math.abs(wholeLog.median) <= 0.25 && wholeLog.maximum <= 0.50;
            String status = approximatelyZero ? "Unverified constant zero" : "Unverified constant value";
            String motion = approximatelyZero ? "Stationary — unverified" : "Motion unknown";
            return new EventIntegrity(
                    motion,
                    entryCause,
                    mode.getDisplayName(),
                    status,
                    wholeLog.minimum,
                    wholeLog.median,
                    wholeLog.maximum,
                    0,
                    0,
                    true,
                    gateRelevant,
                    "Automatic detection found a nearly constant VSS channel across the complete log ("
                            + roundOne(wholeLog.minimum) + " to " + roundOne(wholeLog.maximum)
                            + " km/h). A constant zero cannot prove that the sensor was operational or that the vehicle was stationary. Select VSS verified operational, manually confirmed stationary, or motion unknown for this log.");
        }

        int maximumIndex = Math.min(time.length, speed.length) - 1;
        int start = clamp(event.getStartIndex(), 0, maximumIndex);
        int end = clamp(event.getEndIndex(), start, maximumIndex);
        SpeedSummary eventSpeeds = summarize(speed, start, end);
        if (!eventSpeeds.hasValues) {
            return new EventIntegrity(
                    "Motion unknown",
                    entryCause,
                    mode.getDisplayName(),
                    "No finite event VSS samples",
                    Double.NaN,
                    Double.NaN,
                    Double.NaN,
                    0,
                    0,
                    true,
                    gateRelevant,
                    "The selected event contains no finite vehicle-speed samples and cannot satisfy the motion-integrity gate.");
        }

        String motionClass;
        if (eventSpeeds.maximum <= 0.75) motionClass = "Stationary";
        else if (eventSpeeds.median <= 1.0 && eventSpeeds.maximum <= 3.0) motionClass = "Near-stationary";
        else if (eventSpeeds.maximum <= settings.getMaxVehicleSpeed() + 0.25) motionClass = "Moving below idle limit";
        else motionClass = "Moving above idle limit";

        int dropouts = countShortZeroDropouts(time, speed, start, end);
        int discontinuities = countAbruptDiscontinuities(time, speed, start, end);
        boolean unstable = dropouts > 0 || discontinuities >= 2;
        boolean blocked = unstable && gateRelevant;

        String status;
        if (unstable) {
            status = "Unstable";
        } else if ("Stationary".equals(motionClass) || "Near-stationary".equals(motionClass)) {
            status = "Stable stationary";
        } else {
            status = "Stable moving";
        }

        StringBuilder details = new StringBuilder();
        details.append("VSS source: ").append(mode.getDisplayName()).append(". ")
                .append("Event VSS range ").append(roundOne(eventSpeeds.minimum)).append(" to ")
                .append(roundOne(eventSpeeds.maximum)).append(" km/h; median ")
                .append(roundOne(eventSpeeds.median)).append(" km/h. ");
        if (dropouts > 0) {
            details.append(dropouts).append(" short zero-value dropout")
                    .append(dropouts == 1 ? "" : "s").append(" detected. ");
        }
        if (discontinuities > 0) {
            details.append(discontinuities).append(" abrupt VSS transition")
                    .append(discontinuities == 1 ? "" : "s").append(" detected. ");
        }
        if (!unstable) details.append("No event-level VSS continuity fault was detected. ");
        if (blocked) details.append("This event is blocked from satisfying the recommendation-readiness gate.");

        return new EventIntegrity(
                motionClass,
                entryCause,
                mode.getDisplayName(),
                status,
                eventSpeeds.minimum,
                eventSpeeds.median,
                eventSpeeds.maximum,
                dropouts,
                discontinuities,
                unstable,
                blocked,
                details.toString().trim());
    }

    public String getMotionClass() { return motionClass; }
    public String getEntryCause() { return entryCause; }
    public String getVssBasis() { return vssBasis; }
    public String getVssStatus() { return vssStatus; }
    public double getVssMinimum() { return vssMinimum; }
    public double getVssMedian() { return vssMedian; }
    public double getVssMaximum() { return vssMaximum; }
    public int getDropoutCount() { return dropoutCount; }
    public int getDiscontinuityCount() { return discontinuityCount; }
    public boolean isUnstableVss() { return unstableVss; }
    public boolean isRecommendationBlocked() { return recommendationBlocked; }
    public String getDetails() { return details; }

    private static String detectEntryCause(
            IdleLogData data,
            IdleEvent event,
            IdleAnalysisSettings settings,
            boolean useLoggedVss) {
        String type = event.getType();
        if (type != null && type.startsWith("Fan 1 switched")) return "Fan load step";
        if (type != null && type.startsWith("Low-RPM")) return "Low-RPM disturbance";
        if (type != null && type.startsWith("High-RPM")) return "High-RPM disturbance";
        if (event.isSteadyHold()) return "Steady hold after idle entry";
        if (type != null && type.startsWith("Target-ramp")) return "Return-target ramp";

        double[] time = data.getSeries(LogChannelDefinition.TIME);
        if (time == null || time.length == 0) return "Undetermined";
        int trigger = clamp(event.getTriggerIndex(), 0, time.length - 1);
        int before = findLookbackIndex(time, trigger, 1.0);

        if (useLoggedVss) {
            double[] speed = data.getSeries(LogChannelDefinition.VEHICLE_SPEED);
            if (crossedDown(speed, before, trigger, settings.getMaxVehicleSpeed(), 0.25)) {
                return "VSS crossed idle threshold";
            }
        }
        double[] tps = data.getSeries(LogChannelDefinition.TPS);
        if (crossedDown(tps, before, trigger, settings.getTpsThreshold(), 0.05)) {
            return "TPS closed below idle threshold";
        }
        double[] closedLoop = data.getSeries(LogChannelDefinition.CLOSED_LOOP_ACTIVE);
        if (rose(closedLoop, before, trigger, 0.5)) return "ECU closed-loop entry";
        double[] idling = data.getSeries(LogChannelDefinition.IDLING);
        if (rose(idling, before, trigger, 0.5)) return "ECU idling-state entry";
        if (type != null && type.startsWith("Return to idle")) return "Return-to-idle entry; cause undetermined";
        return "Already inside idle control";
    }

    private static SpeedSummary summarize(double[] speed, int start, int end) {
        List<Double> values = new ArrayList<Double>();
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        int boundedStart = Math.max(0, start);
        int boundedEnd = Math.min(speed.length - 1, end);
        for (int index = boundedStart; index <= boundedEnd; index++) {
            double value = speed[index];
            if (!finite(value)) continue;
            values.add(Double.valueOf(value));
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        if (values.isEmpty()) return new SpeedSummary(false, Double.NaN, Double.NaN, Double.NaN);
        return new SpeedSummary(true, minimum, median(values), maximum);
    }

    private static int countShortZeroDropouts(double[] time, double[] speed, int start, int end) {
        int count = 0;
        int index = start;
        while (index <= end) {
            if (!finite(speed[index]) || speed[index] > 0.25) {
                index++;
                continue;
            }
            int runStart = index;
            int runEnd = index;
            while (runEnd + 1 <= end && finite(speed[runEnd + 1]) && speed[runEnd + 1] <= 0.25) runEnd++;
            int previous = previousFinite(speed, runStart - 1, start);
            int next = nextFinite(speed, runEnd + 1, end);
            double duration = Math.max(0.0, time[runEnd] - time[runStart]);
            boolean bracketedByMotion = previous >= start && next <= end
                    && speed[previous] >= 2.0 && speed[next] >= 2.0;
            boolean shortRun = duration <= 1.0;
            boolean closeBrackets = bracketedByMotion
                    && time[runStart] - time[previous] <= 1.0
                    && time[next] - time[runEnd] <= 1.0;
            if (shortRun && closeBrackets) count++;
            index = runEnd + 1;
        }
        return count;
    }

    private static int countAbruptDiscontinuities(double[] time, double[] speed, int start, int end) {
        int count = 0;
        int previous = -1;
        for (int index = start; index <= end; index++) {
            if (!finite(speed[index])) continue;
            if (previous >= 0) {
                double dt = time[index] - time[previous];
                double delta = Math.abs(speed[index] - speed[previous]);
                double reference = Math.max(Math.abs(speed[index]), Math.abs(speed[previous]));
                double threshold = Math.max(5.0, reference * 0.60);
                if (dt > 0.0 && dt <= 0.25 && delta >= threshold) count++;
            }
            previous = index;
        }
        return count;
    }

    private static boolean crossedDown(double[] values, int start, int end, double threshold, double margin) {
        if (values == null || values.length == 0) return false;
        int boundedEnd = Math.min(end, values.length - 1);
        int boundedStart = Math.max(0, Math.min(start, boundedEnd));
        boolean above = false;
        for (int index = boundedStart; index <= boundedEnd; index++) {
            double value = values[index];
            if (!finite(value)) continue;
            if (value > threshold + margin) above = true;
            if (above && value <= threshold) return true;
        }
        return false;
    }

    private static boolean rose(double[] values, int start, int end, double threshold) {
        if (values == null || values.length == 0) return false;
        int boundedEnd = Math.min(end, values.length - 1);
        int boundedStart = Math.max(0, Math.min(start, boundedEnd));
        boolean below = false;
        for (int index = boundedStart; index <= boundedEnd; index++) {
            double value = values[index];
            if (!finite(value)) continue;
            if (value <= threshold) below = true;
            if (below && value > threshold) return true;
        }
        return false;
    }

    private static int findLookbackIndex(double[] time, int end, double seconds) {
        double target = time[end] - seconds;
        int index = end;
        while (index > 0 && time[index - 1] >= target) index--;
        return index;
    }

    private static int previousFinite(double[] values, int index, int minimum) {
        for (int i = index; i >= minimum; i--) if (finite(values[i])) return i;
        return minimum - 1;
    }

    private static int nextFinite(double[] values, int index, int maximum) {
        for (int i = index; i <= maximum; i++) if (finite(values[i])) return i;
        return maximum + 1;
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) return Double.NaN;
        Collections.sort(values);
        int middle = values.size() / 2;
        if ((values.size() & 1) == 1) return values.get(middle).doubleValue();
        return (values.get(middle - 1).doubleValue() + values.get(middle).doubleValue()) * 0.5;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String roundOne(double value) {
        if (!finite(value)) return "unavailable";
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static final class SpeedSummary {
        private final boolean hasValues;
        private final double minimum;
        private final double median;
        private final double maximum;

        private SpeedSummary(boolean hasValues, double minimum, double median, double maximum) {
            this.hasValues = hasValues;
            this.minimum = minimum;
            this.median = median;
            this.maximum = maximum;
        }
    }
}
