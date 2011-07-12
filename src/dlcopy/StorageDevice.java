package dlcopy;

/**
 * A storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class StorageDevice implements Comparable<StorageDevice> {

    /**
     * the device file path
     */
    protected final String device;
    /**
     * the revision of this device
     */
    protected final String revision;
    /**
     * the storage size of the device given in byte
     */
    protected final long size;
    /**
     * the block size of the device given in byte
     */
    private final int blockSize;

    /**
     * Creates a new StorageDevice
     * @param device the device node (e.g. /dev/sdb)
     * @param revision the revision of the device
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte
     */
    public StorageDevice(String device, String revision, long size,
            int blockSize) {
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
        return "/dev/" + device + ", " + DLCopy.getDataVolumeString(size, 1);
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
     * @return the blockSize
     */
    public int getBlockSize() {
        return blockSize;
    }
}
