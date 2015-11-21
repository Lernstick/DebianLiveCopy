package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.beans.PropertyChangeEvent;
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
public class Installer extends InstallerOrUpgrader {

    private static final Logger LOGGER
            = Logger.getLogger(Installer.class.getName());

    private int autoNumber;
    private final int autoNumberIncrement;
    private final String autoNumberPattern;

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
        super(dlCopy, dlCopyGUI, deviceList, exchangePartitionLabel);
        this.autoNumber = autoNumberStart;
        this.autoNumberIncrement = autoNumberIncrement;
        this.autoNumberPattern = autoNumberPattern;
    }

    @Override
    protected Void doInBackground() throws Exception {
        inhibit = new LogindInhibit("Installing");

        selectionCount = deviceList.size();

        dlCopyGUI.showInstallProgress();

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

        return null;
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }
        dlCopyGUI.installingListFinished();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (ProcessExecutor.LINE.equals(evt.getPropertyName())) {
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
