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

import ch.fhnw.jbackpack.chooser.RdiffFile;
import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.FileTools;
import ch.fhnw.util.OperatingSystem;
import ch.fhnw.util.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author mw
 */
public class RdiffBackupRestore {

    /**
     * the different states of restoring
     */
    public enum RestoreState {

        /**
         * counting files in selected directories
         */
        Counting,
        /**
         * restoring selected files
         */
        Restoring
    }
    private static final Logger LOGGER =
            Logger.getLogger(RdiffBackupRestore.class.getName());
    private static final String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private static final String PROCESSING_STRING = "Processing changed file ";
    private static final String APPLYING_PATCH_STRING = "Applying patch ";
    private ScheduledExecutorService scheduler;
    private ProcessExecutor processExecutor;
    private String currentFile;
    private AtomicLong fileCounter = new AtomicLong();
    private AtomicLong restoreCounter = new AtomicLong();
    private RestoreState restoreState;
    private File includesFile;
    private File excludesFile;

    /**
     * quotes the given <code>path</code> so that it can be used in rdiff-backup
     * exclude and include statements in backup operations
     * @param baseDirectory the base directory of this path
     * @param path the path to quote
     * @return the quoted path
     */
    public static String quoteBackup(String baseDirectory, String path) {
        if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
            /**
             * Only Windows paths are affected because they contain "\" as path
             * separator which collides with "\" also being the escape character
             * for the regular expressions in rdiff-backup's exclude and include
             * statements.
             * The following rules apply on Windows:
             * - the backslashes in the base directory must be escaped
             *   ("\" -> "\\")
             * - the backslashes in the remainder must be converted to slashes
             *   ("\" -> "/")
             */
            String remainder = path.substring(baseDirectory.length());
            baseDirectory = baseDirectory.replace("\\", "\\\\");
            remainder = remainder.replace("\\", "/");
            path = baseDirectory + remainder;
        }
        return path;
    }

    /**
     * backs up the selected files via a file system
     * @param source the directory to backup
     * @param destination the backup destination
     * @param tempDirPath the path to the temporary directory or
     * <tt>null</tt> if the system default should be used
     * @param excludes the files to exclude
     * @param includes the files to include
     * @param compressFiles if <tt>true</tt>, most increment files are
     * compressed
     * @param maxFileSize the maximum file size or null if not set
     * @param minFileSize the minimum file size or null if not set
     * @param excludeDeviceFiles if <tt>true</tt>, device files are excluded
     * @param excludeFifos if <tt>true</tt>, fifos are excluded
     * @param excludeOtherFileSystems if <tt>true</tt>, other file systems are
     * excluded
     * @param excludeSockets if <tt>true</tt>, sockets are excluded
     * @param excludeSymlinks if <tt>true</tt>, symlinks are excluded
     * @return if rdiff-backup process was successful
     * @throws IOException if the backup script could not be written to a temp
     * file
     */
    public boolean backupViaFileSystem(File source, File destination,
            String tempDirPath, String excludes, String includes,
            boolean compressFiles, Long maxFileSize, Long minFileSize,
            boolean excludeDeviceFiles, boolean excludeFifos,
            boolean excludeOtherFileSystems, boolean excludeSockets,
            boolean excludeSymlinks) throws IOException {

        // create command list
        List<String> commandList = createBackupCommandList(source, includes,
                excludes, tempDirPath, maxFileSize, minFileSize, compressFiles,
                excludeDeviceFiles, excludeFifos, excludeOtherFileSystems,
                excludeSockets, excludeSymlinks);
        commandList.add(destination.getPath());

        // execute backup command
        String[] commandArray = new String[commandList.size()];
        commandArray = commandList.toArray(commandArray);
        // do NOT(!) store stdOut, it very often leads to:
        // java.lang.OutOfMemoryError: Java heap space
        int returnValue =
                processExecutor.executeProcess(false, true, commandArray);

        // cleanup
        deleteIncludeExcludeFiles();

        return (returnValue == 0);
    }

    /**
     * backs up the selected files via SSH
     * @param source the directory to backup
     * @param user the user name on the remote server
     * @param host the host name of the remote server
     * @param directory the remote backup directory
     * @param password the password of the user on the remote server
     * @param tempDirPath the path to the temporary directory or
     * <tt>null</tt> if the system default should be used
     * @param excludes the files to exclude
     * @param includes the files to include
     * @param compressFiles if <tt>true</tt>, most increment files are
     * compressed
     * @param maxFileSize the maximum file size or null if not set
     * @param minFileSize the minimum file size or null if not set
     * @param excludeDeviceFiles if <tt>true</tt>, device files are excluded
     * @param excludeFifos if <tt>true</tt>, fifos are excluded
     * @param excludeOtherFileSystems if <tt>true</tt>, other file systems are
     * excluded
     * @param excludeSockets if <tt>true</tt>, sockets are excluded
     * @param excludeSymlinks if <tt>true</tt>, symlinks are excluded
     * @return if rdiff-backup process was successful
     * @throws IOException if the backup script could not be written to a temp
     * file
     */
    public boolean backupViaSSH(File source, String user, String host,
            String directory, String password, String tempDirPath,
            String excludes, String includes, boolean compressFiles,
            Long maxFileSize, Long minFileSize, boolean excludeDeviceFiles,
            boolean excludeFifos, boolean excludeOtherFileSystems,
            boolean excludeSockets, boolean excludeSymlinks)
            throws IOException {

        // create command list
        List<String> commandList = createBackupCommandList(source, includes,
                excludes, tempDirPath, maxFileSize, minFileSize, compressFiles,
                excludeDeviceFiles, excludeFifos, excludeOtherFileSystems,
                excludeSockets, excludeSymlinks);
        commandList.add(user + '@' + host + "::" + directory);

        // wrap command list with backup script
        StringBuilder stringBuilder = new StringBuilder();
        for (String command : commandList) {
            stringBuilder.append(command);
            stringBuilder.append(' ');
        }
        String backupScript = "#!/usr/bin/expect -f" + LINE_SEPARATOR
                + "set password [lindex $argv 0]" + LINE_SEPARATOR
                + "spawn -ignore HUP " + stringBuilder.toString() + LINE_SEPARATOR
                + "while 1 {" + LINE_SEPARATOR
                + "    expect {" + LINE_SEPARATOR
                + "        eof {" + LINE_SEPARATOR
                + "            break" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "        \"continue connecting*\" {" + LINE_SEPARATOR
                + "            send \"yes\r\"" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "        \"" + user + '@' + host + "'s password:\" {"
                + LINE_SEPARATOR
                + "            send \"$password\r\"" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "    }" + LINE_SEPARATOR
                + "}" + LINE_SEPARATOR
                + "set ret [lindex [wait] 3]" + LINE_SEPARATOR
                + "puts \"return value: $ret\"" + LINE_SEPARATOR
                + "exit $ret";

//        // set level to OFF to prevent password leaking into
//        // logfiles
//        Logger logger = Logger.getLogger(
//                ProcessExecutor.class.getName());
//        Level level = logger.getLevel();
//        logger.setLevel(Level.OFF);

        // do NOT(!) store stdOut, it very often leads to
        // java.lang.OutOfMemoryError: Java heap space
        int returnValue = processExecutor.executeScript(
                false, true, backupScript, password);

//        // restore previous log level
//        logger.setLevel(level);

        // cleanup
        deleteIncludeExcludeFiles();

        return (returnValue == 0);
    }

    /**
     * Restores the selected files
     * @param rdiffTimestamp the timestamp used for restoring
     * @param selectedFiles the files selected for restoring
     * @param backupDirectory the backup directory (where to get the files to
     * restore from)
     * @param restoreDirectory the directory where to put the files to restore
     * into
     * @param tempDirPath the path to the temporary directory or
     * <tt>null</tt> if the system default should be used
     * @param countFiles if <tt>true</tt>, files are counted before starting the
     * restore operation
     * @return <tt>true</tt>, if restoring was successfull, <tt>false</tt>
     * otherwise
     * @throws IOException if the restore script could not be written to a temp
     * file
     */
    public boolean restore(String rdiffTimestamp, RdiffFile[] selectedFiles,
            File backupDirectory, File restoreDirectory, String tempDirPath,
            boolean countFiles) throws IOException {

        // reset status
        processExecutor = new ProcessExecutor();
        currentFile = "";
        fileCounter.set(0);
        restoreCounter.set(0);
        if (countFiles) {
            restoreState = RestoreState.Counting;
            for (File selectedFile : selectedFiles) {
                countRestoreFiles(selectedFile);
            }
        }
        restoreState = RestoreState.Restoring;

        // parse rdiff-backup output
        processExecutor.addPropertyChangeListener(
                new MyPropertyChangeListener());

        String restorePath = restoreDirectory.getPath();

        String includes = null;
        String excludes = null;
        includesFile = null;
        excludesFile = null;
        // only specify includes and excludes when restoring selected files
        if ((selectedFiles.length != 1)
                || (selectedFiles[0].getParentFile() != null)) {
            // add files to restore to the list of included files
            StringBuilder stringBuilder = new StringBuilder();
            for (int i = 0, length = selectedFiles.length; i < length; i++) {
                RdiffFile selectedFile = selectedFiles[i];
                if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
                    // rdiff-backup on Windows fails when trying to overwrite
                    // a file in a subsubdirectory, e.g. with the source "C:\"
                    // restoring "C:\dir\subdir\file" fails
                    File realFile =
                            new File(restoreDirectory, selectedFile.getPath());
                    if (realFile.exists()) {
                        if (!FileTools.recursiveDelete(realFile)) {
                            LOGGER.log(Level.WARNING,
                                    "could not delete {0}", realFile);
                        }
                    }
                }
                stringBuilder.append(quoteRestore(restorePath));
                stringBuilder.append('/');
                if (selectedFile.getParentFile() == null) {
                    // special handling for file system roots
                    stringBuilder.append("*");
                } else {
                    String absolutePath = selectedFile.getAbsolutePath();
                    stringBuilder.append(quoteRestore(absolutePath));
                }
                if (i != length - 1) {
                    stringBuilder.append(LINE_SEPARATOR);
                }
            }
            includes = stringBuilder.toString();

            // exclude everything else
            excludes = quoteRestore(restorePath) + "/**";

            includesFile = File.createTempFile("jbackpack_includes_", null);
            excludesFile = File.createTempFile("jbackpack_excludes_", null);
        }

        List<String> commandList = createCommandList(tempDirPath,
                includesFile, includes, excludesFile, excludes);
        commandList.add("--force");
        commandList.add("-r");
        commandList.add(rdiffTimestamp);
        commandList.add(backupDirectory.getPath());
        commandList.add(quoteRestorePath(restorePath));
        String[] commandArray = new String[commandList.size()];
        commandArray = commandList.toArray(commandArray);
        // do NOT(!) store stdOut, it very often leads to:
        // java.lang.OutOfMemoryError: Java heap space
        int returnValue =
                processExecutor.executeProcess(false, true, commandArray);
        boolean success = (returnValue == 0);
        if (success) {
            // delete [in/ex]clude files only when everything worked
            if ((includesFile != null) && !includesFile.delete()) {
                LOGGER.log(Level.WARNING, "could not delete {0}", includesFile);
            }
            if ((excludesFile != null) && !excludesFile.delete()) {
                LOGGER.log(Level.WARNING, "could not delete {0}", excludesFile);
            }
        }
        return success;
    }

    /**
     * @return the restoreState
     */
    public RestoreState getRestoreState() {
        return restoreState;
    }

    /**
     * returns the number of files to restore
     * @return the number of files to restore
     */
    public long getRestoreCounter() {
        return restoreCounter.get();
    }

    /**
     * Cancel the current running rdiff-backup process
     */
    public void cancelRdiffOperation() {
        if (processExecutor != null) {
            processExecutor.destroy();
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }

    /**
     * returns the path of the currently processed file
     * @return the path of the currently processed file
     */
    public String getCurrentFile() {
        return currentFile;
    }

    /**
     * returns the number of processed files
     * @return the number of processed files
     */
    public long getFileCounter() {
        return fileCounter.get();
    }

    /**
     * returns the standard output of the last called process
     * @return the standard output of the last called process
     */
    public String getStdOut() {
        return processExecutor.getStdOut();
    }

    /**
     * returns the standard error of the last called process
     * @return the standard error of the last called process
     */
    public String getStdErr() {
        return processExecutor.getStdErr();
    }

    /**
     * Get the output of this rdiff-backup session
     * @param backupDirectory The path to the backup directory
     * @return The output of the rdiff-backup session
     */
    public Map<String, String> getBackupSessionStatistics(
            String backupDirectory) {
        HashMap<String, String> sessionStatistics =
                new HashMap<String, String>();
        String sessionName = "session_statistics"
                + getCurrentMirror(backupDirectory).getName().replaceAll(
                "current_mirror", "");

        FileReader fileReader = null;
        BufferedReader bufferedReader = null;

        try {
            fileReader = new FileReader(backupDirectory + File.separator
                    + "rdiff-backup-data" + File.separator + sessionName);
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "Reading statistics data from:"
                        + "{0}{1}rdiff-backup-data{2}{3}",
                        new Object[]{backupDirectory, File.separator,
                            File.separator, sessionName});
            }
            bufferedReader = new BufferedReader(fileReader);
            for (String line; (line = bufferedReader.readLine()) != null;) {

                String[] tokens = line.split(" ");
                if (tokens.length > 1) {
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO, "Storing (key,value):{0},{1}",
                                new Object[]{tokens[0], tokens[1]});
                    }
                    sessionStatistics.put(tokens[0], tokens[1]);
                }
            }
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
        return sessionStatistics;
    }

    private List<String> createBackupCommandList(File source,
            String includes, String excludes, String tempDirPath,
            Long maxFileSize, Long minFileSize, boolean compressFiles,
            boolean excludeDeviceFiles, boolean excludeFifos,
            boolean excludeOtherFileSystems, boolean excludeSockets,
            boolean excludeSymlinks) throws IOException {

        // reset status
        processExecutor = new ProcessExecutor();
        currentFile = "";
        fileCounter.set(0);

        // add rdiff-backup output parser
        processExecutor.addPropertyChangeListener(
                new MyPropertyChangeListener());

        // execute the backup process
        if ((includes != null) && includes.length() > 0) {
            includesFile = File.createTempFile("jbackpack_includes_", null);
        } else {
            includesFile = null;
        }
        if ((excludes != null) && excludes.length() > 0) {
            excludesFile = File.createTempFile("jbackpack_excludes_", null);
        } else {
            excludesFile = null;
        }
        List<String> commandList = createCommandList(tempDirPath,
                includesFile, includes, excludesFile, excludes);
        if (maxFileSize != null) {
            commandList.add("--max-file-size");
            commandList.add(String.valueOf(maxFileSize));
        }
        if (minFileSize != null) {
            commandList.add("--min-file-size");
            commandList.add(String.valueOf(minFileSize));
        }
        if (excludeDeviceFiles) {
            commandList.add("--exclude-device-files");
        }
        if (excludeFifos) {
            commandList.add("--exclude-fifos");
        }
        if (excludeOtherFileSystems) {
            commandList.add("--exclude-other-filesystems");
        }
        if (excludeSockets) {
            commandList.add("--exclude-sockets");
        }
        if (excludeSymlinks) {
            commandList.add("--exclude-symbolic-links");
        }
        if (!compressFiles) {
            commandList.add("--no-compression");
        }

        String sourcePath = source.getPath();
        if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
            // Windows needs a path workaround. For more details see:
            // http://wiki.rdiff-backup.org/wiki/index.php/BackupToFromWindowsToLinux#Path_Workarounds
            sourcePath += '/';
        }

        commandList.add(sourcePath);
        return commandList;
    }

    private void deleteIncludeExcludeFiles() {
        if ((includesFile != null) && (!includesFile.delete())) {
            LOGGER.log(Level.WARNING, "could not delete {0}", includesFile);
        }
        if ((excludesFile != null) && (!excludesFile.delete())) {
            LOGGER.log(Level.WARNING, "could not delete {0}", excludesFile);
        }
    }

    private List<String> createCommandList(String tempDirPath,
            File includesFile, String includes,
            File excludesFile, String excludes) {

        List<String> commandList = new ArrayList<String>();
        commandList.add("rdiff-backup");
        commandList.add("--terminal-verbosity");
        commandList.add("7");

        if (tempDirPath != null) {
            commandList.add("--tempdir");
            commandList.add(tempDirPath);
        }

        // !!! includes must be defined before excludes !!!
        if ((includes != null) && includes.length() > 0) {
            writeTempFile(includesFile, includes);
            commandList.add("--include-globbing-filelist");
            commandList.add(includesFile.getPath());
        }
        if ((excludes != null) && excludes.length() > 0) {
            writeTempFile(excludesFile, excludes);
            commandList.add("--exclude-globbing-filelist");
            commandList.add(excludesFile.getPath());
        }

        return commandList;
    }

    private File getCurrentMirror(String backupDst) {
        File rdiffBackupDataDirectory = new File(
                backupDst + File.separator + "rdiff-backup-data");
        if (!rdiffBackupDataDirectory.exists()) {
            return null;
        }
        File[] currentBackup = rdiffBackupDataDirectory.listFiles(
                new FilenameFilter() {

                    public boolean accept(File dir, String name) {
                        return name.matches("current_mirror.*");
                    }
                });
        return (currentBackup.length == 0 ? null : currentBackup[0]);
    }

    private void countRestoreFiles(File file) {
        currentFile = file.getAbsolutePath();
        restoreCounter.incrementAndGet();
        if (file.isDirectory()) {
            for (File subFile : file.listFiles()) {
                countRestoreFiles(subFile);
            }
        }
    }

    private void writeTempFile(File tmpFile, String content) {
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(tmpFile);
            fileWriter.write(content);
            fileWriter.flush();
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private static String quoteRestorePath(String restorePath) {
        if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
            restorePath = restorePath.replace("\\", "/");
            if (restorePath.startsWith("//")) {
                // this is a network path, e.g.
                // "\\VBOXSVR\root\tmp\rdiff-backup-test\source"
                // rdiff-backup wants to have it like this:
                // "\\VBOXSVR/root/tmp/rdiff-backup-test/source"
                restorePath = restorePath.replace("//", "\\\\");
            } else {
                // this is a normal drive letter path, e.g.
                // "C:\tmp\rdiff-backup-test\source"
                // rdiff-backup wants to have it like this:
                // "C:\/tmp/rdiff-backup-test/source"
                restorePath = restorePath.replace(":", ":\\");
            }
        }
        // make sure that it ends with "/", otherwise rdiff-backup fails under
        // certain conditions (especially on Windows...)
        if (!restorePath.endsWith("/")) {
            // do NOT(!) use File.separatorChar, because on Windows this
            // means "\", which rdiff-backup treats as escape character
            // which makes rdiff-backup fail...
            restorePath += '/';
        }
        return restorePath;
    }

    private static String quoteRestore(String restorePath) {
        if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
            restorePath = restorePath.replace("\\", "/");
            if (restorePath.startsWith("//")) {
                // this is a network path, e.g.
                // "\\VBOXSVR\root\tmp\rdiff-backup-test\source"
                // rdiff-backup wants to have it like this:
                // "\\VBOXSVR/root/tmp/rdiff-backup-test/source"
                restorePath = restorePath.replace("//", "\\\\\\\\");
            } else {
                // this is a normal drive letter path, e.g.
                // "C:\tmp\rdiff-backup-test\source"
                // rdiff-backup wants to have it like this:
                // "C:\/tmp/rdiff-backup-test/source"
                restorePath = restorePath.replace(":", ":\\\\");
            }
        }
        return restorePath;
    }

    private class MyPropertyChangeListener implements PropertyChangeListener {

        public void propertyChange(PropertyChangeEvent evt) {
            Object newValue = evt.getNewValue();
            if (newValue instanceof String) {
                String output = (String) newValue;
                if (output.startsWith(PROCESSING_STRING)) {
                    currentFile = output.substring(PROCESSING_STRING.length());
                    fileCounter.incrementAndGet();
                } else if (output.startsWith(APPLYING_PATCH_STRING)) {
                    // output looks like this:
                    // Applying patch .VirtualBox/VDI/XP.vdi.2010-01-20T15:11:46+01:00.diff.gz
                    currentFile =
                            output.substring(APPLYING_PATCH_STRING.length());
                }
            }
        }
    }
}
