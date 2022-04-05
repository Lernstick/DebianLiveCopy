package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.StorageDeviceResult;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class InstallControler {
    
    private static InstallControler instance = null;
    
    private ObservableList<StorageDeviceResult> report = FXCollections.observableArrayList();
    
    private InstallControler() {}
    
    public static synchronized InstallControler getInstance(){
        if (instance == null) {
            instance = new InstallControler();
        }
        
        return instance;
    }
    
    public ObservableList<StorageDeviceResult> getReport() {
        return report;
    }
}
