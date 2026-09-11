package se.anders.tunerstudio.pidautotune.recommendation;

/** User-configurable safety limits for the conservative recommendation model. */
public final class RecommendationSettings {
    private final double maximumPChangePercent;
    private final double maximumIChangePercent;
    private final double maximumDChangePercent;
    private final double minimumConfidencePercent;

    public RecommendationSettings(
            double maximumPChangePercent,
            double maximumIChangePercent,
            double maximumDChangePercent,
            double minimumConfidencePercent) {
        this.maximumPChangePercent = validateChange(maximumPChangePercent, "P");
        this.maximumIChangePercent = validateChange(maximumIChangePercent, "I");
        this.maximumDChangePercent = validateChange(maximumDChangePercent, "D");
        if (!finite(minimumConfidencePercent) || minimumConfidencePercent < 0.0 || minimumConfidencePercent > 100.0) {
            throw new IllegalArgumentException("Minimum confidence must be between 0 and 100 percent.");
        }
        this.minimumConfidencePercent = minimumConfidencePercent;
    }

    public static RecommendationSettings defaults() {
        return new RecommendationSettings(15.0, 15.0, 25.0, 60.0);
    }

    public double getMaximumPChangePercent() { return maximumPChangePercent; }
    public double getMaximumIChangePercent() { return maximumIChangePercent; }
    public double getMaximumDChangePercent() { return maximumDChangePercent; }
    public double getMinimumConfidencePercent() { return minimumConfidencePercent; }

    private static double validateChange(double value, String name) {
        if (!finite(value) || value < 0.0 || value > 50.0) {
            throw new IllegalArgumentException("Maximum " + name + " change must be between 0 and 50 percent.");
        }
        return value;
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
