package se.anders.tunerstudio.pidautotune.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IdleAnalysisResult {
    private final List<IdleEvent> events;
    private final List<String> warnings;
    private final double closedLoopSeconds;
    private final boolean derivedCorrectionUsed;

    public IdleAnalysisResult(
            List<IdleEvent> events,
            List<String> warnings,
            double closedLoopSeconds,
            boolean derivedCorrectionUsed) {
        this.events = Collections.unmodifiableList(new ArrayList<IdleEvent>(events));
        this.warnings = Collections.unmodifiableList(new ArrayList<String>(warnings));
        this.closedLoopSeconds = closedLoopSeconds;
        this.derivedCorrectionUsed = derivedCorrectionUsed;
    }

    public List<IdleEvent> getEvents() { return events; }
    public List<String> getWarnings() { return warnings; }
    public double getClosedLoopSeconds() { return closedLoopSeconds; }
    public boolean isDerivedCorrectionUsed() { return derivedCorrectionUsed; }
}
