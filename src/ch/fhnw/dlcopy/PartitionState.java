package ch.fhnw.dlcopy;

/**
 * the known partition states for a drive
 */
public enum PartitionState {
    /**
     * the drive is too small
     */
    TOO_SMALL,
    /**
     * the drive is so small that only a system partition can be created
     */
    ONLY_SYSTEM,
    /**
     * the system is so small that only a system and persistence partition can
     * be created
     */
    PERSISTENCE,
    /**
     * the system is large enough to create all partition scenarios
     */
    EXCHANGE
}
