package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

import java.util.ArrayList;
import java.util.List;

/**
 * Audited temporary actuator-excitation owner for autonomous DC-IAC identification.
 *
 * This class does not tune DC-IAC bias or PID. It temporarily owns only the upstream
 * main-idle open-loop request so the existing DC-IAC evidence/recommendation engines can
 * observe deterministic target holds and steps. The complete original open-loop table and
 * idle mode are snapshotted and restored; no Burn operation exists here.
 */
public final class DcIacAutonomousExcitationController {
    public static final String IDLE_MODE = "idleMode";
    public static final String OPEN_LOOP_TABLE = "cltIdleCorrTable";
    public static final String DC_MIN_POSITION = "dcIdleMinimumPosition";
    public static final String DC_MAX_POSITION = "dcIdleMaximumPosition";

    // Current EpicEFI/Mega144H7 INI stores cltIdleCorrTable in 0.5 %-point increments.
    private static final double TABLE_QUANTUM = 0.5;
    private static final String OPEN_LOOP_LABEL = "Open Loop";
    private static final String CLOSED_LOOP_LABEL = "Open Loop + Closed Loop";

    private final ControllerParameterServer server;
    private Snapshot activeSnapshot;
    private String activeConfiguration;
    private double activeCommand = Double.NaN;

    public DcIacAutonomousExcitationController(ControllerParameterServer server) {
        if (server == null) throw new IllegalArgumentException("parameter server cannot be null");
        this.server = server;
    }

    public Snapshot snapshot(String configurationName) throws Exception {
        ModeDefinition mode = readMode(configurationName);
        ArrayDefinition table = readArray(configurationName, OPEN_LOOP_TABLE);
        ScalarDefinition minimum = readScalar(configurationName, DC_MIN_POSITION);
        ScalarDefinition maximum = readScalar(configurationName, DC_MAX_POSITION);
        if (!(minimum.value < maximum.value)) {
            throw new IllegalStateException("DC-IAC configured travel is invalid: minimum must be below maximum.");
        }
        if (count(table.values) != 24) {
            throw new IllegalStateException("Expected the audited 8x3 (24-cell) cltIdleCorrTable; active definition contains "
                    + count(table.values) + " cells.");
        }
        return new Snapshot(mode.currentLabel, mode.currentIndex, mode.openLoopIndex,
                table.values, table.minimum, table.maximum, table.decimalPlaces,
                minimum.value, maximum.value);
    }

    /**
     * Replaces the full open-loop table with one value, verifies readback, then switches the
     * main idle controller to Open Loop and verifies the option label. If any step fails, the
     * original table/mode snapshot is restored immediately.
     */
    public void activate(String configurationName, Snapshot snapshot, double initialTableCommand) throws Exception {
        requireSnapshot(snapshot);
        if (activeSnapshot != null) throw new IllegalStateException("Autonomous idle excitation is already active.");
        verifyOriginal(configurationName, snapshot);
        double command = normalizeTableCommand(snapshot, initialTableCommand);
        Exception failure = null;
        try {
            writeConstantTableVerified(configurationName, snapshot, command);
            writeModeVerified(configurationName, snapshot.openLoopIndex, OPEN_LOOP_LABEL);
            activeSnapshot = snapshot;
            activeConfiguration = configurationName;
            activeCommand = command;
            verifyActive(configurationName);
            return;
        } catch (Exception ex) {
            failure = ex;
        }
        try {
            restoreSnapshot(configurationName, snapshot);
        } catch (Exception restoreFailure) {
            failure.addSuppressed(restoreFailure);
        }
        throw failure;
    }

    public double command(String configurationName, double tableCommand) throws Exception {
        requireActive(configurationName);
        verifyActive(configurationName);
        double command = normalizeTableCommand(activeSnapshot, tableCommand);
        writeConstantTableVerified(configurationName, activeSnapshot, command);
        activeCommand = command;
        return command;
    }

    public void verifyActive(String configurationName) throws Exception {
        requireActive(configurationName);
        ModeDefinition mode = readMode(configurationName);
        if (mode.currentIndex != activeSnapshot.openLoopIndex) {
            throw new IllegalStateException("idleMode changed outside autonomous excitation; expected Open Loop option "
                    + activeSnapshot.openLoopIndex + " but read " + mode.currentIndex + " ('" + mode.currentLabel + "').");
        }
        ArrayDefinition table = readArray(configurationName, OPEN_LOOP_TABLE);
        if (!sameShape(activeSnapshot.originalTable, table.values)) {
            throw new IllegalStateException("cltIdleCorrTable shape changed during autonomous excitation.");
        }
        double[][] expected = constantLike(activeSnapshot.originalTable, activeCommand);
        if (!sameMatrix(expected, table.values, activeSnapshot.tableDecimalPlaces)) {
            throw new IllegalStateException("cltIdleCorrTable changed outside autonomous excitation.");
        }
    }

