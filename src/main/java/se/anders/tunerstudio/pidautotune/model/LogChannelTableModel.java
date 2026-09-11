package se.anders.tunerstudio.pidautotune.model;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class LogChannelTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {"Logical channel", "Required", "Matched log caption", "Units", "Status"};
    private List<LogChannelMapping> rows = Collections.emptyList();

    public void setRows(List<LogChannelMapping> rows) {
        this.rows = rows == null
                ? Collections.<LogChannelMapping>emptyList()
                : Collections.unmodifiableList(new ArrayList<LogChannelMapping>(rows));
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        LogChannelMapping row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.getLogicalName();
            case 1: return row.isRequired() ? "Yes" : "No";
            case 2: return row.getMatchedName();
            case 3: return row.getUnits();
            case 4: return row.getStatus();
            default: return "";
        }
    }
}
