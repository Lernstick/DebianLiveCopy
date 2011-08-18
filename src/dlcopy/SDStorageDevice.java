package dlcopy;

/**
 * A SD storage device
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class SDStorageDevice extends StorageDevice {

    /**
     * Creates a new SDStorageDevice
     * @param vendor the vendor
     * @param model the storage device model
     * @param revision the revision of the device
     * @param serial the serial of the device
     * @param device the device node (e.g. /dev/sdb)
     * @param size the size in Byte
     * @param blockSize the block size of the device given in byte
     * @param systemPartitionLabel the (expected) system partition label
     */
    public SDStorageDevice(String vendor, String model, String revision,
            String serial, String device, long size, int blockSize,
            String systemPartitionLabel) {
        super(device, vendor, model, revision, serial, size, blockSize,
                systemPartitionLabel);
    }
}
