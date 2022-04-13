package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A controler for the installation path.
 * Holds the current state of the installation.
 * @author lukas-gysin
 */
public class InstallControler implements DLCopyGUI {
    
    /**
     * The singleton instance of the class
     */
    private static InstallControler instance = null;
    
    /**
     * A list of all installations (pending, ongoing, failed and succeeded)
     */
    private ObservableList<StorageDeviceResult> installations = FXCollections.observableArrayList();
    
    /**
     * Mark the constructor <c>private</c>, so it is not accessable from outside
     */
    private InstallControler() {}
    
    /**
     * A thread save lacy constructor for the singleton
     * @return The instance of the singleton
     */
    public static synchronized InstallControler getInstance(){
        if (instance == null) {
            instance = new InstallControler();
        }
        return instance;
    }
    
    public ObservableList<StorageDeviceResult> getReport() {
        return installations;
    }
}
