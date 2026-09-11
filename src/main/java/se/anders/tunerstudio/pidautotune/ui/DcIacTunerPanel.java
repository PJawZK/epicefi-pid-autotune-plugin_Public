package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.live.DcIacDynamicEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacMeasurement;
import se.anders.tunerstudio.pidautotune.live.DcIacStaticEvidence;
import se.anders.tunerstudio.pidautotune.live.DcIacTunerCoordinator;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasKnotProposal;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacPidRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacGuidedPidRecommendationEngine;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendation;
import se.anders.tunerstudio.pidautotune.session.DcIacIterationComparison;
import se.anders.tunerstudio.pidautotune.session.DcIacTuningSessionSummary;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.BufferedWriter;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AE-Tuner-style guided front end for the complete DC-IAC workflow.
 *
 * The validated readiness/event/measurement engines remain internal. This panel
 * presents only operator concepts: prepare, capture bias, recheck, PID steps,
 * review and result.
 */
public final class DcIacTunerPanel extends JPanel {
    // Visual authority: AE Tuner UI Prototype 0.15 two-palette theme system.
    private static Color BG;
    private static Color CARD;
    private static Color PANEL;
    private static Color TEXT;
    private static Color TEXT_MUTED;
    private static Color NAVY;
    private static Color BLUE;
    private static Color BLUE_SOFT;
    private static Color GREEN;
    private static Color GREEN_SOFT;
    private static Color AMBER;
    private static Color AMBER_SOFT;
    private static Color RED;
    private static Color RED_SOFT;
    private static Color BORDER;
    private static Color BUTTON;
    private static Color BUTTON_BORDER;
    private static Color INACTIVE_STEP;

    static { syncThemeColors(); }

    private static void syncThemeColors() {
        BG = UiTheme.bg();
        CARD = UiTheme.card();
        PANEL = UiTheme.panel();
        TEXT = UiTheme.text();
        TEXT_MUTED = UiTheme.muted();
        NAVY = UiTheme.navy();
        BLUE = UiTheme.blue();
        BLUE_SOFT = UiTheme.softBlue();
        GREEN = UiTheme.green();
        GREEN_SOFT = UiTheme.softGreen();
        AMBER = UiTheme.focusAmber();
        AMBER_SOFT = UiTheme.softAmber();
        RED = UiTheme.red();
        RED_SOFT = UiTheme.softRed();
        BORDER = UiTheme.border();
        BUTTON = UiTheme.button();
        BUTTON_BORDER = UiTheme.buttonBorder();
        INACTIVE_STEP = UiTheme.inactiveStep();
    }

    private static final Font TITLE = new Font("Dialog", Font.BOLD, 22);
    private static final Font H2 = new Font("Dialog", Font.BOLD, 16);
    private static final Font H3 = new Font("Dialog", Font.BOLD, 13);
    private static final Font BODY = new Font("Dialog", Font.PLAIN, 12);
    private static final Font SMALL = new Font("Dialog", Font.PLAIN, 10);

    private enum Stage {
        PREPARE(0, "Prepare"),
        CAPTURE_BIAS(1, "Capture Bias"),
        RECHECK(2, "Recheck"),
        PID_STEPS(3, "PID Steps"),
        REVIEW(4, "Review"),
        RESULT(5, "Result");
        final int index;
        final String label;
        Stage(int index, String label) { this.index = index; this.label = label; }
    }

    /** The external task navigator selects what this capture is allowed to tune. */
    private enum GuidedIntent {
        BIAS("Bias Curve"), PID("Position PID"), VALIDATION("Validation"), NONE("Evidence / history");
        final String label; GuidedIntent(String label) { this.label = label; }
    }

    private final JLabel signature = new JLabel("Not yet reported by TunerStudio");
    private final JLabel state = new JLabel("Not connected");
    private final JLabel config = new JLabel("-");
    private final JComboBox<String> configurations = new JComboBox<String>();
    private final JButton refresh = new JButton("Refresh");
    private final JButton start = new JButton("Start Capture");
    private final JButton archive = new JButton("Stop + archive");
    private final JButton stop = new JButton("Stop");
    private final JButton export = new JButton("Export CSV...");
    private final JButton clear = new JButton("Clear current");

    private final StageStrip stages = new StageStrip();
    private final JLabel objectiveKicker = new JLabel("1. PREPARE");
    private final JLabel objectiveTitle = new JLabel("Start a clean DC-IAC capture");
    private final JTextArea objectiveBody = infoArea();
    private final JLabel statusBanner = new JLabel("Ready", SwingConstants.LEFT);
    private final JLabel nextAction = new JLabel("Next: connect to the ECU and select a configuration");

    private final JLabel liveTarget = valueLabel("-");
    private final JLabel liveActual = valueLabel("-");
    private final JLabel liveDuty = valueLabel("-");
    private final JLabel liveI = valueLabel("-");
    private final JLabel liveRpm = valueLabel("-");
    private final JLabel liveVoltage = valueLabel("-");
    private final JLabel liveFan = valueLabel("-");
    private final JLabel liveHandoff = valueLabel("-");

    private final DefaultTableModel guidedModel = readOnlyModel(new String[] {"Item", "Value", "Status"});
    private final JTable guidedTable = new JTable(guidedModel);
    private final DefaultTableModel biasModel = readOnlyModel(new String[] {"Knot %", "Current", "Suggested", "Support", "Confidence", "Status"});
    private final JTable biasTable = new JTable(biasModel);
    private final DefaultTableModel pidModel = readOnlyModel(new String[] {"Center", "Steps", "Open/Close", "|I0|", "Overshoot", "Action", "Status"});
    private final JTable pidTable = new JTable(pidModel);
    private final DefaultTableModel sessionModel = readOnlyModel(new String[] {"#", "P / I / D", "Static/Dynamic", "Center", "|Bias|", "Overshoot", "Peak duty", "Bias", "PID"});
    private final JTable sessionTable = new JTable(sessionModel);
    private final JTextArea evidenceArea = infoArea();
    private final JTextArea diagnosticsArea = infoArea();

    private final JList<String> taskList = new JList<String>(new String[] {
            "Guided Tuner", "Bias evidence", "PID evidence", "Sessions", "Diagnostics"
    });
    private final CardLayout detailCards = new CardLayout();
    private final JPanel detailPanel = new JPanel(detailCards);
    private JPanel navigationSidebar;
    private JSplitPane navigationSplit;

    private final Timer timer;
    private ControllerAccess controllerAccess;
    private DcIacTunerCoordinator coordinator;
    private int configCheckTicks;
    private final DcIacGuidedPidRecommendationEngine guidedPidEngine = new DcIacGuidedPidRecommendationEngine();
    private GuidedIntent activeIntent = GuidedIntent.BIAS;
    private GuidedIntent lastTuningIntent = GuidedIntent.BIAS;

    public DcIacTunerPanel() {
        super(new BorderLayout(10, 8));
        syncThemeColors();
        setBorder(BorderFactory.createEmptyBorder(10, 10, 8, 10));
        setBackground(BG);
        buildUi();
        wire();
        timer = new Timer(150, e -> refreshUi());
        timer.setRepeats(true);
        updateTaskControls();
        refreshUi();
    }

    public void connect(ControllerAccess access, String controllerSignature) {
        disconnect();
        controllerAccess = access;
        setControllerSignature(controllerSignature);
        if (access != null) {
            coordinator = new DcIacTunerCoordinator(access);
            reloadConfigurations();
            state.setText("Connected - stopped");
        }
        refreshUi();
    }

    public void disconnect() {
        if (timer != null && timer.isRunning()) timer.stop();
        if (coordinator != null) coordinator.stop("Disconnected");
        coordinator = null;
        controllerAccess = null;
        state.setText("Not connected");
        refreshUi();
    }


    /**
     * Lets the plugin-wide task navigator replace the panel-local task list.
     * Only presentation changes: the coordinator, capture state and evidence
     * objects remain the same instances while the sidebar is hidden/shown.
     */
    public void setExternalNavigationMode(boolean external) {
        if (navigationSidebar == null || navigationSplit == null) return;
        navigationSidebar.setVisible(!external);
        stages.setVisible(!external);
        navigationSplit.setDividerSize(external ? 0 : 7);
        navigationSplit.setDividerLocation(external ? 0 : 215);
        navigationSplit.setResizeWeight(external ? 0.0 : 0.0);
        revalidate();
        repaint();
    }

    /** Select one of the existing read-only detail views from an external navigator. */
    public void selectTask(String taskName) {
        if (taskName == null) return;
        setGuidedIntent(GuidedIntent.NONE);
        for (int i = 0; i < taskList.getModel().getSize(); i++) {
            String value = taskList.getModel().getElementAt(i);
            if (taskName.equals(value)) {
                taskList.setSelectedIndex(i); detailCards.show(detailPanel, value); refreshUi(); return;
            }
        }
    }

    void selectBiasTask() { selectGuidedTask(GuidedIntent.BIAS); }
    void selectPidTask() { selectGuidedTask(GuidedIntent.PID); }
    void selectValidationTask() { selectGuidedTask(GuidedIntent.VALIDATION); }

    private void selectGuidedTask(GuidedIntent intent) {
        setGuidedIntent(intent);
        taskList.setSelectedValue("Guided Tuner", true);
        detailCards.show(detailPanel, "Guided Tuner");
        refreshUi();
    }

