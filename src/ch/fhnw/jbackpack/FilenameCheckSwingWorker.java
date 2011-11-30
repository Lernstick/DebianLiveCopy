/*
 * FilenameCheckSwingWorker.java
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

import ch.fhnw.util.ModalDialogHandler;
import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Random;
import javax.swing.SwingWorker;

/**
 * A Swingworker for checking plaintext directories before encryption
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class FilenameCheckSwingWorker extends SwingWorker<Integer, Void> {

    private final FilenameCheckDialog filenameCheckDialog;
    private final ModalDialogHandler dialogHandler;
    private final File directory;
    private int goodLength;
    private int badLength;

    /**
     * creates a new EncryptionCheckSwingWorker
     * @param parentFrame the parent frame
     * @param directory
     */
    public FilenameCheckSwingWorker(Frame parentFrame, File directory) {
        this.directory = directory;
        filenameCheckDialog = new FilenameCheckDialog(parentFrame);
        dialogHandler = new ModalDialogHandler(filenameCheckDialog);
        dialogHandler.show();
    }

    /**
     * runs the file name check in a background thread
     * @return
     */
    @Override
    protected Integer doInBackground() {
        Random random = new Random();
        goodLength = 0;
        badLength = 32768;
        int length = 0;
        while ((badLength - goodLength) > 1) {
            length = (badLength + goodLength) / 2;
            // create random file names until we find a non-existing one
            File testFile = null;
            do {
                StringBuilder stringBuilder = new StringBuilder(length);
                for (int i = 0; i < length; i++) {
                    stringBuilder.append(random.nextInt(10));
                }
                String randomFileName = stringBuilder.toString();
                testFile = new File(directory, randomFileName);
            } while (testFile.exists());

            // now test if we can create the test file
            try {
                if (testFile.createNewFile()) {
                    goodLength = length;
                    testFile.delete();
                }
            } catch (IOException ex) {
                badLength = length;

            }
            publish();
        }
        return length;
    }

    /**
     * updates the information in the progress dialog
     * @param chunks
     */
    @Override
    protected void process(List<Void> chunks) {
        filenameCheckDialog.setLengths(goodLength, badLength);
    }

    /**
     * called when the file name check is finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
    }
}