    public void restore(String configurationName) throws Exception {
        if (activeSnapshot == null) return;
        Snapshot snapshot = activeSnapshot;
        String expectedConfiguration = activeConfiguration;
        Exception failure = null;
        try {
            if (expectedConfiguration != null && configurationName != null
                    && !expectedConfiguration.equals(configurationName)) {
                throw new IllegalStateException("Cannot restore autonomous excitation through a different ECU configuration.");
            }
            restoreSnapshot(configurationName, snapshot);
        } catch (Exception ex) {
            failure = ex;
        } finally {
            activeSnapshot = null;
            activeConfiguration = null;
            activeCommand = Double.NaN;
        }
        if (failure != null) throw failure;
    }

    public boolean isActive() { return activeSnapshot != null; }
    public double getActiveCommand() { return activeCommand; }
    public Snapshot getActiveSnapshot() { return activeSnapshot; }

    public static double normalizeTableCommand(Snapshot snapshot, double value) {
        requireSnapshot(snapshot);
        if (!finite(value)) throw new IllegalArgumentException("Open-loop idle-position command must be finite.");
        double bounded = Math.max(snapshot.tableMinimum, Math.min(snapshot.tableMaximum, value));
        double quantized = Math.round(bounded / TABLE_QUANTUM) * TABLE_QUANTUM;
        return round(quantized, Math.max(1, snapshot.tableDecimalPlaces));
    }

    private void verifyOriginal(String configurationName, Snapshot snapshot) throws Exception {
        ModeDefinition mode = readMode(configurationName);
        if (!same(mode.currentIndex, snapshot.originalModeIndex, 0)) {
            throw new IllegalStateException("idleMode changed since the autonomous snapshot was captured.");
        }
        ArrayDefinition table = readArray(configurationName, OPEN_LOOP_TABLE);
        if (!sameMatrix(snapshot.originalTable, table.values, snapshot.tableDecimalPlaces)) {
            throw new IllegalStateException("cltIdleCorrTable changed since the autonomous snapshot was captured.");
        }
    }

    private void restoreSnapshot(String configurationName, Snapshot snapshot) throws Exception {
        // Restore the table while still in Open Loop so the target moves back toward the user's
        // normal feed-forward surface before handing control back to the original idle mode.
        server.updateParameter(configurationName, OPEN_LOOP_TABLE, copy(snapshot.originalTable));
        ArrayDefinition table = readArray(configurationName, OPEN_LOOP_TABLE);
        if (!sameMatrix(snapshot.originalTable, table.values, snapshot.tableDecimalPlaces)) {
            throw new IllegalStateException("cltIdleCorrTable restore readback mismatch.");
        }
        writeModeVerified(configurationName, snapshot.originalModeIndex, snapshot.originalModeLabel);
    }

    private void writeConstantTableVerified(String configurationName, Snapshot snapshot, double command) throws Exception {
        double[][] proposed = constantLike(snapshot.originalTable, command);
        server.updateParameter(configurationName, OPEN_LOOP_TABLE, proposed);
        ArrayDefinition after = readArray(configurationName, OPEN_LOOP_TABLE);
        if (!sameMatrix(proposed, after.values, snapshot.tableDecimalPlaces)) {
            throw new IllegalStateException("cltIdleCorrTable command readback mismatch.");
        }
    }

    private void writeModeVerified(String configurationName, int optionIndex, String expectedLabel) throws Exception {
        server.updateParameter(configurationName, IDLE_MODE, (double) optionIndex);
        ModeDefinition after = readMode(configurationName);
        if (after.currentIndex != optionIndex) {
            throw new IllegalStateException("idleMode numeric readback mismatch; expected option " + optionIndex
                    + " but read option " + after.currentIndex + " ('" + after.currentLabel + "').");
        }
        String expected = normalizeOptionLabel(expectedLabel);
        if (!expected.isEmpty() && !after.currentLabel.isEmpty() && !expected.equalsIgnoreCase(after.currentLabel)) {
            throw new IllegalStateException("idleMode label readback mismatch; expected " + expectedLabel
                    + " but read " + after.currentLabel + ".");
        }
    }

