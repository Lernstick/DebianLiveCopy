package dlcopy;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public abstract class StorageDevice implements Comparable<StorageDevice> {

    private static final Logger LOGGER =
            Logger.getLogger(StorageDevice.class.getName());
    private final DBusConnection dbusSystemConnection;
    private final String device;
    private final String revision;
    private final long size;
    private final int blockSize;
    private List<Partition> partitions;

    /**
     * Creates a new StorageDevice
     * @param dbusSystemConnection the dbus system connection
     * @param device the device node (e.g. /dev/sdb)
     * @param revision the revision of the device
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte
     */
    public StorageDevice(DBusConnection dbusSystemConnection, String device,
            String revision, long size, int blockSize) {
        this.dbusSystemConnection = dbusSystemConnection;
        this.device = device;
        this.revision = revision;
        this.size = size;
        this.blockSize = blockSize;
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
     * returns the device node of the USB storage device
     * @return the device node of the USB storage device
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns the size of the USB storage device (in Byte)
     * @return the size of the USB storage device (in Byte)
     */
    public long getSize() {
        return size;
    }

    /**
     * returns the revision of this storage device
     * @return the revision of this storage device
     */
    public String getRevision() {
        return revision;
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
            processExecutor.executeProcess(true, "sfdisk", "-uS", "-l", device);
            List<String> lines = processExecutor.getStdOut();

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

    private Partition parsePartition(Matcher matcher, boolean bootable) {
        try {
            String partitionDevice = matcher.group(1);
            String startString = matcher.group(2);
            long start = Long.parseLong(startString);
            String endString = matcher.group(3);
            long end = Long.parseLong(endString);
            String id = matcher.group(4);
            String description = matcher.group(5);
            DBus.Properties deviceProperties =
                    dbusSystemConnection.getRemoteObject(
                    "org.freedesktop.UDisks",
                    "/org/freedesktop/UDisks/devices/" + partitionDevice,
                    DBus.Properties.class);
            String idLabel = deviceProperties.Get(
                    "org.freedesktop.UDisks", "IdLabel");
            return new Partition(partitionDevice,
                    bootable, start, end, id, description, idLabel);
        } catch (NumberFormatException numberFormatException) {
            LOGGER.log(Level.WARNING, "", numberFormatException);
        } catch (DBusException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }
        return null;
    }
}
