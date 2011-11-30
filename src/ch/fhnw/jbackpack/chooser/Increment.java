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
package ch.fhnw.jbackpack.chooser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * The increment of a rdiff-backup
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class Increment {

    private final static Logger LOGGER =
            Logger.getLogger(Increment.class.getName());
    private final Increment youngerIncrement;
    private final File backupDirectory;
    private final RdiffTimestamp timestamp;
    private final RdiffFile rdiffRoot;
    private Long size;

    /**
     * creates a new Increment
     * @param youngerIncrement the younger Increment next to this Increment
     * @param timestamp the timestamp of this increment
     * @param rdiffFileDatabase the rdiff file database
     * @param backupDirectory the backup directory of this increment
     */
    public Increment(Increment youngerIncrement, RdiffTimestamp timestamp,
            RdiffFileDatabase rdiffFileDatabase, File backupDirectory) {

        this.youngerIncrement = youngerIncrement;
        this.timestamp = timestamp;
        this.backupDirectory = backupDirectory;

        /**
         * some notes about size information:
         * ----------------------------------
         *
         * - the size of the current mirror is stored in the
         *   "SourceFileSize" value of the mirror session statistics file
         *
         * - the size of an increment is stored in the
         *   "IncrementFileSize" value of the session
         *   statistics file of the *younger* increment
         */
        if (youngerIncrement == null) {
            // this is the current mirror
            size = Math.max(0,
                    getSessionStatisticsValue("SourceFileSize"));
        } else {
            // this is an increment
            size = Math.max(0, youngerIncrement.getSessionStatisticsValue(
                    "IncrementFileSize"));
        }

        rdiffRoot = new RdiffFile(
                rdiffFileDatabase, this, null, ".", 0, 0, true);
    }

    /**
     * returns <code>true</code>, if this increment is the mirror,
     * <code>false</code> otherwise
     * @return <code>true</code>, if this increment is the mirror,
     * <code>false</code> otherwise
     */
    public boolean isMirror() {
        return (youngerIncrement == null);
    }

    /**
     * returns the younger increment
     * @return the youngerincrement
     */
    public Increment getYoungerIncrement() {
        return youngerIncrement;
    }

    /**
     * returns the timestamp of this increment
     * @return the timestamp of this increment
     */
    public Date getTimestamp() {
        return timestamp.getTimestamp();
    }

    /**
     * returns the timestamp string as rdiff-backup expects it
     * (seconds since epoch)
     * @return the timestamp string as rdiff-backup expects it
     */
    public String getRdiffTimestamp() {
        return String.valueOf(timestamp.getTimestamp().getTime() / 1000);
    }

    /**
     * returns the size of this increment
     * @return the size of this increment
     */
    public Long getSize() {
        return size;
    }

    /**
     * returns the rdiff file structure of this increment
     * @return the rdiff file structure of this increment
     */
    public RdiffFile getRdiffRoot() {
        return rdiffRoot;
    }

    /**
     * returns the backup directory of this increment
     * @return the backup directory of this increment
     */
    public File getBackupDirectory() {
        return backupDirectory;
    }

    /**
     * returns a session statistics value of this increment
     * @param key the wanted session statistics key
     * @return a session statistics value of this increment
     */
    public final long getSessionStatisticsValue(String key) {
        File statisticsFile = getRdiffBackupFile(backupDirectory.getPath(),
                "session_statistics." + timestamp.getFilestamp());
        if (statisticsFile == null) {
            return 0;
        }
        FileReader fileReader = null;
        BufferedReader bufferedReader = null;
        try {
            fileReader = new FileReader(statisticsFile);
            bufferedReader = new BufferedReader(fileReader);
            for (String line; (line = bufferedReader.readLine()) != null;) {
                if (line.startsWith(key)) {
                    String[] tokens = line.split(" ");
                    if (tokens.length > 1) {
                        try {
                            return Long.parseLong(tokens[1]);
                        } catch (NumberFormatException ex) {
                            LOGGER.log(Level.WARNING, null, ex);
                        }
                    } else {
                        LOGGER.log(Level.WARNING,
                                "could not parse line:\n{0}", line);
                    }
                }
            }
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            LOGGER.log(Level.INFO, "could not load increment size", ex);
        } finally {
            if (fileReader != null) {
                try {
                    fileReader.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
        return 0;
    }

    /**
     * returns a file from a directory with a given prefix
     * @param backupDirectory
     * @param prefix
     * @return
     */
    public static File getRdiffBackupFile(
            String backupDirectory, final String prefix) {

        File rdiffBackupDataDirectory = new File(backupDirectory
                + File.separatorChar + "rdiff-backup-data");

        File[] metaFiles = rdiffBackupDataDirectory.listFiles(
                new FilenameFilter() {

                    public boolean accept(File dir, String name) {
                        return name.startsWith(prefix);
                    }
                });

        if ((metaFiles == null) || metaFiles.length == 0) {
            LOGGER.log(Level.WARNING, "No file with prefix {0} found", prefix);
            return null;
        }

        if (metaFiles.length != 1) {
            LOGGER.log(Level.WARNING,
                    "Several files with prefix {0} found", prefix);
        }

        return metaFiles[0];
    }
}
