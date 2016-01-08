package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.jbackpack.RdiffBackupRestore;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.ResourceBundle;

/**
 * An ActionListener when running rdiff-backup in DLCopy
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class BackupActionListener implements ActionListener {

    private final boolean backup;
    private final RdiffBackupRestore rdiffBackupRestore;
    private final DLCopyGUI dLCopyGUI;
    private final ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final long start;

    /**
     * Creates a new BackupActionListener
     *
     * @param backup if <code>true</code> we are running a backup, otherwise a
     * restore operation
     * @param rdiffBackupRestore the RdiffBackupRestore instance
     * @param dLCopyGUI the current GUI of DLCopy
     */
    public BackupActionListener(boolean backup,
            RdiffBackupRestore rdiffBackupRestore, DLCopyGUI dLCopyGUI) {
        this.backup = backup;
        this.rdiffBackupRestore = rdiffBackupRestore;
        this.dLCopyGUI = dLCopyGUI;
        start = System.currentTimeMillis();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        long fileCounter = rdiffBackupRestore.getFileCounter();
        if (fileCounter == 0) {
            // preparation is still running
            dLCopyGUI.setUpgradeBackupProgress(" ");
            dLCopyGUI.setUpgradeBackupFilename(" ");
        } else {
            // files are processed
            String string = BUNDLE.getString(
                    backup ? "Backing_Up_File" : "Restoring_File_Not_Counted");
            string = MessageFormat.format(string, fileCounter);
            dLCopyGUI.setUpgradeBackupProgress(string);
            String currentFile = rdiffBackupRestore.getCurrentFile();
            dLCopyGUI.setUpgradeBackupFilename(currentFile);
        }
        // update time information
        dLCopyGUI.setUpgradeBackupDuration(System.currentTimeMillis() - start);
    }
}
