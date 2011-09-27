package dlcopy;

import dlcopy.tools.DbusTools;
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
    private final int number;
    private final long partitionOffset;
    private final long partitionSize;
    private final String idLabel;
    private final String idType;
    private final String partitionType;
    private final String systemPartitionLabel;
    private Boolean isSystemPartition;

    /**
     * creates a new Partition
     * @param device the device of the partition (e.g. "sda1")
     * @param number the number of the partition (must correspond with
     * <code>device</code>)
     * @param partitionOffset the offset (start) of the partition
     * @param partitionSize the size of the partition
     * @param partitionType the partition type
     * @param idLabel the label of the partition
     * @param idType the ID type (type of the file system)
     * @param systemPartitionLabel the (expected) system partition label
     */
    public Partition(String device, int number, long partitionOffset,
            long partitionSize, String partitionType, String idLabel,
            String idType, String systemPartitionLabel) {
        this.device = device;
        this.number = number;
        this.partitionOffset = partitionOffset;
        this.partitionSize = partitionSize;
        this.idLabel = idLabel;
        this.idType = idType;
        this.partitionType = partitionType;
        this.systemPartitionLabel = systemPartitionLabel;
    }

    /**
     * returns the device of the partition, e.g. "sda1"
     * @return the device of the partition, e.g. "sda1"
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns the partition number
     * @return the partition number
     */
    public int getNumber() {
        return number;
    }

    /**
     * returns the start sector of the partition
     * @return the start sector of the partition
     */
    public long getPartitionOffset() {
        return partitionOffset;
    }

    /**
     * returns the size of this partition
     * @return the size of this partition
     */
    public long getPartitionSize() {
        return partitionSize;
    }

    /**
     * returns the label of the partition
     * @return the label of the partition
     */
    public String getIdLabel() {
        return idLabel;
    }

    /**
     * returns the ID type of the partition
     * @return the ID type of the partition
     */
    public String getIdType() {
        return idType;
    }

    /**
     * returns the partition type
     * @return the partition type
     */
    public String getPartitionType() {
        return partitionType;
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
            LOGGER.log(Level.FINEST, "partition label: \"{0}\"", idLabel);
            if (systemPartitionLabel.equals(idLabel)) {
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
        return idLabel.equals("live-rw");
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
