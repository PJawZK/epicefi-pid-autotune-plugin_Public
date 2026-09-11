package se.anders.tunerstudio.pidautotune.ui;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveRecommendationRefreshTrackerTest {
    @Test
    public void unchangedAttemptCountDoesNotRefreshAfterMark() {
        LiveRecommendationRefreshTracker tracker = new LiveRecommendationRefreshTracker();
        assertTrue(tracker.shouldRefresh(0));
        tracker.markRefreshed(0);
        assertFalse(tracker.shouldRefresh(0));
    }

    @Test
    public void finalizedAttemptForcesImmediateRefresh() {
        LiveRecommendationRefreshTracker tracker = new LiveRecommendationRefreshTracker();
        tracker.markRefreshed(2);
        assertTrue(tracker.shouldRefresh(3));
        tracker.markRefreshed(3);
        assertFalse(tracker.shouldRefresh(3));
    }

    @Test
    public void resetToLowerAttemptCountForcesRefresh() {
        LiveRecommendationRefreshTracker tracker = new LiveRecommendationRefreshTracker();
        tracker.markRefreshed(4);
        assertTrue(tracker.shouldRefresh(0));
    }

    @Test
    public void explicitInvalidationForcesRefreshWithoutAttemptChange() {
        LiveRecommendationRefreshTracker tracker = new LiveRecommendationRefreshTracker();
        tracker.markRefreshed(2);
        tracker.invalidate();
        assertTrue(tracker.shouldRefresh(2));
    }
}
