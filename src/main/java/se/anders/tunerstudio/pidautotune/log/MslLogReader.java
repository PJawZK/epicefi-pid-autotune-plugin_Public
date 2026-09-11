package se.anders.tunerstudio.pidautotune.log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Streaming reader that extracts only the idle-analysis channels from a large TunerStudio MSL file. */
public final class MslLogReader {
    private static final int BUFFER_SIZE = 1024 * 1024;
    private static final String PID_GAIN_METADATA_LABEL = "pid gains at observer start:";

    public IdleLogData read(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Log file cannot be null");
        }
        if (!file.isFile()) {
            throw new IOException("Log file does not exist: " + file.getAbsolutePath());
        }

        BufferedReader reader = new BufferedReader(new InputStreamReader(
                new FileInputStream(file), StandardCharsets.ISO_8859_1), BUFFER_SIZE);
        try {
            List<String> metadata = new ArrayList<String>();
            String headerLine = null;
            char delimiter = '\t';
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || "#".equals(line.trim())) {
                    continue;
                }
                char candidateDelimiter = line.indexOf('\t') >= 0 ? '\t' : ',';
                String first = firstField(line, candidateDelimiter);
                if ("time".equals(normalize(first))) {
                    headerLine = line;
                    delimiter = candidateDelimiter;
                    break;
                }
                metadata.add(unquote(line.trim()));
            }

            if (headerLine == null) {
                throw new IOException("No TunerStudio log header beginning with Time was found.");
            }

            String[] headers = splitLine(headerLine, delimiter);
            String unitsLine = reader.readLine();
            if (unitsLine == null) {
                throw new IOException("The log contains a header but no units row or sample data.");
            }
            String[] units = splitLine(unitsLine, delimiter);

            Map<String, Integer> exactHeaderIndex = new HashMap<String, Integer>();
            Map<String, Integer> simpleHeaderIndex = new HashMap<String, Integer>();
            for (int i = 0; i < headers.length; i++) {
                String exact = normalize(headers[i]);
                if (!exactHeaderIndex.containsKey(exact)) {
                    exactHeaderIndex.put(exact, i);
                }
                String simple = simplify(headers[i]);
                if (!simpleHeaderIndex.containsKey(simple)) {
                    simpleHeaderIndex.put(simple, i);
                }
            }

            int pGainIndex = headerIndex(exactHeaderIndex, "P Gain");
            int iGainIndex = headerIndex(exactHeaderIndex, "I Gain");
            int dGainIndex = headerIndex(exactHeaderIndex, "D Gain");
            boolean hasMachineGainColumns = pGainIndex >= 0 && iGainIndex >= 0 && dGainIndex >= 0;

            EnumMap<LogChannelDefinition, String> matchedNames =
                    new EnumMap<LogChannelDefinition, String>(LogChannelDefinition.class);
            EnumMap<LogChannelDefinition, String> matchedUnits =
                    new EnumMap<LogChannelDefinition, String>(LogChannelDefinition.class);
            List<SelectedColumn> selected = new ArrayList<SelectedColumn>();

            for (LogChannelDefinition definition : LogChannelDefinition.values()) {
                int index = findIndex(definition, exactHeaderIndex, simpleHeaderIndex);
                if (index >= 0) {
                    selected.add(new SelectedColumn(definition, index));
                    matchedNames.put(definition, headers[index].trim());
                    matchedUnits.put(definition, index < units.length ? units[index].trim() : "");
                }
            }

            if (!matchedNames.containsKey(LogChannelDefinition.TIME)) {
                throw new IOException("The Time channel could not be mapped from the log header.");
            }
            if (!matchedNames.containsKey(LogChannelDefinition.RPM)) {
                throw new IOException("The RPM channel could not be mapped from the log header.");
            }
            if (!matchedNames.containsKey(LogChannelDefinition.IDLE_TARGET)) {
                throw new IOException("The idle target channel could not be mapped from the log header.");
            }

            Collections.sort(selected, new Comparator<SelectedColumn>() {
                @Override
                public int compare(SelectedColumn left, SelectedColumn right) {
                    return left.sourceIndex - right.sourceIndex;
                }
            });

            int[] selectedIndices = new int[selected.size()];
            DoubleSeriesBuilder[] builders = new DoubleSeriesBuilder[selected.size()];
            int timeSelectedIndex = -1;
            for (int i = 0; i < selected.size(); i++) {
                selectedIndices[i] = selected.get(i).sourceIndex;
                builders[i] = new DoubleSeriesBuilder(32768);
                if (selected.get(i).definition == LogChannelDefinition.TIME) {
                    timeSelectedIndex = i;
                }
            }

            double machineP = Double.NaN;
            double machineI = Double.NaN;
            double machineD = Double.NaN;
            boolean machineGainConflict = false;
            double[] selectedValues = new double[selected.size()];
            int skippedRows = 0;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                Arrays.fill(selectedValues, Double.NaN);
                extractSelectedValues(line, delimiter, selectedIndices, selectedValues);
                double time = selectedValues[timeSelectedIndex];
                if (!isFinite(time)) {
                    skippedRows++;
                    continue;
                }
                for (int i = 0; i < builders.length; i++) {
                    builders[i].add(selectedValues[i]);
                }

                if (hasMachineGainColumns) {
                    double p = extractFieldValue(line, delimiter, pGainIndex);
                    double i = extractFieldValue(line, delimiter, iGainIndex);
                    double d = extractFieldValue(line, delimiter, dGainIndex);
                    if (validGain(p) && validGain(i) && validGain(d)) {
                        if (!validGain(machineP)) {
                            machineP = p;
                            machineI = i;
                            machineD = d;
                        } else if (!sameGain(machineP, p) || !sameGain(machineI, i) || !sameGain(machineD, d)) {
                            machineGainConflict = true;
                        }
                    }
                }
            }

            EnumMap<LogChannelDefinition, double[]> series =
                    new EnumMap<LogChannelDefinition, double[]>(LogChannelDefinition.class);
            for (int i = 0; i < selected.size(); i++) {
                series.put(selected.get(i).definition, builders[i].toArray());
            }

            double[] time = series.get(LogChannelDefinition.TIME);
            if (time == null || time.length == 0) {
                throw new IOException("The log contains no numeric samples.");
            }

            String normalizedGainMetadata = null;
            if (!machineGainConflict && validGain(machineP) && validGain(machineI) && validGain(machineD)) {
                normalizedGainMetadata = gainMetadata(machineP, machineI, machineD);
            }
            if (normalizedGainMetadata == null) {
                normalizedGainMetadata = normalizeLegacyGainMetadata(metadata);
            }
            if (normalizedGainMetadata != null) {
                metadata.add(0, normalizedGainMetadata);
            }

            double duration = Math.max(0.0, time[time.length - 1] - time[0]);
            double sampleRate = calculateSampleRate(time);

            return new IdleLogData(
                    file,
                    metadata,
                    series,
                    matchedNames,
                    matchedUnits,
                    headers.length,
                    skippedRows,
                    duration,
                    sampleRate);
        } finally {
            reader.close();
        }
    }

    private static int findIndex(
            LogChannelDefinition definition,
            Map<String, Integer> exactHeaderIndex,
            Map<String, Integer> simpleHeaderIndex) {
        for (String alias : definition.getAliases()) {
            Integer exact = exactHeaderIndex.get(normalize(alias));
            if (exact != null) {
                return exact;
            }
        }
        for (String alias : definition.getAliases()) {
            Integer simple = simpleHeaderIndex.get(simplify(alias));
            if (simple != null) {
                return simple;
            }
        }
        return -1;
    }

    private static int headerIndex(Map<String, Integer> exactHeaderIndex, String name) {
        Integer index = exactHeaderIndex.get(normalize(name));
        return index == null ? -1 : index.intValue();
    }

    private static void extractSelectedValues(
            String line,
            char delimiter,
            int[] selectedIndices,
            double[] selectedValues) {
        int fieldIndex = 0;
        int selectedIndex = 0;
        int fieldStart = 0;
        int length = line.length();

        for (int position = 0; position <= length && selectedIndex < selectedIndices.length; position++) {
            if (position == length || line.charAt(position) == delimiter) {
                if (fieldIndex == selectedIndices[selectedIndex]) {
                    selectedValues[selectedIndex] = parseDouble(line, fieldStart, position);
                    selectedIndex++;
                }
                fieldIndex++;
                fieldStart = position + 1;
            }
        }
    }

    private static double extractFieldValue(String line, char delimiter, int wantedIndex) {
        if (wantedIndex < 0) return Double.NaN;
        int fieldIndex = 0;
        int fieldStart = 0;
        int length = line.length();
        for (int position = 0; position <= length; position++) {
            if (position == length || line.charAt(position) == delimiter) {
                if (fieldIndex == wantedIndex) {
                    return parseDouble(line, fieldStart, position);
                }
                fieldIndex++;
                fieldStart = position + 1;
            }
        }
        return Double.NaN;
    }

    private static double parseDouble(String line, int start, int end) {
        while (start < end && Character.isWhitespace(line.charAt(start))) start++;
        while (end > start && Character.isWhitespace(line.charAt(end - 1))) end--;
        if (start >= end) return Double.NaN;
        try {
            return Double.parseDouble(line.substring(start, end));
        } catch (NumberFormatException ex) {
            return Double.NaN;
        }
    }

    private static String normalizeLegacyGainMetadata(List<String> metadata) {
        for (String raw : metadata) {
            String text = raw == null ? "" : raw.replace("#", "").trim();
            String lower = text.toLowerCase(Locale.ROOT);
            int marker = lower.indexOf(PID_GAIN_METADATA_LABEL);
            if (marker < 0) continue;
            int colon = text.indexOf(':', marker);
            String gainText = colon >= 0 ? text.substring(colon + 1) : text.substring(marker);
            double p = Double.NaN;
            double i = Double.NaN;
            double d = Double.NaN;
            for (String part : gainText.split("/")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                char gain = Character.toUpperCase(trimmed.charAt(0));
                double value = firstFlexibleNumberAfter(trimmed, 1);
                if (gain == 'P') p = value;
                else if (gain == 'I') i = value;
                else if (gain == 'D') d = value;
            }
            if (validGain(p) && validGain(i) && validGain(d)) {
                return gainMetadata(p, i, d);
            }
        }
        return null;
    }

    private static double firstFlexibleNumberAfter(String text, int start) {
        if (text == null) return Double.NaN;
        int index = Math.max(0, start);
        while (index < text.length()) {
            char value = text.charAt(index);
            if ((value >= '0' && value <= '9') || value == '-' || value == '+' || value == '.' || value == ',') break;
            index++;
        }
        int end = index;
        boolean separatorSeen = false;
        while (end < text.length()) {
            char value = text.charAt(end);
            if (value >= '0' && value <= '9') {
                end++;
                continue;
            }
            if ((value == '-' || value == '+') && end == index) {
                end++;
                continue;
            }
            if ((value == '.' || value == ',') && !separatorSeen) {
                separatorSeen = true;
                end++;
                continue;
            }
            break;
        }
        if (end <= index) return Double.NaN;
        String token = text.substring(index, end).replace(',', '.');
        try {
            return Double.parseDouble(token);
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static String gainMetadata(double p, double i, double d) {
        return "# PID gains at observer start: P " + machineNumber(p)
                + " / I " + machineNumber(i)
                + " / D " + machineNumber(d);
    }

    private static String machineNumber(double value) {
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private static boolean validGain(double value) {
        return isFinite(value) && value >= 0.0 && value <= 10000.0;
    }

    private static boolean sameGain(double left, double right) {
        return Math.abs(left - right) <= 0.000000001;
    }

    private static double calculateSampleRate(double[] time) {
        if (time.length < 2) return 0.0;
        double[] positiveDeltas = new double[time.length - 1];
        int count = 0;
        for (int i = 1; i < time.length; i++) {
            double delta = time[i] - time[i - 1];
            if (isFinite(delta) && delta > 0.000001) {
                positiveDeltas[count++] = delta;
            }
        }
        if (count == 0) return 0.0;
        Arrays.sort(positiveDeltas, 0, count);
        double median;
        int middle = count / 2;
        if ((count & 1) == 0) {
            median = (positiveDeltas[middle - 1] + positiveDeltas[middle]) * 0.5;
        } else {
            median = positiveDeltas[middle];
        }
        return median <= 0.0 ? 0.0 : 1.0 / median;
    }

    private static String[] splitLine(String line, char delimiter) {
        return line.split(delimiter == '\t' ? "\\t" : ",", -1);
    }

    private static String firstField(String line, char delimiter) {
        int end = line.indexOf(delimiter);
        return end < 0 ? line : line.substring(0, end);
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && value.charAt(0) == '"' && value.charAt(value.length() - 1) == '"') {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private static String simplify(String value) {
        String normalized = normalize(value);
        StringBuilder builder = new StringBuilder(normalized.length());
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            if (Character.isLetterOrDigit(character)) {
                builder.append(character);
            }
        }
        return builder.toString();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static final class SelectedColumn {
        private final LogChannelDefinition definition;
        private final int sourceIndex;

        private SelectedColumn(LogChannelDefinition definition, int sourceIndex) {
            this.definition = definition;
            this.sourceIndex = sourceIndex;
        }
    }

    private static final class DoubleSeriesBuilder {
        private double[] values;
        private int size;

        private DoubleSeriesBuilder(int initialCapacity) {
            values = new double[Math.max(16, initialCapacity)];
        }

        private void add(double value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        private double[] toArray() {
            return Arrays.copyOf(values, size);
        }
    }
}
