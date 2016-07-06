package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.SystemSource;
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
 * upgrade list
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpgradeStorageDeviceAdder extends StorageDeviceAdder {

    private static final Logger LOGGER
            = Logger.getLogger(UpgradeStorageDeviceAdder.class.getName());
    private final SystemSource source;

    /**
     * creates a new UpgradeStorageDeviceAdder
     *
     * @param source the system source
     * @param addedPath the added udisks path
     * @param showHarddisks if true, paths to hard disks are processed,
     * otherwise ignored
     * @param dialogHandler the dialog handler for updating storage device lists
     * @param listModel the ListModel of the storage devices JList
     * @param list the storage devices JList
     * @param swingGUI the DLCopySwingGUI
     */
    public UpgradeStorageDeviceAdder(SystemSource source,
            String addedPath, boolean showHarddisks,
            StorageDeviceListUpdateDialogHandler dialogHandler,
            DefaultListModel<StorageDevice> listModel, JList list,
            DLCopySwingGUI swingGUI) {
        super(addedPath, showHarddisks, dialogHandler,
                listModel, list, swingGUI);
        this.source = source;
    }

    @Override
    public void initDevice() {
        try {
            TimeUnit.SECONDS.sleep(7);
            addedDevice.getUpgradeVariant(
                    DLCopy.getEnlargedSystemSize(source.getSystemSize()));
            for (Partition partition : addedDevice.getPartitions()) {
                try {
                    if (partition.isPersistencePartition()) {
                        partition.getUsedSpace(true);
                    } else {
                        partition.getUsedSpace(false);
                    }
                } catch (Exception ignored) {
                }
            }
        } catch (DBusException | IOException | InterruptedException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    @Override
    public void processAddedDevice() {
        swingGUI.upgradeStorageDeviceListChanged();
        swingGUI.updateUpgradeSelectionCountAndNextButton();
    }
}
