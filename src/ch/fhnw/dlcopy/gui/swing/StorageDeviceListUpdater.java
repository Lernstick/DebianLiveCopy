package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.SwingWorker;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * updates a list of available storage devices
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class StorageDeviceListUpdater extends SwingWorker<Void, Void> {

    /**
     * the DLCopy Swing GUI
     */
    protected final DLCopySwingGUI swingGUI;

    /**
     * the list of all detected storage devices
     */
    protected List<StorageDevice> storageDevices;

    protected final JList<StorageDevice> list;

    private static final Logger LOGGER
            = Logger.getLogger(StorageDeviceListUpdater.class.getName());

    private final DefaultListModel<StorageDevice> listModel;
    private final boolean showHardDisks;
    private final boolean showBootDevice;
    private final String bootDeviceName;
    private final ModalDialogHandler dialogHandler;
    private final List<StorageDevice> selectedValues;

    /**
     * creates a new InstallStorageDeviceListUpdater
     *
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     * @param showBootDevice if the boot device should be included in the list
     * @param bootDeviceName the name of the boot device
     */
    public StorageDeviceListUpdater(DLCopySwingGUI swingGUI,
            JList<StorageDevice> list,
            DefaultListModel<StorageDevice> listModel, boolean showHardDisks,
            boolean showBootDevice, String bootDeviceName) {

        this.swingGUI = swingGUI;
        this.list = list;
        this.listModel = listModel;
        this.showHardDisks = showHardDisks;
        this.showBootDevice = showBootDevice;
        this.bootDeviceName = bootDeviceName;

        StorageDeviceListUpdateDialog dialog
                = new StorageDeviceListUpdateDialog(swingGUI);
        dialogHandler = new ModalDialogHandler(dialog);
        dialogHandler.show();

        // remember selected values so that we can restore the selection
        selectedValues = list.getSelectedValuesList();
    }

    @Override
    protected Void doInBackground() throws Exception {
        synchronized (listModel) {
            listModel.clear();
            try {
                storageDevices = DLCopy.getStorageDevices(
                        showHardDisks, showBootDevice, bootDeviceName);
                Collections.sort(storageDevices);
                initDevices();
            } catch (IOException | DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                throw ex;
            }
        }
        return null;
    }

    @Override
    protected void done() {
        // manipulate list model on Swing Event Thread
        storageDevices.forEach((device) -> {
            listModel.addElement(device);
        });

        // try to restore the previous selection
        for (StorageDevice selectedValue : selectedValues) {
            int index = storageDevices.indexOf(selectedValue);
            if (index != -1) {
                list.addSelectionInterval(index, index);
            }
        }
        updateGUI();
        dialogHandler.hide();
    }

    /**
     * get all the necessary infos about all devices in the background thread so
     * that later rendering in the Swing event thread does not block
     */
    public abstract void initDevices();

    /**
     * updates the GUI for the new storage device list
     */
    public abstract void updateGUI();
}
