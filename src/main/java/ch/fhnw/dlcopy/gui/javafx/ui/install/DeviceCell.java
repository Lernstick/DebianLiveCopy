package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class DeviceCell extends ListCell<StorageDevice>{
    
    private StorageDevice device;
    private FXMLLoader loader;
    
    private LongProperty efiPartitionSize;
    private LongProperty exchangePartitionSize;
    private LongProperty systemPartitionSize;
    
    @FXML private HBox hbPartitions;
    @FXML private Label lblIdentifier;
    @FXML private Label lblPartition1;
    @FXML private Label lblPartition2;
    @FXML private Label lblPartition3;
    @FXML private Label lblPartition4;
    @FXML private VBox panRoot;
    
    public DeviceCell(LongProperty efiPartitionSize, LongProperty exchangePartitionSize, LongProperty systemPartitionSize){
        super();
        this.efiPartitionSize = efiPartitionSize;
        this.exchangePartitionSize = exchangePartitionSize;
        this.systemPartitionSize = systemPartitionSize;
        
    }
    
    public DeviceCell(StorageDevice device){
        super();
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
            
            LongProperty deviceSize = new SimpleLongProperty(device.getSize() / 8);
            
            lblPartition1.prefWidthProperty().bind(
                    efiPartitionSize
                    .multiply(hbPartitions.widthProperty().multiply(0.98))
                    .divide(deviceSize)
            );
            
            lblPartition2.prefWidthProperty().bind(
                    exchangePartitionSize
                    .multiply(hbPartitions.widthProperty().multiply(0.98))
                    .divide(deviceSize)
            );
            
            lblPartition3.prefWidthProperty().bind(
                    deviceSize
                    .subtract(efiPartitionSize)
                    .subtract(exchangePartitionSize)
                    .subtract(systemPartitionSize)
                    .multiply(hbPartitions.widthProperty().multiply(0.98))
                    .divide(deviceSize)
            );
            
            lblPartition4.prefWidthProperty().bind(
                    systemPartitionSize
                    .multiply(hbPartitions.widthProperty().multiply(0.98))
                    .divide(deviceSize)
            );
            
            lblPartition2.textProperty().bind(new SimpleStringProperty(LernstickFileTools.getDataVolumeString(exchangePartitionSize.get(), 1)));
            lblPartition4.textProperty().bind(new SimpleStringProperty(LernstickFileTools.getDataVolumeString(systemPartitionSize.get(), 1)));
            
            exchangePartitionSize.addListener(event -> {
                System.out.println("TRACE: new efiPartitionSize: " + efiPartitionSize.get());
                System.out.println("\texchangePartitionSize: " + exchangePartitionSize.get());
                System.out.println("\tsystemPartitionSize: " + systemPartitionSize.get());
                System.out.println("\tdevice size: " + deviceSize.get());
            });
            
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
