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
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * a SwingWorker to use when cleaning up a failed encryption
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class EncryptionCleanupSwingWorker extends SwingWorker<Void, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(EncryptionCleanupSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final File encfsPlainDir;
    private final String encfsCipherPath;
    private final boolean cancelled;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;

    /**
     * creates a new EncryptionSwingWorker
     * @param parentFrame the parent frame
     * @param encfsCipherPath the path of the ciphertext encfs dir
     * @param encfsPlainDir
     * @param cancelled if the rsync process was manually cancelled
     */
    public EncryptionCleanupSwingWorker(Frame parentFrame,
            String encfsCipherPath, File encfsPlainDir, boolean cancelled) {
        this.parentFrame = parentFrame;
        this.encfsCipherPath = encfsCipherPath;
        this.encfsPlainDir = encfsPlainDir;
        this.cancelled = cancelled;
        processExecutor = new ProcessExecutor();
        ProgressDialog progressDialog = new ProgressDialog(
                parentFrame, processExecutor);
        progressDialog.setIcon(IconManager.INFORMATION_ICON);
        progressDialog.setMessage(BUNDLE.getString(
                "Deleting_Already_Encrypted_Files"));
        progressDialog.setSpecialIcon(null);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * runs the encryption cleanup in a background thread
     * @return
     */
    @Override
    protected Void doInBackground() {
        int returnValue = processExecutor.executeProcess(
                "rm", "-rf", encfsCipherPath);
        if (returnValue != 0) {
            LOGGER.log(Level.WARNING,
                    "could not remove {0}", encfsCipherPath);
        }
        FileTools.deleteIfEmpty(encfsPlainDir);
        return null;
    }

    /**
     * called when encryption cleanup finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
        if (!cancelled) {
            JOptionPane.showMessageDialog(parentFrame,
                    BUNDLE.getString("Encryption_Failed"),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
