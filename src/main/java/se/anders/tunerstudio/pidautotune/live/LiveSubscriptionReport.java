package se.anders.tunerstudio.pidautotune.live;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Result of resolving and subscribing the requested live output channels. */
public final class LiveSubscriptionReport {
    private final List<String> subscribed;
    private final List<String> missingRequired;
    private final List<String> missingOptional;

    public LiveSubscriptionReport(List<String> subscribed, List<String> missingRequired, List<String> missingOptional) {
        this.subscribed = Collections.unmodifiableList(new ArrayList<String>(subscribed));
        this.missingRequired = Collections.unmodifiableList(new ArrayList<String>(missingRequired));
        this.missingOptional = Collections.unmodifiableList(new ArrayList<String>(missingOptional));
    }

    public List<String> getSubscribed() { return subscribed; }
    public List<String> getMissingRequired() { return missingRequired; }
    public List<String> getMissingOptional() { return missingOptional; }
    public boolean isUsable() { return missingRequired.isEmpty(); }
}
