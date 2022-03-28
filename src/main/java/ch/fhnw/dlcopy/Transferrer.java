package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import java.io.File;
import java.io.IOException;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * An abstract base class for Transferrers
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public abstract class Transferrer {

    protected final DLCopyGUI gui;

    public Transferrer(DLCopyGUI gui) {
        this.gui = gui;
    }

    protected void unmountAndDelete(MountInfo mountInfo, Partition partition)
            throws DBusException, IOException {

        if (!mountInfo.alreadyMounted()) {
            DLCopy.umount(partition, gui);
            LernstickFileTools.recursiveDelete(
                    new File(mountInfo.getMountPath()), true);
        }
    }
}
