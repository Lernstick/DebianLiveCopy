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
 * Repairs selected storage media
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Repairer extends SwingWorker<Boolean, Void> {

    private static final Logger LOGGER
            = Logger.getLogger(Repairer.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final List<StorageDevice> deviceList;

    private final boolean formatDataPartition;
    private final String dataPartitionFileSystem;
    private final boolean resetHome;
    private final boolean resetSystem;

    private int deviceListSize;
    private int batchCounter;

    /**
     * creates a new Repairer
     *
     * @param dlCopyGUI the graphical user interface
     * @param deviceList the list of StorageDevices to repair
     * @param formatDataPartition if the data partition should be formatted
     * @param dataPartitionFileSystem the file system of the data partition
     * @param resetHome if the home directory should be reset
     * @param resetSystem if the system (without /home) should be reset
     */
    public Repairer(DLCopyGUI dlCopyGUI, List<StorageDevice> deviceList,
            boolean formatDataPartition, String dataPartitionFileSystem,
            boolean resetHome, boolean resetSystem) {
        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.formatDataPartition = formatDataPartition;
        this.dataPartitionFileSystem = dataPartitionFileSystem;
        this.resetHome = resetHome;
        this.resetSystem = resetSystem;
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        dlCopyGUI.showRepairProgress();

        deviceListSize = deviceList.size();

        for (StorageDevice storageDevice : deviceList) {

            dlCopyGUI.repairingDeviceStarted(storageDevice);

            batchCounter++;
            LOGGER.log(Level.INFO,
                    "repairing storage device: {0} of {1} ({2})",
                    new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            // repair
            Partition dataPartition = storageDevice.getDataPartition();
            if (formatDataPartition) {
                // format data partition
                dlCopyGUI.showRepairFormattingDataPartition();
                DLCopy.formatPersistencePartition(
                        "/dev/" + dataPartition.getDeviceAndNumber(),
                        dataPartitionFileSystem, dlCopyGUI);
            } else {
                // remove files from data partition
                dlCopyGUI.showRepairRemovingFiles();

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

            LOGGER.log(Level.INFO, "repairing of storage device finished: "
                    + "{0} of {1} ({2})", new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });
        }

        return true;
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.repairingFinished(get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }
}
