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

import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.ProcessExecutor;
import java.awt.Frame;
import java.io.File;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * A SwingWorker to use for finishing the decryptionn of a destination directory
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DecryptionFinishSwingWorker extends SwingWorker<Boolean, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(DecryptionFinishSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final File cipherDir;
    private final File plainMountPoint;
    private final String tmpPlainPath;
    private final ProgressDialog progressDialog;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;

    /**
     * creates a new DecryptionFinishSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param cipherDir the encfs ciphertext directory
     * @param plainMountPoint the encfs plaintext mountpoint
     * @param tmpPlainPath the path of the temporary plaintext directory
     * (the target directory of the decryption process)
     */
    public DecryptionFinishSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel, File cipherDir,
            File plainMountPoint, String tmpPlainPath) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.cipherDir = cipherDir;
        this.plainMountPoint = plainMountPoint;
        this.tmpPlainPath = tmpPlainPath;
        processExecutor = new ProcessExecutor();
        progressDialog = new ProgressDialog(
                parentFrame, processExecutor);
        progressDialog.setIcon(IconManager.INFORMATION_ICON);
        progressDialog.setMessage(BUNDLE.getString(
                "Removing_Encrypted_Files"));
        progressDialog.setSpecialIcon(null);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * runs the decryption cleanup in a background thread
     * @return
     */
    @Override
    protected Boolean doInBackground() {
        String cipherPath = cipherDir.getPath();
        // umount encfs
        if (!FileTools.umountFUSE(plainMountPoint, true)) {
            return false;
        }

        // remove original cipher files
        int returnValue = processExecutor.executeProcess(
                "rm", "-rf", cipherPath);
        if (returnValue != 0) {
            LOGGER.log(Level.WARNING, "could not remove {0}", cipherPath);
            return false;
        }

        // move temporary plain dir to original directory path
        returnValue = processExecutor.executeProcess(
                "mv", tmpPlainPath, cipherPath);
        if (returnValue != 0) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "could not move {0} to {1}",
                        new Object[]{tmpPlainPath, cipherPath});
            }
            return false;
        }

        return true;
    }

    /**
     * called when the background thread finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();

        try {
            if (get()) {
                backupMainPanel.setDestinationEncrypted(false);
                backupMainPanel.setEncfsMountPoint(null);
                backupMainPanel.checkDestinationCommon();
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Destination_Decrypted"),
                        BUNDLE.getString("Information"),
                        JOptionPane.INFORMATION_MESSAGE,
                        IconManager.INFORMATION_ICON);
            } else {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Decryption_Failed"),
                        BUNDLE.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
}
