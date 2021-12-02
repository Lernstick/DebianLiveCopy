package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.gui.javafx.ui.View;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.image.ImageView;

public class LoadUI extends View{
    @FXML private ImageView imgExportFile;
    
    public LoadUI(){
        resourcePath = getClass().getResource("/fxml/exportData/load-responsive.fxml");
    }
    
    @Override
    protected void setupEventHandlers(){
        imgExportFile.fitHeightProperty().bind(Bindings.divide(model.heightProperty(), 5.869));
        imgExportFile.fitWidthProperty().bind(Bindings.divide(model.widthProperty(), 9.8969));
    }
}