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

import ch.fhnw.util.DirSizeCalculator;
import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
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
 * A Swingworker for checking ciphertext directories before decryption
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DecryptionCheckSwingWorker extends SwingWorker<Boolean, Void> {

    private final static Logger LOGGER =
            Logger.getLogger(DecryptionCheckSwingWorker.class.getName());
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final Frame parentFrame;
    private final BackupMainPanel backupMainPanel;
    private final File encfsCipherDir;
    private final File encfsPlainDir;
    private final DirectoryCheckDialog dirCheckDialog;
    private final ModalDialogHandler dialogHandler;
    private final DirSizeCalculator dirSizeCalculator = new DirSizeCalculator();
    private long size;
    private boolean enoughSpaceAvailable;
    private boolean spaceKnown;
    private long usableSpace;

    /**
     * creates a new DecryptionCheckSwingWorker
     * @param parentFrame the parent frame
     * @param backupMainPanel the BackupMainPanel
     * @param dirCheckDialog the directory check dialog
     * @param dialogHandler the modal dialog handler
     * @param encfsCipherDir the ciphertext encfs directory
     * @param encfsPlainDir the plaintext encfs directory
     */
    public DecryptionCheckSwingWorker(Frame parentFrame,
            BackupMainPanel backupMainPanel,
            DirectoryCheckDialog dirCheckDialog,
            ModalDialogHandler dialogHandler,
            File encfsCipherDir, File encfsPlainDir) {
        this.parentFrame = parentFrame;
        this.backupMainPanel = backupMainPanel;
        this.dirCheckDialog = dirCheckDialog;
        this.dialogHandler = dialogHandler;
        this.encfsCipherDir = encfsCipherDir;
        this.encfsPlainDir = encfsPlainDir;
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
     * executes the decryption in a background thread
     * @return <code>true</code>, if decryption was successfull,
     * <code>false</code> otherwise
     */
    @Override
    protected Boolean doInBackground() {

        Thread calculatorThread = new Thread() {

            @Override
            public void run() {
                try {
                    dirSizeCalculator.calculateSize(encfsPlainDir);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        };
        calculatorThread.start();
        enoughSpaceAvailable = true;
        while (calculatorThread.isAlive()) {
            if (!checkSize(dirSizeCalculator)) {
                enoughSpaceAvailable = false;
                break;
            }
            try {
                calculatorThread.join(300);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        // a final check is absolutely necessary
        // (otherwise we miss the information of the latest 300 ms)
        if (!checkSize(dirSizeCalculator)) {
            enoughSpaceAvailable = false;
        }

        return enoughSpaceAvailable;
    }

    /**
     * updates the decryption progress information in the dialog
     * @param chunks not used
     */
    @Override
    protected void process(List<Void> chunks) {
        dirCheckDialog.setFileCount(dirSizeCalculator.getFileCounter());
        dirCheckDialog.setCurrentFile(
                dirSizeCalculator.getCurrentFile().getPath());
        dirCheckDialog.setCurrentSize(size);
    }

    /**
     * called when the decryption finished
     */
    @Override
    protected void done() {

        dialogHandler.hide();

        // handle errors and warnings
        if (spaceKnown) {
            if (!enoughSpaceAvailable) {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Error_No_Space_For_Decryption"),
                        BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        } else {
            // show warning dialog
            String message = BUNDLE.getString("Warning_Unknown_Space");
            String sizeString = FileTools.getDataVolumeString(size, 1);
            message = MessageFormat.format(message, sizeString);
            int returnValue = JOptionPane.showOptionDialog(parentFrame, message,
                    BUNDLE.getString("Warning"), JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, null, null);
            if (returnValue != JOptionPane.OK_OPTION) {
                return;
            }
        }

        // decrypt
        DecryptionSwingWorker decryptionSwingWorker =
                new DecryptionSwingWorker(parentFrame, backupMainPanel,
                encfsCipherDir, encfsPlainDir);
        decryptionSwingWorker.execute();
    }

    private boolean checkSize(DirSizeCalculator dirSizeCalculator) {
        size = dirSizeCalculator.getCurrentSize();
        if (!spaceKnown || (size < usableSpace)) {
            publish();
            return true;
        } else {
            dirSizeCalculator.stop();
            return false;
        }
    }

    /**
     * returns the calculated size of the given directory
     * @return the calculated size of the given directory
     */
    public long getSize() {
        return size;
    }
}
