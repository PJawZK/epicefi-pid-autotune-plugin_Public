/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.efiAnalytics.plugin.ecu.ControllerParameter
 *  com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer
 */
package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import java.util.Locale;

public final class DcIacRamWriteCoordinator {
    public static final String BIAS_BINS = "dcIdleBiasBins";
    public static final String BIAS_VALUES = "dcIdleBiasValues";
    public static final String P_NAME = "dcIdlePositionPid_pFactor";
    public static final String I_NAME = "dcIdlePositionPid_iFactor";
    public static final String D_NAME = "dcIdlePositionPid_dFactor";
    private final ControllerParameterServer server;

    public DcIacRamWriteCoordinator(ControllerParameterServer controllerParameterServer) {
        if (controllerParameterServer == null) {
            throw new IllegalArgumentException("parameter server cannot be null");
        }
        this.server = controllerParameterServer;
    }

    public PidSnapshot readPid(String string) throws Exception {
        return new PidSnapshot(this.readScalar((String)string, (String)P_NAME).value, this.readScalar((String)string, (String)I_NAME).value, this.readScalar((String)string, (String)D_NAME).value);
    }

    public BiasSnapshot readBias(String string) throws Exception {
        ArrayDefinition arrayDefinition = this.readArray(string, BIAS_BINS);
        ArrayDefinition arrayDefinition2 = this.readArray(string, BIAS_VALUES);
        if (!DcIacRamWriteCoordinator.sameShape(arrayDefinition.values, arrayDefinition2.values)) {
            throw new IllegalStateException("dcIdleBiasBins/dcIdleBiasValues array shapes do not match.");
        }
        int n = DcIacRamWriteCoordinator.count(arrayDefinition.values);
        if (n < 2) {
            throw new IllegalStateException("DC-IAC bias curve must contain at least two bin/value points.");
        }
        DcIacRamWriteCoordinator.validateIncreasingBins(arrayDefinition.values, arrayDefinition.decimalPlaces);
        return new BiasSnapshot(arrayDefinition.values, arrayDefinition2.values, arrayDefinition.minimum, arrayDefinition.maximum, arrayDefinition.decimalPlaces, arrayDefinition2.minimum, arrayDefinition2.maximum, arrayDefinition2.decimalPlaces);
    }

