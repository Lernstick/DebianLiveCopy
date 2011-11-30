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
package ch.fhnw.util;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A Transferable for putting files into the clipboard
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class FileTransferable implements Transferable {

    DataFlavor[] dataFlavors = {DataFlavor.javaFileListFlavor};
    List<File> files = new ArrayList<File>();

    /**
     * Returns an array of DataFlavor objects indicating the flavors the data
     * can be provided in. The array should be ordered according to preference
     * for providing the data (from most richly descriptive to least
     * descriptive).
     * @return an array of data flavors in which this data can be transferred
     */
    public DataFlavor[] getTransferDataFlavors() {
        return dataFlavors;
    }

    /**
     * Returns whether or not the specified data flavor is supported for this
     * object.
     * @param flavor the requested flavor for the data
     * @return boolean indicating whether or not the data flavor is supported
     */
    public boolean isDataFlavorSupported(DataFlavor flavor) {
        return DataFlavor.javaFileListFlavor.equals(flavor);
    }

    /**
     * Returns an object which represents the data to be transferred. The class
     * of the object returned is defined by the representation class of the
     * flavor.
     * @param flavor the requested flavor for the data 
     * @return an object which represents the data to be transferred. The class
     * of the object returned is defined by the representation class of the
     * flavor.
     * @throws UnsupportedFlavorException if the requested data flavor is not
     * supported.
     * @throws IOException if the data is no longer available in the requested
     * flavor.
     */
    public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        return files;
    }

    /**
     * adds a file
     * @param file the file to add
     */
    public void addFile(File file) {
        files.add(file);
    }

    /**
     * copies the file to the clipboard
     * @param file the file to copy to the clipboard
     */
    public static void copy(File file) {
        FileTransferable fileTransferable = new FileTransferable();
        fileTransferable.addFile(file);
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(fileTransferable, null);
    }
}
