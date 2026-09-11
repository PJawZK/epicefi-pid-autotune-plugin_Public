package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.PidAutotunePlugin;
import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisResult;
import se.anders.tunerstudio.pidautotune.analysis.IdleAnalysisSettings;
import se.anders.tunerstudio.pidautotune.analysis.IdleEvent;
import se.anders.tunerstudio.pidautotune.analysis.IdleLogAnalyzer;
import se.anders.tunerstudio.pidautotune.controller.ControllerDataAccess;
import se.anders.tunerstudio.pidautotune.dataset.AnalyzedLog;
import se.anders.tunerstudio.pidautotune.dataset.DatasetAssessment;
import se.anders.tunerstudio.pidautotune.dataset.DatasetEvent;
import se.anders.tunerstudio.pidautotune.dataset.TuneSnapshot;
import se.anders.tunerstudio.pidautotune.dataset.VssSourceMode;
import se.anders.tunerstudio.pidautotune.log.IdleLogData;
import se.anders.tunerstudio.pidautotune.log.LogChannelDefinition;
import se.anders.tunerstudio.pidautotune.log.MslLogReader;
import se.anders.tunerstudio.pidautotune.model.DiagnosticRow;
import se.anders.tunerstudio.pidautotune.model.DiagnosticTableModel;
import se.anders.tunerstudio.pidautotune.model.DatasetEventTableModel;
import se.anders.tunerstudio.pidautotune.model.IdleEventTableModel;
import se.anders.tunerstudio.pidautotune.model.LogChannelMapping;
import se.anders.tunerstudio.pidautotune.model.LogChannelTableModel;
import se.anders.tunerstudio.pidautotune.model.RecommendationGainTableModel;
import se.anders.tunerstudio.pidautotune.recommendation.ConservativePidRecommendationEngine;
import se.anders.tunerstudio.pidautotune.recommendation.PidRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.RecommendationSettings;
import se.anders.tunerstudio.pidautotune.recommendation.ResponseSummary;
import se.anders.tunerstudio.pidautotune.profile.EpicEfiIdleProfile;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.border.Border;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Main read-only EPICEFI idle PID discovery, live capture, and log-analysis UI. */
public final class PidAutotunePanel extends JPanel {
    private static final DecimalFormat ONE = new DecimalFormat("0.0");
    private static final DecimalFormat GAIN = new DecimalFormat("0.####");
    private static final String CONFIG_TOOLTIP =
            "Select which loaded TunerStudio ECU configuration should be inspected. " +
            "A normal single-ECU project usually contains one entry.";
    private static final String PROFILE_TOOLTIP =
            "Select the controller profile to analyse. Version 0.4.3 contains only the idle speed PID profile.";
    private static final String FILTER_TOOLTIP =
            "Filter parameter and output-channel rows by any visible text, such as idle, pid, target, rpm, or output.";
    private static final String TYPE_TOOLTIP =
            "Limit the diagnostics table to controller parameters, output channels, or both.";
    private static final String LOG_TOOLTIP =
            "Import a TunerStudio MSL log. The parser extracts only the channels used for idle analysis and does not alter the source file.";

    private final JLabel signatureValue = new JLabel("Not yet reported by TunerStudio");
    private final JLabel connectionValue = new JLabel("Not initialized");
    private final JLabel mappingValue = new JLabel("Not checked");
    private final JLabel footerStatus = new JLabel("Version " + PidAutotunePlugin.VERSION + " — read-only live and log analysis");

    private final JComboBox<String> configurationCombo = new JComboBox<String>();
    private final JComboBox<String> profileCombo = new JComboBox<String>(new String[] { EpicEfiIdleProfile.PROFILE_NAME });
    private final JButton refreshButton = new JButton("Refresh from TunerStudio");
    private final JButton copyMappingButton = new JButton("Copy mapped data");

    private final DiagnosticTableModel mappingModel = new DiagnosticTableModel();
    private final DiagnosticTableModel diagnosticsModel = new DiagnosticTableModel();
    private final JTable mappingTable = createTable(mappingModel);
    private final JTable diagnosticsTable = createTable(diagnosticsModel);
    private final TableRowSorter<DiagnosticTableModel> diagnosticsSorter =
            new TableRowSorter<DiagnosticTableModel>(diagnosticsModel);

    private final JTextField diagnosticsFilter = new JTextField(24);
    private final JComboBox<String> diagnosticsType =
            new JComboBox<String>(new String[] { "All", "Parameters", "Output channels" });

    private final JTextField logPathField = new JTextField(48);
    private final JButton browseLogButton = new JButton("Add log…");
    private final JButton reloadLogButton = new JButton("Reload and analyse");
    private final JButton copyLogSummaryButton = new JButton("Copy log summary");
    private final JLabel logSamplesValue = new JLabel("No log loaded");
    private final JLabel logDurationValue = new JLabel("—");
    private final JLabel logRateValue = new JLabel("—");
    private final JLabel logChannelsValue = new JLabel("—");
    private final JLabel idleEventsValue = new JLabel("—");
    private final JLabel datasetLogsValue = new JLabel("0");
    private final JLabel datasetReadinessValue = new JLabel(DatasetAssessment.INSUFFICIENT);
    private final JLabel datasetLogsPanelValue = new JLabel("0");
    private final JLabel datasetReadinessPanelValue = new JLabel(DatasetAssessment.INSUFFICIENT);
    private final LogChannelTableModel logChannelModel = new LogChannelTableModel();
    private final IdleEventTableModel idleEventModel = new IdleEventTableModel();
    private final JTable logChannelTable = new JTable(logChannelModel);
    private final JTable idleEventTable = new JTable(idleEventModel);
    private final JTextArea logWarnings = new JTextArea();
    private final JTextArea eventDetails = new JTextArea();
    private final IdleLogChartPanel logChart = new IdleLogChartPanel();
    private final DatasetEventTableModel datasetEventModel = new DatasetEventTableModel();
    private final JTable datasetEventTable = new JTable(datasetEventModel);
    private final JTextArea datasetAssessmentText = new JTextArea();
    private final JButton removeCurrentLogButton = new JButton("Remove current log");
    private final JButton clearDatasetButton = new JButton("Clear dataset");
    private final JButton exportEventCsvButton = new JButton("Export selected event CSV…");
    private final JLabel gainLogValue = new JLabel("No log selected");
    private final JTextField gainPField = new JTextField(7);
    private final JTextField gainIField = new JTextField(7);
    private final JTextField gainDField = new JTextField(7);
    private final JLabel gainSourceValue = new JLabel("—");
    private final JButton applyGainSnapshotButton = new JButton("Apply gain snapshot");
    private final JButton captureCurrentGainsButton = new JButton("Use current TunerStudio gains");
    private final JComboBox<VssSourceMode> vssSourceModeCombo = new JComboBox<VssSourceMode>(VssSourceMode.values());
    private final JButton applyVssSourceButton = new JButton("Apply VSS source");

    private final LiveCapturePanel liveCapturePanel;

    private final JLabel recommendationStatusValue = new JLabel("No dataset loaded");
    private final JLabel recommendationGroupValue = new JLabel("None");
    private final JLabel recommendationConfidenceValue = new JLabel("—");
    private final JLabel recommendationDatasetGateValue = new JLabel(DatasetAssessment.INSUFFICIENT);
    private final JTextField maximumPChangeField = new JTextField("15", 6);
    private final JTextField maximumIChangeField = new JTextField("15", 6);
    private final JTextField maximumDChangeField = new JTextField("25", 6);
    private final JTextField minimumConfidenceField = new JTextField("60", 6);
    private final JButton recalculateRecommendationButton = new JButton("Recalculate");
    private final JButton copyRecommendationButton = new JButton("Copy recommendation");
    private final RecommendationGainTableModel recommendationGainModel = new RecommendationGainTableModel();
    private final JTable recommendationGainTable = new JTable(recommendationGainModel);
    private final JTextArea recommendationDetails = new JTextArea();
    private final ConservativePidRecommendationEngine recommendationEngine = new ConservativePidRecommendationEngine();
    private final JLabel recommendationSettingsStatusValue = new JLabel("Settings accepted");
    private RecommendationSettings acceptedRecommendationSettings = RecommendationSettings.defaults();
    private Border recommendationNormalBorder;
    private Border recommendationInvalidBorder;
    private Color recommendationNormalBackground;
    private Color recommendationInvalidBackground;
    private boolean recommendationSettingsValid = true;
    private boolean recommendationSettingsDirty;
    private boolean suppressRecommendationSettingEvents;
    private long recommendationSettingsEditRevision;

    private ControllerDataAccess dataAccess;
    private String controllerSignature = "Not yet reported by TunerStudio";
    private List<DiagnosticRow> latestMappingRows = Collections.emptyList();
    private IdleLogData latestLogData;
    private IdleAnalysisResult latestAnalysis;
    private File selectedLogFile;
    private final List<AnalyzedLog> datasetLogs = new ArrayList<AnalyzedLog>();
    private AnalyzedLog currentAnalyzedLog;
    private DatasetAssessment latestDatasetAssessment;
    private PidRecommendation latestRecommendation;
    private boolean suppressDatasetSelection;
    private boolean suppressConfigurationEvent;
    private boolean ecuBusy;
    private boolean logBusy;

    public PidAutotunePanel() {
        super(new BorderLayout(8, 8));
        liveCapturePanel = new LiveCapturePanel(new LiveCapturePanel.SessionExportListener() {
            @Override
            public void sessionExported(File file, boolean addToDataset) {
                if (file == null) return;
                if (addToDataset) {
                    selectedLogFile = file;
                    logPathField.setText(file.getAbsolutePath());
                    logPathField.setCaretPosition(logPathField.getText().length());
                    loadLog(file);
                } else {
                    footerStatus.setText("Saved read-only live session to " + file.getName() + ".");
                }
            }
        });
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        buildUi();
        initializeRecommendationSettingsUi();
        wireActions();
        datasetEventModel.setChangeListener(new Runnable() {
            @Override public void run() { updateDatasetAssessment(); }
        });
        setEcuControlsEnabled(false);
        updateGainSnapshotEditor();
        updateDatasetAssessment();
        updateLogControls();
    }

    public void connect(ControllerAccess controllerAccess, String signature) {
        setControllerSignature(signature);
        try {
            dataAccess = new ControllerDataAccess(controllerAccess);
            liveCapturePanel.connect(controllerAccess);
            connectionValue.setText("TunerStudio controller API available");
            setEcuControlsEnabled(true);
            reloadConfigurationNames();
            refreshData();
        } catch (RuntimeException ex) {
            liveCapturePanel.disconnect();
            dataAccess = null;
            connectionValue.setText("Initialization failed: " + safeMessage(ex));
            footerStatus.setText("Unable to initialize the TunerStudio controller API. Log importing remains available.");
            setEcuControlsEnabled(false);
        }
    }

    public void disconnect() {
        liveCapturePanel.disconnect();
        dataAccess = null;
        connectionValue.setText("Plugin panel closed");
        setEcuControlsEnabled(false);
    }

    public void setControllerSignature(String signature) {
        if (signature != null && !signature.trim().isEmpty()) {
            controllerSignature = signature.trim();
            signatureValue.setText(controllerSignature);
            signatureValue.setToolTipText(controllerSignature);
        }
    }

