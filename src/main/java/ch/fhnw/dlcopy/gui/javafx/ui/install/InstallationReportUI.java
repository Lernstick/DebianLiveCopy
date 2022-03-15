package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.StorageDevice;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class InstallationReportUI extends View{
    
    @FXML TableColumn<StorageDeviceResult, String> colDuration;
    @FXML TableColumn<StorageDeviceResult, String> colFinish;
    @FXML TableColumn<StorageDeviceResult, String> colModel;
    @FXML TableColumn<StorageDeviceResult, String> colMountpoint;
    @FXML TableColumn<StorageDeviceResult, String> colNumber;
    @FXML TableColumn<StorageDeviceResult, String> colSerial;
    @FXML TableColumn<StorageDeviceResult, String> colSize;
    @FXML TableColumn<StorageDeviceResult, String> colStart;
    @FXML TableColumn<StorageDeviceResult, String> colVendor;
    @FXML TableView<StorageDeviceResult> tvReport;
    
    public InstallationReportUI(){
        resourcePath = getClass().getResource("/fxml/install/installationReport.fxml");
    }

    @Override
    protected void initControls() {
        colDuration.setCellValueFactory(cell -> {
            return new SimpleStringProperty("Duration");
        });
        colFinish.setCellValueFactory(cell -> {
            LocalTime finishTime = cell.getValue().getFinishTime();
            String result = (finishTime == null ? "" : finishTime.format(DateTimeFormatter.ISO_LOCAL_TIME));
            return new SimpleStringProperty(result);
        });
        colModel.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getModel()));
        colMountpoint.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getFullDevice()));
        colNumber.setCellValueFactory(cell -> {
            StorageDeviceResult result = cell.getValue();
            int index = InstallControler.getInstance().getReport().indexOf(result);
            return new SimpleStringProperty(String.valueOf(index + 1));
        });
        colSerial.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getSerial()));
        colSize.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getStorageDevice().getSize())));
        colStart.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStartTime().format(DateTimeFormatter.ISO_LOCAL_TIME)));
        colVendor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getVendor()));
        
        tvReport.setItems(InstallControler.getInstance().getReport());
    }
    
    
}
