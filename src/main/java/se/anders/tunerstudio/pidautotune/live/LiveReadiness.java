package se.anders.tunerstudio.pidautotune.live;

import java.util.LinkedHashMap;
import java.util.Map;

/** Current live-condition verdicts shown before and during guided capture. */
public final class LiveReadiness {
    private final LinkedHashMap<String, String> rows = new LinkedHashMap<String, String>();
    private boolean ready = true;

    public void add(String name, boolean passed, String details) {
        rows.put(name, (passed ? "Ready" : "Not ready") + (details == null || details.isEmpty() ? "" : " — " + details));
        if (!passed) ready = false;
    }

    public boolean isReady() { return ready; }
    public Map<String, String> getRows() { return new LinkedHashMap<String, String>(rows); }
}
