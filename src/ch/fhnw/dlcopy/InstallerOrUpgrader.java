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
public abstract class InstallerOrUpgrader extends SwingWorker<Void, Void> 
        implements PropertyChangeListener {

    protected final DLCopyGUI dlCopyGUI;
    protected final DLCopy dlCopy;
    protected final List<StorageDevice> deviceList;
    protected final FileCopier fileCopier = new FileCopier();
    protected String exchangePartitionLabel;
    protected LogindInhibit inhibit;
    protected int selectionCount;
    protected int currentDevice;

    public InstallerOrUpgrader(DLCopy dlCopy, DLCopyGUI dlCopyGUI,
            List<StorageDevice> deviceList, String exchangePartitionLabel) {
        this.dlCopy = dlCopy;
        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.exchangePartitionLabel = exchangePartitionLabel;
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
