package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.VBox;

public class DeviceCell extends ListCell<StorageDevice>{
    
    private StorageDevice device;
    private FXMLLoader loader;
    
    @FXML private Label lblIdentifier;
    @FXML private VBox panRoot;
    
    public DeviceCell(){
        // Empty constructor is for automic load through FX needed
        super();
    }
    
    public DeviceCell(StorageDevice device){
        this();
        this.device = device;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (obj == null) {
            return false;
        } else if (getClass() != obj.getClass()) {
            return false;
        }
        final DeviceCell other = (DeviceCell) obj;
        return device.equals(other.device);
    }
    
    @Override
    protected void updateItem(StorageDevice device, boolean empty) {
        super.updateItem(device, empty);
        
        this.device = device;
        
        if (empty || device == null) {
            // Cell is empty
            setText(null);
            setGraphic(null);
        } else {
            // Cell is not empty -> Populate the DeviceCell
            if (loader == null) {
                // The cell is not empty for the first time -> the FXML ressource is not loaded yet
                loader = new FXMLLoader(getClass().getResource("/fxml/install/deviceCell.fxml"));
                loader.setController(this);

                try {
                    loader.load();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            
            lblIdentifier.setText(device.toString());

            setText(null);
            setGraphic(panRoot);
        }

    }

    public StorageDevice getDevice(){
        return getStorageDevice();
    }
    
    public StorageDevice getStorageDevice(){
        return device;
    }

}
