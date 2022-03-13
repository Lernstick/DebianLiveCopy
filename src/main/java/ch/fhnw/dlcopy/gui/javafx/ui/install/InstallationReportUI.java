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
    
    @FXML TableColumn<StorageDeviceResult, String> colModel;
    @FXML TableColumn<StorageDeviceResult, String> colMountpoint;
    @FXML TableColumn<StorageDeviceResult, String> colSerial;
    @FXML TableColumn<StorageDeviceResult, String> colSize;
    @FXML TableColumn<StorageDeviceResult, String> colVendor;
    @FXML TableView<StorageDeviceResult> tvReport;
    
    public InstallationReportUI(){
        resourcePath = getClass().getResource("/fxml/install/installationReport.fxml");
    }

    @Override
    protected void initControls() {
        colModel.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getModel()));
        colMountpoint.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getFullDevice()));
        colSerial.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getSerial()));
        colSize.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getStorageDevice().getSize())));
        colVendor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getVendor()));
        
        tvReport.setItems(InstallControler.getInstance().getReport());
    }
    
    
}
