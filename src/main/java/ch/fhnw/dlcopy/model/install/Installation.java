package ch.fhnw.dlcopy.model.install;

import ch.fhnw.util.StorageDevice;
import java.time.Duration;
import java.time.LocalTime;

/**
 * Represents a installation.
 * @author lukas-gysin
 */
public class Installation {
    
    /**
     * The detail status of the installation
     */
    private InstallationStatus detailStatus;
    
    /**
     * The device, on witch the installation take place
     */
    private StorageDevice device;
    
    /**
     * The time the installation ended successfull or with an error
     */
    private LocalTime end;
    
    /**
     * The error message, if an error occured.
     */
    private String error;
    
    /**
     * The number of the exchange partition.
     * Is calculated from the autonumbering feature
     */
    private int number;
    
    /**
     * The time the installation started
     */
    private LocalTime start;
    
    /**
     * The overall status of the installation
     */
    private OperationStatus status;
    
    /**
     * Creates a new instace of an installation.
     * It will be asumed, the installation did not start yet.
     * @param device The device on witch the installation takes place
     * @param number The number of the device. Should be given from the autonumbering feature
     */
    public Installation(StorageDevice device, int number){
            this(
            device,
            number,
            OperationStatus.PENDING,
            null, // Detail status
            null, // Start time
            null, // End time
            null // Error message
        );
    }
    
    /**
     * Creates a new instace of an installation without an error
     * @param device The device on witch the installation takes place
     * @param number The number of the device. Should be given from the autonumbering feature
     * @param status The overall status of the installation
     * @param detailStatus The detailed status of the installation. Can be <c>null</c>, if the installation didn't started yet
     * @param start The time the installation started or <c>null</c>, if the installation didn't started yet
     * @param end The time the installation ended with an error or <c>null</c>, if the installation didn't ended yet
     */
    public Installation(StorageDevice device, int number, OperationStatus status, InstallationStatus detailStatus, LocalTime start, LocalTime end) {
        this(
            device,
            number,
            status,
            detailStatus,
            start,
            end,
            null // Error message
        );
    }
    
    /**
     * Creates a new instace of an installation with an error
     * @param device The device on witch the installation takes place
     * @param number The number of the device. Should be given from the autonumbering feature
     * @param status The overall status of the installation
     * @param detailStatus The detailed status of the installation. Can be <c>null</c>, if the installation didn't started yet
     * @param start The time the installation started or <c>null</c>, if the installation didn't started yet
     * @param end The time the installation ended with an error or <c>null</c>, if the installation didn't ended yet
     * @param error The error message of the installation
     */
    public Installation(StorageDevice device, int number, OperationStatus status, InstallationStatus detailStatus, LocalTime start, LocalTime end, String error){
        // Check if device is null
        if (device == null) {
            throw new NullPointerException("device should not be null!");
        }
        this.device = device;
        
        this.number = number;
        
        setStatus(status);
        setDetailStatus(detailStatus);
        setStart(start);
        setEnd(end);
        setError(error);
    }

    /**
     * Returns the detailed status of the installation
     * If the installation is pending or finished it will return <c>null</c>
     * @return The detail status of the installation or <c>null</c>
     */
    public InstallationStatus getDetailStatus() {
        return detailStatus;
    }

    /**
     * Sets the detailed status of the installation
     * @param detailStatus The detailed status of the installation or <c>null</c> if not specified.
     */
    public void setDetailStatus(InstallationStatus detailStatus) {
        this.detailStatus = detailStatus;
    }

    /**
     * Returns the device on witch the installation takes place
     * @return the devie on witch the installation takes place
     */
    public StorageDevice getDevice() {
        return device;
    }
    
    /**
     * Returns the duration, how long the installation took.
     * If the installation is ongoing, the time since the installation started will be returned
     * If the installation did not started yet, <c>null</c> will be returend
     * @return The current duration of the installation or <c>null</c>
     */
    public Duration getDuration() {
        Duration duration = null;
        if (hasStarted()) {
            // Installation has started
            if (hasEnded()) {
                // Installation has ended
                duration = Duration.between(start, end);
            } else {
                // Installation is ongoing
                duration = Duration.between(start, LocalTime.now());
            }
        }
        return duration;
    }
    
