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
     */
    public InstallStorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList list, DefaultListModel<StorageDevice> listModel,
            boolean showHardDisks) {
        super(swingGUI, list, listModel, showHardDisks, false, null);
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
