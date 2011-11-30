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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * a tool class for checking a plaintext directory before encryption
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class PlainDirChecker {

    private final int maxFilenameLength;
    private final AtomicLong size = new AtomicLong();
    private final AtomicLong fileCounter = new AtomicLong();
    private final List<File> tooLongFiles;
    private File currentFile;
    private boolean run = true;

    /**
     * creates a new PlainDirChecker
     * @param maxFilenameLength the maximum file name length
     */
    public PlainDirChecker(int maxFilenameLength) {
        this.maxFilenameLength = maxFilenameLength;
        tooLongFiles = new ArrayList<File>();
    }

    /**
     * recursively calculates the size of a file/directory
     * @param file the file/directory to check recursively
     * @throws IOException if an I/O exception occurs
     */
    public void check(File file) throws IOException {
        // detect and react to stop() calls
        if (!run) {
            return;
        }

        // update state information
        fileCounter.incrementAndGet();
        currentFile = file;

        // check file name length
        if (file.getName().length() > maxFilenameLength) {
            tooLongFiles.add(file);
        }

        // skip symlinks
        if (FileTools.isSymlink(file)) {
            return;
        }

        if (file.isDirectory()) {
            // recurse into directories
            for (File subFile : file.listFiles()) {
                check(subFile);
            }
        } else {
            // update size information
            size.addAndGet(file.length());
        }
    }

    /**
     * returns the intermediate, current sum of observed file sizes
     * @return the intermediate, current sum of observed file sizes
     */
    public long getCurrentSize() {
        return size.get();
    }

    /**
     * returns the number of already checked files
     * @return the number of already checked files
     */
    public long getFileCounter() {
        return fileCounter.get();
    }

    /**
     * returns the list of files with too long filenames
     * @return the list of files with too long filenames
     */
    public List<File> getTooLongFiles() {
        return tooLongFiles;
    }

    /**
     * returns the currently checked file
     * @return the currently checked file
     */
    public File getCurrentFile() {
        return currentFile;
    }

    /**
     * stops the current calculation process
     */
    public void stop() {
        run = false;
    }

    /**
     * resets the size counter to 0
     */
    public void reset() {
        run = true;
        size.set(0);
    }
}
