package ch.fhnw.dlcopy.utility.checker;

import ch.fhnw.util.StorageDevice;

public class DeviceInstallationChecker{
    
    private String errorMessage = null;

    public boolean check(StorageDevice device) {
        
        return true;
    }
    
    public String getErrorMessage(){
        return errorMessage;
    }
}
