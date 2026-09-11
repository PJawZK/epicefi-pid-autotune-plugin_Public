package se.anders.tunerstudio.pidautotune.ui;

import com.efiAnalytics.plugin.ecu.ControllerAccess;
import com.efiAnalytics.plugin.ecu.servers.ControllerParameterServer;
import se.anders.tunerstudio.pidautotune.controller.DcIacAutonomousExcitationController;
import se.anders.tunerstudio.pidautotune.controller.DcIacRamWriteCoordinator;
import se.anders.tunerstudio.pidautotune.live.DcIacOperatingContext;
import se.anders.tunerstudio.pidautotune.live.DcIacTunerCoordinator;
import se.anders.tunerstudio.pidautotune.live.LiveSubscriptionReport;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveOutputSubscription;
import se.anders.tunerstudio.pidautotune.live.ProfileLiveSample;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition.Mapping;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition.MappingRole;
import se.anders.tunerstudio.pidautotune.profile.ControllerProfileDefinition.TuningStrategy;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasKnotProposal;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasBinPlacementEngine;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacBiasBinPlacementProposal;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacPidRecommendation;
import se.anders.tunerstudio.pidautotune.recommendation.DcIacStaticRecommendation;
import se.anders.tunerstudio.pidautotune.session.DcIacIterationComparison;
import se.anders.tunerstudio.pidautotune.session.DcIacIterationComparisonEngine;
import se.anders.tunerstudio.pidautotune.session.DcIacTuningSessionSummary;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Autonomous RAM-only DC-IAC tuner over the validated DC-IAC evidence/recommendation engines.
 *
 * Unlike Guided mode, this panel also owns the physical identification excitation. It temporarily
 * snapshots the user's main-idle open-loop table + idleMode, switches the outer idle controller to
 * Open Loop, and commands deterministic local position holds/steps. The existing DC-IAC bias and
 * PID engines remain the only authority for calibration changes. Temporary outer-idle settings are
 * restored on completion, stop, error, disconnect or explicit restore. No Burn operation exists.
 */
final class AutonomousDcIacTuningPanel extends JPanel {
    private enum Stage {
        STOPPED("Stopped"), PREPARE("0 · Prepare open-loop test"),
        BIAS_CAPTURE("1 · Tune bias"), BIAS_RECHECK("2 · Verify bias"),
        PID_CAPTURE("3 · Baseline PID steps"), PID_RETEST("4 · Retest PID candidate"),
        COMPLETE("Complete — best DC-IAC RAM settings retained"),
        RESTORED("Original DC-IAC settings restored"), ERROR("Stopped after error");
        final String label; Stage(String label){this.label=label;}
    }

    private static final double NOMINAL_STEP = 3.5;
    private static final double MIN_AUTONOMOUS_STEP = 3.0;
    private static final double MAX_ACTUAL_STEP = 4.5;
    private static final double TARGET_TOLERANCE = 0.60;
    private static final double POSITION_TOLERANCE = 1.10;
    private static final double HANDOFF_TOLERANCE = 1.25;
    private static final double MAX_TPS = 2.0;
    private static final double MAX_VSS_KPH = 0.5;
    private static final double MIN_RUNNING_RPM = 450.0;
    private static final double MAX_TEST_RPM = 1800.0;
    private static final long PREPARE_STABLE_NANOS = 1_200_000_000L;
    private static final long PLATEAU_STABLE_NANOS = 2_000_000_000L;
    private static final long TARGET_CORRECTION_NANOS = 900_000_000L;
    private static final long CYCLE_RESTART_NANOS = 1_000_000_000L;
    private static final long FINAL_DECISION_WAIT_NANOS = 3_000_000_000L;
    private static final int MAX_SEQUENCE_CYCLES = 2;
    private static final int MAX_FAN_RESTARTS = 2;

    private static final ControllerProfileDefinition AUTONOMOUS_LIVE_PROFILE = new ControllerProfileDefinition(
            "dc-iac-autonomous-excitation",
            "DC-IAC autonomous excitation",
            TuningStrategy.PID,
            Collections.<Mapping>emptyList(),
            Arrays.asList(
                    map("dcIdleTarget", "Actual DC-IAC position target", MappingRole.TARGET, true),
                    map("idlePositionSensor", "Measured DC-IAC position", MappingRole.PROCESS_VALUE, true),
                    map("dcIdleDutyCycle", "DC-IAC motor duty", MappingRole.CONTROLLER_OUTPUT, false),
                    map("dcIdlePositionStatus_error", "DC-IAC position error", MappingRole.ERROR, false),
                    map("dcIdlePositionStatus_resetCounter", "DC-IAC reset counter", MappingRole.RESET_COUNTER, false),
                    map("dcIdleFaultCode", "DC-IAC local fault", MappingRole.FAULT_STATE, true),
                    map("RPMValue", "Engine RPM safety guard", MappingRole.SAFETY_GUARD, true),
                    map("TPSValue", "Throttle safety guard", MappingRole.SAFETY_GUARD, true),
                    map("vehicleSpeedKph", "Stationary safety guard", MappingRole.SAFETY_GUARD, false),
                    map("baseIdlePosition", "Main idle open-loop base position", MappingRole.CONTEXT, true),
                    map("currentIdlePosition", "Main idle final position request", MappingRole.CONTEXT, true),
                    map("VBatt", "Battery voltage", MappingRole.CONTEXT, false),
                    map("coolant", "Coolant temperature", MappingRole.CONTEXT, false),
                    map("fan1On", "Fan load state", MappingRole.CONTEXT, false),
                    map("isIdleClosedLoop", "Outer idle closed-loop state", MappingRole.CONTEXT, false),
                    map("idleClosedLoop", "Outer idle closed-loop position correction", MappingRole.CONTEXT, false),
                    map("isIdling", "Idle phase state", MappingRole.CONTEXT, false)));

    private final DcIacTunerPanel guided;
    private final DcIacIterationComparisonEngine comparisonEngine = new DcIacIterationComparisonEngine();
    private final DcIacBiasBinPlacementEngine placementEngine = new DcIacBiasBinPlacementEngine();
    private final Timer timer;
    private final JCheckBox manualStationary = new JCheckBox(
            "If VSS is unavailable: I confirm the vehicle is stationary and secured");
    private final JLabel phase = new JLabel(Stage.STOPPED.label);
    private final JLabel configuration = new JLabel("—");
    private final JLabel pid = new JLabel("—");
    private final JLabel bias = new JLabel("—");
    private final JLabel writeCount = new JLabel("0 value / 0 bin / 0 PID");
    private final JLabel result = new JLabel("—");
    private final JTextArea instruction = area();
    private final JTextArea evidence = area();
    private final JTextArea history = area();
    private final JScrollPane evidenceScroll = new JScrollPane(evidence);
    private final JScrollPane historyScroll = new JScrollPane(history);
    private final JButton start = new JButton("Start autonomous DC-IAC tuning");
    private final JButton stopKeep = new JButton("Stop + keep current DC-IAC RAM settings");
    private final JButton restoreOriginal = new JButton("Restore original DC-IAC settings");

