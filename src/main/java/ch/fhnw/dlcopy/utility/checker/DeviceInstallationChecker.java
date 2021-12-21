package ch.fhnw.dlcopy.utility.checker;

import ch.fhnw.util.StorageDevice;

public class DeviceInstallationChecker{
    
    private String errorMessage = null;

    public boolean check(StorageDevice device) {
        UnmountedPersistenceAvailableChecker unmountedPersistenceAvailableChecker = new UnmountedPersistenceAvailableChecker();
        if(!unmountedPersistenceAvailableChecker.check()){
            // persistance (data) partition is not available or is mounted
            errorMessage = unmountedPersistenceAvailableChecker.getErrorMessage();
            return false;
        }
        
        // TODO: Show warning
        
        return true;
    }
    
    public String getErrorMessage(){
        return errorMessage;
    }
}
