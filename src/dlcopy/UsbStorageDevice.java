package dlcopy;

/**
 * A USB storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class UsbStorageDevice extends StorageDevice {

    private final String vendor;
    private final String model;

    /**
     * Creates a new UsbStorageDevice
     * @param vendor the vendor
     * @param model the model
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     */
    public UsbStorageDevice(String vendor, String model, String device,
            long size) {
        super(device, size);
        this.vendor = vendor;
        this.model = model;
    }

    @Override
    public String toString() {
        return vendor + " " + model + ", /dev/" + device + ", " +
                DLCopy.getDataVolumeString(size, 1);
    }

    /**
     * returns the vendor of the USB storage device
     * @return the vendor of the USB storage device
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * returns the model of the USB storage device
     * @return the model of the USB storage device
     */
    public String getModel() {
        return model;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof UsbStorageDevice) {
            UsbStorageDevice otherDevice = (UsbStorageDevice) other;
            return otherDevice.getVendor().equals(vendor) &&
                    otherDevice.getModel().equals(model) &&
                    otherDevice.getDevice().equals(device) &&
                    otherDevice.getSize() == size;
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (this.vendor != null ? this.vendor.hashCode() : 0);
        hash = 41 * hash + (this.model != null ? this.model.hashCode() : 0);
        hash = 41 * hash + (this.device != null ? this.device.hashCode() : 0);
        hash = 41 * hash + (int) (this.size ^ (this.size >>> 32));
        return hash;
    }
}
