package se.anders.tunerstudio.pidautotune.log;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Logical channels used by the idle log analyser and their known TunerStudio captions. */
public enum LogChannelDefinition {
    TIME("time", "Time", true, "Time"),
    RPM("rpm", "Engine speed", true, "RPM", "RPMValue"),
    IDLE_TARGET("idleTarget", "Idle target", true, "Idle: Target RPM", "idleTarget"),
    IDLE_TARGET_BASE("idleTargetBase", "Base idle target", false,
            "Idle: Target RPM base", "baseIdleTarget", "idleTargetBase"),
    BASE_IDLE_POSITION("baseIdlePosition", "Base idle position", false,
            "idle: base value", "baseIdlePosition"),
    CURRENT_IDLE_POSITION("currentIdlePosition", "Commanded idle position", false,
            "Idle: Position", "currentIdlePosition"),
    CLOSED_LOOP_ACTIVE("closedLoopActive", "Closed-loop active", false,
            "Idle: Closed loop active", "isIdleClosedLoop"),
    IDLING("idling", "Idling state", false,
            "Idle: idling", "isIdling"),
    COASTING("coasting", "Coasting state", false,
            "Idle: coasting", "isIacTableForCoasting"),
    IDLE_STATE("idleState", "Idle state", false, "idleState"),
    P_TERM("pTerm", "PID P term", false, "idleStatus_pTerm"),
    I_TERM("iTerm", "PID I term", false, "idleStatus_iTerm"),
    D_TERM("dTerm", "PID D term", false, "idleStatus_dTerm"),
    PID_OUTPUT("pidOutput", "Reported PID output", false, "idleStatus_output", "idleClosedLoop"),
    PID_ERROR("pidError", "Controller error", false, "idleStatus_error", "Idle: Target ERROR", "idleTargetError"),
    TPS("tps", "Throttle position", false, "TPS", "TPSValue"),
    CLT("clt", "Coolant temperature", false, "CLT", "coolant"),
    MAP("map", "Manifold pressure", false, "MAP", "MAPValue"),
    BATTERY("battery", "Battery voltage", false, "Batt V", "VBatt"),
    VEHICLE_SPEED("vehicleSpeed", "Vehicle speed", false,
            "Vehicle Speed", "vehicleSpeedKph", "Wheel Speed Rear AVG", "Wheel Speed Front AVG"),
    FAN1("fan1", "Fan 1 state", false, "fan1On", "fan1m_state"),
    DFCO("dfco", "DFCO active", false, "dfcoActive");

    private final String key;
    private final String displayName;
    private final boolean required;
    private final List<String> aliases;

    LogChannelDefinition(String key, String displayName, boolean required, String... aliases) {
        this.key = key;
        this.displayName = displayName;
        this.required = required;
        this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
    }

    public String getKey() { return key; }
    public String getDisplayName() { return displayName; }
    public boolean isRequired() { return required; }
    public List<String> getAliases() { return aliases; }
}
