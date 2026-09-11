package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.JViewport;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
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
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * Plugin-wide guided workspace.
 *
 * The old top-level DC-IAC / Idle tab choice and the Idle six-tab workflow are
 * replaced by one persistent AE-style task navigator. Existing controller and
 * evidence engines stay alive behind the selected task pages.
 */
public final class PidAutotuneWorkspacePanel extends JPanel {
    private enum Target {
        DC_BIAS,
        DC_POSITION_PID,
        DC_VALIDATION,
        DC_SESSIONS,
        DC_BIAS_EVIDENCE,
        DC_PID_EVIDENCE,
        IDLE_TUNE,
        IDLE_SESSIONS_LOGS,
        IDLE_SETUP_DIAGNOSTICS
    }

    private static final class NavItem {
        final String title;
        final String subtitle;
        final Target target;
        final boolean planned;
        private NavItem(String title, String subtitle, Target target, boolean planned) {
            this.title = title; this.subtitle = subtitle; this.target = target; this.planned = planned;
        }
        static NavItem group(String title) { return new NavItem(title, null, null, false); }
        static NavItem task(String title, String subtitle, Target target) { return new NavItem(title, subtitle, target, false); }
        static NavItem planned(String title, String subtitle) { return new NavItem(title, subtitle, null, true); }
        boolean selectable() { return target != null; }
    }

    private final DcIacTunerPanel dcIacTunerPanel = new DcIacTunerPanel();
    private final AutonomousDcIacTuningPanel autonomousDcIacPanel = new AutonomousDcIacTuningPanel(dcIacTunerPanel);
    private final DcIacTuneModePanel dcIacTunePanel = new DcIacTuneModePanel(dcIacTunerPanel, autonomousDcIacPanel);
    private final IdleGuidedPanel idlePanel = new IdleGuidedPanel();
    private final AutonomousIdleTuningPanel autonomousIdlePanel = new AutonomousIdleTuningPanel();
    private final IdleTuneModePanel idleTunePanel = new IdleTuneModePanel(idlePanel, autonomousIdlePanel);
    private final CardLayout contentCards = new CardLayout();
    private final JPanel content = new JPanel(contentCards);
    private final JScrollPane dcWorkspaceScroll;
    private final JScrollPane idleWorkspaceScroll;
    private final JPanel workspaceHeader = new JPanel(new BorderLayout(12, 0));
    private final JPanel themeBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
    private final JToggleButton lightSide = new JToggleButton("Light Side");
    private final JToggleButton darkSide = new JToggleButton("Dark Side");
    private final JLabel productTitle = new JLabel("EPICEFI PID Tuner");
    private final JLabel productSub = new JLabel("Guided + autonomous controller tuning • RAM candidates never Burn");
    private final DefaultListModel<NavItem> navModel = new DefaultListModel<NavItem>();
    private final JList<NavItem> navigator = new JList<NavItem>(navModel);
    private final JPanel sidebar = new JPanel(new BorderLayout(0, 7));
    private final JSplitPane split;
    private String controllerSignature = "Not yet reported by TunerStudio";
    private int lastSelectableIndex = 7;
    private Target activeTarget;

    public PidAutotuneWorkspacePanel() {
        super(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        dcIacTunerPanel.setExternalNavigationMode(true);
        buildHeader();
        buildNavigation();

        dcWorkspaceScroll = workspaceScroll(dcIacTunePanel);
        idleWorkspaceScroll = workspaceScroll(idleTunePanel);
        content.add(dcWorkspaceScroll, "DC");
        content.add(idleWorkspaceScroll, "IDLE");

        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, content);
        split.setResizeWeight(0.0);
        split.setDividerLocation(246);
        split.setDividerSize(7);
        split.setContinuousLayout(true);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);

        // Stabilize every current nested scroll pane (tables, evidence, logs, diagnostics,
        // autonomous history, and the persistent task menu).  This is idempotent and is
        // repeated after routing/theme changes for any lazily-created legacy panes.
        StableScrollSupport.installRecursively(this);