    private DcIacRamWriteCoordinator writer;
    private DcIacAutonomousExcitationController excitation;
    private DcIacAutonomousExcitationController.Snapshot excitationSnapshot;
    private ProfileLiveOutputSubscription excitationLive;
    private LiveSubscriptionReport excitationReport;
    private DcIacRamWriteCoordinator.PidSnapshot originalPid;
    private DcIacRamWriteCoordinator.BiasSnapshot originalBias;
    private DcIacRamWriteCoordinator.PidSnapshot currentPid;
    private DcIacRamWriteCoordinator.BiasSnapshot currentBias;
    private DcIacRamWriteCoordinator.BiasSnapshot lastBiasBaseline;
    private DcIacRamWriteCoordinator.PidSnapshot pidBaselineGains;
    private DcIacTuningSessionSummary pidBaselineSession;
    private DcIacPidRecommendation activePidCandidate;
    private String cfg;
    private Stage stage = Stage.STOPPED;
    private int biasWrites;
    private int binMoves;
    private int pidWrites;
    private final Set<Integer> relocatedBiasKnots = new HashSet<Integer>();
    private String lastPlacementDetail = "";
    private boolean retryUsed;
    private boolean running;
    private String lastAppliedGain = "";
    private String startCheckpoint = "Idle";
    private String lastFailure = "";
    private String prepareStatus = "Not started";

    // Excitation state.
    private long sessionStartNanos;
    private long prepareStableSince;
    private long openLoopActivatedNanos;
    private long plateauStableSince;
    private long targetMismatchSince;
    private long cycleCompletedNanos;
    private boolean cycleComplete;
    private double baseTarget = Double.NaN;
    private double stepSize = Double.NaN;
    private double targetOffset = Double.NaN;
    private double desiredTarget = Double.NaN;
    private double lastSettledTarget = Double.NaN;
    private double commandReferenceTarget = Double.NaN;
    private int sequenceIndex;
    private int sequenceCycles;
    private int fanRestarts;
    private Double lastFanState;

    AutonomousDcIacTuningPanel(DcIacTunerPanel guided) {
        super(new BorderLayout(8,8));
        this.guided=guided;
        setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        buildUi();
        start.addActionListener(e -> startSession());
        stopKeep.addActionListener(e -> stopKeep("Stopped by user."));
        restoreOriginal.addActionListener(e -> restoreOriginal());
        timer=new Timer(150,e -> tick()); timer.setCoalesce(true);
        refresh();
        applyTheme();
    }

    private static Mapping map(String name, String purpose, MappingRole role, boolean required) {
        return new Mapping(name, purpose, role, required);
    }

    private void buildUi() {
        JPanel top=new JPanel(new BorderLayout(6,6)); top.setOpaque(false);
        JLabel title=new JLabel("Autonomous DC-IAC RAM Tuning"); title.setFont(new Font("Dialog",Font.BOLD,18));
        JLabel sub=new JLabel("Start authorizes temporary audited RAM control · bias first → PID second · exact readback/rollback · never Burn");
        sub.setFont(new Font("Dialog",Font.PLAIN,10));
        JPanel titles=new JPanel(new GridLayout(0,1)); titles.setOpaque(false); titles.add(title); titles.add(sub); top.add(titles,BorderLayout.NORTH);
        instruction.setRows(5); instruction.setFont(new Font("Dialog",Font.BOLD,13));
        top.add(instruction,BorderLayout.CENTER); top.add(manualStationary,BorderLayout.SOUTH); add(top,BorderLayout.NORTH);

        JPanel status=new JPanel(new GridLayout(2,3,6,6));
        status.add(card("Phase",phase)); status.add(card("Configuration",configuration)); status.add(card("Verified PID",pid));
        status.add(card("Bias curve",bias)); status.add(card("Candidate writes",writeCount)); status.add(card("Last result",result));

        evidence.setRows(12); history.setRows(11);
        evidenceScroll.setBorder(BorderFactory.createTitledBorder("Current evidence / gates"));
        historyScroll.setBorder(BorderFactory.createTitledBorder("Autonomous history"));
        StableScrollSupport.install(evidenceScroll);
        StableScrollSupport.install(historyScroll);
        JPanel center=new JPanel(new BorderLayout(6,6)); center.add(status,BorderLayout.NORTH);
        JPanel text=new JPanel(new GridLayout(1,2,6,0)); text.add(evidenceScroll); text.add(historyScroll); center.add(text,BorderLayout.CENTER); add(center,BorderLayout.CENTER);

        JPanel buttons=new JPanel(new FlowLayout(FlowLayout.RIGHT,7,0)); buttons.add(start); buttons.add(stopKeep); buttons.add(restoreOriginal); add(buttons,BorderLayout.SOUTH);
        StableScrollSupport.installRecursively(this);
    }

    private JPanel card(String caption,JLabel value){JPanel p=new JPanel(new BorderLayout(2,2));p.setBorder(BorderFactory.createEmptyBorder(7,8,7,8));JLabel c=new JLabel(caption);c.setFont(new Font("Dialog",Font.PLAIN,9));value.setFont(new Font("Dialog",Font.BOLD,11));p.add(c,BorderLayout.NORTH);p.add(value,BorderLayout.CENTER);p.putClientProperty("caption",c);return p;}

    private void startSession() {
        if(running)return;
        lastFailure=""; result.setText("Starting…"); prepareStatus="Resolving TunerStudio controller connection"; refresh();
        try {
            startCheckpoint="Resolve ControllerAccess";
            ControllerAccess access=resolveControllerAccess();
            if(access==null)throw new IllegalStateException("TunerStudio controller API is not available yet. Connect the ECU and press Start again.");

            startCheckpoint="Attach DC-IAC coordinator"; prepareStatus="Attaching DC-IAC live/evidence coordinator"; refresh();
            guided.workflowEnsureConnected(access);

            startCheckpoint="Resolve ECU configuration";
            cfg=guided.workflowConfigurationName();
            if(cfg.isEmpty())throw new IllegalStateException("No ECU configuration is available yet. Connect TunerStudio to the ECU and press Start again.");

            startCheckpoint="Resolve controller parameter service";
            ControllerParameterServer ps=access.getControllerParameterServer();
            if(ps==null)throw new IllegalStateException("TunerStudio controller-parameter service is not available yet. Connect the ECU and press Start again.");
            writer=new DcIacRamWriteCoordinator(ps);
            excitation=new DcIacAutonomousExcitationController(ps);

            startCheckpoint="Snapshot DC-IAC PID"; prepareStatus="Reading original DC-IAC PID"; refresh();
            originalPid=writer.readPid(cfg); currentPid=originalPid;
            startCheckpoint="Snapshot paired bias curve"; prepareStatus="Reading complete dcIdleBiasBins + dcIdleBiasValues"; refresh();
            originalBias=writer.readBias(cfg); currentBias=originalBias;
            startCheckpoint="Snapshot temporary outer-idle controls"; prepareStatus="Snapshotting idleMode, open-loop table and DC-IAC travel"; refresh();
            excitationSnapshot=excitation.snapshot(cfg);
            resetSessionState();

            startCheckpoint="Subscribe autonomous live channels"; prepareStatus="Subscribing required live channels"; refresh();
            excitationLive=new ProfileLiveOutputSubscription(access);
            excitationReport=excitationLive.start(cfg,AUTONOMOUS_LIVE_PROFILE);
            if(!excitationReport.isUsable())throw new IllegalStateException("Required autonomous live channels missing: " + join(excitationReport.getMissingRequired()));

            startCheckpoint="Verify stationary basis";
            if(!excitationLive.isSubscribed("vehicleSpeedKph") && !manualStationary.isSelected()) {
                throw new IllegalStateException("VSS is unavailable. Confirm the stationary fallback before starting autonomous actuator control.");
            }

            startCheckpoint="Enter PREPARE"; prepareStatus="Waiting for fresh live safety channels";
            DcIacTunerCoordinator c=coordinator();
            if(c!=null&&c.isRunning())c.stop("Autonomous preparation");
            if(c!=null)c.clearCurrent();
            stage=Stage.PREPARE; running=true; sessionStartNanos=System.nanoTime(); timer.start();
            result.setText("Preparing autonomous takeover");
            log("Started autonomous preparation. Original PID " + originalPid + "; paired bias curve " + originalBias.size() + " points.");
            log("Snapshotted idleMode='" + excitationSnapshot.originalModeLabel + "' and all " + excitationSnapshot.tableCellCount() + " cltIdleCorrTable cells before any excitation write.");
            log("Start is the RAM-write authorization. Temporary outer-idle controls will always be restored; no Burn command exists.");
            refresh();
        } catch(Throwable ex){
            if(ex instanceof VirtualMachineError)throw (VirtualMachineError)ex;
            if(ex instanceof ThreadDeath)throw (ThreadDeath)ex;
            failStart("Autonomous DC-IAC tuning did not start",ex);
        }
    }

