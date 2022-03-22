package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.util.StorageDevice;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;

public class InstallationReportUI extends View{
    
    HashMap <StorageDeviceResult, SimpleStringProperty> durations = new HashMap<>();
    private final Timer tableRefresheTimer = new Timer();
    
    @FXML TableColumn<StorageDeviceResult, String> colDuration;
    @FXML TableColumn<StorageDeviceResult, String> colError;
    @FXML TableColumn<StorageDeviceResult, String> colFinish;
    @FXML TableColumn<StorageDeviceResult, String> colModel;
    @FXML TableColumn<StorageDeviceResult, String> colMountpoint;
    @FXML TableColumn<StorageDeviceResult, String> colNumber;
    @FXML TableColumn<StorageDeviceResult, String> colSerial;
    @FXML TableColumn<StorageDeviceResult, String> colSize;
    @FXML TableColumn<StorageDeviceResult, String> colStart;
    @FXML TableColumn<StorageDeviceResult, String> colStatus;
    @FXML TableColumn<StorageDeviceResult, String> colVendor;
    @FXML TableView<StorageDeviceResult> tvReport;
    
    public InstallationReportUI(){
        resourcePath = getClass().getResource("/fxml/install/installationReport.fxml");
    }
    
    /**
     * This function is called, when the view should be deinitalized.
     * It has to be called manually!
     */
    @Override
    public void deinitialize() {
        tableRefresheTimer.cancel();
    }

    @Override
    protected void initSelf() {
        TimerTask tableRefresher = new TimerTask() {
            @Override
            public void run() {
                
                tvReport.refresh();
            }
        };
        tableRefresheTimer.scheduleAtFixedRate(tableRefresher, 0, 1000L); // Starts the `lisstUpdater`-task each 1000ms (1sec)
    }
    
    

    @Override
    protected void initControls() {
        colDuration.setCellValueFactory(cell -> {
            Duration duration = cell.getValue().getDuration();
            
            if (duration == null){
                // Operation still ongoing
                duration = Duration.between(cell.getValue().getStartTime(), LocalTime.now());
            }
            
            String result = duration.toHours() + ":" + duration.toMinutes() + ":" + duration.toSeconds();
            
            SimpleStringProperty value = new SimpleStringProperty(result);
            
            durations.put(cell.getValue(), value);
            
            return value;
        });
        colError.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getErrorMessage()));
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
        colStatus.setCellValueFactory(cell -> {
            if (cell.getValue().getStartTime() == null){
                return new SimpleStringProperty(stringBundle.getString("install.waiting"));
            } else if (cell.getValue().getFinishTime() == null){
                return new SimpleStringProperty(stringBundle.getString("install.installing"));
            } else if (cell.getValue().getErrorMessage() != null && !cell.getValue().getErrorMessage().isBlank()) {
                return new SimpleStringProperty(stringBundle.getString("install.error"));
            } else {
                return new SimpleStringProperty(stringBundle.getString("install.success"));
            }
        });
        colVendor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getStorageDevice().getVendor()));
        
        tvReport.setItems(InstallControler.getInstance().getReport());
    }
    
    
}
