package se.anders.tunerstudio.pidautotune.live;

/** User-visible state of the live guided-capture state machine. */
public enum LiveCaptureState {
    STOPPED,
    WAITING_FOR_STABLE_IDLE,
    READY_TO_REV,
    REV_IN_PROGRESS,
    WAITING_FOR_IDLE_CONTROL,
    MEASURING_RECOVERY,
    CONFIRMING_STABLE_IDLE
}
