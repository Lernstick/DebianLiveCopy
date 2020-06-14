package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.StorageDevice;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * updates the list of available storage devices for the Resetter
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetStorageDeviceListUpdater extends StorageDeviceListUpdater {

    /**
     * creates a new ResetStorageDeviceListUpdater
     *
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     * @param bootDeviceName the name of the boot device
     */
    public ResetStorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList<StorageDevice> list,
            DefaultListModel<StorageDevice> listModel, boolean showHardDisks,
            String bootDeviceName) {

        super(swingGUI, list, listModel, showHardDisks, true, bootDeviceName);
    }

    @Override
    public void initDevices() {
        storageDevices.forEach(device -> {
            device.getPartitions().forEach(partition -> {
                try {
                    partition.getUsedSpace(false);
                } catch (Exception ignored) {
                }
            });
        });
    }

    @Override
    public void updateGUI() {
        swingGUI.resetStorageDeviceListChanged();
        swingGUI.updateResetSelectionCountAndNextButton();
    }
}
