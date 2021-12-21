package ch.fhnw.dlcopy.utility.checker;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.RunningSystemSource;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.javafx.ui.install.SelectDeviceUI;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Checks if a persistance (data) partition is available, but unmounted.
 */
public class UnmountedPersistenceAvailableChecker {
    private static final Logger LOGGER = Logger.getLogger(SelectDeviceUI.class.getName());
    private static final ProcessExecutor PROCESS_EXECUTOR = new ProcessExecutor();
    
    private String errorMessage = null;
    private SystemSource runningSystemSource;
    
    public UnmountedPersistenceAvailableChecker(){
        Map<String, String> environment = new HashMap<>();
        environment.put("LC_ALL", "C");
        PROCESS_EXECUTOR.setEnvironment(environment);

        try {
            runningSystemSource = new RunningSystemSource(PROCESS_EXECUTOR);
        } catch (DBusException | IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
    
    public boolean check(){
        // check that a persistence partition is available
        Partition dataPartition = runningSystemSource.getDataPartition();
        if (dataPartition == null) {
            errorMessage = STRINGS.getString("Error_No_Persistence");
            return false;
        }
        
        try {
            // ensure that the persistence partition is not mounted read-write
            if (DLCopy.isMountedReadWrite(dataPartition.getFullDeviceAndNumber())) {
                if (DLCopy.isBootPersistent()) {
                    // error and hint
                    errorMessage = STRINGS.getString(
                            "Warning_Persistence_Mounted") + "\n"
                            + STRINGS.getString("Hint_Nonpersistent_Boot");
                    return false;
                    
                } else {
                    // persistence partition was manually mounted
                    // warning and offer umount
                    errorMessage = STRINGS.getString(
                            "Warning_Persistence_Mounted") + "\n"
                            + STRINGS.getString("Umount_Question");
                }
            }
        } catch (IOException ex) {
            Logger.getLogger(UnmountedPersistenceAvailableChecker.class.getName()).log(Level.SEVERE, null, ex);
            return false;
        }
        
        return true;
    }
    
    public String getErrorMessage(){
        return errorMessage;
    }
}
