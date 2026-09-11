package se.anders.tunerstudio.pidautotune.model;

import se.anders.tunerstudio.pidautotune.live.LiveComparisonResult;
import se.anders.tunerstudio.pidautotune.live.LiveExperimentalRecommendation;
import se.anders.tunerstudio.pidautotune.live.LiveIterationRecord;
import se.anders.tunerstudio.pidautotune.live.LiveSample;
import se.anders.tunerstudio.pidautotune.live.LiveSessionSummary;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Table model for the complete in-memory live tuning iteration history. */
public final class LiveIterationTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "#", "Started", "P/I/D", "Accepted", "Good", "Target", "CLT", "Candidate",
            "vs original", "vs previous", "Decision"
    };
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss");
    private final List<LiveIterationRecord> rows = new ArrayList<LiveIterationRecord>();

    public void setRows(List<LiveIterationRecord> values) {
        rows.clear();
        if (values != null) rows.addAll(values);
        fireTableDataChanged();
    }

    public LiveIterationRecord getRow(int row) {
        return row < 0 || row >= rows.size() ? null : rows.get(row);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override public Object getValueAt(int rowIndex, int columnIndex) {
        LiveIterationRecord row = rows.get(rowIndex);
        LiveSessionSummary summary = row.getSummary();
        switch (columnIndex) {
            case 0: return Integer.valueOf(row.getIterationNumber());
            case 1: return TIME.format(new Date(row.getStartedMillis()));
            case 2: return row.getGains() == null ? "Unavailable" : row.getGains().toSignature();
            case 3: return Integer.valueOf(summary.getAcceptedCount());
            case 4: return Integer.valueOf(summary.getGoodCount());
            case 5: return value(summary.getMedianTargetRpm(), " RPM");
            case 6: return value(summary.getMedianCoolant(), "°C");
            case 7: return candidate(row.getRecommendation());
            case 8: return status(row.getOriginalComparison());
            case 9: return status(row.getPreviousComparison());
            case 10: return row.getDecision();
            default: return "";
        }
    }

    private static String candidate(LiveExperimentalRecommendation recommendation) {
        if (recommendation == null || !recommendation.isProposalAvailable()) return "—";
        return recommendation.getChangedGain() + " " + signed(recommendation.getChangePercent())
                + " → " + recommendation.getProposed().toSignature();
    }

    private static String status(LiveComparisonResult result) {
        return result == null ? "—" : result.getStatus();
    }

    private static String signed(double value) {
        if (!LiveSample.isFinite(value)) return "—";
        return (value > 0.0 ? "+" : "") + format(value) + "%";
    }

    private static String value(double value, String suffix) {
        return LiveSample.isFinite(value) ? format(value) + suffix : "—";
    }

    private static String format(double value) {
        synchronized (ONE) { return ONE.format(value); }
    }
}
