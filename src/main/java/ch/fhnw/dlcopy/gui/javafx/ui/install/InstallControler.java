package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A controler for the installation path.
 * Holds the current state of the installation.
 * @author lukas-gysin
 */
public class InstallControler implements DLCopyGUI {
    
    /**
     * The singleton instance of the class
     */
    private static InstallControler instance = null;
    
    /**
     * A list of all installations (pending, ongoing, failed and succeeded)
     */
    private ObservableList<StorageDeviceResult> installations = FXCollections.observableArrayList();
    
    /**
     * Mark the constructor <c>private</c>, so it is not accessable from outside
     */
    private InstallControler() {}
    
    /**
     * A thread save lacy constructor for the singleton
     * @return The instance of the singleton
     */
    public static synchronized InstallControler getInstance(){
        if (instance == null) {
            instance = new InstallControler();
        }
        return instance;
    }
    
    public ObservableList<StorageDeviceResult> getReport() {
        return installations;
    }
    
    @Override
    public void showInstallProgress() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallProgress()` is called.");
        // setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI());
    }

    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `installingDeviceStarted()` with %s as StorageDevice is called.", storageDevice.toString()));
        // InstallControler.getInstance().getReport().add(new StorageDeviceResult(storageDevice));
    }

    @Override
    public void showInstallCreatingFileSystems() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallCreatingFileSystems()` is called.");
        // setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.creating_file_systems")));
    }

    @Override
    public void showInstallOverwritingDataPartitionWithRandomData(long done, long size) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `showInstallOverwritingDataPartitionWithRandomData()` with %d as done and %d as size is called.", done, size));
        // setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(done,size));
    }

    @Override
    public void showInstallFileCopy(FileCopier fileCopier) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `showInstallFileCopy()` with %s as FileCopier is called.", fileCopier.toString()));
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallPersistencyCopy(Installer installer, String copyScript, String sourcePath) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `showInstallPersistencyCopy()` with %s as Installer, %s as copyScript and %s as sourcePath is called.", installer.toString(), copyScript, sourcePath));
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setInstallCopyLine(String line) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `setInstallCopyLine()` with %s as line is called.", line));
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallUnmounting() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallUnmounting()` is called.");
        // setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.unmount_file_systems"))); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallWritingBootSector() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallWritingBootSector()` is called.");
        // setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.writing_boot_sector")));
    }


    @Override
    public void installingDeviceFinished(String errorMessage, int autoNumberStart) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `installingDeviceFinished()` with %s as StorageDevice and %d as autoNumberStart is called.", errorMessage, autoNumberStart));
        // ObservableList<StorageDeviceResult> report = InstallControler.getInstance().getReport();
        // report.get(report.size() -1 ).setErrorMessage(errorMessage);
        // report.get(report.size() -1 ).finish();
    }

    @Override
    public void installingListFinished() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `installingListFinished()` is called.");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
