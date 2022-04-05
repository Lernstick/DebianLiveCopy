package ch.fhnw.dlcopy.gui.javafx.ui.install;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.*;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.Timer;
import javafx.beans.binding.Bindings;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;

public class LoadUI extends View {

    @FXML private Button btnReport;
    @FXML private ImageView imgExportFile;
    @FXML private ProgressBar pbStatus;
    @FXML private TextFlow tfExtraInfo;    
    @FXML private Button btnBack;    
    @FXML private Label lblProgressInfo;
    
    
    
    private String tmpMessage;
    
    //Both need to be > 0 for indeterminate time
    private long indetValue;
    private long indetMax; 
    
    
    
    private Timer overwriteTimer;
    private OverwriteRandomTimerTask overwriteRandomTimerTask;
    private int tmpProgress = -1;
    private List<Text>  bulletpoints = new ArrayList<Text>();

    private Label currBP;

    public LoadUI() {
        resourcePath = getClass().getResource("/fxml/install/load.fxml");

    }

    public LoadUI(String message, int progress) {
        this();
        tmpMessage = message;
        tmpProgress = progress;
    }
    
    
    //indeterminate
    public LoadUI(String message) {
        this(message, -1);
    }
  
    
    //determinate
    public LoadUI(long value, long maximum) {
        this();
        indetMax = maximum; 
        indetValue = value; 

    }
    
    
    public void initBulletPoints(){
        bulletpoints.add(new Text(stringBundle.getString("global.unmount_file_systems")));
        bulletpoints.add(new Text("\n"));
        bulletpoints.add(new Text(stringBundle.getString("global.writing_boot_sector")));     
        bulletpoints.add(new Text("\n"));
        bulletpoints.add(new Text(stringBundle.getString("global.creating_file_systems")));
        bulletpoints.add(new Text("\n"));
        
        for (Text bp : bulletpoints){
            tfExtraInfo.getChildren().add(bp);
        }
    }
   

    
    public void setBulletpoint(String bPStr) {
        TextFlow textFlow = new TextFlow();
        for (Text s : bulletpoints){
            if (bPStr.equals(s.getText())){
                s.setFill(Color.BLUE);                
            } else {
                s.setFill(Color.BLACK);
            }
        }
    }
    
    public void showOverwriteRandomProgressBar(long value, long maximum) {
        if (overwriteTimer == null) {
            overwriteTimer = new Timer();
            overwriteTimer.schedule(new OverwriteRandomTimerTask(pbStatus, lblProgressInfo, "Overwrite data",  value), 0,1000);
        }
        overwriteRandomTimerTask.setDone(value);
    }
    
    public void showIndeterminateProgressBar(final String text) {

        if (overwriteTimer != null) {
            overwriteTimer.cancel();
            overwriteTimer = null;
        }

        lblProgressInfo.setText(text);
    }
    
    public void finished(){
        pbStatus.setVisible(false);
        btnBack.setDisable(false);
    }
    

    @Override
    protected void initSelf() {
        if(indetValue != 0 &&  indetMax != 0) {
            showOverwriteRandomProgressBar(indetValue, indetMax);
        } else if (tmpMessage != null){
            initBulletPoints();
            setBulletpoint(tmpMessage);
            showIndeterminateProgressBar(tmpMessage);
        }

        btnBack.setDisable(true);
        double percent = Math.max(tmpProgress, 100) / 100;
        pbStatus.setProgress(percent);
    }

    @Override
    protected void setupEventHandlers() {
        btnReport.setOnAction((ActionEvent event) -> {
            context.setScene(new InstallationReportUI());
            // context.setScene(new InstallationReportUI());
        });
        
        imgExportFile.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 5.869));
        imgExportFile.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 9.8969));
    }
}
