package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.StorageDevice;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * updates the list of available storage devices for the installation transfer
 * list
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallTransferStorageDeviceListUpdater
        extends StorageDeviceListUpdater {

    private static final Logger LOGGER = Logger.getLogger(
            InstallTransferStorageDeviceListUpdater.class.getName());

    /**
     * creates a new InstallTransferStorageDeviceListUpdater
     *
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     * @param bootDeviceName the name of the boot device
     */
    public InstallTransferStorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList<StorageDevice> list,
            DefaultListModel<StorageDevice> listModel, boolean showHardDisks,
            String bootDeviceName) {
        
        super(swingGUI, list, listModel, showHardDisks, false, bootDeviceName);
    }

    @Override
    public void initDevices() {
        for (StorageDevice device : storageDevices) {
            try {
                device.getPartitions().forEach((partition) -> {
                        partition.getUsedSpace(false);
                });
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                throw ex;
            }
        }
    }

    @Override
    public void updateGUI() {
        swingGUI.installTransferStorageDeviceListChanged();
    }
}
