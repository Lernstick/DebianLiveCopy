package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * updates the list of available storage devices for the upgrader
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpgradeStorageDeviceListUpdater extends StorageDeviceListUpdater {

    private static final Logger LOGGER
            = Logger.getLogger(UpgradeStorageDeviceListUpdater.class.getName());
    private final SystemSource source;

    /**
     * creates a new UpgradeStorageDeviceListUpdater
     *
     * @param source the source for upgrades
     * @param swingGUI the DLCopy Swing GUI
     * @param list the list to fill
     * @param listModel the list model
     * @param showHardDisks if true, hard disks are added, otherwise ignored
     */
    public UpgradeStorageDeviceListUpdater(SystemSource source,
            DLCopySwingGUI swingGUI, JList<StorageDevice> list,
            DefaultListModel<StorageDevice> listModel, boolean showHardDisks) {

        super(swingGUI, list, listModel, showHardDisks,
                false, source.getDeviceName());

        this.source = source;
    }

    @Override
    public void initDevices() {
        for (StorageDevice device : storageDevices) {
            try {
                device.getSystemUpgradeVariant(
                        DLCopy.getEnlargedSystemSize(source.getSystemSize()));
                for (Partition partition : device.getPartitions()) {
                    try {
                        if (partition.isPersistencePartition()) {
                            partition.getUsedSpace(true);
                        } else {
                            partition.getUsedSpace(false);
                        }
                    } catch (Exception ignored) {
                    }
                }
            } catch (DBusException | IOException ex) {
                LOGGER.log(Level.WARNING, "", ex);
            } catch (Exception ex) {
                LOGGER.log(Level.SEVERE, "", ex);
                throw ex;
            }
        }
    }

    @Override
    public void updateGUI() {
        swingGUI.upgradeStorageDeviceListChanged();
    }
}