    private ModeDefinition readMode(String configurationName) throws Exception {
        ControllerParameter parameter = parameter(configurationName, IDLE_MODE);
        String paramClass = safe(parameter.getParamClass());
        if (!"bits".equals(paramClass)) {
            throw new IllegalStateException("idleMode is not the expected bits parameter (read " + paramClass + ").");
        }
        String currentRaw = safe(parameter.getStringValue());
        String current = normalizeOptionLabel(currentRaw);
        ArrayList optionsRaw = parameter.getOptionDescriptions();
        List<String> options = new ArrayList<String>();
        List<String> rawOptions = new ArrayList<String>();
        if (optionsRaw != null) {
            for (Object option : optionsRaw) {
                String raw = option == null ? "" : option.toString().trim();
                rawOptions.add(raw);
                options.add(normalizeOptionLabel(raw));
            }
        }

        int numericIndex = -1;
        try {
            double numeric = parameter.getScalarValue();
            if (finite(numeric)) {
                int rounded = (int)Math.round(numeric);
                if (Math.abs(numeric - rounded) <= 0.001 && rounded >= 0 && rounded <= 1) numericIndex = rounded;
            }
        } catch (Throwable ignored) {
            // Some TunerStudio API versions expose bits primarily through string/options.
        }

        int currentIndex = indexOf(options, current);
        if (currentIndex < 0 && numericIndex >= 0) currentIndex = numericIndex;

        int openLoopIndex = indexOf(options, OPEN_LOOP_LABEL);
        // EpicEFI idleMode is a one-bit parameter: 0 = Open Loop + Closed Loop, 1 = Open Loop.
        // If a TunerStudio API build omits/mangles option descriptions, the encoded bit remains authoritative.
        if (openLoopIndex < 0 && (options.isEmpty() || options.size() == 2)) openLoopIndex = 1;

        if (currentIndex < 0) {
            throw new IllegalStateException("Could not resolve current idleMode from label '" + currentRaw
                    + "' or encoded bit value; options=" + rawOptions + ".");
        }
        if (openLoopIndex != 1) {
            throw new IllegalStateException("idleMode Open Loop mapping is not the expected encoded bit value 1; resolved "
                    + openLoopIndex + " from " + rawOptions + ".");
        }

        if (current.isEmpty() || indexOf(options, current) < 0) {
            current = currentIndex == 1 ? OPEN_LOOP_LABEL : CLOSED_LOOP_LABEL;
        }
        return new ModeDefinition(current, currentIndex, openLoopIndex);
    }

    private ScalarDefinition readScalar(String configurationName, String name) throws Exception {
        ControllerParameter parameter = parameter(configurationName, name);
        String paramClass = safe(parameter.getParamClass());
        if (!"scalar".equals(paramClass)) {
            throw new IllegalStateException(name + " is not a scalar parameter (read " + paramClass + ").");
        }
        double value = parameter.getScalarValue();
        if (!finite(value)) throw new IllegalStateException(name + " is not finite.");
        return new ScalarDefinition(value);
    }

    private ArrayDefinition readArray(String configurationName, String name) throws Exception {
        ControllerParameter parameter = parameter(configurationName, name);
        String paramClass = safe(parameter.getParamClass());
        if (!"array".equals(paramClass)) {
            throw new IllegalStateException(name + " is not an array parameter (read " + paramClass + ").");
        }
        double[][] values = parameter.getArrayValues();
        validateMatrix(name, values);
        return new ArrayDefinition(copy(values), parameter.getMin(), parameter.getMax(), parameter.getDecimalPlaces());
    }

    private ControllerParameter parameter(String configurationName, String name) throws Exception {
        ControllerParameter parameter = server.getControllerParameter(configurationName, name);
        if (parameter == null) throw new IllegalStateException(name + " is missing from the active ECU definition.");
        return parameter;
    }

    private void requireActive(String configurationName) {
        if (activeSnapshot == null) throw new IllegalStateException("Autonomous idle excitation is not active.");
        if (activeConfiguration != null && !activeConfiguration.equals(configurationName)) {
            throw new IllegalStateException("Autonomous idle excitation belongs to " + activeConfiguration + ", not " + configurationName + ".");
        }
    }

    private static void requireSnapshot(Snapshot snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("excitation snapshot cannot be null");
    }

    private static int indexOf(List<String> values, String wanted) {
        if (values == null || wanted == null) return -1;
        String canonicalWanted = normalizeOptionLabel(wanted);
        for (int i = 0; i < values.size(); i++) {
            if (canonicalWanted.equalsIgnoreCase(normalizeOptionLabel(values.get(i)))) return i;
        }
        return -1;
    }

    /** TunerStudio may expose INI option descriptions with literal quote characters. */
    private static String normalizeOptionLabel(String value) {
        String out = safe(value).trim();
        boolean changed = true;
        while (changed && out.length() >= 2) {
            changed = false;
            char first = out.charAt(0), last = out.charAt(out.length() - 1);
            if ((first == '\'' && last == '\'') || (first == '\"' && last == '\"')) {
                out = out.substring(1, out.length() - 1).trim();
                changed = true;
            }
        }
        return out.replaceAll("\\s+", " ");
    }

