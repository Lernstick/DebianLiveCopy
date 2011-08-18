package dlcopy;

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
     * @param blockSize the block size of the device given in byte
     * @param systemPartitionLabel the (expected) system partition label
     */
    public Harddisk(String vendor, String model, String revision, String serial,
            String device, long size, int blockSize,
            String systemPartitionLabel) {
        super(device, vendor, model, revision, serial, size, blockSize,
                systemPartitionLabel);
    }
}
