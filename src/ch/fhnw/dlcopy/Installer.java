package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
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
public class Installer extends InstallerOrUpgrader
        implements PropertyChangeListener {

    private static final Logger LOGGER
            = Logger.getLogger(Installer.class.getName());

    private final int exchangePartitionSize;
    private final boolean copyExchangePartition;
    private final int autoNumberIncrement;
    private final String autoNumberPattern;
    private int autoNumber;
    private final boolean copyDataPartition;
    private final DataPartitionMode dataPartitionMode;

    /**
     * creates a new Installer
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param deviceList the list of StorageDevices to install
     * @param exchangePartitionLabel the label of the exchange partition
     * @param source the system source
     * @param exchangePartitionSize the size of the exchange partition
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param copyExchangePartition if the exchange partition should be copied
     * @param autoNumberStart the auto numbering start value
     * @param autoNumberIncrement the auto numbering increment
     * @param autoNumberPattern the auto numbering pattern
     * @param dataPartitionFileSystem the file system of the data partition
     * @param copyDataPartition if the data partition should be copied
     * @param dataPartitionMode the mode of the data partition to set in the
     * bootloaders config
     */
    public Installer(SystemSource source, List<StorageDevice> deviceList,
            String exchangePartitionLabel, String exchangePartitionFileSystem,
            String dataPartitionFileSystem, DLCopyGUI dlCopyGUI,
            int exchangePartitionSize, boolean copyExchangePartition,
            int autoNumberStart, int autoNumberIncrement,
            String autoNumberPattern, boolean copyDataPartition,
            DataPartitionMode dataPartitionMode) {

        super(source, deviceList, exchangePartitionLabel,
                exchangePartitionFileSystem, dataPartitionFileSystem,
                dlCopyGUI);

        this.exchangePartitionSize = exchangePartitionSize;
        this.copyExchangePartition = copyExchangePartition;
        this.autoNumberIncrement = autoNumberIncrement;
        this.autoNumberPattern = autoNumberPattern;
        this.autoNumber = autoNumberStart;
        this.copyDataPartition = copyDataPartition;
        this.dataPartitionMode = dataPartitionMode;
    }

    @Override
    protected Void doInBackground() throws Exception {
        inhibit = new LogindInhibit("Installing");

        dlCopyGUI.showInstallProgress();

        String currentExchangePartitionLabel = exchangePartitionLabel;
        for (StorageDevice storageDevice : deviceList) {

            // update overall progress message
            dlCopyGUI.installingDeviceStarted(storageDevice);

            // auto numbering
            if (!autoNumberPattern.isEmpty()) {
                currentExchangePartitionLabel = exchangePartitionLabel.replace(
                        autoNumberPattern, String.valueOf(autoNumber));
                autoNumber += autoNumberIncrement;
            }

            String errorMessage = null;
            try {
                DLCopy.copyToStorageDevice(source, fileCopier, storageDevice,
                        currentExchangePartitionLabel, this, dlCopyGUI);
            } catch (InterruptedException | IOException
                    | DBusException exception) {
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

    @Override
    public PartitionSizes getPartitionSizes(StorageDevice storageDevice) {
        return DLCopy.getInstallPartitionSizes(
                source, storageDevice, exchangePartitionSize);
    }

    /**
     * returns true if the user selected to copy the exchange partition, false
     * otherwise
     *
     * @return true if the user selected to copy the exchange partition, false
     * otherwise
     */
    public boolean isCopyExchangePartitionSelected() {
        return copyExchangePartition;
    }

    /**
     * returns true if the user selected to copy the data partition, false
     * otherwise
     *
     * @return true if the user selected to copy the data partition, false
     * otherwise
     */
    public boolean isCopyDataPartitionSelected() {
        return copyDataPartition;
    }

    /**
     * returns the mode for the data partition to set in the bootloaders config
     *
     * @return the mode for the data partition to set in the bootloaders config
     */
    public DataPartitionMode getDataPartitionMode() {
        return dataPartitionMode;
    }
}
