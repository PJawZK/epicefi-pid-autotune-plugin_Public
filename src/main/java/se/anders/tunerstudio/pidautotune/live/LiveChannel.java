package se.anders.tunerstudio.pidautotune.live;

/** Logical live output channels used by the guided idle-capture workflow. */
public enum LiveChannel {
    RPM("RPM", true, "RPMValue", "RPM"),
    IDLE_TARGET("Idle target", true, "idleTarget"),
    BASE_IDLE_POSITION("Base idle position", false, "baseIdlePosition"),
    CURRENT_IDLE_POSITION("Commanded idle position", true, "currentIdlePosition"),
    CLOSED_LOOP_ACTIVE("Closed-loop active", true, "isIdleClosedLoop"),
    IDLING("Idling state", false, "isIdling"),
    P_TERM("P term", false, "idleStatus_pTerm"),
    I_TERM("I term", false, "idleStatus_iTerm"),
    D_TERM("D term", false, "idleStatus_dTerm"),
    PID_OUTPUT("PID output", false, "idleStatus_output", "idleClosedLoop"),
    TPS("TPS", true, "TPSValue"),
    CLT("Coolant temperature", true, "coolant"),
    MAP("MAP", false, "MAPValue"),
    BATTERY("Battery voltage", false, "VBatt"),
    FAN1("Fan 1 state", true, "fan1m_state"),
    VEHICLE_SPEED("Vehicle speed", false, "vehicleSpeedKph"),
    DFCO("DFCO active", false, "dfcoActive");

    private final String displayName;
    private final boolean required;
    private final String[] apiNames;

    LiveChannel(String displayName, boolean required, String... apiNames) {
        this.displayName = displayName;
        this.required = required;
        this.apiNames = apiNames;
    }

    public String getDisplayName() { return displayName; }
    public boolean isRequired() { return required; }
    public String[] getApiNames() { return apiNames.clone(); }
}
