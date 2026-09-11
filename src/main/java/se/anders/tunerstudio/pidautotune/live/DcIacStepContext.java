package se.anders.tunerstudio.pidautotune.live;

import java.util.List;

/** Extra integrated-tuner context for one accepted M2/M3 dynamic step. */
public final class DcIacStepContext {
    public enum Source { COMMAND, FAN1_IDLE_ADDER, UNKNOWN }

    private static final double TRAVEL_TOLERANCE_PERCENT = 0.50;

    private final Source source;
    private final boolean processValueOutsideTravel;
    private final double minimumObservedPosition;
    private final double maximumObservedPosition;

    private DcIacStepContext(Source source, boolean outside, double minimumObservedPosition, double maximumObservedPosition) {
        this.source = source == null ? Source.UNKNOWN : source;
        this.processValueOutsideTravel = outside;
        this.minimumObservedPosition = minimumObservedPosition;
        this.maximumObservedPosition = maximumObservedPosition;
    }

    public static DcIacStepContext analyze(List<ProfileLiveSample> samples, double minimumPosition, double maximumPosition) {
        boolean fanSeen = false;
        boolean fanOff = false;
        boolean fanOn = false;
        double minimumObserved = Double.POSITIVE_INFINITY;
        double maximumObserved = Double.NEGATIVE_INFINITY;

        if (samples != null) {
            for (ProfileLiveSample sample : samples) {
                if (sample == null) continue;
                double fan = sample.get("fan1On");
                if (finite(fan)) {
                    fanSeen = true;
                    if (fan > 0.5) fanOn = true;
                    else fanOff = true;
                }
                double actual = sample.get("idlePositionSensor");
                if (finite(actual)) {
                    minimumObserved = Math.min(minimumObserved, actual);
                    maximumObserved = Math.max(maximumObserved, actual);
                }
            }
        }

        Source source = !fanSeen ? Source.UNKNOWN : (fanOn && fanOff ? Source.FAN1_IDLE_ADDER : Source.COMMAND);
        boolean outside = false;
        if (finite(minimumPosition) && finite(maximumPosition) && maximumPosition > minimumPosition
                && finite(minimumObserved) && finite(maximumObserved)) {
            outside = minimumObserved < minimumPosition - TRAVEL_TOLERANCE_PERCENT
                    || maximumObserved > maximumPosition + TRAVEL_TOLERANCE_PERCENT;
        }

        return new DcIacStepContext(source, outside,
                finite(minimumObserved) ? minimumObserved : Double.NaN,
                finite(maximumObserved) ? maximumObserved : Double.NaN);
    }

    public Source getSource() { return source; }
    public boolean isFanIdleAdderStep() { return source == Source.FAN1_IDLE_ADDER; }
    public boolean isProcessValueOutsideTravel() { return processValueOutsideTravel; }
    public double getMinimumObservedPosition() { return minimumObservedPosition; }
    public double getMaximumObservedPosition() { return maximumObservedPosition; }

    public String evidenceFlags() {
        StringBuilder b = new StringBuilder();
        if (isFanIdleAdderStep()) b.append("FAN1_IDLE_ADDER_STEP");
        if (processValueOutsideTravel) {
            if (b.length() > 0) b.append(';');
            b.append("PV_OUTSIDE_TRAVEL");
        }
        return b.toString();
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
