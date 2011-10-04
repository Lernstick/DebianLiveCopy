package dlcopy;

import dlcopy.tools.DbusTools;
import dlcopy.tools.ProcessExecutor;
import java.util.ArrayList;
import java.util.Collections;
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
    private final String systemPartitionLabel;
    private final long systemSize;
    private List<Partition> partitions;
    private Boolean canBeUpgraded;
    private boolean needsRepartitioning;
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
     * @param systemPartitionLabel the (expected) system partition label
     * @param systemSize the size of the currently running Debian Live system
     */
    public StorageDevice(String device, String vendor, String model,
            String revision, String serial, long size,
            String systemPartitionLabel, long systemSize) {
        this.device = device;
        this.vendor = vendor;
        this.model = model;
        this.revision = revision;
        this.serial = serial;
        this.size = size;
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
     * returns the device node of the storage device (e.g. /dev/sdb)
     * @return the device node of the storage device (e.g. /dev/sdb)
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
     * returns the list of partitions of this storage device
     * @return the list of partitions of this storage device
     */
    public synchronized List<Partition> getPartitions() {
        if (partitions == null) {
            // create new list
            partitions = new ArrayList<Partition>();

            // call udisks to get partition info
            ProcessExecutor processExecutor = new ProcessExecutor();
            LOGGER.log(Level.INFO,
                    "calling \"udisks --enumerate\" to get the partitions of {0}", 
                    device);
            processExecutor.executeProcess(true, true, "udisks", "--enumerate");
            List<String> lines = processExecutor.getStdOutList();
            Collections.sort(lines);

            /**
             * parse parted output, example:
             * /org/freedesktop/UDisks/devices/sdb
             * /org/freedesktop/UDisks/devices/sda1
             * /org/freedesktop/UDisks/devices/sda2
             * /org/freedesktop/UDisks/devices/sr0
             * /org/freedesktop/UDisks/devices/sdb2
             * /org/freedesktop/UDisks/devices/loop0
             * /org/freedesktop/UDisks/devices/loop1
             * /org/freedesktop/UDisks/devices/sdb1
             * /org/freedesktop/UDisks/devices/sda
             */
            // we only want to catch the partition numbers...
            Pattern pattern = Pattern.compile(
                    "/org/freedesktop/UDisks/devices/" + device.substring(5)
                    + "(\\d+)");
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.matches()) {
                    partitions.add(parsePartition(matcher));
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
            long remaining = -1;
            Partition previousPartition = null;
            for (Partition partition : getPartitions()) {
                if (partition.isSystemPartition()) {
                    systemPartition = partition;
                    long partitionSize = partition.getSize();
                    // wild guess: give file system maximum 1% overhead...
                    long saveSystemSize = (long) (systemSize * 1.01);
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO,
                                "systemSize: {0}, saveSystemSize: {1}, size of {2}: {3}",
                                new Object[]{systemSize, saveSystemSize,
                                    partition.getDevice(), partitionSize});
                    }
                    remaining = partitionSize - saveSystemSize;
                    LOGGER.log(Level.FINE, "remaining = {0}", remaining);
                    if (remaining >= 0) {
                        canBeUpgraded = true;
                        break; // for
                    }
                    /**
                     * TODO: more sophisticated checks
                     *  - device with partition gaps
                     *  - expand in both directions
                     *  - ...
                     */
                    // check if repartitioning is possible
                    if ((previousPartition != null)
                            && (!previousPartition.isExtended())) {
                        // right now we can only resize ext partitions
                        if (previousPartition.hasExtendedFilesystem()) {
                            long usableSpace =
                                    previousPartition.getUsableSpace();
                            if (usableSpace > Math.abs(remaining)) {
                                canBeUpgraded = true;
                                needsRepartitioning = true;
                                break; // for
                            }
                        }
                    }
                }
                previousPartition = partition;
            }
            if (remaining < 0) {
                if (systemPartition == null) {
                    noUpgradeReason = DLCopy.STRINGS.getString(
                            "No_System_Partition_Found");
                } else {
                    noUpgradeReason = DLCopy.STRINGS.getString(
                            "System_Partition_Too_Small");
                }
            }
        }
        return canBeUpgraded;
    }

    /**
     * checks if the storage device must be repartitioned when upgraded
     * @return <code>true</code>, if the system must be repartitioned when
     * upgraded, <code>false</code> otherwise
     */
    public boolean needsRepartitioning() {
        return needsRepartitioning;
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

    private Partition parsePartition(Matcher matcher) {
        try {
            String numberString = matcher.group(1);
            String partitionDevice = device.substring(5) + numberString;
            String idLabel = DbusTools.getStringProperty(
                    partitionDevice, "IdLabel");
            String idType = DbusTools.getStringProperty(
                    partitionDevice, "IdType");
            long partitionOffset = DbusTools.getLongProperty(
                    partitionDevice, "PartitionOffset");
            long partitionSize = DbusTools.getLongProperty(
                    partitionDevice, "PartitionSize");
            String partitionType = DbusTools.getStringProperty(
                    partitionDevice, "PartitionType");
            Partition partition = new Partition(partitionDevice,
                    Integer.parseInt(numberString), partitionOffset,
                    partitionSize, partitionType, idLabel, idType,
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