    private void resetSessionState() {
        biasWrites=0; binMoves=0; pidWrites=0; relocatedBiasKnots.clear(); lastPlacementDetail=""; retryUsed=false;
        activePidCandidate=null; pidBaselineSession=null; pidBaselineGains=null; lastBiasBaseline=null; lastAppliedGain=""; result.setText("—");
        prepareStableSince=0; openLoopActivatedNanos=0; plateauStableSince=0; targetMismatchSince=0; cycleCompletedNanos=0;
        cycleComplete=false; baseTarget=Double.NaN; stepSize=Double.NaN; targetOffset=Double.NaN;
        desiredTarget=Double.NaN; lastSettledTarget=Double.NaN; commandReferenceTarget=Double.NaN; sequenceIndex=0; sequenceCycles=0; fanRestarts=0; lastFanState=null;
        prepareStatus="Waiting for fresh live safety channels";
    }

    private void tick() {
        if(!running)return;
        try {
            if(excitationLive==null||!excitationLive.isStarted())throw new IllegalStateException("Autonomous live-channel subscription stopped unexpectedly.");
            ProfileLiveSample sample=excitationLive.snapshot();
            if(stage==Stage.PREPARE){prepareTick(sample);refresh();return;}

            DcIacTunerCoordinator c=coordinator();
            if(c==null)throw new IllegalStateException("DC-IAC coordinator is unavailable.");
            if(!c.isRunning())throw new IllegalStateException("DC-IAC capture stopped unexpectedly.");
            if(!c.configurationStillMatches())throw new IllegalStateException("DC-IAC PID/travel/bias changed outside the autonomous tuner during capture.");
            excitation.verifyActive(cfg);
            requireSafeLive(sample,true);
            enforceCommandStepLimit(sample);
            handleFanChange(sample,c);

            if(stage==Stage.BIAS_CAPTURE||stage==Stage.BIAS_RECHECK){
                maintainDesiredTarget(sample,baseTarget);
                evaluateBias(c);
            } else if(stage==Stage.PID_CAPTURE){
                drivePidSequence(c,sample);
                if(cycleComplete)evaluatePidBaseline(c);
            } else if(stage==Stage.PID_RETEST){
                drivePidSequence(c,sample);
                if(cycleComplete)evaluatePidRetest(c);
            }
            refresh();
        } catch(Throwable ex){
            if(ex instanceof VirtualMachineError)throw (VirtualMachineError)ex;
            if(ex instanceof ThreadDeath)throw (ThreadDeath)ex;
            safetyStop("Autonomous DC-IAC loop failed: " + message(ex));
        }
    }

    private void prepareTick(ProfileLiveSample sample) throws Exception {
        long now=System.nanoTime();
        String missingLive=missingRequiredLive(sample);
        if(!missingLive.isEmpty()){
            startCheckpoint="Wait for fresh live channels";
            prepareStatus="Waiting for fresh live channels: " + missingLive;
            if(now-sessionStartNanos>8_000_000_000L)throw new IllegalStateException("Required autonomous live channels did not become fresh within 8 seconds: " + missingLive + ".");
            return;
        }
        requireSafeLive(sample,false);
        double target=required(sample,"dcIdleTarget");
        double pv=required(sample,"idlePositionSensor");

        if(!excitation.isActive()){
            // A poor target/PV match is NOT a precondition for tuning. Requiring an already-good
            // position loop here made Autonomous wait forever on exactly the cars that need it.
            prepareStatus="Safety checks pass; stabilizing before Open Loop takeover (current target error " + one(target-pv) + "%)";
            if(prepareStableSince==0)prepareStableSince=now;
            if(now-prepareStableSince<PREPARE_STABLE_NANOS)return;
            double initialBase=required(sample,"baseIdlePosition");
            double closedLoopCorrection=optional(sample,"idleClosedLoop",0.0);
            double initialCommand=initialBase+closedLoopCorrection;
            if(initialCommand<excitationSnapshot.dcMinimumPosition||initialCommand>excitationSnapshot.dcMaximumPosition){
                // Do not use an invalid correction estimate; the stored open-loop base remains the conservative fallback.
                initialCommand=initialBase;
            }
            if(initialCommand<excitationSnapshot.dcMinimumPosition||initialCommand>excitationSnapshot.dcMaximumPosition)
                throw new IllegalStateException("Initial outer-idle command is outside configured DC-IAC travel; refusing autonomous takeover.");
            double command=DcIacAutonomousExcitationController.normalizeTableCommand(excitationSnapshot,initialCommand);
            commandReferenceTarget=target;
            startCheckpoint="Switch outer idle to Open Loop"; prepareStatus="Writing verified temporary open-loop table and idleMode";
            excitation.activate(cfg,excitationSnapshot,command);
            openLoopActivatedNanos=now; prepareStableSince=0; plateauStableSince=0; targetMismatchSince=0;
            log("Outer idle controller switched to Open Loop after full-table write/readback. Initial table command " + one(command)
                    + "% (base " + one(initialBase) + "%" + (fresh(sample,"idleClosedLoop") ? " + prior CL correction " + signed(closedLoopCorrection) + "%" : "") + ").");
            return;
        }

        startCheckpoint="Verify Open Loop takeover";
        excitation.verifyActive(cfg);
        enforceCommandStepLimit(sample);
        if(excitationLive.isSubscribed("isIdleClosedLoop") && optional(sample,"isIdleClosedLoop",1.0)>0.5){
            prepareStatus="idleMode is Open Loop; waiting for runtime closed-loop state to disengage";
            if(now-openLoopActivatedNanos>4_000_000_000L)throw new IllegalStateException("Outer idle closed-loop state did not disengage after idleMode=Open Loop.");
            return;
        }
        target=required(sample,"dcIdleTarget"); pv=required(sample,"idlePositionSensor");
        // Do not require the DC position loop to already track within 1.1%. Bias calibration is
        // precisely what should improve a large residual. Only require the commanded target itself
        // to remain stable briefly after takeover.
        prepareStatus="Open Loop active; accepting current target baseline despite " + one(target-pv) + "% position error";
        if(prepareStableSince==0)prepareStableSince=now;
        if(now-prepareStableSince<PREPARE_STABLE_NANOS)return;

        baseTarget=target;
        targetOffset=target-excitation.getActiveCommand();
        double available=Math.min(baseTarget-excitationSnapshot.dcMinimumPosition-0.5,
                excitationSnapshot.dcMaximumPosition-0.5-baseTarget);
        stepSize=Math.min(NOMINAL_STEP,Math.min(4.0,available));
        stepSize=Math.floor(stepSize*2.0)/2.0;
        if(stepSize<MIN_AUTONOMOUS_STEP)throw new IllegalStateException("Current target " + one(baseTarget)
                + "% is too close to configured travel limits for a safe bidirectional 3–4% autonomous PID test.");
        desiredTarget=baseTarget; lastSettledTarget=baseTarget; commandReferenceTarget=Double.NaN;

        DcIacTunerCoordinator c=coordinator(); if(c==null)throw new IllegalStateException("DC-IAC coordinator is unavailable.");
        c.clearCurrent(); c.start(cfg); stage=Stage.BIAS_CAPTURE; startCheckpoint="Autonomous bias capture running"; prepareStatus="Takeover complete";
        result.setText("Autonomous bias capture active");
        log("Autonomous takeover complete. Actual dcIdleTarget baseline " + one(baseTarget) + "%; measured position " + one(pv)
                + "%; initial residual " + signed(target-pv) + "%; target/table offset " + signed(targetOffset)
                + "%; planned PID step " + one(stepSize) + "% each direction.");
        log("Phase 1 is static bias/feed-forward. A perfect starting position is not required; the plugin will improve it from the captured evidence.");
    }

