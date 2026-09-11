package se.anders.tunerstudio.pidautotune.model;

import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IdleEventTableModel extends AbstractTableModel {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final String[] COLUMNS = {
            "Event", "Use", "Window start", "Trigger", "Evaluation", "Target", "Target drift", "CLT",
            "Average RPM", "Mean error", "Mean |error|", "RPM σ", "Overshoot", "Undershoot",
            "Settling", "Correction range", "At limit", "Reversals/s", "Fan", "Quality", "Notes"
    };

    private List<IdleEvent> rows = Collections.emptyList();

    public void setRows(List<IdleEvent> rows) {
        this.rows = rows == null
                ? Collections.<IdleEvent>emptyList()
                : Collections.unmodifiableList(new ArrayList<IdleEvent>(rows));
        fireTableDataChanged();
    }

    public IdleEvent getRow(int modelRow) { return rows.get(modelRow); }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        IdleEvent row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.getType();
            case 1: return row.getAnalysisUse();
            case 2: return format(row.getStartSeconds()) + " s";
            case 3: return format(row.getTriggerSeconds()) + " s";
            case 4: return format(row.getEvaluationDurationSeconds()) + " s";
            case 5: return format(row.getTargetRpm());
            case 6: return format(row.getTargetDriftRpm());
            case 7:
                if (Double.isNaN(row.getCoolantMedian())) return "Unavailable";
                return format(row.getCoolantMedian()) + " °C";
            case 8: return format(row.getAverageRpm());
            case 9: return signed(row.getMeanSignedError());
            case 10: return format(row.getMeanAbsoluteError());
            case 11: return format(row.getRpmStandardDeviation());
            case 12: return format(row.getOvershootRpm());
            case 13: return format(row.getUndershootRpm());
            case 14:
                if (!row.isSettlingApplicable()) return "Not applicable";
                return Double.isNaN(row.getSettlingSeconds()) ? "Not settled" : format(row.getSettlingSeconds()) + " s";
            case 15:
                if (Double.isNaN(row.getCorrectionMinimum()) || Double.isNaN(row.getCorrectionMaximum())) return "Unavailable";
                return format(row.getCorrectionMinimum()) + " to " + format(row.getCorrectionMaximum());
            case 16:
                return Double.isNaN(row.getCorrectionLimitPercent()) ? "Unavailable" : format(row.getCorrectionLimitPercent()) + "%";
            case 17:
                return Double.isNaN(row.getCorrectionReversalsPerSecond()) ? "Unavailable" : format(row.getCorrectionReversalsPerSecond());
            case 18: return row.getFanState();
            case 19: return row.getQuality();
            case 20: return row.getQualityDetails();
            default: return "";
        }
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        synchronized (ONE) { return ONE.format(value); }
    }

    private static String signed(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        String text = format(value);
        return value > 0.0 ? "+" + text : text;
    }
}
