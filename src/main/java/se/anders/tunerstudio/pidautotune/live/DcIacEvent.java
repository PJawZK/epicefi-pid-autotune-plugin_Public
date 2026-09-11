package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable M2 DC-IAC event extracted from profile-driven live samples. */
public final class DcIacEvent {
    public enum Type {
        STABLE_HOLD,
        OPENING_STEP,
        CLOSING_STEP
    }

    public enum Status {
        ACCEPTED,
        REJECTED
    }

    private final int sequence;
    private final Type type;
    private final Status status;
    private final double startSeconds;
    private final double endSeconds;
    private final double fromTarget;
    private final double toTarget;
    private final String resultCode;
    private final String detail;
    private final List<ProfileLiveSample> samples;

    public DcIacEvent(
            int sequence,
            Type type,
            Status status,
            double startSeconds,
            double endSeconds,
            double fromTarget,
            double toTarget,
            String resultCode,
            String detail,
            List<ProfileLiveSample> samples) {
        this.sequence = sequence;
        this.type = type;
        this.status = status;
        this.startSeconds = startSeconds;
        this.endSeconds = endSeconds;
        this.fromTarget = fromTarget;
        this.toTarget = toTarget;
        this.resultCode = resultCode == null ? "" : resultCode;
        this.detail = detail == null ? "" : detail;
        this.samples = Collections.unmodifiableList(new ArrayList<ProfileLiveSample>(samples));
    }

    public int getSequence() { return sequence; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public double getStartSeconds() { return startSeconds; }
    public double getEndSeconds() { return endSeconds; }
    public double getDurationSeconds() { return Math.max(0.0, endSeconds - startSeconds); }
    public double getFromTarget() { return fromTarget; }
    public double getToTarget() { return toTarget; }
    public String getResultCode() { return resultCode; }
    public String getDetail() { return detail; }
    public List<ProfileLiveSample> getSamples() { return samples; }

    public boolean isAccepted() { return status == Status.ACCEPTED; }
}
