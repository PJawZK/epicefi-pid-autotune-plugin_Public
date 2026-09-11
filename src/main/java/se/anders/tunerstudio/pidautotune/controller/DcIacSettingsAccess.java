package se.anders.tunerstudio.pidautotune.controller;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.ControllerParameter;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;

/** Read-only access to DC-IAC configuration values used by integrated evidence gating. */
public final class DcIacSettingsAccess {
    private final ControllerParameterServer parameterServer;

    public DcIacSettingsAccess(ControllerAccess access) {
        if (access == null) throw new IllegalArgumentException("controllerAccess cannot be null");
        this.parameterServer = access.getControllerParameterServer();
    }

    public Snapshot read(String configurationName) throws Exception {
        double minimumPosition = scalar(configurationName, "dcIdleMinimumPosition");
        double maximumPosition = scalar(configurationName, "dcIdleMaximumPosition");
        Boolean paused = optionalBool(configurationName, "pauseEtbControl");
        Double pFactor = optionalScalar(configurationName, "dcIdlePositionPid_pFactor");
        Double iFactor = optionalScalar(configurationName, "dcIdlePositionPid_iFactor");
        Double dFactor = optionalScalar(configurationName, "dcIdlePositionPid_dFactor");
        Double fan1ExtraIdle = optionalScalar(configurationName, "fan1ExtraIdle");
        Double fan1ExtraIdleRpmTarget = optionalScalar(configurationName, "fan1ExtraIdleRpmTarget");
        return new Snapshot(minimumPosition, maximumPosition, paused, pFactor, iFactor, dFactor,
                fan1ExtraIdle, fan1ExtraIdleRpmTarget);
    }

    private double scalar(String configurationName, String name) throws Exception {
        ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, name);
        if (parameter == null) throw new IllegalStateException("Required parameter not found: " + name);
        return parameter.getScalarValue();
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

    private Boolean optionalBool(String configurationName, String name) {
        try {
            ControllerParameter parameter = parameterServer.getControllerParameter(configurationName, name);
            if (parameter == null) return null;
            String text = parameter.getStringValue();
            if (text != null) {
                String normalized = text.trim().toLowerCase(java.util.Locale.US);
                if ("true".equals(normalized) || "on".equals(normalized) || "enabled".equals(normalized) || "1".equals(normalized)) return Boolean.TRUE;
                if ("false".equals(normalized) || "off".equals(normalized) || "disabled".equals(normalized) || "0".equals(normalized)) return Boolean.FALSE;
            }
            try {
                return Math.abs(parameter.getScalarValue()) > 0.0001 ? Boolean.TRUE : Boolean.FALSE;
            } catch (RuntimeException ignored) {
                return null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static final class Snapshot {
        private final double minimumPosition;
        private final double maximumPosition;
        private final Boolean motorControlPaused;
        private final Double pFactor;
        private final Double iFactor;
        private final Double dFactor;
        private final Double fan1ExtraIdle;
        private final Double fan1ExtraIdleRpmTarget;

        public Snapshot(double minimumPosition, double maximumPosition, Boolean motorControlPaused) {
            this(minimumPosition, maximumPosition, motorControlPaused, null, null, null, null, null);
        }

        public Snapshot(
                double minimumPosition,
                double maximumPosition,
                Boolean motorControlPaused,
                Double pFactor,
                Double iFactor,
                Double dFactor) {
            this(minimumPosition, maximumPosition, motorControlPaused, pFactor, iFactor, dFactor, null, null);
        }

        public Snapshot(
                double minimumPosition,
                double maximumPosition,
                Boolean motorControlPaused,
                Double pFactor,
                Double iFactor,
                Double dFactor,
                Double fan1ExtraIdle,
                Double fan1ExtraIdleRpmTarget) {
            this.minimumPosition = minimumPosition;
            this.maximumPosition = maximumPosition;
            this.motorControlPaused = motorControlPaused;
            this.pFactor = pFactor;
            this.iFactor = iFactor;
            this.dFactor = dFactor;
            this.fan1ExtraIdle = fan1ExtraIdle;
            this.fan1ExtraIdleRpmTarget = fan1ExtraIdleRpmTarget;
        }

        public double getMinimumPosition() { return minimumPosition; }
        public double getMaximumPosition() { return maximumPosition; }
        public boolean isMotorControlPauseKnown() { return motorControlPaused != null; }
        public boolean isMotorControlPaused() { return Boolean.TRUE.equals(motorControlPaused); }
        public boolean arePidGainsKnown() { return pFactor != null && iFactor != null && dFactor != null; }
        public double getPFactor() { return pFactor == null ? Double.NaN : pFactor.doubleValue(); }
        public double getIFactor() { return iFactor == null ? Double.NaN : iFactor.doubleValue(); }
        public double getDFactor() { return dFactor == null ? Double.NaN : dFactor.doubleValue(); }
        public boolean isFan1ExtraIdleKnown() { return fan1ExtraIdle != null; }
        public double getFan1ExtraIdle() { return fan1ExtraIdle == null ? Double.NaN : fan1ExtraIdle.doubleValue(); }
        public boolean isFan1ExtraIdleRpmTargetKnown() { return fan1ExtraIdleRpmTarget != null; }
        public double getFan1ExtraIdleRpmTarget() { return fan1ExtraIdleRpmTarget == null ? Double.NaN : fan1ExtraIdleRpmTarget.doubleValue(); }
    }
}
