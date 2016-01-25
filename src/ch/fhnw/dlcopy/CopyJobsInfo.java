package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.CopyJob;

/**
 * A collection of different CopyJobs for a storage device
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class CopyJobsInfo {

    private final String destinationEfiPath;
    private final String destinationSystemPath;
    private final CopyJob efiCopyJob;
    private final CopyJob exchangeEfiCopyJob;
    private final CopyJob systemCopyJob;

    /**
     * creates a new CopyJobsInfo
     *
     * @param destinationEfiPath the path to the destination EFI partition
     * mount point
     * @param destinationSystemPath the path to the destination system partition
     * mount point
     * @param efiCopyJob the CopyJob for the EFI partition
     * @param exchangeEfiCopyJob the CopyJob for EFI files on the exchange
     * partition
     * @param systemCopyJob the CopyJob for the system partition
     */
    public CopyJobsInfo(String destinationEfiPath,
            String destinationSystemPath, CopyJob efiCopyJob,
            CopyJob exchangeEfiCopyJob, CopyJob systemCopyJob) {
        this.destinationEfiPath = destinationEfiPath;
        this.destinationSystemPath = destinationSystemPath;
        this.efiCopyJob = efiCopyJob;
        this.exchangeEfiCopyJob = exchangeEfiCopyJob;
        this.systemCopyJob = systemCopyJob;
    }

    /**
     * returns the path to the destination EFI partition mount point
     *
     * @return the path to the destination EFI partition mount point
     */
    public String getDestinationEfiPath() {
        return destinationEfiPath;
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
     * returns the CopyJob for the EFI partition
     *
     * @return the CopyJob for the EFI partition
     */
    public CopyJob getEfiCopyJob() {
        return efiCopyJob;
    }

    /**
     * returns the CopyJob for EFI files on the exchange partition
     *
     * @return the CopyJob for EFI files on the exchange partition
     */
    public CopyJob getExchangeEfiCopyJob() {
        return exchangeEfiCopyJob;
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