    private void evaluateBias(DcIacTunerCoordinator c) throws Exception {
        DcIacBiasKnotProposal knot=firstReadyKnot(c.knotProposals());
        if(knot!=null){
            if(biasWrites>=4){stopKeep("Maximum autonomous bias-write count reached; review the remaining evidence manually.");return;}
            lastBiasBaseline=currentBias;
            c.stopAndArchive();
            DcIacRamWriteCoordinator.WriteResult wr=writer.applyBiasKnot(cfg,currentBias,knot.getKnotIndex(),knot.getPosition(),knot.getProposedValue());
            currentBias=writer.readBias(cfg); biasWrites++; result.setText("Bias knot " + knot.getKnotIndex() + " verified");
            log("Bias candidate: knot " + knot.getKnotIndex() + " at " + one(knot.getPosition()) + "%: " + one(knot.getCurrentValue()) + " → " + one(knot.getProposedValue()) + ". " + wr.message);
            c.clearCurrent(); c.start(cfg); stage=Stage.BIAS_RECHECK; return;
        }
        DcIacStaticRecommendation staticRec=runningIdleStatic(c.staticRecommendations());
        boolean writableBiasClear=staticRec!=null && (staticRec.isBiasReadyForPid() || biasResolvedByWritableKnots(c.knotProposals()));
        if(writableBiasClear){
            DcIacBiasBinPlacementProposal placement=staticRec.isBiasReadyForPid()
                    ? placementEngine.evaluate(currentBias,c.staticRecommendations(),relocatedBiasKnots) : null;
            lastPlacementDetail=placement==null?"":placement.getDetail();
            if(placement!=null && placement.isReady()){
                if(binMoves>=2){stopKeep("Maximum autonomous bias-bin relocation count reached. Local bias is converged, but review the X-axis before PID tuning.");return;}
                lastBiasBaseline=currentBias;
                c.stopAndArchive();
                DcIacRamWriteCoordinator.WriteResult wr=writer.applyBiasPoint(cfg,currentBias,placement.getKnotIndex(),placement.getProposedBin(),placement.getProposedValue());
                currentBias=writer.readBias(cfg); binMoves++; relocatedBiasKnots.add(Integer.valueOf(placement.getKnotIndex()));
                result.setText("Bin " + placement.getKnotIndex() + " relocated + verified");
                log("Bias-bin placement: knot " + placement.getKnotIndex() + " " + one(placement.getCurrentBin()) + "% → " + one(placement.getProposedBin()) + "% using " + placement.getEvidenceWindows() + " converged windows at " + one(placement.getConfidencePercent()) + "% confidence. Local value carried as " + one(placement.getProposedValue()) + ". " + wr.message);
                c.clearCurrent(); c.start(cfg); stage=Stage.BIAS_RECHECK; return;
            }
            c.stopAndArchive();
            if(staticRec.isBiasReadyForPid()) log("Bias cleared by the static engine at controller resolution: " + staticRec.getDetail());
            else log("Bias cleared by writable-knot resolution: local mathematical correction remains, but every supported dcIdleBiasValues knot already rounds to its current whole-number value.");
            if(placement!=null) log("Bin-placement check: " + placement.getStatus() + " — " + placement.getDetail());
            c.clearCurrent(); c.start(cfg); stage=Stage.PID_CAPTURE; result.setText("Bias + bin placement clear — PID unlocked"); retryUsed=false;
            resetPidSequence();
            log("Starting automatic bidirectional PID excitation around " + one(baseTarget) + "%: +" + one(stepSize) + " / -" + one(stepSize) + "%.");
            return;
        }
        if(staticRec!=null && DcIacStaticRecommendation.INCONSISTENT.equals(staticRec.getStatus()) && stage==Stage.BIAS_RECHECK && lastBiasBaseline!=null){
            c.stop("Bias recheck inconsistent"); writer.restoreBias(cfg,lastBiasBaseline); currentBias=writer.readBias(cfg);
            throw new IllegalStateException("Bias recheck became inconsistent; the previous verified curve was restored. " + staticRec.getDetail());
        }
    }

    private void drivePidSequence(DcIacTunerCoordinator c, ProfileLiveSample sample) throws Exception {
        long now=System.nanoTime();
        if(cycleComplete){
            DcIacPidRecommendation rec=actionablePid(c.pidRecommendations());
            if(rec!=null)return;
            if(sequenceCycles<MAX_SEQUENCE_CYCLES && now-cycleCompletedNanos>=CYCLE_RESTART_NANOS){
                cycleComplete=false; sequenceIndex=1; plateauStableSince=0; targetMismatchSince=0;
                commandDesiredTarget(sample,targetForIndex(sequenceIndex),"additional evidence cycle " + (sequenceCycles+1));
                return;
            }
            if(sequenceCycles>=MAX_SEQUENCE_CYCLES && now-cycleCompletedNanos>=FINAL_DECISION_WAIT_NANOS){
                stopKeep("Two complete automatic 2-opening/2-closing PID cycles produced no actionable recommendation; stopping instead of adding more excitation.");
            }
            return;
        }

        desiredTarget=targetForIndex(sequenceIndex);
        maintainDesiredTarget(sample,desiredTarget);
        double actualTarget=required(sample,"dcIdleTarget");
        double pv=required(sample,"idlePositionSensor");
        if(Math.abs(actualTarget-desiredTarget)>TARGET_TOLERANCE || Math.abs(pv-actualTarget)>POSITION_TOLERANCE){
            plateauStableSince=0; return;
        }
        if(plateauStableSince==0)plateauStableSince=now;
        if(now-plateauStableSince<PLATEAU_STABLE_NANOS)return;

        if(finite(lastSettledTarget)){
            double actualStep=Math.abs(actualTarget-lastSettledTarget);
            if(actualStep>MAX_ACTUAL_STEP+1e-6)throw new IllegalStateException("Observed dcIdleTarget step " + one(actualStep)
                    + "% exceeded the 4.5% autonomous hard limit.");
        }
        lastSettledTarget=actualTarget; commandReferenceTarget=Double.NaN;
        plateauStableSince=0; targetMismatchSince=0;

        if(sequenceIndex<4){
            sequenceIndex++;
            commandDesiredTarget(sample,targetForIndex(sequenceIndex),"PID sequence step " + sequenceIndex + "/4");
            return;
        }
        sequenceCycles++;
        cycleComplete=true; cycleCompletedNanos=now;
        log("Automatic PID excitation cycle " + sequenceCycles + " complete: 2 opening + 2 closing target transitions, each <= " + one(MAX_ACTUAL_STEP) + "% actual.");
    }

