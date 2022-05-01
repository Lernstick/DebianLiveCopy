package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Creates a SquashFS file of the data partition
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SquashFSCreator implements PropertyChangeListener {

    private static final Logger LOGGER
            = Logger.getLogger(SquashFSCreator.class.getName());
    private final static ProcessExecutor PROCESS_EXECUTOR
            = new ProcessExecutor();

    // mksquashfs output looks like this:
    // [==========           ]  43333/230033  18%
    private static final Pattern MKSQUASHFS_PATTERN
            = Pattern.compile("\\[.* (.*)/(.*) .*");

    private final DLCopyGUI dlCopyGUI;
    private final SystemSource systemSource;
    private final String tmpDirectory;
    private final boolean showNotUsedDialog;
    private final boolean autoStartInstaller;

    private String squashFsPath;
    private LogindInhibit inhibit = null;

    /**
     * creates a new ISOCreator
     *
     * @param dlCopyGUI the DLCopy GUI
     * @param systemSource the system source
     * @param tmpDirectory the path to a temporary directory
     * @param showNotUsedDialog if the dialog that data partition is not in use
     * should be shown
     * @param autoStartInstaller if the installer should start automatically if
     * no datapartition is in use
     */
    public SquashFSCreator(DLCopyGUI dlCopyGUI, SystemSource systemSource,
            String tmpDirectory, boolean showNotUsedDialog,
            boolean autoStartInstaller) {
        this.dlCopyGUI = dlCopyGUI;
        this.systemSource = systemSource;
        this.tmpDirectory = tmpDirectory;
        this.showNotUsedDialog = showNotUsedDialog;
        this.autoStartInstaller = autoStartInstaller;
    }

    public Boolean createSquashFS() throws Exception {
        inhibit = new LogindInhibit("Creating ISO");

        try {
            dlCopyGUI.showSquashFSProgressMessage(
                    STRINGS.getString("Copying_Files"));

            // create new temporary directory in selected directory
            File tmpDirFile = LernstickFileTools.createTempDirectory(
                    new File(tmpDirectory), "Lernstick-SquashFS");
            String targetDirectory = tmpDirFile.getPath();
            createSquashFS(targetDirectory);

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } finally {
            inhibit.delete();
        }
        return true;
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        if (ProcessExecutor.LINE.equals(propertyName)) {
            String line = (String) evt.getNewValue();
            Matcher matcher = MKSQUASHFS_PATTERN.matcher(line);
            if (matcher.matches()) {
                String doneString = matcher.group(1).trim();
                String maxString = matcher.group(2).trim();
                try {
                    int doneInt = Integer.parseInt(doneString);
                    int maxInt = Integer.parseInt(maxString);
                    final int progress = (doneInt * 100) / maxInt;
                    String message = STRINGS.getString(
                            "Compressing_Filesystem_Progress");
                    message = MessageFormat.format(
                            message, progress + "%");
                    dlCopyGUI.showSquashFSProgressMessage(message, progress);
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING,
                            "could not parse mksquashfs progress", ex);
                }
            }
        }
    }

    public String getSquashFsPath() {
        return squashFsPath;
    }

    private void createSquashFS(String targetDirectory)
            throws IOException, DBusException {

        dlCopyGUI.showSquashFSProgressMessage(
                STRINGS.getString("Mounting_Partitions"));
        // mount persistence (data partition)
        Partition dataPartition = systemSource.getDataPartition();
        MountInfo dataMountInfo = dataPartition.mount();
        String dataPartitionPath = dataMountInfo.getMountPath();
        if (!Files.exists(Paths.get(dataPartitionPath, "home"))) {
            // Debian 9 and newer
            dataPartitionPath += "/rw";
        }

        // check if the data partition is mounted in read-write mode
        boolean remountedReadWrite = false;
        try {
            Files.createTempFile(Paths.get(dataPartitionPath), null, null);
        } catch (IOException ex) {
            // temporarily remount the data partition in read-write mode
            ProcessExecutor processExecutor = new ProcessExecutor();
            processExecutor.executeProcess(true, true, "mount", "-o",
                    "remount,rw", dataPartition.getFullDeviceAndNumber());
            remountedReadWrite = true;
        }

        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        // Using a layered file system here is not possible because we need to
        // include the whiteout files of the data partition into our squashfs.
        // Using the data partition as the lower layer would hide the whiteouts.
        // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
        //
        // temporarily apply our settings to the lernstickWelcome properties
        // file (will be reset at the end)
        Properties lernstickWelcomeProperties = new Properties();
        File propertiesFile = new File(dataPartitionPath,
                "/etc/lernstickWelcome");
        try (FileReader reader = new FileReader(propertiesFile)) {
            lernstickWelcomeProperties.load(reader);
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }

        String originalShowNotUsed
                = lernstickWelcomeProperties.getProperty("ShowNotUsedInfo");
        String originalAutoStart
                = lernstickWelcomeProperties.getProperty("AutoStartInstaller");
        lernstickWelcomeProperties.setProperty("ShowNotUsedInfo",
                showNotUsedDialog ? "true" : "false");
        lernstickWelcomeProperties.setProperty("AutoStartInstaller",
                autoStartInstaller ? "true" : "false");
        writeProperties(lernstickWelcomeProperties, propertiesFile);

        // exclude file handling
        File excludeFile;
        File defaultExcludes = new File(dataPartitionPath,
                "/etc/mksquashfs_exclude");

        if (defaultExcludes.exists()) {
            LOGGER.log(Level.INFO,
                    "using default exclude file \"{0}\"", defaultExcludes);
            excludeFile = defaultExcludes;

        } else {
            LOGGER.log(Level.INFO, "default exclude file \"{0}\" doesn't exist",
                    defaultExcludes);
            excludeFile = File.createTempFile("mksquashfs_exclude", null);
            try (FileWriter writer = new FileWriter(excludeFile)) {
                writer.write("boot\n"
                        + "tmp\n"
                        + "var/lib/apt/lists\n"
                        + "var/lib/clamav\n"
                        + "var/log\n"
                        + "var/cache\n"
                        + "var/tmp");
            } catch (IOException iOException) {
                LOGGER.log(Level.WARNING, "", iOException);
            }
        }

        // create new squashfs image
        squashFsPath = targetDirectory + "/lernstick.squashfs";
        dlCopyGUI.showSquashFSProgressMessage(
                STRINGS.getString("Compressing_Filesystem"));
        PROCESS_EXECUTOR.addPropertyChangeListener(this);
        int exitValue = PROCESS_EXECUTOR.executeProcess(true, true, "mksquashfs",
                dataPartitionPath, squashFsPath,
                "-comp", "zstd", "-Xcompression-level", "22", "-wildcards",
                "-ef", excludeFile.getPath());
            LOGGER.log(Level.FINEST, PROCESS_EXECUTOR.getStdOut());
        if (exitValue != 0) {
            LOGGER.log(Level.SEVERE, PROCESS_EXECUTOR.getStdErr());
            throw new IOException(
                    STRINGS.getString("Error_Creating_Squashfs") + ": " + exitValue);
        }
        PROCESS_EXECUTOR.removePropertyChangeListener(this);

        // cleanup
        lernstickWelcomeProperties.setProperty(
                "ShowNotUsedInfo", originalShowNotUsed);
        lernstickWelcomeProperties.setProperty(
                "AutoStartInstaller", originalAutoStart);
        writeProperties(lernstickWelcomeProperties, propertiesFile);
        if (!dataMountInfo.alreadyMounted()) {
            systemSource.getDataPartition().umount();
        } else if (remountedReadWrite) {
            ProcessExecutor processExecutor = new ProcessExecutor();
            processExecutor.executeProcess(true, true, "mount", "-o",
                    "remount,ro", dataPartition.getFullDeviceAndNumber());
        }
    }

    private void writeProperties(Properties properties, File file) {
        try (FileWriter writer = new FileWriter(file)) {
            properties.store(writer, "lernstick Welcome properties");
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }
    }
}
