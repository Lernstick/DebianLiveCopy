/*
 * EncryptionCheckSwingWorker.java
 *
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
 * 
 * Created on 27.06.2010, 10:22:15
 *
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.PlainDirChecker;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * A Swingworker for checking plaintext directories before encryption
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class EncryptionCheckSwingWorker extends SwingWorker<Object, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(EncryptionCheckSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final DirectoryCheckDialog dirCheckDialog;
    private final ModalDialogHandler dialogHandler;
    private final PlainDirChecker plainDirChecker;
    private final File destinationDirectory;
    private final File encfsCipherDirectory;
    private final File encfsPlainDirectory;
    private final String password;
    private boolean spaceKnown;
    private long usableSpace;
    private long size;
    private boolean enoughSpaceAvailable;

    /**
     * creates a new EncryptionCheckSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param dirCheckDialog the DirectoryCheckDialog where the checking
     * progress is displayed
     * @param dialogHandler the handler for the modal dialog
     * @param destinationDirectory the directory to check
     * @param encfsCipherDirectory the encfs ciphertext directory
     * @param encfsPlainDirectory the encfs plaintext directory
     * @param password the encfs password
     * @param maxFilenameLength the maximum filename length
     */
    public EncryptionCheckSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel,
            DirectoryCheckDialog dirCheckDialog,
            ModalDialogHandler dialogHandler,
            File destinationDirectory,
            File encfsCipherDirectory, File encfsPlainDirectory,
            String password, int maxFilenameLength) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.dirCheckDialog = dirCheckDialog;
        this.dialogHandler = dialogHandler;
        this.destinationDirectory = destinationDirectory;
        this.encfsCipherDirectory = encfsCipherDirectory;
        this.encfsPlainDirectory = encfsPlainDirectory;
        this.password = password;
        plainDirChecker = new PlainDirChecker(maxFilenameLength);
    }

    /**
     * sets the usable space in the destination directory
     * @param usableSpace
     */
    public void setUsableSpace(long usableSpace) {
        spaceKnown = true;
        // we bail out when 95% of the usable space would be needed
        this.usableSpace = (usableSpace * 95) / 100;
    }

    /**
     * runs the encryption check in a background thread
     * @return
     */
    @Override
    protected Object doInBackground() {

        Thread checkThread = new Thread() {

            @Override
            public void run() {
                try {
                    plainDirChecker.check(destinationDirectory);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        };
        checkThread.start();
        enoughSpaceAvailable = true;
        while (checkThread.isAlive()) {
            if (!update(plainDirChecker)) {
                enoughSpaceAvailable = false;
                break;
            }
            try {
                checkThread.join(300);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        // a final check is absolutely necessary
        // (otherwise we miss the information of the latest 300 ms)
        if (!update(plainDirChecker)) {
            enoughSpaceAvailable = false;
        }

        return enoughSpaceAvailable;
    }

    /**
     * updates the progress information in the dialog
     * @param chunks unused
     */
    @Override
    protected void process(List<Void> chunks) {
        dirCheckDialog.setFileCount(plainDirChecker.getFileCounter());
        dirCheckDialog.setCurrentFile(
                plainDirChecker.getCurrentFile().getPath());
        dirCheckDialog.setCurrentSize(size);
        dirCheckDialog.setFilenameCheckstatus(
                plainDirChecker.getTooLongFiles().isEmpty());
    }

    /**
     * called when the encryption check finished
     */
    @Override
    protected void done() {

        dialogHandler.hide();

        if (spaceKnown) {
            if (!enoughSpaceAvailable) {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Error_No_Space_For_Encryption"),
                        BUNDLE.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
                cleanup();
                return;
            }

        } else {
            // show warning dialog
            String message = BUNDLE.getString("Warning_Unknown_Space");
            String sizeString = FileTools.getDataVolumeString(size, 1);
            message = MessageFormat.format(message, sizeString);
            int returnValue = JOptionPane.showOptionDialog(parentFrame,
                    message, BUNDLE.getString("Warning"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, null, null);
            if (returnValue != JOptionPane.OK_OPTION) {
                cleanup();
                return;
            }
        }

        // check file name size
        List<File> tooLongFiles = plainDirChecker.getTooLongFiles();
        if (!tooLongFiles.isEmpty()) {
            TooLongFilenamesDialog tooLongFilenamesDialog =
                    new TooLongFilenamesDialog(parentFrame, tooLongFiles);
            tooLongFilenamesDialog.setVisible(true);
            cleanup();
            return;
        }

        // encrypt
        EncryptionSwingWorker encryptionSwingWorker = new EncryptionSwingWorker(
                parentFrame, backupMainPanel,
                destinationDirectory.getPath(), encfsPlainDirectory,
                encfsCipherDirectory.getPath(), password);
        encryptionSwingWorker.execute();
    }

    private boolean update(PlainDirChecker plainDirChecker) {
        size = plainDirChecker.getCurrentSize();
        if (!spaceKnown || (size < usableSpace)) {
            publish();
            return true;
        } else {
            plainDirChecker.stop();
            return false;
        }
    }

    private void cleanup() {
        FileTools.umountFUSE(encfsPlainDirectory, true);
        try {
            FileTools.recursiveDelete(encfsCipherDirectory);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
}
