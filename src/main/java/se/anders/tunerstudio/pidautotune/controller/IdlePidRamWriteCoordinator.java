/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.efiAnalytics.plugin.ecu.ControllerParameter
 *  com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer
 *  se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot
 */
package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;

public final class IdlePidRamWriteCoordinator {
    public static final String P_NAME = "idleRpmPid_pFactor";
    public static final String I_NAME = "idleRpmPid_iFactor";
    public static final String D_NAME = "idleRpmPid_dFactor";
    private final ControllerParameterServer parameterServer;

    public IdlePidRamWriteCoordinator(ControllerParameterServer controllerParameterServer) {
        if (controllerParameterServer == null) {
            throw new IllegalArgumentException("parameterServer cannot be null");
        }
        this.parameterServer = controllerParameterServer;
    }

    public TuneSnapshot readGains(String string, String string2) throws Exception {
        IdlePidRamWriteCoordinator.requireConfiguration(string);
        return new TuneSnapshot(this.readScalar((String)string, (String)P_NAME).value, this.readScalar((String)string, (String)I_NAME).value, this.readScalar((String)string, (String)D_NAME).value, string2 == null ? "ECU RAM readback" : string2, false);
    }

    public WriteResult applySingleGainCandidate(String string, TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2) throws Exception {
        double d;
        IdlePidRamWriteCoordinator.requireConfiguration(string);
        IdlePidRamWriteCoordinator.requireComplete("expected current gains", tuneSnapshot);
        IdlePidRamWriteCoordinator.requireComplete("proposed gains", tuneSnapshot2);
        TuneSnapshot tuneSnapshot3 = this.readGains(string, "Pre-write ECU readback");
        if (!this.sameSnapshot(string, tuneSnapshot, tuneSnapshot3)) {
            throw new IllegalStateException("ECU idle PID gains changed since the candidate was prepared. Expected " + tuneSnapshot.toSignature() + " but ECU now reports " + tuneSnapshot3.toSignature() + ".");
        }
        String string2 = IdlePidRamWriteCoordinator.changedGain(tuneSnapshot3, tuneSnapshot2);
        if (string2 == null) {
            throw new IllegalArgumentException("Candidate must change exactly one of P, I, or D.");
        }
        String string3 = IdlePidRamWriteCoordinator.parameterName(string2);
        ScalarDefinition scalarDefinition = this.readScalar(string, string3);
        double d2 = IdlePidRamWriteCoordinator.normalize(IdlePidRamWriteCoordinator.gainValue(tuneSnapshot2, string2), scalarDefinition.decimalPlaces);
        if (IdlePidRamWriteCoordinator.sameAtPrecision(d2, d = IdlePidRamWriteCoordinator.normalize(IdlePidRamWriteCoordinator.gainValue(tuneSnapshot3, string2), scalarDefinition.decimalPlaces), scalarDefinition.decimalPlaces)) {
            throw new IllegalArgumentException("Candidate does not produce a representable " + string2 + " change at the active controller-definition precision.");
        }
        try {
            VerifiedScalar verifiedScalar = this.writeScalarVerified(string, string3, d2);
            TuneSnapshot tuneSnapshot4 = this.readGains(string, "Verified RAM candidate");
            this.verifyUnchangedGains(string, tuneSnapshot3, tuneSnapshot4, string2);
            TuneSnapshot tuneSnapshot5 = IdlePidRamWriteCoordinator.replaceGain(tuneSnapshot3, string2, verifiedScalar.readback, "Normalized RAM candidate");
            this.verifySnapshot(string, tuneSnapshot5, tuneSnapshot4, "candidate readback");
            return new WriteResult("Candidate applied", string2, verifiedScalar.requested, verifiedScalar.readback, tuneSnapshot3, tuneSnapshot4, "RAM-only candidate written and verified. No Burn command was sent.");
        }
        catch (Exception exception) {
            try {
                this.restore(string, tuneSnapshot3);
            }
            catch (Exception exception2) {
                exception.addSuppressed(exception2);
            }
            throw exception;
        }
    }

    public boolean matchesCurrent(String string, TuneSnapshot tuneSnapshot) throws Exception {
        IdlePidRamWriteCoordinator.requireConfiguration(string);
        IdlePidRamWriteCoordinator.requireComplete("expected gains", tuneSnapshot);
        return this.sameSnapshot(string, tuneSnapshot, this.readGains(string, "Current ECU readback"));
    }

