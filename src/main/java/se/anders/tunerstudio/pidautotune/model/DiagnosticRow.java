package se.anders.tunerstudio.pidautotune.model;

/** Immutable row displayed by the discovery and mapping tables. */
public final class DiagnosticRow {
    private final String category;
    private final String name;
    private final String value;
    private final String units;
    private final String minimum;
    private final String maximum;
    private final String dataType;
    private final String details;
    private final String status;

    public DiagnosticRow(
            String category,
            String name,
            String value,
            String units,
            String minimum,
            String maximum,
            String dataType,
            String details,
            String status) {
        this.category = safe(category);
        this.name = safe(name);
        this.value = safe(value);
        this.units = safe(units);
        this.minimum = safe(minimum);
        this.maximum = safe(maximum);
        this.dataType = safe(dataType);
        this.details = safe(details);
        this.status = safe(status);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    public String getCategory() { return category; }
    public String getName() { return name; }
    public String getValue() { return value; }
    public String getUnits() { return units; }
    public String getMinimum() { return minimum; }
    public String getMaximum() { return maximum; }
    public String getDataType() { return dataType; }
    public String getDetails() { return details; }
    public String getStatus() { return status; }
}