    /**
     * Returns the duration as a string
     * @return The current duration of the installation 
     */
    public String getDuratioinString() {
        String result;
        Duration duration = getDuration();
        
        if (duration == null){
            // Operation has not started yet
            result = "";
        } else {
            result = duration.toHours() + ":" + duration.toMinutes() + ":" + duration.toSeconds();
        }

        return result;
    }
    
    /**
     * The end time of the installation or <c>null</c>, if the installation did not end yet.
     * @return The end time of the installation or <c>null</c>
     */
    public LocalTime getEnd() {
        return end;
    }
    
    /**
     * Returns, if the installation has ended yet
     * @return <c>true</c>, if the installation has ended
     */
    public boolean hasEnded() {
        return end != null;
    }
    
    /**
     * Returns, if the installation is finished
     * Is an alias of {@link #hasEnded()}
     * @return <c>true</c>, if the installation has ended
     * @see #hasEnded() hasEnded()
     */
    public boolean isFinished() {
        return hasEnded();
    }

    /**
     * Sets the end time of the installation
     * If the installation has not ended yet, set <c>null</c>
     * If the end time is set, the start time must be set in advance
     * @param end The end time of the installation or <c>null</c>
     */
    public void setEnd(LocalTime end) {
        // When the end time is defiened, the start time must also be defined
        if (start == null && end != null) {
            throw new IllegalArgumentException("You must provide a valid start time, if the end time is specified!");
        }
        // Check if end is before the start
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("The installation can not be finished bevore it started!");
        }
        this.end = end;
    }

    /**
     * Returns the error message of the failed installation
     * If the installation does not have an error <c>null</c> is returned
     * @return The error message or <c>null</c>
     */
    public String getError() {
        return error;
    }

    /**
     * Returns, if the installation has an error
     * @return <c>true</c>, if the installation has en error
     */
    public boolean hasError() {
        return error !=  null;
    }
    
    /**
     * Sets an error message
     * @param error The error message of the failed installation
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * Returns the number of the device
     * The number is according to the autonumber feature
     * @return The number of the device
     */
    public int getNumber() {
        return number;
    }

    /**
     * Returns the start time of the installation or <c>null</c>, if the installation did not start yet
     * @return The start time of the installation or <c>null</c>
     */
    public LocalTime getStart() {
        return start;
    }
    
    /**
     * Returns, if the installation has started yet
     * @return <c>true</c>, if the installation has started
     */
    public boolean hasStarted() {
        return start != null;
    }

    /**
     * Sets the start start time
     * @param start The start time of the installation
     */
    public void setStart(LocalTime start) {
        // When the end time is defiened, the start time must also be defined
        if (start == null && end != null) {
            throw new IllegalArgumentException("You must provide a valid start time, if the end time is specified!");
        }
        // Check if end is before the start
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException("The installation can not be finished bevore it started!");
        }
        this.start = start;
    }

    /**
     * Returns the overall status of the installation
     * @return The status of the installation
     */
    public OperationStatus getStatus() {
        return status;
    }

    /**
     * Sets the overall status of the installation
     * The start time gets updated, if the status switches form <c>PENDING</c> to <c>ONGOING</c>
     * The start end gets updated, if the status switches form <c>ONGOING</c> to <c>SUCCESSFULL</c> or <c>FAILED</c>
     * @param status The overall status of the installation
     */
    public void setStatus(OperationStatus status) {
        // Checks if the status is null
        if (status == null){
            throw new NullPointerException("status should not be null!");
        }
        
        // If the status changes from `PENDIGN` to `ONGOING`
        // Then the installation just started and the `start` time can be set
        if (this.status == OperationStatus.PENDING && status == OperationStatus.ONGOING){
            setStart(LocalTime.now());
        }
        
        // If the status changes from `ONGOING` to `SUCCESSFUL`
        // Then the installation just ended and the `end` time can be set
        if (this.status == OperationStatus.ONGOING && status == OperationStatus.SUCCESSFULL){
            setEnd(LocalTime.now());
            setDetailStatus(null);
        }
        
        // If the status changes from `ONGOING` to `FAILED`
        // Then the installation just ended and the `end` time can be set
        if (this.status == OperationStatus.ONGOING && status == OperationStatus.FAILED){
            setEnd(LocalTime.now());
            setDetailStatus(null);
        }
        
        this.status = status;
    }
}