    private void setGuidedIntent(GuidedIntent intent) {
        if (intent == null) intent = GuidedIntent.NONE;
        if (activeIntent == intent) { updateTaskControls(); return; }
        if (coordinator != null && coordinator.isRunning()) {
            coordinator.stop("Guided DC-IAC task changed - capture stopped to prevent evidence mixing");
            if (timer.isRunning()) timer.stop();
        }
        if (intent != GuidedIntent.NONE) {
            if (coordinator != null && lastTuningIntent != intent) coordinator.clearCurrent();
            lastTuningIntent = intent;
        }
        activeIntent = intent; updateTaskControls();
    }

    private void updateTaskControls() {
        switch (activeIntent) {
            case BIAS: start.setText("Start Bias Capture"); export.setToolTipText("Export only DC-IAC bias/static evidence for the current task"); break;
            case PID: start.setText("Start PID Capture"); export.setToolTipText("Export only DC-IAC PID/step evidence for the current task"); break;
            case VALIDATION: start.setText("Start Validation"); export.setToolTipText("Export response/target-tracking validation evidence; no tuning proposal rows"); break;
            default: start.setText("Start Capture"); export.setToolTipText("Select Bias Curve, Position PID or Validation to start a capture");
        }
        updateControls();
    }

    public void setControllerSignature(String value) {
        if (value == null || value.trim().isEmpty()) return;
        signature.setText(value.trim());
        signature.setToolTipText(value.trim());
    }

