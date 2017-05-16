package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/**
 * Resets selected storage media
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Resetter extends SwingWorker<Boolean, Void> {

    private static final Logger LOGGER
            = Logger.getLogger(Resetter.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final List<StorageDevice> deviceList;

    private final boolean formatExchangePartition;
    private final String exchangePartitionFileSystem;
    private final boolean keepExchangePartitionLabel;
    private final String newExchangePartitionLabel;
    private final boolean formatDataPartition;
    private final String dataPartitionFileSystem;
    private final boolean resetHome;
    private final boolean resetSystem;

    private int deviceListSize;
    private int batchCounter;

    /**
     * creates a new Resetter
     *
     * @param dlCopyGUI the graphical user interface
     * @param deviceList the list of StorageDevices to reset
     * @param formatExchangePartition if the exchange partition should be
     * formatted
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param keepExchangePartitionLabel if <code>true</code>, the old label of
     * the exchange partition is kept, otherwise the
     * <code>newExchangePartitionLabel</code> will be set
     * @param newExchangePartitionLabel if
     * <code>keepExchangePartitionLabel</code> is <code>false</code> this string
     * will be used as the new label when reformatting the exchange partition
     * @param formatDataPartition if the data partition should be formatted
     * @param dataPartitionFileSystem the file system of the data partition
     * @param resetHome if the home directory should be reset
     * @param resetSystem if the system (without /home) should be reset
     */
    public Resetter(DLCopyGUI dlCopyGUI, List<StorageDevice> deviceList,
            boolean formatExchangePartition, String exchangePartitionFileSystem,
            boolean keepExchangePartitionLabel, String newExchangePartitionLabel,
            boolean formatDataPartition, String dataPartitionFileSystem,
            boolean resetHome, boolean resetSystem) {
        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.formatExchangePartition = formatExchangePartition;
        this.exchangePartitionFileSystem = exchangePartitionFileSystem;
        this.keepExchangePartitionLabel = keepExchangePartitionLabel;
        this.newExchangePartitionLabel = newExchangePartitionLabel;
        this.formatDataPartition = formatDataPartition;
        this.dataPartitionFileSystem = dataPartitionFileSystem;
        this.resetHome = resetHome;
        this.resetSystem = resetSystem;
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        dlCopyGUI.showResetProgress();

        deviceListSize = deviceList.size();

        for (StorageDevice storageDevice : deviceList) {

            dlCopyGUI.resettingDeviceStarted(storageDevice);

            batchCounter++;
            LOGGER.log(Level.INFO,
                    "resetting storage device: {0} of {1} ({2})",
                    new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            // reset exchange partition
            if (formatExchangePartition) {
                dlCopyGUI.showResetFormattingExchangePartition();
                Partition exchangePartition
                        = storageDevice.getExchangePartition();
                String label;
                if (keepExchangePartitionLabel) {
                    label = exchangePartition.getIdLabel();
                } else {
                    label = newExchangePartitionLabel;
                }
                DLCopy.formatExchangePartition(
                        "/dev/" + exchangePartition.getDeviceAndNumber(),
                        label, exchangePartitionFileSystem);
            }

            // reset data partition
            Partition dataPartition = storageDevice.getDataPartition();
            if (dataPartition != null) {
                if (formatDataPartition) {
                    // format data partition
                    dlCopyGUI.showResetFormattingDataPartition();
                    DLCopy.formatPersistencePartition(
                            "/dev/" + dataPartition.getDeviceAndNumber(),
                            dataPartitionFileSystem, dlCopyGUI);
                } else {
                    // remove files from data partition
                    dlCopyGUI.showResetRemovingFiles();

                    MountInfo mountInfo = dataPartition.mount();
                    String mountPoint = mountInfo.getMountPath();
                    ProcessExecutor processExecutor = new ProcessExecutor();
                    if (resetSystem && resetHome) {
                        // remove all files
                        // but keep "/lost+found/" and "persistence.conf"
                        processExecutor.executeProcess("find", mountPoint,
                                "!", "-regex", mountPoint,
                                "!", "-regex", mountPoint + "/lost\\+found",
                                "!", "-regex", mountPoint + "/persistence.conf",
                                "-exec", "rm", "-rf", "{}", ";");
                    } else {
                        if (resetSystem) {
                            // remove all files but keep
                            // "/lost+found/", "persistence.conf" and "/home/"
                            processExecutor.executeProcess("find", mountPoint,
                                    "!", "-regex", mountPoint,
                                    "!", "-regex", mountPoint + "/lost\\+found",
                                    "!", "-regex", mountPoint + "/persistence.conf",
                                    "!", "-regex", mountPoint + "/home.*",
                                    "-exec", "rm", "-rf", "{}", ";");
                        }
                        if (resetHome) {
                            // only remove "/home/user/"
                            processExecutor.executeProcess(
                                    "rm", "-rf", mountPoint + "/home/user/");
                        }
                    }
                    if (resetHome) {
                        // restore "/home/user/" from "/etc/skel/"
                        processExecutor.executeProcess("mkdir",
                                mountPoint + "/home/");
                        processExecutor.executeProcess("cp", "-a",
                                "/etc/skel/", mountPoint + "/home/user/");
                        processExecutor.executeProcess("chown", "-R",
                                "user.user", mountPoint + "/home/user/");
                    }
                    if (!mountInfo.alreadyMounted()) {
                        DLCopy.umount(dataPartition, dlCopyGUI);
                    }
                }
            }

            LOGGER.log(Level.INFO, "resetting of storage device finished: "
                    + "{0} of {1} ({2})", new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });
        }

        return true;
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.resettingFinished(get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }
}
