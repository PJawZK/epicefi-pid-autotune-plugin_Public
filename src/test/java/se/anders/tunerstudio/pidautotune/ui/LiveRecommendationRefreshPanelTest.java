package se.anders.tunerstudio.pidautotune.ui;

import org.junit.Test;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveSample;

import javax.swing.SwingUtilities;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

public class LiveRecommendationRefreshPanelTest {
    @Test
    public void repeatedUiRefreshWithoutFinalizedAttemptReusesRecommendation() throws Exception {
        final AtomicReference<LiveExperimentalRecommendation> first = new AtomicReference<LiveExperimentalRecommendation>();
        final AtomicReference<LiveExperimentalRecommendation> second = new AtomicReference<LiveExperimentalRecommendation>();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    LiveCapturePanel panel = new LiveCapturePanel(null);
                    invokeUpdateUi(panel);
                    first.set(recommendation(panel));
                    invokeUpdateUi(panel);
                    second.set(recommendation(panel));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        assertSame("unchanged finalized-attempt evidence must not be recomputed", first.get(), second.get());
    }

    @Test
    public void resetInvalidationRefreshesEvenWhenAttemptCountStaysZero() throws Exception {
        final AtomicReference<LiveExperimentalRecommendation> before = new AtomicReference<LiveExperimentalRecommendation>();
        final AtomicReference<LiveExperimentalRecommendation> after = new AtomicReference<LiveExperimentalRecommendation>();

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                try {
                    LiveCapturePanel panel = new LiveCapturePanel(null);
                    invokeUpdateUi(panel);
                    before.set(recommendation(panel));
                    Method reset = LiveCapturePanel.class.getDeclaredMethod("resetLiveSession", boolean.class);
                    reset.setAccessible(true);
                    reset.invoke(panel, Boolean.FALSE);
                    after.set(recommendation(panel));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            }
        });

        assertNotSame("explicit reset invalidation must recompute recommendation state", before.get(), after.get());
    }

    private static void invokeUpdateUi(LiveCapturePanel panel) throws Exception {
        Method update = LiveCapturePanel.class.getDeclaredMethod("updateUi", LiveSample.class);
        update.setAccessible(true);
        update.invoke(panel, new Object[] { null });
    }

    private static LiveExperimentalRecommendation recommendation(LiveCapturePanel panel) throws Exception {
        Field field = LiveCapturePanel.class.getDeclaredField("latestLiveRecommendation");
        field.setAccessible(true);
        return (LiveExperimentalRecommendation) field.get(panel);
    }
}