    private void buildUi() {
        add(buildHeader(), BorderLayout.NORTH);

        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        taskList.setSelectedIndex(0);
        taskList.setFixedCellHeight(52);
        taskList.setCellRenderer(new TaskCellRenderer());
        taskList.setBackground(PANEL);
        taskList.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JScrollPane listScroll = new JScrollPane(taskList);
        listScroll.setBorder(null);
        listScroll.getViewport().setBackground(PANEL);

        navigationSidebar = new JPanel(new BorderLayout(0, 7));
        JPanel sidebar = navigationSidebar;
        sidebar.setBackground(PANEL);
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        JPanel sideHead = new JPanel(new GridLayout(0, 1, 0, 1));
        sideHead.setOpaque(false);
        JLabel sideTitle = new JLabel("DC IAC tuning");
        sideTitle.setFont(H2); sideTitle.setForeground(TEXT);
        JLabel sideSub = new JLabel("Guided workflow + evidence");
        sideSub.setFont(SMALL); sideSub.setForeground(TEXT_MUTED);
        sideHead.add(sideTitle); sideHead.add(sideSub);
        sidebar.add(sideHead, BorderLayout.NORTH);
        sidebar.add(listScroll, BorderLayout.CENTER);
        JLabel sideHint = new JLabel("Validated engines remain internal");
        sideHint.setFont(SMALL); sideHint.setForeground(TEXT_MUTED);
        sidebar.add(sideHint, BorderLayout.SOUTH);
        sidebar.setPreferredSize(new Dimension(205, 500));

        detailPanel.setBackground(BG);
        detailPanel.add(buildGuidedPage(), "Guided Tuner");
        detailPanel.add(buildBiasPage(), "Bias evidence");
        detailPanel.add(buildPidPage(), "PID evidence");
        detailPanel.add(buildSessionPage(), "Sessions");
        detailPanel.add(buildDiagnosticsPage(), "Diagnostics");

        navigationSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, detailPanel);
        JSplitPane split = navigationSplit;
        split.setResizeWeight(0.0);
        split.setDividerLocation(215);
        split.setDividerSize(7);
        split.setContinuousLayout(true);
        split.setBorder(null);
        split.setBackground(BG);
        add(split, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.setBackground(CARD);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 9, 6, 9)));
        JLabel dot = new JLabel("●");
        dot.setFont(new Font("Dialog", Font.BOLD, 12)); dot.setForeground(BLUE);
        nextAction.setForeground(TEXT); nextAction.setFont(BODY);
        JPanel next = new JPanel(new BorderLayout(7, 0)); next.setOpaque(false);
        next.add(dot, BorderLayout.WEST); next.add(nextAction, BorderLayout.CENTER);
        footer.add(next, BorderLayout.CENTER);
        JLabel readonly = new JLabel("READ-ONLY  •  NO ECU WRITES");
        readonly.setFont(new Font("Dialog", Font.BOLD, 9)); readonly.setForeground(NAVY);
        footer.add(readonly, BorderLayout.EAST);
        add(footer, BorderLayout.SOUTH);
    }

    private JComponent buildHeader() {
        JPanel root = new JPanel(new BorderLayout(0, 7));
        root.setOpaque(false);

        JPanel titleRow = new JPanel(new BorderLayout(12, 0));
        titleRow.setOpaque(false);
        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 1));
        titles.setOpaque(false);
        JLabel h = new JLabel("DC IAC Guided Tuner");
        h.setFont(TITLE); h.setForeground(TEXT);
        JLabel sub = new JLabel("Position-loop bias and PID tuning — guided by validated DC-IAC evidence");
        sub.setFont(BODY); sub.setForeground(TEXT_MUTED);
        titles.add(h); titles.add(sub);
        titleRow.add(titles, BorderLayout.WEST);

        JPanel right = new JPanel(new BorderLayout(8, 0));
        right.setOpaque(false);
        JPanel badge = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0)); badge.setOpaque(false);
        badge.add(pill("DC IAC • READ-ONLY", BLUE_SOFT, BLUE));
        right.add(badge, BorderLayout.WEST);
        JPanel connection = new JPanel(new GridLayout(0, 1, 0, 0));
        connection.setOpaque(false);
        connection.add(compactLine("Controller", signature));
        connection.add(compactLine("State", state));
        connection.add(compactLine("Config", config));
        right.add(connection, BorderLayout.CENTER);
        titleRow.add(right, BorderLayout.EAST);
        root.add(titleRow, BorderLayout.NORTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.setOpaque(false);
        configurations.setPrototypeDisplayValue("MEGA144H7EPIC-Volvo940Turbo");
        configurations.setFont(BODY);
        configurations.setBackground(CARD);
        configurations.setForeground(TEXT);
        styleButton(refresh, false);
        styleButton(start, true);
        styleButton(archive, false);
        styleButton(stop, false);
        styleButton(export, false);
        styleButton(clear, false);
        controls.add(configurations); controls.add(refresh); controls.add(start); controls.add(archive); controls.add(stop); controls.add(export); controls.add(clear);
        root.add(controls, BorderLayout.CENTER);
        root.add(stages, BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildGuidedPage() {
        JPanel root = new JPanel(new BorderLayout(0, 8));
        root.setOpaque(false);
        root.add(buildObjectiveCard(), BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(8, 0));
        center.setOpaque(false);

        JPanel evidenceCard = cardPanel(new BorderLayout(0, 6));
        JPanel evidenceHead = new JPanel(new BorderLayout()); evidenceHead.setOpaque(false);
        JLabel evidenceTitle = new JLabel("Current objective / evidence");
        evidenceTitle.setFont(H3); evidenceTitle.setForeground(TEXT);
        JLabel evidenceHint = new JLabel("Only evidence relevant to the current guided step is shown here");
        evidenceHint.setFont(SMALL); evidenceHint.setForeground(TEXT_MUTED);
        evidenceHead.add(evidenceTitle, BorderLayout.WEST); evidenceHead.add(evidenceHint, BorderLayout.EAST);
        evidenceCard.add(evidenceHead, BorderLayout.NORTH);
        styleTable(guidedTable);
        JScrollPane tableScroll = new JScrollPane(guidedTable);
        tableScroll.setBorder(BorderFactory.createLineBorder(BORDER));
        tableScroll.getViewport().setBackground(CARD);
        evidenceCard.add(tableScroll, BorderLayout.CENTER);
        center.add(evidenceCard, BorderLayout.CENTER);

        JPanel context = new JPanel(new GridLayout(2, 1, 0, 8));
        context.setOpaque(false);
        context.setPreferredSize(new Dimension(330, 0));
        context.add(buildPrepareCard());
        context.add(buildProgressCard());
        center.add(context, BorderLayout.EAST);
        root.add(center, BorderLayout.CENTER);
        root.add(buildLiveStrip(), BorderLayout.SOUTH);
        return root;
    }

    private JComponent buildPrepareCard() {
        JPanel p = cardPanel(new BorderLayout(4, 6));
        JLabel t = new JLabel("Readiness"); t.setFont(H3); t.setForeground(TEXT);
        JLabel s = new JLabel("Automatic before evidence is trusted"); s.setFont(SMALL); s.setForeground(TEXT_MUTED);
        JPanel head = new JPanel(new GridLayout(0, 1, 0, 1)); head.setOpaque(false); head.add(t); head.add(s);
        p.add(head, BorderLayout.NORTH);
        JTextArea a = infoArea();
        a.setText("●  ECU + configuration readable\n" +
                "●  Target + feedback available\n" +
                "●  No DC-IAC fault / pause\n" +
                "●  Bias + PID settings readable\n\n" +
                "P active / I=0 / D=0: valid for bias calibration.");
        a.setForeground(TEXT_MUTED);
        a.setFont(SMALL);
        p.add(a, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildObjectiveCard() {
        JPanel p = cardPanel(new BorderLayout(12, 6));
        JPanel copy = new JPanel(new BorderLayout(0, 4)); copy.setOpaque(false);
        objectiveKicker.setForeground(BLUE);
        objectiveKicker.setFont(new Font("Dialog", Font.BOLD, 11));
        objectiveTitle.setFont(new Font("Dialog", Font.BOLD, 19));
        objectiveTitle.setForeground(TEXT);
        JPanel head = new JPanel(new GridLayout(0, 1, 0, 2)); head.setOpaque(false);
        head.add(objectiveKicker); head.add(objectiveTitle);
        copy.add(head, BorderLayout.NORTH);
        objectiveBody.setFont(BODY); objectiveBody.setForeground(TEXT);
        objectiveBody.setRows(4);
        copy.add(objectiveBody, BorderLayout.CENTER);
        p.add(copy, BorderLayout.CENTER);

        JPanel stateCard = new JPanel(new BorderLayout(0, 5));
        stateCard.setOpaque(false); stateCard.setPreferredSize(new Dimension(330, 0));
        JLabel now = new JLabel("CURRENT STATE"); now.setFont(new Font("Dialog", Font.BOLD, 9)); now.setForeground(TEXT_MUTED);
        statusBanner.setOpaque(true);
        statusBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(9, 10, 9, 10)));
        statusBanner.setFont(new Font("Dialog", Font.BOLD, 12));
        stateCard.add(now, BorderLayout.NORTH); stateCard.add(statusBanner, BorderLayout.CENTER);
        p.add(stateCard, BorderLayout.EAST);
        return p;
    }

    private JComponent buildProgressCard() {
        JPanel p = cardPanel(new BorderLayout(4, 6));
        JLabel t = new JLabel("How the tuner advances"); t.setFont(H3); t.setForeground(TEXT);
        p.add(t, BorderLayout.NORTH);
        JTextArea hint = infoArea();
        hint.setText("READY_KNOT  →  change only that bias bin, then recheck.\n\n" +
                "NO_CHANGE  →  local bias is cleared for PID steps.\n\n" +
                "PID READY  →  archive, change one gain, then repeat the same test.");
        hint.setForeground(TEXT_MUTED);
        hint.setFont(SMALL);
        p.add(hint, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildLiveStrip() {
        JPanel p = cardPanel(new BorderLayout(0, 5));
        JLabel t = new JLabel("Live context"); t.setFont(H3); t.setForeground(TEXT);
        p.add(t, BorderLayout.NORTH);
        JPanel row = new JPanel(new GridLayout(1, 8, 0, 0)); row.setOpaque(false);
        row.add(metricCard("Target", liveTarget));
        row.add(metricCard("Actual", liveActual));
        row.add(metricCard("Duty", liveDuty));
        row.add(metricCard("I term", liveI));
        row.add(metricCard("RPM", liveRpm));
        row.add(metricCard("VBatt", liveVoltage));
        row.add(metricCard("Fan", liveFan));
        row.add(metricCard("Handoff", liveHandoff));
        p.add(row, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildBiasPage() {
        styleTable(biasTable);
        JPanel p = detailPage("Bias evidence", "Static equilibrium evidence and guarded bias-knot proposals.");
        JScrollPane table = new JScrollPane(biasTable); table.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(table, BorderLayout.CENTER);
        JScrollPane evidence = new JScrollPane(evidenceArea); evidence.setPreferredSize(new Dimension(0, 125)); evidence.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(evidence, BorderLayout.SOUTH);
        return p;
    }

    private JComponent buildPidPage() {
        styleTable(pidTable);
        JPanel p = detailPage("PID evidence", "Local static bias must be NO_CHANGE before a gain candidate is authoritative.");
        JScrollPane table = new JScrollPane(pidTable); table.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(table, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildSessionPage() {
        styleTable(sessionTable);
        JPanel p = detailPage("Sessions", "Comparable archived runs used to judge whether a manual change improved the controller.");
        JScrollPane table = new JScrollPane(sessionTable); table.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(table, BorderLayout.CENTER);
        return p;
    }

    private JComponent buildDiagnosticsPage() {
        JPanel p = detailPage("Diagnostics", "Detailed internal evidence and classification. This is not part of the normal driving workflow.");
        JScrollPane scroll = new JScrollPane(diagnosticsArea); scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        p.add(scroll, BorderLayout.CENTER);
        return p;
    }

    private void wire() {
        refresh.addActionListener(e -> reloadConfigurations());
        start.addActionListener(e -> startCapture());
        archive.addActionListener(e -> { if (coordinator != null) coordinator.stopAndArchive(); refreshUi(); });
        stop.addActionListener(e -> { if (coordinator != null) coordinator.stop("Stopped without archive"); refreshUi(); });
        clear.addActionListener(e -> { if (coordinator != null) coordinator.clearCurrent(); refreshUi(); });
        export.addActionListener(e -> exportCsv());
        configurations.addActionListener(e -> {
            if (coordinator != null && coordinator.isRunning()) coordinator.stop("Configuration selection changed - stopped");
        });
        taskList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && taskList.getSelectedValue() != null) {
                String selected = taskList.getSelectedValue();
                if (!"Guided Tuner".equals(selected) && navigationSidebar != null && navigationSidebar.isVisible()) setGuidedIntent(GuidedIntent.NONE);
                detailCards.show(detailPanel, selected); updateControls();
            }
        });
    }

    private void startCapture() {
        if (coordinator == null || activeIntent == GuidedIntent.NONE) return;
        String name = (String) configurations.getSelectedItem(); if (name == null) return;
        try {
            coordinator.start(name); // start() clears previous current evidence
            configCheckTicks = 0; timer.start(); state.setText(activeIntent.label + " capture running");
        } catch (Exception ex) { state.setText("Start failed: " + message(ex)); }
        refreshUi();
    }

    private void refreshUi() {
        if (coordinator == null) {
            stages.setStage(Stage.PREPARE);
            setBanner("Not connected", RED_SOFT, RED);
            objectiveKicker.setText("1. PREPARE");
            objectiveTitle.setText("Connect the ECU and choose a configuration");
            objectiveBody.setText("The guided tuner will use the validated internal M1/M2/M3 engines automatically. No milestone pages are required.");
            updateLive("-", "-");
            clearModels();
            updateControls();
            return;
        }

        state.setText(coordinator.status());
        config.setText(coordinator.configurationSummary());
        String live = coordinator.liveSummary();
        String diag = coordinator.diagnosticsSummary();
        updateLive(live, diag);
        renderBias();
        renderPid();
        renderSessions();
        renderEvidence();
        diagnosticsArea.setText("Controller: " + signature.getText() + "\n\n" +
                "Configuration:\n" + coordinator.configurationSummary() + "\n\n" +
                "Live:\n" + live + "\n\n" +
                "Diagnostics:\n" + diag + "\n\n" +
                "State:\n" + coordinator.status() + "\n" + coordinator.readiness());

        Stage stage = determineStage();
        stages.setStage(stage);
        renderObjective(stage);
        renderGuidedRows(stage);

        if (coordinator.isRunning() && ++configCheckTicks >= 20) {
            configCheckTicks = 0;
            if (!coordinator.configurationStillMatches()) {
                coordinator.stop("PID/travel/bias changed during capture - restart cleanly");
                refreshUi();
                return;
            }
        }
        if (!coordinator.isRunning() && timer.isRunning()) timer.stop();
        updateControls();
    }

    private Stage determineStage() {
        if (activeIntent == GuidedIntent.BIAS) {
            for (DcIacBiasKnotProposal r : coordinator.knotProposals()) if (r.isReady()) return Stage.RECHECK;
            if (biasConvergedAtControllerResolution()) return Stage.RESULT;
            if (!coordinator.staticEvidence().isEmpty() || coordinator.isRunning()) return Stage.CAPTURE_BIAS;
            return Stage.PREPARE;
        }
        if (activeIntent == GuidedIntent.PID) {
            for (DcIacPidRecommendation r : guidedPidRecommendations()) if (r.isReady()) return Stage.REVIEW;
            if (!coordinator.dynamicEvidence().isEmpty() || coordinator.isRunning()) return Stage.PID_STEPS;
            return Stage.PREPARE;
        }
        if (activeIntent == GuidedIntent.VALIDATION) {
            ValidationSummary v=validationSummary();
            if (coordinator.isRunning()) return v.coverageReady ? Stage.REVIEW : Stage.PID_STEPS;
            if (!coordinator.dynamicEvidence().isEmpty() || !coordinator.staticEvidence().isEmpty()) return Stage.REVIEW;
            return Stage.PREPARE;
        }
        return Stage.PREPARE;
    }

    private void renderObjective(Stage stage) {
        if (activeIntent == GuidedIntent.PID) { renderPidObjective(stage); return; }
        if (activeIntent == GuidedIntent.VALIDATION) { renderValidationObjective(); return; }
        if (activeIntent == GuidedIntent.NONE) {
            objectiveKicker.setText("DC IAC"); objectiveTitle.setText("Read-only evidence / history");
            objectiveBody.setText("Select Bias Curve, Position PID or Validation from the task navigator to start a new purpose-specific capture.");
            setBanner("No tuning task selected", BLUE_SOFT, BLUE); nextAction.setText("Next: choose a DC-IAC tuning task"); return;
        }
        double target = metric(coordinator.liveSummary(), "target");
        DcIacBiasKnotProposal nearest = nearestKnot(target);
        double knot = nearest == null ? 18.0 : nearest.getPosition();
        DcIacBiasKnotProposal ready = firstReadyKnot();
        DcIacPidRecommendation pid = firstReadyPid();
        objectiveKicker.setText((stage.index + 1) + ". " + stage.label.toUpperCase(Locale.US));

        switch (stage) {
            case PREPARE:
                objectiveTitle.setText("Tune the DC-IAC bias curve only");
                double cfgI = configurationGain("I"), cfgD = configurationGain("D");
                boolean pOnly = finite(cfgI) && finite(cfgD) && Math.abs(cfgI) < 0.0005 && Math.abs(cfgD) < 0.0005;
                objectiveBody.setText("This task tunes dcIdleBiasValues / eligible bias-bin placement only; it will never propose P/I/D.\n\n"
                        + "1. Warm/stabilize the engine.\n2. Keep fan/load state reasonably steady when practical.\n3. Hold at any repeatable position in the useful bias-curve region.\n4. Press Start Bias Capture.\n\n"
                        + "Exact firmware-knot targeting is NOT required. Complete rolling windows are retained and graded GOOD / OK / LOW; quality affects confidence, not whether capture is allowed. P-only (I=0, D=0) still gives the cleanest/direct result."
                        + (pOnly ? "\n\nCurrent I/D are zero: ideal for bias calibration." : "\n\nCurrent I/D are not both zero. Capture still proceeds; moving I/D terms will simply lower the evidence quality/confidence until the curve improves."));
                setBanner(pOnly ? "Ready for bias capture" : "Bias capture: I/D motion may delay qualification", pOnly ? BLUE_SOFT : AMBER_SOFT, pOnly ? BLUE : AMBER);
                nextAction.setText("Next: Start Bias Capture, then hold any useful repeatable position in the curve");
                break;
            case CAPTURE_BIAS:
                objectiveTitle.setText("Collect bias behavior at the position the system can actually hold");
                String read = coordinator.readiness();
                objectiveBody.setText("There is no exact-knot tolerance gate. Hold as steadily as practical anywhere inside a useful bias-curve segment; the projector uses the ACTUAL settled target and interpolates it onto the surrounding firmware knots.\n\nDo NOT make deliberate 3-4% PID steps in Bias Curve. Complete rolling windows are retained as GOOD, OK or LOW quality. GOOD/OK evidence can contribute to a converged correction; LOW evidence remains visible/context and does not stop collection.\n\nCurrent evidence status: " + (read == null || read.trim().isEmpty() ? "Waiting for live samples" : read));
                String upperRead = read == null ? "" : read.toUpperCase(Locale.US);
                if (upperRead.contains("GOOD")) setBanner(read, GREEN_SOFT, GREEN);
                else if (upperRead.contains("OK")) setBanner(read, BLUE_SOFT, BLUE);
                else if (!upperRead.isEmpty()) setBanner(read, AMBER_SOFT, AMBER);
                else setBanner("Collecting bias evidence", BLUE_SOFT, BLUE);
                nextAction.setText("Next: keep collecting useful regions until the segment fit supports a bounded knot change or NO_CHANGE");
                break;
            case RECHECK:
                if (ready != null) {
                    objectiveTitle.setText("Bias data collected - change the " + v1(ready.getPosition()) + "% knot");
                    objectiveBody.setText("This is an actual dcIdleBiasBins / dcIdleBiasValues point.\n\n" +
                            "Current bias: " + vBias(ready.getCurrentValue()) + "% duty\n" +
                            "Suggested bias: " + vBias(ready.getProposedValue()) + "% duty\n" +
                            "Writable change: " + signedBias(ready.getProposedCorrection()) + " duty points\n\n" +
                            "1. Stop + archive this capture.\n" +
                            "2. Apply ONLY this knot change manually in TunerStudio.\n" +
                            "3. Start a NEW clean capture at the same knot.\n" +
                            "4. Hold until the region returns NO_CHANGE.");
                    setBanner("DATA COLLECTED - APPLY KNOT, THEN RECHECK", GREEN_SOFT, GREEN);
                    nextAction.setText("Next: Stop + archive, change " + v1(ready.getPosition()) + "% bias from " + vBias(ready.getCurrentValue()) + " to " + vBias(ready.getProposedValue()) + ", then recapture");
                } else {
                    objectiveTitle.setText("Recheck the same bias region");
                    objectiveBody.setText("Start a new clean capture after the manual bias change and hold the same knot until the local static result becomes NO_CHANGE.");
                    setBanner("Recheck required", AMBER_SOFT, AMBER);
                    nextAction.setText("Next: Recapture the same knot after the manual bias change");
                }
                break;
            case PID_STEPS:
                double low = finite(target) ? target : knot;
                double high = low + 4.0;
                int openings = 0, closings = 0;
                for (DcIacDynamicEvidence e : coordinator.dynamicEvidence()) {
                    if (!e.isSafeLocalStep()) continue;
                    if (e.isOpening()) openings++;
                    if (e.isClosing()) closings++;
                }
                objectiveTitle.setText("Bias cleared - perform 3-4% actual target steps");
                objectiveBody.setText("Use repeated local steps around the cleared region. A practical sequence is approximately " +
                        v1(low) + " <-> " + v1(high) + "%.\n\n" +
                        "Need at least 2 opening + 2 closing steps around the same center.\n" +
                        "Let the valve settle between steps.\n" +
                        "Avoid fan transitions; fan-triggered steps are context-only.\n" +
                        "Never exceed 4.5% actual dcIdleTarget change.");
                setBanner("PID STEP CAPTURE: " + openings + "/2 opening, " + closings + "/2 closing", BLUE_SOFT, BLUE);
                nextAction.setText("Next: Collect 2 opening + 2 closing 3-4% steps with fan state stable");
                break;
            case REVIEW:
                if (pid != null) {
                    objectiveTitle.setText("PID candidate ready - change one gain only");
                    objectiveBody.setText("Action: " + pid.getPrimaryAction() + "\n\n" +
                            "Current P/I/D: " + v3(pid.getCurrentP()) + " / " + v3(pid.getCurrentI()) + " / " + v3(pid.getCurrentD()) + "\n" +
                            "Proposed P/I/D: " + v3(pid.getProposedP()) + " / " + v3(pid.getProposedI()) + " / " + v3(pid.getProposedD()) + "\n" +
                            "Confidence: " + v1(pid.getConfidencePercent()) + "%\n\n" +
                            "1. Stop + archive.\n2. Apply only the proposed gain manually.\n3. Start a new capture and repeat the same local test.\n4. Compare the two sessions.");
                    setBanner("DATA COLLECTED - PID CANDIDATE READY", GREEN_SOFT, GREEN);
                    nextAction.setText("Next: Archive, apply one proposed gain manually, then repeat the same step sequence");
                } else {
                    objectiveTitle.setText("Review the collected evidence");
                    objectiveBody.setText("Open PID evidence and Diagnostics for the detailed classification. Do not change gains from incomplete or contaminated evidence.");
                    setBanner("Review evidence", AMBER_SOFT, AMBER);
                    nextAction.setText("Next: Review the evidence quality before making any change");
                }
                break;
            case RESULT:
                if (activeIntent == GuidedIntent.BIAS && biasConvergedAtControllerResolution()) {
                    DcIacStaticRecommendation local = bestRunningIdleStatic();
                    objectiveTitle.setText("Bias region complete at ECU resolution");
                    objectiveBody.setText("The measured bias requirement is now close enough that the nearest value the ECU can actually store is the value already in the table.\n\n"
                            + "dcIdleBiasValues is integer-resolution on this ECU. The tuner therefore stops when another whole-number change is not supported; it does NOT require idlePositionSensor to return to exactly 18.000% or any other exact firmware-bin position.\n\n"
                            + (local == null ? "" : "Measured region: " + v1(local.getCenterTarget()) + "%\nResidual mathematical correction: " + signed(local.getRawCorrection()) + " duty points\n\n")
                            + "This region is accepted for Bias Curve. You may archive it, sample another useful position/segment, or continue to Position PID.");
                    setBanner("BIAS CONVERGED AT CONTROLLER RESOLUTION", GREEN_SOFT, GREEN);
                    nextAction.setText("Next: Stop + archive; sample another bias region or continue to Position PID / Validation");
                    break;
                }
                DcIacIterationComparison comparison = coordinator.lastComparison();
                objectiveTitle.setText("Compare the latest tuning iteration");
                if (comparison == null) {
                    objectiveBody.setText("A session has been archived. Repeat the same operating region after the manual change so the tuner can compare baseline and candidate.");
                    setBanner("Baseline archived", BLUE_SOFT, BLUE);
                } else {
                    objectiveBody.setText("Comparison: " + comparison.getStatus() + "\n\n" + comparison.getDetail() + "\n\nThe comparison is advisory only; the plugin never automatically keeps or reverts settings.");
                    Color bg = DcIacIterationComparison.IMPROVED.equals(comparison.getStatus()) ? GREEN_SOFT : AMBER_SOFT;
                    Color fg = DcIacIterationComparison.IMPROVED.equals(comparison.getStatus()) ? GREEN : AMBER;
                    setBanner("SESSION COMPARISON: " + comparison.getStatus(), bg, fg);
                }
                nextAction.setText("Next: Repeat comparable evidence until the bias/PID result is stable and repeatable");
                break;
        }
    }

    private void renderPidObjective(Stage stage) {
        DcIacPidRecommendation pid = firstReadyPid(); DcIacStaticRecommendation biasGate = bestRunningIdleStatic();
        objectiveKicker.setText("POSITION PID");
        if (stage == Stage.PREPARE) {
            objectiveTitle.setText("Capture the DC-IAC position-loop response");
            objectiveBody.setText("This task tunes P/I/D only. It never proposes a bias-curve change.\n\nStart a clean PID capture, then make repeated local 3-4% actual dcIdleTarget steps with at least 2 opening + 2 closing responses. Guided task order is user-controlled: Bias Curve is observed only as related context. An unresolved bias condition lowers confidence and is suggested as a follow-up, but it never blocks PID collection or results.");
            setBanner("Ready for PID-specific capture", BLUE_SOFT, BLUE); nextAction.setText("Next: Start PID Capture, then collect comparable 3-4% opening/closing steps"); return;
        }
        int openings=0, closings=0, context=0; for (DcIacDynamicEvidence e : coordinator.dynamicEvidence()) { if (e.isSafeLocalStep() && e.isOpening()) openings++; else if (e.isSafeLocalStep() && e.isClosing()) closings++; else context++; }
        if (pid != null) {
            objectiveTitle.setText("PID candidate ready - change one gain only");
            objectiveBody.setText("Action: " + pid.getPrimaryAction() + "\n\nCurrent P/I/D: " + v3(pid.getCurrentP()) + " / " + v3(pid.getCurrentI()) + " / " + v3(pid.getCurrentD()) + "\nProposed P/I/D: " + v3(pid.getProposedP()) + " / " + v3(pid.getProposedI()) + " / " + v3(pid.getProposedD()) + "\nConfidence: " + v1(pid.getConfidencePercent()) + "%\n\nStop + archive, apply only the proposed gain manually, then repeat the same local step pattern for comparison.");
            setBanner("PID DATA COLLECTED - CANDIDATE READY", GREEN_SOFT, GREEN); nextAction.setText("Next: Archive, apply one PID gain, then repeat the same Position PID capture");
        } else {
            objectiveTitle.setText("Collect PID steps - " + openings + "/2 opening, " + closings + "/2 closing");
            objectiveBody.setText("This capture ignores bias-curve proposals as tuning actions.\n\nNeed 2 opening + 2 closing safe local steps around the same operating center. Let each response settle, keep fan/load state stable, and keep actual target steps at 3-4% (hard maximum 4.5%).\n\n" + (biasGate == null ? "Bias Curve state is being observed in the background as advisory context; it does not block this task." : "Related Bias status (advisory only): " + biasGate.getStatus() + ".") + (context > 0 ? "\nContext-only/rejected steps so far: " + context + "." : ""));
            setBanner("PID STEP CAPTURE", BLUE_SOFT, BLUE); nextAction.setText("Next: Continue comparable opening/closing steps until the PID evidence gate resolves");
        }
    }

    private void renderValidationObjective() {
        ValidationSummary v=validationSummary(); objectiveKicker.setText("SYSTEM VALIDATION"); objectiveTitle.setText(v.title); objectiveBody.setText(v.detail);
        if ("PASS".equals(v.status)) setBanner("VALIDATION PASS", GREEN_SOFT, GREEN); else if ("COLLECT_MORE".equals(v.status)) setBanner("VALIDATION - COLLECT MORE", BLUE_SOFT, BLUE); else setBanner("VALIDATION - REVIEW", AMBER_SOFT, AMBER);
        nextAction.setText(v.nextAction);
    }

    private static final class ValidationSummary { final String status,title,detail,nextAction; final boolean coverageReady; ValidationSummary(String s,String t,String d,String n,boolean c){status=s;title=t;detail=d;nextAction=n;coverageReady=c;} }

    private ValidationSummary validationSummary() {
        int open=0,close=0,context=0,outside=0,saturation=0,valid=0; java.util.List<Double> settle=new java.util.ArrayList<Double>(), overshoot=new java.util.ArrayList<Double>(), steady=new java.util.ArrayList<Double>();
        for (DcIacDynamicEvidence e:coordinator.dynamicEvidence()) { DcIacMeasurement m=e.getMeasurement(); if(e.isProcessValueOutsideTravel())outside++; if(m.isPredictedSaturationRisk())saturation++; if(e.isSafeLocalStep()){if(e.isOpening())open++;if(e.isClosing())close++;if(m.isValid())valid++;addFinite(settle,m.getSettlingSeconds());addFinite(overshoot,Math.abs(m.getOvershootPercentOfStep()));addFinite(steady,Math.abs(m.getSteadyStateError()));}else context++; }
        boolean coverage=open>=2&&close>=2; DcIacStaticRecommendation biasGate=bestRunningIdleStatic(); DcIacPidRecommendation pidStatus=bestPidRecommendation();
        String metrics="Coverage: "+open+" opening / "+close+" closing safe local steps\nValid measured responses: "+valid+"\nMedian settling: "+v2(median(settle))+" s\nMedian |overshoot|: "+v1(median(overshoot))+"% of step\nMedian |steady target error|: "+v2(median(steady))+" position-%\nTravel violations: "+outside+" · saturation-risk events: "+saturation+" · context-only/rejected: "+context+"\nTarget handoff: "+liveHandoff.getText();
        if(!coverage)return new ValidationSummary("COLLECT_MORE","Exercise the tuned system in both directions","Validation never proposes settings. Perform the same local 3-4% target movements you expect the position controller to handle.\n\n"+metrics,"Next: collect at least 2 opening + 2 closing safe local responses",false);
        if(outside>0||saturation>0)return new ValidationSummary("REVIEW","Validation found a safety/authority problem",metrics+"\n\nThe system should not be accepted as validated while travel or predicted command-authority limits are being hit.","Next: inspect the affected events in PID Evidence / Diagnostics before accepting the tune",true);
        if(biasGate!=null&&!biasGate.isBiasReadyForPid()&&!biasConvergedAtControllerResolution())return new ValidationSummary("REVIEW","Validation found unresolved feed-forward behavior",metrics+"\n\nRelated Bias advisory: "+biasGate.getStatus()+" — "+biasGate.getDetail()+"\nValidation result is still shown; use Bias Curve separately if you want to improve feed-forward attribution.","Next: Bias Curve is a suggested follow-up; repeat Validation whenever you choose",true);
        if(pidStatus!=null&&DcIacPidRecommendation.NO_CHANGE.equals(pidStatus.getStatus()))return new ValidationSummary("PASS","DC-IAC response is supported by the measured evidence",metrics+"\n\nThe existing PID evidence engine reports NO_CHANGE for the measured region, with bias/feed-forward cleared and no travel/saturation violation. This is a validation result, not a tuning proposal.","Next: archive/export this validation run and repeat at another important operating/load region if desired",true);
        if(pidStatus!=null&&pidStatus.isReady())return new ValidationSummary("REVIEW","System is measurable but further PID tuning is still supported",metrics+"\n\nThe PID evidence engine still sees an actionable response issue. Validation deliberately hides the gain proposal; use Position PID if you want to tune it.","Next: use Position PID, then repeat Validation",true);
        return new ValidationSummary("REVIEW","Coverage is complete; evidence is not yet a clean acceptance",metrics+"\n\nEnough bidirectional coverage exists, but the existing evidence gates have not resolved to a clean NO_CHANGE state. Continue comparable validation or inspect the evidence classification.","Next: review PID Evidence / Diagnostics or collect another comparable cycle",true);
    }

    private boolean biasConvergedAtControllerResolution(){
        if(coordinator==null)return false;
        DcIacStaticRecommendation local=bestRunningIdleStatic();
        if(local==null)return false;
        if(local.isBiasReadyForPid())return true;
        boolean supported=false;
        for(DcIacBiasKnotProposal proposal:coordinator.knotProposals()){
            if(proposal==null||proposal.getSupportingRegions()<=0)continue;
            supported=true;
            if(proposal.isReady()||DcIacBiasKnotProposal.INCONSISTENT.equals(proposal.getStatus()))return false;
        }
        return supported;
    }

    private java.util.List<DcIacPidRecommendation> guidedPidRecommendations() {
        if (coordinator == null) return new java.util.ArrayList<DcIacPidRecommendation>();
        return guidedPidEngine.evaluate(coordinator.dynamicEvidence(), coordinator.staticRecommendations(),
                configurationGain("P"), configurationGain("I"), configurationGain("D"));
    }

    private DcIacStaticRecommendation bestRunningIdleStatic(){DcIacStaticRecommendation best=null;for(DcIacStaticRecommendation r:coordinator.staticRecommendations()){if(r==null||r.getOperatingContext()!=se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext.RUNNING_IDLE)continue;if(best==null||r.getEvidenceWindows()>best.getEvidenceWindows())best=r;}return best;}
    private DcIacPidRecommendation bestPidRecommendation(){DcIacPidRecommendation best=null;for(DcIacPidRecommendation r:guidedPidRecommendations()){if(r==null)continue;if(best==null||r.getEvidenceCount()>best.getEvidenceCount())best=r;}return best;}
    private static void addFinite(java.util.List<Double> v,double x){if(finite(x))v.add(x);}
    private static double median(java.util.List<Double> v){if(v==null||v.isEmpty())return Double.NaN;java.util.Collections.sort(v);int n=v.size();return n%2==1?v.get(n/2):(v.get(n/2-1)+v.get(n/2))*0.5;}

    private void renderGuidedRows(Stage stage) {
        guidedModel.setRowCount(0);
        if (activeIntent == GuidedIntent.BIAS) {
            for (DcIacBiasKnotProposal r:coordinator.knotProposals()) guidedModel.addRow(new Object[]{"Bias knot "+v1(r.getPosition())+"%",vBias(r.getCurrentValue())+" -> "+vBias(r.getProposedValue()),r.getStatus()});
            if(guidedModel.getRowCount()==0)for(DcIacStaticRecommendation r:coordinator.staticRecommendations())guidedModel.addRow(new Object[]{"Static region "+v1(r.getCenterTarget())+"%","windows="+r.getEvidenceWindows()+", correction="+signed(r.getRawCorrection()),r.getStatus()});
            if(guidedModel.getRowCount()==0)guidedModel.addRow(new Object[]{"Bias evidence","Hold a stable actual target near a real bias knot","Collecting"}); return;
        }
        if (activeIntent == GuidedIntent.PID) {
            DcIacStaticRecommendation gate=bestRunningIdleStatic(); if(gate!=null&&!gate.isBiasReadyForPid()&&!biasConvergedAtControllerResolution())guidedModel.addRow(new Object[]{"Related Bias advisory",v1(gate.getCenterTarget())+"% · correction "+signed(gate.getRawCorrection()),gate.getStatus()+" · does not block PID"});
            for(DcIacPidRecommendation r:guidedPidRecommendations())guidedModel.addRow(new Object[]{"PID region "+v1(r.getCenterTarget())+"%",r.getOpeningCount()+" open / "+r.getClosingCount()+" close, |I0|="+v2(r.getMedianInitialIAbs()),r.getStatus()});
            int open=0,close=0,context=0;for(DcIacDynamicEvidence e:coordinator.dynamicEvidence()){if(e.isSafeLocalStep()&&e.isOpening())open++;else if(e.isSafeLocalStep()&&e.isClosing())close++;else context++;}
            guidedModel.addRow(new Object[]{"Opening steps",String.valueOf(open),open>=2?"Enough":"Need "+Math.max(0,2-open)}); guidedModel.addRow(new Object[]{"Closing steps",String.valueOf(close),close>=2?"Enough":"Need "+Math.max(0,2-close)}); guidedModel.addRow(new Object[]{"Context-only/rejected",String.valueOf(context),context==0?"None":"Not used for PID ID"}); return;
        }
        if (activeIntent == GuidedIntent.VALIDATION) { ValidationSummary v=validationSummary(); guidedModel.addRow(new Object[]{"Validation",v.title,v.status}); int open=0,close=0,outside=0,sat=0;for(DcIacDynamicEvidence e:coordinator.dynamicEvidence()){if(e.isSafeLocalStep()&&e.isOpening())open++;if(e.isSafeLocalStep()&&e.isClosing())close++;if(e.isProcessValueOutsideTravel())outside++;if(e.getMeasurement().isPredictedSaturationRisk())sat++;} guidedModel.addRow(new Object[]{"Bidirectional coverage",open+" opening / "+close+" closing",open>=2&&close>=2?"Enough":"Collect more"}); guidedModel.addRow(new Object[]{"Travel / authority",outside+" travel, "+sat+" saturation-risk",outside==0&&sat==0?"OK":"Review"}); guidedModel.addRow(new Object[]{"Target handoff",liveHandoff.getText(),"MISMATCH".equals(liveHandoff.getText())?"Review":"Observed"}); return; }
        guidedModel.addRow(new Object[]{"Read-only view","Select Bias Curve, Position PID or Validation to start a capture","No tuning action"});
    }

    private void renderBias() {
        biasModel.setRowCount(0);
        for (DcIacBiasKnotProposal r : coordinator.knotProposals()) {
            biasModel.addRow(new Object[] {v1(r.getPosition()), vBias(r.getCurrentValue()), vBias(r.getProposedValue()), r.getSupportingRegions(), v1(r.getConfidencePercent()) + "%", r.getStatus()});
        }
    }

    private void renderPid() {
        pidModel.setRowCount(0);
        for (DcIacPidRecommendation r : guidedPidRecommendations()) {
            pidModel.addRow(new Object[] {
                    v1(r.getCenterTarget()),
                    r.getEvidenceCount() + " (q" + r.getQuantitativeCount() + "/l" + r.getSourceLimitedCount() + ")",
                    r.getOpeningCount() + "/" + r.getClosingCount(),
                    v2(r.getMedianInitialIAbs()),
                    v1(r.getMedianOvershootPercent()) + "%",
                    r.getPrimaryAction(),
                    r.getStatus()
            });
        }
    }

    private void renderSessions() {
        sessionModel.setRowCount(0);
        for (DcIacTuningSessionSummary r : coordinator.sessions()) {
            sessionModel.addRow(new Object[] {
                    r.getSequence(),
                    v3(r.getP()) + " / " + v3(r.getI()) + " / " + v3(r.getD()),
                    r.getStaticEvidenceCount() + "/" + r.getDynamicEvidenceCount(),
                    v1(r.getCenterTarget()),
                    v2(r.getMedianBiasCorrectionAbs()),
                    v1(r.getMedianOvershootAbs()),
                    v1(r.getMedianPeakDuty()),
                    r.getDominantBiasStatus(),
                    r.getDominantPidStatus()
            });
        }
    }

    private void renderEvidence() {
        StringBuilder b = new StringBuilder();
        b.append("STATIC EVIDENCE\n");
        for (DcIacStaticRecommendation r : coordinator.staticRecommendations()) {
            b.append(r.getOperatingContext().displayName()).append("  center ").append(v2(r.getCenterTarget()))
                    .append("  windows ").append(r.getEvidenceWindows())
                    .append("  correction ").append(signed(r.getRawCorrection()))
                    .append("  trend ").append(signed(r.getCorrectionTrendPerSecond())).append("/s")
                    .append("  ").append(r.getStatus()).append('\n')
                    .append("  ").append(r.getDetail()).append('\n');
        }
        b.append("\nRAW STATIC WINDOWS: ").append(coordinator.staticEvidence().size()).append('\n');
        for (DcIacStaticEvidence e : coordinator.staticEvidence()) {
            b.append("  target ").append(v2(e.getTarget())).append("  bias ").append(v2(e.getConfiguredBias()))
                    .append("  required ").append(v2(e.getRequiredBias())).append("  correction ").append(signed(e.getCorrection()))
                    .append("  quality ").append(e.getEvidence().getQuality())
                    .append("  ").append(e.getOperatingContext().displayName()).append('\n')
                    .append("    ").append(e.getEvidence().getQualityDetail()).append('\n');
        }
        evidenceArea.setText(b.toString());
    }

    private void updateLive(String live, String diag) {
        liveTarget.setText(metricText(live, "target", "%"));
        liveActual.setText(metricText(live, "actual", "%"));
        liveDuty.setText(metricText(live, "duty", "%"));
        liveI.setText(metricText(live, "I", ""));
        liveRpm.setText(metricText(live, "RPM", " rpm"));
        liveVoltage.setText(metricText(live, "V", " V"));
        liveFan.setText(diag != null && diag.contains("fan1 ON") ? "ON" : diag != null && diag.contains("fan1 OFF") ? "OFF" : "-");
        if (diag != null && diag.contains("MISMATCH")) liveHandoff.setText("MISMATCH");
        else if (diag != null && diag.contains("target handoff med")) liveHandoff.setText("OK");
        else liveHandoff.setText("-");
    }

    private void exportCsv() {
        if (coordinator == null || activeIntent == GuidedIntent.NONE) return;
        JFileChooser chooser=new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter("CSV files","csv"));
        String stem=activeIntent==GuidedIntent.BIAS?"dc-iac-bias-curve.csv":activeIntent==GuidedIntent.PID?"dc-iac-position-pid.csv":"dc-iac-validation.csv"; chooser.setSelectedFile(new File(stem));
        if(chooser.showSaveDialog(this)!=JFileChooser.APPROVE_OPTION)return; File file=chooser.getSelectedFile(); if(!file.getName().toLowerCase(Locale.US).endsWith(".csv"))file=new File(file.getParentFile(),file.getName()+".csv");
        try{BufferedWriter w=Files.newBufferedWriter(file.toPath(),StandardCharsets.UTF_8);try{w.write("row_type,context,target,metric_a,metric_b,metric_c,status,detail\n"); row(w,"TASK",activeIntent.name(),"","","","",coordinator.isRunning()?"RUNNING":"STOPPED","Purpose-specific export; not a raw sample log");
            if(activeIntent==GuidedIntent.BIAS){for(DcIacStaticEvidence r:coordinator.staticEvidence())row(w,"STATIC_EVIDENCE",r.getOperatingContext().name(),n(r.getTarget()),n(r.getConfiguredBias()),n(r.getRequiredBias()),n(r.getCorrection()),r.getEvidence().getQuality(),r.getCurveSegment()+"; "+r.getEvidence().getQualityDetail());for(DcIacStaticRecommendation r:coordinator.staticRecommendations())row(w,"STATIC_RECOMMENDATION",r.getOperatingContext().name(),n(r.getCenterTarget()),n(r.getCurrentBias()),n(r.getRequiredBias()),n(r.getProposedCorrection()),r.getStatus(),r.getDetail());for(DcIacBiasKnotProposal r:coordinator.knotProposals())row(w,"BIAS_KNOT","RUNNING_IDLE",n(r.getPosition()),n(r.getCurrentValue()),n(r.getProposedValue()),n(r.getProposedCorrection()),r.getStatus(),r.getDetail());}
            else{for(DcIacDynamicEvidence r:coordinator.dynamicEvidence()){DcIacMeasurement m=r.getMeasurement();row(w,"DYNAMIC",r.getOperatingContext().name(),n(m.getOperatingCenterTarget()),n(m.getStepMagnitude()),n(m.getOvershootPercentOfStep()),n(m.getSteadyStateError()),m.getResultCode(),"settling="+n(m.getSettlingSeconds())+"; source="+r.getStepSource()+"; initialI="+n(m.getInitialITerm())+"; flags="+r.getCombinedEvidenceFlags());} if(activeIntent==GuidedIntent.PID){for(DcIacPidRecommendation r:guidedPidRecommendations())row(w,"PID_RECOMMENDATION",r.getOperatingContext().name(),n(r.getCenterTarget()),n(r.getProposedP()),n(r.getProposedI()),n(r.getProposedD()),r.getStatus(),r.getPrimaryAction()+": "+r.getDetail());}else{ValidationSummary v=validationSummary();row(w,"VALIDATION_SUMMARY","SYSTEM","","","","",v.status,v.title+": "+v.detail.replace('\n',' '));}}
        }finally{w.close();}state.setText("Exported "+activeIntent.label+": "+file.getAbsolutePath());}catch(Exception ex){state.setText("Export failed: "+message(ex));}
    }

    private void reloadConfigurations() {
        if (coordinator == null) return;
        String old = (String) configurations.getSelectedItem();
        configurations.removeAllItems();
        for (String name : coordinator.configurations()) configurations.addItem(name);
        if (old != null) configurations.setSelectedItem(old);
        if (configurations.getSelectedIndex() < 0 && configurations.getItemCount() > 0) configurations.setSelectedIndex(0);
        updateControls();
    }

    private void updateControls() {
        boolean connected = controllerAccess != null && coordinator != null;
        boolean running = connected && coordinator.isRunning();
        configurations.setEnabled(connected && !running);
        refresh.setEnabled(connected && !running);
        boolean tuningTask=activeIntent!=GuidedIntent.NONE; start.setEnabled(connected&&!running&&tuningTask&&configurations.getItemCount()>0);
        archive.setEnabled(running&&tuningTask); stop.setEnabled(running&&tuningTask);
        boolean taskEvidence=false;
        if(connected){taskEvidence=activeIntent==GuidedIntent.BIAS?!coordinator.staticEvidence().isEmpty():(activeIntent==GuidedIntent.PID||activeIntent==GuidedIntent.VALIDATION)?!coordinator.dynamicEvidence().isEmpty():false;}
        export.setEnabled(connected&&taskEvidence);
        clear.setEnabled(connected && !running);
    }

    private DcIacBiasKnotProposal nearestKnot(double target) {
        DcIacBiasKnotProposal best = null;
        double distance = Double.POSITIVE_INFINITY;
        for (DcIacBiasKnotProposal r : coordinator.knotProposals()) {
            double d = finite(target) ? Math.abs(r.getPosition() - target) : Math.abs(r.getPosition() - 18.0);
            if (d < distance) { distance = d; best = r; }
        }
        return best;
    }

    private DcIacBiasKnotProposal firstReadyKnot() {
        for (DcIacBiasKnotProposal r : coordinator.knotProposals()) if (r.isReady()) return r;
        return null;
    }

    private DcIacPidRecommendation firstReadyPid() {
        for (DcIacPidRecommendation r : guidedPidRecommendations()) if (r.isReady()) return r;
        return null;
    }

    private double configurationGain(String gain) {
        String summary=coordinator==null?"":coordinator.configurationSummary(); if(summary==null)return Double.NaN; Matcher m=Pattern.compile("(?:^|\\s)"+Pattern.quote(gain)+"=([-+0-9.eE]+)").matcher(summary); if(!m.find())return Double.NaN; try{return Double.parseDouble(m.group(1));}catch(Exception ex){return Double.NaN;}
    }

    private void setBanner(String text, Color bg, Color fg) {
        statusBanner.setText(text == null || text.trim().isEmpty() ? "-" : text);
        statusBanner.setBackground(bg);
        statusBanner.setForeground(fg);
    }

    // Package-private bridge for the autonomous DC-IAC shell.  This deliberately exposes
    // the already-validated coordinator instead of creating a second evidence engine.
    DcIacTunerCoordinator workflowCoordinator() { return coordinator; }
    ControllerAccess workflowControllerAccess() { return controllerAccess; }
    String workflowConfigurationName() {
        Object selected = configurations.getSelectedItem();
        return selected == null ? "" : selected.toString().trim();
    }
    boolean workflowConnected() { return controllerAccess != null && coordinator != null; }

    /**
     * Autonomous-mode recovery hook for TunerStudio lifecycle ordering.
     * Some hosts expose ControllerAccess after the panel was constructed/initialized,
     * so Autonomous must be able to attach the same Guided coordinator lazily instead
     * of silently disabling Start forever. This does not begin capture or write anything.
     */
    void workflowEnsureConnected(ControllerAccess access) {
        if (access == null) return;
        if (controllerAccess == access && coordinator != null) return;
        String currentSignature = signature.getText();
        connect(access, currentSignature);
    }

    /** Apply the current AE Tuner Light/Dark palette without touching capture state. */
    void applyTheme() {
        syncThemeColors();
        retargetTree(this);
        setBackground(BG);
        detailPanel.setBackground(BG);
        taskList.setBackground(PANEL);
        configurations.setBackground(CARD);
        configurations.setForeground(TEXT);

        styleButton(refresh, false);
        styleButton(start, true);
        styleButton(archive, false);
        styleButton(stop, false);
        styleButton(export, false);
        styleButton(clear, false);
        styleTable(guidedTable);
        styleTable(biasTable);
        styleTable(pidTable);
        styleTable(sessionTable);

        // Re-evaluate semantic status colours from the current evidence state.
        refreshUi();
        stages.repaint();
        StableScrollSupport.installRecursively(this);
        repaint();
    }

    private static void retargetTree(Component c) {
        if (c == null) return;
        Color bg = retargetBackground(c.getBackground());
        Color fg = retargetForeground(c.getForeground());
        if (bg != null) c.setBackground(bg);
        if (fg != null) c.setForeground(fg);

        if (c instanceof JComponent) {
            JComponent jc = (JComponent)c;
            jc.setBorder(retargetBorder(jc.getBorder()));
        }
        if (c instanceof JScrollPane) {
            JScrollPane sp = (JScrollPane)c;
            if (sp.getViewport() != null) sp.getViewport().setBackground(CARD);
        }
        if (c instanceof Container) {
            for (Component child : ((Container)c).getComponents()) retargetTree(child);
        }
    }

    private static Color retargetBackground(Color c) {
        if (c == null) return null;
        if (isAny(c, 242,244,247, 24,28,34)) return BG;
        if (isAny(c, 255,255,255, 34,40,48)) return CARD;
        if (isAny(c, 235,238,243, 29,34,41)) return PANEL;
        if (isAny(c, 229,237,252, 31,49,72)) return BLUE_SOFT;
        if (isAny(c, 226,246,233, 31,59,43)) return GREEN_SOFT;
        if (isAny(c, 255,246,221, 70,55,28)) return AMBER_SOFT;
        if (isAny(c, 255,235,232, 72,38,38)) return RED_SOFT;
        if (isAny(c, 238,240,243, 45,52,62)) return BUTTON;
        if (isAny(c, 238,241,245, 43,50,59)) return UiTheme.tableHeader();
        if (isAny(c, 228,231,236, 59,66,76)) return INACTIVE_STEP;
        if (isAny(c, 178,207,232, 31,64,96)) return UiTheme.taskSelectedBg();
        if (isAny(c, 45,54,66, 45,54,66)) return UiTheme.taskAvailableBg();
        if (isAny(c, 229,232,236, 27,32,39)) return UiTheme.disabled();
        if (isAny(c, 255,197,54, 255,197,67)) return UiTheme.amber();
        return c;
    }

    private static Color retargetForeground(Color c) {
        if (c == null) return null;
        if (isAny(c, 28,33,40, 232,237,243)) return TEXT;
        if (isAny(c, 96,105,118, 160,172,187)) return TEXT_MUTED;
        if (isAny(c, 35,72,108, 136,181,225)) return NAVY;
        if (isAny(c, 42,108,214, 88,157,255)) return BLUE;
        if (isAny(c, 41,157,84, 69,190,108)) return GREEN;
        if (isAny(c, 229,143,0, 246,172,50)) return AMBER;
        if (isAny(c, 213,66,58, 241,103,96)) return RED;
        if (isAny(c, 22,39,58, 255,255,255)) {
            // White is also a semantic marker colour, so selected task text is
            // handled by TaskCellRenderer instead of globally remapping white.
            if (!sameRgb(c, Color.WHITE)) return UiTheme.taskSelectedText();
        }
        return c;
    }

    private static javax.swing.border.Border retargetBorder(javax.swing.border.Border b) {
        if (b == null) return null;
        if (b instanceof javax.swing.border.CompoundBorder) {
            javax.swing.border.CompoundBorder cb = (javax.swing.border.CompoundBorder)b;
            return BorderFactory.createCompoundBorder(retargetBorder(cb.getOutsideBorder()), retargetBorder(cb.getInsideBorder()));
        }
        if (b instanceof javax.swing.border.LineBorder) {
            javax.swing.border.LineBorder lb = (javax.swing.border.LineBorder)b;
            Color c = retargetBorderColor(lb.getLineColor());
            return new javax.swing.border.LineBorder(c, lb.getThickness(), lb.getRoundedCorners());
        }
        if (b instanceof javax.swing.border.MatteBorder) {
            javax.swing.border.MatteBorder mb = (javax.swing.border.MatteBorder)b;
            java.awt.Insets in = mb.getBorderInsets();
            Color c = retargetBorderColor(mb.getMatteColor());
            if (c != null) return BorderFactory.createMatteBorder(in.top, in.left, in.bottom, in.right, c);
        }
        if (b instanceof javax.swing.border.TitledBorder) {
            javax.swing.border.TitledBorder tb = (javax.swing.border.TitledBorder)b;
            return BorderFactory.createTitledBorder(retargetBorder(tb.getBorder()), tb.getTitle(), tb.getTitleJustification(),
                    tb.getTitlePosition(), tb.getTitleFont(), retargetForeground(tb.getTitleColor()));
        }
        return b;
    }

    private static Color retargetBorderColor(Color c) {
        if (c == null) return BORDER;
        if (isAny(c, 207,213,221, 69,78,90)) return BORDER;
        if (isAny(c, 187,193,202, 78,90,105)) return BUTTON_BORDER;
        if (isAny(c, 171,187,204, 82,101,122)) return UiTheme.focusButtonBorder();
        if (isAny(c, 121,159,191, 86,149,211)) return UiTheme.taskSelectedBorder();
        if (isAny(c, 168,111,0, 176,119,18)) return UiTheme.amberDark();
        if (isAny(c, 42,108,214, 88,157,255)) return BLUE;
        return c;
    }

    private static boolean isAny(Color c, int lr, int lg, int lb, int dr, int dg, int db) {
        return c != null && ((c.getRed()==lr && c.getGreen()==lg && c.getBlue()==lb) ||
                (c.getRed()==dr && c.getGreen()==dg && c.getBlue()==db));
    }

    private static boolean sameRgb(Color a, Color b) {
        return a != null && b != null && a.getRed()==b.getRed() && a.getGreen()==b.getGreen() && a.getBlue()==b.getBlue();
    }

    private void clearModels() {
        guidedModel.setRowCount(0); biasModel.setRowCount(0); pidModel.setRowCount(0); sessionModel.setRowCount(0);
        evidenceArea.setText(""); diagnosticsArea.setText("");
    }

    private static JPanel detailPage(String title, String subtitle) {
        JPanel p = new JPanel(new BorderLayout(0, 7));
        p.setOpaque(false);
        JPanel head = new JPanel(new BorderLayout(8, 0)); head.setOpaque(false);
        JLabel t = new JLabel(title); t.setFont(TITLE); t.setForeground(TEXT);
        JLabel s = new JLabel(subtitle); s.setFont(BODY); s.setForeground(TEXT_MUTED);
        head.add(t, BorderLayout.WEST); head.add(s, BorderLayout.CENTER);
        p.add(head, BorderLayout.NORTH);
        return p;
    }

    private static JPanel cardPanel(java.awt.LayoutManager layout) {
        JPanel p = new JPanel(layout);
        p.setBackground(CARD);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        return p;
    }

    private static JPanel metricCard(String title, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(0, 2));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER),
                BorderFactory.createEmptyBorder(0, 8, 0, 8)));
        JLabel t = new JLabel(title);
        t.setFont(SMALL); t.setForeground(TEXT_MUTED);
        p.add(t, BorderLayout.NORTH);
        p.add(value, BorderLayout.CENTER);
        return p;
    }

    private static JLabel valueLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(BLUE);
        l.setFont(new Font("Dialog", Font.BOLD, 16));
        return l;
    }

    private static JPanel compactLine(String label, JLabel value) {
        JPanel p = new JPanel(new BorderLayout(5, 0));
        p.setOpaque(false);
        JLabel l = new JLabel(label + ":"); l.setFont(SMALL); l.setForeground(TEXT_MUTED);
        value.setFont(new Font("Dialog", Font.BOLD, 10)); value.setForeground(TEXT);
        p.add(l, BorderLayout.WEST); p.add(value, BorderLayout.CENTER);
        return p;
    }

    private static JLabel pill(String text, Color bg, Color fg) {
        JLabel l = new JLabel(text);
        l.setFont(new Font("Dialog", Font.BOLD, 10));
        l.setOpaque(true); l.setBackground(bg); l.setForeground(fg);
        l.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.focusButtonBorder()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)));
        return l;
    }

    private static void styleButton(JButton b, boolean primary) {
        b.setFont(new Font("Dialog", Font.BOLD, 11));
        b.setFocusPainted(false); b.setOpaque(true);
        b.setBackground(primary ? UiTheme.amber() : BUTTON);
        b.setForeground(primary ? new Color(42, 31, 4) : TEXT);
        b.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(primary ? UiTheme.amberDark() : BUTTON_BORDER, primary ? 2 : 1),
                BorderFactory.createEmptyBorder(primary ? 4 : 5, 9, primary ? 4 : 5, 9)));
    }

    private static void styleTable(JTable table) {
        table.setFont(BODY);
        table.setRowHeight(22);
        table.setForeground(TEXT);
        table.setBackground(CARD);
        table.setGridColor(BORDER);
        table.setSelectionBackground(BLUE_SOFT);
        table.setSelectionForeground(TEXT);
        table.getTableHeader().setFont(new Font("Dialog", Font.BOLD, 10));
        table.getTableHeader().setBackground(UiTheme.tableHeader());
        table.getTableHeader().setForeground(TEXT);
    }

    private static JTextArea infoArea() {
        JTextArea a = new JTextArea();
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFont(BODY);
        a.setForeground(TEXT);
        return a;
    }

    private static DefaultTableModel readOnlyModel(String[] columns) {
        return new DefaultTableModel(columns, 0) {
            public boolean isCellEditable(int row, int column) { return false; }
        };
    }

    private static double metric(String live, String key) {
        if (live == null) return Double.NaN;
        Pattern p = Pattern.compile("(?:^|\\s/\\s)" + Pattern.quote(key) + "\\s*([+-]?[0-9]+(?:\\.[0-9]+)?)");
        Matcher m = p.matcher(live);
        if (!m.find()) return Double.NaN;
        try { return Double.parseDouble(m.group(1)); } catch (RuntimeException ex) { return Double.NaN; }
    }

    private static String metricText(String live, String key, String suffix) {
        double x = metric(live, key);
        return finite(x) ? v2(x) + suffix : "-";
    }

    private static String v1(double x) { return finite(x) ? String.format(Locale.US, "%.1f", x) : "-"; }
    private static String v2(double x) { return finite(x) ? String.format(Locale.US, "%.2f", x) : "-"; }
    private static String vBias(double x) { return finite(x) ? Long.toString(Math.round(x)) : "-"; }
    private static String signedBias(double x) { return finite(x) ? ((x >= 0 ? "+" : "") + Long.toString(Math.round(x))) : "-"; }
    private static String v3(double x) { return finite(x) ? String.format(Locale.US, "%.3f", x) : "-"; }
    private static String n(double x) { return finite(x) ? String.format(Locale.US, "%.9f", x) : ""; }
    private static String signed(double x) { return finite(x) ? String.format(Locale.US, "%+.2f", x) : "-"; }
    private static boolean finite(double x) { return !Double.isNaN(x) && !Double.isInfinite(x); }
    private static String message(Throwable t) { return t == null ? "Unknown" : t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage(); }

    private static void row(BufferedWriter w, String... fields) throws Exception {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) w.write(',');
            String s = fields[i] == null ? "" : fields[i];
            w.write('"'); w.write(s.replace("\"", "\"\"")); w.write('"');
        }
        w.write('\n');
    }

    private final class TaskCellRenderer extends DefaultListCellRenderer {
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
            JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focus);
            l.setOpaque(true);
            l.setFont(new Font("Dialog", index == 0 ? Font.BOLD : Font.PLAIN, 12));
            l.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(selected ? UiTheme.taskSelectedBorder() : UiTheme.taskAvailableBorder()),
                    BorderFactory.createEmptyBorder(8, 9, 8, 8)));
            l.setBackground(selected ? UiTheme.taskSelectedBg() : UiTheme.taskAvailableBg());
            l.setForeground(selected ? UiTheme.taskSelectedText() : UiTheme.taskAvailableText());
            return l;
        }
    }

    /** AE Tuner-style circular workflow stepper. */
    private static final class StageStrip extends JPanel {
        private Stage stage = Stage.PREPARE;
        private final String[] subtitles = {
                "Check readiness", "Static hold", "Verify bias", "Local steps", "See result", "Compare run"
        };

        StageStrip() {
            setOpaque(false);
            setPreferredSize(new Dimension(760, 72));
        }

        void setStage(Stage stage) {
            this.stage = stage == null ? Stage.PREPARE : stage;
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Stage[] all = Stage.values();
            int n = all.length;
            int margin = 48;
            int y = 24;
            int available = Math.max(1, getWidth() - margin * 2);
            int step = available / Math.max(1, n - 1);
            g2.setStroke(new BasicStroke(2f));
            g2.setColor(BORDER);
            g2.drawLine(margin, y, margin + step * (n - 1), y);
            for (int i = 0; i < n; i++) {
                int x = margin + step * i;
                boolean complete = i < stage.index;
                boolean active = i == stage.index;
                g2.setColor(complete ? GREEN : active ? BLUE : INACTIVE_STEP);
                g2.fillOval(x - 13, y - 13, 26, 26);
                g2.setColor(complete || active ? Color.WHITE : TEXT_MUTED);
                g2.setFont(new Font("Dialog", Font.BOLD, 11));
                String marker = complete ? "✓" : String.valueOf(i + 1);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(marker, x - fm.stringWidth(marker) / 2, y + 4);

                g2.setColor(TEXT);
                g2.setFont(new Font("Dialog", Font.BOLD, 10));
                fm = g2.getFontMetrics();
                g2.drawString(all[i].label, x - fm.stringWidth(all[i].label) / 2, y + 27);
                g2.setColor(TEXT_MUTED);
                g2.setFont(new Font("Dialog", Font.PLAIN, 8));
                fm = g2.getFontMetrics();
                String sub = subtitles[i];
                g2.drawString(sub, x - fm.stringWidth(sub) / 2, y + 39);
            }
            g2.dispose();
        }
    }

}
