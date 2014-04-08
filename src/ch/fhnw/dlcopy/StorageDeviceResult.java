package ch.fhnw.dlcopy;

import ch.fhnw.util.StorageDevice;

/**
 * the result of an operation on a storage device
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class StorageDeviceResult {

    private final StorageDevice storageDevice;
    private final long duration;
    private final String errorMessage;

    /**
     * creates a new StorageDeviceResult
     *
     * @param storageDevice the storage device
     * @param duration the duration of the operation
     * @param errorMessage the error message of the operation or <tt>null</tt>
     * if there was no error
     */
    public StorageDeviceResult(StorageDevice storageDevice,
            long duration, String errorMessage) {
        this.storageDevice = storageDevice;
        this.duration = duration;
        this.errorMessage = errorMessage;
    }

    /**
     * returns the StorageDevice of this result
     *
     * @return the StorageDevice of this result
     */
    public StorageDevice getStorageDevice() {
        return storageDevice;
    }

    /**
     * returns the duration (in ms)
     * @return the duration (in ms)
     */
    public long getDuration() {
        return duration;
    }

    /**
     * returns the error message of the operation or <tt>null</tt> if there was
     * no error
     *
     * @return the error message of the operation or <tt>null</tt> if there was
     * no error
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}
