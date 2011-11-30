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
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * a SwingWorker to use when encrypting a directory
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class EncryptionSwingWorker extends SwingWorker<Boolean, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(EncryptionSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final static String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final String destinationPath;
    private final File encfsPlainDir;
    private final String encfsCipherPath;
    private final String password;
    private final RsyncCopyDialog rsyncCopyDialog;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;
    private boolean cancelled;

    /**
     * creates a new EncryptionSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param destinationPath the path of the current destination directory
     * @param encfsPlainDir the plaintext encfs dir where to copy the files into
     * @param encfsCipherPath the path of the ciphertext encfs dir
     * @param password the encfs password
     */
    public EncryptionSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel, String destinationPath,
            File encfsPlainDir, String encfsCipherPath, String password) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.destinationPath = destinationPath;
        this.encfsPlainDir = encfsPlainDir;
        this.encfsCipherPath = encfsCipherPath;
        this.password = password;
        processExecutor = new ProcessExecutor();
        rsyncCopyDialog = new RsyncCopyDialog(parentFrame,
                BUNDLE.getString("Encrypting_Destination_Directory"),
                new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/encryption_animation.gif")),
                processExecutor);
        dialogHandler = new ModalDialogHandler(rsyncCopyDialog);
        processExecutor.addPropertyChangeListener(rsyncCopyDialog);
        dialogHandler.show();
    }

    /**
     * execute the encryption in a background thread
     * @return
     */
    @Override
    protected Boolean doInBackground() {
        // do not store stdout but store stderr for logging purposes in case of
        // error
        int returnValue = 0;
        if (CurrentOperatingSystem.OS == OperatingSystem.Linux) {
            // "-a" does not work when running on Linux and the destination
            // directory is located on a NTFS partition
            returnValue = processExecutor.executeProcess(false, true,
                    "rsync", "-rv", "--no-inc-recursive", "--progress",
                    destinationPath + File.separatorChar,
                    encfsPlainDir.getPath() + File.separatorChar);

        } else {
            returnValue = processExecutor.executeProcess(false, true,
                    "rsync", "-rv", "--progress",
                    destinationPath + File.separatorChar,
                    encfsPlainDir.getPath() + File.separatorChar);
        }
        cancelled = rsyncCopyDialog.isCancelPressed();
        // we have to wait here, until plaindir ends up in /etc/mtab!?!?
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        // umount temporary encfs
        if (!FileTools.umountFUSE(encfsPlainDir, false)) {
            return false;
        }
        // yes, we evaluate the return value of the rsync operation not until
        // we umounted the temporary encfs (otherwise we end up with lots of
        // pending temporary encfs mounts)
        if (returnValue == 0) {
            EncryptionFinishSwingWorker encryptionFinishSwingWorker =
                    new EncryptionFinishSwingWorker(parentFrame,
                    backupMainPanel, destinationPath, encfsPlainDir,
                    encfsCipherPath, password);
            encryptionFinishSwingWorker.execute();
            return true;
        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "rsync error messages:{0}{1}",
                        new Object[]{
                            LINE_SEPARATOR, processExecutor.getStdErr()
                        });
            }
            EncryptionCleanupSwingWorker encryptionCleanupSwingWorker =
                    new EncryptionCleanupSwingWorker(
                    parentFrame, encfsCipherPath, encfsPlainDir, cancelled);
            encryptionCleanupSwingWorker.execute();
            return false;
        }
    }

    /**
     * called when the encryption finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
        try {
            if (!get() && !cancelled) {
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
