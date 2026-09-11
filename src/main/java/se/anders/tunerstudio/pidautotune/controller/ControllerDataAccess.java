package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.OutputChannel;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;
import se.anders.tunerstudio.pidautotune.model.DiagnosticRow;
import se.anders.tunerstudio.pidautotune.profile.EpicEfiIdleProfile;

import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Read-only wrapper around the TunerStudio controller APIs. */
public final class ControllerDataAccess {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.####");

    private final ControllerAccess controllerAccess;
    private final ControllerParameterServer parameterServer;
    private final OutputChannelServer outputServer;

    public ControllerDataAccess(ControllerAccess controllerAccess) {
        if (controllerAccess == null) {
            throw new IllegalArgumentException("controllerAccess cannot be null");
        }
        this.controllerAccess = controllerAccess;
        this.parameterServer = controllerAccess.getControllerParameterServer();
        this.outputServer = controllerAccess.getOutputChannelServer();
    }

    public String[] getConfigurationNames() {
        String[] names = controllerAccess.getEcuConfigurationNames();
        if (names == null) {
            return new String[0];
        }
        String[] copy = Arrays.copyOf(names, names.length);
        Arrays.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    public List<DiagnosticRow> loadKnownIdleMapping(String configurationName) {
        List<DiagnosticRow> rows = new ArrayList<DiagnosticRow>();

        for (EpicEfiIdleProfile.Mapping mapping : EpicEfiIdleProfile.parameters()) {
            rows.add(readParameter(configurationName, mapping.getName(), mapping.getPurpose(), true));
        }
        for (EpicEfiIdleProfile.Mapping mapping : EpicEfiIdleProfile.outputChannels()) {
            rows.add(readOutputChannel(configurationName, mapping.getName(), mapping.getPurpose(), true));
        }
        return rows;
    }

    public List<DiagnosticRow> loadAllDiagnostics(String configurationName) {
        List<DiagnosticRow> rows = new ArrayList<DiagnosticRow>();

        String[] parameterNames = parameterServer.getParameterNames(configurationName);
        if (parameterNames != null) {
            Arrays.sort(parameterNames, String.CASE_INSENSITIVE_ORDER);
            for (String name : parameterNames) {
                rows.add(readParameter(configurationName, name, "", false));
            }
        }

        try {
            String[] outputNames = outputServer.getOutputChannels(configurationName);
            if (outputNames != null) {
                Arrays.sort(outputNames, String.CASE_INSENSITIVE_ORDER);
                for (String name : outputNames) {
                    rows.add(readOutputChannel(configurationName, name, "", false));
                }
            }
        } catch (Exception ex) {
            rows.add(errorRow("Output channel", "<enumeration>", ex));
        }

        Collections.sort(rows, new Comparator<DiagnosticRow>() {
            @Override
            public int compare(DiagnosticRow left, DiagnosticRow right) {
                int category = left.getCategory().compareToIgnoreCase(right.getCategory());
                return category != 0 ? category : left.getName().compareToIgnoreCase(right.getName());
            }
        });
        return rows;
    }

    private DiagnosticRow readParameter(
            String configurationName,
            String name,
            String purpose,
            boolean mapped) {
        try {
            ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, name);
            if (parameter == null) {
                return missingRow("Parameter", name, purpose, mapped);
            }

            String paramClass = safe(parameter.getParamClass());
            String value = formatParameterValue(parameter, paramClass);
            String details = purpose;
            if (ControllerParameter.PARAM_CLASS_ARRAY.equals(paramClass)) {
                Dimension shape = parameter.getShape();
                if (shape != null) {
                    details = joinDetails(purpose, "Array shape: " + shape.width + " x " + shape.height);
                }
            } else if (ControllerParameter.PARAM_CLASS_BITS.equals(paramClass)) {
                details = joinDetails(purpose, "Options: " + safe(String.valueOf(parameter.getOptionDescriptions())));
            } else {
                details = joinDetails(purpose, "Decimal places: " + parameter.getDecimalPlaces());
            }

            LimitDisplay limits = parameterLimits(parameter.getMin(), parameter.getMax(), paramClass);
            details = joinDetails(details, limits.note);
            return new DiagnosticRow(
                    "Parameter",
                    name,
                    value,
                    safe(parameter.getUnits()),
                    limits.minimum,
                    limits.maximum,
                    paramClass,
                    details,
                    mapped ? "Mapped" : "Available");
        } catch (Exception ex) {
            return errorRow("Parameter", name, ex, purpose);
        }
    }

