package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.StorageDevice;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingWorker;

/**
 * parses udisks output paths and adds the storage devices to the corresponding
 * list
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class StorageDeviceAdder extends SwingWorker<Void, Void> {

    /**
     * the DLCopySwingGUI
     */
    protected final DLCopySwingGUI swingGUI;

    /**
     * the added device
     */
    protected StorageDevice addedDevice;

    private final String addedPath;
    private final boolean showHarddisks;
    private final StorageDeviceListUpdateDialogHandler dialogHandler;
    private final DefaultListModel<StorageDevice> listModel;
    private final JList<StorageDevice> list;

    /**
     * creates a new StorageDeviceAdder
     *
     * @param addedPath the added udisks path
     * @param showHarddisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @param dialogHandler the dialog handler for updating storage device lists
     * @param listModel the ListModel of the storage devices JList
     * @param list the storage devices JList
     * @param swingGUI the DLCopySwingGUI
     */
    public StorageDeviceAdder(String addedPath, boolean showHarddisks,
            StorageDeviceListUpdateDialogHandler dialogHandler,
            DefaultListModel<StorageDevice> listModel, JList list,
            DLCopySwingGUI swingGUI) {
        this.addedPath = addedPath;
        this.showHarddisks = showHarddisks;
        this.dialogHandler = dialogHandler;
        this.listModel = listModel;
        this.list = list;
        this.swingGUI = swingGUI;
        dialogHandler.addPath(addedPath);
    }

    @Override
    protected Void doInBackground() throws Exception {

        Thread.currentThread().setName(getClass().getName());

        addedDevice = DLCopy.getStorageDeviceAfterTimeout(
                addedPath, showHarddisks);
        if (addedDevice != null) {
            initDevice();
        }
        return null;
    }

    @Override
    protected void done() {
        dialogHandler.removePath(addedPath);

        if (addedDevice == null) {
            return;
        }
        synchronized (listModel) {
            // do nothing, if device was added in the meantime
            // e.g. via a StorageDeviceListUpdater
            if (!listModel.contains(addedDevice)) {
                addDeviceToList();
                updateGUI();
            }
        }
    }

    /**
     * get all the necessary infos about a device in the background thread so
     * that later rendering in the Swing event thread does not block
     */
    public abstract void initDevice();

    /**
     * do all the necessary GUI updates (showing or hiding panels, disabling or
     * enabling buttons, ...) after a device has been added to the list
     */
    public abstract void updateGUI();

    private void addDeviceToList() {

        // remember selected values
        List<StorageDevice> selectedValues = list.getSelectedValuesList();

        // insert device in a "sorted" fashion
        //
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // DON'T clear the listmodel here and add all devices again because
        // this would break DLCopySwingGUI.handleListDataEvent() where ALL(!)
        // devices added to the list model might be automatically upgraded or
        // reset.
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        int addIndex = 0;
        while (addIndex < listModel.size()
                && listModel.get(addIndex).compareTo(addedDevice) < 0) {
            addIndex++;
        }
        listModel.add(addIndex, addedDevice);

        // try to restore the previous selection
        for (StorageDevice selectedValue : selectedValues) {
            int index = listModel.indexOf(selectedValue);
            if (index != -1) {
                list.addSelectionInterval(index, index);
            }
        }
    }
}
