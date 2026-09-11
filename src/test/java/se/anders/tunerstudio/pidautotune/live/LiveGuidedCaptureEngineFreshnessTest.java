package se.anders.tunerstudio.pidautotune.live;

import org.junit.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public final class LiveGuidedCaptureEngineFreshnessTest {
    @Test
    public void staleRequiredTelemetryRejectsActiveAttempt() {
        LiveGuidedCaptureEngine engine = readyEngine(LiveMotionMode.USE_LIVE_VSS, false);
        LiveCaptureSettings settings = settings(LiveMotionMode.USE_LIVE_VSS, false);

        process(engine, sample(7.0, 7L, 1600.0, 2.0, true, null, 7L), settings);
        assertEquals(LiveCaptureState.REV_IN_PROGRESS, engine.getState());

        process(engine, sample(10.0, 10L, 1600.0, 2.0, true, LiveChannel.TPS, 7L), settings);

        assertEquals(LiveCaptureState.WAITING_FOR_STABLE_IDLE, engine.getState());
        assertEquals(1, engine.getRejectedCount());
        assertEquals("REQUIRED_TELEMETRY_MISSING_OR_STALE", engine.getAttempts().get(0).getResultCode());
    }

    @Test
    public void staleLiveVssRejectsActiveAttempt() {
        LiveGuidedCaptureEngine engine = readyEngine(LiveMotionMode.USE_LIVE_VSS, false);
        LiveCaptureSettings settings = settings(LiveMotionMode.USE_LIVE_VSS, false);

        process(engine, sample(7.0, 7L, 1600.0, 2.0, true, null, 7L), settings);
        assertEquals(LiveCaptureState.REV_IN_PROGRESS, engine.getState());

        process(engine, sample(10.0, 10L, 1600.0, 2.0, true, LiveChannel.VEHICLE_SPEED, 7L), settings);

        assertEquals(LiveCaptureState.WAITING_FOR_STABLE_IDLE, engine.getState());
        assertEquals(1, engine.getRejectedCount());
        assertEquals("VSS_MISSING_OR_STALE", engine.getAttempts().get(0).getResultCode());
    }

    @Test
    public void manualStationaryModeDoesNotRequireVssFreshness() {
        LiveGuidedCaptureEngine engine = readyEngine(LiveMotionMode.MANUAL_STATIONARY, true);
        LiveCaptureSettings settings = settings(LiveMotionMode.MANUAL_STATIONARY, true);

        process(engine, sample(7.0, 7L, 1600.0, 2.0, false, null, 7L), settings);
        assertEquals(LiveCaptureState.REV_IN_PROGRESS, engine.getState());

        process(engine, sample(10.0, 10L, 1600.0, 2.0, false, null, 10L), settings);

        assertEquals(LiveCaptureState.REV_IN_PROGRESS, engine.getState());
        assertEquals(0, engine.getRejectedCount());
    }

    @Test
    public void staleReadyDataCannotStartAttemptFromCachedValues() {
        LiveGuidedCaptureEngine engine = readyEngine(LiveMotionMode.USE_LIVE_VSS, false);
        LiveCaptureSettings settings = settings(LiveMotionMode.USE_LIVE_VSS, false);

        process(engine, sample(9.0, 9L, 1600.0, 2.0, true, LiveChannel.TPS, 6L), settings);

        assertEquals(LiveCaptureState.WAITING_FOR_STABLE_IDLE, engine.getState());
        assertEquals(0, engine.getAttempts().size());
        assertEquals("READY_CANCELLED", engine.getEventMarker());
        assertEquals("REQUIRED_TELEMETRY_MISSING_OR_STALE", engine.getResultCode());
    }

    private static LiveGuidedCaptureEngine readyEngine(LiveMotionMode motionMode, boolean manuallyStationary) {
        LiveGuidedCaptureEngine engine = new LiveGuidedCaptureEngine();
        LiveCaptureSettings settings = settings(motionMode, manuallyStationary);
        engine.start();
        for (long second = 0L; second <= 6L; second++) {
            process(engine, sample((double) second, second, 800.0, 0.0,
                    motionMode == LiveMotionMode.USE_LIVE_VSS, null, second), settings);
        }
        assertEquals(LiveCaptureState.READY_TO_REV, engine.getState());
        assertEquals(0, engine.getAttempts().size());
        return engine;
    }

    private static void process(LiveGuidedCaptureEngine engine, LiveSample sample, LiveCaptureSettings settings) {
        engine.process(sample, settings, nanos(sample.getTimeSeconds()));
    }

    private static LiveSample sample(double timeSeconds,
                                     long freshSecond,
                                     double rpm,
                                     double tps,
                                     boolean includeVss,
                                     LiveChannel staleChannel,
                                     long staleSecond) {
        Map<LiveChannel, Double> values = new EnumMap<LiveChannel, Double>(LiveChannel.class);
        values.put(LiveChannel.RPM, rpm);
        values.put(LiveChannel.IDLE_TARGET, 800.0);
        values.put(LiveChannel.CURRENT_IDLE_POSITION, 25.0);
        values.put(LiveChannel.CLOSED_LOOP_ACTIVE, 1.0);
        values.put(LiveChannel.TPS, tps);
        values.put(LiveChannel.CLT, 80.0);
        values.put(LiveChannel.FAN1, 0.0);
        if (includeVss) values.put(LiveChannel.VEHICLE_SPEED, 0.0);

        Map<LiveChannel, Long> updates = new EnumMap<LiveChannel, Long>(LiveChannel.class);
        for (LiveChannel channel : values.keySet()) {
            long updateSecond = channel == staleChannel ? staleSecond : freshSecond;
            updates.put(channel, Long.valueOf(nanos(updateSecond)));
        }
        return new LiveSample(timeSeconds, values, updates);
    }

    private static LiveCaptureSettings settings(LiveMotionMode motionMode, boolean manuallyStationary) {
        return new LiveCaptureSettings(
                1.0,
                1.0,
                300.0,
                50.0,
                -20.0,
                20.0,
                70.0,
                100.0,
                1400.0,
                2200.0,
                100.0,
                60.0,
                20.0,
                10.0,
                30.0,
                LiveCaptureProfile.STANDARD,
                motionMode,
                manuallyStationary);
    }

    private static long nanos(double seconds) {
        return (long) (seconds * 1000000000.0);
    }
}