        idlePanel.setDestinationListener(task -> selectIdleDestination(task));

        navigator.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            int idx = navigator.getSelectedIndex();
            if (idx < 0) return;
            NavItem item = navModel.get(idx);
            if (!item.selectable()) {
                navigator.setSelectedIndex(lastSelectableIndex);
                return;
            }
            lastSelectableIndex = idx;
            route(item.target);
        });
        navigator.setSelectedIndex(findTarget(Target.IDLE_TUNE));
        applyTheme();
    }

    /**
     * Main tuning pages can be taller than the usable TunerStudio content area on
     * 1366x768-class laptops. Keep the global product header and task navigator
     * fixed, but let the active workspace scroll vertically. The wrapper tracks
     * viewport width so horizontal scrolling is not introduced just because a
     * child has a large preferred width.
     */
    private static JScrollPane workspaceScroll(JComponent view) {
        FitWidthView wrapper = new FitWidthView(view);
        JScrollPane scroll = new JScrollPane(
                wrapper,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        StableScrollSupport.install(scroll);
        scroll.setWheelScrollingEnabled(true);
        scroll.getVerticalScrollBar().setUnitIncrement(22);
        scroll.getVerticalScrollBar().setBlockIncrement(110);
        return scroll;
    }

    private static final class FitWidthView extends JPanel implements Scrollable {
        FitWidthView(JComponent child) {
            super(new BorderLayout());
            setOpaque(false);
            add(child, BorderLayout.CENTER);
        }

        @Override public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 22;
        }

        @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return Math.max(80, visibleRect.height - 48);
        }

        @Override public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    private void scrollCurrentWorkspaceToTop(Target target) {
        JScrollPane scroll = (target == Target.IDLE_TUNE
                || target == Target.IDLE_SESSIONS_LOGS
                || target == Target.IDLE_SETUP_DIAGNOSTICS)
                ? idleWorkspaceScroll : dcWorkspaceScroll;
        if (scroll != null) {
            // Route changes already execute on the Swing EDT. Reset immediately so no stale
            // invokeLater task can fire after the user has switched mode or started capture.
            StableScrollSupport.setVerticalPosition(scroll, 0);
        }
    }

    private void buildHeader() {
        ButtonGroup group = new ButtonGroup();
        group.add(lightSide); group.add(darkSide);
        lightSide.addActionListener(new ThemeAction(UiTheme.Side.LIGHT_SIDE));
        darkSide.addActionListener(new ThemeAction(UiTheme.Side.DARK_SIDE));
        themeBar.add(lightSide); themeBar.add(darkSide);

        JPanel titles = new JPanel(new GridLayout(0, 1, 0, 0));
        titles.setOpaque(false);
        productTitle.setFont(new Font("Dialog", Font.BOLD, 16));
        productSub.setFont(new Font("Dialog", Font.PLAIN, 10));
        titles.add(productTitle); titles.add(productSub);
        workspaceHeader.add(titles, BorderLayout.WEST);
        workspaceHeader.add(themeBar, BorderLayout.EAST);
        workspaceHeader.setBorder(BorderFactory.createEmptyBorder(1, 3, 1, 3));
        add(workspaceHeader, BorderLayout.NORTH);
    }

    private void buildNavigation() {
        navModel.addElement(NavItem.group("DC IAC"));
        navModel.addElement(NavItem.task("Bias Curve", "Guided or autonomous feed-forward calibration", Target.DC_BIAS));
        navModel.addElement(NavItem.task("Position PID", "Guided or autonomous H-bridge response", Target.DC_POSITION_PID));
        navModel.addElement(NavItem.task("Validation", "Read-only whole-system response and target tracking", Target.DC_VALIDATION));
        navModel.addElement(NavItem.task("Sessions", "Archived actuator tuning runs", Target.DC_SESSIONS));
        navModel.addElement(NavItem.task("Bias Evidence", "Static equilibrium details", Target.DC_BIAS_EVIDENCE));
        navModel.addElement(NavItem.task("PID Evidence", "Step-response classification", Target.DC_PID_EVIDENCE));

        navModel.addElement(NavItem.group("IDLE CLOSED LOOP"));
        navModel.addElement(NavItem.task("Tune Idle", "Guided workflow or autonomous RAM tuning", Target.IDLE_TUNE));
        navModel.addElement(NavItem.task("Sessions & Logs", "History, log analysis, dataset evidence", Target.IDLE_SESSIONS_LOGS));
        navModel.addElement(NavItem.task("Setup & Diagnostics", "Mapping, capture settings, raw diagnostics", Target.IDLE_SETUP_DIAGNOSTICS));

        navModel.addElement(NavItem.group("PLANNED"));
        navModel.addElement(NavItem.planned("Boost Closed Loop", "Future controller tuner"));
        navModel.addElement(NavItem.planned("Boost Feed Forward", "Future feed-forward tuner"));

        navigator.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        navigator.setCellRenderer(new NavRenderer());
        navigator.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

        JScrollPane scroll = new JScrollPane(
                navigator,
                JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        StableScrollSupport.install(scroll);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        JPanel head = new JPanel(new GridLayout(0, 1, 0, 1));
        head.setOpaque(false);
        JLabel h = new JLabel("Tuning tasks"); h.setFont(new Font("Dialog", Font.BOLD, 13));
        JLabel sub = new JLabel("Choose the job — the tuner chooses the view"); sub.setFont(new Font("Dialog", Font.PLAIN, 9));
        head.add(h); head.add(sub);
        head.putClientProperty("nav.title", h); head.putClientProperty("nav.sub", sub);

        JLabel hint = new JLabel("Autonomous RAM tuning never burns settings");
        hint.setFont(new Font("Dialog", Font.PLAIN, 9));
        hint.putClientProperty("nav.hint", Boolean.TRUE);

        sidebar.add(head, BorderLayout.NORTH);
        sidebar.add(scroll, BorderLayout.CENTER);
        sidebar.add(hint, BorderLayout.SOUTH);
        sidebar.setPreferredSize(new Dimension(240, 620));
    }

    private void route(Target t) {
        if (t == null) return;
        // Only a real top-level destination change may reset the workspace viewport.
        // Live capture/state refreshes and mode-card changes must never chase the scrollbar.
        if (activeTarget != t) {
            scrollCurrentWorkspaceToTop(t);
            activeTarget = t;
        }
        if (dcIacTunePanel.isAutonomousRunning()) {
            if (t == Target.DC_POSITION_PID) {
                contentCards.show(content, "DC"); dcIacTunePanel.showAutonomousPid(); return;
            }
            if (t == Target.DC_BIAS) {
                contentCards.show(content, "DC"); dcIacTunePanel.showAutonomousBias(); return;
            }
            if (t == Target.DC_VALIDATION) {
                contentCards.show(content, "DC"); return;
            }
            int dcIndex = findTarget(Target.DC_BIAS);
            if (dcIndex >= 0 && navigator.getSelectedIndex() != dcIndex) navigator.setSelectedIndex(dcIndex);
            contentCards.show(content, "DC"); dcIacTunePanel.showAutonomousBias(); return;
        }
        if (idleTunePanel.isAutonomousRunning() && t != Target.IDLE_TUNE) {
            int idleIndex = findTarget(Target.IDLE_TUNE);
            if (idleIndex >= 0 && navigator.getSelectedIndex() != idleIndex) { navigator.setSelectedIndex(idleIndex); }
            contentCards.show(content, "IDLE"); idleTunePanel.showAutonomous(); return;
        }
        switch (t) {
            case DC_BIAS:
                contentCards.show(content, "DC"); dcIacTunePanel.selectBiasDestination(); break;
            case DC_POSITION_PID:
                contentCards.show(content, "DC"); dcIacTunePanel.selectPidDestination(); break;
            case DC_VALIDATION:
                contentCards.show(content, "DC"); dcIacTunePanel.selectValidationDestination(); break;
            case DC_SESSIONS:
                contentCards.show(content, "DC"); dcIacTunePanel.showGuidedTask("Sessions"); break;
            case DC_BIAS_EVIDENCE:
                contentCards.show(content, "DC"); dcIacTunePanel.showGuidedTask("Bias evidence"); break;
            case DC_PID_EVIDENCE:
                contentCards.show(content, "DC"); dcIacTunePanel.showGuidedTask("PID evidence"); break;
            case IDLE_TUNE:
                contentCards.show(content, "IDLE"); idleTunePanel.showTuneHome(); break;
            case IDLE_SESSIONS_LOGS:
                contentCards.show(content, "IDLE"); idleTunePanel.showSessionsLogs(); break;
            case IDLE_SETUP_DIAGNOSTICS:
                contentCards.show(content, "IDLE"); idleTunePanel.showSetupDiagnostics(); break;
        }
        StableScrollSupport.installRecursively(this);
        applyTheme();
    }

    private void selectIdleDestination(IdleGuidedPanel.Task task) {
        Target target = task == IdleGuidedPanel.Task.SESSIONS_LOGS ? Target.IDLE_SESSIONS_LOGS
                : task == IdleGuidedPanel.Task.SETUP_DIAGNOSTICS ? Target.IDLE_SETUP_DIAGNOSTICS
                : Target.IDLE_TUNE;
        int index = findTarget(target);
        if (index >= 0) navigator.setSelectedIndex(index);
    }

    private int findTarget(Target target) {
        for (int i = 0; i < navModel.size(); i++) {
            NavItem item = navModel.get(i);
            if (item.target == target) return i;
        }
        return -1;
    }

    private final class ThemeAction implements ActionListener {
        private final UiTheme.Side side;
        ThemeAction(UiTheme.Side side) { this.side = side; }
        @Override public void actionPerformed(ActionEvent e) {
            if (UiTheme.side() != side) UiTheme.set(side);
            applyTheme();
        }
    }

    private void applyTheme() {
        setBackground(UiTheme.bg());
        workspaceHeader.setBackground(UiTheme.bg());
        themeBar.setBackground(UiTheme.bg());
        productTitle.setForeground(UiTheme.text());
        productSub.setForeground(UiTheme.muted());
        styleThemeChoice(lightSide, UiTheme.Side.LIGHT_SIDE);
        styleThemeChoice(darkSide, UiTheme.Side.DARK_SIDE);

        sidebar.setBackground(UiTheme.panel());
        sidebar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.border()), BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        navigator.setBackground(UiTheme.panel());
        navigator.setForeground(UiTheme.text());
        navigator.setCellRenderer(new NavRenderer());
        for (Component c : sidebar.getComponents()) {
            if (c instanceof JScrollPane) {
                c.setBackground(UiTheme.panel());
                ((JScrollPane)c).getViewport().setBackground(UiTheme.panel());
            } else if (c instanceof JLabel) {
                c.setForeground(UiTheme.muted());
            } else if (c instanceof JPanel) {
                JPanel p = (JPanel)c;
                Object h = p.getClientProperty("nav.title"); if (h instanceof JLabel) ((JLabel)h).setForeground(UiTheme.text());
                Object s = p.getClientProperty("nav.sub"); if (s instanceof JLabel) ((JLabel)s).setForeground(UiTheme.muted());
            }
        }
        split.setBackground(UiTheme.bg());
        content.setBackground(UiTheme.bg());
        styleWorkspaceScroll(dcWorkspaceScroll);
        styleWorkspaceScroll(idleWorkspaceScroll);

        dcIacTunePanel.applyTheme();
        idleTunePanel.applyTheme();
        StableScrollSupport.installRecursively(this);
        repaint();
    }

    private static void styleWorkspaceScroll(JScrollPane scroll) {
        if (scroll == null) return;
        StableScrollSupport.install(scroll);
        scroll.setBackground(UiTheme.bg());
        scroll.getViewport().setBackground(UiTheme.bg());
        scroll.setBorder(null);
        JScrollBar vertical = scroll.getVerticalScrollBar();
        if (vertical != null) {
            vertical.setUI(new ThemeScrollBarUI());
            vertical.setBackground(UiTheme.panel());
            vertical.setForeground(UiTheme.buttonBorder());
            vertical.setUnitIncrement(22);
            vertical.setBlockIncrement(110);
        }
    }

    private static void styleThemeChoice(AbstractButton b, UiTheme.Side side) {
        boolean selected = UiTheme.side() == side;
        b.setSelected(selected);
        b.setFont(new Font("Dialog", Font.BOLD, 10));
        b.setFocusPainted(false);
        b.setOpaque(true);
        if (selected) {
            b.setBackground(UiTheme.softBlue()); b.setForeground(UiTheme.blue());
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.blue()), BorderFactory.createEmptyBorder(4, 8, 4, 8)));
        } else {
            b.setBackground(UiTheme.button()); b.setForeground(UiTheme.text());
            b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.buttonBorder()), BorderFactory.createEmptyBorder(5, 9, 5, 9)));
        }
    }

    /** Shared compatibility skin used by the guided Idle shell for retained legacy components. */
    static void applyLegacyTheme(Component c) {
        if (c == null) return;
        Color bg = c.getBackground(); Color fg = c.getForeground();
        if (isLegacyNeutral(bg, 238,238,238) || isThemeBg(bg)) c.setBackground(UiTheme.bg());
        else if (isLegacyNeutral(bg,255,255,255) || isThemeCard(bg)) c.setBackground(UiTheme.card());
        else if (isLegacyNeutral(bg,184,207,229) || isThemePanel(bg)) c.setBackground(UiTheme.panel());

        if (isLegacyNeutral(fg,51,51,51) || isThemeText(fg)) c.setForeground(UiTheme.text());
        else if (isLegacyNeutral(fg,184,207,229)) c.setForeground(UiTheme.taskDisabledText());

        if (c instanceof JTable) {
            JTable table=(JTable)c; table.setBackground(UiTheme.card()); table.setForeground(UiTheme.text());
            table.setGridColor(UiTheme.border()); table.setSelectionBackground(UiTheme.softBlue()); table.setSelectionForeground(UiTheme.text());
            if(table.getTableHeader()!=null){table.getTableHeader().setBackground(UiTheme.tableHeader());table.getTableHeader().setForeground(UiTheme.text());}
        } else if (c instanceof JTextArea || c instanceof JTextField) {
            c.setBackground(c.isEnabled()?UiTheme.card():UiTheme.disabled()); c.setForeground(c.isEnabled()?UiTheme.text():UiTheme.taskDisabledText());
        } else if (c instanceof JComboBox) {
            c.setBackground(c.isEnabled()?UiTheme.card():UiTheme.disabled()); c.setForeground(c.isEnabled()?UiTheme.text():UiTheme.taskDisabledText());
        } else if (c instanceof JTabbedPane) {
            // Keep hidden guided tab strips hidden; only style visible nested tabs.
            if (!( ((JTabbedPane)c).getUI().getClass().getName().contains("HiddenTabbedPaneUI") )) ((JTabbedPane)c).setUI(new ThemeTabbedPaneUI());
            c.setBackground(UiTheme.bg()); c.setForeground(UiTheme.text());
        } else if (c instanceof JScrollBar) {
            ((JScrollBar)c).setUI(new ThemeScrollBarUI()); c.setBackground(UiTheme.panel()); c.setForeground(UiTheme.buttonBorder());
        } else if (c instanceof JScrollPane || c instanceof JViewport || c instanceof JSplitPane) {
            c.setBackground(UiTheme.bg()); c.setForeground(UiTheme.text());
        }
        if (c instanceof AbstractButton) {
            AbstractButton b=(AbstractButton)c; b.setBackground(b.isEnabled()?UiTheme.button():UiTheme.disabled()); b.setForeground(b.isEnabled()?UiTheme.text():UiTheme.taskDisabledText());
        }
        if (c instanceof Container) for(Component child:((Container)c).getComponents()) applyLegacyTheme(child);
    }

    private static boolean isLegacyNeutral(Color c,int r,int g,int b){return c!=null&&c.getRed()==r&&c.getGreen()==g&&c.getBlue()==b;}
    private static boolean isThemeBg(Color c){return matchesEither(c,new Color(242,244,247),new Color(24,28,34));}
    private static boolean isThemeCard(Color c){return matchesEither(c,Color.WHITE,new Color(34,40,48));}
    private static boolean isThemePanel(Color c){return matchesEither(c,new Color(235,238,243),new Color(29,34,41));}
    private static boolean isThemeText(Color c){return matchesEither(c,new Color(28,33,40),new Color(232,237,243));}
    private static boolean matchesEither(Color c,Color a,Color b){return c!=null&&(sameRgb(c,a)||sameRgb(c,b));}
    private static boolean sameRgb(Color a,Color b){return a!=null&&b!=null&&a.getRed()==b.getRed()&&a.getGreen()==b.getGreen()&&a.getBlue()==b.getBlue();}

    private final class NavRenderer implements ListCellRenderer<NavItem> {
        @Override public Component getListCellRendererComponent(JList<? extends NavItem> list, NavItem value, int index, boolean selected, boolean focus) {
            if (value == null) return new JLabel("");
            if (value.subtitle == null && !value.planned && value.target == null) {
                JLabel g = new JLabel(value.title);
                g.setOpaque(true);
                g.setBackground(UiTheme.panel());
                g.setFont(new Font("Dialog", Font.BOLD, 9));
                g.setForeground(UiTheme.taskGroupOff());
                g.setBorder(BorderFactory.createEmptyBorder(8, 5, 3, 4));
                g.setPreferredSize(new Dimension(210, 25));
                return g;
            }
            String status = value.planned ? "  •  PLANNED" : "";
            JLabel row = new JLabel("<html><b>" + html(value.title) + "</b><br>" + html(value.subtitle == null ? "" : value.subtitle + status) + "</html>");
            row.setFont(new Font("Dialog", Font.PLAIN, 10));
            Color bg = value.planned ? UiTheme.taskDisabledBg() : (selected ? UiTheme.taskSelectedBg() : UiTheme.taskAvailableBg());
            Color fg = value.planned ? UiTheme.taskDisabledText() : (selected ? UiTheme.taskSelectedText() : UiTheme.taskAvailableText());
            Color border = value.planned ? UiTheme.taskDisabledBorder() : (selected ? UiTheme.taskSelectedBorder() : UiTheme.taskAvailableBorder());
            row.setOpaque(true); row.setBackground(bg); row.setForeground(fg);
            row.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(border), BorderFactory.createEmptyBorder(5,7,5,7)));
            row.setPreferredSize(new Dimension(210, 46));
            return row;
        }
        private String html(String s) {
            if (s == null) return "";
            return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
    }

    private static final class ThemeTabbedPaneUI extends javax.swing.plaf.basic.BasicTabbedPaneUI {
        @Override protected void installDefaults(){super.installDefaults();tabAreaInsets=new java.awt.Insets(2,4,0,4);selectedTabPadInsets=new java.awt.Insets(0,0,0,0);contentBorderInsets=new java.awt.Insets(1,1,1,1);}
        @Override protected void paintTabBackground(Graphics g,int tabPlacement,int tabIndex,int x,int y,int w,int h,boolean selected){g.setColor(selected?UiTheme.taskSelectedBg():UiTheme.panel());g.fillRect(x,y,w,h);}
        @Override protected void paintTabBorder(Graphics g,int tabPlacement,int tabIndex,int x,int y,int w,int h,boolean selected){g.setColor(selected?UiTheme.taskSelectedBorder():UiTheme.border());g.drawRect(x,y,Math.max(0,w-1),Math.max(0,h-1));}
        @Override protected void paintText(Graphics g,int tabPlacement,Font font,FontMetrics metrics,int tabIndex,String title,Rectangle textRect,boolean selected){g.setFont(font);g.setColor(selected?UiTheme.taskSelectedText():UiTheme.text());g.drawString(title,textRect.x,textRect.y+metrics.getAscent());}
        @Override protected void paintFocusIndicator(Graphics g,int tabPlacement,Rectangle[] rects,int tabIndex,Rectangle iconRect,Rectangle textRect,boolean selected){}
        @Override protected void paintContentBorder(Graphics g,int tabPlacement,int selectedIndex){int x=0,y=calculateTabAreaHeight(tabPlacement,runCount,maxTabHeight),w=tabPane.getWidth(),h=tabPane.getHeight()-y;if(tabPlacement==LEFT){x=calculateTabAreaWidth(tabPlacement,runCount,maxTabWidth);y=0;w-=x;h=tabPane.getHeight();}else if(tabPlacement==RIGHT){y=0;w-=calculateTabAreaWidth(tabPlacement,runCount,maxTabWidth);h=tabPane.getHeight();}else if(tabPlacement==BOTTOM){y=0;h-=calculateTabAreaHeight(tabPlacement,runCount,maxTabHeight);}g.setColor(UiTheme.border());g.drawRect(x,y,Math.max(0,w-1),Math.max(0,h-1));}
    }

    private static final class ThemeScrollBarUI extends javax.swing.plaf.basic.BasicScrollBarUI {
        @Override protected void configureScrollBarColors(){trackColor=UiTheme.panel();thumbColor=UiTheme.buttonBorder();thumbDarkShadowColor=UiTheme.border();thumbHighlightColor=UiTheme.muted();thumbLightShadowColor=UiTheme.buttonBorder();}
        @Override protected JButton createDecreaseButton(int orientation){return arrow(orientation);} @Override protected JButton createIncreaseButton(int orientation){return arrow(orientation);}
        private JButton arrow(int orientation){javax.swing.plaf.basic.BasicArrowButton b=new javax.swing.plaf.basic.BasicArrowButton(orientation,UiTheme.button(),UiTheme.buttonBorder(),UiTheme.text(),UiTheme.border());b.setBorder(BorderFactory.createLineBorder(UiTheme.border()));return b;}
    }

    public void connect(ControllerAccess access, String signature) {
        // Autonomous DC-IAC temporarily owns idleMode + cltIdleCorrTable. Restore those
        // while the old ControllerAccess is still valid before DcIacTunerPanel.connect()
        // internally disconnects/replaces its coordinator.
        autonomousDcIacPanel.controllerDisconnecting();
        setControllerSignature(signature);
        dcIacTunerPanel.connect(access, controllerSignature);
        idleTunePanel.connect(access, controllerSignature);
    }

    public void disconnect() {
        // Restore temporary autonomous actuator-control context before the guided panel
        // drops the TunerStudio parameter server reference.
        autonomousDcIacPanel.controllerDisconnecting();
        dcIacTunerPanel.disconnect();
        idleTunePanel.disconnect();
    }

    public void setControllerSignature(String value) {
        if (value == null || value.trim().isEmpty()) return;
        controllerSignature = value.trim();
        dcIacTunerPanel.setControllerSignature(controllerSignature);
        idleTunePanel.setControllerSignature(controllerSignature);
    }
}
