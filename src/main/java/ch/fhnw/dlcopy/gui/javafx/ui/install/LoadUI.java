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

    @FXML private Button btnBack;
    @FXML private Button btnReport;
    @FXML private ImageView imgExportFile;
    @FXML private Label lblProgressInfo;
    @FXML private ProgressBar pbStatus;
    @FXML private TextFlow tfExtraInfo;

    public LoadUI() {
        resourcePath = getClass().getResource("/fxml/install/load.fxml");
    }

    public void initBulletPoints(){
        List<Text>  bulletpoints = new ArrayList<Text>();
        
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

    @Override
    protected void initControls() {
        btnBack.setDisable(true);
        initBulletPoints();
    }
    
    @Override
    protected void setupBindings(){
        InstallControler controller = InstallControler.getInstance(context);
        
        pbStatus.progressProperty().bind(controller.getProgress());
        lblProgressInfo.textProperty().bind(controller.getInstallationStep());
    }

    @Override
    protected void setupEventHandlers() {
        btnReport.setOnAction((ActionEvent event) -> {
            context.setScene(new InstallationReportUI());
        });
        
        imgExportFile.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 5.869));
        imgExportFile.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 9.8969));
    }
}
