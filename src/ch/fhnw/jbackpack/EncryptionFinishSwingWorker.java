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
import java.io.IOException;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * a SwingWorker to use when encrypting a directory
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class EncryptionFinishSwingWorker extends SwingWorker<Boolean, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(EncryptionFinishSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final String destinationPath;
    private final File encfsPlainDir;
    private final String encfsCipherPath;
    private final String password;
    private final String encfsPath;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;

    /**
     * creates a new EncryptionSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param destinationPath the path of the current destination directory
     * @param encfsPlainDir the plaintext encfs dir where to copy the files into
     * @param encfsCipherPath the path of the ciphertext encfs dir
     * @param password the encfs password
     */
    public EncryptionFinishSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel, String destinationPath,
            File encfsPlainDir, String encfsCipherPath, String password) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.destinationPath = destinationPath;
        this.encfsPlainDir = encfsPlainDir;
        this.encfsCipherPath = encfsCipherPath;
        this.password = password;
        encfsPath = encfsPlainDir.getPath();
        processExecutor = new ProcessExecutor();
        ProgressDialog progressDialog = new ProgressDialog(
                parentFrame, processExecutor);
        progressDialog.setIcon(IconManager.INFORMATION_ICON);
        progressDialog.setMessage(BUNDLE.getString(
                "Removing_Unencrypted_Files"));
        progressDialog.setSpecialIcon(null);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * finishes the encryption in a background thread
     * @return
     */
    @Override
    protected Boolean doInBackground() {
        // remove original plaintext files
        int returnValue = processExecutor.executeProcess(
                "rm", "-rf", destinationPath);
        if (returnValue != 0) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "could not remove original plaintext "
                        + "files in directory {0}", destinationPath);
            }
            return false;
        }
        // move temporary cipherDir to original directory path
        returnValue = processExecutor.executeProcess(
                "mv", encfsCipherPath, destinationPath);
        if (returnValue != 0) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "could not move temporary cipher "
                        + "directory {0} to original directory path {1}",
                        new Object[]{encfsCipherPath, destinationPath});
            }
            return false;
        }
        // re-mount encrypted directory at original path
        try {
            if (FileTools.mountEncFs(destinationPath, encfsPath, password)) {
                return true;
            } else {
                if (!encfsPlainDir.delete()) {
                    LOGGER.log(Level.WARNING,
                            "could not delete {0}", encfsPlainDir);
                }
                return false;
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING,
                    "re-mounting encrypted directory at original path failed",
                    ex);
            return false;
        }
    }

    /**
     * called when the background job finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
        try {
            if (get()) {
                backupMainPanel.setDestinationEncrypted(true);
                backupMainPanel.setEncfsMountPoint(encfsPath);
                backupMainPanel.checkDestinationCommon();
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Destination_Encrypted"),
                        BUNDLE.getString("Information"),
                        JOptionPane.INFORMATION_MESSAGE,
                        IconManager.INFORMATION_ICON);
            } else {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Encryption_Failed"),
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
