package se.anders.tunerstudio.pidautotune.dataset;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Immutable per-log snapshot of the idle PID gains used when the log was recorded. */
public final class TuneSnapshot {
    private static final DecimalFormat FORMAT = new DecimalFormat(
            "0.####", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final double p;
    private final double i;
    private final double d;
    private final String source;
    private final boolean manualOverride;

    public TuneSnapshot(double p, double i, double d, String source, boolean manualOverride) {
        this.p = p;
        this.i = i;
        this.d = d;
        this.source = source == null ? "" : source.trim();
        this.manualOverride = manualOverride;
    }

    public static TuneSnapshot unknown(String source) {
        return new TuneSnapshot(Double.NaN, Double.NaN, Double.NaN, source, false);
    }

    public double getP() { return p; }
    public double getI() { return i; }
    public double getD() { return d; }
    public String getSource() { return source; }
    public boolean isManualOverride() { return manualOverride; }

    public boolean isComplete() {
        return finite(p) && finite(i) && finite(d);
    }

    public String toSignature() {
        return "P " + format(p) + " / I " + format(i) + " / D " + format(d);
    }

    public String describeSource() {
        if (source.isEmpty()) return manualOverride ? "Manual override" : "Source not recorded";
        return source;
    }

    private static String format(double value) {
        if (!finite(value)) return "unknown";
        synchronized (FORMAT) { return FORMAT.format(value); }
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