    private void resetPidSequence() {
        sequenceIndex=0; sequenceCycles=0; cycleComplete=false; cycleCompletedNanos=0; plateauStableSince=0; targetMismatchSince=0;
        desiredTarget=baseTarget; lastSettledTarget=baseTarget;
    }

    private double targetForIndex(int index) {
        switch(index){case 1:return baseTarget+stepSize;case 2:return baseTarget;case 3:return baseTarget-stepSize;case 4:return baseTarget;default:return baseTarget;}
    }

    private void maintainDesiredTarget(ProfileLiveSample sample,double desired) throws Exception {
        double actual=required(sample,"dcIdleTarget");
        if(Math.abs(actual-desired)<=TARGET_TOLERANCE){targetMismatchSince=0;return;}
        long now=System.nanoTime();
        if(targetMismatchSince==0){targetMismatchSince=now;return;}
        if(now-targetMismatchSince<TARGET_CORRECTION_NANOS)return;
        commandDesiredTarget(sample,desired,"target-offset correction");
        targetMismatchSince=0; plateauStableSince=0;
    }

    private void commandDesiredTarget(ProfileLiveSample sample,double desired,String reason) throws Exception {
        if(desired<excitationSnapshot.dcMinimumPosition+0.25||desired>excitationSnapshot.dcMaximumPosition-0.25)
            throw new IllegalStateException("Desired autonomous target " + one(desired) + "% is outside configured DC-IAC travel margin.");
        double actual=required(sample,"dcIdleTarget");
        double command=excitation.getActiveCommand()+(desired-actual);
        commandReferenceTarget=actual;
        double written=excitation.command(cfg,command);
        desiredTarget=desired; plateauStableSince=0; targetMismatchSince=0;
        log("Commanded " + reason + ": desired dcIdleTarget " + one(desired) + "% via verified open-loop table command " + one(written) + "%.");
    }


    private void enforceCommandStepLimit(ProfileLiveSample sample) {
        if(!finite(commandReferenceTarget)||!fresh(sample,"dcIdleTarget"))return;
        double actual=sample.get("dcIdleTarget");
        double delta=Math.abs(actual-commandReferenceTarget);
        if(delta>MAX_ACTUAL_STEP+1e-6)throw new IllegalStateException("Observed dcIdleTarget changed " + one(delta)
                + "% from the pre-command target, exceeding the 4.5% autonomous hard limit.");
    }

    private void handleFanChange(ProfileLiveSample sample,DcIacTunerCoordinator c) throws Exception {
        if(!excitationLive.isSubscribed("fan1On")||!sample.has("fan1On"))return;
        double fan=sample.get("fan1On")>0.5?1.0:0.0;
        if(lastFanState==null){lastFanState=Double.valueOf(fan);return;}
        if(Math.abs(fan-lastFanState.doubleValue())<0.5)return;
        lastFanState=Double.valueOf(fan);
        if(stage==Stage.PID_CAPTURE||stage==Stage.PID_RETEST){
            fanRestarts++;
            if(fanRestarts>MAX_FAN_RESTARTS)throw new IllegalStateException("Fan state changed repeatedly during PID identification; stopping instead of mixing load conditions.");
            c.stop("Fan state changed during autonomous PID sequence"); c.clearCurrent();
            commandDesiredTarget(sample,baseTarget,"fan-load re-baseline"); c.start(cfg); resetPidSequence();
            log("Fan state changed during PID identification. Discarded the partial sequence and restarted at the same baseline after the load transition.");
        } else {
            log("Fan state changed during static bias capture. Existing FAN1 context classification remains authoritative; holding the same target while evidence reacquires.");
        }
    }

    private void evaluatePidBaseline(DcIacTunerCoordinator c) throws Exception {
        DcIacPidRecommendation rec=actionablePid(c.pidRecommendations());
        if(rec==null)return;
        if(DcIacPidRecommendation.NO_CHANGE.equals(rec.getStatus())){c.stopAndArchive();stopKeep("Position PID evidence indicates NO_CHANGE; best verified DC-IAC RAM settings retained.");return;}
        if(!pidActionAllowed(rec))return;
        if(pidWrites>=6){stopKeep("Maximum autonomous PID-write count reached; best verified DC-IAC RAM settings retained.");return;}
        c.stopAndArchive(); pidBaselineSession=lastSession(c.sessions()); pidBaselineGains=currentPid;
        applyPidRecommendation(c,rec,false);
    }

    private void evaluatePidRetest(DcIacTunerCoordinator c) throws Exception {
        DcIacPidRecommendation rec=actionablePid(c.pidRecommendations());
        if(rec==null)return;
        c.stopAndArchive(); DcIacTuningSessionSummary candidate=lastSession(c.sessions());
        if(pidBaselineSession==null||candidate==null)throw new IllegalStateException("Candidate/baseline session summary unavailable.");
        DcIacIterationComparison cmp=comparisonEngine.compare(pidBaselineSession,candidate);
        result.setText(cmp.getStatus()); log("PID candidate comparison: " + cmp.getStatus() + " — " + cmp.getDetail());
        if(DcIacIterationComparison.IMPROVED.equals(cmp.getStatus())){
            log("Candidate promoted as the verified working PID baseline: " + currentPid + "."); retryUsed=false; activePidCandidate=null;
            // A later safety stop must preserve the newly promoted best candidate, not roll back
            // to the older baseline that preceded this successful comparison.
            pidBaselineGains=currentPid; pidBaselineSession=candidate;
            c.clearCurrent();c.start(cfg);stage=Stage.PID_CAPTURE;resetPidSequence();return;
        }
        if(DcIacIterationComparison.REGRESSED.equals(cmp.getStatus())){
            writer.restorePid(cfg,pidBaselineGains); currentPid=writer.readPid(cfg); log("Regression rollback verified: " + currentPid + ".");
            if(!retryUsed && activePidCandidate!=null && pidWrites<6){
                DcIacRamWriteCoordinator.PidSnapshot half=halfStep(pidBaselineGains,proposal(activePidCandidate));
                if(changedGain(pidBaselineGains,half)!=null){
                    retryUsed=true; c.clearCurrent();
                    DcIacRamWriteCoordinator.WriteResult wr=writer.applyPidCandidate(cfg,pidBaselineGains,half); currentPid=writer.readPid(cfg); pidWrites++;
                    log("Trying one half-step after regression: " + currentPid + ". " + wr.message); c.start(cfg); stage=Stage.PID_RETEST;resetPidSequence(); return;
                }
            }
            stopKeep("Regressed PID candidate rejected; previous verified PID baseline restored.");return;
        }
        if(DcIacIterationComparison.SIMILAR.equals(cmp.getStatus())){
            writer.restorePid(cfg,pidBaselineGains);currentPid=writer.readPid(cfg);stopKeep("Candidate was similar rather than measurably better; previous verified PID baseline restored.");return;
        }
        writer.restorePid(cfg,pidBaselineGains);currentPid=writer.readPid(cfg);
        stopKeep("PID comparison was " + cmp.getStatus() + "; previous verified baseline restored instead of guessing.");
    }

