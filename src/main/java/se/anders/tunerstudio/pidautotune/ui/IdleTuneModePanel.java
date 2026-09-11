package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/**
 * Keeps Idle Closed Loop as three navigation destinations while allowing Tune Idle to switch
 * between the existing guided/manual workflow and the autonomous RAM-only workflow.
 */
final class IdleTuneModePanel extends JPanel {
    private static final String GUIDED = "GUIDED";
    private static final String AUTO = "AUTO";

    private final IdleGuidedPanel guided;
    private final AutonomousIdleTuningPanel autonomous;
    private final CardLayout cards = new CardLayout();
    private final JPanel body = new JPanel(cards);
    private final JPanel modeBar = new JPanel(new BorderLayout(8, 0));
    private final JLabel title = new JLabel("Tune Idle");
    private final JLabel sub = new JLabel("Choose guided/manual operation or audited autonomous RAM tuning");
    private final JToggleButton guidedButton = new JToggleButton("Guided Workflow");
    private final JToggleButton autoButton = new JToggleButton("Autonomous RAM");
    private String visible = GUIDED;

    IdleTuneModePanel(IdleGuidedPanel guided, AutonomousIdleTuningPanel autonomous) {
        super(new BorderLayout(0, 7));
        this.guided = guided;
        this.autonomous = autonomous;
        buildHeader();
        body.add(guided, GUIDED);
        body.add(autonomous, AUTO);
        add(modeBar, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        showGuided();
        applyTheme();
    }

    private void buildHeader() {
        JPanel labels = new JPanel(new java.awt.GridLayout(0, 1, 0, 1));
        labels.setOpaque(false);
        title.setFont(new Font("Dialog", Font.BOLD, 14));
        sub.setFont(new Font("Dialog", Font.PLAIN, 9));
        labels.add(title); labels.add(sub);

        ButtonGroup group = new ButtonGroup(); group.add(guidedButton); group.add(autoButton);
        JPanel choices = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0)); choices.setOpaque(false);
        choices.add(guidedButton); choices.add(autoButton);
        guidedButton.addActionListener(e -> {
            if (autonomous.isRunning()) { autoButton.setSelected(true); return; }
            showGuided();
        });
        autoButton.addActionListener(e -> showAutonomous());

        modeBar.add(labels, BorderLayout.WEST); modeBar.add(choices, BorderLayout.EAST);
        modeBar.setBorder(new EmptyBorder(2, 3, 2, 3));
    }

    void showTuneHome() {
        modeBar.setVisible(true);
        if (autonomous.isRunning()) showAutonomous();
        else if (AUTO.equals(visible)) showAutonomous();
        else showGuided();
        guided.selectTask(IdleGuidedPanel.Task.TUNE_IDLE);
    }

    void showSessionsLogs() {
        if (autonomous.isRunning()) { showAutonomous(); return; }
        showGuided(); modeBar.setVisible(false); guided.selectTask(IdleGuidedPanel.Task.SESSIONS_LOGS);
    }

    void showSetupDiagnostics() {
        if (autonomous.isRunning()) { showAutonomous(); return; }
        showGuided(); modeBar.setVisible(false); guided.selectTask(IdleGuidedPanel.Task.SETUP_DIAGNOSTICS);
    }

    void showGuided() {
        visible = GUIDED; cards.show(body, GUIDED); guidedButton.setSelected(true); autoButton.setSelected(false); applyTheme();
    }

    void showAutonomous() {
        visible = AUTO; modeBar.setVisible(true); cards.show(body, AUTO); autoButton.setSelected(true); guidedButton.setSelected(false); applyTheme();
    }

    boolean isAutonomousRunning() { return autonomous.isRunning(); }
    AutonomousIdleTuningPanel autonomousPanel() { return autonomous; }
    IdleGuidedPanel guidedPanel() { return guided; }

    void connect(ControllerAccess access, String signature) {
        guided.connect(access, signature); autonomous.connect(access);
    }
    void disconnect() { autonomous.disconnect(); guided.disconnect(); }
    void setControllerSignature(String signature) { guided.setControllerSignature(signature); }

    void applyTheme() {
        setBackground(UiTheme.bg()); body.setBackground(UiTheme.bg()); modeBar.setBackground(UiTheme.bg());
        title.setForeground(UiTheme.text()); sub.setForeground(UiTheme.muted());
        styleChoice(guidedButton, GUIDED.equals(visible)); styleChoice(autoButton, AUTO.equals(visible));
        guided.applyTheme(); autonomous.applyTheme();
        repaint();
    }

    private static void styleChoice(JToggleButton b, boolean selected) {
        b.setFont(new Font("Dialog", Font.BOLD, 10)); b.setFocusPainted(false); b.setOpaque(true);
        if (selected) {
            b.setBackground(UiTheme.softBlue()); b.setForeground(UiTheme.blue());
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.blue()), new EmptyBorder(5,9,5,9)));
        } else {
            b.setBackground(UiTheme.button()); b.setForeground(UiTheme.text());
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.buttonBorder()), new EmptyBorder(5,9,5,9)));
        }
    }
}
