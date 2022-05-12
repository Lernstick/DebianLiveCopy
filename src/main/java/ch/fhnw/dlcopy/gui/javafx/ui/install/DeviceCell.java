package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
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
    private LongProperty exchangePartitionSize = new SimpleLongProperty();
    private LongProperty systemPartitionSize;
    private ReadOnlyDoubleProperty rowSpace;
    
    @FXML private HBox hbPartitions;
    @FXML private Label lblIdentifier;
    @FXML private Label lblPartition1;
    @FXML private Label lblPartition2;
    @FXML private Label lblPartition3;
    @FXML private Label lblPartition4;
    @FXML private VBox panRoot;
    
    public DeviceCell(LongProperty efiPartitionSize, LongProperty exchangePartitionSize, LongProperty systemPartitionSize, ReadOnlyDoubleProperty rowSpace){
        super();
        this.efiPartitionSize = efiPartitionSize;
        this.exchangePartitionSize.bind(exchangePartitionSize);
        this.systemPartitionSize = systemPartitionSize;
        this.rowSpace = rowSpace;
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
            
            LongProperty deviceSize = new SimpleLongProperty(device.getSize());
            LongProperty dataPartitionSize = new SimpleLongProperty();
            dataPartitionSize.bind(
                    deviceSize
                    .subtract(efiPartitionSize)
                    .subtract(exchangePartitionSize)
                    .subtract(systemPartitionSize)
            );
            
            lblPartition1.prefWidthProperty().bind(
                    efiPartitionSize
                    .multiply(rowSpace.multiply(0.95))
                    .divide(deviceSize)
            );
            
            lblPartition2.prefWidthProperty().bind(
                    exchangePartitionSize
                    .multiply(rowSpace.multiply(0.95))
                    .divide(deviceSize)
            );
            
            lblPartition3.prefWidthProperty().bind(
                    dataPartitionSize
                    .multiply(rowSpace.multiply(0.95))
                    .divide(deviceSize)
            );
            
            lblPartition4.prefWidthProperty().bind(
                    systemPartitionSize
                    .multiply(rowSpace.multiply(0.95))
                    .divide(deviceSize)
            );
            
            efiPartitionSize.addListener(event -> {
                lblPartition1.setText(LernstickFileTools.getDataVolumeString(efiPartitionSize.get(), 1));
            });
            
            exchangePartitionSize.addListener(event -> {
                lblPartition2.setText(LernstickFileTools.getDataVolumeString(exchangePartitionSize.get(), 1));
            });
            
            dataPartitionSize.addListener(event -> {
                lblPartition3.setText(LernstickFileTools.getDataVolumeString(dataPartitionSize.get(), 1));
            });
            
            systemPartitionSize.addListener(event -> {
                lblPartition4.setText(LernstickFileTools.getDataVolumeString(systemPartitionSize.get(), 1));
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
