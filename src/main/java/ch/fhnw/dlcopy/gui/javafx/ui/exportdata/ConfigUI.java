package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.gui.javafx.ui.View;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public class ConfigUI extends View{
    
    @FXML private ImageView imgTargetDirectory;
    
    public ConfigUI(){
        resourcePath = getClass().getResource("/fxml/exportData/dataexport.fxml");
    }
    
    @Override
    protected void setupEventHandlers() {
        imgTargetDirectory.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 25.714));
        imgTargetDirectory.fitWidthProperty().bind(Bindings.divide(model.heightProperty(), 4.1739));
    }
}