    private void applyPidRecommendation(DcIacTunerCoordinator c,DcIacPidRecommendation rec,boolean retry) throws Exception {
        DcIacRamWriteCoordinator.PidSnapshot proposed=proposal(rec); String gain=changedGain(currentPid,proposed);
        if(gain==null)throw new IllegalStateException("Recommendation did not contain exactly one changed gain.");
        if("D".equals(gain)&&proposed.d>currentPid.d)throw new IllegalStateException("Automatic D increase is prohibited.");
        activePidCandidate=rec; lastAppliedGain=gain;
        DcIacRamWriteCoordinator.WriteResult wr=writer.applyPidCandidate(cfg,currentPid,proposed); currentPid=writer.readPid(cfg); pidWrites++; retryUsed=retry;
        log("PID candidate " + gain + " applied from existing engine: " + currentPid + ". " + wr.message);
        c.clearCurrent();c.start(cfg);stage=Stage.PID_RETEST;result.setText("Testing " + gain + " candidate");resetPidSequence();
    }

    private boolean pidActionAllowed(DcIacPidRecommendation r){
        if(r.isReady())return true;
        if(!DcIacPidRecommendation.SOURCE_LIMITED.equals(r.getStatus()))return false;
        DcIacRamWriteCoordinator.PidSnapshot p=proposal(r);String g=changedGain(currentPid,p);if(g==null)return false;
        if("P".equals(g))return p.p<currentPid.p; if("I".equals(g))return p.i<currentPid.i; if("D".equals(g))return p.d<currentPid.d; return false;
    }

    private boolean requiredLiveReady(ProfileLiveSample sample){
        return missingRequiredLive(sample).isEmpty();
    }

    private String missingRequiredLive(ProfileLiveSample sample){
        String[] names={"dcIdleTarget","idlePositionSensor","dcIdleFaultCode","RPMValue","TPSValue","baseIdlePosition","currentIdlePosition"};
        StringBuilder b=new StringBuilder();
        for(String name:names){
            if(fresh(sample,name))continue;
            if(b.length()>0)b.append(", ");
            b.append(name);
            if(sample==null||!sample.has(name))b.append(" missing");
            else b.append(" stale/invalid");
        }
        return b.toString();
    }

    private void requireSafeLive(ProfileLiveSample sample,boolean checkHandoff) {
        if(!requiredLiveReady(sample))throw new IllegalStateException("Required autonomous live channel became missing or stale.");
        double fault=required(sample,"dcIdleFaultCode"); if(Math.abs(fault)>0.1)throw new IllegalStateException("DC-IAC fault code became active: " + one(fault) + ".");
        double rpm=required(sample,"RPMValue"); if(rpm<MIN_RUNNING_RPM)throw new IllegalStateException("RPM fell below the " + one(MIN_RUNNING_RPM) + " RPM stall guard.");
        if(rpm>MAX_TEST_RPM)throw new IllegalStateException("RPM exceeded the " + one(MAX_TEST_RPM) + " RPM autonomous idle-test guard.");
        double tps=required(sample,"TPSValue"); if(tps>MAX_TPS)throw new IllegalStateException("TPS rose above " + one(MAX_TPS) + "% during autonomous actuator control.");
        if(excitationLive!=null&&excitationLive.isSubscribed("vehicleSpeedKph")&&fresh(sample,"vehicleSpeedKph")){
            double vss=Math.abs(sample.get("vehicleSpeedKph")); if(vss>MAX_VSS_KPH)throw new IllegalStateException("Vehicle speed exceeded the stationary guard (" + one(vss) + " km/h).");
        } else if(!manualStationary.isSelected())throw new IllegalStateException("VSS is unavailable and stationary fallback is not confirmed.");
        double target=required(sample,"dcIdleTarget"); double pv=required(sample,"idlePositionSensor");
        if(excitationSnapshot!=null){
            if(target<excitationSnapshot.dcMinimumPosition-0.5||target>excitationSnapshot.dcMaximumPosition+0.5)throw new IllegalStateException("dcIdleTarget left configured travel.");
            if(pv<excitationSnapshot.dcMinimumPosition-1.0||pv>excitationSnapshot.dcMaximumPosition+1.0)throw new IllegalStateException("Measured idle position left configured travel.");
        }
        if(checkHandoff){double handoff=required(sample,"currentIdlePosition");if(Math.abs(handoff-target)>HANDOFF_TOLERANCE)throw new IllegalStateException("Outer-target handoff diverged: currentIdlePosition vs dcIdleTarget = " + one(handoff-target) + "%.");}
    }

    private static boolean fresh(ProfileLiveSample sample,String name){
        if(sample==null||!sample.has(name))return false;double v=sample.get(name);return finite(v)&&sample.getAgeSeconds(name)<=1.0;
    }
    private static double required(ProfileLiveSample sample,String name){if(!fresh(sample,name))throw new IllegalStateException(name+" is missing/stale.");return sample.get(name);}
    private static double optional(ProfileLiveSample sample,String name,double fallback){return fresh(sample,name)?sample.get(name):fallback;}

    private void stopKeep(String why){
        timer.stop();
        DcIacTunerCoordinator c=coordinator();if(c!=null&&c.isRunning())c.stop("Autonomous stop");
        running=false;
        try{restoreExcitationContext();stage=Stage.COMPLETE;log(why + " Temporary idleMode/open-loop table restored. Best verified DC-IAC settings remain RAM-only. No Burn command was sent.");}
        catch(Exception ex){stage=Stage.ERROR;log(why + " DC-IAC settings were kept, but temporary outer-idle restore FAILED: " + message(ex));}
        stopExcitationLive();refresh();
    }

    private void restoreOriginal(){
        if(originalPid==null||originalBias==null){log("No complete original DC-IAC snapshot is available.");return;}
        try{
            timer.stop();DcIacTunerCoordinator c=coordinator();if(c!=null&&c.isRunning())c.stop("Restore original");
            writer.restorePid(cfg,originalPid);writer.restoreBias(cfg,originalBias);currentPid=writer.readPid(cfg);currentBias=writer.readBias(cfg);
            restoreExcitationContext();stopExcitationLive();running=false;stage=Stage.RESTORED;result.setText("Original verified");
            log("Original DC-IAC PID plus dcIdleBiasBins/dcIdleBiasValues restored and read back; temporary outer-idle test controls restored. No Burn command was sent.");refresh();
        }catch(Exception ex){running=false;stopExcitationLive();stage=Stage.ERROR;log("Original restore failed: " + message(ex));refresh();}
    }

