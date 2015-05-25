package ch.fhnw.dlcopy;

import ch.fhnw.util.ModalDialogHandler;
import java.awt.Frame;
import java.util.HashSet;
import java.util.Set;

/**
 * Shows the StorageDeviceListUpdateDialog as long as there are paths parsed.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class StorageDeviceListUpdateDialogHandler {

    private final Set<String> paths = new HashSet<>();
    private final Frame parent;
    private ModalDialogHandler dialogHandler;
    private StorageDeviceListUpdateDialog dialog;
    private boolean showing;

    public StorageDeviceListUpdateDialogHandler(Frame parent) {
        this.parent = parent;
    }

    public synchronized void addPath(String path) {
        paths.add(path);
        if (!showing) {
            if (dialog == null) {
                dialog = new StorageDeviceListUpdateDialog(parent);
            }
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
            showing = true;
        }
    }

    public synchronized void removePath(String path) {
        paths.remove(path);
        if (paths.isEmpty()) {
            dialogHandler.hide();
            showing = false;
        }
    }
}
