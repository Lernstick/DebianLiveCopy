package ch.fhnw.dlcopy.gui.javafx;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.dlcopy.gui.javafx.ui.StartscreenUI;
import ch.fhnw.dlcopy.gui.javafx.ui.View;
import ch.fhnw.dlcopy.gui.javafx.ui.install.InstallControler;
import ch.fhnw.dlcopy.model.PresentationModel;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class StorageMediaManagement
        extends Application
        implements SceneContext{

    private Scene scene;
    private PresentationModel model = PresentationModel.getInstance();
    private ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");
    private View view;

    @Override
    /**
     * A view calls this methode, when the scene should be cnaged to another view
     * @param view The target view to be displayed
     */
    public void setScene(View view){
        this.view.deinitialize();
        this.view = view;
        try {
            scene.setRoot(view.getRoot(this));
        } catch (IOException ex) {
            Logger.getLogger(StorageMediaManagement.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void stop() throws Exception {
        view.deinitialize();
    }
    
    @Override
    public void start(Stage stage) throws Exception {
        view = new StartscreenUI();
        Parent root = view.getRoot(this);
        scene = new Scene(root);

        stage.setScene(scene);
        stage.setHeight(model.getHeight());
        stage.setWidth(model.getWidth());
        stage.setTitle(stringBundle.getString("global.title"));
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/branding/Lernstick_Icon.png")));

        stage.heightProperty().addListener((observable, oldValue, newValue) -> {
            model.setHeight((int) (double) newValue);
        });
        stage.widthProperty().addListener((observable, oldValue, newValue) -> {
            model.setWidth((int) (double) newValue);
        });

        stage.show();
    }

    /**
     * @param args the command line arguments
     */
    public static void launchFX(String[] args) {
        launch(args);
    }

    @Override
    public void showInstallProgress() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallProgress()` is called.");
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI());
    }

    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `installingDeviceStarted()` with %s as StorageDevice is called.", storageDevice.toString()));
        InstallControler.getInstance().getReport().add(new StorageDeviceResult(storageDevice));
    }

    @Override
    public void showInstallCreatingFileSystems() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallCreatingFileSystems()` is called.");
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.creating_file_systems")));
    }

    @Override
    public void showInstallOverwritingDataPartitionWithRandomData(long done, long size) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `showInstallOverwritingDataPartitionWithRandomData()` with %d as done and %d as size is called.", done, size));
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(done,size));
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
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.unmount_file_systems"))); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void showInstallWritingBootSector() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `showInstallWritingBootSector()` is called.");
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.install.LoadUI(stringBundle.getString("global.writing_boot_sector")));
    }


    @Override
    public void installingDeviceFinished(String errorMessage, int autoNumberStart) {
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `installingDeviceFinished()` with %s as StorageDevice and %d as autoNumberStart is called.", errorMessage, autoNumberStart));
        ObservableList<StorageDeviceResult> report = InstallControler.getInstance().getReport();
        report.get(report.size() -1 ).setErrorMessage(errorMessage);
        report.get(report.size() -1 ).finish();
    }

    @Override
    public void installingListFinished() {
        System.out.println(">>>>>>>>>>>>>>> TRACE: `installingListFinished()` is called.");
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
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.LoadUI(message));
    }

    @Override
    public void showIsoProgressMessage(String message, int value) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.LoadUI(message, value));
    }

    @Override
    public void isoCreationFinished(String path, boolean success) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportsystem.InfoUI(path, success));
    }

    @Override
    public void showSquashFSProgressMessage(String message) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.LoadUI(message));
    }

    @Override
    public void showSquashFSProgressMessage(String message, int value) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.LoadUI(message, value));
    }

    @Override
    public void squashFSCreationFinished(String path, boolean success) {
        setScene(new ch.fhnw.dlcopy.gui.javafx.ui.exportdata.InfoUI(path, success));
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
}
