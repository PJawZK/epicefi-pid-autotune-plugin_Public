package se.anders.tunerstudio.pidautotune.profile;

import java.util.Arrays;

import static se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition.MappingRole;

/**
 * EPICEFI feedback DC idle-valve position loop.
 *
 * This is the fast inner actuator loop:
 * requested idle-valve position -> position PID + DC-idle bias -> signed H-bridge duty.
 * It is intentionally independent of the slower engine-speed idle PID.
 */
public final class DcIacPositionProfile {
    private DcIacPositionProfile() { }

    public static final String PROFILE_ID = "dc-iac-position";
    public static final String PROFILE_NAME = "DC IAC position PID";

    public static final ControllerProfileDefinition DEFINITION = new ControllerProfileDefinition(
            PROFILE_ID,
            PROFILE_NAME,
            ControllerProfileDefinition.TuningStrategy.PID,
            Arrays.asList(
                    parameter("dcIdlePositionPid_pFactor",
                            "Proportional gain for the feedback DC idle-valve position controller.",
                            MappingRole.PROPORTIONAL_GAIN, true),
                    parameter("dcIdlePositionPid_iFactor",
                            "Integral gain for the feedback DC idle-valve position controller.",
                            MappingRole.INTEGRAL_GAIN, true),
                    parameter("dcIdlePositionPid_dFactor",
                            "Derivative gain for the feedback DC idle-valve position controller.",
                            MappingRole.DERIVATIVE_GAIN, true),
                    parameter("dcIdlePositionPid_offset",
                            "Constant duty feed-forward offset inside the position PID definition; observe separately from the bias curve.",
                            MappingRole.PID_OFFSET, true),
                    parameter("dcIdlePositionPid_periodMs",
                            "Configured PID period metadata. The controller also reports/uses its actual loop timing; retain this for definition diagnostics.",
                            MappingRole.PID_PERIOD, false),
                    parameter("dcIdlePositionPid_minValue",
                            "Minimum signed PID correction/output authority.",
                            MappingRole.OUTPUT_MINIMUM, true),
                    parameter("dcIdlePositionPid_maxValue",
                            "Maximum signed PID correction/output authority.",
                            MappingRole.OUTPUT_MAXIMUM, true),
                    parameter("dcIdle_iTermMin",
                            "Minimum integral-term clamp for the DC idle position controller.",
                            MappingRole.INTEGRAL_MINIMUM, true),
                    parameter("dcIdle_iTermMax",
                            "Maximum integral-term clamp for the DC idle position controller.",
                            MappingRole.INTEGRAL_MAXIMUM, true),
                    parameter("dcIdleMinimumPosition",
                            "Lowest valve-position target the DC idle controller is allowed to request.",
                            MappingRole.PLANT_MINIMUM, true),
                    parameter("dcIdleMaximumPosition",
                            "Highest valve-position target the DC idle controller is allowed to request.",
                            MappingRole.PLANT_MAXIMUM, true),
                    parameter("dcIdleBiasBins",
                            "Target-position axis for the DC idle motor-duty feed-forward curve.",
                            MappingRole.FEED_FORWARD_AXIS, true),
                    parameter("dcIdleBiasValues",
                            "Motor-duty feed-forward curve used before/with closed-loop position correction.",
                            MappingRole.FEED_FORWARD_VALUES, true),
                    parameter("idlePositionChannel",
                            "Configured analog input for the feedback idle-valve position sensor.",
                            MappingRole.CONFIGURATION, true),
                    parameter("idlePositionMin",
                            "Calibrated sensor voltage corresponding to the low/closed end of idle-valve travel.",
                            MappingRole.CONFIGURATION, true),
                    parameter("idlePositionMax",
                            "Calibrated sensor voltage corresponding to the high/open end of idle-valve travel.",
                            MappingRole.CONFIGURATION, true),
                    parameter("dcIdlePositionFaultTimeoutMs",
                            "Time a bad position-sensor condition may persist before the DC idle motor is faulted.",
                            MappingRole.SAFETY_GUARD, true),
                    parameter("dcIdleJamDetectThreshold",
                            "Position-error threshold used by DC idle jam detection.",
                            MappingRole.SAFETY_GUARD, true),
                    parameter("dcIdleJamTimeout",
                            "Duration excessive position error may persist before jam protection latches.",
                            MappingRole.SAFETY_GUARD, true),
                    parameter("pauseEtbControl",
                            "Legacy/shared firmware motor-pause flag. Optional context for DC IAC: if exposed and active, evidence is invalid, but this ETB-named parameter is not required for a DC-IAC-only configuration.",
                            MappingRole.SAFETY_GUARD, false),
                    parameter("fan1ExtraIdle",
                            "Optional fan-1 idle-position adder. A fan transition is a load disturbance plus position-target step and is context-only for PID identification.",
                            MappingRole.CONFIGURATION, false),
                    parameter("fan1ExtraIdleRpmTarget",
                            "Optional fan-1 idle RPM target used as disturbance context; it is not part of the fast DC-IAC position loop.",
                            MappingRole.CONFIGURATION, false)
            ),
            Arrays.asList(
                    output("dcIdleTarget",
                            "Requested DC idle-valve position; setpoint for the fast inner position loop.",
                            MappingRole.TARGET, true),
                    output("idlePositionSensor",
                            "Measured feedback DC idle-valve position.",
                            MappingRole.PROCESS_VALUE, true),
                    output("dcIdleDutyCycle",
                            "Final signed H-bridge motor duty commanded by the DC idle controller.",
                            MappingRole.CONTROLLER_OUTPUT, true),
                    output("dcIdlePositionStatus_pTerm",
                            "Reported proportional contribution from the DC idle position PID.",
                            MappingRole.P_TERM, true),
                    output("dcIdlePositionStatus_iTerm",
                            "Reported integral contribution from the DC idle position PID.",
                            MappingRole.I_TERM, true),
                    output("dcIdlePositionStatus_dTerm",
                            "Reported derivative contribution from the DC idle position PID.",
                            MappingRole.D_TERM, true),
                    output("dcIdlePositionStatus_output",
                            "Reported combined closed-loop PID contribution/state.",
                            MappingRole.PID_OUTPUT, true),
                    output("dcIdlePositionStatus_error",
                            "Controller-reported valve-position error.",
                            MappingRole.ERROR, true),
                    output("dcIdlePositionStatus_resetCounter",
                            "PID reset counter; a change splits/invalidates a continuous response event.",
                            MappingRole.RESET_COUNTER, true),
                    output("dcIdleFaultCode",
                            "Local DC idle fault status. Any non-zero fault blocks tuning use of the sample/event.",
                            MappingRole.FAULT_STATE, true),
                    output("RPMValue",
                            "Engine speed context; separates engine-off actuator characterization from running tests.",
                            MappingRole.CONTEXT, false),
                    output("VBatt",
                            "Battery voltage context; motor authority and bias can vary with supply voltage.",
                            MappingRole.CONTEXT, false),
                    output("coolant",
                            "Coolant-temperature context for running-idle captures.",
                            MappingRole.CONTEXT, false),
                    output("currentIdlePosition",
                            "Outer idle controller's final requested idle position; should agree with the inner-loop target apart from DC-IAC clamping.",
                            MappingRole.CONTEXT, false),
                    output("fan1On",
                            "Optional fan-1 state. A transition marks a combined electrical/engine-load disturbance plus idle-position adder and must not be used as a pure quantitative PID-ID step.",
                            MappingRole.CONTEXT, false),
                    output("isIdleClosedLoop",
                            "Outer idle RPM closed-loop state. Context only; the DC-IAC inner-loop tuner must not depend on it.",
                            MappingRole.CONTEXT, false),
                    output("isIdling",
                            "Idle state-machine context. The DC-IAC inner-loop tuner may characterize the actuator outside the Idling phase when safe.",
                            MappingRole.CONTEXT, false)
            ));

    private static ControllerProfileDefinition.Mapping parameter(
            String name,
            String purpose,
            MappingRole role,
            boolean required) {
        return new ControllerProfileDefinition.Mapping(name, purpose, role, required);
    }

    private static ControllerProfileDefinition.Mapping output(
            String name,
            String purpose,
            MappingRole role,
            boolean required) {
        return new ControllerProfileDefinition.Mapping(name, purpose, role, required);
    }
}
