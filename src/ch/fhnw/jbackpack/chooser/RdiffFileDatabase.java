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

import ch.fhnw.util.FileTools;
import ch.fhnw.util.ProcessExecutor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/**
 * An embedded database of rdiff-backup filenames and metadata
 *
 * The following tables are used in the database:
 *
 * - mirror timestamp
 *      contains only ONE(!) entry: the timestamp of the mirror, e.g.:
 *       -----------
 *      | TIMESTAMP |
 *      |-----------|
 *      | 34567     |
 *       -----------
 *
 * - increment timestamps
 *      contains the timestamps of the known increments, e.g.:
 *       -----------
 *      | TIMESTAMP |
 *      |-----------|
 *      | 12345     |
 *      |-----------|
 *      | 23456     |
 *       -----------
 *
 * - mirror directories
 *      contains the directories of the mirror, e.g.:
 *       -----------------------
 *      | ID | PARENT_ID | NAME |
 *      |----|-----------|------|
 *      | 1  | 0         |      |
 *      |----|-----------|------|
 *      | 2  | 1         | etc  |
 *      |----|-----------|------|
 *      | 3  | 1         | var  |
 *      |----|-----------|------|
 *      | 4  | 2         | log  |
 *       -----------------------
 *
 * - mirror files
 *      contains the file data of the mirror, e.g.:
 *      the ID column is the id of the directory (see mirror directories table)
 *       ----------------------------------------
 *      | ID | NAME     | TYPE  | SIZE | MODTIME |
 *      |----|----------|-------|------|---------|
 *      | 2  | passwd   | reg   | 123  | 12345   |
 *      |----|----------|-------|------|---------|
 *      | 2  | X11      | dir   | 0    | 23456   |
 *      |----|----------|-------|------|---------|
 *      | 4  | messages | reg   | 345  | 87654   |
 *       ----------------------------------------
 *
 * - increment directories
 *      contains the directories of the increments
 *       -----------------------------------
 *      | ID | TIMESTAMP | PARENT_ID | NAME |
 *      |----|-----------|-----------|------|
 *      | 1  | 12345     | 0         |      |
 *      |----|-----------|-----------|------|
 *      | 2  | 12345     | 1         | etc  |
 *      |----|-----------|-----------|------|
 *      | 3  | 23456     | 0         |      |
 *      |----|-----------|-----------|------|
 *      | 4  | 23456     | 3         | etc  |
 *       -----------------------------------
 *
 * - increment files
 *      contains the file data of the increments
 *      the ID column is the id of the directory
 *      (see increment directories table)
 *       --------------------------------------
 *      | ID | NAME   | TYPE  | SIZE | MODTIME |
 *      |----|--------|-------|------|---------|
 *      | 2  | passwd | reg   | 345  | 2345678 |
 *      |----|--------|-------|------|---------|
 *      | 4  | shadow | none  |      |         |
 *       --------------------------------------
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffFileDatabase {

    /**
     * the different states of the database when syncing
     */
    public enum SyncState {

        /**
         * checking what to delete and insert
         */
        CHECKING,
        /**
         * the database is trimmed
         */
        TRIMMING,
        /**
         * files are synced
         */
        SYNCING,
        /**
         * the database is compressed
         */
        COMPRESSING
    }
    private final static Logger LOGGER =
            Logger.getLogger(RdiffFileDatabase.class.getName());
    // the tables
    private final static String MIRROR_TIMESTAMP_TABLE = "mirror_timestamp";
    private final static String INCREMENT_TIMESTAMPS_TABLE = "increment_timestamps";
    private final static String MIRROR_DIRECTORIES_TABLE = "mirror_directories";
    private final static String MIRROR_FILES_TABLE = "mirror_files";
    private final static String INCREMENT_DIRECTORIES_TABLE = "increment_directories";
    private final static String INCREMENT_FILES_TABLE = "increment_files";
    // the columns
    private final static String ID_COLUMN = "id";
    private final static String PARENT_ID_COLUMN = "parent_id";
    private final static String TIMESTAMP_COLUMN = "timestamp";
    private final static String NAME_COLUMN = "name";
    private final static String TYPE_COLUMN = "type";
    private final static String SIZE_COLUMN = "size";
    private final static String MODTIME_COLUMN = "mod_time";
    private final static String QUOTED_SEPARATOR = Pattern.quote(File.separator);
    private final static DateFormat DATE_FORMAT = DateFormat.getDateTimeInstance();
    private final File backupDirectory;
    private final List<String> rdiffBackupListOutput;
    private final List<DirCacheEntry> mirrorDirectoryIdCache;
    private final List<DirCacheEntry> incrementDirectoryIdCache;
    private Connection connection;
    private PreparedStatement getIncrementsStatement;
    private PreparedStatement getMirrorDirIDStatement;
    private PreparedStatement getMirrorFilesStatement;
    private PreparedStatement getIncrementDirIDStatement;
    private PreparedStatement getIncrementFilesStatement;
    private SyncState syncState = SyncState.CHECKING;
    private int maxIncrementCounter;
    private int incrementCounter;
    private long directoryCounter;
    private long fileCounter;
    private Date currentTimestamp;
    private String currentPath;
    private String currentName;
    private boolean connectionSucceeded;
    private boolean anotherInstanceRunning;
    private List<RdiffTimestamp> fileSystemTimestamps;

    /**
     * Creates a new RdiffFileDatabase
     * @param backupDirectory the backup directory
     * @param databasePath the path to the database
     * @param rdiffBackupListOutput the output of calling
     * "rdiff-backup --parsable-output -l <backupDirectory>"
     * @return a new RdiffFileDatabase
     */
    public static RdiffFileDatabase getInstance(File backupDirectory,
            String databasePath, List<String> rdiffBackupListOutput) {
        return new RdiffFileDatabase(
                backupDirectory, databasePath, rdiffBackupListOutput);
    }

    /**
     * Creates a new RdiffFileDatabase
     * @param backupDirectory the backup directory
     * @return a new RdiffFileDatabase
     */
    public static RdiffFileDatabase getInstance(File backupDirectory) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        processExecutor.executeProcess(true, true, "rdiff-backup",
                "--parsable-output", "-l", backupDirectory.getPath());
        List<String> output = processExecutor.getStdOutList();
        String databasePath = backupDirectory.getPath() + File.separatorChar
                + "rdiff-backup-data" + File.separatorChar + "jbackpack";
        return new RdiffFileDatabase(backupDirectory, databasePath, output);
    }

    private RdiffFileDatabase(File backupDirectory, String databasePath,
            List<String> rdiffBackupListOutput) {

        this.backupDirectory = backupDirectory;
        this.rdiffBackupListOutput = rdiffBackupListOutput;

        mirrorDirectoryIdCache = new ArrayList<DirCacheEntry>();
        incrementDirectoryIdCache = new ArrayList<DirCacheEntry>();

        System.setProperty("derby.storage.pageSize", "32768");
        System.setProperty("derby.stream.error.method",
                "ch.fhnw.jbackpack.chooser.RdiffFileDatabase.disableDerbyLogFile");
        String driver = "org.apache.derby.jdbc.EmbeddedDriver";
        String connectionURL = "jdbc:derby:" + databasePath + ";create=true";

        try {
            Class.forName(driver).newInstance();

            LOGGER.log(Level.INFO, "connecting to database {0}", databasePath);

            connection = DriverManager.getConnection(connectionURL);

            // check if our tables are in the database
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            ResultSet tables = databaseMetaData.getTables(
                    null, null, MIRROR_TIMESTAMP_TABLE.toUpperCase(), null);
            if (tables.next()) {
                LOGGER.fine("jbackpack tables are already there");
            } else {
                LOGGER.info("creating tables for jbackpack");
                // create tables
                Statement statement = connection.createStatement();
                statement.executeUpdate("CREATE TABLE " + MIRROR_TIMESTAMP_TABLE
                        + "(" + TIMESTAMP_COLUMN + " BIGINT)");
                statement.executeUpdate("CREATE TABLE "
                        + INCREMENT_TIMESTAMPS_TABLE + "(" + TIMESTAMP_COLUMN
                        + " BIGINT)");
                statement.executeUpdate("CREATE TABLE "
                        + MIRROR_DIRECTORIES_TABLE + "("
                        + ID_COLUMN + " BIGINT GENERATED ALWAYS AS IDENTITY, "
                        + PARENT_ID_COLUMN + " BIGINT, "
                        + NAME_COLUMN + " VARCHAR(255))");
                statement.executeUpdate("CREATE TABLE " + MIRROR_FILES_TABLE
                        + "(" + ID_COLUMN + " BIGINT, "
                        + NAME_COLUMN + " VARCHAR(255), "
                        + TYPE_COLUMN + " VARCHAR(15), "
                        + SIZE_COLUMN + " BIGINT, "
                        + MODTIME_COLUMN + " BIGINT)");
                statement.executeUpdate("CREATE TABLE "
                        + INCREMENT_DIRECTORIES_TABLE + "(" + ID_COLUMN
                        + " BIGINT GENERATED ALWAYS AS IDENTITY, "
                        + TIMESTAMP_COLUMN + " BIGINT, "
                        + PARENT_ID_COLUMN + " BIGINT, "
                        + NAME_COLUMN + " VARCHAR(255))");
                statement.executeUpdate("CREATE TABLE " + INCREMENT_FILES_TABLE
                        + "(" + ID_COLUMN + " BIGINT, "
                        + NAME_COLUMN + " VARCHAR(255), "
                        + TYPE_COLUMN + " VARCHAR(15), "
                        + SIZE_COLUMN + " BIGINT, "
                        + MODTIME_COLUMN + " BIGINT)");

                // create mirror directories table index
                statement.executeUpdate("CREATE INDEX MirrorDir_Index ON "
                        + MIRROR_DIRECTORIES_TABLE + '('
                        + PARENT_ID_COLUMN + ',' + NAME_COLUMN + ')');

                // create mirror files table index
                statement.executeUpdate("CREATE INDEX MirrorFiles_Index ON "
                        + MIRROR_FILES_TABLE + '(' + ID_COLUMN + ')');

                // create increment directories table indices
                statement.executeUpdate("CREATE INDEX IncDir_Time_Index ON "
                        + INCREMENT_DIRECTORIES_TABLE
                        + '(' + TIMESTAMP_COLUMN + ')');
                statement.executeUpdate(
                        "CREATE INDEX IncDir_TimeParentName_Index ON "
                        + INCREMENT_DIRECTORIES_TABLE + '(' + TIMESTAMP_COLUMN
                        + ',' + PARENT_ID_COLUMN + ',' + NAME_COLUMN + ')');

                // create increment files table indices
                statement.executeUpdate("CREATE INDEX IncFiles_ID_Index ON "
                        + INCREMENT_FILES_TABLE + '(' + ID_COLUMN + ')');
            }
            tables.close();

            // prepare some often-used statements
            getIncrementsStatement = connection.prepareStatement(
                    "SELECT * FROM " + INCREMENT_TIMESTAMPS_TABLE);
            getMirrorDirIDStatement = connection.prepareStatement(
                    "SELECT " + ID_COLUMN + " FROM " + MIRROR_DIRECTORIES_TABLE
                    + " WHERE " + PARENT_ID_COLUMN + "=? AND "
                    + NAME_COLUMN + "=?");
            getMirrorFilesStatement = connection.prepareStatement("SELECT "
                    + NAME_COLUMN + ',' + TYPE_COLUMN + ',' + SIZE_COLUMN + ','
                    + MODTIME_COLUMN + " FROM " + MIRROR_FILES_TABLE
                    + " WHERE " + ID_COLUMN + "=?");
            getIncrementDirIDStatement = connection.prepareStatement("SELECT "
                    + ID_COLUMN + " FROM " + INCREMENT_DIRECTORIES_TABLE
                    + " WHERE " + TIMESTAMP_COLUMN + "=? AND "
                    + PARENT_ID_COLUMN + "=? AND " + NAME_COLUMN + "=?");
            getIncrementFilesStatement = connection.prepareStatement("SELECT "
                    + NAME_COLUMN + ',' + TYPE_COLUMN + ',' + SIZE_COLUMN + ','
                    + MODTIME_COLUMN + " FROM " + INCREMENT_FILES_TABLE
                    + " WHERE " + ID_COLUMN + "=?");

            connectionSucceeded = true;
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            while ("XJ040".equals(ex.getSQLState())) {
                ex = ex.getNextException();
            }
            anotherInstanceRunning = "XSDB6".equals(ex.getSQLState());

        } catch (ClassNotFoundException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * returns <code>true</code>, if the connection to the database was
     * established, <code>false</code> otherwise
     * @return <code>true</code>, if the connection to the database was
     * established, <code>false</code> otherwise
     */
    public boolean isConnected() {
        return connectionSucceeded;
    }

    /**
     * returns <code>true</code>, if another instance of derby is already
     * accessing the database, <code>false</code> otherwise
     * @return <code>true</code>, if another instance of derby is already
     * accessing the database, <code>false</code> otherwise
     */
    public boolean isAnotherInstanceRunning() {
        return anotherInstanceRunning;
    }

    /**
     * syncs the database with the file system
     * @throws SQLException if an SQL exception occurs
     * @throws IOException if an I/O exception occurs
     */
    public void sync() throws SQLException, IOException {

        String backupPath = backupDirectory.getPath();
        fileSystemTimestamps = getFileSystemTimestamps();

        if (fileSystemTimestamps.isEmpty()) {
            LOGGER.log(Level.WARNING,
                    "no rdiff-backup mirror/increments found in \"{0}\"",
                    backupPath);
            return;
        }

        // check if mirror is up-to-date
        boolean deleteMirror = false;
        boolean insertMirror = false;
        Date fileSystemMirrorTimestamp =
                fileSystemTimestamps.get(0).getTimestamp();
        Date databaseMirrorTimestamp = getDatabaseMirrorTimestamp();
        deleteMirror = (databaseMirrorTimestamp != null)
                && (!fileSystemMirrorTimestamp.equals(databaseMirrorTimestamp));
        insertMirror = (databaseMirrorTimestamp == null)
                || (!fileSystemMirrorTimestamp.equals(databaseMirrorTimestamp));

        // determine list of increments in database to delete
        List<Date> databaseIncrementTimestamps =
                getDatabaseIncrementTimestamps();
        List<Date> timestampsToDelete = new ArrayList<Date>();
        for (Date databaseTimestamp : databaseIncrementTimestamps) {
            boolean delete = true;
            for (int i = 1, size = fileSystemTimestamps.size(); i < size; i++) {
                Date fileSystemTimestamp =
                        fileSystemTimestamps.get(i).getTimestamp();
                if (databaseTimestamp.equals(fileSystemTimestamp)) {
                    delete = false;
                    break;
                }
            }
            if (delete) {
                timestampsToDelete.add(databaseTimestamp);
            }
        }

        // check which increments must be added to the database
        List<RdiffTimestamp> timestampsToAdd = new ArrayList<RdiffTimestamp>();
        for (int i = 1, size = fileSystemTimestamps.size(); i < size; i++) {
            RdiffTimestamp rdiffTimestamp = fileSystemTimestamps.get(i);
            Date timestamp = rdiffTimestamp.getTimestamp();
            boolean add = true;
            for (Date databaseTimestamp : databaseIncrementTimestamps) {
                if (databaseTimestamp.equals(timestamp)) {
                    add = false;
                    break;
                }
            }
            if (add) {
                timestampsToAdd.add(rdiffTimestamp);
            }
        }

        boolean databaseChanged = false;

        // delete everything that is no longer needed
        syncState = SyncState.TRIMMING;
        incrementCounter = 0;
        maxIncrementCounter =
                timestampsToDelete.size() + (deleteMirror ? 1 : 0);

        if (deleteMirror) {
            currentTimestamp = databaseMirrorTimestamp;
            incrementCounter++;

            LOGGER.info("deleting old mirror timestamp");
            Statement statement = connection.createStatement();
            statement.executeUpdate("DELETE FROM " + MIRROR_TIMESTAMP_TABLE);
            statement.close();

            LOGGER.info("deleting old mirror directories");
            statement = connection.createStatement();
            statement.executeUpdate("DELETE FROM " + MIRROR_DIRECTORIES_TABLE);
            statement.close();

            LOGGER.info("deleting old mirror files");
            statement = connection.createStatement();
            statement.executeUpdate("DELETE FROM " + MIRROR_FILES_TABLE);
            statement.close();
            databaseChanged = true;
        }

        // delete deprecated increments from database
        if (!timestampsToDelete.isEmpty()) {
            trimIncrements(timestampsToDelete);
            databaseChanged = true;
        }

        syncState = SyncState.SYNCING;
        incrementCounter = 0;
        maxIncrementCounter = timestampsToAdd.size() + (insertMirror ? 1 : 0);
        directoryCounter = 0;
        fileCounter = 0;

        if (insertMirror) {
            LOGGER.info("inserting new mirror timestamp");
            Statement statement = connection.createStatement();
            statement.executeUpdate("INSERT INTO "
                    + MIRROR_TIMESTAMP_TABLE + " VALUES("
                    + (fileSystemMirrorTimestamp.getTime() / 1000) + ")");
            statement.close();

            LOGGER.info("adding file system root into mirror directories");
            statement = connection.createStatement();
            statement.executeUpdate("INSERT INTO " + MIRROR_DIRECTORIES_TABLE
                    + " VALUES(DEFAULT,0,'')");
            statement.close();

            LOGGER.info("inserting new mirror files");
            PreparedStatement insertMirrorDirStatement =
                    connection.prepareStatement("INSERT INTO "
                    + MIRROR_DIRECTORIES_TABLE + " VALUES (DEFAULT,?,?)");
            PreparedStatement insertMirrorFileStatement =
                    connection.prepareStatement("INSERT INTO "
                    + MIRROR_FILES_TABLE + " VALUES (?,?,?,?,?)");
            parseMetaDataFile(backupPath, fileSystemTimestamps.get(0),
                    insertMirrorDirStatement, insertMirrorFileStatement,
                    null, null);

            databaseChanged = true;
        }

        // add new increments
        for (RdiffTimestamp timestampToAdd : timestampsToAdd) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "inserting increment timestamp of {0}",
                        timestampToAdd.getFilestamp());
            }
            Statement statement = connection.createStatement();
            statement.executeUpdate("INSERT INTO "
                    + INCREMENT_TIMESTAMPS_TABLE + " VALUES("
                    + (timestampToAdd.getTimestamp().getTime() / 1000) + ")");
            statement.close();

            incrementDirectoryIdCache.clear();
            PreparedStatement insertIncrementDirStatement =
                    connection.prepareStatement("INSERT INTO "
                    + INCREMENT_DIRECTORIES_TABLE + " VALUES (DEFAULT,?,?,?)");
            PreparedStatement insertIncrementFileStatement =
                    connection.prepareStatement("INSERT INTO "
                    + INCREMENT_FILES_TABLE + " VALUES (?,?,?,?,?)");
            parseMetaDataFile(backupPath, timestampToAdd, null, null,
                    insertIncrementDirStatement, insertIncrementFileStatement);
            databaseChanged = true;
        }

        if (databaseChanged) {
            long start = System.currentTimeMillis();
            LOGGER.log(Level.INFO, "compressing tables");
            syncState = SyncState.COMPRESSING;
            connection.commit();
            CallableStatement cstatement = connection.prepareCall(
                    "CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE('APP', ?, 0)");
            cstatement.setString(1, MIRROR_TIMESTAMP_TABLE.toUpperCase());
            cstatement.execute();
            cstatement.setString(1, MIRROR_DIRECTORIES_TABLE.toUpperCase());
            cstatement.execute();
            cstatement.setString(1, MIRROR_FILES_TABLE.toUpperCase());
            cstatement.execute();
            cstatement.setString(1, INCREMENT_TIMESTAMPS_TABLE.toUpperCase());
            cstatement.execute();
            cstatement.setString(1, INCREMENT_DIRECTORIES_TABLE.toUpperCase());
            cstatement.execute();
            cstatement.setString(1, INCREMENT_FILES_TABLE.toUpperCase());
            cstatement.execute();
            if (LOGGER.isLoggable(Level.INFO)) {
                long time = System.currentTimeMillis() - start;
                LOGGER.log(Level.INFO, "compressing tables took {0} ms", time);
            }
        }
    }

    /**
     * returns the sync state of the database
     * @return the sync state of the database
     */
    public SyncState getSyncState() {
        return syncState;
    }

    /**
     * returns the list of increments
     * @return the list of increments
     */
    public List<Increment> getIncrements() {
        List<Date> timestamps = new ArrayList<Date>();
        try {
            Date databaseMirrorTimestamp = getDatabaseMirrorTimestamp();
            if (databaseMirrorTimestamp != null) {
                timestamps.add(databaseMirrorTimestamp);
            }
            timestamps.addAll(getDatabaseIncrementTimestamps());
        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        Collections.sort(timestamps, Collections.reverseOrder());

        Increment youngerIncrement = null;
        List<Increment> increments = new ArrayList<Increment>();
        for (Date timestamp : timestamps) {
            RdiffTimestamp rdiffTimestamp = null;
            for (RdiffTimestamp fileSystemTimestamp : fileSystemTimestamps) {
                if (fileSystemTimestamp.getTimestamp().equals(timestamp)) {
                    rdiffTimestamp = fileSystemTimestamp;
                }
            }
            Increment increment = new Increment(youngerIncrement,
                    rdiffTimestamp, this, backupDirectory);
            youngerIncrement = increment;
            increments.add(increment);
        }
        return increments;
    }

    /**
     * returns a list of files of a given increment in a given directory
     * @param increment the increment
     * @param directory the directory
     * @return a list of files
     */
    public synchronized List<RdiffFile> listFiles(
            Increment increment, RdiffFile directory) {

        long start = System.currentTimeMillis();

        List<RdiffFile> files = new ArrayList<RdiffFile>();
        try {
            // determine path and directory ID
            String path = null;
            long directoryID = 0;
            if (directory.getParentFile() == null) {
                path = "";
                directoryID = 1;
            } else {
                path = directory.getPath();
                directoryID = getMirrorDirID(path.split(QUOTED_SEPARATOR));
            }

            // start with the mirror file list
            getMirrorFilesStatement.setLong(1, directoryID);
            ResultSet mirrorFiles = getMirrorFilesStatement.executeQuery();
            while (mirrorFiles.next()) {
                String name = mirrorFiles.getString(NAME_COLUMN);
                String type = mirrorFiles.getString(TYPE_COLUMN);
                long size = mirrorFiles.getLong(SIZE_COLUMN);
                long modtime = mirrorFiles.getLong(MODTIME_COLUMN) * 1000;
                files.add(new RdiffFile(this, increment, directory, name,
                        size, modtime, "dir".equals(type)));
            }
            mirrorFiles.close();
            if (LOGGER.isLoggable(Level.INFO)) {
                long time = System.currentTimeMillis() - start;
                LOGGER.log(Level.INFO,
                        "getting mirror files of directory \"{0}\" took {1} ms",
                        new Object[]{path, time});
            }

            if (!increment.isMirror()) {
                // process all increments
                List<Increment> increments = new ArrayList<Increment>();
                for (Increment tmpIncrement = increment; tmpIncrement != null;
                        tmpIncrement = tmpIncrement.getYoungerIncrement()) {
                    if (!tmpIncrement.isMirror()) {
                        increments.add(tmpIncrement);
                    }
                }
                // must start with youngest increment!!!
                for (int i = increments.size() - 1; i >= 0; i--) {
                    replayIncrement(increments.get(i), directory, path, files);
                }
            }
        } catch (SQLException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
        return files;
    }

    /**
     * returns the maximum number of increments to sync
     * @return the maximum number of increments to sync
     */
    public int getMaxIncrementCounter() {
        return maxIncrementCounter;
    }

    /**
     * returns the number of the currently synced increment
     * @return the number of the currently synced increment
     */
    public int getIncrementCounter() {
        return incrementCounter;
    }

    /**
     * returns the currently parsed timestamp while syncing the database
     * @return the currently parsed timestamp while syncing the database
     */
    public Date getCurrentTimestamp() {
        return currentTimestamp;
    }

    /**
     * returns the number of processed directories while syncing the database
     * @return the number of processed directories while syncing the database
     */
    public long getDirectoryCounter() {
        return directoryCounter;
    }

    /**
     * returns the number of processed files while syncing the database
     * @return the number of processed files while syncing the database
     */
    public long getFileCounter() {
        return fileCounter;
    }

    /**
     * returns the currently processed file while syncing the database
     * @return the currently processed file while syncing the database
     */
    public String getCurrentFile() {
        if ((currentPath == null) || (currentName == null)) {
            return "";
        }
        return currentPath + '/' + currentName;
    }

    /**
     * closes the database connection
     */
    public void close() {
        try {
            getIncrementsStatement.close();
            getMirrorDirIDStatement.close();
            getMirrorFilesStatement.close();
            getIncrementDirIDStatement.close();
            getIncrementFilesStatement.close();
            connection.close();
            DriverManager.getConnection("jdbc:derby:;shutdown=true");
        } catch (SQLException ex) {
            if ((ex.getErrorCode() == 50000)
                    && ("XJ015".equals(ex.getSQLState()))) {
                // we got the expected exception
                LOGGER.info("Derby shut down normally");
                // Note that for single database shutdown, the expected
                // SQL state is "08006", and the error code is 45000.
            } else {
                // if the error code or SQLState is different, we have
                // an unexpected exception (shutdown failed)
                LOGGER.log(Level.WARNING,
                        "Derby did not shut down normally", ex);
            }
        }
    }

    /**
     * returns a dummy outputstream that can be used to disable the derby log
     * file
     * @return a dummy outputstream
     */
    public static OutputStream disableDerbyLogFile() {
        return new OutputStream() {

            public void write(int b) throws IOException {
                // Ignore all log messages
            }
        };
    }

    private long getMirrorDirID(String[] directories) throws SQLException {
        long directoryID = 1;
        for (String directory : directories) {
            long tmpID = getMirrorDirID(directoryID, directory);
            if (tmpID == 0) {
                // the directory was not found
                directoryID = 0;
                break;
            } else {
                directoryID = tmpID;
            }
        }
        return directoryID;
    }

    private long getMirrorDirID(long parentID, String name)
            throws SQLException {
        long directoryID = 0;
        getMirrorDirIDStatement.setLong(1, parentID);
        getMirrorDirIDStatement.setString(2, name);
        ResultSet resultSet = getMirrorDirIDStatement.executeQuery();
        if (resultSet.next()) {
            directoryID = resultSet.getLong(ID_COLUMN);
        }
        resultSet.close();
        return directoryID;
    }

    private long getIncrementDirID(Date timestamp, String[] directories)
            throws SQLException {
        long directoryID = getIncrementDirID(timestamp, 0, "");
        for (String directory : directories) {
            long tmpID = getIncrementDirID(timestamp, directoryID, directory);
            if (tmpID == 0) {
                // the directory was not found
                directoryID = 0;
                break;
            } else {
                directoryID = tmpID;
            }
        }
        return directoryID;
    }

    private long getIncrementDirID(Date timestamp, long parentID, String path)
            throws SQLException {
        long directoryID = 0;
        getIncrementDirIDStatement.setLong(1, timestamp.getTime() / 1000);
        getIncrementDirIDStatement.setLong(2, parentID);
        getIncrementDirIDStatement.setString(3, path);
        ResultSet resultSet = getIncrementDirIDStatement.executeQuery();
        if (resultSet.next()) {
            directoryID = resultSet.getLong(ID_COLUMN);
        }
        resultSet.close();
        LOGGER.log(Level.FINE, "directoryID = {0}", directoryID);
        return directoryID;
    }

    private void replayIncrement(Increment increment, RdiffFile directory,
            String directoryPath, List<RdiffFile> files) throws SQLException {

        long start = System.currentTimeMillis();

        Date timestamp = increment.getTimestamp();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "processing increment {0} of directory \"{1}\"",
                    new Object[]{timestamp, directoryPath});
        }

        long directoryID = 0;
        if (directoryPath.isEmpty()) {
            directoryID = getIncrementDirID(timestamp, 0, "");
        } else {
            String[] directories = directoryPath.split(QUOTED_SEPARATOR);
            directoryID = getIncrementDirID(timestamp, directories);
        }

        getIncrementFilesStatement.setLong(1, directoryID);
        ResultSet resultSet = getIncrementFilesStatement.executeQuery();
        while (resultSet.next()) {
            String name = resultSet.getString(NAME_COLUMN);
            if (".".equals(name)) {
                // do not add root entries
                continue;
            }
            String type = resultSet.getString(TYPE_COLUMN);
            if ("None".equals(type)) {
                // remove file
                for (int i = files.size() - 1; i >= 0; i--) {
                    RdiffFile file = files.get(i);
                    if (file.getName().equals(name)) {
                        files.remove(i);
                        break;
                    }
                }
            } else {
                // add/update file
                long size = resultSet.getLong(SIZE_COLUMN);
                long modtime = resultSet.getLong(MODTIME_COLUMN) * 1000;
                boolean updated = false;
                for (int i = 0, j = files.size(); i < j; i++) {
                    RdiffFile file = files.get(i);
                    if (file.getName().equals(name)) {
                        files.set(i, new RdiffFile(this, increment,
                                directory, name, size, modtime,
                                "dir".equals(type)));
                        updated = true;
                        break;
                    }
                }
                if (!updated) {
                    // file was not there, we have to add it
                    files.add(new RdiffFile(this, increment, directory,
                            name, size, modtime, "dir".equals(type)));
                }
            }
        }
        resultSet.close();

        if (LOGGER.isLoggable(Level.INFO)) {
            long time = System.currentTimeMillis() - start;
            LOGGER.log(Level.INFO, "processing increment {0} took {1} ms",
                    new Object[]{timestamp, time});
        }
    }

    private void parseMetaDataFile(String backupPath,
            RdiffTimestamp rdiffTimestamp,
            PreparedStatement insertMirrorDirStatement,
            PreparedStatement insertMirrorFileStatement,
            PreparedStatement insertIncrementDirStatement,
            PreparedStatement insertIncrementFileStatement)
            throws IOException, SQLException {

        long start = System.currentTimeMillis();
        incrementCounter++;
        Date timestamp = rdiffTimestamp.getTimestamp();
        String filestamp = rdiffTimestamp.getFilestamp();
        currentTimestamp = timestamp;
        connection.setAutoCommit(false);
        BufferedReader reader = null;
        try {
            // find the meta data file
            File metaDataFile = Increment.getRdiffBackupFile(backupPath,
                    "mirror_metadata." + filestamp);
            if (metaDataFile == null) {
                LOGGER.log(Level.WARNING,
                        "could not find mirror_metadata file for {0}",
                        filestamp);
                return;
            }
            // read lines from zipped file
            reader = new BufferedReader(new InputStreamReader(
                    new GZIPInputStream(new FileInputStream(metaDataFile))));

            String path = null;
            String type = null;
            long size = 0;
            long modTime = 0;
            for (String line = null; (line = reader.readLine()) != null;) {
                if (line.startsWith("File ")) {
                    if (path != null) {
                        // process previous file
                        processParsedFile(timestamp, path, type, size,
                                modTime, insertMirrorDirStatement,
                                insertMirrorFileStatement,
                                insertIncrementDirStatement,
                                insertIncrementFileStatement);
                    }
                    path = line.substring(5);
                    // reset values of previous file
                    type = null;
                    size = 0;
                    modTime = 0;

                } else if (line.startsWith("  Type ")) {
                    type = line.substring(7);

                } else if (line.startsWith("  Size ")) {
                    size = Long.parseLong(line.substring(7));

                } else if (line.startsWith("  ModTime ")) {
                    modTime = Long.parseLong(line.substring(10));
                }
            }
            // process last file
            processParsedFile(timestamp, path, type, size, modTime,
                    insertMirrorDirStatement, insertMirrorFileStatement,
                    insertIncrementDirStatement, insertIncrementFileStatement);
        } finally {
            connection.commit();
            connection.setAutoCommit(true);
            if (reader != null) {
                reader.close();
            }
        }
        if (LOGGER.isLoggable(Level.INFO)) {
            long time = System.currentTimeMillis() - start;
            LOGGER.log(Level.INFO, "parsing timestamp {0} took {1} ms",
                    new Object[]{filestamp, time});
        }
    }

    private void processParsedFile(Date timestamp,
            String path, String type, long size, long modTime,
            PreparedStatement insertMirrorDirStatement,
            PreparedStatement insertMirrorFileStatement,
            PreparedStatement insertIncrementDirStatement,
            PreparedStatement insertIncrementFileStatement)
            throws SQLException {

        fileCounter++;
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex == -1) {
            currentPath = "";
            currentName = path;
        } else {
            currentPath = path.substring(0, lastSlashIndex);
            currentName = path.substring(lastSlashIndex + 1);
        }

        if (insertMirrorDirStatement != null) {
            // this file goes into the mirror tables

            // ignore file system root
            // (it was already added explicitly per default)
            if ("".equals(currentPath) && ".".equals(currentName)) {
                return;
            }

            long directoryID = insertMirrorDir(insertMirrorDirStatement);
            insertFile(insertMirrorFileStatement,
                    directoryID, type, size, modTime);

        } else {
            // this file goes into the increment tables

            // special handling of file system root
            // (only insert currentPath to increment directories table)
            if ("".equals(currentPath) && ".".equals(currentName)) {
                insertIncrementDir(timestamp, insertIncrementDirStatement);
                return;
            }

            long directoryID = insertIncrementDir(
                    timestamp, insertIncrementDirStatement);
            insertFile(insertIncrementFileStatement,
                    directoryID, type, size, modTime);
        }
    }

    private void insertFile(PreparedStatement insertFileStatement,
            long directoryID, String type, long size, long modTime)
            throws SQLException {
        insertFileStatement.setLong(1, directoryID);
        insertFileStatement.setString(2, currentName);
        insertFileStatement.setString(3, type);
        insertFileStatement.setLong(4, size);
        insertFileStatement.setLong(5, modTime);
        int rowCount = insertFileStatement.executeUpdate();
        if (rowCount == 1) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "added file {0}/{1}",
                        new Object[]{currentPath, currentName});
            }
        } else {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING,
                        "could not insert file {0}/{1} to database",
                        new Object[]{currentPath, currentName});
            }
        }
    }

    private long insertMirrorDir(PreparedStatement insertMirrorDirStatement)
            throws SQLException {
        long directoryID = 1;
        if (!currentPath.isEmpty()) {
            String[] directories = currentPath.split("/");
            for (int i = 0, length = directories.length; i < length; i++) {
                String directory = directories[i];
                if (i < mirrorDirectoryIdCache.size()) {
                    DirCacheEntry entry = mirrorDirectoryIdCache.get(i);
                    if (directory.equals(entry.directory)) {
                        // cache hit
                        directoryID = entry.ID;
                    } else {
                        // cache miss
                        directoryID = getOrAddMirrorDir(directoryID, directory,
                                insertMirrorDirStatement);
                        //  update cache position
                        entry.directory = directory;
                        entry.ID = directoryID;
                        //  trim cache
                        for (int j = mirrorDirectoryIdCache.size() - 1;
                                j > i; j--) {
                            mirrorDirectoryIdCache.remove(j);
                        }
                    }

                } else {
                    directoryID = getOrAddMirrorDir(directoryID, directory,
                            insertMirrorDirStatement);
                    DirCacheEntry entry = new DirCacheEntry();
                    entry.directory = directory;
                    entry.ID = directoryID;
                    mirrorDirectoryIdCache.add(entry);
                }
            }
        }
        return directoryID;
    }

    private long insertIncrementDir(Date timestamp,
            PreparedStatement insertIncrementDirStatement) throws SQLException {

        long directoryID = getOrAddIncrementDir(
                timestamp, 0, "", insertIncrementDirStatement);
        if (!currentPath.isEmpty()) {
            String[] directories = currentPath.split("/");
            for (int i = 0, length = directories.length; i < length; i++) {
                String directory = directories[i];
                if (i < incrementDirectoryIdCache.size()) {
                    DirCacheEntry entry = incrementDirectoryIdCache.get(i);
                    if (directory.equals(entry.directory)) {
                        // cache hit
                        directoryID = entry.ID;
                    } else {
                        // cache miss
                        directoryID = getOrAddIncrementDir(
                                timestamp, directoryID, directory,
                                insertIncrementDirStatement);
                        //  update cache position
                        entry.directory = directory;
                        entry.ID = directoryID;
                        //  trim cache
                        for (int j = incrementDirectoryIdCache.size() - 1;
                                j > i; j--) {
                            incrementDirectoryIdCache.remove(j);
                        }
                    }

                } else {
                    directoryID = getOrAddIncrementDir(
                            timestamp, directoryID, directory,
                            insertIncrementDirStatement);
                    DirCacheEntry entry = new DirCacheEntry();
                    entry.directory = directory;
                    entry.ID = directoryID;
                    incrementDirectoryIdCache.add(entry);
                }
            }
        }
        return directoryID;
    }

    private long getOrAddMirrorDir(long parentID, String directory,
            PreparedStatement insertMirrorDirStatement) throws SQLException {
        long directoryID = getMirrorDirID(parentID, directory);
        if (directoryID == 0) {
            insertMirrorDirStatement.setLong(1, parentID);
            insertMirrorDirStatement.setString(2, directory);
            int rowCount = insertMirrorDirStatement.executeUpdate();
            if (rowCount == 1) {
                directoryCounter++;
                LOGGER.log(Level.FINE, "added mirror directory {0}", directory);
            } else {
                LOGGER.log(Level.WARNING,
                        "could not insert mirror directory {0} to database",
                        directory);
            }
            directoryID = getMirrorDirID(parentID, directory);
        }
        return directoryID;
    }

    private long getOrAddIncrementDir(Date timestamp, long parentID,
            String directory, PreparedStatement insertIncrementDirStatement)
            throws SQLException {
        long directoryID = getIncrementDirID(timestamp, parentID, directory);
        if (directoryID == 0) {
            insertIncrementDirStatement.setLong(1, timestamp.getTime() / 1000);
            insertIncrementDirStatement.setLong(2, parentID);
            insertIncrementDirStatement.setString(3, directory);
            int rowCount = insertIncrementDirStatement.executeUpdate();
            if (rowCount == 1) {
                directoryCounter++;
                LOGGER.log(Level.FINE,
                        "added increment directory {0}", directory);
            } else {
                LOGGER.log(Level.WARNING,
                        "could not insert increment directory {0} to database",
                        directory);
            }
            directoryID = getIncrementDirID(timestamp, parentID, directory);
        }
        return directoryID;
    }

    private void trimIncrements(List<Date> timestampsToDelete)
            throws SQLException {

        long trimStart = System.currentTimeMillis();
        PreparedStatement deleteTimestampStatement =
                connection.prepareStatement(
                "DELETE FROM " + INCREMENT_TIMESTAMPS_TABLE
                + " WHERE " + TIMESTAMP_COLUMN + "=?");
        PreparedStatement deleteFilesStatement =
                connection.prepareStatement("DELETE FROM "
                + INCREMENT_FILES_TABLE + " WHERE " + ID_COLUMN
                + " IN (SELECT " + ID_COLUMN
                + " FROM " + INCREMENT_DIRECTORIES_TABLE
                + " WHERE " + TIMESTAMP_COLUMN + "=?)");
        PreparedStatement deleteDirectoriesStatement =
                connection.prepareStatement(
                "DELETE FROM " + INCREMENT_DIRECTORIES_TABLE
                + " WHERE " + TIMESTAMP_COLUMN + "=?");

        for (Date timestampToDelete : timestampsToDelete) {
            LOGGER.log(Level.INFO, "deleting increment timestamp {0}",
                    DATE_FORMAT.format(timestampToDelete));
            incrementCounter++;
            long rdiffTimestamp = timestampToDelete.getTime() / 1000;
            deleteTimestampStatement.setLong(1, rdiffTimestamp);
            int rowCount = deleteTimestampStatement.executeUpdate();
            if (rowCount != 1) {
                LOGGER.log(Level.WARNING,
                        "could not remove timestamp {0} from database",
                        DATE_FORMAT.format(timestampToDelete));
            }

            // delete files before directories!!!
            // (otherwise it would be impossible to find the directory IDs)
            long start = System.currentTimeMillis();
            deleteFilesStatement.setLong(1, rdiffTimestamp);
            rowCount = deleteFilesStatement.executeUpdate();
            if (LOGGER.isLoggable(Level.INFO)) {
                long time = System.currentTimeMillis() - start;
                LOGGER.log(Level.INFO, "removing {0} files took {1} ms",
                        new Object[]{rowCount, time});
            }
            start = System.currentTimeMillis();
            deleteDirectoriesStatement.setLong(1, rdiffTimestamp);
            rowCount = deleteDirectoriesStatement.executeUpdate();
            if (LOGGER.isLoggable(Level.INFO)) {
                long time = System.currentTimeMillis() - start;
                LOGGER.log(Level.INFO,
                        "removing {0} directories took {1} ms",
                        new Object[]{rowCount, time});
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            long time = System.currentTimeMillis() - trimStart;
            LOGGER.log(Level.INFO, "trimming database took {0} ms", time);
        }
    }

    private List<RdiffTimestamp> getFileSystemTimestamps() {

        // get timestamps
        List<Long> timestamps = new ArrayList<Long>();
        for (String line : rdiffBackupListOutput) {
            String[] tokens = line.split(" ");
            if (tokens.length == 2) {
                String timestampString = tokens[0];
                try {
                    long timestamp = Long.parseLong(timestampString);
                    timestamps.add(timestamp);
                } catch (NumberFormatException numberFormatException) {
                    LOGGER.log(Level.WARNING,
                            "could not parse timestamp" + timestampString,
                            numberFormatException);
                }
            } else {
                LOGGER.log(Level.WARNING,
                        "could not parse line \"{0}\"", line);
            }
        }
        Collections.sort(timestamps, Collections.reverseOrder());
        if (LOGGER.isLoggable(Level.INFO)) {
            StringBuilder stringBuilder =
                    new StringBuilder("filesystem timestamps:\n");
            for (long timestamp : timestamps) {
                stringBuilder.append("\t");
                stringBuilder.append(String.valueOf(timestamp));
                stringBuilder.append("\n");
            }
            LOGGER.info(stringBuilder.toString());
        }

        // map timestamps
        // (look into the session statistics files)
        List<RdiffTimestamp> rdiffTimestamps = new ArrayList<RdiffTimestamp>();
        File rdiffBackupDataDirectory =
                new File(backupDirectory, "rdiff-backup-data");
        File[] statisticsFiles = rdiffBackupDataDirectory.listFiles(
                new FilenameFilter() {

                    public boolean accept(File dir, String name) {
                        return name.startsWith("session_statistics.");
                    }
                });
        for (Long timestamp : timestamps) {
            for (File statisticsFile : statisticsFiles) {
                try {
                    List<String> lines = FileTools.readFile(statisticsFile);
                    for (String line : lines) {
                        if (line.startsWith("StartTime")) {
                            String timeString =
                                    line.split(" ")[1].split("\\.")[0];
                            if (timestamp.toString().equals(timeString)) {
                                String fileName = statisticsFile.getName();
                                String fileStamp = fileName.split("\\.")[1];
                                rdiffTimestamps.add(new RdiffTimestamp(
                                        new Date(timestamp * 1000), fileStamp));
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            StringBuilder stringBuilder =
                    new StringBuilder("rdiffTimestamps:\n");
            for (RdiffTimestamp timestamp : rdiffTimestamps) {
                stringBuilder.append("\t");
                stringBuilder.append(String.valueOf(timestamp.getTimestamp()));
                stringBuilder.append(" (");
                stringBuilder.append(timestamp.getFilestamp());
                stringBuilder.append(")\n");
            }
            LOGGER.info(stringBuilder.toString());
        }

        return rdiffTimestamps;
    }

    private Date getDatabaseMirrorTimestamp() throws SQLException {
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("SELECT "
                + TIMESTAMP_COLUMN + " FROM " + MIRROR_TIMESTAMP_TABLE);
        if (resultSet.next()) {
            Date databaseMirrorTimeStamp = new Date(
                    resultSet.getLong(TIMESTAMP_COLUMN) * 1000);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "database mirror timestamp: {0}",
                        DATE_FORMAT.format(databaseMirrorTimeStamp));
            }
            statement.close();
            return databaseMirrorTimeStamp;
        }
        statement.close();
        return null;
    }

    private List<Date> getDatabaseIncrementTimestamps() throws SQLException {
        List<Date> timestamps = new ArrayList<Date>();
        ResultSet resultSet = getIncrementsStatement.executeQuery();
        while (resultSet.next()) {
            timestamps.add(new Date(resultSet.getLong(TIMESTAMP_COLUMN) * 1000));
        }
        resultSet.close();
        return timestamps;
    }

    private static class DirCacheEntry {

        public String directory;
        public long ID;
    }
}
