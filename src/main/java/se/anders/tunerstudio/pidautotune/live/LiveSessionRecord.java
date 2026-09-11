package se.anders.tunerstudio.pidautotune.live;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

/** One exported live sample plus guided-capture and tuning-iteration metadata. */
public final class LiveSessionRecord {
    private final LiveSample sample;
    private final LiveCaptureState state;
    private final int attemptNumber;
    private final String eventMarker;
    private final String resultCode;
    private final LiveCaptureProfile profile;
    private final long wallClockMillis;
    private final int iterationNumber;
    private final TuneSnapshot gains;

    public LiveSessionRecord(LiveSample sample, LiveCaptureState state, int attemptNumber,
                             String eventMarker, String resultCode, LiveCaptureProfile profile) {
        this(sample, state, attemptNumber, eventMarker, resultCode, profile,
                System.currentTimeMillis(), 0, TuneSnapshot.unknown("Legacy live session record"));
    }

    public LiveSessionRecord(LiveSample sample, LiveCaptureState state, int attemptNumber,
                             String eventMarker, String resultCode, LiveCaptureProfile profile,
                             long wallClockMillis, int iterationNumber, TuneSnapshot gains) {
        this.sample = sample;
        this.state = state;
        this.attemptNumber = attemptNumber;
        this.eventMarker = safe(eventMarker);
        this.resultCode = safe(resultCode);
        this.profile = profile == null ? LiveCaptureProfile.CUSTOM : profile;
        this.wallClockMillis = wallClockMillis;
        this.iterationNumber = iterationNumber;
        this.gains = gains;
    }

    public LiveSample getSample() { return sample; }
    public LiveCaptureState getState() { return state; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getEventMarker() { return eventMarker; }
    public String getResultCode() { return resultCode; }
    public LiveCaptureProfile getProfile() { return profile; }
    public long getWallClockMillis() { return wallClockMillis; }
    public int getIterationNumber() { return iterationNumber; }
    public TuneSnapshot getGains() { return gains; }

    private static String safe(String value) { return value == null ? "" : value; }
}
