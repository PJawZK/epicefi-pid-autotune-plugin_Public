package se.anders.tunerstudio.pidautotune.live;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.OutputChannelClient;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Thread-safe read-only subscription to the EPICEFI live channels used by guided capture. */
public final class LiveOutputSubscription implements OutputChannelClient {
    private final OutputChannelServer outputServer;
    private final Object lock = new Object();
    private final Map<String, LiveChannel> logicalByApiName = new HashMap<String, LiveChannel>();
    private final EnumMap<LiveChannel, Double> values = new EnumMap<LiveChannel, Double>(LiveChannel.class);
    private final EnumMap<LiveChannel, Long> updateNanos = new EnumMap<LiveChannel, Long>(LiveChannel.class);
    private boolean started;
    private long startNanos;

    public LiveOutputSubscription(ControllerAccess controllerAccess) {
        if (controllerAccess == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        this.outputServer = controllerAccess.getOutputChannelServer();
    }

    public LiveSubscriptionReport start(String configurationName) throws Exception {
        stop();
        if (configurationName == null || configurationName.trim().isEmpty()) {
            throw new IllegalArgumentException("configurationName cannot be empty");
        }

        String[] availableArray = outputServer.getOutputChannels(configurationName);
        Set<String> available = availableArray == null
                ? Collections.<String>emptySet()
                : new HashSet<String>(Arrays.asList(availableArray));
        List<String> subscribed = new ArrayList<String>();
        List<String> missingRequired = new ArrayList<String>();
        List<String> missingOptional = new ArrayList<String>();

        synchronized (lock) {
            logicalByApiName.clear();
            values.clear();
            updateNanos.clear();
            startNanos = System.nanoTime();
        }

        started = true;
        for (LiveChannel logical : LiveChannel.values()) {
            String selectedName = null;
            for (String candidate : logical.getApiNames()) {
                if (available.contains(candidate)) {
                    selectedName = candidate;
                    break;
                }
            }
            if (selectedName == null) {
                (logical.isRequired() ? missingRequired : missingOptional).add(logical.getDisplayName());
                continue;
            }
            outputServer.subscribe(configurationName, selectedName, this);
            synchronized (lock) {
                logicalByApiName.put(selectedName, logical);
            }
            subscribed.add(logical.getDisplayName() + " ← " + selectedName);
        }
        return new LiveSubscriptionReport(subscribed, missingRequired, missingOptional);
    }

    public void stop() {
        if (started) {
            try {
                outputServer.unsubscribe(this);
            } catch (RuntimeException ignored) {
                // TunerStudio also unsubscribes plugin clients during close; stopping remains best effort.
            }
        }
        started = false;
        synchronized (lock) {
            logicalByApiName.clear();
            values.clear();
            updateNanos.clear();
        }
    }

    public boolean isStarted() { return started; }

    public LiveSample snapshot() {
        long now = System.nanoTime();
        synchronized (lock) {
            double time = startNanos == 0L ? 0.0 : (now - startNanos) / 1000000000.0;
            return new LiveSample(time, values, updateNanos);
        }
    }

    @Override
    public void setCurrentOutputChannelValue(String outputChannelName, double rawValue) {
        long now = System.nanoTime();
        synchronized (lock) {
            LiveChannel logical = logicalByApiName.get(outputChannelName);
            if (logical != null) {
                values.put(logical, rawValue);
                updateNanos.put(logical, now);
            }
        }
    }
}
