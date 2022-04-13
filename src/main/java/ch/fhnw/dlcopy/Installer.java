package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.Lock;
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
    private final String autoNumberPattern;
    private int autoNumber;
    private final int autoNumberIncrement;
    private final int autoNumberMinDigits;
    private final boolean personalDataPartitionEncryption;
    private final String personalEncryptionPassword;
    private final boolean secondaryDataPartitionEncryption;
    private final String secondaryEncryptionPassword;
    private final boolean randomFillDataPartition;
    private final boolean copyDataPartition;
    private final DataPartitionMode dataPartitionMode;
    private final StorageDevice transferDevice;
    private final boolean transferExchange;
    private final boolean transferHome;
    private final boolean transferNetwork;
    private final boolean transferPrinter;
    private final boolean transferFirewall;
    private final boolean checkCopies;

    /**
     * creates a new Installer
     *
     * @param source the system source
     * @param deviceList the list of StorageDevices to install
     * @param exchangePartitionLabel the label of the exchange partition
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param dataPartitionFileSystem the file system of the data partition
     * @param digestCache a global digest cache for speeding up repeated file
     * checks
     * @param dlCopyGUI the DLCopy GUI
     * @param exchangePartitionSize the size of the exchange partition
     * @param copyExchangePartition if the exchange partition should be copied
     * @param autoNumberPattern the auto numbering pattern
     * @param autoNumberStart the auto numbering start value
     * @param autoNumberIncrement the auto numbering increment
     * @param autoNumberMinDigits the minimal number of digits to use for auto
     * numbering
     * @param personalDataPartitionEncryption if the data partition should be
     * encrypted with a personal password
     * @param personalEncryptionPassword the personal password for data
     * partition encryption
     * @param secondaryDataPartitionEncryption if the data partition should be
     * encrypted with a secondary password
     * @param secondaryEncryptionPassword the secondary password for data
     * partition encryption
     * @param randomFillDataPartition if the data partition should be filled
     * with random data before formatting
     * @param copyDataPartition if the data partition should be copied
     * @param dataPartitionMode the mode of the data partition to set in the
     * bootloaders config
     * @param checkCopies if copies should be checked for errors
     * @param transferDevice the device to transfer data from or null, if no
     * data should be transferred
     * @param transferExchange if the exchange partition should be transferred
     * @param transferHome if the home folder should be transferred
     * @param transferNetwork if the network settings should be transferred
     * @param transferPrinter if the printer settings should be transferred
     * @param transferFirewall if the firewall settings should be transferred
     * @param lock the lock to aquire before executing in background
     */
    public Installer(SystemSource source, List<StorageDevice> deviceList,
            String exchangePartitionLabel, String exchangePartitionFileSystem,
            String dataPartitionFileSystem, HashMap<String, byte[]> digestCache,
            DLCopyGUI dlCopyGUI, int exchangePartitionSize,
            boolean copyExchangePartition, String autoNumberPattern,
            int autoNumberStart, int autoNumberIncrement,
            int autoNumberMinDigits, boolean personalDataPartitionEncryption,
            String personalEncryptionPassword,
            boolean secondaryDataPartitionEncryption,
            String secondaryEncryptionPassword, boolean randomFillDataPartition,
            boolean copyDataPartition,
            DataPartitionMode dataPartitionMode, StorageDevice transferDevice,
            boolean transferExchange, boolean transferHome,
            boolean transferNetwork, boolean transferPrinter,
            boolean transferFirewall, boolean checkCopies, Lock lock) {

        super(source, deviceList, exchangePartitionLabel,
                exchangePartitionFileSystem, dataPartitionFileSystem,
                digestCache, dlCopyGUI, lock);

        this.exchangePartitionSize = exchangePartitionSize;
        this.copyExchangePartition = copyExchangePartition;
        this.autoNumberPattern = autoNumberPattern;
        this.autoNumber = autoNumberStart;
        this.autoNumberIncrement = autoNumberIncrement;
        this.autoNumberMinDigits = autoNumberMinDigits;
        this.personalDataPartitionEncryption = personalDataPartitionEncryption;
        this.personalEncryptionPassword = personalEncryptionPassword;
        this.secondaryDataPartitionEncryption = secondaryDataPartitionEncryption;
        this.secondaryEncryptionPassword = secondaryEncryptionPassword;
        this.randomFillDataPartition = randomFillDataPartition;
        this.copyDataPartition = copyDataPartition;
        this.dataPartitionMode = dataPartitionMode;
        this.checkCopies = checkCopies;
        this.transferDevice = transferDevice;
        this.transferExchange = transferExchange;
        this.transferHome = transferHome;
        this.transferNetwork = transferNetwork;
        this.transferPrinter = transferPrinter;
        this.transferFirewall = transferFirewall;
    }

    @Override
    protected Void doInBackground() throws Exception {

        lock.lock();
        try {
            inhibit = new LogindInhibit("Installing");

            dlCopyGUI.showInstallProgress();

            String currentExchangePartitionLabel = exchangePartitionLabel;
            for (StorageDevice storageDevice : deviceList) {

                // update overall progress message
                dlCopyGUI.installingDeviceStarted(storageDevice);

                // auto numbering
                if (!autoNumberPattern.isEmpty()) {
                    String autoNumberString = String.valueOf(autoNumber);
                    int nrOfPrefixZeros
                            = autoNumberMinDigits - autoNumberString.length();
                    for (int i = 0; i < nrOfPrefixZeros; i++) {
                        autoNumberString = "0" + autoNumberString;
                    }
                    currentExchangePartitionLabel
                            = exchangePartitionLabel.replace(
                                    autoNumberPattern, autoNumberString);
                    autoNumber += autoNumberIncrement;
                }

                String errorMessage = null;
                try {
                    DLCopy.copyToStorageDevice(source, fileCopier,
                            storageDevice, currentExchangePartitionLabel,
                            this, personalDataPartitionEncryption,
                            personalEncryptionPassword,
                            secondaryDataPartitionEncryption,
                            secondaryEncryptionPassword,
                            randomFillDataPartition, checkCopies, dlCopyGUI);
                } catch (InterruptedException | IOException
                        | DBusException exception) {
                    LOGGER.log(Level.WARNING, "", exception);
                    errorMessage = exception.getMessage();
                }

                if (transferDevice != null) {
                    DLCopy.transfer(transferDevice, storageDevice,
                            transferExchange, transferHome, transferNetwork,
                            transferPrinter, transferFirewall, checkCopies,
                            this, dlCopyGUI);
                }

                dlCopyGUI.installingDeviceFinished(errorMessage, autoNumber);
            }

            return null;

        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }

        // the following try-catch block is needed to log otherwise invisible
        // runtime exceptions
//        try {
//            get();
//        } catch (Exception ex) {
//            LOGGER.log(Level.WARNING, "", ex);
//        }

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
