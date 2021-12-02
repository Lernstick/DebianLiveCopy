package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/**
 * Creates a SquashFS file of the data partition
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SquashFSCreatorSwingWorker extends SwingWorker<Boolean, String> {

    private static final Logger LOGGER
            = Logger.getLogger(SquashFSCreator.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final SystemSource systemSource;
    private final String tmpDirectory;
    private final boolean showNotUsedDialog;
    private final boolean autoStartInstaller;

    private SquashFSCreator squashFSCreator;

    /**
     * creates a new ISOCreator
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param systemSource the system source
     * @param tmpDirectory the path to a temporary directory
     * @param showNotUsedDialog if the dialog that data partition is not in use
     * should be shown
     * @param autoStartInstaller if the installer should start automatically if
     * no datapartition is in use
     */
    public SquashFSCreatorSwingWorker(DLCopyGUI dlCopyGUI, SystemSource systemSource,
            String tmpDirectory, boolean showNotUsedDialog,
            boolean autoStartInstaller) {
        this.dlCopyGUI = dlCopyGUI;
        this.systemSource = systemSource;
        this.tmpDirectory = tmpDirectory;
        this.showNotUsedDialog = showNotUsedDialog;
        this.autoStartInstaller = autoStartInstaller;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        squashFSCreator = new SquashFSCreator(
            dlCopyGUI,
            systemSource,
            tmpDirectory,
            showNotUsedDialog,
            autoStartInstaller
        );

        return squashFSCreator.createSquashFS();
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.isoCreationFinished(squashFSCreator.getSquashFsPath(), get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }
}
