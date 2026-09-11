package se.anders.tunerstudio.pidautotune.model;

import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.recommendation.PidRecommendation;

import javax.swing.table.AbstractTableModel;
import java.text.DecimalFormat;

/** Read-only current-versus-proposed gain table. */
public final class RecommendationGainTableModel extends AbstractTableModel {
    private static final String[] COLUMNS = { "Gain", "Current", "Proposed", "Change", "Expected effect", "Reason" };
    private static final String[] GAINS = { "P", "I", "D" };
    private static final DecimalFormat GAIN = new DecimalFormat("0.####");
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private PidRecommendation recommendation;

    public void setRecommendation(PidRecommendation recommendation) {
        this.recommendation = recommendation;
        fireTableDataChanged();
    }

    @Override public int getRowCount() { return 3; }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int column) { return COLUMNS[column]; }
    @Override public Class<?> getColumnClass(int columnIndex) { return String.class; }
    @Override public boolean isCellEditable(int rowIndex, int columnIndex) { return false; }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        if (columnIndex == 0) return GAINS[rowIndex];
        if (recommendation == null) return columnIndex <= 3 ? "—" : "No recommendation has been calculated.";
        TuneSnapshot current = recommendation.getCurrent();
        TuneSnapshot proposed = recommendation.getProposed();
        switch (columnIndex) {
            case 1: return current == null ? "Unavailable" : formatGain(value(current, rowIndex));
            case 2:
                if (!recommendation.isProposalAvailable() || proposed == null) return "Not calculated";
                return formatGain(value(proposed, rowIndex));
            case 3:
                if (!recommendation.isProposalAvailable()) return "—";
                return formatPercent(change(recommendation, rowIndex));
            case 4: return effect(recommendation, rowIndex);
            case 5: return reason(recommendation, rowIndex);
            default: return "";
        }
    }

    private static double value(TuneSnapshot snapshot, int row) {
        if (row == 0) return snapshot.getP();
        if (row == 1) return snapshot.getI();
        return snapshot.getD();
    }

    private static double change(PidRecommendation recommendation, int row) {
        if (row == 0) return recommendation.getPChangePercent();
        if (row == 1) return recommendation.getIChangePercent();
        return recommendation.getDChangePercent();
    }

    private static String reason(PidRecommendation recommendation, int row) {
        if (row == 0) return recommendation.getPReason();
        if (row == 1) return recommendation.getIReason();
        return recommendation.getDReason();
    }

    private static String effect(PidRecommendation recommendation, int row) {
        if (row == 0) return recommendation.getPEffect();
        if (row == 1) return recommendation.getIEffect();
        return recommendation.getDEffect();
    }

    private static String formatGain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "Unavailable";
        synchronized (GAIN) { return GAIN.format(value); }
    }

    private static String formatPercent(double value) {
        synchronized (ONE) {
            String prefix = value > 0.0001 ? "+" : "";
            return prefix + ONE.format(value) + "%";
        }
    }
}
