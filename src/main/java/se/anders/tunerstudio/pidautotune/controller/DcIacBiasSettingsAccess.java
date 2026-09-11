package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Read-only access to the DC-IAC feed-forward/bias curve and integral limits used by M4A. */
public final class DcIacBiasSettingsAccess {
    private final ControllerParameterServer parameterServer;

    public DcIacBiasSettingsAccess(ControllerAccess access) {
        if (access == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        this.parameterServer = access.getControllerParameterServer();
    }

    public Snapshot read(String configurationName) throws Exception {
        ArrayRead binsRead = array(configurationName, "dcIdleBiasBins");
        ArrayRead valuesRead = array(configurationName, "dcIdleBiasValues");
        double[] bins = binsRead.values;
        double[] values = valuesRead.values;
        if (bins.length < 2 || bins.length != values.length) {
            throw new IllegalStateException("dcIdleBiasBins/dcIdleBiasValues must have the same length >= 2");
        }
        for (int i = 1; i < bins.length; i++) {
            if (!(bins[i] > bins[i - 1])) {
                throw new IllegalStateException("dcIdleBiasBins must be strictly increasing");
            }
        }
        Double iMin = optionalScalar(configurationName, "dcIdle_iTermMin");
        Double iMax = optionalScalar(configurationName, "dcIdle_iTermMax");
        return new Snapshot(bins, values, iMin, iMax, binsRead.decimalPlaces, valuesRead.decimalPlaces);
    }

    private ArrayRead array(String configurationName, String name) throws Exception {
        ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, name);
        if (parameter == null) throw new IllegalStateException("Required parameter not found: " + name);
        double[][] values = parameter.getArrayValues();
        if (values == null) throw new IllegalStateException("Required array value unavailable: " + name);
        List<Double> flattened = new ArrayList<Double>();
        for (double[] row : values) {
            if (row == null) continue;
            for (double value : row) {
                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalStateException("Non-finite value in required array: " + name);
                }
                flattened.add(Double.valueOf(value));
            }
        }
        double[] result = new double[flattened.size()];
        for (int i = 0; i < result.length; i++) result[i] = flattened.get(i).doubleValue();
        int decimals;
        try { decimals = Math.max(0, parameter.getDecimalPlaces()); }
        catch (Throwable ignored) { decimals = 0; }
        return new ArrayRead(result, decimals);
    }

    private Double optionalScalar(String configurationName, String name) {
        try {
            ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, name);
            if (parameter == null) return null;
            double value = parameter.getScalarValue();
            return Double.isNaN(value) || Double.isInfinite(value) ? null : Double.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static final class ArrayRead {
        private final double[] values;
        private final int decimalPlaces;
        private ArrayRead(double[] values, int decimalPlaces) {
            this.values = values;
            this.decimalPlaces = decimalPlaces;
        }
    }

    public static final class Snapshot {
        private final double[] bins;
        private final double[] values;
        private final Double iTermMinimum;
        private final Double iTermMaximum;
        private final int binDecimalPlaces;
        private final int valueDecimalPlaces;

        /** Compatibility constructor retained for older tests/callers. */
        public Snapshot(double[] bins, double[] values, Double iTermMinimum, Double iTermMaximum) {
            this(bins, values, iTermMinimum, iTermMaximum, 0, 0);
        }

        public Snapshot(double[] bins, double[] values, Double iTermMinimum, Double iTermMaximum,
                        int binDecimalPlaces, int valueDecimalPlaces) {
            if (bins == null || values == null || bins.length < 2 || bins.length != values.length) {
                throw new IllegalArgumentException("bias bins/values must have the same length >= 2");
            }
            this.bins = Arrays.copyOf(bins, bins.length);
            this.values = Arrays.copyOf(values, values.length);
            this.iTermMinimum = iTermMinimum;
            this.iTermMaximum = iTermMaximum;
            this.binDecimalPlaces = Math.max(0, binDecimalPlaces);
            this.valueDecimalPlaces = Math.max(0, valueDecimalPlaces);
        }

        public int size() { return bins.length; }
        public double getBin(int index) { return bins[index]; }
        public double getValue(int index) { return values[index]; }
        public double[] getBins() { return Arrays.copyOf(bins, bins.length); }
        public double[] getValues() { return Arrays.copyOf(values, values.length); }
        public int getBinDecimalPlaces() { return binDecimalPlaces; }
        public int getValueDecimalPlaces() { return valueDecimalPlaces; }
        public double getValueQuantum() { return Math.pow(10.0, -valueDecimalPlaces); }
        public boolean areIntegralLimitsKnown() { return iTermMinimum != null && iTermMaximum != null; }
        public double getITermMinimum() { return iTermMinimum == null ? Double.NaN : iTermMinimum.doubleValue(); }
        public double getITermMaximum() { return iTermMaximum == null ? Double.NaN : iTermMaximum.doubleValue(); }

        public double interpolate(double position) {
            if (position <= bins[0]) return values[0];
            int last = bins.length - 1;
            if (position >= bins[last]) return values[last];
            int low = lowerIndex(position);
            int high = low + 1;
            double fraction = (position - bins[low]) / (bins[high] - bins[low]);
            return values[low] + fraction * (values[high] - values[low]);
        }

        public int lowerIndex(double position) {
            if (position <= bins[0]) return 0;
            for (int i = 0; i < bins.length - 1; i++) {
                if (position < bins[i + 1]) return i;
            }
            return bins.length - 2;
        }

        public String segmentLabel(double position) {
            int low = lowerIndex(position);
            return trim(bins[low]) + "↔" + trim(bins[low + 1]) + "%";
        }

        private static String trim(double value) {
            if (Math.abs(value - Math.rint(value)) < 0.000001) return Long.toString(Math.round(value));
            return String.format(java.util.Locale.US, "%.2f", value);
        }
    }
}
