package se.anders.tunerstudio.pidautotune.ui;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;

/** Keeps Guided and Autonomous DC-IAC modes in one DC-IAC destination. */
final class DcIacTuneModePanel extends JPanel {
    private enum Destination { BIAS, PID, VALIDATION, OTHER }

    private final DcIacTunerPanel guided;
    private final AutonomousDcIacTuningPanel autonomous;
    private final CardLayout cards=new CardLayout();
    private final JPanel body=new JPanel(cards);
    private final JPanel bar=new JPanel(new FlowLayout(FlowLayout.LEFT,5,0));
    private final JToggleButton guidedButton=new JToggleButton("Guided Workflow");
    private final JToggleButton autonomousButton=new JToggleButton("Autonomous RAM");
    private Destination destination=Destination.BIAS;
    private boolean autonomousSelected;

    DcIacTuneModePanel(DcIacTunerPanel guided,AutonomousDcIacTuningPanel autonomous){
        super(new BorderLayout(0,6));
        this.guided=guided; this.autonomous=autonomous;
        ButtonGroup g=new ButtonGroup(); g.add(guidedButton); g.add(autonomousButton);
        guidedButton.setSelected(true); autonomousSelected=false;
        bar.add(guidedButton); bar.add(autonomousButton); add(bar,BorderLayout.NORTH);
        body.add(guided,"GUIDED"); body.add(autonomous,"AUTO"); add(body,BorderLayout.CENTER);
        guidedButton.addActionListener(e->selectGuidedMode());
        autonomousButton.addActionListener(e->selectAutonomousMode());
        applyTheme();
    }

    private void selectGuidedMode(){
        if(autonomous.isRunning()){
            autonomousSelected=true; autonomousButton.setSelected(true); cards.show(body,"AUTO");
        }else{
            autonomousSelected=false; guidedButton.setSelected(true); cards.show(body,"GUIDED"); showCurrentGuidedDestination();
        }
        applyTheme(); revalidate(); repaint();
    }

    private void selectAutonomousMode(){
        autonomousSelected=true; autonomousButton.setSelected(true); cards.show(body,"AUTO");
        if(destination==Destination.PID) autonomous.showPidIntent(); else autonomous.showBiasIntent();
        applyTheme(); revalidate(); repaint();
    }

    private void showCurrentGuidedDestination(){
        if(destination==Destination.PID) guided.selectPidTask();
        else if(destination==Destination.VALIDATION) guided.selectValidationTask();
        else if(destination==Destination.BIAS) guided.selectBiasTask();
    }

    void showGuidedTask(String task){
        destination=Destination.OTHER;
        if(!autonomous.isRunning()){
            autonomousSelected=false; guidedButton.setSelected(true); cards.show(body,"GUIDED"); guided.selectTask(task);
        }else{
            autonomousSelected=true; autonomousButton.setSelected(true); cards.show(body,"AUTO");
        }
        applyTheme();
    }

    void selectBiasDestination(){
        destination=Destination.BIAS;
        if(autonomousSelected) showAutonomousBias(); else {guidedButton.setSelected(true);cards.show(body,"GUIDED");guided.selectBiasTask();}
        applyTheme();
    }

    void selectPidDestination(){
        destination=Destination.PID;
        if(autonomousSelected) showAutonomousPid(); else {guidedButton.setSelected(true);cards.show(body,"GUIDED");guided.selectPidTask();}
        applyTheme();
    }

    void selectValidationDestination(){
        destination=Destination.VALIDATION;
        // Validation is intentionally read-only/manual. If Autonomous is actively running,
        // keep its owned actuator context visible; otherwise move to Guided validation.
        if(autonomous.isRunning()){
            autonomousSelected=true; autonomousButton.setSelected(true); cards.show(body,"AUTO");
        }else{
            autonomousSelected=false; guidedButton.setSelected(true); cards.show(body,"GUIDED"); guided.selectValidationTask();
        }
        applyTheme();
    }

    void showAutonomousBias(){autonomousSelected=true;destination=Destination.BIAS;autonomousButton.setSelected(true);cards.show(body,"AUTO");autonomous.showBiasIntent();applyTheme();}
    void showAutonomousPid(){autonomousSelected=true;destination=Destination.PID;autonomousButton.setSelected(true);cards.show(body,"AUTO");autonomous.showPidIntent();applyTheme();}
    boolean isAutonomousRunning(){return autonomous.isRunning();}
    boolean isAutonomousSelected(){return autonomousSelected;}

    void applyTheme(){
        setBackground(UiTheme.bg());bar.setBackground(UiTheme.bg());body.setBackground(UiTheme.bg());
        style(guidedButton,guidedButton.isSelected());style(autonomousButton,autonomousButton.isSelected());
        guided.applyTheme();autonomous.applyTheme();repaint();
    }
    private static void style(JToggleButton b,boolean sel){b.setFont(new Font("Dialog",Font.BOLD,10));b.setFocusPainted(false);b.setOpaque(true);b.setBackground(sel?UiTheme.softBlue():UiTheme.button());b.setForeground(sel?UiTheme.blue():UiTheme.text());b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(sel?UiTheme.blue():UiTheme.buttonBorder()),BorderFactory.createEmptyBorder(4,8,4,8)));}
}
