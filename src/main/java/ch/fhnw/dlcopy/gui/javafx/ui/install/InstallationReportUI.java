package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.model.install.Installation;
import ch.fhnw.dlcopy.model.install.OperationStatus;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
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
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import org.freedesktop.dbus.exceptions.DBusException;

public class InstallationReportUI extends View{
    
    private final Timer tableRefresheTimer = new Timer();
    
    @FXML Button btnProgress;
    @FXML TableColumn<Installation, String> colDuration;
    @FXML TableColumn<Installation, String> colError;
    @FXML TableColumn<Installation, String> colFinish;
    @FXML TableColumn<Installation, String> colModel;
    @FXML TableColumn<Installation, String> colMounted;
    @FXML TableColumn<Installation, String> colMountpoint;
    @FXML TableColumn<Installation, String> colNumber;
    @FXML TableColumn<Installation, String> colSerial;
    @FXML TableColumn<Installation, String> colSize;
    @FXML TableColumn<Installation, String> colStart;
    @FXML TableColumn<Installation, String> colStatus;
    @FXML TableColumn<Installation, String> colVendor;
    @FXML TableView<Installation> tvReport;
    
    public InstallationReportUI(){
        resourcePath = getClass().getResource("/fxml/install/installationreport.fxml");
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
        tableRefresheTimer.scheduleAtFixedRate(tableRefresher, 0, 1000L); // Starts the `listUpdater`-task each 1000ms (1sec)
    }
    
    @Override
    protected void initControls() {

        colDuration.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDuratioinString()));
        colError.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getError()));
        colFinish.setCellValueFactory(cell -> {
            LocalTime finishTime = cell.getValue().getEnd();
            String result = (finishTime == null ? "" : finishTime.format(DateTimeFormatter.ISO_LOCAL_TIME));
            return new SimpleStringProperty(result);
        });
        colModel.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDevice().getModel()));
        colMounted.setCellValueFactory(cell -> {
            try {
                if (DLCopy.getStorageDevices(false, false, "bootDeviceName").contains(cell.getValue().getDevice())) {
                    // Device is sitll in the plugged devices
                    return new SimpleStringProperty(stringBundle.getString("global.yes"));
                }
                return new SimpleStringProperty(stringBundle.getString("global.no"));
            } catch (IOException | DBusException ex) {
                Logger.getLogger(InstallationReportUI.class.getName()).log(Level.SEVERE, null, ex);
                return new SimpleStringProperty("unknown");
            }
        });
        colMountpoint.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDevice().getFullDevice()));
        colNumber.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getNumber())));
        colSerial.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDevice().getSerial()));
        colSize.setCellValueFactory(cell -> new SimpleStringProperty(String.valueOf(cell.getValue().getDevice().getSize())));
        colStart.setCellValueFactory(cell -> {
            LocalTime startTime = cell.getValue().getStart();
            String result = (startTime == null ? "" : startTime.format(DateTimeFormatter.ISO_LOCAL_TIME));
            return new SimpleStringProperty(result);
        });
        colStatus.setCellValueFactory(cell -> {
            String resutl;
            
            switch (cell.getValue().getStatus()) {
                case PENDING:
                    resutl = stringBundle.getString("install.waiting");
                    break;
                case ONGOING:
                    resutl = stringBundle.getString("install.installing");
                    break;
                case SUCCESSFULL:
                    resutl = stringBundle.getString("error.error");
                    break;
                case FAILED:
                    resutl = stringBundle.getString("install.success");
                    break;
                default:
                    throw new IllegalStateException("This Operations state is unknown: " + cell.getValue().getStatus());
            }
            
            return new SimpleStringProperty(resutl);
        });
        colVendor.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getDevice().getVendor()));
        
        tvReport.setItems(InstallControler.getInstance(context).getInstallations());
    }

    @Override
    protected void setupEventHandlers() {
        btnProgress.setOnAction(event -> {
            context.setScene(new LoadUI());
        });
    }
    
    
}