    private void safetyStop(String why){
        String visible=(why==null||why.trim().isEmpty()?"Autonomous DC-IAC safety stop":why.trim());
        lastFailure=visible + " [checkpoint: " + startCheckpoint + "]";
        prepareStatus="Stopped at " + startCheckpoint;
        result.setText("STOPPED — " + startCheckpoint);
        try{DcIacTunerCoordinator c=coordinator();if(c!=null&&c.isRunning())c.stop("Autonomous safety stop");
            if(writer!=null&&cfg!=null){if(pidBaselineGains!=null)writer.restorePid(cfg,pidBaselineGains);if(lastBiasBaseline!=null&&(stage==Stage.BIAS_RECHECK))writer.restoreBias(cfg,lastBiasBaseline);}}
        catch(Exception rollback){log("DC-IAC safety rollback also failed: " + message(rollback));}
        try{restoreExcitationContext();}catch(Exception restore){log("Temporary outer-idle safety restore also failed: " + message(restore));}
        timer.stop();stopExcitationLive();running=false;stage=Stage.ERROR;log(lastFailure);refresh();
    }

    private void restoreExcitationContext() throws Exception {
        if(excitation!=null&&excitation.isActive()){
            excitation.restore(cfg);
            log("Restored complete cltIdleCorrTable and idleMode='" + excitationSnapshot.originalModeLabel + "' with readback verification.");
        }
    }

    private void stopExcitationLive(){if(excitationLive!=null){excitationLive.stop();excitationLive=null;}excitationReport=null;}

    private void failStart(String prefix,Throwable ex){
        String detail=prefix+" at ["+startCheckpoint+"]: "+ex.getClass().getSimpleName()+": "+message(ex);
        try{restoreExcitationContext();}catch(Exception restore){log("Start-failure restore also failed: "+message(restore));detail += " | restore: "+message(restore);}
        stopExcitationLive();timer.stop();running=false;stage=Stage.ERROR;lastFailure=detail;prepareStatus="Failed at "+startCheckpoint;result.setText("START FAILED — "+startCheckpoint);log(detail);refresh();
    }

    /** Called by the workspace before it releases/replaces ControllerAccess. */
    void controllerDisconnecting(){
        if(running||excitation!=null&&excitation.isActive())safetyStop("Controller connection is closing; autonomous DC-IAC tuning stopped and restore was attempted before disconnect.");
        else stopExcitationLive();
    }

    boolean isRunning(){return running;}
    void showBiasIntent(){if(!running)setAreaText(instruction,"AUTONOMOUS BIAS + BIN PLACEMENT\nPress Start, then keep hands and feet off the controls. The plugin will snapshot the outer idle controls, enter Open Loop, hold a safe local DC-IAC target itself, converge the existing bias-value engine, optionally relocate an eligible interior bias bin, and verify every RAM write.");}
    void showPidIntent(){if(!running)setAreaText(instruction,"AUTONOMOUS POSITION PID\nBias/feed-forward clears first. The plugin then generates the same local 2-opening + 2-closing 3–4% target sequence for baseline and candidate tests, while the existing PID recommendation/comparison engines decide what may be written or rolled back.");}

    void applyTheme(){
        setBackground(UiTheme.bg()); setForeground(UiTheme.text());
        for(java.awt.Component x:getComponents()) themeTree(x);
        repaint();
    }
    private void themeTree(java.awt.Component x){if(x==null)return;x.setForeground(UiTheme.text());if(x instanceof JPanel)x.setBackground(UiTheme.card());else if(x instanceof JTextArea){x.setBackground(UiTheme.card());x.setForeground(UiTheme.text());}else if(x instanceof JScrollPane){x.setBackground(UiTheme.bg());((JScrollPane)x).getViewport().setBackground(UiTheme.card());}else if(x instanceof JButton){x.setBackground(UiTheme.button());x.setForeground(UiTheme.text());}else if(x instanceof JCheckBox){x.setBackground(UiTheme.bg());x.setForeground(UiTheme.text());}if(x instanceof java.awt.Container)for(java.awt.Component c:((java.awt.Container)x).getComponents())themeTree(c);}

    private static void setAreaText(JTextArea area,String value){String next=value==null?"":value;if(!next.equals(area.getText()))area.setText(next);}
    private static void setLabelText(JLabel label,String value){String next=value==null?"":value;if(!next.equals(label.getText()))label.setText(next);}

    private void refresh(){
        setLabelText(phase,stage.label);setLabelText(configuration,cfg==null||cfg.isEmpty()?"—":cfg);setLabelText(pid,currentPid==null?"—":currentPid.toString());
        setLabelText(bias,currentBias==null?"—":currentBias.size()+" knots · bins + values verified");setLabelText(writeCount,biasWrites+" value / "+binMoves+" bin / "+pidWrites+" PID");
        if(running){
            StringBuilder b=new StringBuilder();
            if(excitationLive!=null&&excitationLive.isStarted()){
                ProfileLiveSample s=excitationLive.snapshot();
                if(s.has("dcIdleTarget"))b.append("Autonomous target: ").append(one(s.get("dcIdleTarget"))).append("%  desired ").append(one(desiredTarget)).append("%\n");
                if(excitation!=null&&excitation.isActive())b.append("Open-loop table command: ").append(one(excitation.getActiveCommand())).append("%\n");
                if(finite(stepSize))b.append("PID test span: ±").append(one(stepSize)).append("%  cycle ").append(sequenceCycles).append("/").append(MAX_SEQUENCE_CYCLES).append("  transition ").append(Math.min(sequenceIndex,4)).append("/4\n");
                b.append("Stationary basis: ").append(excitationLive.isSubscribed("vehicleSpeedKph")?"live VSS":"manual confirmation").append("\n");
                if(stage==Stage.PREPARE)b.append("Prepare: ").append(prepareStatus).append("\nCheckpoint: ").append(startCheckpoint).append("\n");
                b.append("\n");
            }
            DcIacTunerCoordinator c=coordinator();if(c!=null&&c.isRunning()){
                b.append("State: ").append(c.status()).append('\n').append(c.readiness()).append("\n\n");
                DcIacStaticRecommendation sr=runningIdleStatic(c.staticRecommendations());if(sr!=null)b.append("Static: ").append(sr.getStatus()).append(" — ").append(sr.getDetail()).append("\n");
                DcIacBiasKnotProposal kp=firstReadyKnot(c.knotProposals());if(kp!=null)b.append("Bias value: #").append(kp.getKnotIndex()).append(" @ ").append(one(kp.getPosition())).append("%  ").append(one(kp.getCurrentValue())).append(" → ").append(one(kp.getProposedValue())).append("\n");
                if(sr!=null&&sr.isBiasReadyForPid()&&currentBias!=null){DcIacBiasBinPlacementProposal bp=placementEngine.evaluate(currentBias,c.staticRecommendations(),relocatedBiasKnots);if(bp!=null)b.append("Bin placement: ").append(bp.getStatus()).append(" — ").append(bp.getDetail()).append("\n");}
                DcIacPidRecommendation pr=actionablePid(c.pidRecommendations());if(pr!=null)b.append("PID: ").append(pr.getStatus()).append(" — ").append(pr.getPrimaryAction()).append("\n");
            }
            setAreaText(evidence,b.toString());
        } else if(stage==Stage.ERROR)setAreaText(evidence,lastFailure.isEmpty()?"Autonomous stopped after an error; review history for details.":lastFailure);
        else if(stage==Stage.STOPPED)setAreaText(evidence,"Press Start Autonomous directly. The panel will attach to the current TunerStudio controller/configuration if available and report any missing prerequisite explicitly; no Guided-page visit or separate write-arm checkbox is required.");

        switch(stage){
            case PREPARE:setAreaText(instruction,"KEEP HANDS AND FEET OFF THE CONTROLS — PREPARING\nThe plugin is verifying stationary/closed-throttle/fault/travel guards, snapshotting the complete open-loop table, then switching the main idle controller to Open Loop with exact readback before it owns the DC-IAC target.");break;
            case BIAS_CAPTURE:setAreaText(instruction,"AUTONOMOUS BIAS HOLD — NO USER TARGET INPUT\nThe plugin is holding one stable local DC-IAC target itself. Existing READY_KNOT / NO_CHANGE evidence gates remain authoritative for bias value and eligible interior-bin placement.");break;
            case BIAS_RECHECK:setAreaText(instruction,"AUTONOMOUS BIAS RECHECK — KEEP CONTROLS UNTOUCHED\nA paired bias-curve change was written and read back. The plugin is holding the same actual dcIdleTarget while the existing static engine proves the operating point again.");break;
            case PID_CAPTURE:setAreaText(instruction,"AUTONOMOUS PID BASELINE — 2 OPENING + 2 CLOSING STEPS\nThe plugin is commanding a repeatable local ±"+one(stepSize)+"% sequence and checking the actual dcIdleTarget step against the 4.5% hard limit. Do not move the throttle or vehicle.");break;
            case PID_RETEST:setAreaText(instruction,"AUTONOMOUS PID CANDIDATE RETEST\nA one-gain RAM candidate is active and verified. The plugin is replaying the same local target sequence for a directly comparable keep/rollback decision.");break;
            case COMPLETE:setAreaText(instruction,"AUTONOMOUS PASS COMPLETE\nBest verified DC-IAC bias/PID settings remain in ECU RAM only. The temporary Open Loop test table and original idleMode have been restored. Nothing was burned.");break;
            case RESTORED:setAreaText(instruction,"ORIGINAL SETTINGS RESTORED\nOriginal DC-IAC PID/bias settings and the temporary outer-idle test context were written back and verified. Nothing was burned.");break;
            case ERROR:setAreaText(instruction,"AUTONOMOUS TUNING STOPPED\n"+(lastFailure.isEmpty()?"Review history and verify ECU settings before resuming.":lastFailure)+"\nAny available rollback/outer-idle restore was attempted immediately.");break;
            default:if(instruction.getText().trim().isEmpty())showBiasIntent();
        }
        updateControls();repaint();
    }

