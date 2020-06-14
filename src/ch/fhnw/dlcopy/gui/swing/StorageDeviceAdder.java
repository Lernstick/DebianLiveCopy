package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.StorageDevice;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private static final Logger LOGGER
            = Logger.getLogger(StorageDeviceAdder.class.getName());

    /**
     * the DLCopySwingGUI
     */
    protected final DLCopySwingGUI swingGUI;

    /**
     * the added device
     */
    protected StorageDevice addedDevice;

    protected final JList<StorageDevice> list;

    private final String addedPath;
    private final boolean showHardDisks;
    private final StorageDeviceListUpdateDialogHandler dialogHandler;
    private final DefaultListModel<StorageDevice> listModel;
    private final Lock lock;

    /**
     * creates a new StorageDeviceAdder
     *
     * @param addedPath the added udisks path
     * @param showHardDisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @param dialogHandler the dialog handler for updating storage device lists
     * @param listModel the ListModel of the storage devices JList
     * @param list the storage devices JList
     * @param swingGUI the DLCopySwingGUI
     * @param lock the lock to aquire before adding the device to the listModel
     */
    public StorageDeviceAdder(String addedPath, boolean showHardDisks,
            StorageDeviceListUpdateDialogHandler dialogHandler,
            DefaultListModel<StorageDevice> listModel,
            JList<StorageDevice> list, DLCopySwingGUI swingGUI, Lock lock) {

        this.addedPath = addedPath;
        this.showHardDisks = showHardDisks;
        this.dialogHandler = dialogHandler;
        this.listModel = listModel;
        this.list = list;
        this.swingGUI = swingGUI;
        this.lock = lock;

        dialogHandler.addPath(addedPath);
    }

    @Override
    protected Void doInBackground() throws Exception {

        Thread.currentThread().setName(getClass().getName());

        addedDevice = DLCopy.getStorageDeviceAfterTimeout(
                addedPath, showHardDisks);
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
                LOGGER.info("trying to acquire lock...");
                lock.lock();
                LOGGER.info("lock aquired");
                try {
                    addDeviceToList();
                    updateGUI();
                } finally {
                    LOGGER.info("releasing lock...");
                    lock.unlock();
                    LOGGER.info("unlocked");
                }
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
        LOGGER.log(Level.INFO, "adding {0} to index {1}",
                new Object[]{addedDevice, addIndex});
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
