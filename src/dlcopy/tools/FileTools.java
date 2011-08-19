package dlcopy.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JProgressBar;

/**
 * some file tools
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class FileTools {

    private static final Logger LOGGER =
            Logger.getLogger(FileTools.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "dlcopy/Strings");
    private static final String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private final static NumberFormat NUMBER_FORMAT =
            NumberFormat.getInstance();
    private static final long UNKNOWN_SPACE = 1073741824000L;

    /**
     * shows space information about a given file on a progressbar
     * @param file a given file
     * @param progressBar the progressbar where to display the space information
     * about the given file
     */
    public static void showSpaceInfo(File file, JProgressBar progressBar) {
        if (isSpaceKnown(file)) {
            long totalSpace = file.getTotalSpace();
            if (totalSpace == 0) {
                progressBar.setValue(0);
                progressBar.setString("");
            } else {
                long usedSpace = totalSpace - file.getUsableSpace();
                progressBar.setValue((int) ((usedSpace * 100) / totalSpace));
                String usedSpaceString = getDataVolumeString(usedSpace, 1);
                String totalSpaceString = getDataVolumeString(totalSpace, 1);
                String text = BUNDLE.getString("Free_Space");
                text = MessageFormat.format(
                        text, usedSpaceString, totalSpaceString);
                progressBar.setString(text);
            }
        } else {
            progressBar.setValue(0);
            progressBar.setString(BUNDLE.getString("Unknown"));
        }
    }

    /**
     * recusively deletes a file
     * @param file the file to delete
     * @param removeFile if the file (directory) itself should be removed or
     * just its subfiles
     * @return <code>true</code> if and only if the file or directory is
     *          successfully deleted; <code>false</code> otherwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean recursiveDelete(File file, boolean removeFile)
            throws IOException {
        // do NOT(!) follow symlinks when deleting files
        if (file.isDirectory() && !isSymlink(file)) {
            File[] subFiles = file.listFiles();
            if (subFiles != null) {
                for (File subFile : subFiles) {
                    recursiveDelete(subFile, true);
                }
            }
        }
        return removeFile ? file.delete() : true;
    }

    /**
     * returns the string representation of a given data volume
     * @param bytes the datavolume given in Byte
     * @param fractionDigits the number of fraction digits to display
     * @return the string representation of a given data volume
     */
    public static String getDataVolumeString(long bytes, int fractionDigits) {
        if (bytes >= 1024) {
            NUMBER_FORMAT.setMaximumFractionDigits(fractionDigits);
            float kbytes = (float) bytes / 1024;
            if (kbytes >= 1024) {
                float mbytes = (float) bytes / 1048576;
                if (mbytes >= 1024) {
                    float gbytes = (float) bytes / 1073741824;
                    if (gbytes >= 1024) {
                        float tbytes = (float) bytes / 1099511627776L;
                        return NUMBER_FORMAT.format(tbytes) + " TiB";
                    }
                    return NUMBER_FORMAT.format(gbytes) + " GiB";
                }

                return NUMBER_FORMAT.format(mbytes) + " MiB";
            }

            return NUMBER_FORMAT.format(kbytes) + " KiB";
        }

        return NUMBER_FORMAT.format(bytes) + " Byte";
    }

    /**
     * checks, if space information is available for a given file
     * @param file the file to check
     * @return <tt>true</tt>, if space information is available,
     * <tt>false</tt> otherwise
     */
    public static boolean isSpaceKnown(File file) {
        long usableSpace = file.getUsableSpace();
        long totalSpace = file.getTotalSpace();
        return (usableSpace != UNKNOWN_SPACE
                || totalSpace != UNKNOWN_SPACE);
    }

    /**
     * checks if a file is a symlink
     * @param file the file to check
     * @return <tt>true</tt>, if <tt>file</tt> is a symlink, <tt>false</tt>
     * otherwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean isSymlink(File file) throws IOException {
        File canon;
        if (file.getParent() == null) {
            canon = file;
        } else {
            File canonDir = file.getParentFile().getCanonicalFile();
            canon = new File(canonDir, file.getName());
        }
        return !canon.getCanonicalFile().equals(canon.getAbsoluteFile());
    }

    /**
     * returns the encfs mount point of a given search string or <tt>null</tt>,
     * if the mount point can not be determined
     * @param searchString a string that the encfs mountpoint must contain
     * @return the mount point of a device or <tt>null</tt>, if the mount point
     * can not be determined
     * @throws IOException if reading /proc/mounts fails
     */
    public static String getEncfsMountPoint(String searchString)
            throws IOException {
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                List<String> mounts = readFile(new File("/proc/mounts"));
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[0].equals("encfs")
                            && tokens[1].contains(searchString)) {
                        return tokens[1];
                    }
                }
                break;

            case Mac_OS_X:
                ProcessExecutor processExecutor = new ProcessExecutor();
                processExecutor.executeProcess(true, true, "mount");
                mounts = processExecutor.getStdOutList();
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[0].startsWith("encfs@fuse")
                            && tokens[2].contains(searchString)) {
                        return tokens[2];
                    }
                }
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported", CurrentOperatingSystem.OS);

        }
        return null;
    }

    /**
     * umounts a FUSE filesystem
     * @param mountPoint the mountpoint to umount
     * @param delete if <tt>true</tt>, the mountpoint will be deleted if it is
     * empty
     * @return <tt>true</tt>, if umounting succeeded, <tt>false</tt> otherwise
     */
    public static boolean umountFUSE(File mountPoint, boolean delete) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                int returnValue = processExecutor.executeProcess(
                        "fusermount", "-u", mountPoint.getPath());
                boolean success = (returnValue == 0);
                if (!success) {
                    LOGGER.log(Level.WARNING,
                            "could not umount {0}", mountPoint);
                }
                if (delete) {
                    deleteIfEmpty(mountPoint);
                }
                return success;

            case Mac_OS_X:
                returnValue = processExecutor.executeProcess(
                        "umount", mountPoint.getPath());
                success = (returnValue == 0);
                if (!success) {
                    LOGGER.log(Level.WARNING,
                            "could not umount {0}", mountPoint);
                }
                if (delete) {
                    deleteIfEmpty(mountPoint);
                }
                return success;

            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported", CurrentOperatingSystem.OS);
                return false;
        }
    }

    /**
     * deletes a directory when it is empty
     * @param directory the directory
     * @return <tt>true</tt> if the directory was deleted, <tt>false</tt>
     * otherwise
     */
    public static boolean deleteIfEmpty(File directory) {
        if (directory.listFiles().length == 0) {
            if (!directory.delete()) {
                LOGGER.log(Level.WARNING,
                        "could not delete {0}", directory);
                return false;
            }
        } else {
            LOGGER.log(Level.WARNING,
                    "encfs mountpoint {0} is not empty", directory);
            return false;
        }
        return true;
    }

    /**
     * mounts an encrypted filesystem
     * @param cipherDir the directory where only ciphertext is visible
     * @param plainDir the directory where plaintext is visible
     * @param password the encryption password
     * @return <tt>true</tt> if mounting was successfull,
     * <tt>false</tt> otherwise
     * @throws IOException
     */
    public static boolean mountEncFs(String cipherDir, String plainDir,
            String password) throws IOException {

        String script = "#!/bin/sh" + LINE_SEPARATOR
                + "echo \"" + password + "\" | encfs -S "
                + cipherDir + ' ' + plainDir + LINE_SEPARATOR;

        ProcessExecutor processExecutor = new ProcessExecutor();

        // set level to OFF to prevent password leaking into logfiles
        Logger logger = Logger.getLogger(ProcessExecutor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.OFF);

        int returnValue = processExecutor.executeScript(script);

        // restore previous log level
        logger.setLevel(level);

        if (returnValue == 0) {
            return true;
        }
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, "could not mount {0} to {1}",
                    new Object[]{cipherDir, plainDir});
        }
        return false;
    }

    /**
     * checks if a given directory is encrypted with encfs
     * @param directory the directory to check
     * @return <tt>true</tt>, if a given directory is encrypted with encfs,
     * <tt>false</tt> otherwise
     */
    public static boolean isEncFS(String directory) {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess("encfsctl", directory);
        return returnValue == 0;
    }

    /**
     * reads a file line by line
     * @param file the file to read
     * @return the list of lines in this file
     * @throws IOException if an I/O exception occurs
     */
    public static List<String> readFile(File file) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader = new BufferedReader(new FileReader(file));
        for (String line = reader.readLine(); line != null;) {
            lines.add(line);
            line = reader.readLine();
        }
        reader.close();
        return lines;
    }

    /**
     * creates a temporary directory
     * @param prefix the directory prefix
     * @param suffix the directory suffix
     * @return a temporary directory
     * @throws IOException if an I/O exception occurs
     */
    public static File createTempDirectory(String prefix, String suffix)
            throws IOException {
        File tempDirectory = File.createTempFile(prefix, suffix);
        tempDirectory.delete();
        if (tempDirectory.mkdirs()) {
            LOGGER.log(Level.INFO,
                    "using temporary directory {0}", tempDirectory);
            return tempDirectory;
        } else {
            throw new IOException("could not create " + tempDirectory);
        }
    }

    /**
     * creates a temporary directory
     * @param parentDir the parent directory
     * @param name the name of the temporary directory
     * @return the temporary directory
     */
    public static File createTempDirectory(File parentDir, String name) {
        File tempDir = new File(parentDir, name);
        if (tempDir.exists()) {
            // search for an alternative non-existing directory
            for (int i = 1;
                    (tempDir = new File(parentDir, name + i)).exists(); i++) {
            }
        }
        if (!tempDir.mkdirs()) {
            LOGGER.log(Level.WARNING, "can not create {0}", tempDir);
        }
        return tempDir;
    }

    /**
     * creates a usable mountpoint in a given directory with a preferred name
     * @param directory the parent directory of the mountpoint
     * @param name the preferred name of the mountpoint
     * @return a usable mountpoint in a given directory
     * @throws IOException if an I/O exception occurs
     */
    public static File createMountPoint(File directory, String name)
            throws IOException {
        File mountPoint = new File(directory, name);
        if (mountPoint.exists()) {
            if (isMountPoint(mountPoint.getPath())
                    || mountPoint.listFiles().length != 0) {
                // we can not use the preferred name
                // lets find an alternative
                for (int i = 1;; i++) {
                    mountPoint = new File(directory, name + "_" + i);
                    if (mountPoint.exists()) {
                        if (!isMountPoint(mountPoint.getPath())
                                && mountPoint.listFiles().length == 0) {
                            // we re-use an existing directory
                            return mountPoint;
                        }
                    } else {
                        if (mountPoint.mkdirs()) {
                            return mountPoint;
                        } else {
                            LOGGER.log(Level.WARNING,
                                    "can not create {0}", mountPoint);
                        }
                    }
                }
            } else {
                // we re-use an existing directory
                return mountPoint;
            }
        } else {
            if (mountPoint.mkdirs()) {
                return mountPoint;
            } else {
                LOGGER.log(Level.WARNING, "can not create {0}", mountPoint);
            }
        }
        return null;
    }

    /**
     * returns <code>true</code> if a given path is a currently used mountpoint,
     * <code>false</code> othwerwise
     * @param path the path to check
     * @return <code>true</code> if a given path is a currently used mountpoint,
     * <code>false</code> othwerwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean isMountPoint(String path) throws IOException {
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                List<String> mounts = readFile(new File("/proc/mounts"));
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[1].equals(path)) {
                        return true;
                    }
                }
                break;

            case Mac_OS_X:
                ProcessExecutor processExecutor = new ProcessExecutor();
                processExecutor.executeProcess(true, true, "mount");
                mounts = processExecutor.getStdOutList();
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[2].equals(path)) {
                        return true;
                    }
                }
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported", CurrentOperatingSystem.OS);
        }
        return false;
    }

    /**
     * returns the mount point of a device or <tt>null</tt>, if the mount point
     * can not be determined
     * @param device the device to search for
     * @return the mount point of a device or <tt>null</tt>, if the mount point
     * can not be determined
     * @throws IOException if an I/O exception occurs
     */
    public static String getMountPoint(String device) throws IOException {
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                List<String> mounts = readFile(new File("/proc/mounts"));
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[0].startsWith(device)) {
                        return tokens[1];
                    }
                }
                break;

            case Mac_OS_X:
                ProcessExecutor processExecutor = new ProcessExecutor();
                processExecutor.executeProcess(true, true, "mount");
                mounts = processExecutor.getStdOutList();
                for (String mount : mounts) {
                    String[] tokens = mount.split(" ");
                    if (tokens[0].startsWith(device)) {
                        return tokens[2];
                    }
                }
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported", CurrentOperatingSystem.OS);
        }
        return null;
    }

    /**
     * checks, if a certain device is mounted
     * @param device the device to check
     * @return <tt>true</tt> if the device is mounted, <tt>false</tt> otherwise
     * @throws IOException if an I/O exception occurs
     */
    public static boolean isMounted(String device) throws IOException {
        return getMountPoint(device) != null;
    }
}
