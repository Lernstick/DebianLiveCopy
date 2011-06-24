package dlcopy;

/**
 * A storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class StorageDevice {

    protected final String device;
    protected final long size;

    /**
     * Creates a new StorageDevice
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     */
    public StorageDevice(String device, long size) {
        this.device = device;
        this.size = size;
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

    @Override
    public boolean equals(Object other) {
        if (other instanceof StorageDevice) {
            StorageDevice otherDevice = (StorageDevice) other;
            return otherDevice.getDevice().equals(device) &&
                    otherDevice.getSize() == size;
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
}
