package se.anders.tunerstudio.pidautotune.model;

import se.anders.tunerstudio.pidautotune.live.LiveAttempt;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

/** Table model for live guided-capture attempt results. */
public final class LiveAttemptTableModel extends AbstractTableModel {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final String[] COLUMNS = {
            "#", "Result", "Quality", "Code", "Trigger", "Peak RPM", "Target", "Settling",
            "MAE", "RPM σ", "Overshoot", "Undershoot", "Limit %", "Reversals/s", "D span", "Reason"
    };
    private final List<LiveAttempt> rows = new ArrayList<LiveAttempt>();

    public void setRows(List<LiveAttempt> values) {
        rows.clear();
        if (values != null) rows.addAll(values);
        fireTableDataChanged();
    }

    public LiveAttempt getRow(int row) {
        return row < 0 || row >= rows.size() ? null : rows.get(row);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LiveAttempt attempt = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return attempt.getNumber();
            case 1: return attempt.isAccepted() ? "Accepted" : "Rejected";
            case 2: return attempt.getQuality();
            case 3: return attempt.getResultCode();
            case 4: return format(attempt.getTriggerSeconds()) + " s";
            case 5: return format(attempt.getPeakRpm());
            case 6: return format(attempt.getTargetRpm());
            case 7: return format(attempt.getSettlingSeconds()) + " s";
            case 8: return format(attempt.getMeanAbsoluteError()) + " RPM";
            case 9: return format(attempt.getRpmStandardDeviation()) + " RPM";
            case 10: return format(attempt.getOvershootRpm()) + " RPM";
            case 11: return format(attempt.getUndershootRpm()) + " RPM";
            case 12: return format(attempt.getCorrectionLimitPercent()) + "%";
            case 13: return format(attempt.getCorrectionReversalsPerSecond());
            case 14: return format(attempt.getDTermSpan());
            case 15: return attempt.getReason();
            default: return "";
        }
    }

    private static String format(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "—" : ONE.format(value);
    }
}
