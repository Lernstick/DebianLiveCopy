package ch.fhnw.dlcopy;

/**
 * A harddisk
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class Harddisk extends StorageDevice {

    /**
     * Creates a new Harddisk
     * @param vendor the vendor
     * @param model the model
     * @param revision the revision of the device
     * @param serial the serial of the device
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     * @param systemPartitionLabel the (expected) system partition label
     * @param systemSize the size of the currently running Debian Live system
     */
    public Harddisk(String vendor, String model, String revision, String serial,
            String device, long size, String systemPartitionLabel,
            long systemSize) {
        super(device, vendor, model, revision, serial, size,
                systemPartitionLabel, systemSize);
    }
}
