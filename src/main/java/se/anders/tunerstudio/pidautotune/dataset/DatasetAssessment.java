package se.anders.tunerstudio.pidautotune.dataset;

import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative readiness result for a manually curated multi-log event dataset. */
public final class DatasetAssessment {
    public static final String INSUFFICIENT = "Insufficient";
    public static final String DIAGNOSTIC_ONLY = "Diagnostic only";
    public static final String CONSERVATIVE_READY = "Suitable for conservative recommendation";

    private final String level;
    private final String summary;
    private final String details;
    private final int includedEvents;
    private final int includedTransientEvents;
    private final int includedSteadyEvents;
    private final int loadedLogs;
    private final String bestGroup;

    private DatasetAssessment(
            String level,
            String summary,
            String details,
            int includedEvents,
            int includedTransientEvents,
            int includedSteadyEvents,
            int loadedLogs,
            String bestGroup) {
        this.level = level;
        this.summary = summary;
        this.details = details;
        this.includedEvents = includedEvents;
        this.includedTransientEvents = includedTransientEvents;
        this.includedSteadyEvents = includedSteadyEvents;
        this.loadedLogs = loadedLogs;
        this.bestGroup = bestGroup == null ? "" : bestGroup;
    }

    public static DatasetAssessment assess(List<DatasetEvent> rows, int loadedLogCount) {
        Map<String, GroupCounts> responseGroups = new HashMap<String, GroupCounts>();
        Map<String, Integer> steadyByOperatingGroup = new HashMap<String, Integer>();
        Set<String> includedLogIds = new HashSet<String>();
        int included = 0;
        int transients = 0;
        int steady = 0;
        int blockedByMotionIntegrity = 0;
        int blockedByGains = 0;

        if (rows != null) {
            for (DatasetEvent row : rows) {
                if (!row.isIncluded() || !row.isSelectable()) continue;
                included++;
                includedLogIds.add(row.getAnalyzedLog().getIdentity());
                IdleEvent event = row.getEvent();
                if (event.isFutureGainEligible()) transients++;
                else if (event.isSteadyHold()) steady++;

                if (!row.getTuneSnapshot().isComplete()) blockedByGains++;
                if (row.getIntegrity().isRecommendationBlocked()) blockedByMotionIntegrity++;
                if (!row.isRecommendationGateEligible()) continue;

                if (event.isFutureGainEligible()) {
                    GroupCounts counts = responseGroups.get(row.getResponseGroup());
                    if (counts == null) {
                        counts = new GroupCounts(row.getResponseGroup(), row.getOperatingGroup());
                        responseGroups.put(row.getResponseGroup(), counts);
                    }
                    counts.total++;
                    if ("Good for analysis".equals(event.getQuality())) counts.good++;
                    counts.logIds.add(row.getAnalyzedLog().getIdentity());
                } else if (event.isSteadyHold()) {
                    Integer count = steadyByOperatingGroup.get(row.getOperatingGroup());
                    steadyByOperatingGroup.put(row.getOperatingGroup(), count == null ? 1 : count + 1);
                }
            }
        }

        GroupCounts best = null;
        for (GroupCounts candidate : responseGroups.values()) {
            if (best == null
                    || candidate.total > best.total
                    || (candidate.total == best.total && candidate.good > best.good)
                    || (candidate.total == best.total && candidate.good == best.good
                        && candidate.name.compareToIgnoreCase(best.name) < 0)) {
                best = candidate;
            }
        }

        String level;
        String summary;
        StringBuilder details = new StringBuilder();
        if (included == 0) {
            level = INSUFFICIENT;
            summary = "No eligible events are included.";
            details.append("Load one or more logs, then include Good or Usable transient and steady-state rows.");
        } else if (best == null) {
            level = DIAGNOSTIC_ONLY;
            summary = steady + " steady-state event(s) and " + transients
                    + " transient event(s) are included, but none form a gate-eligible transient group.";
            details.append("A recommendation group requires a complete per-log P/I/D snapshot and an accepted per-log VSS/motion basis. ")
                    .append("Steady holds remain useful for target-tracking and controller-activity diagnostics.");
        } else {
            int compatibleSteady = value(steadyByOperatingGroup.get(best.operatingGroup));
            int remaining = Math.max(0, 3 - best.total);
            if (best.total >= 3 && best.good >= 2 && compatibleSteady >= 1) {
                level = CONSERVATIVE_READY;
                summary = best.total + " compatible transient event(s), including " + best.good
                        + " Good event(s), plus " + compatibleSteady + " compatible steady hold(s).";
                details.append("The dataset has met the minimum gate for the conservative recommendation model. ")
                        .append("Every counted event has a complete per-log gain snapshot and an acceptable VSS/motion basis. ")
                        .append("Version 0.4.3 may now pass this group to the confidence-gated conservative model; no gain is ever written or burned automatically.");
            } else {
                level = DIAGNOSTIC_ONLY;
                summary = best.total + " compatible gate-eligible transient event(s) are included in the strongest group.";
                if (remaining > 0) {
                    details.append("Collect ").append(remaining).append(" more compatible transient event(s). ");
                }
                if (best.good < 2) {
                    details.append("At least two of the three transient events should be graded Good for analysis. ");
                }
                if (compatibleSteady < 1) {
                    details.append("Add one compatible Good or Usable steady-idle hold. ");
                }
                details.append("The current data remains useful for diagnostics and test planning.");
            }
        }

        details.append("\n\nStrongest response group: ")
                .append(best == null ? "None" : best.name)
                .append("\nIncluded events: ").append(included)
                .append(" (transient ").append(transients).append(", steady ").append(steady).append(")")
                .append(" across ").append(includedLogIds.size()).append(" included log(s); ")
                .append(loadedLogCount).append(" log(s) loaded.")
                .append("\nReadiness-blocked included events: ")
                .append(blockedByMotionIntegrity).append(" due to VSS/motion integrity; ")
                .append(blockedByGains).append(" due to incomplete P/I/D snapshots.")
                .append("\nEach imported log stores its own gain snapshot and VSS source. Correct the gain snapshot when the displayed tune did not match the recorded tune, and explicitly mark logs recorded with unavailable VSS as manually stationary or motion unknown.");

        return new DatasetAssessment(
                level,
                summary,
                details.toString(),
                included,
                transients,
                steady,
                loadedLogCount,
                best == null ? "" : best.name);
    }

    public String getLevel() { return level; }
    public String getSummary() { return summary; }
    public String getDetails() { return details; }
    public int getIncludedEvents() { return includedEvents; }
    public int getIncludedTransientEvents() { return includedTransientEvents; }
    public int getIncludedSteadyEvents() { return includedSteadyEvents; }
    public int getLoadedLogs() { return loadedLogs; }
    public String getBestGroup() { return bestGroup; }

    private static int value(Integer value) { return value == null ? 0 : value.intValue(); }

    private static final class GroupCounts {
        private final String name;
        private final String operatingGroup;
        private int total;
        private int good;
        private final Set<String> logIds = new HashSet<String>();

        private GroupCounts(String name, String operatingGroup) {
            this.name = name;
            this.operatingGroup = operatingGroup;
        }
    }
}
