package se.anders.tunerstudio.pidautotune.ui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Font;

/** Explicit operator instructions for the integrated DC-IAC tuning workflow. */
public final class DcIacStartHerePanel extends JPanel {
    public DcIacStartHerePanel() {
        super(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("DC IAC - START HERE");
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 5));
        add(title, BorderLayout.NORTH);

        JTextArea text = new JTextArea();
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        text.setText(
            "FOLLOW THESE PHASES IN ORDER. DO NOT MIX THEM IN ONE DECISION.\n\n" +
            "PHASE 1 - STATIC BIAS / FEED-FORWARD\n" +
            "1. Engine running at a stable idle condition.\n" +
            "2. Open DC IAC - Tuner and start integrated capture.\n" +
            "3. DO NOT make PID test steps yet. Keep dcIdleTarget steady.\n" +
            "4. Choose ONE existing bias-table knot shown in the Bias knots tab. Aim dcIdleTarget within +/-0.75 position-% of that knot.\n" +
            "5. Hold the same target long enough to obtain repeated 5-second non-overlapping equilibrium windows. In practice allow roughly 45-60 seconds if I term is still moving.\n" +
            "6. Watch Diagnostics. 'I-law ... KEEP HOLDING' means the position error is still predictably integrating into I even if the valve looks mechanically quiet.\n" +
            "7. Read the Static bias and Bias knots tabs together.\n\n" +
            "STATIC STATUS MEANING\n" +
            "COLLECT_MORE     = keep holding the same target. Do not step yet.\n" +
            "NOT_CONVERGED    = valid windows exist, but inferred bias is still drifting. Keep holding.\n" +
            "INCONSISTENT     = repeat the same hold cleanly; do not change a setting from this evidence.\n" +
            "READY_LOCAL_BIAS = a local bias mismatch is proven, BUT THIS IS NOT AN EDITABLE TABLE-KNOT COMMAND. Never copy the local capped correction directly into a bias cell.\n" +
            "READY_KNOT       = an actual existing dcIdleBiasValues knot has enough local support for a bounded manual change.\n" +
            "NO_CHANGE        = this local static-bias region is cleared and may be used for PID work.\n\n" +
            "IMPORTANT: If Static bias says READY_LOCAL_BIAS but every Bias knot row still says COLLECT_MORE, STOP + ARCHIVE and export the run. Then start a clean capture closer to the intended existing knot. Do not apply the local +/-6 preview to any table cell.\n\n" +
            "WHEN READY_KNOT APPEARS\n" +
            "1. Stop + archive.\n" +
            "2. Export CSV and save the matching TunerStudio .mlg.\n" +
            "3. Manually apply ONLY the one supported knot change.\n" +
            "4. Start a NEW clean capture at the same local region.\n" +
            "5. Repeat until Static bias returns NO_CHANGE.\n\n" +
            "PHASE 2 - PID IDENTIFICATION (ONLY AFTER LOCAL STATIC STATUS = NO_CHANGE)\n" +
            "1. Keep the test around the same cleared operating region.\n" +
            "2. Make an ACTUAL dcIdleTarget change of 3-4 position-%.\n" +
            "3. NEVER exceed 4.5%. A 6% step is intentionally rejected as too large for local identification.\n" +
            "4. Let the valve settle between steps, normally at least 3-5 seconds.\n" +
            "5. Collect at least 2 opening and 2 closing steps around the same center.\n" +
            "6. Watch initial I (|I0|). Keep comparable steps near the same initial-I state; inherited I strongly changes apparent overshoot.\n" +
            "7. If fan1 changes state during a step, that event is tagged FAN1_IDLE_ADDER_STEP and is CONTEXT ONLY. Repeat the PID step after fan state is stable.\n" +
            "8. A PV_OUTSIDE_TRAVEL event is CONTEXT ONLY and cannot drive a gain recommendation.\n" +
            "9. Source-limited events may support a gain REDUCTION when harmful behavior was actually observed, but can never justify a gain increase.\n\n" +
            "WHEN A PID CANDIDATE APPEARS\n" +
            "1. Stop + archive and export the session.\n" +
            "2. Change only the ONE proposed gain manually.\n" +
            "3. Start a NEW clean capture and repeat the same local test.\n" +
            "4. Use History only to compare comparable before/after sessions.\n\n" +
            "SAFETY / ATTRIBUTION\n" +
            "- The plugin is read-only: no ECU writes, burns or actuator commands.\n" +
            "- Target handoff compares currentIdlePosition with dcIdleTarget; a stable mismatch is an upstream/configuration issue, not a valve PID tracking error.\n" +
            "- Changing P/I/D, travel, fan-adder configuration or the bias table during capture invalidates attribution; the tuner stops when it detects this.\n" +
            "- Engine-off evidence is not pooled with running-idle evidence.\n" +
            "- LTIT is not required.\n"
        );
        text.setCaretPosition(0);
        add(new JScrollPane(text), BorderLayout.CENTER);
    }
}
