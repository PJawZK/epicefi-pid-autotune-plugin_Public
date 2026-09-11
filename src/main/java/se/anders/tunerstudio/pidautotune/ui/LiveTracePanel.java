package se.anders.tunerstudio.pidautotune.ui;

import se.anders.tunerstudio.pidautotune.live.LiveChannel;
import se.anders.tunerstudio.pidautotune.live.LiveSample;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.List;

/** Lightweight rolling live chart for RPM/target and derived idle correction. */
public final class LiveTracePanel extends JPanel {
    private final List<LiveSample> samples = new ArrayList<LiveSample>();
    private double visibleSeconds = 45.0;

    public LiveTracePanel() {
        setOpaque(true);
        setBackground(Color.WHITE);
        setToolTipText("Rolling live RPM/target and derived idle-correction traces. Full session samples are retained separately for export.");
    }

    public void setSamples(List<LiveSample> values) {
        samples.clear();
        if (values != null) samples.addAll(values);
        repaint();
    }

    public void clear() {
        samples.clear();
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int width = getWidth();
            int height = getHeight();
            if (width < 120 || height < 100) return;
            int left = 48;
            int right = 18;
            int top = 22;
            int bottom = 28;
            int middle = top + (height - top - bottom) * 2 / 3;
            int plotWidth = width - left - right;

            g.setColor(new Color(235, 235, 235));
            g.drawRect(left, top, plotWidth, middle - top);
            g.drawRect(left, middle + 8, plotWidth, height - bottom - middle - 8);
            g.setColor(Color.DARK_GRAY);
            g.drawString("RPM / target", left + 4, top + 14);
            g.drawString("Idle correction", left + 4, middle + 22);

            if (samples.size() < 2) {
                g.drawString("Start the live observer to display data.", left + 8, top + 40);
                return;
            }

            double latest = samples.get(samples.size() - 1).getTimeSeconds();
            double earliest = Math.max(0.0, latest - visibleSeconds);
            double rpmMin = Double.POSITIVE_INFINITY;
            double rpmMax = Double.NEGATIVE_INFINITY;
            double correctionMax = 1.0;
            for (LiveSample sample : samples) {
                if (sample.getTimeSeconds() < earliest) continue;
                double rpm = sample.get(LiveChannel.RPM);
                double target = sample.get(LiveChannel.IDLE_TARGET);
                if (LiveSample.isFinite(rpm)) { rpmMin = Math.min(rpmMin, rpm); rpmMax = Math.max(rpmMax, rpm); }
                if (LiveSample.isFinite(target)) { rpmMin = Math.min(rpmMin, target); rpmMax = Math.max(rpmMax, target); }
                double correction = sample.getCorrection();
                if (LiveSample.isFinite(correction)) correctionMax = Math.max(correctionMax, Math.abs(correction));
            }
            if (!LiveSample.isFinite(rpmMin) || !LiveSample.isFinite(rpmMax)) return;
            rpmMin = Math.max(0.0, Math.floor((rpmMin - 100.0) / 100.0) * 100.0);
            rpmMax = Math.ceil((rpmMax + 100.0) / 100.0) * 100.0;
            if (rpmMax <= rpmMin) rpmMax = rpmMin + 500.0;

            drawSeries(g, earliest, latest, left, plotWidth, top, middle - top,
                    LiveChannel.RPM, rpmMin, rpmMax, new Color(45, 95, 180), false);
            drawSeries(g, earliest, latest, left, plotWidth, top, middle - top,
                    LiveChannel.IDLE_TARGET, rpmMin, rpmMax, new Color(210, 80, 55), false);
            drawCorrection(g, earliest, latest, left, plotWidth, middle + 8,
                    height - bottom - middle - 8, correctionMax, new Color(55, 145, 80));

            g.setColor(Color.DARK_GRAY);
            FontMetrics metrics = g.getFontMetrics();
            String rpmScale = (int) rpmMin + "–" + (int) rpmMax + " RPM";
            g.drawString(rpmScale, Math.max(left, width - right - metrics.stringWidth(rpmScale)), top + 14);
            String timeScale = "Last " + (int) visibleSeconds + " s";
            g.drawString(timeScale, width - right - metrics.stringWidth(timeScale), height - 8);
        } finally {
            g.dispose();
        }
    }

    private void drawSeries(Graphics2D g, double earliest, double latest, int left, int width,
                            int top, int height, LiveChannel channel, double minimum, double maximum,
                            Color color, boolean correction) {
        g.setColor(color);
        g.setStroke(new BasicStroke(channel == LiveChannel.IDLE_TARGET ? 1.8f : 1.3f));
        int previousX = -1;
        int previousY = -1;
        for (LiveSample sample : samples) {
            if (sample.getTimeSeconds() < earliest) continue;
            double value = sample.get(channel);
            if (!LiveSample.isFinite(value)) continue;
            int x = left + (int) Math.round((sample.getTimeSeconds() - earliest) / Math.max(0.001, latest - earliest) * width);
            int y = top + height - (int) Math.round((value - minimum) / (maximum - minimum) * height);
            if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
            previousX = x;
            previousY = y;
        }
    }

    private void drawCorrection(Graphics2D g, double earliest, double latest, int left, int width,
                                int top, int height, double maximum, Color color) {
        int zero = top + height / 2;
        g.setColor(new Color(205, 205, 205));
        g.drawLine(left, zero, left + width, zero);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.2f));
        int previousX = -1;
        int previousY = -1;
        for (LiveSample sample : samples) {
            if (sample.getTimeSeconds() < earliest) continue;
            double value = sample.getCorrection();
            if (!LiveSample.isFinite(value)) continue;
            int x = left + (int) Math.round((sample.getTimeSeconds() - earliest) / Math.max(0.001, latest - earliest) * width);
            int y = zero - (int) Math.round(value / maximum * (height * 0.45));
            if (previousX >= 0) g.drawLine(previousX, previousY, x, y);
            previousX = x;
            previousY = y;
        }
    }
}