    public WriteResult restore(String string, TuneSnapshot tuneSnapshot) throws Exception {
        IdlePidRamWriteCoordinator.requireConfiguration(string);
        IdlePidRamWriteCoordinator.requireComplete("restore target", tuneSnapshot);
        TuneSnapshot tuneSnapshot2 = this.readGains(string, "Pre-restore ECU readback");
        this.writeScalarVerified(string, P_NAME, tuneSnapshot.getP());
        this.writeScalarVerified(string, I_NAME, tuneSnapshot.getI());
        this.writeScalarVerified(string, D_NAME, tuneSnapshot.getD());
        TuneSnapshot tuneSnapshot3 = this.readGains(string, "Verified RAM restore");
        this.verifySnapshot(string, tuneSnapshot, tuneSnapshot3, "restore readback");
        return new WriteResult("Snapshot restored", "P/I/D", Double.NaN, Double.NaN, tuneSnapshot2, tuneSnapshot3, "RAM-only restore completed and verified. No Burn command was sent.");
    }

    private VerifiedScalar writeScalarVerified(String string, String string2, double d) throws Exception {
        ScalarDefinition scalarDefinition = this.readScalar(string, string2);
        IdlePidRamWriteCoordinator.validateRequested(string2, scalarDefinition, d);
        double d2 = IdlePidRamWriteCoordinator.normalize(d, scalarDefinition.decimalPlaces);
        this.parameterServer.updateParameter(string, string2, d2);
        ScalarDefinition scalarDefinition2 = this.readScalar(string, string2);
        if (!IdlePidRamWriteCoordinator.sameAtPrecision(d2, scalarDefinition2.value, scalarDefinition2.decimalPlaces)) {
            throw new IllegalStateException(string2 + " readback mismatch: requested " + d2 + " but ECU/TunerStudio reports " + scalarDefinition2.value + ".");
        }
        return new VerifiedScalar(d2, scalarDefinition2.value);
    }

    private ScalarDefinition readScalar(String string, String string2) throws Exception {
        ControllerParameter controllerParameter = this.parameterServer.getControllerParameter(string, string2);
        if (controllerParameter == null) {
            throw new IllegalStateException(string2 + " is not present in the active ECU definition.");
        }
        if (!"scalar".equals(controllerParameter.getParamClass())) {
            throw new IllegalStateException(string2 + " is not a scalar parameter in the active ECU definition.");
        }
        return new ScalarDefinition(controllerParameter.getScalarValue(), controllerParameter.getMin(), controllerParameter.getMax(), Math.max(0, controllerParameter.getDecimalPlaces()));
    }

    private static void validateRequested(String string, ScalarDefinition scalarDefinition, double d) {
        if (!IdlePidRamWriteCoordinator.finite(d)) {
            throw new IllegalArgumentException(string + " candidate is not finite.");
        }
        if (IdlePidRamWriteCoordinator.finite(scalarDefinition.minimum) && IdlePidRamWriteCoordinator.finite(scalarDefinition.maximum) && Math.abs(scalarDefinition.minimum) < 1.0E8 && Math.abs(scalarDefinition.maximum) < 1.0E8 && scalarDefinition.minimum < scalarDefinition.maximum && (d < scalarDefinition.minimum || d > scalarDefinition.maximum)) {
            throw new IllegalArgumentException(string + " candidate " + d + " is outside the ECU-definition range " + scalarDefinition.minimum + " to " + scalarDefinition.maximum + ".");
        }
    }

    private void verifyUnchangedGains(String string, TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2, String string2) throws Exception {
        if (!"P".equals(string2)) {
            this.verifyGain(string, P_NAME, tuneSnapshot.getP(), tuneSnapshot2.getP(), "P unexpectedly changed");
        }
        if (!"I".equals(string2)) {
            this.verifyGain(string, I_NAME, tuneSnapshot.getI(), tuneSnapshot2.getI(), "I unexpectedly changed");
        }
        if (!"D".equals(string2)) {
            this.verifyGain(string, D_NAME, tuneSnapshot.getD(), tuneSnapshot2.getD(), "D unexpectedly changed");
        }
    }

    private void verifySnapshot(String string, TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2, String string2) throws Exception {
        this.verifyGain(string, P_NAME, tuneSnapshot.getP(), tuneSnapshot2.getP(), string2 + " P mismatch");
        this.verifyGain(string, I_NAME, tuneSnapshot.getI(), tuneSnapshot2.getI(), string2 + " I mismatch");
        this.verifyGain(string, D_NAME, tuneSnapshot.getD(), tuneSnapshot2.getD(), string2 + " D mismatch");
    }

    private void verifyGain(String string, String string2, double d, double d2, String string3) throws Exception {
        ScalarDefinition scalarDefinition = this.readScalar(string, string2);
        if (!IdlePidRamWriteCoordinator.sameAtPrecision(d, d2, scalarDefinition.decimalPlaces)) {
            throw new IllegalStateException(string3 + ": expected " + d + ", read back " + d2 + ".");
        }
    }

