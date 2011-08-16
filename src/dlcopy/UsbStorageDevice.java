package dlcopy;

import org.freedesktop.dbus.DBusConnection;

/**
 * A USB storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class UsbStorageDevice extends StorageDevice {

    private final String vendor;
    private final String model;

    /**
     * Creates a new UsbStorageDevice
     * @param dbusSystemConnection the dbus system connection
     * @param vendor the vendor
     * @param model the model
     * @param revision the revision of the device
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte 
     */
    public UsbStorageDevice(DBusConnection dbusSystemConnection, String vendor,
            String model, String revision, String device, long size,
            int blockSize) {
        super(dbusSystemConnection, device, revision, size, blockSize);
        this.vendor = vendor;
        this.model = model;
    }

    @Override
    public String toString() {
        return vendor + " " + model + ", " + getDevice() + ", "
                + DLCopy.getDataVolumeString(getSize(), 1);
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
            return otherDevice.getVendor().equals(vendor)
                    && otherDevice.getModel().equals(model)
                    && otherDevice.getDevice().equals(getDevice())
                    && otherDevice.getSize() == getSize();
        }
        return false;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 41 * hash + (vendor != null ? vendor.hashCode() : 0);
        hash = 41 * hash + (model != null ? model.hashCode() : 0);
        hash = 41 * hash + (getDevice() != null ? getDevice().hashCode() : 0);
        hash = 41 * hash + (int) (getSize() ^ (getSize() >>> 32));
        return hash;
    }
}
