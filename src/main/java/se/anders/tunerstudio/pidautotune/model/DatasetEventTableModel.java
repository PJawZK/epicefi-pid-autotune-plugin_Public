package se.anders.tunerstudio.pidautotune.model;

import se.anders.tunerstudio.pidautotune.dataset.DatasetEvent;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Editable include/exclude table for compatible events from multiple imported logs. */
public final class DatasetEventTableModel extends AbstractTableModel {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final String[] COLUMNS = {
            "Include", "Source log", "P/I/D snapshot", "Event", "Use", "Trigger", "Target", "CLT", "Fan",
            "Motion", "Idle entry", "VSS source", "VSS integrity", "Quality", "Gate", "Compatibility group", "Eligibility"
    };

    private List<DatasetEvent> rows = Collections.emptyList();
    private Runnable changeListener;

    public void setRows(List<DatasetEvent> rows) {
        this.rows = rows == null
                ? Collections.<DatasetEvent>emptyList()
                : new ArrayList<DatasetEvent>(rows);
        fireTableDataChanged();
    }

    public List<DatasetEvent> getRows() {
        return Collections.unmodifiableList(rows);
    }

    public DatasetEvent getRow(int modelRow) { return rows.get(modelRow); }

    public void setChangeListener(Runnable changeListener) {
        this.changeListener = changeListener;
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return columnIndex == 0 ? Boolean.class : String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return columnIndex == 0 && rows.get(rowIndex).isSelectable();
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
        if (columnIndex != 0 || rowIndex < 0 || rowIndex >= rows.size()) return;
        DatasetEvent row = rows.get(rowIndex);
        row.setIncluded(Boolean.TRUE.equals(value));
        fireTableCellUpdated(rowIndex, columnIndex);
        if (changeListener != null) changeListener.run();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DatasetEvent row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return Boolean.valueOf(row.isIncluded());
            case 1: return row.getAnalyzedLog().getDisplayName();
            case 2: return row.getTuneSnapshot().toSignature();
            case 3: return row.getEvent().getType();
            case 4: return row.getEvent().getAnalysisUse();
            case 5: return format(row.getEvent().getTriggerSeconds()) + " s";
            case 6: return format(row.getEvent().getTargetRpm()) + " RPM";
            case 7:
                return Double.isNaN(row.getEvent().getCoolantMedian())
                        ? "Unavailable"
                        : format(row.getEvent().getCoolantMedian()) + " °C";
            case 8: return row.getEvent().getFanState();
            case 9: return row.getIntegrity().getMotionClass();
            case 10: return row.getIntegrity().getEntryCause();
            case 11: return row.getIntegrity().getVssBasis();
            case 12: return row.getIntegrity().getVssStatus();
            case 13: return row.getEvent().getQuality();
            case 14: return row.isRecommendationGateEligible() ? "Eligible" : "Blocked";
            case 15: return row.getEvent().isFutureGainEligible() ? row.getResponseGroup() : row.getOperatingGroup();
            case 16: return row.getEligibility();
            default: return "";
        }
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        synchronized (ONE) { return ONE.format(value); }
    }
}