    private void buildUi() {
        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Idle PID overview", buildMappingTab());
        tabs.addTab("Live guided capture", liveCapturePanel);
        tabs.addTab("Log analysis", buildLogAnalysisTab());
        tabs.addTab("Dataset preparation", buildDatasetPanel());
        tabs.addTab("PID recommendation", buildRecommendationPanel());
        tabs.addTab("Advanced diagnostics", buildDiagnosticsTab());
        tabs.setToolTipTextAt(0, "Shows the known EPICEFI idle PID parameters and channels used by the analyser.");
        tabs.setToolTipTextAt(1, "Subscribes to live EPICEFI channels, validates conditions, and guides read-only return-to-idle captures with immediate accept/reject feedback.");
        tabs.setToolTipTextAt(2, "Imports an MSL or exported live-session log, separates target ramps, steady idle holds, and load disturbances, then calculates read-only diagnostics.");
        tabs.setToolTipTextAt(3, "Combines manually selected compatible events from multiple logs and reports conservative readiness.");
        tabs.setToolTipTextAt(4, "Runs the read-only conservative recommendation model only when the dataset readiness and confidence gates pass.");
        tabs.setToolTipTextAt(5, "Lists every parameter and output channel exposed by the active TunerStudio definition.");
        add(tabs, BorderLayout.CENTER);

        footerStatus.setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));
        footerStatus.setToolTipText("Current plugin activity and read-only safety status.");
        add(footerStatus, BorderLayout.SOUTH);
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));

        JLabel title = new JLabel("EPICEFI PID Autotune");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 4.0f));
        title.setToolTipText("Read-only EPICEFI idle PID analysis and future recommendation plugin.");

        JLabel safety = new JLabel(
                "Version " + PidAutotunePlugin.VERSION + " observes live data but cannot write ECU RAM or burn settings.",
                SwingConstants.RIGHT);
        safety.setToolTipText("This release reads project definitions, subscribes to live output channels, and analyses captured/logged data only.");

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.add(title, BorderLayout.WEST);
        titleRow.add(safety, BorderLayout.EAST);
        panel.add(titleRow, BorderLayout.NORTH);

        JPanel information = new JPanel(new GridBagLayout());
        GridBagConstraints c = baseConstraints();
        addReadOnlyRow(information, c, 0, "Controller signature", signatureValue,
                "Signature reported by TunerStudio for the currently active controller definition.");
        addReadOnlyRow(information, c, 1, "Connection", connectionValue,
                "Shows whether the TunerStudio controller API was successfully supplied to the plugin.");
        addReadOnlyRow(information, c, 2, "EPICEFI idle mapping", mappingValue,
                "Shows how many known idle PID parameters and channels were found in the active definition.");
        panel.add(information, BorderLayout.CENTER);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel configLabel = new JLabel("ECU configuration:");
        configLabel.setToolTipText(CONFIG_TOOLTIP);
        configurationCombo.setToolTipText(CONFIG_TOOLTIP);
        configurationCombo.setPrototypeDisplayValue("mainController — long configuration name");

        JLabel profileLabel = new JLabel("Profile:");
        profileLabel.setToolTipText(PROFILE_TOOLTIP);
        profileCombo.setToolTipText(PROFILE_TOOLTIP);
        refreshButton.setToolTipText("Re-read current parameters and available output channels from TunerStudio.");

        controls.add(configLabel);
        controls.add(configurationCombo);
        controls.add(profileLabel);
        controls.add(profileCombo);
        controls.add(refreshButton);
        panel.add(controls, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildMappingTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        JTextArea explanation = createExplanation(
                "This page verifies the exact EPICEFI names needed for idle PID analysis. " +
                "Mapped rows are read directly through TunerStudio; nothing is written back. " +
                "Generic numeric storage ranges are labelled Definition type limit and their raw values remain in Details.");
        panel.add(explanation, BorderLayout.NORTH);

        configureDiagnosticColumns(mappingTable);
        JScrollPane scroll = new JScrollPane(mappingTable);
        scroll.setToolTipText("Known idle PID parameters and output channels detected in the active EPICEFI definition.");
        panel.add(scroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyMappingButton.setToolTipText("Copy the mapped idle PID rows as tab-separated text for notes or issue reports.");
        buttons.add(copyMappingButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildLogAnalysisTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel filePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel fileLabel = new JLabel("Log file:");
        fileLabel.setToolTipText(LOG_TOOLTIP);
        logPathField.setEditable(false);
        logPathField.setToolTipText(LOG_TOOLTIP);
        browseLogButton.setToolTipText(LOG_TOOLTIP);
        reloadLogButton.setToolTipText("Read the selected file again and re-run detection using the current tune limits.");
        filePanel.add(fileLabel);
        filePanel.add(logPathField);
        filePanel.add(browseLogButton);
        filePanel.add(reloadLogButton);

        JPanel summary = new JPanel(new GridBagLayout());
        GridBagConstraints c = baseConstraints();
        addReadOnlyRow(summary, c, 0, "Samples", logSamplesValue, "Number of numeric TunerStudio log records imported.");
        addReadOnlyRow(summary, c, 1, "Duration", logDurationValue, "Elapsed time between the first and final numeric sample.");
        addReadOnlyRow(summary, c, 2, "Median sample rate", logRateValue, "Rate calculated from the median positive timestamp interval.");
        addReadOnlyRow(summary, c, 3, "Mapped log channels", logChannelsValue, "Logical analyser channels matched to actual MSL captions.");
        addReadOnlyRow(summary, c, 4, "Detected idle events", idleEventsValue, "Closed-loop regions, target ramps, steady holds, and load transitions detected in the current log.");
        addReadOnlyRow(summary, c, 5, "Dataset logs", datasetLogsValue, "Number of imported logs currently retained in the in-memory dataset.");
        addReadOnlyRow(summary, c, 6, "Dataset readiness", datasetReadinessValue, "Conservative readiness gate based on manually included compatible events across all loaded logs.");

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(filePanel, BorderLayout.NORTH);
        north.add(summary, BorderLayout.CENTER);
        panel.add(north, BorderLayout.NORTH);

        configureEventColumns(idleEventTable);
        idleEventTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        idleEventTable.setFillsViewportHeight(true);
        idleEventTable.setToolTipText("Select an event to display that time range in the graph.");

        configureLogChannelColumns(logChannelTable);
        logChannelTable.setFillsViewportHeight(true);
        logChannelTable.setToolTipText("Shows which actual MSL captions were mapped to each logical analyser channel.");

        logWarnings.setEditable(false);
        logWarnings.setLineWrap(true);
        logWarnings.setWrapStyleWord(true);
        logWarnings.setRows(8);
        logWarnings.setToolTipText("Parser notes, data-quality checks, and reasons a log may not yet be suitable for PID recommendations.");

        eventDetails.setEditable(false);
        eventDetails.setLineWrap(true);
        eventDetails.setWrapStyleWord(true);
        eventDetails.setRows(8);
        eventDetails.setText("Select a detected event to inspect its steady-state and controller-activity diagnostics.");
        eventDetails.setToolTipText("Detailed metrics for the selected event, including signed error, RPM stability, output activity, and P/I/D term ranges.");

        JTabbedPane detailTabs = new JTabbedPane();
        detailTabs.addTab("Detected events", new JScrollPane(idleEventTable));
        detailTabs.addTab("Selected event details", new JScrollPane(eventDetails));
        detailTabs.addTab("Log channel mapping", new JScrollPane(logChannelTable));
        detailTabs.addTab("Data quality", new JScrollPane(logWarnings));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, logChart, detailTabs);
        split.setResizeWeight(0.58);
        split.setDividerLocation(330);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        copyLogSummaryButton.setToolTipText("Copy imported-log details, channel mapping, detected events, and data-quality notes.");
        buttons.add(copyLogSummaryButton);
        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent buildDatasetPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel datasetSummary = new JPanel(new GridBagLayout());
        GridBagConstraints summaryConstraints = baseConstraints();
        addReadOnlyRow(datasetSummary, summaryConstraints, 0, "Loaded logs", datasetLogsPanelValue,
                "Number of imported logs currently retained in the in-memory dataset.");
        addReadOnlyRow(datasetSummary, summaryConstraints, 1, "Readiness", datasetReadinessPanelValue,
                "Conservative readiness gate for future recommendation calculations.");
        addReadOnlyRow(datasetSummary, summaryConstraints, 2, "Selected log", gainLogValue,
                "Log whose stored P/I/D snapshot is shown below. Select any dataset row to change the current log.");
        addReadOnlyRow(datasetSummary, summaryConstraints, 3, "Gain source", gainSourceValue,
                "How the selected log's P/I/D snapshot was obtained: captured at import, recaptured, or manually overridden.");

        final String gainTooltip = "Stored gain for the selected imported log. MSL files do not reliably contain PID constants, so correct this value when the displayed tune did not match the tune used to record the log.";
        JPanel gainEditor = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel pLabel = new JLabel("P:");
        JLabel iLabel = new JLabel("I:");
        JLabel dLabel = new JLabel("D:");
        pLabel.setToolTipText(gainTooltip);
        iLabel.setToolTipText(gainTooltip);
        dLabel.setToolTipText(gainTooltip);
        gainPField.setToolTipText(gainTooltip);
        gainIField.setToolTipText(gainTooltip);
        gainDField.setToolTipText(gainTooltip);
        applyGainSnapshotButton.setToolTipText("Validate and store the entered P/I/D values as a manual snapshot for the selected log. No ECU value is changed.");
        captureCurrentGainsButton.setToolTipText("Replace the selected log snapshot with the P/I/D values currently displayed by TunerStudio. No ECU value is changed.");
        gainEditor.add(pLabel);
        gainEditor.add(gainPField);
        gainEditor.add(iLabel);
        gainEditor.add(gainIField);
        gainEditor.add(dLabel);
        gainEditor.add(gainDField);
        gainEditor.add(applyGainSnapshotButton);
        gainEditor.add(captureCurrentGainsButton);

        final String vssTooltip = "Per-log interpretation of the vehicle-speed channel. Use Automatic only when the log can prove VSS continuity; explicitly select verified operational, manually confirmed stationary, or motion unknown when appropriate. This setting changes only dataset grouping and readiness.";
        JPanel vssEditor = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel vssLabel = new JLabel("VSS source:");
        vssLabel.setToolTipText(vssTooltip);
        vssSourceModeCombo.setToolTipText(vssTooltip);
        applyVssSourceButton.setToolTipText("Store the selected VSS source for this imported log and rebuild its motion-integrity assessment. No source log or ECU value is changed.");
        vssEditor.add(vssLabel);
        vssEditor.add(vssSourceModeCombo);
        vssEditor.add(applyVssSourceButton);

        JPanel metadataEditors = new JPanel(new BorderLayout(2, 2));
        metadataEditors.add(gainEditor, BorderLayout.NORTH);
        metadataEditors.add(vssEditor, BorderLayout.SOUTH);

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        removeCurrentLogButton.setToolTipText("Remove the currently displayed log from the in-memory dataset. The source file is not changed.");
        clearDatasetButton.setToolTipText("Remove every imported log and event from the in-memory dataset. Source files are not changed.");
        exportEventCsvButton.setToolTipText("Export the full-resolution logical channels for the selected dataset or detected event as a new CSV file.");
        controls.add(removeCurrentLogButton);
        controls.add(clearDatasetButton);
        controls.add(exportEventCsvButton);
        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(datasetSummary, BorderLayout.NORTH);
        north.add(metadataEditors, BorderLayout.CENTER);
        north.add(controls, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        datasetEventTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        datasetEventTable.setFillsViewportHeight(true);
        datasetEventTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        datasetEventTable.setToolTipText("Good and Usable transient or steady rows may be manually included or excluded. The Gate, VSS source, and VSS integrity columns show whether an included event can satisfy recommendation readiness.");
        configureDatasetColumns(datasetEventTable);

        datasetAssessmentText.setEditable(false);
        datasetAssessmentText.setLineWrap(true);
        datasetAssessmentText.setWrapStyleWord(true);
        datasetAssessmentText.setRows(8);
        datasetAssessmentText.setText("No logs have been added to the dataset.");
        datasetAssessmentText.setToolTipText("Explains the strongest compatibility group and what is still required before a conservative recommendation model may run.");

        JTextArea testGuide = createExplanation(
                "Guided repeatable test: verify the stored P/I/D snapshot for every imported log; warm the engine to a narrow CLT range; " +
                "prefer a verified VSS at zero and TPS closed; when VSS is unavailable, explicitly mark the complete log as manually confirmed stationary or motion unknown; " +
                "keep fan state consistent; record at least three comparable return-to-idle or load-step responses; allow at least 10 to 15 seconds after each disturbance; " +
                "and collect one steady-idle hold under the same target, CLT band, fan state, movement class, VSS basis, and gains. " +
                "Do not use events with unexplained VSS dropouts for recommendation readiness. Stop the test if RPM becomes unsafe or correction remains saturated.");
        testGuide.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JTabbedPane lower = new JTabbedPane();
        lower.addTab("Readiness", new JScrollPane(datasetAssessmentText));
        lower.addTab("Guided test", new JScrollPane(testGuide));

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(datasetEventTable),
                lower);
        split.setResizeWeight(0.68);
        split.setDividerLocation(360);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private JComponent buildRecommendationPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel summary = new JPanel(new GridBagLayout());
        GridBagConstraints summaryConstraints = baseConstraints();
        addReadOnlyRow(summary, summaryConstraints, 0, "Recommendation status", recommendationStatusValue,
                "Shows whether a conservative candidate is available or why the model is blocked.");
        addReadOnlyRow(summary, summaryConstraints, 1, "Dataset gate", recommendationDatasetGateValue,
                "The existing multi-log readiness gate must report Suitable for conservative recommendation before any gain value is calculated.");
        addReadOnlyRow(summary, summaryConstraints, 2, "Strongest compatible group", recommendationGroupValue,
                "Only the strongest included, gate-eligible compatibility group is supplied to the model.");
        addReadOnlyRow(summary, summaryConstraints, 3, "Model confidence", recommendationConfidenceValue,
                "Confidence is based on repeatability, quality grading, event consistency, and correction saturation.");

        final String changeTooltip =
                "Maximum relative change the conservative model may propose for this gain in one iteration. " +
                "Allowed range: 0 to 50 percent. The plugin never writes the value to the ECU.";
        final String confidenceTooltip =
                "Minimum model confidence required before proposed gains are displayed. Allowed range: 0 to 100 percent.";
        JPanel settings = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        JLabel pLabel = new JLabel("Max P change:");
        JLabel iLabel = new JLabel("Max I change:");
        JLabel dLabel = new JLabel("Max D change:");
        JLabel confidenceLabel = new JLabel("Minimum confidence:");
        pLabel.setToolTipText(changeTooltip);
        iLabel.setToolTipText(changeTooltip);
        dLabel.setToolTipText(changeTooltip);
        confidenceLabel.setToolTipText(confidenceTooltip);
        maximumPChangeField.setToolTipText(changeTooltip);
        maximumIChangeField.setToolTipText(changeTooltip);
        maximumDChangeField.setToolTipText(changeTooltip);
        minimumConfidenceField.setToolTipText(confidenceTooltip);
        recalculateRecommendationButton.setToolTipText(
                "Validate the limits and run the read-only model again using the currently included dataset events.");
        settings.add(pLabel);
        settings.add(maximumPChangeField);
        settings.add(new JLabel("%"));
        settings.add(iLabel);
        settings.add(maximumIChangeField);
        settings.add(new JLabel("%"));
        settings.add(dLabel);
        settings.add(maximumDChangeField);
        settings.add(new JLabel("%"));
        settings.add(confidenceLabel);
        settings.add(minimumConfidenceField);
        settings.add(new JLabel("%"));
        settings.add(recalculateRecommendationButton);
        copyRecommendationButton.setToolTipText(
                "Copy the current model status, current and proposed gains, confidence, reasons, expected effects, and diagnostics. " +
                "Export is disabled until all settings are valid and recalculated.");
        settings.add(copyRecommendationButton);
        final String settingsStatusTooltip =
                "Shows whether the displayed recommendation limits are accepted, pending recalculation, or invalid.";
        JLabel settingsStatusLabel = new JLabel("Settings status:");
        settingsStatusLabel.setToolTipText(settingsStatusTooltip);
        recommendationSettingsStatusValue.setToolTipText(settingsStatusTooltip);

        JPanel settingsStatus = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        settingsStatus.add(settingsStatusLabel);
        settingsStatus.add(recommendationSettingsStatusValue);

        // Keep the state indicator on its own line. The longer pending text was previously
        // pushed beyond the right edge of narrower TunerStudio plugin windows even though
        // the underlying state was correct.
        JPanel settingsBlock = new JPanel(new BorderLayout(0, 2));
        settingsBlock.add(settings, BorderLayout.NORTH);
        settingsBlock.add(settingsStatus, BorderLayout.SOUTH);

        JPanel north = new JPanel(new BorderLayout(4, 4));
        north.add(summary, BorderLayout.NORTH);
        north.add(settingsBlock, BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);

        recommendationGainTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        recommendationGainTable.setFillsViewportHeight(true);
        recommendationGainTable.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        recommendationGainTable.setToolTipText(
                "Current versus proposed P/I/D values, bounded relative changes, expected effects, and the reason for every decision.");
        configureRecommendationColumns(recommendationGainTable);

        recommendationDetails.setEditable(false);
        recommendationDetails.setLineWrap(true);
        recommendationDetails.setWrapStyleWord(true);
        recommendationDetails.setRows(12);
        recommendationDetails.setText(
                "No recommendation model has been evaluated yet. Load logs and prepare a compatible dataset first.");
        recommendationDetails.setToolTipText(
                "Read-only model status, aggregate response metrics, diagnostic observations, and safety guidance.");

        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                new JScrollPane(recommendationGainTable),
                new JScrollPane(recommendationDetails));
        split.setResizeWeight(0.34);
        split.setDividerLocation(190);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);

        return panel;
    }

    private JComponent buildDiagnosticsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));

        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JLabel filterLabel = new JLabel("Search:");
        filterLabel.setToolTipText(FILTER_TOOLTIP);
        diagnosticsFilter.setToolTipText(FILTER_TOOLTIP);

        JLabel typeLabel = new JLabel("Show:");
        typeLabel.setToolTipText(TYPE_TOOLTIP);
        diagnosticsType.setToolTipText(TYPE_TOOLTIP);

        filterPanel.add(filterLabel);
        filterPanel.add(diagnosticsFilter);
        filterPanel.add(typeLabel);
        filterPanel.add(diagnosticsType);
        panel.add(filterPanel, BorderLayout.NORTH);

        diagnosticsTable.setRowSorter(diagnosticsSorter);
        configureDiagnosticColumns(diagnosticsTable);
        JScrollPane scroll = new JScrollPane(diagnosticsTable);
        scroll.setToolTipText("All controller parameters and output channels exposed by the selected configuration.");

        JTextArea guidance = createExplanation(
                "Useful searches: idle, iac, pid, target, rpm, output, fan, tps, vss. " +
                "This diagnostics page is retained as a development tool for future firmware changes.");

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scroll, guidance);
        split.setResizeWeight(1.0);
        split.setDividerLocation(430);
        split.setBorder(null);
        panel.add(split, BorderLayout.CENTER);
        return panel;
    }

    private void wireActions() {
        refreshButton.addActionListener(event -> refreshData());
        copyMappingButton.addActionListener(event -> copyMappedData());
        configurationCombo.addActionListener(event -> {
            if (!suppressConfigurationEvent) refreshData();
        });
        diagnosticsType.addActionListener(event -> updateDiagnosticFilter());
        diagnosticsFilter.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { updateDiagnosticFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { updateDiagnosticFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { updateDiagnosticFilter(); }
        });
        browseLogButton.addActionListener(event -> chooseLogFile());
        reloadLogButton.addActionListener(event -> {
            if (selectedLogFile != null) loadLog(selectedLogFile);
        });
        copyLogSummaryButton.addActionListener(event -> copyLogSummary());
        removeCurrentLogButton.addActionListener(event -> removeCurrentLogFromDataset());
        clearDatasetButton.addActionListener(event -> clearDataset());
        exportEventCsvButton.addActionListener(event -> exportSelectedEventCsv());
        applyGainSnapshotButton.addActionListener(event -> applyManualGainSnapshot());
        captureCurrentGainsButton.addActionListener(event -> captureCurrentGainSnapshot());
        applyVssSourceButton.addActionListener(event -> applyVssSourceMode());
        vssSourceModeCombo.addActionListener(event -> {
            VssSourceMode selected = (VssSourceMode) vssSourceModeCombo.getSelectedItem();
            if (selected != null) vssSourceModeCombo.setToolTipText(selected.getDescription());
        });
        wireRecommendationField(maximumPChangeField);
        wireRecommendationField(maximumIChangeField);
        wireRecommendationField(maximumDChangeField);
        wireRecommendationField(minimumConfidenceField);
        recalculateRecommendationButton.addActionListener(event -> recalculateRecommendation());
        copyRecommendationButton.addActionListener(event -> copyRecommendation());
        idleEventTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateSelectedEventChart();
        });
        datasetEventTable.getSelectionModel().addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting() && !suppressDatasetSelection) showSelectedDatasetEvent();
        });
    }

    private void reloadConfigurationNames() {
        if (dataAccess == null) return;
        String previous = (String) configurationCombo.getSelectedItem();
        String[] names = dataAccess.getConfigurationNames();
        suppressConfigurationEvent = true;
        try {
            configurationCombo.removeAllItems();
            for (String name : names) configurationCombo.addItem(name);
            if (previous != null) configurationCombo.setSelectedItem(previous);
            if (configurationCombo.getSelectedIndex() < 0 && configurationCombo.getItemCount() > 0) {
                configurationCombo.setSelectedIndex(0);
            }
        } finally {
            suppressConfigurationEvent = false;
        }
        liveCapturePanel.setConfigurationName((String) configurationCombo.getSelectedItem());
    }

    private void refreshData() {
        if (dataAccess == null) {
            footerStatus.setText("TunerStudio controller API is not initialized.");
            return;
        }
        final String configurationName = (String) configurationCombo.getSelectedItem();
        liveCapturePanel.setConfigurationName(configurationName);
        if (configurationName == null || configurationName.trim().isEmpty()) {
            mappingModel.setRows(Collections.<DiagnosticRow>emptyList());
            diagnosticsModel.setRows(Collections.<DiagnosticRow>emptyList());
            mappingValue.setText("No loaded ECU configuration");
            footerStatus.setText("No TunerStudio ECU configuration is available to inspect.");
            return;
        }

        setEcuBusy(true, "Reading " + configurationName + " …");
        new SwingWorker<DiscoveryResult, Void>() {
            @Override
            protected DiscoveryResult doInBackground() {
                return new DiscoveryResult(
                        dataAccess.loadKnownIdleMapping(configurationName),
                        dataAccess.loadAllDiagnostics(configurationName));
            }

            @Override
            protected void done() {
                try {
                    DiscoveryResult result = get();
                    latestMappingRows = result.mappingRows;
                    mappingModel.setRows(result.mappingRows);
                    diagnosticsModel.setRows(result.diagnosticRows);
                    updateMappingStatus(result.mappingRows);
                    if (!datasetLogs.isEmpty()) rebuildDatasetRows();
                    if ("Not yet reported by TunerStudio".equals(controllerSignature)) {
                        signatureValue.setText("Not reported; using " + configurationName);
                    }
                    footerStatus.setText(
                            "Read " + result.diagnosticRows.size() + " definitions from " + configurationName +
                            ". No ECU values were changed.");
                } catch (Exception ex) {
                    footerStatus.setText("Discovery failed: " + safeMessage(ex));
                    mappingValue.setText("Read failed");
                } finally {
                    setEcuBusy(false, null);
                }
            }
        }.execute();
    }

    private void chooseLogFile() {
        JFileChooser chooser = new JFileChooser(initialLogDirectory());
        chooser.setDialogTitle("Select a TunerStudio log");
        chooser.setFileFilter(new FileNameExtensionFilter("TunerStudio logs (*.msl, *.csv)", "msl", "csv"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectedLogFile = chooser.getSelectedFile();
            logPathField.setText(selectedLogFile.getAbsolutePath());
            logPathField.setCaretPosition(logPathField.getText().length());
            loadLog(selectedLogFile);
        }
    }

    private void loadLog(final File file) {
        final IdleAnalysisSettings settings = analysisSettingsFromMapping();
        final TuneSnapshot tuneSnapshot = currentTuneSnapshot("Captured from TunerStudio when imported");
        setLogBusy(true, "Reading and analysing " + file.getName() + " …");
        new SwingWorker<LogLoadResult, Void>() {
            @Override
            protected LogLoadResult doInBackground() throws Exception {
                IdleLogData data = new MslLogReader().read(file);
                IdleAnalysisSettings effectiveSettings = applyLiveCaptureMetadata(data, settings);
                TuneSnapshot effectiveTuneSnapshot = applyLiveGainMetadata(data, tuneSnapshot);
                IdleAnalysisResult analysis = new IdleLogAnalyzer().analyze(data, effectiveSettings);
                return new LogLoadResult(data, analysis, effectiveSettings, effectiveTuneSnapshot);
            }

            @Override
            protected void done() {
                try {
                    LogLoadResult result = get();
                    AnalyzedLog analyzedLog = new AnalyzedLog(result.data, result.analysis, result.settings, result.tuneSnapshot);
                    addOrReplaceDatasetLog(analyzedLog);
                    currentAnalyzedLog = analyzedLog;
                    latestLogData = result.data;
                    latestAnalysis = result.analysis;
                    selectedLogFile = file;
                    updateLogDisplay(result.data, result.analysis, null);
                    updateGainSnapshotEditor();
                    footerStatus.setText(
                            "Analysed " + result.data.getSampleCount() + " log samples from " + file.getName() +
                            " and added it to the in-memory dataset. No ECU values or log data were changed.");
                } catch (Exception ex) {
                    latestLogData = null;
                    latestAnalysis = null;
                    logChart.setData(null, null);
                    idleEventModel.setRows(Collections.<IdleEvent>emptyList());
                    logChannelModel.setRows(Collections.<LogChannelMapping>emptyList());
                    logWarnings.setText("Log import failed:\n" + safeMessage(ex));
                    eventDetails.setText("No event details are available because log import failed.");
                    logSamplesValue.setText("Read failed");
                    logDurationValue.setText("—");
                    logRateValue.setText("—");
                    logChannelsValue.setText("—");
                    idleEventsValue.setText("—");
                    footerStatus.setText("Log import failed: " + safeMessage(ex));
                } finally {
                    setLogBusy(false, null);
                }
            }
        }.execute();
    }

    private void addOrReplaceDatasetLog(AnalyzedLog analyzedLog) {
        int replacement = -1;
        for (int i = 0; i < datasetLogs.size(); i++) {
            if (datasetLogs.get(i).getIdentity().equals(analyzedLog.getIdentity())) {
                replacement = i;
                break;
            }
        }
        if (replacement >= 0) {
            AnalyzedLog previousLog = datasetLogs.get(replacement);
            TuneSnapshot previousSnapshot = previousLog.getTuneSnapshot();
            if (previousSnapshot.isManualOverride()) analyzedLog.setTuneSnapshot(previousSnapshot);
            analyzedLog.setVssSourceMode(previousLog.getVssSourceMode());
            datasetLogs.set(replacement, analyzedLog);
        } else {
            datasetLogs.add(analyzedLog);
        }
        rebuildDatasetRows();
        updateGainSnapshotEditor();
    }

    private void rebuildDatasetRows() {
        Map<String, DatasetEvent> previousRows = new HashMap<String, DatasetEvent>();
        for (DatasetEvent row : datasetEventModel.getRows()) {
            previousRows.put(datasetEventKey(row), row);
        }

        List<DatasetEvent> rows = new ArrayList<DatasetEvent>();
        for (AnalyzedLog log : datasetLogs) {
            for (IdleEvent event : log.getAnalysis().getEvents()) {
                DatasetEvent row = new DatasetEvent(log, event);
                DatasetEvent previous = previousRows.get(datasetEventKey(row));
                if (previous != null) {
                    boolean newlyGateEligible = !previous.isRecommendationGateEligible()
                            && row.isRecommendationGateEligible();
                    row.setIncluded(newlyGateEligible || previous.isIncluded());
                }
                rows.add(row);
            }
        }
        Collections.sort(rows, (left, right) -> {
            int file = left.getAnalyzedLog().getDisplayName().compareToIgnoreCase(right.getAnalyzedLog().getDisplayName());
            if (file != 0) return file;
            return Double.compare(left.getEvent().getTriggerSeconds(), right.getEvent().getTriggerSeconds());
        });

        suppressDatasetSelection = true;
        try {
            datasetEventModel.setRows(rows);
            datasetEventTable.clearSelection();
        } finally {
            suppressDatasetSelection = false;
        }
        updateDatasetAssessment();
    }

    private void updateDatasetAssessment() {
        latestDatasetAssessment = DatasetAssessment.assess(
                datasetEventModel.getRows(),
                datasetLogs.size());
        String loadedCount = String.valueOf(datasetLogs.size());
        String loadedTooltip = datasetLogs.size() + " imported log(s) are retained in memory.";
        datasetLogsValue.setText(loadedCount);
        datasetLogsPanelValue.setText(loadedCount);
        datasetLogsValue.setToolTipText(loadedTooltip);
        datasetLogsPanelValue.setToolTipText(loadedTooltip);
        datasetReadinessValue.setText(latestDatasetAssessment.getLevel());
        datasetReadinessPanelValue.setText(latestDatasetAssessment.getLevel());
        datasetReadinessValue.setToolTipText(latestDatasetAssessment.getSummary());
        datasetReadinessPanelValue.setToolTipText(latestDatasetAssessment.getSummary());
        datasetAssessmentText.setText(
                latestDatasetAssessment.getLevel() + "\n"
                        + latestDatasetAssessment.getSummary() + "\n\n"
                        + latestDatasetAssessment.getDetails());
        datasetAssessmentText.setCaretPosition(0);
        recalculateRecommendationSilently();
        updateLogControls();
    }

    private void showSelectedDatasetEvent() {
        int viewRow = datasetEventTable.getSelectedRow();
        if (viewRow < 0) return;
        int modelRow = datasetEventTable.convertRowIndexToModel(viewRow);
        DatasetEvent row = datasetEventModel.getRow(modelRow);
        currentAnalyzedLog = row.getAnalyzedLog();
        latestLogData = currentAnalyzedLog.getData();
        latestAnalysis = currentAnalyzedLog.getAnalysis();
        selectedLogFile = latestLogData.getSourceFile();
        logPathField.setText(selectedLogFile.getAbsolutePath());
        logPathField.setCaretPosition(logPathField.getText().length());
        updateLogDisplay(latestLogData, latestAnalysis, row.getEvent());
        eventDetails.setText(buildDatasetEventDetails(row));
        eventDetails.setCaretPosition(0);
        updateGainSnapshotEditor();
        footerStatus.setText("Showing " + row.getEvent().getType() + " from "
                + currentAnalyzedLog.getDisplayName() + ". The dataset and source logs remain unchanged.");
    }

    private void removeCurrentLogFromDataset() {
        if (currentAnalyzedLog == null) {
            footerStatus.setText("No current dataset log is selected.");
            return;
        }
        String identity = currentAnalyzedLog.getIdentity();
        for (int i = datasetLogs.size() - 1; i >= 0; i--) {
            if (datasetLogs.get(i).getIdentity().equals(identity)) datasetLogs.remove(i);
        }
        currentAnalyzedLog = null;
        rebuildDatasetRows();
        if (datasetLogs.isEmpty()) {
            clearCurrentLogDisplay();
            updateGainSnapshotEditor();
            footerStatus.setText("The final log was removed from the in-memory dataset. No source file was changed.");
        } else {
            AnalyzedLog next = datasetLogs.get(datasetLogs.size() - 1);
            currentAnalyzedLog = next;
            latestLogData = next.getData();
            latestAnalysis = next.getAnalysis();
            selectedLogFile = latestLogData.getSourceFile();
            logPathField.setText(selectedLogFile.getAbsolutePath());
            updateLogDisplay(latestLogData, latestAnalysis, null);
            updateGainSnapshotEditor();
            footerStatus.setText("Removed one log from the in-memory dataset. No source file was changed.");
        }
    }

    private void clearDataset() {
        datasetLogs.clear();
        currentAnalyzedLog = null;
        datasetEventModel.setRows(Collections.<DatasetEvent>emptyList());
        updateDatasetAssessment();
        clearCurrentLogDisplay();
        updateGainSnapshotEditor();
        footerStatus.setText("Cleared the in-memory dataset. No source files were changed.");
    }

    private void clearCurrentLogDisplay() {
        latestLogData = null;
        latestAnalysis = null;
        selectedLogFile = null;
        logPathField.setText("");
        logSamplesValue.setText("No log loaded");
        logDurationValue.setText("—");
        logRateValue.setText("—");
        logChannelsValue.setText("—");
        idleEventsValue.setText("—");
        idleEventModel.setRows(Collections.<IdleEvent>emptyList());
        logChannelModel.setRows(Collections.<LogChannelMapping>emptyList());
        logWarnings.setText("");
        eventDetails.setText("Select or add a dataset log to inspect its events.");
        logChart.setData(null, null);
        updateGainSnapshotEditor();
        updateLogControls();
    }

    private void exportSelectedEventCsv() {
        SelectedEvent selection = selectedEventForExport();
        if (selection == null) {
            footerStatus.setText("Select a detected event or dataset event before exporting CSV.");
            return;
        }

        JFileChooser chooser = new JFileChooser(initialLogDirectory());
        chooser.setDialogTitle("Export selected idle event as CSV");
        chooser.setFileFilter(new FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File(defaultExportName(selection)));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File output = chooser.getSelectedFile();
        if (!output.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            output = new File(output.getParentFile(), output.getName() + ".csv");
        }
        try {
            writeEventCsv(output, selection.data, selection.event);
            footerStatus.setText("Exported full-resolution event data to " + output.getName()
                    + ". No ECU values or source log data were changed.");
        } catch (Exception ex) {
            footerStatus.setText("CSV export failed: " + safeMessage(ex));
        }
    }

    private SelectedEvent selectedEventForExport() {
        int datasetViewRow = datasetEventTable.getSelectedRow();
        if (datasetViewRow >= 0) {
            DatasetEvent row = datasetEventModel.getRow(datasetEventTable.convertRowIndexToModel(datasetViewRow));
            return new SelectedEvent(row.getAnalyzedLog().getData(), row.getEvent());
        }
        if (latestLogData != null && latestAnalysis != null) {
            int eventViewRow = idleEventTable.getSelectedRow();
            if (eventViewRow >= 0) {
                IdleEvent event = idleEventModel.getRow(idleEventTable.convertRowIndexToModel(eventViewRow));
                return new SelectedEvent(latestLogData, event);
            }
        }
        return null;
    }

    private static void writeEventCsv(File output, IdleLogData data, IdleEvent event) throws Exception {
        List<LogChannelDefinition> channels = new ArrayList<LogChannelDefinition>();
        for (LogChannelDefinition definition : LogChannelDefinition.values()) {
            if (data.has(definition)) channels.add(definition);
        }
        double[] time = data.getSeries(LogChannelDefinition.TIME);
        int start = Math.max(0, event.getStartIndex());
        int end = Math.min(data.getSampleCount() - 1, event.getEndIndex());
        try (PrintWriter writer = new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(output), StandardCharsets.UTF_8))) {
            writer.print("Event time (s)");
            for (LogChannelDefinition channel : channels) {
                writer.print(',');
                writer.print(csv(data.getMatchedName(channel).isEmpty()
                        ? channel.getDisplayName()
                        : data.getMatchedName(channel)));
            }
            writer.println();
            for (int i = start; i <= end; i++) {
                writer.print(raw(time[i] - time[event.getTriggerIndex()]));
                for (LogChannelDefinition channel : channels) {
                    writer.print(',');
                    double[] values = data.getSeries(channel);
                    writer.print(values != null && i < values.length ? raw(values[i]) : "");
                }
                writer.println();
            }
        }
    }

    private void initializeRecommendationSettingsUi() {
        recommendationNormalBorder = maximumPChangeField.getBorder();
        recommendationInvalidBorder = BorderFactory.createLineBorder(new Color(185, 55, 55), 2);
        recommendationNormalBackground = maximumPChangeField.getBackground();
        recommendationInvalidBackground = new Color(255, 225, 225);
        setRecommendationFieldsFromSettings(acceptedRecommendationSettings);
        copyRecommendationButton.setEnabled(false);
        updateRecommendationSettingsState();
    }

    private void wireRecommendationField(final JTextField field) {
        field.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { recommendationFieldEdited(); }
            @Override public void removeUpdate(DocumentEvent e) { recommendationFieldEdited(); }
            @Override public void changedUpdate(DocumentEvent e) { recommendationFieldEdited(); }
        });
        field.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) {
                restoreRecommendationFieldIfInvalid(field);
            }
        });
    }

    private void recommendationFieldEdited() {
        if (!suppressRecommendationSettingEvents) {
            recommendationSettingsEditRevision++;
            updateRecommendationSettingsState();
        }
    }

    private void recalculateRecommendation() {
        List<String> restored = restoreInvalidRecommendationFields();
        try {
            RecommendationSettings settings = recommendationSettingsFromFields();
            acceptedRecommendationSettings = settings;

            // Normalize the visible text under listener suppression so a successful
            // recalculation cannot be mistaken for a new pending user edit.
            setRecommendationFieldsFromSettings(settings);
            final long acceptedRevision = recommendationSettingsEditRevision;

            applyRecommendation(recommendationEngine.evaluate(
                    datasetEventModel.getRows(), latestDatasetAssessment, settings));
            markRecommendationSettingsAccepted();

            // Button activation can move focus after its ActionEvent has run. Reassert
            // the accepted state on the next EDT turn so a late UI event cannot leave
            // Copy disabled or hide the status after a successful recalculation.
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (recommendationSettingsEditRevision == acceptedRevision) {
                        markRecommendationSettingsAccepted();
                    } else {
                        updateRecommendationSettingsState();
                    }
                }
            });

            if (restored.isEmpty()) {
                footerStatus.setText("Recalculated the read-only conservative PID model. No ECU value was changed.");
            } else {
                footerStatus.setText("Restored invalid " + joinNames(restored)
                        + " to the last accepted value and recalculated the read-only model. No ECU value was changed.");
            }
        } catch (IllegalArgumentException ex) {
            updateRecommendationSettingsState();
            footerStatus.setText(ex.getMessage());
        }
    }

    private void recalculateRecommendationSilently() {
        applyRecommendation(recommendationEngine.evaluate(
                datasetEventModel.getRows(), latestDatasetAssessment, acceptedRecommendationSettings));
        updateRecommendationSettingsState();
    }

    private void markRecommendationSettingsAccepted() {
        setRecommendationFieldsFromSettings(acceptedRecommendationSettings);
        updateRecommendationFieldAppearance(maximumPChangeField);
        updateRecommendationFieldAppearance(maximumIChangeField);
        updateRecommendationFieldAppearance(maximumDChangeField);
        updateRecommendationFieldAppearance(minimumConfidenceField);
        recommendationSettingsValid = true;
        recommendationSettingsDirty = false;
        recommendationSettingsStatusValue.setText("Settings accepted");
        recommendationSettingsStatusValue.setToolTipText(
                "The displayed limits are the validated settings used by the current model result.");
        copyRecommendationButton.setEnabled(latestRecommendation != null);
    }

    private RecommendationSettings recommendationSettingsFromFields() {
        return new RecommendationSettings(
                parsePercent(maximumPChangeField.getText(), "Maximum P change", 50.0),
                parsePercent(maximumIChangeField.getText(), "Maximum I change", 50.0),
                parsePercent(maximumDChangeField.getText(), "Maximum D change", 50.0),
                parsePercent(minimumConfidenceField.getText(), "Minimum confidence", 100.0));
    }

    private static double parsePercent(String text, String name, double maximum) {
        try {
            double value = Double.parseDouble(text == null ? "" : text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > maximum) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " must be a finite value from 0 to "
                    + format(maximum) + " percent.");
        }
    }

    private void updateRecommendationSettingsState() {
        if (suppressRecommendationSettingEvents || recommendationNormalBorder == null) return;
        boolean pValid = updateRecommendationFieldAppearance(maximumPChangeField);
        boolean iValid = updateRecommendationFieldAppearance(maximumIChangeField);
        boolean dValid = updateRecommendationFieldAppearance(maximumDChangeField);
        boolean confidenceValid = updateRecommendationFieldAppearance(minimumConfidenceField);
        recommendationSettingsValid = pValid && iValid && dValid && confidenceValid;

        RecommendationSettings draft = recommendationSettingsValid ? recommendationSettingsFromFields() : null;
        recommendationSettingsDirty = recommendationSettingsValid
                && !sameRecommendationSettings(draft, acceptedRecommendationSettings);

        if (!recommendationSettingsValid) {
            recommendationSettingsStatusValue.setText("Invalid setting");
            recommendationSettingsStatusValue.setToolTipText(
                    "Correct the highlighted field, leave it to restore the last accepted value, or press Recalculate to restore invalid fields.");
            copyRecommendationButton.setEnabled(false);
        } else if (recommendationSettingsDirty) {
            recommendationSettingsStatusValue.setText("Recalculate to apply");
            recommendationSettingsStatusValue.setToolTipText(
                    "The displayed values are valid but have not been used by the model yet. Recalculate before copying a report.");
            copyRecommendationButton.setEnabled(false);
        } else {
            recommendationSettingsStatusValue.setText("Settings accepted");
            recommendationSettingsStatusValue.setToolTipText(
                    "The displayed limits are the validated settings used by the current model result.");
            copyRecommendationButton.setEnabled(latestRecommendation != null);
        }
    }

    private boolean updateRecommendationFieldAppearance(JTextField field) {
        String error = recommendationFieldError(field);
        boolean valid = error == null;
        field.setBorder(valid ? recommendationNormalBorder : recommendationInvalidBorder);
        field.setBackground(valid ? recommendationNormalBackground : recommendationInvalidBackground);
        String base = recommendationFieldBaseTooltip(field);
        field.setToolTipText(valid ? base : base + " Current value is invalid: " + error);
        return valid;
    }

    private String recommendationFieldError(JTextField field) {
        try {
            parsePercent(field.getText(), recommendationFieldName(field), recommendationFieldMaximum(field));
            return null;
        } catch (IllegalArgumentException ex) {
            return ex.getMessage();
        }
    }

    private void restoreRecommendationFieldIfInvalid(JTextField field) {
        if (recommendationFieldError(field) == null) return;
        String name = recommendationFieldName(field);
        suppressRecommendationSettingEvents = true;
        try {
            field.setText(formatGain(acceptedRecommendationValue(field)));
        } finally {
            suppressRecommendationSettingEvents = false;
        }
        updateRecommendationSettingsState();
        footerStatus.setText(name + " was invalid and has been restored to the last accepted value. "
                + "No ECU value was changed.");
    }

    private List<String> restoreInvalidRecommendationFields() {
        List<String> restored = new ArrayList<String>();
        JTextField[] fields = recommendationFields();
        suppressRecommendationSettingEvents = true;
        try {
            for (JTextField field : fields) {
                if (recommendationFieldError(field) != null) {
                    restored.add(recommendationFieldName(field));
                    field.setText(formatGain(acceptedRecommendationValue(field)));
                }
            }
        } finally {
            suppressRecommendationSettingEvents = false;
        }
        updateRecommendationSettingsState();
        return restored;
    }

    private void setRecommendationFieldsFromSettings(RecommendationSettings settings) {
        suppressRecommendationSettingEvents = true;
        try {
            maximumPChangeField.setText(formatGain(settings.getMaximumPChangePercent()));
            maximumIChangeField.setText(formatGain(settings.getMaximumIChangePercent()));
            maximumDChangeField.setText(formatGain(settings.getMaximumDChangePercent()));
            minimumConfidenceField.setText(formatGain(settings.getMinimumConfidencePercent()));
        } finally {
            suppressRecommendationSettingEvents = false;
        }
    }

    private JTextField[] recommendationFields() {
        return new JTextField[] {
                maximumPChangeField, maximumIChangeField, maximumDChangeField, minimumConfidenceField
        };
    }

    private String recommendationFieldName(JTextField field) {
        if (field == maximumPChangeField) return "Maximum P change";
        if (field == maximumIChangeField) return "Maximum I change";
        if (field == maximumDChangeField) return "Maximum D change";
        return "Minimum confidence";
    }

    private double recommendationFieldMaximum(JTextField field) {
        return field == minimumConfidenceField ? 100.0 : 50.0;
    }

    private double acceptedRecommendationValue(JTextField field) {
        if (field == maximumPChangeField) return acceptedRecommendationSettings.getMaximumPChangePercent();
        if (field == maximumIChangeField) return acceptedRecommendationSettings.getMaximumIChangePercent();
        if (field == maximumDChangeField) return acceptedRecommendationSettings.getMaximumDChangePercent();
        return acceptedRecommendationSettings.getMinimumConfidencePercent();
    }

    private String recommendationFieldBaseTooltip(JTextField field) {
        if (field == minimumConfidenceField) {
            return "Minimum model confidence required before proposed gains are displayed. Allowed range: 0 to 100 percent.";
        }
        return "Maximum relative change the conservative model may propose for this gain in one iteration. "
                + "Allowed range: 0 to 50 percent. The plugin never writes the value to the ECU.";
    }

    private static boolean sameRecommendationSettings(RecommendationSettings left, RecommendationSettings right) {
        if (left == null || right == null) return false;
        return close(left.getMaximumPChangePercent(), right.getMaximumPChangePercent())
                && close(left.getMaximumIChangePercent(), right.getMaximumIChangePercent())
                && close(left.getMaximumDChangePercent(), right.getMaximumDChangePercent())
                && close(left.getMinimumConfidencePercent(), right.getMinimumConfidencePercent());
    }

    private static boolean close(double left, double right) {
        return Math.abs(left - right) <= 0.0000001;
    }

    private static String joinNames(List<String> names) {
        if (names == null || names.isEmpty()) return "settings";
        if (names.size() == 1) return names.get(0);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) text.append(i == names.size() - 1 ? " and " : ", ");
            text.append(names.get(i));
        }
        return text.toString();
    }

    private void applyRecommendation(PidRecommendation recommendation) {
        latestRecommendation = recommendation;
        recommendationGainModel.setRecommendation(recommendation);
        String gate = latestDatasetAssessment == null
                ? DatasetAssessment.INSUFFICIENT : latestDatasetAssessment.getLevel();
        recommendationDatasetGateValue.setText(gate);
        recommendationDatasetGateValue.setToolTipText(latestDatasetAssessment == null
                ? "No dataset assessment is available." : latestDatasetAssessment.getSummary());
        if (recommendation == null) {
            recommendationStatusValue.setText("No result");
            recommendationGroupValue.setText("None");
            recommendationConfidenceValue.setText("—");
            recommendationDetails.setText("No recommendation result is available.");
            updateRecommendationSettingsState();
            return;
        }
        recommendationStatusValue.setText(recommendation.getStatus());
        recommendationStatusValue.setToolTipText(recommendation.getSummary());
        String group = recommendation.getBestGroup().isEmpty() ? "None" : recommendation.getBestGroup();
        recommendationGroupValue.setText(group);
        recommendationGroupValue.setToolTipText(group);
        recommendationConfidenceValue.setText(
                Double.isNaN(recommendation.getConfidencePercent())
                        ? "Not calculated"
                        : format(recommendation.getConfidencePercent()) + "%");
        recommendationConfidenceValue.setToolTipText(
                Double.isNaN(recommendation.getConfidencePercent())
                        ? "Confidence is calculated only after the dataset readiness gate passes."
                        : "Calculated model confidence for the strongest compatible group.");
        recommendationDetails.setText(buildRecommendationDetails(recommendation));
        recommendationDetails.setCaretPosition(0);
        updateRecommendationSettingsState();
    }

    private static String buildRecommendationDetails(PidRecommendation recommendation) {
        StringBuilder text = new StringBuilder();
        text.append(recommendation.getStatus()).append('\n');
        text.append(recommendation.getSummary()).append("\n\n");
        text.append("Strongest group: ").append(
                recommendation.getBestGroup().isEmpty() ? "None" : recommendation.getBestGroup()).append('\n');
        text.append("Model confidence: ").append(
                Double.isNaN(recommendation.getConfidencePercent())
                        ? "Not calculated"
                        : format(recommendation.getConfidencePercent()) + "%").append("\n\n");
        ResponseSummary response = recommendation.getResponseSummary();
        text.append("Model measurements\n");
        text.append("Compatible transient events: ").append(response.getTransientCount())
                .append("; Good: ").append(response.getGoodTransientCount())
                .append("; compatible steady holds: ").append(response.getSteadyCount()).append('\n');
        text.append("Median response delay: ").append(formatValue(response.getMedianDelaySeconds(), " s"))
                .append("; settling: ").append(formatValue(response.getMedianSettlingSeconds(), " s"))
                .append("; consistency: ").append(formatValue(response.getTransientConsistencyPercent(), "%")).append('\n');
        text.append("Median opposite-side overshoot: ")
                .append(formatRatioPercent(response.getMedianOppositeOvershootRatio()))
                .append("; decay ratio: ").append(formatValue(response.getMedianDecayRatio(), ""))
                .append("; residual oscillation: ").append(formatValue(response.getMedianOscillationHz(), " Hz")).append('\n');
        text.append("Median steady signed error: ").append(formatValue(response.getMedianSteadySignedErrorRpm(), " RPM"))
                .append("; mean absolute error: ").append(formatValue(response.getMedianSteadyMeanAbsoluteErrorRpm(), " RPM"))
                .append("; RPM standard deviation: ").append(formatValue(response.getMedianSteadyRpmStandardDeviation(), " RPM")).append('\n');
        text.append("Median steady correction reversals: ")
                .append(formatValue(response.getMedianSteadyCorrectionReversalsPerSecond(), "/s"))
                .append("; D-term span: ").append(formatValue(response.getMedianSteadyDerivativeSpan(), ""))
                .append("; I-term span: ").append(formatValue(response.getMedianSteadyIntegralSpan(), "")).append("\n\n");
        text.append(recommendation.getDetails());
        return text.toString();
    }

    private static String formatValue(double value, String suffix) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "Unavailable" : format(value) + suffix;
    }

    private static String formatRatioPercent(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? "Unavailable" : format(value * 100.0) + "%";
    }

    private void copyRecommendation() {
        updateRecommendationSettingsState();
        if (!recommendationSettingsValid) {
            footerStatus.setText("Recommendation settings are invalid. Correct them or restore the last accepted values before copying.");
            return;
        }
        if (recommendationSettingsDirty) {
            footerStatus.setText("Recommendation settings have changed. Press Recalculate before copying a report.");
            return;
        }
        if (latestRecommendation == null) {
            footerStatus.setText("There is no recommendation result to copy.");
            return;
        }
        PidRecommendation r = latestRecommendation;
        StringBuilder text = new StringBuilder();
        text.append("EPICEFI PID Autotune conservative model — version ")
                .append(PidAutotunePlugin.VERSION).append('\n');
        text.append("Status: ").append(r.getStatus()).append('\n');
        text.append("Dataset gate: ").append(latestDatasetAssessment == null
                ? DatasetAssessment.INSUFFICIENT : latestDatasetAssessment.getLevel()).append('\n');
        text.append("Strongest group: ").append(r.getBestGroup().isEmpty() ? "None" : r.getBestGroup()).append('\n');
        text.append("Confidence: ").append(Double.isNaN(r.getConfidencePercent())
                ? "Not calculated" : format(r.getConfidencePercent()) + "%").append('\n');
        RecommendationSettings accepted = acceptedRecommendationSettings;
        text.append("Safety limits: P ±").append(formatGain(accepted.getMaximumPChangePercent()))
                .append("%; I ±").append(formatGain(accepted.getMaximumIChangePercent()))
                .append("%; D ±").append(formatGain(accepted.getMaximumDChangePercent()))
                .append("%; minimum confidence ").append(formatGain(accepted.getMinimumConfidencePercent())).append("%\n\n");
        TuneSnapshot current = r.getCurrent();
        TuneSnapshot proposed = r.getProposed();
        text.append("Gain\tCurrent\tProposed\tChange\tExpected effect\tReason\n");
        appendRecommendationGain(text, "P", current == null ? Double.NaN : current.getP(),
                r.isProposalAvailable() && proposed != null ? proposed.getP() : Double.NaN,
                r.getPChangePercent(), r.getPEffect(), r.getPReason(), r.isProposalAvailable());
        appendRecommendationGain(text, "I", current == null ? Double.NaN : current.getI(),
                r.isProposalAvailable() && proposed != null ? proposed.getI() : Double.NaN,
                r.getIChangePercent(), r.getIEffect(), r.getIReason(), r.isProposalAvailable());
        appendRecommendationGain(text, "D", current == null ? Double.NaN : current.getD(),
                r.isProposalAvailable() && proposed != null ? proposed.getD() : Double.NaN,
                r.getDChangePercent(), r.getDEffect(), r.getDReason(), r.isProposalAvailable());
        text.append('\n').append(buildRecommendationDetails(r)).append('\n');
        text.append("\nRead-only result: no ECU value was written or burned.\n");
        copyToClipboard(text.toString());
        footerStatus.setText("Recommendation result copied to the clipboard. No ECU value was changed.");
    }

    private static void appendRecommendationGain(
            StringBuilder text, String name, double current, double proposed, double change,
            String effect, String reason, boolean available) {
        text.append(name).append('\t')
                .append(formatGain(current)).append('\t')
                .append(available ? formatGain(proposed) : "Not calculated").append('\t')
                .append(available ? formatSigned(change) + "%" : "—").append('\t')
                .append(clean(effect)).append('\t').append(clean(reason)).append('\n');
    }

    private TuneSnapshot currentTuneSnapshot(String source) {
        return new TuneSnapshot(
                mappedDouble("idleRpmPid_pFactor", Double.NaN),
                mappedDouble("idleRpmPid_iFactor", Double.NaN),
                mappedDouble("idleRpmPid_dFactor", Double.NaN),
                source,
                false);
    }

    private void applyManualGainSnapshot() {
        if (currentAnalyzedLog == null) {
            footerStatus.setText("Select an imported log before editing its gain snapshot.");
            return;
        }
        try {
            double p = parseGain(gainPField.getText(), "P");
            double i = parseGain(gainIField.getText(), "I");
            double d = parseGain(gainDField.getText(), "D");
            currentAnalyzedLog.setTuneSnapshot(new TuneSnapshot(p, i, d, "Manual override", true));
            rebuildDatasetRows();
            updateGainSnapshotEditor();
            footerStatus.setText("Stored a manual P/I/D snapshot for " + currentAnalyzedLog.getDisplayName()
                    + ". No ECU value or source log was changed.");
        } catch (IllegalArgumentException ex) {
            footerStatus.setText(ex.getMessage());
        }
    }

    private void captureCurrentGainSnapshot() {
        if (currentAnalyzedLog == null) {
            footerStatus.setText("Select an imported log before capturing current TunerStudio gains.");
            return;
        }
        TuneSnapshot snapshot = currentTuneSnapshot("Recaptured from current TunerStudio project");
        if (!snapshot.isComplete()) {
            footerStatus.setText("Current TunerStudio P/I/D values are incomplete; the selected log snapshot was not changed.");
            return;
        }
        currentAnalyzedLog.setTuneSnapshot(snapshot);
        rebuildDatasetRows();
        updateGainSnapshotEditor();
        footerStatus.setText("Stored the currently displayed TunerStudio P/I/D values for "
                + currentAnalyzedLog.getDisplayName() + ". No ECU value was changed.");
    }

    private void applyVssSourceMode() {
        if (currentAnalyzedLog == null) {
            footerStatus.setText("Select an imported log before changing its VSS source.");
            return;
        }
        VssSourceMode mode = (VssSourceMode) vssSourceModeCombo.getSelectedItem();
        if (mode == null) {
            footerStatus.setText("Select a VSS source before applying it.");
            return;
        }
        currentAnalyzedLog.setVssSourceMode(mode);
        rebuildDatasetRows();
        updateGainSnapshotEditor();
        footerStatus.setText("Stored VSS source ‘" + mode.getDisplayName() + "’ for "
                + currentAnalyzedLog.getDisplayName()
                + ". Dataset integrity and grouping were rebuilt; no ECU value or source log was changed.");
    }

    private void updateGainSnapshotEditor() {
        boolean available = currentAnalyzedLog != null;
        gainPField.setEnabled(available && !logBusy);
        gainIField.setEnabled(available && !logBusy);
        gainDField.setEnabled(available && !logBusy);
        applyGainSnapshotButton.setEnabled(available && !logBusy);
        captureCurrentGainsButton.setEnabled(available && !logBusy && dataAccess != null);
        vssSourceModeCombo.setEnabled(available && !logBusy);
        applyVssSourceButton.setEnabled(available && !logBusy);
        if (!available) {
            gainLogValue.setText("No log selected");
            gainSourceValue.setText("—");
            gainPField.setText("");
            gainIField.setText("");
            gainDField.setText("");
            vssSourceModeCombo.setSelectedItem(VssSourceMode.AUTOMATIC);
            vssSourceModeCombo.setToolTipText(VssSourceMode.AUTOMATIC.getDescription());
            return;
        }
        TuneSnapshot snapshot = currentAnalyzedLog.getTuneSnapshot();
        gainLogValue.setText(currentAnalyzedLog.getDisplayName());
        gainLogValue.setToolTipText(currentAnalyzedLog.getData().getSourceFile().getAbsolutePath());
        gainSourceValue.setText(snapshot.describeSource());
        gainSourceValue.setToolTipText(snapshot.describeSource());
        gainPField.setText(formatGain(snapshot.getP()));
        gainIField.setText(formatGain(snapshot.getI()));
        gainDField.setText(formatGain(snapshot.getD()));
        VssSourceMode mode = currentAnalyzedLog.getVssSourceMode();
        vssSourceModeCombo.setSelectedItem(mode);
        vssSourceModeCombo.setToolTipText(mode.getDescription());
    }

    private static double parseGain(String text, String name) {
        try {
            double value = Double.parseDouble(text == null ? "" : text.trim());
            if (Double.isNaN(value) || Double.isInfinite(value) || value < 0.0 || value > 10000.0) {
                throw new NumberFormatException();
            }
            return value;
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException(name + " gain must be a finite value from 0 to 10000.");
        }
    }

    private static String formatGain(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        synchronized (GAIN) { return GAIN.format(value); }
    }

    private static String datasetEventKey(DatasetEvent row) {
        return row.getAnalyzedLog().getIdentity() + "|" + row.getEvent().getType() + "|"
                + raw(row.getEvent().getTriggerSeconds());
    }

    private static int findMatchingEventIndex(List<IdleEvent> events, IdleEvent preferred) {
        for (int i = 0; i < events.size(); i++) {
            IdleEvent event = events.get(i);
            if (event == preferred) return i;
            if (event.getType().equals(preferred.getType())
                    && Math.abs(event.getTriggerSeconds() - preferred.getTriggerSeconds()) < 0.001) return i;
        }
        return findPreferredEventIndex(events);
    }

    private static String defaultExportName(SelectedEvent selection) {
        String name = selection.data.getSourceFile().getName().replaceFirst("(?i)\\.(msl|csv)$", "");
        String event = selection.event.getType().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        return name + "_" + event + "_" + format(selection.event.getTriggerSeconds()).replace('.', '-') + "s.csv";
    }

    private static String csv(String value) {
        String safe = value == null ? "" : value;
        if (safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0 || safe.indexOf('\n') >= 0) {
            return '"' + safe.replace("\"", "\"\"") + '"';
        }
        return safe;
    }

    private static String raw(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        return Double.toString(value);
    }

    private void updateLogDisplay(IdleLogData data, IdleAnalysisResult analysis, IdleEvent preferredEvent) {
        logSamplesValue.setText(String.valueOf(data.getSampleCount()));
        logDurationValue.setText(format(data.getDurationSeconds()) + " s");
        logRateValue.setText(format(data.getSampleRateHz()) + " Hz");
        int requiredMissing = 0;
        for (LogChannelMapping row : data.buildMappingRows()) {
            if (row.isRequired() && !"Mapped".equals(row.getStatus())) requiredMissing++;
        }
        logChannelsValue.setText(data.getMappedChannelCount() + " mapped; " + requiredMissing + " required missing");
        int steadyCount = 0;
        int responseCount = 0;
        for (IdleEvent event : analysis.getEvents()) {
            if (event.isSteadyHold()) steadyCount++;
            if ("Transient response".equals(event.getAnalysisUse())) responseCount++;
        }
        idleEventsValue.setText(
                analysis.getEvents().size() + " events (" + steadyCount + " steady, " + responseCount
                        + " response); " + format(analysis.getClosedLoopSeconds()) + " s closed loop");
        logChannelModel.setRows(data.buildMappingRows());
        idleEventModel.setRows(analysis.getEvents());
        logWarnings.setText(buildWarningsText(data, analysis));
        logWarnings.setCaretPosition(0);

        if (!analysis.getEvents().isEmpty()) {
            int preferred = preferredEvent == null
                    ? findPreferredEventIndex(analysis.getEvents())
                    : findMatchingEventIndex(analysis.getEvents(), preferredEvent);
            idleEventTable.setRowSelectionInterval(preferred, preferred);
            IdleEvent selected = analysis.getEvents().get(preferred);
            logChart.setData(data, selected);
            DatasetEvent datasetRow = findDatasetEvent(currentAnalyzedLog, selected);
            eventDetails.setText(datasetRow == null ? buildEventDetails(selected) : buildDatasetEventDetails(datasetRow));
            eventDetails.setCaretPosition(0);
        } else {
            idleEventTable.clearSelection();
            logChart.setData(data, null);
            eventDetails.setText("No idle event was detected in this log.");
        }
    }

    private void updateSelectedEventChart() {
        if (latestLogData == null || latestAnalysis == null) return;
        int viewRow = idleEventTable.getSelectedRow();
        if (viewRow < 0) {
            logChart.setData(latestLogData, null);
            eventDetails.setText("Select a detected event to inspect its diagnostics.");
            return;
        }
        int modelRow = idleEventTable.convertRowIndexToModel(viewRow);
        IdleEvent selected = idleEventModel.getRow(modelRow);
        logChart.setData(latestLogData, selected);
        DatasetEvent datasetRow = findDatasetEvent(currentAnalyzedLog, selected);
        eventDetails.setText(datasetRow == null ? buildEventDetails(selected) : buildDatasetEventDetails(datasetRow));
        eventDetails.setCaretPosition(0);
    }

    private static IdleAnalysisSettings applyLiveCaptureMetadata(IdleLogData data, IdleAnalysisSettings base) {
        if (data == null || base == null) return base;
        String profile = "Live custom";
        double settlingBand = Double.NaN;
        for (String raw : data.getMetadata()) {
            String metadata = raw == null ? "" : raw.replace("#", "").trim();
            String lower = metadata.toLowerCase(Locale.ROOT);
            int profileIndex = lower.indexOf("capture profile:");
            if (profileIndex >= 0) {
                profile = metadata.substring(profileIndex + "capture profile:".length()).trim();
            }
            int settlingIndex = lower.indexOf("settling");
            if (lower.indexOf("capture tolerances:") >= 0 && settlingIndex >= 0) {
                double parsed = firstNumberAfter(metadata, settlingIndex + "settling".length());
                if (!Double.isNaN(parsed) && !Double.isInfinite(parsed) && parsed >= 20.0 && parsed <= 300.0) {
                    settlingBand = parsed;
                }
            }
        }
        return Double.isNaN(settlingBand) ? base : base.withLiveCaptureOverrides(settlingBand, profile);
    }

    private static TuneSnapshot applyLiveGainMetadata(IdleLogData data, TuneSnapshot fallback) {
        if (data == null) return fallback;
        for (String raw : data.getMetadata()) {
            String metadata = raw == null ? "" : raw.replace("#", "").trim();
            String lower = metadata.toLowerCase(Locale.ROOT);
            int marker = lower.indexOf("pid gains at observer start:");
            if (marker < 0) continue;
            int colon = metadata.indexOf(':', marker);
            String gainText = colon >= 0 ? metadata.substring(colon + 1) : metadata.substring(marker);
            double p = Double.NaN;
            double i = Double.NaN;
            double d = Double.NaN;
            for (String part : gainText.split("/")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                char gain = Character.toUpperCase(trimmed.charAt(0));
                double value = firstNumberAfter(trimmed, 1);
                if (gain == 'P') p = value;
                else if (gain == 'I') i = value;
                else if (gain == 'D') d = value;
            }
            if (!Double.isNaN(p) && !Double.isInfinite(p)
                    && !Double.isNaN(i) && !Double.isInfinite(i)
                    && !Double.isNaN(d) && !Double.isInfinite(d)) {
                return new TuneSnapshot(p, i, d,
                        "Captured from live-session metadata at observer start", false);
            }
        }
        return fallback;
    }

    private static double firstNumberAfter(String text, int start) {
        if (text == null) return Double.NaN;
        int index = Math.max(0, start);
        while (index < text.length()) {
            char value = text.charAt(index);
            if ((value >= '0' && value <= '9') || value == '-' || value == '.') break;
            index++;
        }
        int end = index;
        while (end < text.length()) {
            char value = text.charAt(end);
            if (!((value >= '0' && value <= '9') || value == '-' || value == '.')) break;
            end++;
        }
        if (end <= index) return Double.NaN;
        try { return Double.parseDouble(text.substring(index, end)); }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    private IdleAnalysisSettings analysisSettingsFromMapping() {
        IdleAnalysisSettings defaults = IdleAnalysisSettings.defaults();
        return new IdleAnalysisSettings(
                mappedDouble("idlePidDeactivationTpsThreshold", defaults.getTpsThreshold()),
                mappedDouble("maxIdleVss", defaults.getMaxVehicleSpeed()),
                mappedDouble("idlePidRpmUpperLimit", defaults.getRpmUpperLimit()),
                mappedDouble("idlePidRpmDeadZone", defaults.getRpmDeadZone()),
                mappedDouble("idleRpmPid_minValue", defaults.getCorrectionMinimum()),
                mappedDouble("idleRpmPid_maxValue", defaults.getCorrectionMaximum()),
                defaults.getMinimumRegionSeconds());
    }

    private double mappedDouble(String name, double fallback) {
        for (DiagnosticRow row : latestMappingRows) {
            if (name.equals(row.getName())) {
                try { return Double.parseDouble(row.getValue().trim()); }
                catch (RuntimeException ignored) { return fallback; }
            }
        }
        return fallback;
    }

    private String buildWarningsText(IdleLogData data, IdleAnalysisResult analysis) {
        StringBuilder text = new StringBuilder();
        for (String metadata : data.getMetadata()) {
            if (!metadata.trim().isEmpty()) text.append(metadata).append('\n');
        }
        if (!data.getMetadata().isEmpty()) text.append('\n');
        text.append("Source columns: ").append(data.getSourceColumnCount()).append('\n');
        text.append("Extracted analyser channels: ").append(data.getMappedChannelCount()).append('\n');
        text.append("Correction source: ")
                .append(analysis.isDerivedCorrectionUsed()
                        ? "Commanded idle position minus base idle position"
                        : "Reported PID output or unavailable")
                .append("\n\n");
        for (String warning : analysis.getWarnings()) text.append("• ").append(warning).append('\n');
        text.append("\nVersion 0.4.3 adds read-only iterative live-tuning history, original and active baselines, full-history CSV export, and a diminishing-returns guard while retaining guided capture, correct DFCO handling, and conservative recommendations. It cannot apply or burn PID gains.");
        return text.toString();
    }

    private void updateMappingStatus(List<DiagnosticRow> rows) {
        int mapped = 0;
        int missing = 0;
        int errors = 0;
        for (DiagnosticRow row : rows) {
            if ("Mapped".equals(row.getStatus())) mapped++;
            else if (row.getStatus().contains("Not found")) missing++;
            else if (row.getStatus().toLowerCase(Locale.ROOT).contains("error")) errors++;
        }
        mappingValue.setText(mapped + " found, " + missing + " missing, " + errors + " read errors");
        mappingValue.setToolTipText("Known idle PID mapping result for signature: " + controllerSignature);
    }

    private void updateDiagnosticFilter() {
        final String text = diagnosticsFilter.getText() == null
                ? ""
                : diagnosticsFilter.getText().trim().toLowerCase(Locale.ROOT);
        final String type = (String) diagnosticsType.getSelectedItem();

        diagnosticsSorter.setRowFilter(new RowFilter<DiagnosticTableModel, Integer>() {
            @Override
            public boolean include(Entry<? extends DiagnosticTableModel, ? extends Integer> entry) {
                String category = String.valueOf(entry.getValue(0));
                if ("Parameters".equals(type) && !"Parameter".equals(category)) return false;
                if ("Output channels".equals(type) && !"Output channel".equals(category)) return false;
                if (text.isEmpty()) return true;
                for (int column = 0; column < entry.getValueCount(); column++) {
                    if (String.valueOf(entry.getValue(column)).toLowerCase(Locale.ROOT).contains(text)) return true;
                }
                return false;
            }
        });
    }

    private void copyMappedData() {
        if (latestMappingRows.isEmpty()) {
            footerStatus.setText("There is no mapped data to copy yet.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("Category\tName\tCurrent value\tUnits\tMinimum\tMaximum\tType\tStatus\tDetails\n");
        for (DiagnosticRow row : latestMappingRows) {
            text.append(clean(row.getCategory())).append('\t')
                    .append(clean(row.getName())).append('\t')
                    .append(clean(row.getValue())).append('\t')
                    .append(clean(row.getUnits())).append('\t')
                    .append(clean(row.getMinimum())).append('\t')
                    .append(clean(row.getMaximum())).append('\t')
                    .append(clean(row.getDataType())).append('\t')
                    .append(clean(row.getStatus())).append('\t')
                    .append(clean(row.getDetails())).append('\n');
        }
        copyToClipboard(text.toString());
        footerStatus.setText("Mapped idle PID data copied to the clipboard. No ECU values were changed.");
    }

    private void copyLogSummary() {
        if (latestLogData == null || latestAnalysis == null) {
            footerStatus.setText("There is no imported log summary to copy yet.");
            return;
        }
        StringBuilder text = new StringBuilder();
        text.append("EPICEFI PID Autotune log analysis — version ").append(PidAutotunePlugin.VERSION).append('\n');
        text.append("File: ").append(latestLogData.getSourceFile().getAbsolutePath()).append('\n');
        text.append("Samples: ").append(latestLogData.getSampleCount()).append('\n');
        text.append("Duration: ").append(format(latestLogData.getDurationSeconds())).append(" s\n");
        text.append("Median sample rate: ").append(format(latestLogData.getSampleRateHz())).append(" Hz\n");
        text.append("Closed-loop time: ").append(format(latestAnalysis.getClosedLoopSeconds())).append(" s\n");
        if (currentAnalyzedLog != null) {
            text.append("P/I/D snapshot: ").append(currentAnalyzedLog.getTuneSnapshot().toSignature())
                    .append(" — ").append(currentAnalyzedLog.getTuneSnapshot().describeSource()).append('\n');
            text.append("VSS source: ").append(currentAnalyzedLog.getVssSourceMode().getDisplayName()).append('\n');
        }
        text.append('\n');
        text.append("Event\tUse\tWindow start s\tTrigger s\tEvaluation s\tTarget RPM\tTarget drift RPM\tCLT median C\tCLT minimum C\tCLT maximum C\tAverage RPM\tMean signed error\tMean abs error\tRPM std dev\tOvershoot\tUndershoot\tSettling s\tCorrection minimum\tCorrection maximum\tAt limit %\tReversals per s\tCommanded minimum\tCommanded maximum\tBase minimum\tBase maximum\tP minimum\tP maximum\tI minimum\tI maximum\tD minimum\tD maximum\tMotion\tIdle entry\tVSS source\tVSS integrity\tVSS dropouts\tVSS discontinuities\tReadiness gate\tQuality\tNotes\n");
        for (IdleEvent event : latestAnalysis.getEvents()) {
            DatasetEvent datasetRow = findDatasetEvent(currentAnalyzedLog, event);
            text.append(clean(event.getType())).append('\t')
                    .append(clean(event.getAnalysisUse())).append('\t')
                    .append(format(event.getStartSeconds())).append('\t')
                    .append(format(event.getTriggerSeconds())).append('\t')
                    .append(format(event.getEvaluationDurationSeconds())).append('\t')
                    .append(format(event.getTargetRpm())).append('\t')
                    .append(format(event.getTargetDriftRpm())).append('\t')
                    .append(format(event.getCoolantMedian())).append('\t')
                    .append(format(event.getCoolantMinimum())).append('\t')
                    .append(format(event.getCoolantMaximum())).append('\t')
                    .append(format(event.getAverageRpm())).append('\t')
                    .append(format(event.getMeanSignedError())).append('\t')
                    .append(format(event.getMeanAbsoluteError())).append('\t')
                    .append(format(event.getRpmStandardDeviation())).append('\t')
                    .append(format(event.getOvershootRpm())).append('\t')
                    .append(format(event.getUndershootRpm())).append('\t')
                    .append(event.isSettlingApplicable() ? format(event.getSettlingSeconds()) : "").append('\t')
                    .append(format(event.getCorrectionMinimum())).append('\t')
                    .append(format(event.getCorrectionMaximum())).append('\t')
                    .append(format(event.getCorrectionLimitPercent())).append('\t')
                    .append(format(event.getCorrectionReversalsPerSecond())).append('\t')
                    .append(format(event.getCommandedIdleMinimum())).append('\t')
                    .append(format(event.getCommandedIdleMaximum())).append('\t')
                    .append(format(event.getBaseIdleMinimum())).append('\t')
                    .append(format(event.getBaseIdleMaximum())).append('\t')
                    .append(format(event.getPTermMinimum())).append('\t')
                    .append(format(event.getPTermMaximum())).append('\t')
                    .append(format(event.getITermMinimum())).append('\t')
                    .append(format(event.getITermMaximum())).append('\t')
                    .append(format(event.getDTermMinimum())).append('\t')
                    .append(format(event.getDTermMaximum())).append('\t');
            if (datasetRow != null) {
                text.append(clean(datasetRow.getIntegrity().getMotionClass())).append('\t')
                        .append(clean(datasetRow.getIntegrity().getEntryCause())).append('\t')
                        .append(clean(datasetRow.getIntegrity().getVssBasis())).append('\t')
                        .append(clean(datasetRow.getIntegrity().getVssStatus())).append('\t')
                        .append(datasetRow.getIntegrity().getDropoutCount()).append('\t')
                        .append(datasetRow.getIntegrity().getDiscontinuityCount()).append('\t')
                        .append(datasetRow.isRecommendationGateEligible() ? "Eligible" : "Blocked").append('\t');
            } else {
                text.append("\t\t\t\t\t\t\t");
            }
            text.append(clean(event.getQuality())).append('\t')
                    .append(clean(event.getQualityDetails())).append('\n');
        }
        text.append("\nDataset readiness: ")
                .append(latestDatasetAssessment == null ? DatasetAssessment.INSUFFICIENT : latestDatasetAssessment.getLevel())
                .append('\n');
        if (latestDatasetAssessment != null) text.append(latestDatasetAssessment.getDetails()).append("\n");
        text.append("\n").append(logWarnings.getText());
        copyToClipboard(text.toString());
        footerStatus.setText("Log analysis summary copied to the clipboard. No ECU values were changed.");
    }

    private static int findPreferredEventIndex(List<IdleEvent> events) {
        int bestIndex = 0;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < events.size(); i++) {
            IdleEvent event = events.get(i);
            int score;
            if ("Good for analysis".equals(event.getQuality())) score = 400;
            else if ("Usable".equals(event.getQuality())) score = 300;
            else if ("Overview only".equals(event.getQuality())) score = 100;
            else if ("Poor".equals(event.getQuality())) score = 50;
            else score = 0;
            if (event.isFutureGainEligible()) score += 30;
            else if (event.isSteadyHold()) score += 20;
            score += Math.min(20, (int) Math.round(event.getEvaluationDurationSeconds()));
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private DatasetEvent findDatasetEvent(AnalyzedLog analyzedLog, IdleEvent event) {
        if (analyzedLog == null || event == null) return null;
        for (DatasetEvent row : datasetEventModel.getRows()) {
            if (!row.getAnalyzedLog().getIdentity().equals(analyzedLog.getIdentity())) continue;
            IdleEvent candidate = row.getEvent();
            if (candidate == event
                    || (candidate.getType().equals(event.getType())
                    && Math.abs(candidate.getTriggerSeconds() - event.getTriggerSeconds()) < 0.001)) {
                return row;
            }
        }
        return null;
    }

    private static String buildDatasetEventDetails(DatasetEvent row) {
        StringBuilder text = new StringBuilder(buildEventDetails(row.getEvent()));
        text.append("\nLog gain snapshot: ").append(row.getTuneSnapshot().toSignature())
                .append(" — ").append(row.getTuneSnapshot().describeSource()).append('\n');
        text.append("Movement class: ").append(row.getIntegrity().getMotionClass()).append('\n');
        text.append("Likely idle-entry cause: ").append(row.getIntegrity().getEntryCause()).append('\n');
        text.append("VSS source: ").append(row.getIntegrity().getVssBasis()).append('\n');
        text.append("VSS integrity: ").append(row.getIntegrity().getVssStatus()).append(". ")
                .append(row.getIntegrity().getDetails()).append('\n');
        text.append("Recommendation readiness gate: ")
                .append(row.isRecommendationGateEligible() ? "Eligible" : "Blocked").append('\n');
        text.append("Dataset eligibility: ").append(row.getEligibility()).append('\n');
        text.append("Compatibility group: ")
                .append(row.getEvent().isFutureGainEligible() ? row.getResponseGroup() : row.getOperatingGroup())
                .append('\n');
        return text.toString();
    }

    private static String buildEventDetails(IdleEvent event) {
        StringBuilder text = new StringBuilder();
        text.append(event.getType()).append(" — ").append(event.getQuality()).append('\n');
        text.append("Analysis use: ").append(event.getAnalysisUse()).append('\n');
        text.append("Window: ").append(format(event.getStartSeconds())).append(" to ")
                .append(format(event.getEndSeconds())).append(" s; trigger ")
                .append(format(event.getTriggerSeconds())).append(" s\n");
        text.append("Target: ").append(format(event.getTargetRpm())).append(" RPM; drift ")
                .append(format(event.getTargetDriftRpm())).append(" RPM\n");
        text.append("Coolant temperature: median ").append(format(event.getCoolantMedian()))
                .append(" °C; range ").append(formatRange(event.getCoolantMinimum(), event.getCoolantMaximum())).append(" °C\n");
        text.append("Average RPM: ").append(format(event.getAverageRpm()))
                .append("; mean signed error ").append(formatSigned(event.getMeanSignedError()))
                .append(" RPM; mean absolute error ").append(format(event.getMeanAbsoluteError())).append(" RPM\n");
        text.append("RPM standard deviation: ").append(format(event.getRpmStandardDeviation()))
                .append(" RPM; overshoot ").append(format(event.getOvershootRpm()))
                .append(" RPM; undershoot ").append(format(event.getUndershootRpm())).append(" RPM\n");
        text.append("Correction: ").append(formatRange(event.getCorrectionMinimum(), event.getCorrectionMaximum()))
                .append("; at limit ").append(format(event.getCorrectionLimitPercent()))
                .append("%; direction reversals ").append(format(event.getCorrectionReversalsPerSecond()))
                .append("/s after 50 ms averaging\n");
        text.append("Commanded idle position: ").append(formatRange(event.getCommandedIdleMinimum(), event.getCommandedIdleMaximum()))
                .append("%; base idle position: ").append(formatRange(event.getBaseIdleMinimum(), event.getBaseIdleMaximum())).append("%\n");
        text.append("P term: ").append(formatRange(event.getPTermMinimum(), event.getPTermMaximum()))
                .append("; I term: ").append(formatRange(event.getITermMinimum(), event.getITermMaximum()))
                .append("; D term: ").append(formatRange(event.getDTermMinimum(), event.getDTermMaximum())).append('\n');
        if (event.isFutureGainEligible()) {
            text.append("Future gain use: eligible as a transient-response candidate once enough comparable events exist.\n");
        } else if (event.isSteadyHold()) {
            text.append("Future gain use: steady-state validation only; combine with transient events before calculating gains.\n");
        } else {
            text.append("Future gain use: not eligible.\n");
        }
        if (!event.getQualityDetails().isEmpty()) text.append("Notes: ").append(event.getQualityDetails()).append('\n');
        return text.toString();
    }

    private static String formatRange(double minimum, double maximum) {
        if (Double.isNaN(minimum) || Double.isNaN(maximum)) return "Unavailable";
        return format(minimum) + " to " + format(maximum);
    }

    private static String formatSigned(double value) {
        String formatted = format(value);
        return value > 0.0 && !formatted.isEmpty() ? "+" + formatted : formatted;
    }

    private void setEcuBusy(boolean busy, String message) {
        ecuBusy = busy;
        setEcuControlsEnabled(dataAccess != null && !busy);
        if (busy && message != null) footerStatus.setText(message);
    }

    private void setLogBusy(boolean busy, String message) {
        logBusy = busy;
        updateLogControls();
        if (busy && message != null) footerStatus.setText(message);
    }

    private void setEcuControlsEnabled(boolean enabled) {
        configurationCombo.setEnabled(enabled && !ecuBusy);
        profileCombo.setEnabled(enabled && !ecuBusy);
        refreshButton.setEnabled(enabled && !ecuBusy);
        diagnosticsFilter.setEnabled(enabled && !ecuBusy);
        diagnosticsType.setEnabled(enabled && !ecuBusy);
        copyMappingButton.setEnabled(enabled && !ecuBusy && !latestMappingRows.isEmpty());
        updateGainSnapshotEditor();
    }

    private void updateLogControls() {
        browseLogButton.setEnabled(!logBusy);
        reloadLogButton.setEnabled(!logBusy && selectedLogFile != null);
        copyLogSummaryButton.setEnabled(!logBusy && latestLogData != null && latestAnalysis != null);
        removeCurrentLogButton.setEnabled(!logBusy && currentAnalyzedLog != null);
        clearDatasetButton.setEnabled(!logBusy && !datasetLogs.isEmpty());
        exportEventCsvButton.setEnabled(!logBusy && selectedEventForExport() != null);
        updateGainSnapshotEditor();
    }

    private File initialLogDirectory() {
        if (selectedLogFile != null && selectedLogFile.getParentFile() != null) return selectedLogFile.getParentFile();
        File tunerStudioProjects = new File(System.getProperty("user.home"), "TunerStudioProjects");
        return tunerStudioProjects.isDirectory() ? tunerStudioProjects : new File(System.getProperty("user.home"));
    }

    private static JTable createTable(DiagnosticTableModel model) {
        JTable table = new JTable(model);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        table.setToolTipText("Select a row to inspect the mapped name, current value, limits, and purpose.");
        return table;
    }

    private static void configureDiagnosticColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {110, 220, 190, 75, 125, 125, 110, 500, 160};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void configureEventColumns(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {180, 150, 95, 85, 90, 80, 90, 80, 100, 90, 95, 80, 80, 85, 95, 125, 80, 90, 75, 110, 480};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void configureDatasetColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {65, 220, 190, 165, 145, 85, 90, 85, 75, 180, 190, 310, 190, 115, 85, 520, 480};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void configureRecommendationColumns(JTable table) {
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {70, 100, 110, 90, 420, 620};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static void configureLogChannelColumns(JTable table) {
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        TableColumnModel columns = table.getColumnModel();
        int[] widths = {190, 75, 250, 90, 190};
        for (int i = 0; i < widths.length && i < columns.getColumnCount(); i++) {
            columns.getColumn(i).setPreferredWidth(widths[i]);
        }
    }

    private static JTextArea createExplanation(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        return area;
    }

    private static GridBagConstraints baseConstraints() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        return c;
    }

    private static void addReadOnlyRow(
            JPanel panel,
            GridBagConstraints base,
            int row,
            String labelText,
            JLabel value,
            String tooltip) {
        GridBagConstraints labelConstraints = (GridBagConstraints) base.clone();
        labelConstraints.gridx = 0;
        labelConstraints.gridy = row;
        labelConstraints.weightx = 0.0;
        JLabel label = new JLabel(labelText + ":");
        label.setToolTipText(tooltip);
        panel.add(label, labelConstraints);

        GridBagConstraints valueConstraints = (GridBagConstraints) base.clone();
        valueConstraints.gridx = 1;
        valueConstraints.gridy = row;
        valueConstraints.weightx = 1.0;
        value.setToolTipText(tooltip);
        panel.add(value, valueConstraints);
    }

    private static void copyToClipboard(String text) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    private static String clean(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
    }

    private static String format(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) return "";
        synchronized (ONE) { return ONE.format(value); }
    }

    private static String safeMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.trim().isEmpty()
                ? current.getClass().getSimpleName()
                : message;
    }

    private static final class SelectedEvent {
        private final IdleLogData data;
        private final IdleEvent event;

        private SelectedEvent(IdleLogData data, IdleEvent event) {
            this.data = data;
            this.event = event;
        }
    }

    private static final class DiscoveryResult {
        private final List<DiagnosticRow> mappingRows;
        private final List<DiagnosticRow> diagnosticRows;

        private DiscoveryResult(List<DiagnosticRow> mappingRows, List<DiagnosticRow> diagnosticRows) {
            this.mappingRows = mappingRows == null
                    ? Collections.<DiagnosticRow>emptyList()
                    : new ArrayList<DiagnosticRow>(mappingRows);
            this.diagnosticRows = diagnosticRows == null
                    ? Collections.<DiagnosticRow>emptyList()
                    : new ArrayList<DiagnosticRow>(diagnosticRows);
        }
    }

    private static final class LogLoadResult {
        private final IdleLogData data;
        private final IdleAnalysisResult analysis;
        private final IdleAnalysisSettings settings;
        private final TuneSnapshot tuneSnapshot;

        private LogLoadResult(IdleLogData data, IdleAnalysisResult analysis,
                              IdleAnalysisSettings settings, TuneSnapshot tuneSnapshot) {
            this.data = data;
            this.analysis = analysis;
            this.settings = settings;
            this.tuneSnapshot = tuneSnapshot;
        }
    }
}
