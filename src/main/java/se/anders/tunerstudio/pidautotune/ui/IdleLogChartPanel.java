package se.anders.tunerstudio.pidautotune.ui;

import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.LogChannelDefinition;

import javax.swing.JPanel;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.text.DecimalFormat;

/** Lightweight chart for target/RPM and base/commanded idle position, with no external dependency. */
public final class IdleLogChartPanel extends JPanel {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final Color RPM_COLOR = new Color(40, 110, 210);
    private static final Color TARGET_COLOR = new Color(220, 120, 30);
    private static final Color CURRENT_COLOR = new Color(35, 150, 90);
    private static final Color BASE_COLOR = new Color(120, 120, 120);
    private static final Color TRIGGER_COLOR = new Color(170, 50, 150);

    private IdleLogData data;
    private IdleEvent event;

    public IdleLogChartPanel() {
        setPreferredSize(new Dimension(900, 310));
        setMinimumSize(new Dimension(400, 220));
        setToolTipText("Actual and target RPM with base and commanded idle position. Display traces are averaged into short time buckets; calculations retain full-resolution samples.");
    }

    public void setData(IdleLogData data, IdleEvent event) {
        this.data = data;
        this.event = event;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color foreground = UIManager.getColor("Label.foreground");
            if (foreground == null) foreground = Color.DARK_GRAY;
            Color grid = UIManager.getColor("Separator.foreground");
            if (grid == null) grid = new Color(180, 180, 180);
            grid = new Color(grid.getRed(), grid.getGreen(), grid.getBlue(), 95);

            int width = getWidth();
            int height = getHeight();
            int left = 64;
            int right = 18;
            int top = 28;
            int bottom = 32;
            int gap = 27;
            int plotWidth = Math.max(1, width - left - right);
            int availableHeight = Math.max(80, height - top - bottom - gap);
            int rpmHeight = (int) Math.round(availableHeight * 0.66);
            int positionHeight = availableHeight - rpmHeight;
            int rpmTop = top;
            int rpmBottom = rpmTop + rpmHeight;
            int positionTop = rpmBottom + gap;
            int positionBottom = positionTop + positionHeight;

            if (data == null || data.getSampleCount() == 0) {
                g.setColor(foreground);
                drawCentered(g, "Import a TunerStudio MSL log to display idle response data.", width, height);
                return;
            }

            double[] time = data.getSeries(LogChannelDefinition.TIME);
            double[] rpm = data.getSeries(LogChannelDefinition.RPM);
            double[] target = data.getSeries(LogChannelDefinition.IDLE_TARGET);
            double[] current = data.getSeries(LogChannelDefinition.CURRENT_IDLE_POSITION);
            double[] base = data.getSeries(LogChannelDefinition.BASE_IDLE_POSITION);

            int start = event == null ? 0 : Math.max(0, event.getStartIndex());
            int end = event == null ? time.length - 1 : Math.min(time.length - 1, event.getEndIndex());
            if (end <= start) end = Math.min(time.length - 1, start + 1);

            Range rpmRange = range(rpm, target, start, end, 50.0);
            Range positionRange = range(current, base, start, end, 2.0);

            drawGrid(g, left, plotWidth, rpmTop, rpmBottom, rpmRange, "RPM", foreground, grid);
            drawGrid(g, left, plotWidth, positionTop, positionBottom, positionRange, "Idle %", foreground, grid);

            drawAveragedSeries(g, time, target, start, end, left, plotWidth, rpmTop, rpmBottom, rpmRange, TARGET_COLOR, 0.04);
            drawAveragedSeries(g, time, rpm, start, end, left, plotWidth, rpmTop, rpmBottom, rpmRange, RPM_COLOR, 0.04);
            drawAveragedSeries(g, time, base, start, end, left, plotWidth, positionTop, positionBottom, positionRange, BASE_COLOR, 0.10);
            drawAveragedSeries(g, time, current, start, end, left, plotWidth, positionTop, positionBottom, positionRange, CURRENT_COLOR, 0.10);

            if (event != null && event.getTriggerIndex() > start && event.getTriggerIndex() < end) {
                drawTrigger(g, time, event, start, end, left, plotWidth, rpmTop, positionBottom, foreground);
            }

            g.setColor(foreground);
            String title = event == null ? "Whole imported log" : event.getType() + " — " + event.getQuality();
            g.drawString(title, left, 17);
            drawLegend(g, left + Math.max(180, plotWidth - 440), 17, foreground);

            String startLabel = format(time[start]) + " s";
            String endLabel = format(time[end]) + " s";
            g.drawString(startLabel, left, height - 8);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(endLabel, left + plotWidth - metrics.stringWidth(endLabel), height - 8);
        } finally {
            g.dispose();
        }
    }

    private static void drawGrid(
            Graphics2D g,
            int left,
            int width,
            int top,
            int bottom,
            Range range,
            String label,
            Color foreground,
            Color grid) {
        g.setStroke(new BasicStroke(1.0f));
        for (int i = 0; i <= 4; i++) {
            int y = top + (bottom - top) * i / 4;
            g.setColor(grid);
            g.drawLine(left, y, left + width, y);
            double value = range.maximum - (range.maximum - range.minimum) * i / 4.0;
            g.setColor(foreground);
            String valueText = format(value);
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(valueText, left - metrics.stringWidth(valueText) - 7, y + 4);
        }
        g.setColor(foreground);
        g.drawRect(left, top, width, bottom - top);
        g.drawString(label, 6, top + 14);
    }

    /**
     * Collapses duplicate timestamps and high-rate switching into time buckets that are no
     * narrower than the requested display interval. Full-resolution samples remain untouched.
     */
    private static void drawAveragedSeries(
            Graphics2D g,
            double[] time,
            double[] values,
            int start,
            int end,
            int left,
            int width,
            int top,
            int bottom,
            Range range,
            Color color,
            double minimumBucketSeconds) {
        if (values == null || values.length == 0) return;
        double startTime = time[start];
        double endTime = time[end];
        double timeSpan = Math.max(0.000001, endTime - startTime);
        int currentPixel = -1;
        double sum = 0.0;
        int count = 0;
        int previousX = 0;
        int previousY = 0;
        boolean havePrevious = false;

        g.setColor(color);
        g.setStroke(new BasicStroke(1.6f));
        for (int i = start; i <= end; i++) {
            if (i >= values.length || !finite(values[i]) || !finite(time[i])) continue;
            double bucketSeconds = Math.max(minimumBucketSeconds, timeSpan / Math.max(1, width));
            int bucket = (int) Math.floor((time[i] - startTime) / bucketSeconds);
            double bucketTime = startTime + (bucket + 0.5) * bucketSeconds;
            int pixel = (int) Math.round((bucketTime - startTime) / timeSpan * width);
            pixel = Math.max(0, Math.min(width, pixel));
            if (currentPixel >= 0 && pixel != currentPixel) {
                double average = sum / Math.max(1, count);
                int x = left + currentPixel;
                int y = valueToY(average, top, bottom, range);
                if (havePrevious) g.drawLine(previousX, previousY, x, y);
                previousX = x;
                previousY = y;
                havePrevious = true;
                currentPixel = pixel;
                sum = values[i];
                count = 1;
            } else {
                if (currentPixel < 0) currentPixel = pixel;
                sum += values[i];
                count++;
            }
        }
        if (currentPixel >= 0 && count > 0) {
            int x = left + currentPixel;
            int y = valueToY(sum / count, top, bottom, range);
            if (havePrevious) g.drawLine(previousX, previousY, x, y);
        }
    }

    private static int valueToY(double value, int top, int bottom, Range range) {
        int y = bottom - (int) Math.round((value - range.minimum) / (range.maximum - range.minimum) * (bottom - top));
        return Math.max(top, Math.min(bottom, y));
    }

    private static void drawTrigger(
            Graphics2D g,
            double[] time,
            IdleEvent event,
            int start,
            int end,
            int left,
            int width,
            int top,
            int bottom,
            Color foreground) {
        double span = Math.max(0.000001, time[end] - time[start]);
        int x = left + (int) Math.round((event.getTriggerSeconds() - time[start]) / span * width);
        g.setColor(TRIGGER_COLOR);
        g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 1.0f, new float[]{5.0f, 4.0f}, 0.0f));
        g.drawLine(x, top, x, bottom);
        String label = "Trigger " + format(event.getTriggerSeconds()) + " s";
        int labelX = Math.min(left + width - g.getFontMetrics().stringWidth(label) - 3, x + 4);
        g.setColor(foreground);
        g.drawString(label, Math.max(left + 3, labelX), top + 13);
    }

    private static Range range(double[] first, double[] second, int start, int end, double minimumPadding) {
        double minimum = Double.POSITIVE_INFINITY;
        double maximum = Double.NEGATIVE_INFINITY;
        double[][] values = {first, second};
        for (double[] series : values) {
            if (series == null) continue;
            for (int i = start; i <= end && i < series.length; i++) {
                if (!finite(series[i])) continue;
                minimum = Math.min(minimum, series[i]);
                maximum = Math.max(maximum, series[i]);
            }
        }
        if (minimum == Double.POSITIVE_INFINITY) return new Range(0.0, 100.0);
        double span = maximum - minimum;
        double padding = Math.max(minimumPadding, span * 0.12);
        if (span < 0.0001) padding = Math.max(minimumPadding, 1.0);
        return new Range(minimum - padding, maximum + padding);
    }

    private static void drawLegend(Graphics2D g, int x, int y, Color foreground) {
        int currentX = x;
        currentX = legendEntry(g, currentX, y, RPM_COLOR, "RPM", foreground);
        currentX = legendEntry(g, currentX, y, TARGET_COLOR, "Target", foreground);
        currentX = legendEntry(g, currentX, y, CURRENT_COLOR, "Commanded idle", foreground);
        legendEntry(g, currentX, y, BASE_COLOR, "Base idle", foreground);
    }

    private static int legendEntry(Graphics2D g, int x, int y, Color color, String text, Color foreground) {
        g.setColor(color);
        g.setStroke(new BasicStroke(2.0f));
        g.drawLine(x, y - 4, x + 18, y - 4);
        g.setColor(foreground);
        g.drawString(text, x + 23, y);
        return x + 29 + g.getFontMetrics().stringWidth(text);
    }

    private static void drawCentered(Graphics2D g, String text, int width, int height) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, Math.max(8, (width - metrics.stringWidth(text)) / 2), height / 2);
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static String format(double value) {
        if (!finite(value)) return "";
        synchronized (ONE) { return ONE.format(value); }
    }

    private static final class Range {
        private final double minimum;
        private final double maximum;

        private Range(double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
