package dlcopy;

/**
 * A storage device partition
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class Partition {

    private final String device;
    private final boolean bootable;
    private final long start;
    private final long end;
    private final String typeID;
    private final String typeDescription;
    private final String label;

    /**
     * creates a new Partition
     * @param device the device file of the partition (e.g. sda1)
     * @param bootable if the partition is bootable
     * @param start the start of the partition (given in MB)
     * @param end the end of the partition (given in MB)
     * @param typeID the partition type ID
     * @param typeDescription the partition type description
     * @param label the label of the partition
     */
    public Partition(String device, boolean bootable, long start, long end,
            String typeID, String typeDescription, String label) {
        this.device = device;
        this.bootable = bootable;
        this.start = start;
        this.end = end;
        this.typeID = typeID;
        this.typeDescription = typeDescription;
        this.label = label;
    }

    /**
     * returns the device file of the partition
     * @return the device file of the partition
     */
    public String getDevice() {
        return device;
    }

    /**
     * returns <code>true</code>, if the partition is bootable,
     * <cocde>false</code> otherwise
     * @return <code>true</code>, if the partition is bootable,
     * <cocde>false</code> otherwise
     */
    public boolean isBootable() {
        return bootable;
    }

    /**
     * returns the start of the partition (given in MB)
     * @return the start of the partition (given in MB)
     */
    public long getStart() {
        return start;
    }

    /**
     * returns the end of the partition (given in MB)
     * @return the end of the partition (given in MB)
     */
    public long getEnd() {
        return end;
    }

    /**
     * returns the partition type ID
     * @return the partition type ID
     */
    public String getTypeID() {
        return typeID;
    }

    /**
     * returns the partition type description
     * @return the partition type description
     */
    public String getTypeDescription() {
        return typeDescription;
    }

    /**
     * returns the label of the partition
     * @return the label of the partition
     */
    public String getLabel() {
        return label;
    }
}
