package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * updates the list of available storage devices for the repairer
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class RepairStorageDeviceListUpdater extends StorageDeviceListUpdater {

    /**
     * creates a new RepairStorageDeviceListUpdater
     *
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     * @param bootDeviceName the name of the boot device
     */
    public RepairStorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList list, DefaultListModel<StorageDevice> listModel,
            boolean showHardDisks, String bootDeviceName) {
        super(swingGUI, list, listModel, showHardDisks, true, bootDeviceName);
    }

    @Override
    public void initDevices() {
        for (StorageDevice device : storageDevices) {
            for (Partition partition : device.getPartitions()) {
                try {
                    partition.getUsedSpace(false);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Override
    public void updateGUI() {
        swingGUI.repairStorageDeviceListChanged();
        swingGUI.updateRepairSelectionCountAndNextButton();
    }
}
