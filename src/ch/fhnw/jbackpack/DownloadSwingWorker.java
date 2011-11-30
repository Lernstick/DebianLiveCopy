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
import java.awt.Frame;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

/**
 * A Swingworker for downloading files from a URL
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class DownloadSwingWorker extends SwingWorker<Boolean, Integer> {

    private final static Logger LOGGER =
            Logger.getLogger(DownloadSwingWorker.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final URL url;
    private final File destination;
    private final ProgressDialog progressDialog;
    private final ModalDialogHandler dialogHandler;
    private IOException ioException;

    /**
     * creates a new DownloadSwingWorker
     * @param parentFrame the parent frame
     * @param url the url to the file to download
     * @param description a description of the file to download
     * @param destination the destination of the download
     */
    public DownloadSwingWorker(Frame parentFrame, URL url, String description,
            File destination) {
        this.url = url;
        this.destination = destination;
        progressDialog = new ProgressDialog(parentFrame, null);
        progressDialog.setIcon(IconManager.INFORMATION_ICON);
        String message = BUNDLE.getString("Downloading");
        message = MessageFormat.format(message, description);
        progressDialog.setMessage(message);
        progressDialog.setSpecialIcon(null);
        progressDialog.setIndeterminate(false);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * returns the I/O exception (if any happened)
     * @return the I/O exception (if any happened)
     */
    public IOException getIoException() {
        return ioException;
    }

    /**
     * downloads the given URL to the given destination file
     * @return <code>true</code> if downloading was successfull,
     * <code>false</code> otherwise
     */
    @Override
    protected Boolean doInBackground() {
        try {
            URLConnection connection = url.openConnection();
            int contentLength = connection.getContentLength();
            LOGGER.log(Level.INFO, "contentLength: {0}", contentLength);
            InputStream inputStream = url.openStream();
            FileOutputStream fileOutputStream =
                    new FileOutputStream(destination);
            byte[] buffer = new byte[1024];
            long downloadCounter = 0;
            for (int count = inputStream.read(buffer); count != -1;) {
                fileOutputStream.write(buffer, 0, count);
                downloadCounter += count;
                int progress = (int) ((downloadCounter * 100) / contentLength);
                publish(progress);
                count = inputStream.read(buffer);
            }
            inputStream.close();
            fileOutputStream.close();
            LOGGER.log(Level.INFO, "downloading finished");
            return true;
        } catch (IOException ex) {
            ioException = ex;
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    /**
     * processes the progress information chunks
     * @param chunks a list of progress values
     */
    @Override
    protected void process(List<Integer> chunks) {
        progressDialog.setProgress(chunks.get(0));
    }

    /**
     * finishes the download operation
     */
    @Override
    protected void done() {
        dialogHandler.hide();
    }
}
