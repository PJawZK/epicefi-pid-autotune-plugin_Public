package se.anders.tunerstudio.pidautotune.profile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only description of one controller tuning profile.
 *
 * The shared plugin can use this metadata for discovery/diagnostics while each
 * controller-specific analyser remains free to implement its own capture,
 * readiness, response metrics, and recommendation strategy.
 */
public final class ControllerProfileDefinition {
    public enum TuningStrategy {
        PID,
        FEED_FORWARD
    }

    public enum MappingRole {
        CONFIGURATION,
        PROPORTIONAL_GAIN,
        INTEGRAL_GAIN,
        DERIVATIVE_GAIN,
        PID_OFFSET,
        PID_PERIOD,
        OUTPUT_MINIMUM,
        OUTPUT_MAXIMUM,
        INTEGRAL_MINIMUM,
        INTEGRAL_MAXIMUM,
        PLANT_MINIMUM,
        PLANT_MAXIMUM,
        FEED_FORWARD_AXIS,
        FEED_FORWARD_VALUES,
        TARGET,
        PROCESS_VALUE,
        CONTROLLER_OUTPUT,
        P_TERM,
        I_TERM,
        D_TERM,
        PID_OUTPUT,
        ERROR,
        RESET_COUNTER,
        FAULT_STATE,
        SAFETY_GUARD,
        CONTEXT
    }

    public static final class Mapping {
        private final String name;
        private final String purpose;
        private final MappingRole role;
        private final boolean required;

        public Mapping(String name, String purpose, MappingRole role, boolean required) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("mapping name cannot be empty");
            }
            if (role == null) {
                throw new IllegalArgumentException("mapping role cannot be null");
            }
            this.name = name;
            this.purpose = purpose == null ? "" : purpose;
            this.role = role;
            this.required = required;
        }

        public String getName() { return name; }
        public String getPurpose() { return purpose; }
        public MappingRole getRole() { return role; }
        public boolean isRequired() { return required; }
    }

    private final String id;
    private final String displayName;
    private final TuningStrategy tuningStrategy;
    private final List<Mapping> parameters;
    private final List<Mapping> outputChannels;

    public ControllerProfileDefinition(
            String id,
            String displayName,
            TuningStrategy tuningStrategy,
            List<Mapping> parameters,
            List<Mapping> outputChannels) {
        if (id == null || id.trim().isEmpty()) {
            throw new IllegalArgumentException("profile id cannot be empty");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("profile display name cannot be empty");
        }
        if (tuningStrategy == null) {
            throw new IllegalArgumentException("tuning strategy cannot be null");
        }
        this.id = id;
        this.displayName = displayName;
        this.tuningStrategy = tuningStrategy;
        this.parameters = immutableCopy(parameters);
        this.outputChannels = immutableCopy(outputChannels);
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }
    public TuningStrategy getTuningStrategy() { return tuningStrategy; }
    public List<Mapping> getParameters() { return parameters; }
    public List<Mapping> getOutputChannels() { return outputChannels; }

    private static List<Mapping> immutableCopy(List<Mapping> mappings) {
        if (mappings == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(new ArrayList<Mapping>(mappings));
    }
}
