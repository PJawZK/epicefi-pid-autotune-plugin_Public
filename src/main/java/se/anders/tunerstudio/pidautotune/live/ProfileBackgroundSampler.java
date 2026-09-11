package se.anders.tunerstudio.pidautotune.live;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

/**
 * Background sampler for measurement stages.
 *
 * Sampling is deliberately detached from Swing's EDT so measurement timing does
 * not inherit UI rendering stalls. The underlying subscription remains read-only.
 */
public final class ProfileBackgroundSampler {
    public interface Listener {
        void onSample(ProfileLiveSample sample);
        void onError(Throwable error);
    }

    private final ProfileLiveOutputSubscription subscription;
    private final long periodMillis;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> future;
    private volatile boolean running;

    public ProfileBackgroundSampler(ProfileLiveOutputSubscription subscription, long periodMillis) {
        if (subscription == null) throw new IllegalArgumentException("subscription cannot be null");
        if (periodMillis < 5L) throw new IllegalArgumentException("periodMillis must be >= 5");
        this.subscription = subscription;
        this.periodMillis = periodMillis;
    }

    public synchronized void start(final Listener listener) {
        stop();
        if (listener == null) throw new IllegalArgumentException("listener cannot be null");
        running = true;
        executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "pid-autotune-profile-sampler");
                thread.setDaemon(true);
                return thread;
            }
        });
        future = executor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!running || !subscription.isStarted()) return;
                try {
                    listener.onSample(subscription.snapshot());
                } catch (Throwable error) {
                    listener.onError(error);
                }
            }
        }, 0L, periodMillis, TimeUnit.MILLISECONDS);
    }

    public synchronized void stop() {
        running = false;
        if (future != null) {
            future.cancel(true);
            future = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    public boolean isRunning() { return running; }
    public long getPeriodMillis() { return periodMillis; }
}
