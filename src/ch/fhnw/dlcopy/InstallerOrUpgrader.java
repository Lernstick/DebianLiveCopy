package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.swing.SwingWorker;

/**
 * An abstract base class for Installer and Upgrader
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class InstallerOrUpgrader
        extends SwingWorker<Void, Void>
        implements PropertyChangeListener {

    /**
     * the graphical user interface
     */
    protected final DLCopyGUI dlCopyGUI;

    /**
     * the programm core
     */
    protected final DLCopy dlCopy;

    /**
     * the list of storage devices to handle
     */
    protected final List<StorageDevice> deviceList;

    /**
     * the FileCopier to use for copying files
     */
    protected final FileCopier fileCopier = new FileCopier();

    /**
     * the label of the exchange partition
     */
    protected String exchangePartitionLabel;

    /**
     * the LogindInhibit for preventing to switch into power saving mode
     */
    protected LogindInhibit inhibit;

    /**
     * the size of the device list
     */
    protected int deviceListSize;

    /**
     * creates a new InstallerOrUpgrader
     *
     * @param dlCopy the program core
     * @param dlCopyGUI the graphical user interface
     * @param deviceList the list of storage devices to handle
     * @param exchangePartitionLabel the label of the exchange partition
     */
    public InstallerOrUpgrader(DLCopy dlCopy, DLCopyGUI dlCopyGUI,
            List<StorageDevice> deviceList, String exchangePartitionLabel) {
        this.dlCopy = dlCopy;
        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.exchangePartitionLabel = exchangePartitionLabel;
        deviceListSize = deviceList.size();
    }

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
}
