package dlcopy;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

/**
 * A storage device partition
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class Partition {

    private final static Logger LOGGER =
            Logger.getLogger(Partition.class.getName());
    private final String device;
    private final boolean bootable;
    private final long startSector;
    private final long endSector;
    private final String typeID;
    private final String typeDescription;
    private final String label;
    private final String fileSystem;
    private final String systemPartitionLabel;
    private Boolean isSystemPartition;

    /**
     * creates a new Partition
     * @param device the device file of the partition (e.g. sda1)
     * @param bootable if the partition is bootable
     * @param startSector the start sector of the partition
     * @param endSector the end sector of the partition
     * @param typeID the partition type ID
     * @param typeDescription the partition type description
     * @param label the label of the partition
     * @param fileSystem the file system on this partition
     * @param systemPartitionLabel the (expected) system partition label
     */
    public Partition(String device, boolean bootable, long startSector,
            long endSector, String typeID, String typeDescription,
            String label, String fileSystem, String systemPartitionLabel) {
        this.device = device;
        this.bootable = bootable;
        this.startSector = startSector;
        this.endSector = endSector;
        this.typeID = typeID;
        this.typeDescription = typeDescription;
        this.label = label;
        this.fileSystem = fileSystem;
        this.systemPartitionLabel = systemPartitionLabel;
    }

    /**
     * returns the device file of the partition
     * @return the device file of the partition
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns <code>true</code>, if the partition is bootable,
     * <cocde>false</code> otherwise
     * @return <code>true</code>, if the partition is bootable,
     * <cocde>false</code> otherwise
     */
    public boolean isBootable() {
        return bootable;
    }

    /**
     * returns the start sector of the partition
     * @return the start sector of the partition
     */
    public long getStartSector() {
        return startSector;
    }

    /**
     * returns the end sector of the partition
     * @return the end sector of the partition
     */
    public long getEndSector() {
        return endSector;
    }

    /**
     * returns the number of sectors of this partition
     * @return the number of sectors of this partition
     */
    public long getSectorCount() {
        return endSector - startSector + 1;
    }

    /**
     * returns the partition type ID
     * @return the partition type ID
     */
    public String getTypeID() {
        return typeID;
    }

    /**
     * returns the partition type description
     * @return the partition type description
     */
    public String getTypeDescription() {
        return typeDescription;
    }

    /**
     * returns the label of the partition
     * @return the label of the partition
     */
    public String getLabel() {
        return label;
    }

    /**
     * returns a list of mount paths of this partition
     * @return a list of mount paths of this partition
     * @throws DBusException if a dbus exception occurs
     */
    public List<String> getMountPaths() throws DBusException {
        return DbusTools.getStringListProperty(device, "DeviceMountPaths");
    }

    /**
     * mounts this partition via dbus/udisks
     * @return the mount point
     * @throws DBusException if a dbus exception occurs
     */
    public String mount() throws DBusException {
        List<String> mountPaths = getMountPaths();
        if (mountPaths.isEmpty()) {
            return DbusTools.getDevice(device).FilesystemMount(
                    "auto", new ArrayList<String>());
        } else {
            String mountPath = mountPaths.get(0);
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                        new Object[]{device, mountPath});
            }
            return mountPath;
        }
    }

    /**
     * umounts this partition via dbus/udisks
     * @throws DBusException if a dbus exception occurs
     */
    public void umount() throws DBusException {
        DbusTools.getDevice(device).FilesystemUnmount(new ArrayList<String>());
    }

    /**
     * returns <code>true</code>, if this partition is a Debian Live system
     * partition, <code>false</code> otherwise
     * @return <code>true</code>, if this partition is a Debian Live system
     * partition, <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public boolean isSystemPartition() throws DBusException {
        if (isSystemPartition == null) {
            isSystemPartition = false;
            LOGGER.log(Level.FINEST, "checking partition {0}", device);
            LOGGER.log(Level.FINEST, "partition label: \"{0}\"", label);
            if (systemPartitionLabel.equals(label)) {
                // mount partition if not already mounted
                String mountPath = null;
                boolean tmpMount = false;
                List<String> mountPaths = getMountPaths();
                if (mountPaths.isEmpty()) {
                    mountPath = mount();
                    tmpMount = true;
                } else {
                    mountPath = mountPaths.get(0);
                    if (LOGGER.isLoggable(Level.FINEST)) {
                        LOGGER.log(Level.FINEST, "{0} already mounted at {1}",
                                new Object[]{device, mountPath});
                    }
                }

                // check partition file structure
                LOGGER.log(Level.FINEST,
                        "checking file structure on partition {0}", device);
                File squashFS = new File(
                        mountPath + "/live/filesystem.squashfs");
                if (squashFS.exists()) {
                    LOGGER.log(Level.INFO,
                            "found squashfs on partition {0}", device);
                    // ok, now we are pretty sure that this partition is a
                    // Debian Live system partition
                    isSystemPartition = true;
                } else {
                    LOGGER.log(Level.INFO, "{0} does not exist", squashFS);
                }

                // cleanup
                if (tmpMount) {
                    boolean success = false;
                    for (int i = 0; !success && (i < 10); i++) {
                        try {
                            umount();
                            success = true;
                        } catch (DBusExecutionException ex) {
                            handleUmountException(ex);
                        } catch (DBusException ex) {
                            handleUmountException(ex);
                        }
                    }
                }
            }
        }
        return isSystemPartition;
    }

    /**
     * returns <code>true</code>, if this partition is a Debian Live persistency
     * partition, <code>false</code> otherwise
     * @return <code>true</code>, if this partition is a Debian Live persistency
     * partition, <code>false</code> otherwise
     */
    public boolean isPersistencyPartition() {
        return label.equals("live-rw");
    }

    /**
     * returns the file system on this partition
     * @return the file system on this partition
     */
    public String getFileSystem() {
        return fileSystem;
    }

    private void handleUmountException(Exception ex) {
        LOGGER.log(Level.WARNING, "", ex);
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex2) {
            LOGGER.log(Level.SEVERE, "", ex2);
        }
    }
}
