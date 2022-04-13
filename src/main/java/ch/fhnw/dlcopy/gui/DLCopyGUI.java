package ch.fhnw.dlcopy.gui;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.nio.file.Path;
import java.util.List;

/**
 * The interface a GUI for DLCopy has to implement
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public interface DLCopyGUI {

    /**
     * shows the user interface for showing the installation progress
     */
    public default void showInstallProgress() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the info about the currently installed device
     *
     * @param storageDevice the StorageDevice to be installed
     */
    public default void installingDeviceStarted(StorageDevice storageDevice) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for creating file systems of a running
     * installation
     */
    public default void showInstallCreatingFileSystems() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for overwriting the data partition with random
     * data
     *
     * @param done the amount of data that is already overwritten
     * @param size the size of the data partition
     */
    public default void showInstallOverwritingDataPartitionWithRandomData(
            long done, long size) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for copying files of a running installation
     *
     * @param fileCopier the FileCopier used for copying files of an
     * installation
     */
    public default void showInstallFileCopy(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for copying the persistency partition during
     * installation
     *
     * @param installer the Installer currently in use
     * @param copyScript the shell script for the copy operation
     * @param sourcePath the path to the source directory
     */
    public default void showInstallPersistencyCopy(
            Installer installer, String copyScript, String sourcePath) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the current output line of the copy process
     *
     * @param line the current output line of the copy process
     */
    public default void setInstallCopyLine(String line) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for unmouting file systems during installation
     */
    public default void showInstallUnmounting() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for writing the boot sector of a running
     * installation
     */
    public default void showInstallWritingBootSector() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when installing of a StorageDevice finished
     *
     * @param errorMessage the error message or <code>null</code> if there was
     * no error
     * @param autoNumberStart the new auto numbering start value
     */
    public default void installingDeviceFinished(
            String errorMessage, int autoNumberStart) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when installing all selected StorageDevices finished
     */
    public default void installingListFinished() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for creating file systems of a running upgrade
     */
    public default void showUpgradeCreatingFileSystems() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for copying files of a running upgrade
     *
     * @param fileCopier the FileCopier used for copying files of an upgrade
     */
    public default void showUpgradeFileCopy(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for unmouting file systems during upgrade
     */
    public default void showUpgradeUnmounting() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for writing the boot sector of a running upgrade
     */
    public default void showUpgradeWritingBootSector() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for backing up during a running upgrade
     */
    public default void showUpgradeBackup() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for backing up the exchange partition during a
     * running upgrade
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public default void showUpgradeBackupExchangePartition(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for initializing the restore operation during a
     * running upgrade
     */
    public default void showUpgradeRestoreInit() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for running the restore operation during a
     * running upgrade
     */
    public default void showUpgradeRestoreRunning() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for backing up the exchange partition during a
     * running upgrade
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public default void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for resetting the data partition up during a
     * running upgrade
     */
    public default void showUpgradeDataPartitionReset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for changing partition sizes up during a running
     * upgrade
     */
    public default void showUpgradeChangingPartitionSizes() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the progress of the backup/restore process when upgrading a system
     *
     * @param progressInfo the backup progress message (e.g. "x of y files")
     */
    public default void setUpgradeBackupProgress(String progressInfo) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the currently processed file of the backup/restore process when
     * upgrading a system
     *
     * @param filename the name of the currently processed file
     */
    public default void setUpgradeBackupFilename(String filename) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the duration of the currently running backup/restore process
     *
     * @param duration the duration of the currently running backup/restore
     * process
     */
    public default void setUpgradeBackupDuration(long duration) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for resetting the system partition up during a
     * running upgrade
     */
    public default void showUpgradeSystemPartitionReset() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the info about the currently upgraded device
     *
     * @param storageDevice the StorageDevice to be upgraded
     */
    public default void upgradingDeviceStarted(StorageDevice storageDevice) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when upgrading of a StorageDevice finished
     *
     * @param errorMessage the error message or <code>null</code> if there was
     * no error
     */
    public default void upgradingDeviceFinished(String errorMessage) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when upgrading all selected StorageDevices finished
     */
    public default void upgradingListFinished() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows a message regarding the ISO creation progress
     *
     * @param message the message to show
     */
    public default void showIsoProgressMessage(String message) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows a message and progress percentage value regarding the ISO creation
     * progress
     *
     * @param message the message to show
     * @param value the progress given in percent
     */
    public default void showIsoProgressMessage(String message, int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when creating the ISO file finished
     *
     * @param path the path to the created ISO
     * @param success if creating the ISO was successfull
     */
    public default void isoCreationFinished(String path, boolean success) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows a message regarding the SquashFS creation progress
     *
     * @param message the message to show
     */
    public default void showSquashFSProgressMessage(String message) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows a message and progress percentage value regarding the SquashFS creation
     * progress
     *
     * @param message the message to show
     * @param value the progress given in percent
     */
    public default void showSquashFSProgressMessage(String message, int value) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when creating the SquashFS file finished
     *
     * @param path the path to the created SquashFS
     * @param success if creating the SquashFS was successfull
     */
    public default void squashFSCreationFinished(String path, boolean success) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for visualization of the reset progress
     */
    public default void showResetProgress() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * sets the info about the currently reset device
     *
     * @param storageDevice the StorageDevice to be reset
     */
    public default void resettingDeviceStarted(StorageDevice storageDevice) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for printing documents during reset
     */
    public default void showPrintingDocuments() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user a list of documents that can be selected to print
     *
     * @param type the type of documents to select
     * @param mountPath the path where the exchange partition is mounted
     * @param documents the list of documents to print
     * @return the list of documents selected for printing
     */
    public default List<Path> selectDocumentsToPrint(
            String type, String mountPath, List<Path> documents) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for backing up the exchange partition during a
     * running reset
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public default void showResetBackup(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for formatting the exchange partition during
     * reset
     */
    public default void showResetFormattingExchangePartition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for formatting the data partition during reset
     */
    public default void showResetFormattingDataPartition() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for removing files during reset
     */
    public default void showResetRemovingFiles() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows the user interface for restoring files during a running reset
     *
     * @param fileCopier the FileCopier used for restoring files during a
     * running reset
     */
    public default void showResetRestore(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * called when resetting all selected StorageDevices finished
     *
     * @param success if resetting all storage devices was successful
     */
    public default void resettingFinished(boolean success) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows an error message
     *
     * @param errorMessage the error message
     */
    public default void showErrorMessage(String errorMessage) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * shows a confirm dialog
     *
     * @param title the dialog title
     * @param message the message to display
     * @return true, if the user confirmed, false otherwise
     */
    public default boolean showConfirmDialog(String title, String message) {
        throw new UnsupportedOperationException("Not supported yet.");
    }
}
