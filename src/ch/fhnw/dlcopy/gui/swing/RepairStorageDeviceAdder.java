package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.InstallationSource;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * parses udisks output paths and adds the corresponding storage devices to the
 * repair list
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class RepairStorageDeviceAdder extends StorageDeviceAdder {

    private static final Logger LOGGER
            = Logger.getLogger(RepairStorageDeviceAdder.class.getName());

    /**
     * creates a new RepairStorageDeviceAdder
     *
     * @param addedPath the added udisks path
     * @param showHarddisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @param dialogHandler the dialog handler for updating storage device lists
     * @param listModel the ListModel of the storage devices JList
     * @param list the storage devices JList
     * @param swingGUI the DLCopySwingGUI
     */
    public RepairStorageDeviceAdder(String addedPath, boolean showHarddisks,
            StorageDeviceListUpdateDialogHandler dialogHandler,
            DefaultListModel<StorageDevice> listModel, JList list,
            DLCopySwingGUI swingGUI) {
        super(addedPath, showHarddisks, dialogHandler,
                listModel, list, swingGUI);
    }

    @Override
    public void initDevice() {
        try {
            TimeUnit.SECONDS.sleep(7);
            for (Partition partition : addedDevice.getPartitions()) {
                try {
                    partition.getUsedSpace(false);
                } catch (Exception ignored) {
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    @Override
    public void processAddedDevice() {
        swingGUI.repairStorageDeviceListChanged();
        swingGUI.updateRepairSelectionCountAndNextButton();
    }
}
