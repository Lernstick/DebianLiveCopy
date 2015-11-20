package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Installs the system from an InstallationSource to a list of StorageDevices
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Installer extends Thread
        implements InstallerOrUpgrader, PropertyChangeListener {

    private static final Logger LOGGER
            = Logger.getLogger(Installer.class.getName());

    private final DLCopy dlCopy;
    private final DLCopyGUI dlCopyGUI;
    private final List<StorageDevice> deviceList;
    private String exchangePartitionLabel;
    private int autoNumber;
    private final int autoNumberIncrement;
    private final String autoNumberPattern;
    private FileCopier fileCopier;
    private int currentDevice;
    private int selectionCount;

    /**
     * creates a new Installer
     *
     * @param dlCopy the main DLCopy instance
     * @param dlCopyGUI the DLCopy GUI
     * @param deviceList the list of StorageDevices to install
     * @param exchangePartitionLabel the label of the exchange partition
     * @param autoNumberStart the auto numbering start value
     * @param autoNumberIncrement the auto numbering increment
     * @param autoNumberPattern the auto numbering pattern
     */
    public Installer(DLCopy dlCopy, DLCopyGUI dlCopyGUI,
            List<StorageDevice> deviceList, String exchangePartitionLabel,
            int autoNumberStart, int autoNumberIncrement,
            String autoNumberPattern) {
        this.dlCopy = dlCopy;
        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.exchangePartitionLabel = exchangePartitionLabel;
        this.autoNumber = autoNumberStart;
        this.autoNumberIncrement = autoNumberIncrement;
        this.autoNumberPattern = autoNumberPattern;
    }

    @Override
    public void run() {

        LogindInhibit inhibit = new LogindInhibit("Installing");

        dlCopyGUI.showInstallProgress();

        selectionCount = deviceList.size();
        fileCopier = new FileCopier();

        for (StorageDevice storageDevice : deviceList) {

            currentDevice++;

            // update overall progress message
            dlCopyGUI.installingDeviceStarted(storageDevice);

            // auto numbering
            if (!autoNumberPattern.isEmpty()) {
                exchangePartitionLabel = exchangePartitionLabel.replace(
                        autoNumberPattern, String.valueOf(autoNumber));
                autoNumber += autoNumberIncrement;
            }

            String errorMessage = null;
            try {
                dlCopy.copyToStorageDevice(fileCopier, storageDevice,
                        exchangePartitionLabel, this);
            } catch (InterruptedException | IOException |
                    DBusException exception) {
                LOGGER.log(Level.WARNING, "", exception);
                errorMessage = exception.getMessage();
            }

            dlCopyGUI.installingDeviceFinished(errorMessage, autoNumber);
        }

        dlCopyGUI.installingListFinished();

        inhibit.delete();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        switch (propertyName) {
            case FileCopier.BYTE_COUNTER_PROPERTY:
                long byteCount = fileCopier.getByteCount();
                long copiedBytes = fileCopier.getCopiedBytes();
                int progress = (int) ((100 * copiedBytes) / byteCount);
                dlCopyGUI.setProgressInTitle(progress + "% "
                        + STRINGS.getString("Copied") + " ("
                        + currentDevice + '/' + selectionCount + ')');
                break;

            case ProcessExecutor.LINE:
                // store current cp progress line
                // (will be pattern matched later when needed)
                String line = (String) evt.getNewValue();
                dlCopyGUI.setInstallCopyLine(line);
        }
    }

    @Override
    public void showCreatingFileSystems() {
        dlCopyGUI.showInstallCreatingFileSystems();
    }

    @Override
    public void showCopyingFiles(FileCopier fileCopier) {
        dlCopyGUI.showInstallFileCopy(fileCopier);
    }

    @Override
    public void showUnmounting() {
        dlCopyGUI.showInstallUnmounting();
    }

    @Override
    public void showWritingBootSector() {
        dlCopyGUI.showInstallWritingBootSector();
    }
}
