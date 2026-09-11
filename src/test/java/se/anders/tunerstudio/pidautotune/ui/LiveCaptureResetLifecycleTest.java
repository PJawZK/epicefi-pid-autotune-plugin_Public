package se.anders.tunerstudio.pidautotune.ui;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.live.LiveChannel;
import se.anders.tunerstudio.pidautotune.live.LiveIterationRecord;
import se.anders.tunerstudio.pidautotune.live.LiveSample;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LiveCaptureResetLifecycleTest {
    @Test
    public void activeResetArchivesCurrentStageAndStartsFreshUnarchivedIteration() throws Exception {
        LiveCapturePanel panel = new LiveCapturePanel(null);
        setInt(panel, "currentIterationNumber", 1);
        setLong(panel, "sessionStartedWallClockMillis", 1000L);
        setBoolean(panel, "currentIterationArchived", false);
        samples(panel).add(emptySample(0.1));

        invoke(panel, "resetLiveSession", new Class<?>[] { boolean.class }, Boolean.TRUE);

        List<LiveIterationRecord> history = history(panel);
        assertEquals(1, history.size());
        assertEquals(1, history.get(0).getIterationNumber());
        assertEquals(2, getInt(panel, "currentIterationNumber"));
        assertFalse(getBoolean(panel, "currentIterationArchived"));
        assertTrue(getLong(panel, "sessionStartedWallClockMillis") > 1000L);
        assertTrue(samples(panel).isEmpty());

        samples(panel).add(emptySample(0.2));
        invoke(panel, "archiveCurrentIteration", new Class<?>[] { String.class }, "Stopped after reset");

        history = history(panel);
        assertEquals(2, history.size());
        assertEquals(2, history.get(1).getIterationNumber());
        assertTrue(getBoolean(panel, "currentIterationArchived"));
    }

    @Test
    public void stoppedResetDoesNotCreateAnOpenIteration() throws Exception {
        LiveCapturePanel panel = new LiveCapturePanel(null);
        setInt(panel, "currentIterationNumber", 3);
        setLong(panel, "sessionStartedWallClockMillis", 1000L);
        setBoolean(panel, "currentIterationArchived", false);
        samples(panel).add(emptySample(0.1));

        invoke(panel, "resetLiveSession", new Class<?>[] { boolean.class }, Boolean.FALSE);

        assertEquals(1, history(panel).size());
        assertEquals(3, history(panel).get(0).getIterationNumber());
        assertTrue(getBoolean(panel, "currentIterationArchived"));
        assertTrue(samples(panel).isEmpty());

        samples(panel).add(emptySample(0.2));
        invoke(panel, "archiveCurrentIteration", new Class<?>[] { String.class }, "Should remain closed");
        assertEquals(1, history(panel).size());
    }

    @SuppressWarnings("unchecked")
    private static List<LiveSample> samples(LiveCapturePanel panel) throws Exception {
        return (List<LiveSample>) field("sessionSamples").get(panel);
    }

    @SuppressWarnings("unchecked")
    private static List<LiveIterationRecord> history(LiveCapturePanel panel) throws Exception {
        return (List<LiveIterationRecord>) field("tuningHistory").get(panel);
    }

    private static LiveSample emptySample(double timeSeconds) {
        Map<LiveChannel, Double> values = new EnumMap<LiveChannel, Double>(LiveChannel.class);
        Map<LiveChannel, Long> updates = new EnumMap<LiveChannel, Long>(LiveChannel.class);
        return new LiveSample(timeSeconds, values, updates);
    }

    private static void invoke(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = LiveCapturePanel.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        method.invoke(target, args);
    }

    private static Field field(String name) throws Exception {
        Field field = LiveCapturePanel.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void setInt(Object target, String name, int value) throws Exception {
        field(name).setInt(target, value);
    }

    private static int getInt(Object target, String name) throws Exception {
        return field(name).getInt(target);
    }

    private static void setLong(Object target, String name, long value) throws Exception {
        field(name).setLong(target, value);
    }

    private static long getLong(Object target, String name) throws Exception {
        return field(name).getLong(target);
    }

    private static void setBoolean(Object target, String name, boolean value) throws Exception {
        field(name).setBoolean(target, value);
    }

    private static boolean getBoolean(Object target, String name) throws Exception {
        return field(name).getBoolean(target);
    }
}
