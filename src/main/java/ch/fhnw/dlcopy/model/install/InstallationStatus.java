package ch.fhnw.dlcopy.model.install;

/**
 * Represents the detail states an installation can be in
 */
public enum InstallationStatus {
    CREATE_FILE_SYSTEMS,
    OVERWRITE_DATA_PARTITION_WITH_RANDOM_DATA,
    COPY_FILES,
    COPY_PERSISTENCY_PARTITION,
    UNMOUNTING,
    WRITE_BOOT_SECTOR
}
