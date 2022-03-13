package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.StorageDevice;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class InstallationReportUI extends View{
    
    @FXML TableColumn<StorageDeviceResult, String> colMountpoint;
    @FXML TableView<StorageDeviceResult> tvReport;
    
    public InstallationReportUI(){
        resourcePath = getClass().getResource("/fxml/install/installationReport.fxml");
    }

    @Override
    protected void initControls() {
        colMountpoint.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getFullDevice()));
        
        tvReport.setItems(InstallControler.getInstance().getReport());
    }
    
    
}
