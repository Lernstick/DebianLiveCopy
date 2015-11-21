package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;

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
     * shows the user interface for copying files of a running installation
     *
     * @param fileCopier the FileCopier used for copying files of an
     * installation
     */
    public void showInstallFileCopy(FileCopier fileCopier);

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
     * shows the user interface for backing the exchange partition up during a
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
     * shows the user interface for backing the exchange partition up during a
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
     * sets the progress info in the title of the main window
     *
     * @param progressInfo the progress info to show in the title of the main
     * window
     */
    public void setProgressInTitle(String progressInfo);

    /**
     * shows an error message
     *
     * @param errorMessage the error message
     */
    public void showErrorMessage(String errorMessage);
}
