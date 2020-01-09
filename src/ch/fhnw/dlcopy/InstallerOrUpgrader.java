package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
import javax.swing.SwingWorker;

/**
 * An abstract base class for Installer and Upgrader
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class InstallerOrUpgrader
        extends SwingWorker<Void, Void> {

    /**
     * the system source
     */
    protected final SystemSource source;

    /**
     * the list of storage devices to handle
     */
    protected final List<StorageDevice> deviceList;

    /**
     * the label of the exchange partition
     */
    protected String exchangePartitionLabel;

    /**
     * the graphical user interface
     */
    protected final DLCopyGUI dlCopyGUI;

    /**
     * the FileCopier to use for copying files
     */
    protected final FileCopier fileCopier;

    /**
     * the lock to aquire before executing in background
     */
    protected final Lock lock;

    /**
     * the LogindInhibit for preventing to switch into power saving mode
     */
    protected LogindInhibit inhibit;

    /**
     * the size of the device list
     */
    protected int deviceListSize;

    private final String exchangePartitionFileSystem;
    private final String dataPartitionFileSystem;

    /**
     * creates a new InstallerOrUpgrader
     *
     * @param source the system source
     * @param deviceList the list of storage devices to handle
     * @param exchangePartitionLabel the label of the exchange partition
     * @param exhangePartitionFileSystem the file system of the exchange
     * partition
     * @param dataPartitionFileSystem the file system of the data partition
     * @param digestCache a global digest cache for speeding up repeated file
     * checks
     * @param dlCopyGUI the graphical user interface
     * @param lock the lock to aquire before executing in background
     */
    public InstallerOrUpgrader(SystemSource source,
            List<StorageDevice> deviceList, String exchangePartitionLabel,
            String exhangePartitionFileSystem, String dataPartitionFileSystem,
            HashMap<String, byte[]> digestCache, DLCopyGUI dlCopyGUI,
            Lock lock) {

        this.source = source;
        this.deviceList = deviceList;
        this.exchangePartitionLabel = exchangePartitionLabel;
        this.exchangePartitionFileSystem = exhangePartitionFileSystem;
        this.dataPartitionFileSystem = dataPartitionFileSystem;
        this.fileCopier = new FileCopier(digestCache);
        this.dlCopyGUI = dlCopyGUI;
        this.lock = lock;
        deviceListSize = deviceList.size();
    }

    /**
     * returns the partition sizes on a storage device
     *
     * @param storageDevice the StorageDevice to check
     * @return the partition sizes on a storage device
     */
    public abstract PartitionSizes getPartitionSizes(
            StorageDevice storageDevice);

    /**
     * shows that file systems are being created
     */
    public abstract void showCreatingFileSystems();

    /**
     * shows that files are being copied
     *
     * @param fileCopier the fileCopier used to copy files
     */
    public abstract void showCopyingFiles(FileCopier fileCopier);

    /**
     * shows that file systems are being unmounted
     */
    public abstract void showUnmounting();

    /**
     * shows that the boot sector is written
     */
    public abstract void showWritingBootSector();

    /**
     * returns the selected file system of the exchange partition
     *
     * @return the selected file system of the exchange partition
     */
    public String getExchangePartitionFileSystem() {
        return exchangePartitionFileSystem;
    }

    /**
     * returns the selected file system of the data partition
     *
     * @return the selected file system of the data partition
     */
    public String getDataPartitionFileSystem() {
        return dataPartitionFileSystem;
    }

    /**
     * returns the size of the source system
     *
     * @return the size of the source system
     */
    public long getSourceSystemSize() {
        return source.getSystemSize();
    }
}