    private void updateControls(){
        // Start is deliberately available whenever no autonomous session is running.
        // Connection/configuration readiness is checked on click and reported in-panel.
        // Do not hide lifecycle/readiness failures behind a permanently grey button.
        start.setEnabled(!running);
        stopKeep.setEnabled(running);
        restoreOriginal.setEnabled(!running&&writer!=null&&originalPid!=null&&originalBias!=null);
        manualStationary.setEnabled(!running);
    }

    private ControllerAccess resolveControllerAccess(){
        ControllerAccess access=guided.workflowControllerAccess();
        if(access!=null)return access;
        try{return ControllerAccess.getInstance();}
        catch(Throwable ignored){return null;}
    }

    private DcIacTunerCoordinator coordinator(){return guided.workflowCoordinator();}
    private static boolean biasResolvedByWritableKnots(List<DcIacBiasKnotProposal> list){
        boolean supported=false;
        if(list!=null)for(DcIacBiasKnotProposal r:list){
            if(r==null||r.getSupportingRegions()<=0)continue;
            supported=true;
            if(r.isReady()||DcIacBiasKnotProposal.INCONSISTENT.equals(r.getStatus()))return false;
        }
        return supported;
    }
    private static DcIacBiasKnotProposal firstReadyKnot(List<DcIacBiasKnotProposal> list){if(list!=null)for(DcIacBiasKnotProposal r:list)if(r!=null&&r.isReady())return r;return null;}
    private static DcIacStaticRecommendation runningIdleStatic(List<DcIacStaticRecommendation> list){DcIacStaticRecommendation best=null;if(list!=null)for(DcIacStaticRecommendation r:list){if(r==null||r.getOperatingContext()!=DcIacOperatingContext.RUNNING_IDLE)continue;if(best==null||r.getEvidenceWindows()>best.getEvidenceWindows())best=r;}return best;}
    private static DcIacPidRecommendation actionablePid(List<DcIacPidRecommendation> list){if(list==null)return null;DcIacPidRecommendation fallback=null;for(DcIacPidRecommendation r:list){if(r==null||r.getOperatingContext()!=DcIacOperatingContext.RUNNING_IDLE)continue;if(r.isReady())return r;if(DcIacPidRecommendation.NO_CHANGE.equals(r.getStatus()))fallback=r;else if(fallback==null&&DcIacPidRecommendation.SOURCE_LIMITED.equals(r.getStatus()))fallback=r;}return fallback;}
    private static DcIacTuningSessionSummary lastSession(List<DcIacTuningSessionSummary> l){return l==null||l.isEmpty()?null:l.get(l.size()-1);}
    private static DcIacRamWriteCoordinator.PidSnapshot proposal(DcIacPidRecommendation r){return new DcIacRamWriteCoordinator.PidSnapshot(r.getProposedP(),r.getProposedI(),r.getProposedD());}
    private static DcIacRamWriteCoordinator.PidSnapshot halfStep(DcIacRamWriteCoordinator.PidSnapshot a,DcIacRamWriteCoordinator.PidSnapshot b){return new DcIacRamWriteCoordinator.PidSnapshot((a.p+b.p)/2.0,(a.i+b.i)/2.0,(a.d+b.d)/2.0);}
    private static String changedGain(DcIacRamWriteCoordinator.PidSnapshot a,DcIacRamWriteCoordinator.PidSnapshot b){if(a==null||b==null)return null;int n=0;String g=null;if(Math.abs(a.p-b.p)>1e-8){n++;g="P";}if(Math.abs(a.i-b.i)>1e-8){n++;g="I";}if(Math.abs(a.d-b.d)>1e-8){n++;g="D";}return n==1?g:null;}
    private static JTextArea area(){JTextArea a=new JTextArea();a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);a.setFont(new Font("Dialog",Font.PLAIN,11));a.setBorder(BorderFactory.createEmptyBorder(6,7,6,7));return a;}
    private void log(String s){boolean follow=StableScrollSupport.isAtBottom(historyScroll,12);int keep=StableScrollSupport.verticalPosition(historyScroll);String t=new SimpleDateFormat("HH:mm:ss").format(new Date());if(history.getText().length()>0)history.append("\n");history.append(t+"  "+s);if(follow)StableScrollSupport.scrollToBottomLater(historyScroll,keep);else StableScrollSupport.setVerticalPosition(historyScroll,keep);}
    private static String join(List<String> values){if(values==null||values.isEmpty())return "none";StringBuilder b=new StringBuilder();for(int i=0;i<values.size();i++){if(i>0)b.append(", ");b.append(values.get(i));}return b.toString();}
    private static String signed(double v){return (v>=0?"+":"")+one(v);}
    private static String one(double v){return finite(v)?String.format(java.util.Locale.ROOT,"%.2f",v):"—";}
    private static boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
    private static String message(Throwable t){String m=t==null?null:t.getMessage();return m==null||m.trim().isEmpty()?(t==null?"Unknown error":t.getClass().getSimpleName()):m;}
}
