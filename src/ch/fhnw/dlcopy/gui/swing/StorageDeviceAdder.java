package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.StorageDevice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingWorker;

/**
 * parses udisks output paths and adds the corresponding storage devices to the
 * installation list
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
    private final JList list;

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
                processAddedDevice();
            }
        }
    }

    /**
     * get all the necessary infos about a device in the background thread so
     * that later rendering in the Swing event thread does not block
     */
    public abstract void initDevice();

    /**
     * do all the necessary things after a device has been added to the list
     */
    public abstract void processAddedDevice();

    private void addDeviceToList() {
        // put new device into a "sorted" position
        List<StorageDevice> deviceList = new ArrayList<>();
        Object[] entries = listModel.toArray();
        for (Object entry : entries) {
            deviceList.add((StorageDevice) entry);
        }
        deviceList.add(addedDevice);
        Collections.sort(deviceList);
        List selectedValues = list.getSelectedValuesList();
        listModel.clear();
        for (StorageDevice device : deviceList) {
            listModel.addElement(device);
        }

        // try to restore the previous selection
        for (Object selectedValue : selectedValues) {
            int index = deviceList.indexOf(selectedValue);
            if (index != -1) {
                list.addSelectionInterval(index, index);
            }
        }
    }
}
