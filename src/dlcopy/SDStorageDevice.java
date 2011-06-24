package dlcopy;

/**
 * A SD storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class SDStorageDevice extends StorageDevice {

    private final String name;

    /**
     * Creates a new SDStorageDevice
     * @param vendor the vendor
     * @param model the model
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     */
    public SDStorageDevice(String name, String device, long size) {
        super(device, size);
        this.name = name;
    }

    @Override
    public String toString() {
        return name + " " + ", /dev/" + device + ", " +
                DLCopy.getDataVolumeString(size, 1);
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
            return otherDevice.getName().equals(name) &&
                    otherDevice.getDevice().equals(device) &&
                    otherDevice.getSize() == size;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (this.name != null ? this.name.hashCode() : 0);
        hash = 41 * hash + (this.device != null ? this.device.hashCode() : 0);
        hash = 41 * hash + (int) (this.size ^ (this.size >>> 32));
        return hash;
    }
}
