package ch.fhnw.dlcopy;

import ch.fhnw.util.StorageDevice;
import java.time.Duration;
import java.time.LocalTime;

/**
 * the result of an operation on a storage device
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class StorageDeviceResult {

    private final StorageDevice storageDevice;
    private final LocalTime startTime;
    private LocalTime finishTime;
    private Duration duration;
    private String errorMessage;

    /**
     * creates a new StorageDeviceResult
     *
     * @param storageDevice the storage device
     */
    public StorageDeviceResult(StorageDevice storageDevice) {
        this.storageDevice = storageDevice;
        startTime = LocalTime.now();
    }

    /**
     * sets the finishTime to the current time and calculates the duration
     */
    public void finish() {
        finishTime = LocalTime.now();
        duration = Duration.between(startTime, finishTime);
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
     * returns the start time of the operation
     *
     * @return the start time of the operation
     */
    public LocalTime getStartTime() {
        return startTime;
    }

    /**
     * returns the finish time of the operation
     *
     * @return the finish time of the operation
     */
    public LocalTime getFinishTime() {
        return finishTime;
    }

    /**
     * returns the duration (in ms) or <tt>null</tt> if the operation is still
     * in progress
     *
     * @return the duration (in ms) or <tt>null</tt> if the operation is still
     * in progress
     */
    public Duration getDuration() {
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

    /**
     * sets the error message of an operation
     *
     * @param errorMessage the error message of the operation
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
