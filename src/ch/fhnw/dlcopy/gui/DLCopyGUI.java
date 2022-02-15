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
    public void showInstallProgress();

    /**
     * sets the info about the currently installed device
     *
     * @param storageDevice the StorageDevice to be installed
     */
    public void installingDeviceStarted(StorageDevice storageDevice);

    /**
     * shows the user interface for creating file systems of a running
     * installation
     */
    public void showInstallCreatingFileSystems();

    /**
     * shows the user interface for overwriting the data partition with random
     * data
     *
     * @param done the amount of data that is already overwritten
     * @param size the size of the data partition
     */
    public void showInstallOverwritingDataPartitionWithRandomData(
            long done, long size);

    /**
     * shows the user interface for copying files of a running installation
     *
     * @param fileCopier the FileCopier used for copying files of an
     * installation
     */
    public void showInstallFileCopy(FileCopier fileCopier);

    /**
     * shows the user interface for copying the persistency partition during
     * installation
     *
     * @param installer the Installer currently in use
     * @param copyScript the shell script for the copy operation
     * @param sourcePath the path to the source directory
     */
    public void showInstallPersistencyCopy(
            Installer installer, String copyScript, String sourcePath);

    /**
     * sets the current output line of the copy process
     *
     * @param line the current output line of the copy process
     */
    public void setInstallCopyLine(String line);

    /**
     * shows the user interface for unmouting file systems during installation
     */
    public void showInstallUnmounting();

    /**
     * shows the user interface for writing the boot sector of a running
     * installation
     */
    public void showInstallWritingBootSector();

    /**
     * called when installing of a StorageDevice finished
     *
     * @param errorMessage the error message or <code>null</code> if there was
     * no error
     * @param autoNumberStart the new auto numbering start value
     */
    public void installingDeviceFinished(
            String errorMessage, int autoNumberStart);

    /**
     * called when installing all selected StorageDevices finished
     */
    public void installingListFinished();

    /**
     * shows the user interface for creating file systems of a running upgrade
     */
    public void showUpgradeCreatingFileSystems();

    /**
     * shows the user interface for copying files of a running upgrade
     *
     * @param fileCopier the FileCopier used for copying files of an upgrade
     */
    public void showUpgradeFileCopy(FileCopier fileCopier);

    /**
     * shows the user interface for unmouting file systems during upgrade
     */
    public void showUpgradeUnmounting();

    /**
     * shows the user interface for writing the boot sector of a running upgrade
     */
    public void showUpgradeWritingBootSector();

    /**
     * shows the user interface for backing up during a running upgrade
     */
    public void showUpgradeBackup();

    /**
     * shows the user interface for backing up the exchange partition during a
     * running upgrade
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public void showUpgradeBackupExchangePartition(FileCopier fileCopier);

    /**
     * shows the user interface for initializing the restore operation during a
     * running upgrade
     */
    public void showUpgradeRestoreInit();

    /**
     * shows the user interface for running the restore operation during a
     * running upgrade
     */
    public void showUpgradeRestoreRunning();

    /**
     * shows the user interface for backing up the exchange partition during a
     * running upgrade
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier);

    /**
     * shows the user interface for resetting the data partition up during a
     * running upgrade
     */
    public void showUpgradeDataPartitionReset();

    /**
     * shows the user interface for changing partition sizes up during a running
     * upgrade
     */
    public void showUpgradeChangingPartitionSizes();

    /**
     * sets the progress of the backup/restore process when upgrading a system
     *
     * @param progressInfo the backup progress message (e.g. "x of y files")
     */
    public void setUpgradeBackupProgress(String progressInfo);

    /**
     * sets the currently processed file of the backup/restore process when
     * upgrading a system
     *
     * @param filename the name of the currently processed file
     */
    public void setUpgradeBackupFilename(String filename);

    /**
     * sets the duration of the currently running backup/restore process
     *
     * @param duration the duration of the currently running backup/restore
     * process
     */
    public void setUpgradeBackupDuration(long duration);

    /**
     * shows the user interface for resetting the system partition up during a
     * running upgrade
     */
    public void showUpgradeSystemPartitionReset();

    /**
     * sets the info about the currently upgraded device
     *
     * @param storageDevice the StorageDevice to be upgraded
     */
    public void upgradingDeviceStarted(StorageDevice storageDevice);

    /**
     * called when upgrading of a StorageDevice finished
     *
     * @param errorMessage the error message or <code>null</code> if there was
     * no error
     */
    public void upgradingDeviceFinished(String errorMessage);

    /**
     * called when upgrading all selected StorageDevices finished
     */
    public void upgradingListFinished();

    /**
     * shows a message regarding the ISO creation progress
     *
     * @param message the message to show
     */
    public void showIsoProgressMessage(String message);

    /**
     * shows a message and progress percentage value regarding the ISO creation
     * progress
     *
     * @param message the message to show
     * @param value the progress given in percent
     */
    public void showIsoProgressMessage(String message, int value);

    /**
     * called when creating the ISO file finished
     *
     * @param path the path to the created ISO
     * @param success if creating the ISO was successfull
     */
    public void isoCreationFinished(String path, boolean success);

    /**
     * shows a message regarding the SquashFS creation progress
     *
     * @param message the message to show
     */
    public void showSquashFSProgressMessage(String message);

    /**
     * shows a message and progress percentage value regarding the SquashFS creation
     * progress
     *
     * @param message the message to show
     * @param value the progress given in percent
     */
    public void showSquashFSProgressMessage(String message, int value);

    /**
     * called when creating the SquashFS file finished
     *
     * @param path the path to the created SquashFS
     * @param success if creating the SquashFS was successfull
     */
    public void squashFSCreationFinished(String path, boolean success);

    /**
     * shows the user interface for visualization of the reset progress
     */
    public void showResetProgress();

    /**
     * sets the info about the currently reset device
     *
     * @param storageDevice the StorageDevice to be reset
     */
    public void resettingDeviceStarted(StorageDevice storageDevice);

    /**
     * shows the user interface for printing documents during reset
     */
    public void showPrintingDocuments();

    /**
     * shows the user a list of documents that can be selected to print
     *
     * @param type the type of documents to select
     * @param mountPath the path where the exchange partition is mounted
     * @param documents the list of documents to print
     * @return the list of documents selected for printing
     */
    public List<Path> selectDocumentsToPrint(
            String type, String mountPath, List<Path> documents);

    /**
     * shows the user interface for backing up the exchange partition during a
     * running reset
     *
     * @param fileCopier the FileCopier used for backing up the exchange
     * partition
     */
    public void showResetBackup(FileCopier fileCopier);

    /**
     * shows the user interface for formatting the exchange partition during
     * reset
     */
    public void showResetFormattingExchangePartition();

    /**
     * shows the user interface for formatting the data partition during reset
     */
    public void showResetFormattingDataPartition();

    /**
     * shows the user interface for removing files during reset
     */
    public void showResetRemovingFiles();

    /**
     * shows the user interface for restoring files during a running reset
     *
     * @param fileCopier the FileCopier used for restoring files during a
     * running reset
     */
    public void showResetRestore(FileCopier fileCopier);

    /**
     * called when resetting all selected StorageDevices finished
     *
     * @param success if resetting all storage devices was successful
     */
    public void resettingFinished(boolean success);

    /**
     * shows an error message
     *
     * @param errorMessage the error message
     */
    public void showErrorMessage(String errorMessage);

    /**
     * shows a confirm dialog
     *
     * @param title the dialog title
     * @param message the message to display
     * @return true, if the user confirmed, false otherwise
     */
    public boolean showConfirmDialog(String title, String message);
}
