package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A Transferrer for transferring files
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class FileTransferrer extends Transferrer {

    private static final Logger LOGGER
            = Logger.getLogger(FileTransferrer.class.getName());

    private final Installer installer;
    private final StorageDevice sourceDevice;
    private final Partition destinationPartition;

    private Partition systemPartition;
    private MountInfo systemMountInfo;
    private List<String> readOnlyMountPoints;
    private Partition dataPartition;
    private MountInfo dataMountInfo;
    private File overlayDir;
    private String cowPath;
    private MountInfo destinationMountInfo;

    public FileTransferrer(DLCopyGUI gui, Installer installer,
            StorageDevice sourceDevice, Partition destinationPartition) {

        super(gui);
        this.installer = installer;
        this.sourceDevice = sourceDevice;
        this.destinationPartition = destinationPartition;
    }

    public void transfer(boolean transferHome, boolean transferNetwork,
            boolean transferPrinter, boolean transferFirewall)
            throws IOException, DBusException {

        mount();

        if (transferHome) {
            transferDirectory("/home/user/");
            ProcessExecutor executor = new ProcessExecutor();
            executor.executeProcess("chown", "user.user",
                    destinationMountInfo.getMountPath() + "/rw/home/user");
        }
        if (transferNetwork) {
            transferDirectory("/etc/NetworkManager/");
        }
        if (transferPrinter) {
            transferDirectory("/etc/cups/");
        }
        if (transferFirewall) {
            transferDirectory("/etc/lernstick-firewall/");
        }
        // TODO: find a way to transfer user settings
        // (directly after installation, the necessary files are not there yet)
//        if (transferUserSettings) {
//            new UserConfiguration(cowPath).apply(
//                    destinationMountInfo.getMountPath() + "/rw");
//        }

        unmount();
    }

    private void mount() throws DBusException, IOException {
        systemPartition = sourceDevice.getSystemPartition();
        systemMountInfo = systemPartition.mount();
        readOnlyMountPoints = LernstickFileTools.mountAllSquashFS(
                systemMountInfo.getMountPath());

        // union read only squashfs's with data partition
        dataPartition = sourceDevice.getDataPartition();
        dataMountInfo = dataPartition.mount();
        overlayDir = LernstickFileTools.mountOverlay(
                dataMountInfo.getMountPath(), readOnlyMountPoints, true);
        cowPath = new File(overlayDir, "merged").getPath();

        destinationMountInfo = destinationPartition.mount();
    }

    private void transferDirectory(String sourceDir) {
        String destination = destinationMountInfo.getMountPath()
                + "/rw" + sourceDir;
        String copyScript = "#!/bin/bash\n"
                + "mkdir -p \"" + destination + "\"\n"
                + "cp -av \"" + cowPath + sourceDir + "\"* \""
                + destination + "\"";
        gui.showInstallPersistencyCopy(installer, copyScript, cowPath);
    }

    private void unmount() throws IOException, DBusException {
        try {
            TimeUnit.SECONDS.sleep(5);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        DLCopy.umount(cowPath, gui);

        for (String readOnlyMountPoint : readOnlyMountPoints) {
            DLCopy.umount(readOnlyMountPoint, gui);
        }

        LernstickFileTools.recursiveDelete(overlayDir, true);

        unmountAndDelete(dataMountInfo, dataPartition);
        unmountAndDelete(systemMountInfo, systemPartition);
        unmountAndDelete(destinationMountInfo, destinationPartition);
    }
}
