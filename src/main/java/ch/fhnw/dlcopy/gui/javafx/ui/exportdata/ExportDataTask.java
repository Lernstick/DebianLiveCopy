package ch.fhnw.dlcopy.gui.javafx.ui.exportdata;

import ch.fhnw.dlcopy.SquashFSCreator;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import java.util.concurrent.ExecutionException;
import javafx.concurrent.Task;

public class ExportDataTask extends Task<Boolean> implements DLCopyGUI {

    private final DLCopyGUI dlCopyGUI;
    private final SystemSource systemSource;
    private final String tmpDirectory;
    private final boolean showNotUsedDialog;
    private final boolean autoStartInstaller;

    private SquashFSCreator squashFSCreator;

    /**
     * creates a new SquashFSCreator
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param systemSource the system source
     * @param tmpDirectory the path to a temporary directory
     * @param showNotUsedDialog if the dialog that data partition is not in use
     * should be shown
     * @param autoStartInstaller if the installer should start automatically if
     * no datapartition is in use
     */
    public ExportDataTask(DLCopyGUI dlCopyGUI, SystemSource systemSource,
            String tmpDirectory, boolean showNotUsedDialog,
            boolean autoStartInstaller) {
        this.dlCopyGUI = dlCopyGUI;
        this.systemSource = systemSource;
        this.tmpDirectory = tmpDirectory;
        this.showNotUsedDialog = showNotUsedDialog;
        this.autoStartInstaller = autoStartInstaller;
    }

    @Override
    protected Boolean call() throws Exception {
        squashFSCreator = new SquashFSCreator(
            dlCopyGUI,
            systemSource,
            tmpDirectory,
            showNotUsedDialog,
            autoStartInstaller
        );

        dlCopyGUI.showSquashFSProgressMessage("test");

        return squashFSCreator.createSquashFS();
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.squashFSCreationFinished(squashFSCreator.getSquashFsPath(), get());
        } catch (InterruptedException | ExecutionException ex) {
            dlCopyGUI.squashFSCreationFinished(ex.getLocalizedMessage(), false);
        }
    }
}
