package se.anders.tunerstudio.pidautotune.dataset;

import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult;
import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisSettings;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;

/** One imported log together with its analysis, tune snapshot, and analysis limits. */
public final class AnalyzedLog {
    private final IdleLogData data;
    private final IdleAnalysisResult analysis;
    private final IdleAnalysisSettings settings;
    private final String identity;
    private TuneSnapshot tuneSnapshot;
    private VssSourceMode vssSourceMode;

    public AnalyzedLog(IdleLogData data, IdleAnalysisResult analysis) {
        this(data, analysis, IdleAnalysisSettings.defaults(), TuneSnapshot.unknown("Gain snapshot not captured"));
    }

    public AnalyzedLog(
            IdleLogData data,
            IdleAnalysisResult analysis,
            IdleAnalysisSettings settings,
            TuneSnapshot tuneSnapshot) {
        if (data == null) throw new IllegalArgumentException("Log data cannot be null");
        if (analysis == null) throw new IllegalArgumentException("Analysis cannot be null");
        this.data = data;
        this.analysis = analysis;
        this.settings = settings == null ? IdleAnalysisSettings.defaults() : settings;
        this.tuneSnapshot = tuneSnapshot == null
                ? TuneSnapshot.unknown("Gain snapshot not captured")
                : tuneSnapshot;
        this.vssSourceMode = VssSourceMode.AUTOMATIC;
        this.identity = data.getSourceFile().getAbsoluteFile().toURI().normalize().getPath();
    }

    public IdleLogData getData() { return data; }
    public IdleAnalysisResult getAnalysis() { return analysis; }
    public IdleAnalysisSettings getSettings() { return settings; }
    public String getIdentity() { return identity; }
    public String getDisplayName() { return data.getSourceFile().getName(); }
    public TuneSnapshot getTuneSnapshot() { return tuneSnapshot; }
    public VssSourceMode getVssSourceMode() { return vssSourceMode; }

    public void setVssSourceMode(VssSourceMode vssSourceMode) {
        if (vssSourceMode == null) throw new IllegalArgumentException("VSS source mode cannot be null");
        this.vssSourceMode = vssSourceMode;
    }

    public void setTuneSnapshot(TuneSnapshot tuneSnapshot) {
        if (tuneSnapshot == null) throw new IllegalArgumentException("Tune snapshot cannot be null");
        this.tuneSnapshot = tuneSnapshot;
    }
}
