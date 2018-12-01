package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.StorageDevice;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * parses udisks output paths and adds the corresponding storage devices to the
 * installation list
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class InstallStorageDeviceAdder extends StorageDeviceAdder {

    /**
     * creates a new InstallStorageDeviceAdder
     *
     * @param addedPath the added udisks path
     * @param showHarddisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @param dialogHandler the dialog handler for updating storage device lists
     * @param listModel the ListModel of the storage devices JList
     * @param list the storage devices JList
     * @param swingGUI the DLCopySwingGUI
     */
    public InstallStorageDeviceAdder(String addedPath, boolean showHarddisks,
            StorageDeviceListUpdateDialogHandler dialogHandler,
            DefaultListModel<StorageDevice> listModel, JList list,
            DLCopySwingGUI swingGUI) {
        super(addedPath, showHarddisks, dialogHandler,
                listModel, list, swingGUI);
    }

    @Override
    public void initDevice() {
        // we don't need to do here anything...
    }

    @Override
    public void updateGUI() {
        swingGUI.installStorageDeviceListChanged();
        swingGUI.updateInstallSelectionCountAndExchangeInfo();
    }
}
