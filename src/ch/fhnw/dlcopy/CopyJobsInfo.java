package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.CopyJob;

/**
 * A collection of different CopyJobs for a storage device
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class CopyJobsInfo {

    private final String destinationBootPath;
    private final String destinationSystemPath;
    private final CopyJob bootCopyJob;
    private final CopyJob bootFilesCopyJob;
    private final CopyJob systemCopyJob;

    /**
     * creates a new CopyJobsInfo
     *
     * @param destinationBootPath the path to the destination boot partition
     * mount point
     * @param destinationSystemPath the path to the destination system partition
     * mount point
     * @param bootCopyJob the CopyJob for the boot partition
     * @param bootFilesCopyJob the CopyJob for boot files on the exchange
     * partition
     * @param systemCopyJob the CopyJob for the system partition
     */
    public CopyJobsInfo(String destinationBootPath,
            String destinationSystemPath, CopyJob bootCopyJob,
            CopyJob bootFilesCopyJob, CopyJob systemCopyJob) {
        this.destinationBootPath = destinationBootPath;
        this.destinationSystemPath = destinationSystemPath;
        this.bootCopyJob = bootCopyJob;
        this.bootFilesCopyJob = bootFilesCopyJob;
        this.systemCopyJob = systemCopyJob;
    }

    /**
     * returns the path to the destination boot partition mount point
     *
     * @return the path to the destination boot partition mount point
     */
    public String getDestinationBootPath() {
        return destinationBootPath;
    }

    /**
     * returns the path to the destination system partition mount point
     *
     * @return the path to the destination system partition mount point
     */
    public String getDestinationSystemPath() {
        return destinationSystemPath;
    }

    /**
     * returns the CopyJob for the boot partition
     *
     * @return the CopyJob for the boot partition
     */
    public CopyJob getBootCopyJob() {
        return bootCopyJob;
    }

    /**
     * returns the CopyJob for boot files on the exchange partition
     *
     * @return the CopyJob for boot files on the exchange partition
     */
    public CopyJob getBootFilesCopyJob() {
        return bootFilesCopyJob;
    }

    /**
     * returns the CopyJob for the system partition
     *
     * @return the CopyJob for the system partition
     */
    public CopyJob getSystemCopyJob() {
        return systemCopyJob;
    }
}
