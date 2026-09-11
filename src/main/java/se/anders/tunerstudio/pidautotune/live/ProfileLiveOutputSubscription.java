package se.anders.tunerstudio.pidautotune.live;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.OutputChannelClient;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generic read-only subscription for one controller profile.
 *
 * Unlike the existing idle guided-capture subscription, this class is deliberately
 * profile-driven and has no controller-specific event or recommendation semantics.
 */
public final class ProfileLiveOutputSubscription implements OutputChannelClient {
    private final OutputChannelServer outputServer;
    private final Object lock = new Object();
    private final Map<String, Double> values = new HashMap<String, Double>();
    private final Map<String, Long> updateNanos = new HashMap<String, Long>();
    private final Set<String> subscribedNames = new HashSet<String>();
    private boolean started;
    private long startNanos;

    public ProfileLiveOutputSubscription(ControllerAccess controllerAccess) {
        if (controllerAccess == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        outputServer = controllerAccess.getOutputChannelServer();
    }

    public LiveSubscriptionReport start(
            String configurationName,
            ControllerProfileDefinition profile) throws Exception {
        stop();
        if (configurationName == null || configurationName.trim().isEmpty()) {
            throw new IllegalArgumentException("configurationName cannot be empty");
        }
        if (profile == null) throw new IllegalArgumentException("profile cannot be null");

        String[] availableArray = outputServer.getOutputChannels(configurationName);
        Set<String> available = availableArray == null
                ? Collections.<String>emptySet()
                : new HashSet<String>(Arrays.asList(availableArray));
        List<String> subscribed = new ArrayList<String>();
        List<String> missingRequired = new ArrayList<String>();
        List<String> missingOptional = new ArrayList<String>();

        synchronized (lock) {
            values.clear();
            updateNanos.clear();
            subscribedNames.clear();
            startNanos = System.nanoTime();
        }

        started = true;
        for (ControllerProfileDefinition.Mapping mapping : profile.getOutputChannels()) {
            String name = mapping.getName();
            if (!available.contains(name)) {
                (mapping.isRequired() ? missingRequired : missingOptional).add(name);
                continue;
            }
            outputServer.subscribe(configurationName, name, this);
            synchronized (lock) {
                subscribedNames.add(name);
            }
            subscribed.add(name);
        }
        return new LiveSubscriptionReport(subscribed, missingRequired, missingOptional);
    }

    public void stop() {
        if (started) {
            try {
                outputServer.unsubscribe(this);
            } catch (RuntimeException ignored) {
                // TunerStudio may already have removed plugin clients during shutdown.
            }
        }
        started = false;
        synchronized (lock) {
            values.clear();
            updateNanos.clear();
            subscribedNames.clear();
        }
    }

    public boolean isStarted() { return started; }

    public ProfileLiveSample snapshot() {
        long now = System.nanoTime();
        synchronized (lock) {
            double time = startNanos == 0L ? 0.0 : (now - startNanos) / 1000000000.0;
            return new ProfileLiveSample(time, now, values, updateNanos);
        }
    }

    public boolean isSubscribed(String name) {
        synchronized (lock) {
            return subscribedNames.contains(name);
        }
    }

    @Override
    public void setCurrentOutputChannelValue(String outputChannelName, double rawValue) {
        long now = System.nanoTime();
        synchronized (lock) {
            if (!subscribedNames.contains(outputChannelName)) return;
            values.put(outputChannelName, rawValue);
            updateNanos.put(outputChannelName, now);
        }
    }
}
