package se.anders.tunerstudio.pidautotune;

import com.efiAnalytics.plugin.ApplicationPlugin;
import com.efiAnalytics.plugin.ecu.ControllerAccess;
import se.anders.tunerstudio.pidautotune.ui.PidAutotuneWorkspacePanel;
import javax.swing.JComponent;

/** TunerStudio entry point for the EPICEFI PID Tuner project. */
public final class PidAutotunePlugin implements ApplicationPlugin {
    public static final String VERSION = "0.5.24";
    private final PidAutotuneWorkspacePanel panel = new PidAutotuneWorkspacePanel();
    private String controllerSignature = "Not yet reported by TunerStudio";

    public String getIdName() { return "epicefiPidAutotune"; }
    public int getPluginType() { return 2; }
    public String getDisplayName() { return "PID Autotune (EPICEFI)"; }
    public String getDescription() {
        return "EPICEFI PID tuning workspace with task-based Guided and audited RAM-only Autonomous workflows for DC-IAC position control and Idle Closed Loop. Includes live evidence capture, recommendation review, iteration history, comparison and rollback support. Autonomous operation uses bounded temporary RAM changes with readback verification and restore handling. No automatic Burn.";
    }
    public void initialize(ControllerAccess controllerAccess) { panel.connect(controllerAccess, controllerSignature); }
    public boolean displayPlugin(String controllerSignature) {
        if (controllerSignature != null && !controllerSignature.trim().isEmpty()) {
            this.controllerSignature = controllerSignature.trim();
            panel.setControllerSignature(this.controllerSignature);
        }
        return true;
    }
    public boolean isMenuEnabled() { return true; }
    public String getAuthor() { return "Anders Wedin"; }
    public JComponent getPluginPanel() { return panel; }
    public void close() { panel.disconnect(); }
    public String getHelpUrl() { return ""; }
    public String getVersion() { return VERSION; }
    public double getRequiredPluginSpec() { return 1.0; }
}
