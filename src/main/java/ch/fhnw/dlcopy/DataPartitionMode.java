package ch.fhnw.dlcopy;

/**
 * the different operating modes of a data partition
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public enum DataPartitionMode {

    /**
     * the data partition is read and written
     *//**
     * the data partition is read and written
     */
    READ_WRITE,

    /**
     * the data partition is only read, all write operations go to an ephemeral
     * tmpfs
     */
    READ_ONLY,

    /**
     * the data partition is not used at all
     */
    NOT_USED
}
