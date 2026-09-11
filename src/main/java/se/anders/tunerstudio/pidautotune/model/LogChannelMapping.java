package se.anders.tunerstudio.pidautotune.model;

/** One logical analyser channel and the actual caption matched in an imported log. */
public final class LogChannelMapping {
    private final String logicalName;
    private final boolean required;
    private final String matchedName;
    private final String units;
    private final String status;

    public LogChannelMapping(String logicalName, boolean required, String matchedName, String units, String status) {
        this.logicalName = safe(logicalName);
        this.required = required;
        this.matchedName = safe(matchedName);
        this.units = safe(units);
        this.status = safe(status);
    }

    private static String safe(String value) { return value == null ? "" : value; }

    public String getLogicalName() { return logicalName; }
    public boolean isRequired() { return required; }
    public String getMatchedName() { return matchedName; }
    public String getUnits() { return units; }
    public String getStatus() { return status; }
}
