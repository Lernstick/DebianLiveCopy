package dlcopy;

import dlcopy.tools.ProcessExecutor;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public abstract class StorageDevice implements Comparable<StorageDevice> {

    private static final Logger LOGGER =
            Logger.getLogger(StorageDevice.class.getName());
    private final String device;
    private final String vendor;
    private final String model;
    private final String revision;
    private final String serial;
    private final long size;
    private final int blockSize;
    private final String systemPartitionLabel;
    private final long systemSize;
    private List<Partition> partitions;
    private Boolean canBeUpgraded;
    private String noUpgradeReason;
    private Partition systemPartition;
    private Partition dataPartition;

    /**
     * Creates a new StorageDevice
     * @param device the device node (e.g. /dev/sdb)
     * @param vendor the storage device vendor
     * @param model the storage device model
     * @param revision the revision of the storage device
     * @param serial the serial of the storage device
     * @param size the size of the storage device in Byte
     * @param blockSize the block size of the storage device given in byte
     * @param systemPartitionLabel the (expected) system partition label
     * @param systemSize the size of the currently running Debian Live system
     */
    public StorageDevice(String device, String vendor, String model,
            String revision, String serial, long size, int blockSize,
            String systemPartitionLabel, long systemSize) {
        this.device = device;
        this.vendor = vendor;
        this.model = model;
        this.revision = revision;
        this.serial = serial;
        this.size = size;
        this.blockSize = blockSize;
        this.systemPartitionLabel = systemPartitionLabel;
        this.systemSize = systemSize;
    }

    @Override
    public int compareTo(StorageDevice other) {
        return device.compareTo(other.getDevice());
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof StorageDevice) {
            StorageDevice otherDevice = (StorageDevice) other;
            return otherDevice.getDevice().equals(device)
                    && otherDevice.getSize() == size;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (this.device != null ? this.device.hashCode() : 0);
        hash = 41 * hash + (int) (this.size ^ (this.size >>> 32));
        return hash;
    }

    @Override
    public String toString() {
        return device + ", " + DLCopy.getDataVolumeString(size, 1);
    }

    /**
     * returns the device node of the storage device
     * @return the device node of the storage device
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns the storage device vendor
     * @return the storage device vendor
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * returns the storage device model
     * @return the storage device model
     */
    public String getModel() {
        return model;
    }

    /**
     * returns the revision of this storage device
     * @return the revision of this storage device
     */
    public String getRevision() {
        return revision;
    }

    /**
     * returns the serial of the storage device
     * @return the serial of the storage device
     */
    public String getSerial() {
        return serial;
    }

    /**
     * returns the size of the storage device (in Byte)
     * @return the size of the storage device (in Byte)
     */
    public long getSize() {
        return size;
    }

    /**
     * @return the blockSize
     */
    public int getBlockSize() {
        return blockSize;
    }

    /**
     * returns the list of partitions of this storage device
     * @return the list of partitions of this storage device
     */
    public synchronized List<Partition> getPartitions() {
        if (partitions == null) {
            // create new list
            partitions = new ArrayList<Partition>();

            // call sfdisk to get partition info
            ProcessExecutor processExecutor = new ProcessExecutor();
            processExecutor.executeProcess(true, true, 
                    "sfdisk", "-uS", "-l", device);
            List<String> lines = processExecutor.getStdOutList();

            /**
             * parse partition lines, example:
             *
             *    Device Boot    Start       End   #sectors  Id  System
             * /dev/sda1             1 1241824499 1241824499  83  Linux
             * /dev/sda2   * 1241824500 1250258624    8434125   c  W95 FAT32 (LBA)
             * /dev/sda3             0         -          0   0  Empty
             * /dev/sda4             0         -          0   0  Empty
             */
            Pattern bootablePartitionPattern = Pattern.compile(
                    "/dev/(\\w+)\\s+\\*\\s+(\\w+)\\s+(\\w+)\\s+\\w+\\s+(\\w+)\\s+(.*)");
            Pattern nonBootablePartitionPattern = Pattern.compile(
                    "/dev/(\\w+)\\s+(\\w+)\\s+(\\w+)\\s+\\w+\\s+(\\w+)\\s+(.*)");
            for (String line : lines) {
                Matcher bootableMatcher =
                        bootablePartitionPattern.matcher(line);
                if (bootableMatcher.matches()) {
                    partitions.add(parsePartition(bootableMatcher, true));
                }
                Matcher nonBootableMatcher =
                        nonBootablePartitionPattern.matcher(line);
                if (nonBootableMatcher.matches()) {
                    partitions.add(parsePartition(nonBootableMatcher, false));
                }
            }
        }
        return partitions;
    }

    /**
     * returns <code>true</code>, if this storage device can be upgraded,
     * <code>false</code> otherwise
     * @return <code>true</code>, if this storage device can be upgraded,
     * <code>false</code> otherwise
     * @throws DBusException if a dbus exception occurs
     */
    public synchronized boolean canBeUpgraded() throws DBusException {
        // lazy initialization of canBeUpgraded
        if (canBeUpgraded == null) {
            canBeUpgraded = false;
            boolean systemPartitionFound = false;
            boolean sizeFits = false;
            for (Partition partition : getPartitions()) {
                if (partition.isSystemPartition()) {
                    systemPartitionFound = true;
                    long partitionSize = blockSize
                            * partition.getSectorCount();
                    sizeFits = partitionSize > (systemSize / 1.1f);
                    if (sizeFits) {
                        systemPartition = partition;
                        canBeUpgraded = true;
                        break; // for
                    }
                }
            }
            if (!sizeFits) {
                if (systemPartitionFound) {
                    noUpgradeReason = DLCopy.STRINGS.getString(
                            "System_Partition_Too_Small");
                } else {
                    noUpgradeReason = DLCopy.STRINGS.getString(
                            "No_System_Partition_Found");
                }
            }
        }
        return canBeUpgraded;
    }

    /**
     * returns the reason why this storage device can not be upgraded
     * @return the noUpgradeReason
     */
    public String getNoUpgradeReason() {
        return noUpgradeReason;
    }

    /**
     * returns the system partition of this storage device
     * @return the system partition of this storage device
     */
    public synchronized Partition getSystemPartition() {
        if (systemPartition == null) {
            try {
                canBeUpgraded();
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        }
        return systemPartition;
    }

    /**
     * returns the data partition of this storage device
     * @return the data partition of this storage device
     */
    public Partition getDataPartition() {
        return dataPartition;
    }

    private Partition parsePartition(Matcher matcher, boolean bootable) {
        try {
            String partitionDevice = matcher.group(1);
            String startString = matcher.group(2);
            long start = Long.parseLong(startString);
            String endString = matcher.group(3);
            long end = Long.parseLong(endString);
            String id = matcher.group(4);
            String description = matcher.group(5);
            String idLabel = DbusTools.getStringProperty(
                    partitionDevice, "IdLabel");
            String idType = DbusTools.getStringProperty(
                    partitionDevice, "IdType");
            Partition partition = new Partition(partitionDevice, bootable,
                    start, end, id, description, idLabel, idType,
                    systemPartitionLabel);
            if ("live-rw".equals(idLabel)) {
                dataPartition = partition;
            }
            return partition;
        } catch (NumberFormatException numberFormatException) {
            LOGGER.log(Level.WARNING, "", numberFormatException);
        } catch (DBusException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }
        return null;
    }
}
