package se.anders.tunerstudio.pidautotune.dataset;

/**
 * Per-log interpretation of the vehicle-speed channel.
 *
 * The setting changes only dataset integrity and grouping. It never alters the
 * imported log or any ECU value.
 */
public enum VssSourceMode {
    AUTOMATIC(
            "Automatic detection",
            "Use the logged VSS channel and continuity checks. A missing or constant-zero channel is not accepted as proof that the vehicle was stationary."),
    VERIFIED_OPERATIONAL(
            "VSS verified operational",
            "Use the logged VSS channel and continuity checks. Select this only when the VSS input was known to be functioning during the complete log."),
    UNAVAILABLE_MANUALLY_STATIONARY(
            "VSS unavailable — vehicle manually confirmed stationary",
            "Ignore the logged VSS values and treat the complete log as stationary based on the operator's explicit confirmation."),
    UNAVAILABLE_MOTION_UNKNOWN(
            "VSS unavailable — motion unknown",
            "Ignore the logged VSS values. Events remain available for diagnostics but cannot satisfy recommendation readiness because motion is unknown.");

    private final String displayName;
    private final String description;

    VssSourceMode(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }

    public boolean usesLoggedVss() {
        return this == AUTOMATIC || this == VERIFIED_OPERATIONAL;
    }

    public boolean isManuallyConfirmedStationary() {
        return this == UNAVAILABLE_MANUALLY_STATIONARY;
    }

    public boolean isMotionUnknown() {
        return this == UNAVAILABLE_MOTION_UNKNOWN;
    }

    @Override
    public String toString() { return displayName; }
}
