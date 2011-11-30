/**
 * Copyright (C) 2010 imedias
 *
 * This file is part of JBackpack.
 *
 * JBackpack is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * JBackpack is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.OperatingSystem;
import ch.fhnw.util.ProcessExecutor;
import java.awt.Frame;
import java.io.File;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.SwingWorker;

/**
 * A SwingWorker for decrypting a destination directory
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DecryptionSwingWorker extends SwingWorker<Void, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(DecryptionSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final File cipherDir;
    private final File plainDir;
    private final RsyncCopyDialog rsyncCopyDialog;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;
    private boolean cancelled;

    /**
     * creates a new DecryptionSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param cipherDir the encfs ciphertext directory
     * @param plainDir the encfs plaintext directory
     */
    public DecryptionSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel, File cipherDir, File plainDir) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.cipherDir = cipherDir;
        this.plainDir = plainDir;
        processExecutor = new ProcessExecutor();
        rsyncCopyDialog = new RsyncCopyDialog(parentFrame,
                BUNDLE.getString("Decrypting_Destination_Directory"),
                new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/decryption_animation.gif")),
                processExecutor);
        dialogHandler = new ModalDialogHandler(rsyncCopyDialog);
        processExecutor.addPropertyChangeListener(rsyncCopyDialog);
        dialogHandler.show();
    }

    /**
     * executes the decryption in a background thread
     * @return
     */
    @Override
    protected Void doInBackground() {
        // create a temporary directory for plain text
        File parentDir = cipherDir.getParentFile();
        String tmpPlainPath = FileTools.createTempDirectory(
                parentDir, cipherDir.getName() + ".plain").getPath();

        // decrypt all existing files by copying everything into temporary
        // plainDir
        int returnValue = 0;
        if (CurrentOperatingSystem.OS == OperatingSystem.Linux) {
            // "-a" does not work when running on Linux and the destination
            // directory is located on a NTFS partition
            returnValue = processExecutor.executeProcess(
                    "rsync", "-rv", "--no-inc-recursive", "--progress",
                    plainDir.getPath() + File.separatorChar,
                    tmpPlainPath + File.separatorChar);
        } else {
            returnValue = processExecutor.executeProcess(
                    "rsync", "-rv", "--progress",
                    plainDir.getPath() + File.separatorChar,
                    tmpPlainPath + File.separatorChar);
        }

        cancelled = rsyncCopyDialog.isCancelPressed();

        if (returnValue == 0) {
            DecryptionFinishSwingWorker decryptionFinishSwingWorker =
                    new DecryptionFinishSwingWorker(parentFrame,
                    backupMainPanel, cipherDir, plainDir, tmpPlainPath);
            decryptionFinishSwingWorker.execute();

        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "rsync from {0} to {1} failed",
                        new Object[]{plainDir, tmpPlainPath});
            }
            DecryptionCleanupSwingWorker decryptionCleanupSwingWorker =
                    new DecryptionCleanupSwingWorker(
                    parentFrame, tmpPlainPath, cancelled);
            decryptionCleanupSwingWorker.execute();
        }

        return null;
    }

    /**
     * called when decryption finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
    }
}
