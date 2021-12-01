package ch.fhnw.dlcopy.gui.javafx.ui.exportsystem;

import ch.fhnw.dlcopy.gui.javafx.ui.View;
import javafx.fxml.FXML;
import javafx.scene.control.ComboBox;

public class SystemexportUI extends View{
    
    private String option_NotUsed = stringBundle.getString("global.notUsed");
    private String option_ReadOnly = stringBundle.getString("global.readWrite");
    private String option_ReadWrite = stringBundle.getString("global.readOnly");
    
    @FXML private ComboBox<String> cmbDataPartitionMode;
    
    public SystemexportUI(){
        resourcePath = getClass().getResource("/fxml/exportSystem/systemexport.fxml");
    }
    
    @Override
    protected void initControls(){
        cmbDataPartitionMode.getItems().addAll(option_ReadWrite, option_ReadOnly, option_NotUsed);
        cmbDataPartitionMode.setValue(option_ReadWrite);
    }
}
