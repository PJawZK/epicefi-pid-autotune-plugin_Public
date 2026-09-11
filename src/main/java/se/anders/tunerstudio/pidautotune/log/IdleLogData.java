package se.anders.tunerstudio.pidautotune.log;

import se.anders.tunerstudio.pidautotune.model.LogChannelMapping;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Immutable selected-channel data extracted from a TunerStudio MSL log. */
public final class IdleLogData {
    private final File sourceFile;
    private final List<String> metadata;
    private final Map<LogChannelDefinition, double[]> series;
    private final Map<LogChannelDefinition, String> matchedNames;
    private final Map<LogChannelDefinition, String> units;
    private final int sourceColumnCount;
    private final int skippedRows;
    private final double durationSeconds;
    private final double sampleRateHz;

    public IdleLogData(
            File sourceFile,
            List<String> metadata,
            Map<LogChannelDefinition, double[]> series,
            Map<LogChannelDefinition, String> matchedNames,
            Map<LogChannelDefinition, String> units,
            int sourceColumnCount,
            int skippedRows,
            double durationSeconds,
            double sampleRateHz) {
        this.sourceFile = sourceFile;
        this.metadata = Collections.unmodifiableList(new ArrayList<String>(metadata));
        this.series = Collections.unmodifiableMap(new EnumMap<LogChannelDefinition, double[]>(series));
        this.matchedNames = Collections.unmodifiableMap(new EnumMap<LogChannelDefinition, String>(matchedNames));
        this.units = Collections.unmodifiableMap(new EnumMap<LogChannelDefinition, String>(units));
        this.sourceColumnCount = sourceColumnCount;
        this.skippedRows = skippedRows;
        this.durationSeconds = durationSeconds;
        this.sampleRateHz = sampleRateHz;
    }

    public File getSourceFile() { return sourceFile; }
    public List<String> getMetadata() { return metadata; }
    public int getSourceColumnCount() { return sourceColumnCount; }
    public int getSkippedRows() { return skippedRows; }
    public double getDurationSeconds() { return durationSeconds; }
    public double getSampleRateHz() { return sampleRateHz; }

    public int getSampleCount() {
        double[] time = getSeries(LogChannelDefinition.TIME);
        return time == null ? 0 : time.length;
    }

    public boolean has(LogChannelDefinition definition) {
        return series.containsKey(definition);
    }

    public double[] getSeries(LogChannelDefinition definition) {
        return series.get(definition);
    }

    public String getMatchedName(LogChannelDefinition definition) {
        String value = matchedNames.get(definition);
        return value == null ? "" : value;
    }

    public String getUnits(LogChannelDefinition definition) {
        String value = units.get(definition);
        return value == null ? "" : value;
    }

    public int getMappedChannelCount() { return series.size(); }

    public List<LogChannelMapping> buildMappingRows() {
        List<LogChannelMapping> rows = new ArrayList<LogChannelMapping>();
        for (LogChannelDefinition definition : LogChannelDefinition.values()) {
            boolean mapped = has(definition);
            rows.add(new LogChannelMapping(
                    definition.getDisplayName(),
                    definition.isRequired(),
                    mapped ? getMatchedName(definition) : "",
                    mapped ? getUnits(definition) : "",
                    mapped ? "Mapped" : (definition.isRequired() ? "Required channel missing" : "Optional channel not present")));
        }
        return rows;
    }
}
