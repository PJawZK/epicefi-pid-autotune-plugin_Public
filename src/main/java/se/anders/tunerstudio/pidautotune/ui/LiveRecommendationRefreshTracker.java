package se.anders.tunerstudio.pidautotune.ui;

/**
 * Tracks the finalized-attempt revision used by the live recommendation UI.
 * Intermediate live samples do not change recommendation evidence until an attempt is appended.
 */
final class LiveRecommendationRefreshTracker {
    private int lastRefreshedAttemptCount = -1;

    boolean shouldRefresh(int currentAttemptCount) {
        return currentAttemptCount != lastRefreshedAttemptCount;
    }

    void markRefreshed(int currentAttemptCount) {
        lastRefreshedAttemptCount = currentAttemptCount;
    }

    void invalidate() {
        lastRefreshedAttemptCount = -1;
    }

    int getLastRefreshedAttemptCount() {
        return lastRefreshedAttemptCount;
    }
}
