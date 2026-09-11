package se.anders.tunerstudio.pidautotune.live;

/** How stationary operation is established for a live guided-capture session. */
public enum LiveMotionMode {
    USE_LIVE_VSS("Use live VSS — verified operational", "Require a known-working fresh VSS channel and keep speed below the stationary threshold."),
    MANUAL_STATIONARY("VSS unavailable — manually confirmed stationary", "Ignore VSS for the live session and require an explicit stationary confirmation.");

    private final String displayName;
    private final String description;

    LiveMotionMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDescription() { return description; }

    @Override
    public String toString() { return displayName; }
}
