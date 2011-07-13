package dlcopy;

/**
 * A SD storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class SDStorageDevice extends StorageDevice {

    private final String name;

    /**
     * Creates a new SDStorageDevice
     * @param name the name of the device
     * @param revision the revision of the device
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte 
     */
    public SDStorageDevice(String name, String revision, String device,
            long size, int blockSize) {
        super(device, revision, size, blockSize);
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " " + ", " + getDevice() + ", "
                + DLCopy.getDataVolumeString(getSize(), 1);
    }

    /**
     * returns the name of the SD storage device
     * @return the name of the SD storage device
     */
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SDStorageDevice) {
            SDStorageDevice otherDevice = (SDStorageDevice) other;
            return otherDevice.getName().equals(name)
                    && otherDevice.getDevice().equals(getDevice())
                    && otherDevice.getSize() == getSize();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (name != null ? name.hashCode() : 0);
        hash = 41 * hash + (getDevice() != null ? getDevice().hashCode() : 0);
        hash = 41 * hash + (int) (getSize() ^ (getSize() >>> 32));
        return hash;
    }
}
