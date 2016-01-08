package ch.fhnw.dlcopy;

/**
 * Holds information about partition sizes
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class PartitionSizes {

    private final int exchangeMB;
    private final int persistenceMB;

    public PartitionSizes(int exchangeMB, int persistenceMB) {
        this.exchangeMB = exchangeMB;
        this.persistenceMB = persistenceMB;
    }

    /**
     * returns the size of the exchange partition (in MiB)
     *
     * @return the size of the exchange partition (in MiB)
     */
    public int getExchangeMB() {
        return exchangeMB;
    }

    /**
     * returns the size of the persistence partition (in MiB)
     *
     * @return the size of the persistence partition (in MiB)
     */
    public int getPersistenceMB() {
        return persistenceMB;
    }
}
