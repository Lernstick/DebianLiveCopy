package dlcopy;

/**
 * A harddisk
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class Harddisk extends StorageDevice {

    private final String vendor;
    private final String model;

    /**
     * Creates a new Harddisk
     * @param vendor the vendor
     * @param model the model
     * @param revision the revision of the device
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte 
     */
    public Harddisk(String vendor, String model, String revision, String device,
            long size, int blockSize) {
        super(device, revision, size, blockSize);
        this.vendor = vendor;
        this.model = model;
    }

    @Override
    public String toString() {
        return vendor + " " + model + ", " + getDevice() + ", "
                + DLCopy.getDataVolumeString(getSize(), 1);
    }

    /**
     * returns the vendor of the harddisk
     * @return the vendor of the harddisk
     */
    public String getVendor() {
        return vendor;
    }

    /**
     * returns the model of the harddisk
     * @return the model of the harddisk
     */
    public String getModel() {
        return model;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Harddisk) {
            Harddisk otherDevice = (Harddisk) other;
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
