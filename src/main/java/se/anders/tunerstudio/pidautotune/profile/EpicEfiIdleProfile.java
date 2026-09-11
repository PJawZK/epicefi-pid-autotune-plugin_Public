package se.anders.tunerstudio.pidautotune.profile;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Known EPICEFI/rusEFI idle-control names from the active controller definition. */
public final class EpicEfiIdleProfile {
    private EpicEfiIdleProfile() { }

    public static final String PROFILE_NAME = "Idle speed PID";

    public static final class Mapping {
        private final String name;
        private final String purpose;

        public Mapping(String name, String purpose) {
            this.name = name;
            this.purpose = purpose;
        }

        public String getName() { return name; }
        public String getPurpose() { return purpose; }
    }

    public static List<Mapping> parameters() {
        return Collections.unmodifiableList(Arrays.asList(
                new Mapping("idleMode", "Confirms that open-loop plus closed-loop idle control is enabled."),
                new Mapping("idleRpmPid_pFactor", "Proportional gain used by the idle RPM controller."),
                new Mapping("idleRpmPid_iFactor", "Integral gain used by the idle RPM controller."),
                new Mapping("idleRpmPid_dFactor", "Derivative gain used by the idle RPM controller."),
                new Mapping("idleRpmPid_offset", "Linear PID output offset/feed-forward value."),
                new Mapping("idleRpmPid_periodMs", "Configured PID calculation period, where explicitly set."),
                new Mapping("idleRpmPid_minValue", "Minimum closed-loop correction authority."),
                new Mapping("idleRpmPid_maxValue", "Maximum closed-loop correction authority."),
                new Mapping("idlerpmpid_iTermMin", "Minimum integral-term clamp."),
                new Mapping("idlerpmpid_iTermMax", "Maximum integral-term clamp."),
                new Mapping("idlePidRpmDeadZone", "RPM error band where closed-loop correction is suppressed."),
                new Mapping("idlePidRpmUpperLimit", "RPM margin above target that still counts as idle."),
                new Mapping("idlePidDeactivationTpsThreshold", "TPS/driver-intent threshold below which idle is allowed."),
                new Mapping("idlePidActivationTime", "Delay before the closed-loop idle controller is activated."),
                new Mapping("maxIdleVss", "Maximum vehicle speed at which idle control is allowed."),
                new Mapping("idleReturnTargetRamp", "Enables a ramped target during return-to-idle."),
                new Mapping("idleReturnTargetRampDuration", "Duration of the return-to-idle target ramp."),
                new Mapping("cltIdleRpmBins", "Coolant-temperature axis for base idle target."),
                new Mapping("cltIdleRpm", "Base idle target RPM by coolant temperature."),
                new Mapping("fan1ExtraIdle", "Additional base idle-valve position while fan 1 is active."),
                new Mapping("fan1ExtraIdleRpmTarget", "Idle target used while fan 1 is active."),
                new Mapping("acIdleExtraOffset", "Additional base idle-valve position while A/C idle-up is active."),
                new Mapping("acIdleRpmTarget", "Idle target used while A/C idle-up is active."),
                new Mapping("idle_solenoidFrequency", "PWM frequency used by the idle solenoid."),
                new Mapping("useIdleTimingPidControl", "Shows whether the separate idle ignition PID controller is enabled.")
        ));
    }

    public static List<Mapping> outputChannels() {
        return Collections.unmodifiableList(Arrays.asList(
                new Mapping("RPMValue", "Measured engine speed; primary process value (logged with the caption RPM)."),
                new Mapping("idleTarget", "Effective idle target after temperature and idle-up corrections."),
                new Mapping("baseIdlePosition", "Open-loop/base idle-valve position before closed-loop correction."),
                new Mapping("currentIdlePosition", "Final commanded idle-valve position."),
                new Mapping("idleClosedLoop", "Closed-loop idle correction contribution."),
                new Mapping("isIdleClosedLoop", "True when closed-loop idle control is active."),
                new Mapping("idleState", "Current idle state-machine state."),
                new Mapping("isIdling", "True when the idle state machine considers the engine to be idling."),
                new Mapping("idleStatus_pTerm", "Reported proportional controller term."),
                new Mapping("idleStatus_iTerm", "Reported integral controller term."),
                new Mapping("idleStatus_dTerm", "Reported derivative controller term."),
                new Mapping("idleStatus_output", "Reported combined PID output; may be zero on this firmware."),
                new Mapping("idleStatus_error", "Controller-internal filtered/scaled error signal."),
                new Mapping("idleTargetError", "Idle target error diagnostic channel."),
                new Mapping("TPSValue", "Throttle position used to reject non-idle samples (logged with the caption TPS)."),
                new Mapping("DriverThrottleIntent", "Driver-intent channel used on some throttle configurations."),
                new Mapping("coolant", "Coolant temperature used to select the base target RPM (logged with the caption CLT)."),
                new Mapping("MAPValue", "Manifold pressure, useful for rejecting throttle/load disturbances and comparing idle load."),
                new Mapping("VBatt", "Electrical-system voltage; useful for load/disturbance analysis (logged with the caption Batt V)."),
                new Mapping("fan1m_state", "Fan state used to identify a repeatable idle-load disturbance (logged with the caption fan1On)."),
                new Mapping("vehicleSpeedKph", "Vehicle speed used to reject moving/coasting samples."),
                new Mapping("dfcoActive", "DFCO state used to reject fuel-cut recovery from live or logged idle analysis.")
        ));
    }
}