    private static void validateMatrix(String name, double[][] values) {
        if (values == null || values.length == 0) throw new IllegalStateException(name + " is empty.");
        int width = -1;
        for (double[] row : values) {
            if (row == null || row.length == 0) throw new IllegalStateException(name + " contains an empty row.");
            if (width < 0) width = row.length;
            if (row.length != width) throw new IllegalStateException(name + " is ragged.");
            for (double value : row) if (!finite(value)) throw new IllegalStateException(name + " contains a non-finite value.");
        }
    }

    private static int count(double[][] values) {
        int count = 0;
        if (values != null) for (double[] row : values) if (row != null) count += row.length;
        return count;
    }

    private static boolean sameShape(double[][] a, double[][] b) {
        if (a == null || b == null || a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i] == null || b[i] == null || a[i].length != b[i].length) return false;
        return true;
    }

    private static boolean sameMatrix(double[][] expected, double[][] actual, int decimalPlaces) {
        if (!sameShape(expected, actual)) return false;
        for (int r = 0; r < expected.length; r++) {
            for (int c = 0; c < expected[r].length; c++) {
                if (!same(expected[r][c], actual[r][c], decimalPlaces)) return false;
            }
        }
        return true;
    }

    private static boolean same(double a, double b, int decimalPlaces) {
        if (!finite(a) || !finite(b)) return false;
        double tolerance = 0.5 * Math.pow(10.0, -Math.max(0, decimalPlaces)) + 1e-9;
        return Math.abs(a - b) <= tolerance;
    }

    private static double[][] constantLike(double[][] shape, double value) {
        double[][] out = new double[shape.length][];
        for (int r = 0; r < shape.length; r++) {
            out[r] = new double[shape[r].length];
            for (int c = 0; c < out[r].length; c++) out[r][c] = value;
        }
        return out;
    }

    private static double[][] copy(double[][] input) {
        double[][] out = new double[input.length][];
        for (int i = 0; i < input.length; i++) out[i] = input[i].clone();
        return out;
    }

    private static double round(double value, int decimalPlaces) {
        double factor = Math.pow(10.0, Math.max(0, decimalPlaces));
        return Math.round(value * factor) / factor;
    }

    private static boolean finite(double value) { return !Double.isNaN(value) && !Double.isInfinite(value); }
    private static String safe(String value) { return value == null ? "" : value.trim(); }

    private static final class ModeDefinition {
        final String currentLabel;
        final int currentIndex;
        final int openLoopIndex;
        ModeDefinition(String currentLabel, int currentIndex, int openLoopIndex) {
            this.currentLabel = currentLabel;
            this.currentIndex = currentIndex;
            this.openLoopIndex = openLoopIndex;
        }
    }

    private static final class ScalarDefinition {
        final double value;
        ScalarDefinition(double value) { this.value = value; }
    }

    private static final class ArrayDefinition {
        final double[][] values;
        final double minimum;
        final double maximum;
        final int decimalPlaces;
        ArrayDefinition(double[][] values, double minimum, double maximum, int decimalPlaces) {
            this.values = values;
            this.minimum = minimum;
            this.maximum = maximum;
            this.decimalPlaces = decimalPlaces;
        }
    }

    public static final class Snapshot {
        public final String originalModeLabel;
        public final int originalModeIndex;
        public final int openLoopIndex;
        private final double[][] originalTable;
        public final double tableMinimum;
        public final double tableMaximum;
        public final int tableDecimalPlaces;
        public final double dcMinimumPosition;
        public final double dcMaximumPosition;

        Snapshot(String originalModeLabel, int originalModeIndex, int openLoopIndex,
                 double[][] originalTable, double tableMinimum, double tableMaximum, int tableDecimalPlaces,
                 double dcMinimumPosition, double dcMaximumPosition) {
            this.originalModeLabel = originalModeLabel;
            this.originalModeIndex = originalModeIndex;
            this.openLoopIndex = openLoopIndex;
            this.originalTable = copy(originalTable);
            this.tableMinimum = tableMinimum;
            this.tableMaximum = tableMaximum;
            this.tableDecimalPlaces = tableDecimalPlaces;
            this.dcMinimumPosition = dcMinimumPosition;
            this.dcMaximumPosition = dcMaximumPosition;
        }

        public double[][] originalTableCopy() { return copy(originalTable); }
        public int tableCellCount() { return count(originalTable); }
    }
}
