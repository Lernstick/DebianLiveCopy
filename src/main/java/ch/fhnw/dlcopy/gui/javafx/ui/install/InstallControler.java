package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.nio.file.Path;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class InstallControler implements DLCopyGUI{
    
    private static InstallControler instance = null;
    
    private ObservableList<StorageDeviceResult> report = FXCollections.observableArrayList();
    
    private InstallControler() {}
    
    public static synchronized InstallControler getInstance(){
        if (instance == null) {
            instance = new InstallControler();
        }
        
        return instance;
    }

    @Override
    public void showInstallProgress() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallProgress");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {
        report.add(new StorageDeviceResult(storageDevice));
    }

    @Override
    public void showInstallCreatingFileSystems() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallCreatingFileSystems");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallOverwritingDataPartitionWithRandomData(long done, long size) {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallOverwritingDataPartitionWithRandomData");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallFileCopy(FileCopier fileCopier) {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallFileCopy");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallPersistencyCopy(Installer installer, String copyScript, String sourcePath) {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallPersistencyCopy");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setInstallCopyLine(String line) {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: setInstallCopyLine");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallUnmounting() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallUnmounting");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallWritingBootSector() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: showInstallWritingBootSector");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void installingDeviceFinished(String errorMessage, int autoNumberStart) {
        report.get(report.size() -1 ).setErrorMessage(errorMessage);
        report.get(report.size() -1 ).finish();
    }

    @Override
    public void installingListFinished() {
        System.out.println(">>>>>>>>>>>>>>>>>>>>>>>>> TRACE: installingListFinished");
        // throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeCreatingFileSystems() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeFileCopy(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeUnmounting() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeWritingBootSector() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeBackup() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeBackupExchangePartition(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeRestoreInit() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeRestoreRunning() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeRestoreExchangePartition(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeDataPartitionReset() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeChangingPartitionSizes() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setUpgradeBackupProgress(String progressInfo) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setUpgradeBackupFilename(String filename) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void setUpgradeBackupDuration(long duration) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showUpgradeSystemPartitionReset() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void upgradingDeviceStarted(StorageDevice storageDevice) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void upgradingDeviceFinished(String errorMessage) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void upgradingListFinished() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showIsoProgressMessage(String message) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showIsoProgressMessage(String message, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void isoCreationFinished(String path, boolean success) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showSquashFSProgressMessage(String message) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showSquashFSProgressMessage(String message, int value) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void squashFSCreationFinished(String path, boolean success) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetProgress() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resettingDeviceStarted(StorageDevice storageDevice) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showPrintingDocuments() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Path> selectDocumentsToPrint(String type, String mountPath, List<Path> documents) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetBackup(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetFormattingExchangePartition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetFormattingDataPartition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetRemovingFiles() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showResetRestore(FileCopier fileCopier) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void resettingFinished(boolean success) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showErrorMessage(String errorMessage) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean showConfirmDialog(String title, String message) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    public ObservableList<StorageDeviceResult> getReport() {
        return report;
    }
    
}
