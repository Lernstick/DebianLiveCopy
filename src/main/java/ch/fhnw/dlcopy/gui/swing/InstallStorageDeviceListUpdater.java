package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.StorageDevice;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * updates the list of available storage devices for the installer
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallStorageDeviceListUpdater extends StorageDeviceListUpdater {

    /**
     * creates a new InstallStorageDeviceListUpdater
     *
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     * @param bootDeviceName the name of the boot device
     */
    public InstallStorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList<StorageDevice> list,
            DefaultListModel<StorageDevice> listModel, boolean showHardDisks,
            String bootDeviceName) {
        super(swingGUI, list, listModel, showHardDisks, false, bootDeviceName);
    }

    @Override
    public void initDevices() {
        // we don't need to do here anything...
    }

    @Override
    public void updateGUI() {
        swingGUI.installStorageDeviceListChanged();
        swingGUI.updateInstallSelectionCountAndExchangeInfo();
    }
}
