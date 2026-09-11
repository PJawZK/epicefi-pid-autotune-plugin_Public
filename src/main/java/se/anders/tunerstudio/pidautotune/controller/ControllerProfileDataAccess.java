package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.OutputChannel;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import com.efiAnalytics.plugin.ecu.servers.OutputChannelServer;
import se.anders.tunerstudio.pidautotune.model.DiagnosticRow;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition;

import java.awt.Dimension;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Generic read-only definition discovery for ControllerProfileDefinition. */
public final class ControllerProfileDataAccess {
    private static final DecimalFormat NUMBER = new DecimalFormat("0.####");

    private final ControllerAccess controllerAccess;
    private final ControllerParameterServer parameterServer;
    private final OutputChannelServer outputServer;

    public ControllerProfileDataAccess(ControllerAccess controllerAccess) {
        if (controllerAccess == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        this.controllerAccess = controllerAccess;
        this.parameterServer = controllerAccess.getControllerParameterServer();
        this.outputServer = controllerAccess.getOutputChannelServer();
    }

    public String[] getConfigurationNames() {
        String[] names = controllerAccess.getEcuConfigurationNames();
        if (names == null) return new String[0];
        String[] copy = Arrays.copyOf(names, names.length);
        Arrays.sort(copy, String.CASE_INSENSITIVE_ORDER);
        return copy;
    }

    public List<DiagnosticRow> loadProfileMapping(
            String configurationName,
            ControllerProfileDefinition profile) {
        if (configurationName == null || configurationName.trim().isEmpty()) {
            throw new IllegalArgumentException("configurationName cannot be empty");
        }
        if (profile == null) throw new IllegalArgumentException("profile cannot be null");

        List<DiagnosticRow> rows = new ArrayList<DiagnosticRow>();
        for (ControllerProfileDefinition.Mapping mapping : profile.getParameters()) {
            rows.add(readParameter(configurationName, mapping));
        }
        for (ControllerProfileDefinition.Mapping mapping : profile.getOutputChannels()) {
            rows.add(readOutputChannel(configurationName, mapping));
        }
        return rows;
    }

    private DiagnosticRow readParameter(
            String configurationName,
            ControllerProfileDefinition.Mapping mapping) {
        try {
            ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, mapping.getName());
            if (parameter == null) return missingRow("Parameter", mapping);

            String paramClass = safe(parameter.getParamClass());
            String value = formatParameterValue(parameter, paramClass);
            String details = mappingDetails(mapping);
            if (ControllerParameter.PARAM_CLASS_ARRAY.equals(paramClass)) {
                Dimension shape = parameter.getShape();
                if (shape != null) details = join(details, "Array shape: " + shape.width + " x " + shape.height);
            } else if (ControllerParameter.PARAM_CLASS_BITS.equals(paramClass)) {
                details = join(details, "Options: " + safe(String.valueOf(parameter.getOptionDescriptions())));
            } else {
                details = join(details, "Decimal places: " + parameter.getDecimalPlaces());
            }

            return new DiagnosticRow(
                    "Parameter",
                    mapping.getName(),
                    value,
                    safe(parameter.getUnits()),
                    format(parameter.getMin()),
                    format(parameter.getMax()),
                    paramClass,
                    details,
                    "Mapped");
        } catch (Exception ex) {
            return errorRow("Parameter", mapping, ex);
        }
    }

    private DiagnosticRow readOutputChannel(
            String configurationName,
            ControllerProfileDefinition.Mapping mapping) {
        try {
            OutputChannel channel = outputServer.getOutputChannel(configurationName, mapping.getName());
            if (channel == null) return missingRow("Output channel", mapping);
            String details = mappingDetails(mapping);
            if (channel.getFormula() != null && !channel.getFormula().trim().isEmpty()) {
                details = join(details, "Formula: " + channel.getFormula());
            }
            return new DiagnosticRow(
                    "Output channel",
                    mapping.getName(),
                    "Live value available after subscription",
                    safe(channel.getUnits()),
                    format(channel.getMinValue()),
                    format(channel.getMaxValue()),
                    "OutputChannel",
                    details,
                    "Mapped");
        } catch (Exception ex) {
            return errorRow("Output channel", mapping, ex);
        }
    }

    private static String formatParameterValue(ControllerParameter parameter, String paramClass) {
        if (ControllerParameter.PARAM_CLASS_BITS.equals(paramClass)) return safe(parameter.getStringValue());
        if (ControllerParameter.PARAM_CLASS_ARRAY.equals(paramClass)) return previewArray(parameter.getArrayValues());
        return format(parameter.getScalarValue());
    }

    private static String previewArray(double[][] values) {
        if (values == null || values.length == 0) return "[]";
        StringBuilder builder = new StringBuilder("[");
        int shown = 0;
        int total = 0;
        for (double[] row : values) {
            if (row == null) continue;
            total += row.length;
            for (double value : row) {
                if (shown < 10) {
                    if (shown > 0) builder.append(", ");
                    builder.append(format(value));
                    shown++;
                }
            }
        }
        if (total > shown) builder.append(", … ").append(total).append(" values");
        return builder.append(']').toString();
    }

    private static DiagnosticRow missingRow(String category, ControllerProfileDefinition.Mapping mapping) {
        return new DiagnosticRow(
                category,
                mapping.getName(),
                "",
                "",
                "",
                "",
                "",
                mappingDetails(mapping),
                mapping.isRequired() ? "Required mapping missing" : "Optional mapping missing");
    }

    private static DiagnosticRow errorRow(
            String category,
            ControllerProfileDefinition.Mapping mapping,
            Exception ex) {
        String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        return new DiagnosticRow(
                category,
                mapping.getName(),
                "",
                "",
                "",
                "",
                "",
                join(mappingDetails(mapping), message),
                "Read error");
    }

    private static String mappingDetails(ControllerProfileDefinition.Mapping mapping) {
        return mapping.getRole().name() + " — " + (mapping.isRequired() ? "required" : "optional")
                + ". " + mapping.getPurpose();
    }

    private static String join(String first, String second) {
        String a = safe(first).trim();
        String b = safe(second).trim();
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        return a + "  " + b;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return String.valueOf(value);
        synchronized (NUMBER) {
            return NUMBER.format(value);
        }
    }
}