    private boolean sameSnapshot(String string, TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2) throws Exception {
        return this.sameGain(string, P_NAME, tuneSnapshot.getP(), tuneSnapshot2.getP()) && this.sameGain(string, I_NAME, tuneSnapshot.getI(), tuneSnapshot2.getI()) && this.sameGain(string, D_NAME, tuneSnapshot.getD(), tuneSnapshot2.getD());
    }

    private boolean sameGain(String string, String string2, double d, double d2) throws Exception {
        ScalarDefinition scalarDefinition = this.readScalar(string, string2);
        return IdlePidRamWriteCoordinator.sameAtPrecision(d, d2, scalarDefinition.decimalPlaces);
    }

    private static String changedGain(TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2) {
        int n = 0;
        String string = null;
        if (!IdlePidRamWriteCoordinator.approximately(tuneSnapshot.getP(), tuneSnapshot2.getP())) {
            ++n;
            string = "P";
        }
        if (!IdlePidRamWriteCoordinator.approximately(tuneSnapshot.getI(), tuneSnapshot2.getI())) {
            ++n;
            string = "I";
        }
        if (!IdlePidRamWriteCoordinator.approximately(tuneSnapshot.getD(), tuneSnapshot2.getD())) {
            ++n;
            string = "D";
        }
        return n == 1 ? string : null;
    }

    private static TuneSnapshot replaceGain(TuneSnapshot tuneSnapshot, String string, double d, String string2) {
        double d2 = tuneSnapshot.getP();
        double d3 = tuneSnapshot.getI();
        double d4 = tuneSnapshot.getD();
        if ("P".equals(string)) {
            d2 = d;
        } else if ("I".equals(string)) {
            d3 = d;
        } else if ("D".equals(string)) {
            d4 = d;
        }
        return new TuneSnapshot(d2, d3, d4, string2, false);
    }

    private static String parameterName(String string) {
        if ("P".equals(string)) {
            return P_NAME;
        }
        if ("I".equals(string)) {
            return I_NAME;
        }
        if ("D".equals(string)) {
            return D_NAME;
        }
        throw new IllegalArgumentException("Unsupported gain " + string + ".");
    }

    private static double gainValue(TuneSnapshot tuneSnapshot, String string) {
        if ("P".equals(string)) {
            return tuneSnapshot.getP();
        }
        if ("I".equals(string)) {
            return tuneSnapshot.getI();
        }
        if ("D".equals(string)) {
            return tuneSnapshot.getD();
        }
        throw new IllegalArgumentException("Unsupported gain " + string + ".");
    }

    private static double normalize(double d, int n) {
        double d2 = Math.pow(10.0, Math.max(0, Math.min(9, n)));
        return (double)Math.round(d * d2) / d2;
    }

    private static boolean sameAtPrecision(double d, double d2, int n) {
        double d3 = 0.5 / Math.pow(10.0, Math.max(0, Math.min(9, n))) + 1.0E-12;
        return Math.abs(d - d2) <= d3;
    }

    private static boolean approximately(double d, double d2) {
        return IdlePidRamWriteCoordinator.finite(d) && IdlePidRamWriteCoordinator.finite(d2) && Math.abs(d - d2) <= 1.0E-7;
    }

    private static boolean finite(double d) {
        return !Double.isNaN(d) && !Double.isInfinite(d);
    }

    private static void requireConfiguration(String string) {
        if (string == null || string.trim().isEmpty()) {
            throw new IllegalArgumentException("configurationName cannot be empty");
        }
    }

    private static void requireComplete(String string, TuneSnapshot tuneSnapshot) {
        if (tuneSnapshot == null || !tuneSnapshot.isComplete()) {
            throw new IllegalArgumentException(string + " are incomplete.");
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

    private static final class VerifiedScalar {
        final double requested;
        final double readback;

        VerifiedScalar(double d, double d2) {
            this.requested = d;
            this.readback = d2;
        }
    }

    public static final class WriteResult {
        private final String status;
        private final String changedGain;
        private final String message;
        private final double requested;
        private final double readback;
        private final TuneSnapshot before;
        private final TuneSnapshot after;

        WriteResult(String string, String string2, double d, double d2, TuneSnapshot tuneSnapshot, TuneSnapshot tuneSnapshot2, String string3) {
            this.status = string;
            this.changedGain = string2;
            this.requested = d;
            this.readback = d2;
            this.before = tuneSnapshot;
            this.after = tuneSnapshot2;
            this.message = string3;
        }

        public String getStatus() {
            return this.status;
        }

        public String getChangedGain() {
            return this.changedGain;
        }

        public double getRequested() {
            return this.requested;
        }

        public double getReadback() {
            return this.readback;
        }

        public TuneSnapshot getBefore() {
            return this.before;
        }

        public TuneSnapshot getAfter() {
            return this.after;
        }

        public String getMessage() {
            return this.message;
        }
    }
}