    private DiagnosticRow readOutputChannel(
            String configurationName,
            String name,
            String purpose,
            boolean mapped) {
        try {
            OutputChannel channel = outputServer.getOutputChannel(configurationName, name);
            if (channel == null) {
                return missingRow("Output channel", name, purpose, mapped);
            }
            LimitDisplay limits = outputLimits(channel.getMinValue(), channel.getMaxValue());
            String details = joinDetails(purpose, emptyToNull(channel.getFormula()) == null
                    ? ""
                    : "Formula: " + channel.getFormula());
            details = joinDetails(details, limits.note);
            return new DiagnosticRow(
                    "Output channel",
                    name,
                    "Live value available after subscription",
                    safe(channel.getUnits()),
                    limits.minimum,
                    limits.maximum,
                    "OutputChannel",
                    details,
                    mapped ? "Mapped" : "Available");
        } catch (Exception ex) {
            return errorRow("Output channel", name, ex, purpose);
        }
    }

    private static String formatParameterValue(ControllerParameter parameter, String paramClass) {
        if (ControllerParameter.PARAM_CLASS_BITS.equals(paramClass)) {
            return safe(parameter.getStringValue());
        }
        if (ControllerParameter.PARAM_CLASS_ARRAY.equals(paramClass)) {
            return previewArray(parameter.getArrayValues());
        }
        return format(parameter.getScalarValue());
    }

    private static String previewArray(double[][] values) {
        if (values == null || values.length == 0) {
            return "[]";
        }
        StringBuilder builder = new StringBuilder("[");
        int shown = 0;
        int total = 0;
        for (double[] row : values) {
            if (row == null) {
                continue;
            }
            total += row.length;
            for (double value : row) {
                if (shown < 10) {
                    if (shown > 0) {
                        builder.append(", ");
                    }
                    builder.append(format(value));
                    shown++;
                }
            }
        }
        if (total > shown) {
            builder.append(", … ").append(total).append(" values");
        }
        return builder.append(']').toString();
    }

    private static DiagnosticRow missingRow(String category, String name, String purpose, boolean mapped) {
        return new DiagnosticRow(
                category, name, "", "", "", "", "",
                purpose,
                mapped ? "Not found in active definition" : "Unavailable");
    }

    private static DiagnosticRow errorRow(String category, String name, Exception ex) {
        return errorRow(category, name, ex, "");
    }

    private static DiagnosticRow errorRow(String category, String name, Exception ex, String purpose) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return new DiagnosticRow(category, name, "", "", "", "", "",
                joinDetails(purpose, message), "Read error");
    }

    private static String joinDetails(String first, String second) {
        String a = safe(first).trim();
        String b = safe(second).trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + "  " + b;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }


    private static LimitDisplay parameterLimits(double minimum, double maximum, String paramClass) {
        if (ControllerParameter.PARAM_CLASS_BITS.equals(paramClass)
                || isObviousDefinitionTypeRange(minimum, maximum)) {
            return definitionTypeLimits(minimum, maximum);
        }
        return new LimitDisplay(format(minimum), format(maximum), "");
    }

    private static LimitDisplay outputLimits(double minimum, double maximum) {
        if (isCommonOutputTypeRange(minimum, maximum)
                || isObviousDefinitionTypeRange(minimum, maximum)) {
            return definitionTypeLimits(minimum, maximum);
        }
        return new LimitDisplay(format(minimum), format(maximum), "");
    }

    private static LimitDisplay definitionTypeLimits(double minimum, double maximum) {
        return new LimitDisplay(
                "Definition type limit",
                "Definition type limit",
                "Raw definition range: " + format(minimum) + " to " + format(maximum));
    }

    private static boolean isObviousDefinitionTypeRange(double minimum, double maximum) {
        return Math.abs(minimum) >= 1000000000.0 || Math.abs(maximum) >= 1000000000.0;
    }

    private static boolean isCommonOutputTypeRange(double minimum, double maximum) {
        return pair(minimum, maximum, -127.0, 127.0)
                || pair(minimum, maximum, 0.0, 255.0)
                || pair(minimum, maximum, 0.0, 65535.0)
                || pair(minimum, maximum, -327.67, 327.67)
                || pair(minimum, maximum, -163.835, 163.835)
                || pair(minimum, maximum, -32768.0, 32767.0)
                || pair(minimum, maximum, -2147483648.0, 2147483647.0)
                || pair(minimum, maximum, 0.0, 2147483646.0)
                || pair(minimum, maximum, -1073741823.0, 1073741823.0);
    }

    private static boolean pair(double minimum, double maximum, double expectedMinimum, double expectedMaximum) {
        return approximately(minimum, expectedMinimum) && approximately(maximum, expectedMaximum);
    }

    private static boolean approximately(double left, double right) {
        double scale = Math.max(1.0, Math.abs(right));
        return Math.abs(left - right) <= scale * 0.000001;
    }

    private static final class LimitDisplay {
        private final String minimum;
        private final String maximum;
        private final String note;

        private LimitDisplay(String minimum, String maximum, String note) {
            this.minimum = minimum;
            this.maximum = maximum;
            this.note = note;
        }
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return String.valueOf(value);
        }
        synchronized (NUMBER) {
            return NUMBER.format(value);
        }
    }
}
