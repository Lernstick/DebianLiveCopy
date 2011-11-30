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

import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.ProcessExecutor;
import java.awt.Frame;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * A SwingWorker to use when cleaning up a failed decryption attempt
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DecryptionCleanupSwingWorker extends SwingWorker<Void, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(DecryptionCleanupSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final ProcessExecutor processExecutor;
    private final String tmpPlainPath;
    private final boolean cancelled;
    private final ProgressDialog progressDialog;
    private final ModalDialogHandler dialogHandler;

    /**
     * creates a new DecryptionCleanupSwingWorker
     * @param parentFrame the parent frame
     * @param tmpPlainPath the path to the temporary plaintext directory that
     * must be deleted
     * @param cancelled if <tt>true</tt>, decryption was manually cancelled,
     * otherwise it really failed
     */
    public DecryptionCleanupSwingWorker(Frame parentFrame,
            String tmpPlainPath, boolean cancelled) {
        this.parentFrame = parentFrame;
        this.tmpPlainPath = tmpPlainPath;
        this.cancelled = cancelled;
        processExecutor = new ProcessExecutor();
        progressDialog = new ProgressDialog(parentFrame, processExecutor);
        progressDialog.setIcon(IconManager.INFORMATION_ICON);
        progressDialog.setMessage(BUNDLE.getString(
                "Deleting_Already_Decrypted_Files"));
        progressDialog.setSpecialIcon(null);
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * removes the plaintext directory in a background thread
     * @return
     */
    @Override
    protected Void doInBackground() {
        int returnValue = processExecutor.executeProcess(
                "rm", "-rf", tmpPlainPath);
        if (returnValue != 0) {
            LOGGER.log(Level.WARNING,
                    "could not remove {0}", tmpPlainPath);
        }
        return null;
    }

    /**
     * called when cleaning up finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
        if (!cancelled) {
            JOptionPane.showMessageDialog(parentFrame,
                    BUNDLE.getString("Decryption_Failed"),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }
}
