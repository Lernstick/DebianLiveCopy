package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.Installer;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.javafx.SceneContext;
import ch.fhnw.dlcopy.model.install.Installation;
import ch.fhnw.dlcopy.model.install.InstallationStatus;
import ch.fhnw.dlcopy.model.install.OperationStatus;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.util.StorageDevice;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableStringValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * A controler for the installation path.
 * Holds the current state of the installation.
 * @author lukas-gysin
 */
public class InstallControler implements DLCopyGUI {
    
    private Installation currentInstallation = null;
    protected ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");
    
    /**
     * The singleton instance of the class
     */
    private static InstallControler instance = null;
    
    /**
     * A list of all selected devices
     * On each of this devices a installation is planed
     */
    private final List<Installation> installations = new ArrayList<>();
    
    /**
     * The scene context, witch is used to switch the scene
     */
    private final SceneContext context;
    
    private final Property<String> installationStep = new SimpleStringProperty();
    private final Property<Number> progress = new SimpleDoubleProperty(-1);
    
    /**
     * Mark the constructor <c>private</c>, so it is not accessable from outside
     */
    private InstallControler(SceneContext context) {
        this.context = context;
    }
    
    /**
     * A thread save lacy constructor for the singleton
     * @param context The scene context, witch will be used to switch the scene.
     * @return The instance of the singleton
     */
    public static synchronized InstallControler getInstance(SceneContext context){
        if (instance == null) {
            instance = new InstallControler(context);
        }
        return instance;
    }
    
    /**
     * Generates the `installations` list from the given list
     * All installations will have the status `PENDING`
     * @param devices A list of all selected devices. On each device a installation will take place.
     * @param start The start value of the autonumbering
     * @param increment The amount, the numbering should increase per device
     */
    public void createInstallationList(List<StorageDevice> devices, int start, int increment) {
        int currentNumber = start;
        for (StorageDevice device : devices){
            installations.add(new Installation(device, currentNumber));
            currentNumber += increment;
        }
    }
    
    /**
     * This methode is called, when installation starts.
     * It will show initial the load ui. 
     */
    @Override
    public void showInstallProgress() {
        context.setScene(new LoadUI());
    }

    /**
     * This methode is called, when the installation on the given device starts
     * @param storageDevice 
     */
    @Override
    public void installingDeviceStarted(StorageDevice storageDevice) {
        currentInstallation = getInstallationFor(storageDevice);
        currentInstallation.setStatus(OperationStatus.ONGOING);
    }

    @Override
    public void showInstallCreatingFileSystems() {
        currentInstallation.setDetailStatus(InstallationStatus.CREATE_FILE_SYSTEMS);
        installationStep.setValue(stringBundle.getString("install.create_fs"));
        progress.setValue(-1);
    }

    @Override
    public void showInstallOverwritingDataPartitionWithRandomData(long done, long size) {
        currentInstallation.setDetailStatus(InstallationStatus.OVERWRITE_DATA_PARTITION_WITH_RANDOM_DATA);
        installationStep.setValue(stringBundle.getString("install.overwritte"));
        progress.setValue(done / size);
    }

    @Override
    public void showInstallFileCopy(FileCopier fileCopier) {
        currentInstallation.setDetailStatus(InstallationStatus.COPY_FILES);
        installationStep.setValue(stringBundle.getString("install.copy"));
        progress.setValue(-1);
        
    }

    @Override
    public void showInstallPersistencyCopy(Installer installer, String copyScript, String sourcePath) {
        currentInstallation.setDetailStatus(InstallationStatus.COPY_PERSISTENCY_PARTITION);
        progress.setValue(-1);
    }

    @Override
    public void setInstallCopyLine(String line) {
        // TODO: What does this function do?
        System.out.println(String.format(">>>>>>>>>>>>>>> TRACE: `setInstallCopyLine()` with %s as line is called.", line));
    }

    @Override
    public void showInstallUnmounting() {
        currentInstallation.setDetailStatus(InstallationStatus.UNMOUNTING);
        installationStep.setValue(stringBundle.getString("install.unmounting"));
        progress.setValue(-1);
    }

    @Override
    public void showInstallWritingBootSector() {
        currentInstallation.setDetailStatus(InstallationStatus.WRITE_BOOT_SECTOR);
        installationStep.setValue(stringBundle.getString("install.writing_boot_sector"));
        progress.setValue(-1);
    }


    @Override
    public void installingDeviceFinished(String errorMessage, int autoNumberStart) {
        // Update the status
        if (errorMessage == null) {
            // No error occured
            currentInstallation.setStatus(OperationStatus.SUCCESSFULL);
            installationStep.setValue(stringBundle.getString("install.success"));
        } else {
            // An error occured
            currentInstallation.setError(errorMessage);
            currentInstallation.setStatus(OperationStatus.FAILED);
            installationStep.setValue(stringBundle.getString("error.error") + ": " + errorMessage);
        }
        progress.setValue(1);
    }

    @Override
    public void installingListFinished() {
        currentInstallation.setStatus(OperationStatus.SUCCESSFULL); // It's possible thant for the last device no installing Device finished is called
        context.setScene(new InfoUI());
    }
    
    /**
     * Returns the list of installations
     * @return The list of all installations
     */
    public ObservableList<Installation> getInstallations() {
        return FXCollections.observableList(installations);
    }
    
    public Property<String> getInstallationStep(){
        return installationStep;
    }
    
    public Property<Number> getProgress(){
        return progress;
    }
    
    /**
     * Finds the current installation for the given device
     * @param device The device, for witch the installation should be searched
     * @return The installation for the given device
     */
    public Installation getInstallationFor(StorageDevice device){
        for (int i = 0; i < installations.size(); i++) {
            Installation installation = installations.get(i);
            if (installation.getDevice().equals(device)) {
                return installation;
            }
        }
        return null;
    }
}