    public WriteResult applyPidCandidate(String string, PidSnapshot pidSnapshot, PidSnapshot pidSnapshot2) throws Exception {
        DcIacRamWriteCoordinator.requirePid(pidSnapshot, "expected PID");
        DcIacRamWriteCoordinator.requirePid(pidSnapshot2, "proposed PID");
        PidSnapshot pidSnapshot3 = this.readPid(string);
        this.verifyPid(string, pidSnapshot, pidSnapshot3, "PID changed outside the autonomous tuner");
        String string2 = this.changedGain(string, pidSnapshot3, pidSnapshot2);
        if (string2 == null) {
            throw new IllegalArgumentException("DC-IAC PID candidate must change exactly one of P, I or D.");
        }
        if ("D".equals(string2) && pidSnapshot2.d > pidSnapshot3.d) {
            throw new IllegalArgumentException("Automatic DC-IAC D increases are prohibited by the validated safety policy.");
        }
        try {
            String string3;
            String string4 = "P".equals(string2) ? P_NAME : (string3 = "I".equals(string2) ? I_NAME : D_NAME);
            double d = "P".equals(string2) ? pidSnapshot2.p : ("I".equals(string2) ? pidSnapshot2.i : pidSnapshot2.d);
            this.writeScalarVerified(string, string4, d);
            PidSnapshot pidSnapshot4 = this.readPid(string);
            this.verifyPid(string, pidSnapshot2, pidSnapshot4, "candidate readback mismatch");
            this.verifyOtherGainsUnchanged(string, pidSnapshot3, pidSnapshot4, string2);
            return new WriteResult("PID candidate applied", string2, pidSnapshot3.toString(), pidSnapshot4.toString(), "RAM-only DC-IAC PID candidate written and verified; no Burn command was sent.");
        }
        catch (Exception exception) {
            try {
                this.restorePid(string, pidSnapshot3);
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            throw exception;
        }
    }

    public WriteResult restorePid(String string, PidSnapshot pidSnapshot) throws Exception {
        DcIacRamWriteCoordinator.requirePid(pidSnapshot, "PID restore target");
        PidSnapshot pidSnapshot2 = this.readPid(string);
        this.writeScalarVerified(string, P_NAME, pidSnapshot.p);
        this.writeScalarVerified(string, I_NAME, pidSnapshot.i);
        this.writeScalarVerified(string, D_NAME, pidSnapshot.d);
        PidSnapshot pidSnapshot3 = this.readPid(string);
        this.verifyPid(string, pidSnapshot, pidSnapshot3, "PID restore readback mismatch");
        return new WriteResult("PID restored", "P/I/D", pidSnapshot2.toString(), pidSnapshot3.toString(), "RAM-only DC-IAC PID restore verified; no Burn command was sent.");
    }

    public WriteResult applyBiasKnot(String string, BiasSnapshot biasSnapshot, int n, double d, double d2) throws Exception {
        if (biasSnapshot == null) {
            throw new IllegalArgumentException("expected bias snapshot cannot be null");
        }
        BiasSnapshot biasSnapshot2 = this.readBias(string);
        DcIacRamWriteCoordinator.verifyBias(biasSnapshot, biasSnapshot2, "Bias curve changed outside the autonomous tuner");
        DcIacRamWriteCoordinator.requireBiasIndex(biasSnapshot2, n);
        if (!DcIacRamWriteCoordinator.same(d, biasSnapshot2.getBin(n), biasSnapshot2.binDecimalPlaces)) {
            throw new IllegalStateException("Bias proposal/bin mismatch at knot " + n + ": proposal position " + d + " but ECU dcIdleBiasBins reads " + biasSnapshot2.getBin(n) + ".");
        }
        DcIacRamWriteCoordinator.validateRange("dcIdleBiasValues[" + n + "]", d2, biasSnapshot2.minimum, biasSnapshot2.maximum);
        double d3 = DcIacRamWriteCoordinator.normalize(d2, biasSnapshot2.decimalPlaces);
        if (DcIacRamWriteCoordinator.same(d3, biasSnapshot2.get(n), biasSnapshot2.decimalPlaces)) {
            throw new IllegalArgumentException("Bias candidate is identical to the current knot at controller precision.");
        }
        BiasSnapshot biasSnapshot3 = biasSnapshot2.withValue(n, d3);
        try {
            this.server.updateParameter(string, BIAS_VALUES, biasSnapshot3.valuesMatrixCopy());
            BiasSnapshot biasSnapshot4 = this.readBias(string);
            DcIacRamWriteCoordinator.verifyBias(biasSnapshot3, biasSnapshot4, "bias candidate readback mismatch");
            return new WriteResult("Bias knot applied", "Bias[" + n + "] @ " + DcIacRamWriteCoordinator.format(biasSnapshot2.getBin(n)) + "%", DcIacRamWriteCoordinator.format(biasSnapshot2.get(n)), DcIacRamWriteCoordinator.format(biasSnapshot4.get(n)), "RAM-only dcIdleBiasValues knot written; dcIdleBiasBins and dcIdleBiasValues were both read back and verified; no Burn command was sent.");
        }
        catch (Exception exception) {
            try {
                this.restoreBias(string, biasSnapshot2);
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            throw exception;
        }
    }

    public WriteResult applyBiasKnot(String string, BiasSnapshot biasSnapshot, int n, double d) throws Exception {
        if (biasSnapshot == null) {
            throw new IllegalArgumentException("expected bias snapshot cannot be null");
        }
        DcIacRamWriteCoordinator.requireBiasIndex(biasSnapshot, n);
        return this.applyBiasKnot(string, biasSnapshot, n, biasSnapshot.getBin(n), d);
    }

    public WriteResult applyBiasPoint(String string, BiasSnapshot biasSnapshot, int n, double d, double d2) throws Exception {
        if (biasSnapshot == null) {
            throw new IllegalArgumentException("expected bias snapshot cannot be null");
        }
        BiasSnapshot biasSnapshot2 = this.readBias(string);
        DcIacRamWriteCoordinator.verifyBias(biasSnapshot, biasSnapshot2, "Bias curve changed outside the autonomous tuner");
        DcIacRamWriteCoordinator.requireBiasIndex(biasSnapshot2, n);
        DcIacRamWriteCoordinator.validateRange("dcIdleBiasBins[" + n + "]", d, biasSnapshot2.binMinimum, biasSnapshot2.binMaximum);
        DcIacRamWriteCoordinator.validateRange("dcIdleBiasValues[" + n + "]", d2, biasSnapshot2.minimum, biasSnapshot2.maximum);
        double d3 = DcIacRamWriteCoordinator.normalize(d, biasSnapshot2.binDecimalPlaces);
        double d4 = DcIacRamWriteCoordinator.normalize(d2, biasSnapshot2.decimalPlaces);
        BiasSnapshot biasSnapshot3 = biasSnapshot2.withPoint(n, d3, d4);
        DcIacRamWriteCoordinator.validateIncreasingBins(biasSnapshot3.bins, biasSnapshot3.binDecimalPlaces);
        if (DcIacRamWriteCoordinator.same(d3, biasSnapshot2.getBin(n), biasSnapshot2.binDecimalPlaces) && DcIacRamWriteCoordinator.same(d4, biasSnapshot2.get(n), biasSnapshot2.decimalPlaces)) {
            throw new IllegalArgumentException("Bias point candidate is identical to the current bin/value point at controller precision.");
        }
        try {
            this.server.updateParameter(string, BIAS_VALUES, biasSnapshot3.valuesMatrixCopy());
            this.server.updateParameter(string, BIAS_BINS, biasSnapshot3.binsMatrixCopy());
            BiasSnapshot biasSnapshot4 = this.readBias(string);
            DcIacRamWriteCoordinator.verifyBias(biasSnapshot3, biasSnapshot4, "bias point readback mismatch");
            return new WriteResult("Bias point applied", "Bias[" + n + "]", DcIacRamWriteCoordinator.format(biasSnapshot2.getBin(n)) + "% / " + DcIacRamWriteCoordinator.format(biasSnapshot2.get(n)), DcIacRamWriteCoordinator.format(biasSnapshot4.getBin(n)) + "% / " + DcIacRamWriteCoordinator.format(biasSnapshot4.get(n)), "RAM-only dcIdleBiasValues + dcIdleBiasBins point staged and both complete arrays verified; no Burn command was sent.");
        }
        catch (Exception exception) {
            try {
                this.restoreBias(string, biasSnapshot2);
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            throw exception;
        }
    }

    public WriteResult restoreBias(String string, BiasSnapshot biasSnapshot) throws Exception {
        if (biasSnapshot == null) {
            throw new IllegalArgumentException("bias restore target cannot be null");
        }
        BiasSnapshot biasSnapshot2 = this.readBias(string);
        try {
            this.server.updateParameter(string, BIAS_BINS, biasSnapshot.binsMatrixCopy());
            this.server.updateParameter(string, BIAS_VALUES, biasSnapshot.valuesMatrixCopy());
            BiasSnapshot biasSnapshot3 = this.readBias(string);
            DcIacRamWriteCoordinator.verifyBias(biasSnapshot, biasSnapshot3, "bias restore readback mismatch");
            return new WriteResult("Bias curve restored", "dcIdleBiasBins + dcIdleBiasValues", biasSnapshot2.signature(), biasSnapshot3.signature(), "RAM-only DC-IAC bias bins and values restored and verified; no Burn command was sent.");
        }
        catch (Exception exception) {
            try {
                this.server.updateParameter(string, BIAS_BINS, biasSnapshot2.binsMatrixCopy());
                this.server.updateParameter(string, BIAS_VALUES, biasSnapshot2.valuesMatrixCopy());
                DcIacRamWriteCoordinator.verifyBias(biasSnapshot2, this.readBias(string), "bias rollback readback mismatch");
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            throw exception;
        }
    }

    private ScalarDefinition readScalar(String string, String string2) throws Exception {
        ControllerParameter controllerParameter = this.server.getControllerParameter(string, string2);
        if (controllerParameter == null) {
            throw new IllegalStateException(string2 + " is missing from the active ECU definition.");
        }
        if (!"scalar".equals(controllerParameter.getParamClass())) {
            throw new IllegalStateException(string2 + " is not scalar.");
        }
        return new ScalarDefinition(controllerParameter.getScalarValue(), controllerParameter.getMin(), controllerParameter.getMax(), Math.max(0, controllerParameter.getDecimalPlaces()));
    }

    private ArrayDefinition readArray(String string, String string2) throws Exception {
        double[][] dArray;
        ControllerParameter controllerParameter = this.server.getControllerParameter(string, string2);
        if (controllerParameter == null) {
            throw new IllegalStateException(string2 + " is missing from the active ECU definition.");
        }
        if (!"array".equals(controllerParameter.getParamClass())) {
            throw new IllegalStateException(string2 + " is not an array.");
        }
        double[][] dArray2 = controllerParameter.getArrayValues();
        if (dArray2 == null || dArray2.length == 0) {
            throw new IllegalStateException(string2 + " array values are unavailable.");
        }
        int n = DcIacRamWriteCoordinator.count(dArray2);
        if (n == 0) {
            throw new IllegalStateException(string2 + " array is empty.");
        }
        for (double[] dArray3 : dArray = DcIacRamWriteCoordinator.copy(dArray2)) {
            if (dArray3 == null) continue;
            for (double d : dArray3) {
                if (DcIacRamWriteCoordinator.finite(d)) continue;
                throw new IllegalStateException(string2 + " contains a non-finite value.");
            }
        }
        return new ArrayDefinition(dArray, controllerParameter.getMin(), controllerParameter.getMax(), Math.max(0, controllerParameter.getDecimalPlaces()));
    }

    private void writeScalarVerified(String string, String string2, double d) throws Exception {
        ScalarDefinition scalarDefinition = this.readScalar(string, string2);
        DcIacRamWriteCoordinator.validateRange(string2, d, scalarDefinition.minimum, scalarDefinition.maximum);
        double d2 = DcIacRamWriteCoordinator.normalize(d, scalarDefinition.decimalPlaces);
        this.server.updateParameter(string, string2, d2);
        ScalarDefinition scalarDefinition2 = this.readScalar(string, string2);
        if (!DcIacRamWriteCoordinator.same(d2, scalarDefinition2.value, scalarDefinition2.decimalPlaces)) {
            throw new IllegalStateException(string2 + " readback mismatch: requested " + d2 + " but read " + scalarDefinition2.value + ".");
        }
    }

    private void verifyPid(String string, PidSnapshot pidSnapshot, PidSnapshot pidSnapshot2, String string2) throws Exception {
        this.verifyScalar(string, P_NAME, pidSnapshot.p, pidSnapshot2.p, string2 + " (P)");
        this.verifyScalar(string, I_NAME, pidSnapshot.i, pidSnapshot2.i, string2 + " (I)");
        this.verifyScalar(string, D_NAME, pidSnapshot.d, pidSnapshot2.d, string2 + " (D)");
    }

    private void verifyOtherGainsUnchanged(String string, PidSnapshot pidSnapshot, PidSnapshot pidSnapshot2, String string2) throws Exception {
        if (!"P".equals(string2)) {
            this.verifyScalar(string, P_NAME, pidSnapshot.p, pidSnapshot2.p, "P unexpectedly changed");
        }
        if (!"I".equals(string2)) {
            this.verifyScalar(string, I_NAME, pidSnapshot.i, pidSnapshot2.i, "I unexpectedly changed");
        }
        if (!"D".equals(string2)) {
            this.verifyScalar(string, D_NAME, pidSnapshot.d, pidSnapshot2.d, "D unexpectedly changed");
        }
    }

    private void verifyScalar(String string, String string2, double d, double d2, String string3) throws Exception {
        int n = this.readScalar((String)string, (String)string2).decimalPlaces;
        if (!DcIacRamWriteCoordinator.same(d, d2, n)) {
            throw new IllegalStateException(string3 + ": expected " + d + ", read " + d2 + ".");
        }
    }

    private String changedGain(String string, PidSnapshot pidSnapshot, PidSnapshot pidSnapshot2) throws Exception {
        int n = 0;
        String string2 = null;
        if (!DcIacRamWriteCoordinator.same(pidSnapshot.p, pidSnapshot2.p, this.readScalar((String)string, (String)P_NAME).decimalPlaces)) {
            ++n;
            string2 = "P";
        }
        if (!DcIacRamWriteCoordinator.same(pidSnapshot.i, pidSnapshot2.i, this.readScalar((String)string, (String)I_NAME).decimalPlaces)) {
            ++n;
            string2 = "I";
        }
        if (!DcIacRamWriteCoordinator.same(pidSnapshot.d, pidSnapshot2.d, this.readScalar((String)string, (String)D_NAME).decimalPlaces)) {
            ++n;
            string2 = "D";
        }
        return n == 1 ? string2 : null;
    }

    private static void verifyBias(BiasSnapshot biasSnapshot, BiasSnapshot biasSnapshot2, String string) {
        if (!DcIacRamWriteCoordinator.sameShape(biasSnapshot.bins, biasSnapshot2.bins) || !DcIacRamWriteCoordinator.sameShape(biasSnapshot.values, biasSnapshot2.values)) {
            throw new IllegalStateException(string + ": bias curve array shape changed.");
        }
        int n = Math.max(biasSnapshot.binDecimalPlaces, biasSnapshot2.binDecimalPlaces);
        int n2 = Math.max(biasSnapshot.decimalPlaces, biasSnapshot2.decimalPlaces);
        for (int i = 0; i < biasSnapshot.size(); ++i) {
            if (!DcIacRamWriteCoordinator.same(biasSnapshot.getBin(i), biasSnapshot2.getBin(i), n)) {
                throw new IllegalStateException(string + " at dcIdleBiasBins[" + i + "]: expected " + biasSnapshot.getBin(i) + ", read " + biasSnapshot2.getBin(i) + ".");
            }
            if (DcIacRamWriteCoordinator.same(biasSnapshot.get(i), biasSnapshot2.get(i), n2)) continue;
            throw new IllegalStateException(string + " at dcIdleBiasValues[" + i + "]: expected " + biasSnapshot.get(i) + ", read " + biasSnapshot2.get(i) + ".");
        }
    }

    private static void validateIncreasingBins(double[][] dArray, int n) {
        int n2 = DcIacRamWriteCoordinator.count(dArray);
        double d = Double.NaN;
        for (int i = 0; i < n2; ++i) {
            double d2 = DcIacRamWriteCoordinator.flatGet(dArray, i);
            double d3 = DcIacRamWriteCoordinator.normalize(d2, n);
            if (i > 0 && d3 <= DcIacRamWriteCoordinator.normalize(d, n)) {
                throw new IllegalStateException("dcIdleBiasBins must be strictly increasing at controller precision; knot " + i + " reads " + d2 + " after " + d + ".");
            }
            d = d2;
        }
    }

    private static void requireBiasIndex(BiasSnapshot biasSnapshot, int n) {
        if (n < 0 || n >= biasSnapshot.size()) {
            throw new IllegalArgumentException("Bias knot index is outside dcIdleBiasBins/dcIdleBiasValues.");
        }
    }

    private static void validateRange(String string, double d, double d2, double d3) {
        if (!DcIacRamWriteCoordinator.finite(d)) {
            throw new IllegalArgumentException(string + " candidate is not finite.");
        }
        if (d < d2 || d > d3) {
            throw new IllegalArgumentException(string + " candidate " + d + " is outside definition range " + d2 + " to " + d3 + ".");
        }
    }

    private static void requirePid(PidSnapshot pidSnapshot, String string) {
        if (pidSnapshot == null || !pidSnapshot.complete()) {
            throw new IllegalArgumentException(string + " is incomplete.");
        }
    }

    private static double normalize(double d, int n) {
        double d2 = Math.pow(10.0, n);
        return (double)Math.round(d * d2) / d2;
    }

    private static boolean same(double d, double d2, int n) {
        return Math.abs(DcIacRamWriteCoordinator.normalize(d, n) - DcIacRamWriteCoordinator.normalize(d2, n)) <= Math.pow(10.0, -Math.max(0, n)) * 0.25;
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static double[][] copy(double[][] dArray) {
        double[][] dArrayArray = new double[dArray.length][];
        for (int i = 0; i < dArray.length; ++i) {
            dArrayArray[i] = dArray[i] == null ? null : (double[])dArray[i].clone();
        }
        return dArrayArray;
    }

    private static int count(double[][] dArray) {
        int n = 0;
        for (double[] dArray2 : dArray) {
            if (dArray2 == null) continue;
            n += dArray2.length;
        }
        return n;
    }

    private static double flatGet(double[][] dArray, int n) {
        int n2 = 0;
        for (double[] dArray2 : dArray) {
            if (dArray2 == null) continue;
            if (n < n2 + dArray2.length) {
                return dArray2[n - n2];
            }
            n2 += dArray2.length;
        }
        throw new IndexOutOfBoundsException();
    }

    private static double[][] flatWith(double[][] dArray, int n, double d) {
        double[][] dArray2 = DcIacRamWriteCoordinator.copy(dArray);
        int n2 = 0;
        for (double[] dArray3 : dArray2) {
            if (dArray3 == null) continue;
            if (n < n2 + dArray3.length) {
                dArray3[n - n2] = d;
                return dArray2;
            }
            n2 += dArray3.length;
        }
        throw new IndexOutOfBoundsException();
    }

    private static boolean sameShape(double[][] dArray, double[][] dArray2) {
        if (dArray.length != dArray2.length) {
            return false;
        }
        for (int i = 0; i < dArray.length; ++i) {
            int n;
            int n2 = dArray[i] == null ? -1 : dArray[i].length;
            int n3 = n = dArray2[i] == null ? -1 : dArray2[i].length;
            if (n2 == n) continue;
            return false;
        }
        return true;
    }

    private static String format(double d) {
        return String.format(Locale.ROOT, "%.4f", d);
    }

    public static final class PidSnapshot {
        public final double p;
        public final double i;
        public final double d;

        public PidSnapshot(double d, double d2, double d3) {
            this.p = d;
            this.i = d2;
            this.d = d3;
        }

        public boolean complete() {
            return DcIacRamWriteCoordinator.finite(this.p) && DcIacRamWriteCoordinator.finite(this.i) && DcIacRamWriteCoordinator.finite(this.d);
        }

        public String toString() {
            return "P " + DcIacRamWriteCoordinator.format(this.p) + " / I " + DcIacRamWriteCoordinator.format(this.i) + " / D " + DcIacRamWriteCoordinator.format(this.d);
        }
    }

    private static final class ScalarDefinition {
        final double value;
        final double minimum;
        final double maximum;
        final int decimalPlaces;

        ScalarDefinition(double d, double d2, double d3, int n) {
            this.value = d;
            this.minimum = d2;
            this.maximum = d3;
            this.decimalPlaces = n;
        }
    }

    private static final class ArrayDefinition {
        final double[][] values;
        final double minimum;
        final double maximum;
        final int decimalPlaces;

        ArrayDefinition(double[][] dArray, double d, double d2, int n) {
            this.values = dArray;
            this.minimum = d;
            this.maximum = d2;
            this.decimalPlaces = n;
        }
    }

    public static final class BiasSnapshot {
        private final double[][] bins;
        private final double[][] values;
        public final double binMinimum;
        public final double binMaximum;
        public final int binDecimalPlaces;
        public final double minimum;
        public final double maximum;
        public final int decimalPlaces;

        BiasSnapshot(double[][] dArray, double[][] dArray2, double d, double d2, int n, double d3, double d4, int n2) {
            this.bins = DcIacRamWriteCoordinator.copy(dArray);
            this.values = DcIacRamWriteCoordinator.copy(dArray2);
            this.binMinimum = d;
            this.binMaximum = d2;
            this.binDecimalPlaces = n;
            this.minimum = d3;
            this.maximum = d4;
            this.decimalPlaces = n2;
        }

        public int size() {
            return DcIacRamWriteCoordinator.count(this.values);
        }

        public double getBin(int n) {
            return DcIacRamWriteCoordinator.flatGet(this.bins, n);
        }

        public double getValue(int n) {
            return DcIacRamWriteCoordinator.flatGet(this.values, n);
        }

        public double get(int n) {
            return this.getValue(n);
        }

        public BiasSnapshot withValue(int n, double d) {
            return new BiasSnapshot(this.bins, DcIacRamWriteCoordinator.flatWith(this.values, n, d), this.binMinimum, this.binMaximum, this.binDecimalPlaces, this.minimum, this.maximum, this.decimalPlaces);
        }

        public BiasSnapshot withPoint(int n, double d, double d2) {
            return new BiasSnapshot(DcIacRamWriteCoordinator.flatWith(this.bins, n, d), DcIacRamWriteCoordinator.flatWith(this.values, n, d2), this.binMinimum, this.binMaximum, this.binDecimalPlaces, this.minimum, this.maximum, this.decimalPlaces);
        }

        public double[][] binsMatrixCopy() {
            return DcIacRamWriteCoordinator.copy(this.bins);
        }

        public double[][] valuesMatrixCopy() {
            return DcIacRamWriteCoordinator.copy(this.values);
        }

        public double[][] matrixCopy() {
            return this.valuesMatrixCopy();
        }

        public String signature() {
            int n;
            StringBuilder stringBuilder = new StringBuilder("bins=");
            for (n = 0; n < this.size(); ++n) {
                if (n > 0) {
                    stringBuilder.append(',');
                }
                stringBuilder.append(DcIacRamWriteCoordinator.format(this.getBin(n)));
            }
            stringBuilder.append(" | values=");
            for (n = 0; n < this.size(); ++n) {
                if (n > 0) {
                    stringBuilder.append(',');
                }
                stringBuilder.append(DcIacRamWriteCoordinator.format(this.get(n)));
            }
            return stringBuilder.toString();
        }
    }

    public static final class WriteResult {
        public final String status;
        public final String target;
        public final String before;
        public final String after;
        public final String message;

        WriteResult(String string, String string2, String string3, String string4, String string5) {
            this.status = string;
            this.target = string2;
            this.before = string3;
            this.after = string4;
            this.message = string5;
        }
    }
}

