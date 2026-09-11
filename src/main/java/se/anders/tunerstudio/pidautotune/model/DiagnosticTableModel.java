package se.anders.tunerstudio.pidautotune.model;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DiagnosticTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = {
            "Category", "Name", "Current value", "Units", "Minimum", "Maximum", "Type", "Details", "Status"
    };

    private List<DiagnosticRow> rows = Collections.emptyList();

    public void setRows(List<DiagnosticRow> rows) {
        this.rows = rows == null
                ? Collections.<DiagnosticRow>emptyList()
                : Collections.unmodifiableList(new ArrayList<DiagnosticRow>(rows));
        fireTableDataChanged();
    }

    public DiagnosticRow getRow(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMNS[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DiagnosticRow row = rows.get(rowIndex);
        switch (columnIndex) {
            case 0: return row.getCategory();
            case 1: return row.getName();
            case 2: return row.getValue();
            case 3: return row.getUnits();
            case 4: return row.getMinimum();
            case 5: return row.getMaximum();
            case 6: return row.getDataType();
            case 7: return row.getDetails();
            case 8: return row.getStatus();
            default: return "";
        }
    }
}
