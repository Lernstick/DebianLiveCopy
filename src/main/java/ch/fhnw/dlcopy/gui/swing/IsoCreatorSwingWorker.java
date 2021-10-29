package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.*;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/**
 * Creates an ISO file from a running system
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class IsoCreatorSwingWorker extends SwingWorker<Boolean, String> {

    private static final Logger LOGGER
            = Logger.getLogger(IsoCreatorSwingWorker.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final SystemSource systemSource;
    private final boolean onlyBootMedium;
    private final String tmpDirectory;
    private final DataPartitionMode dataPartitionMode;
    private final boolean showNotUsedDialog;
    private final boolean autoStartInstaller;
    private final String isoLabel;

    private IsoCreator isoCreator;

    /**
     * creates a new ISOCreator
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param systemSource the system source
     * @param onlyBootMedium if only a boot medium should be created
     * @param tmpDirectory the path to a temporary directory
     * @param dataPartitionMode the data partition mode
     * @param showNotUsedDialog if the dialog that data partition is not in use
     * should be shown
     * @param autoStartInstaller if the installer should start automatically if
     * no datapartition is in use
     * @param isoLabel the label to use for the final ISO
     */
    public IsoCreatorSwingWorker(DLCopyGUI dlCopyGUI, SystemSource systemSource,
            boolean onlyBootMedium, String tmpDirectory,
            DataPartitionMode dataPartitionMode,
            boolean showNotUsedDialog, boolean autoStartInstaller,
            String isoLabel) {

        this.dlCopyGUI = dlCopyGUI;
        this.systemSource = systemSource;
        this.onlyBootMedium = onlyBootMedium;
        this.tmpDirectory = tmpDirectory;
        this.dataPartitionMode = dataPartitionMode;
        this.showNotUsedDialog = showNotUsedDialog;
        this.autoStartInstaller = autoStartInstaller;
        this.isoLabel = isoLabel;
    }

    /**
     * creates the ISO in a background thread
     *
     * @return <code>true</code>, if the ISO was successfully created,
     * <code>false</code> otherwise
     * @throws Exception
     */
    @Override
    protected Boolean doInBackground() throws Exception {

        isoCreator = new IsoCreator(dlCopyGUI, systemSource,
                onlyBootMedium, tmpDirectory, dataPartitionMode,
                showNotUsedDialog, autoStartInstaller, isoLabel);

        return isoCreator.createISO();
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.isoCreationFinished(isoCreator.getIsoPath(), get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }
}