package ch.fhnw.dlcopy;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingWorker;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Creates a SquashFS file of the data partition
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SquashFSCreator
        extends SwingWorker<Boolean, String>
        implements PropertyChangeListener {

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

    @Override
    protected Boolean doInBackground() throws Exception {
        inhibit = new LogindInhibit("Creating ISO");

        try {
            dlCopyGUI.showIsoProgressMessage(
                    STRINGS.getString("Copying_Files"));

            // create new temporary directory in selected directory
            File tmpDirFile = LernstickFileTools.createTempDirectory(
                    new File(tmpDirectory), "Lernstick-SquashFS");
            String targetDirectory = tmpDirFile.getPath();
            createSquashFS(targetDirectory);

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        return true;
    }

    @Override
    protected void done() {
        if (inhibit != null) {
            inhibit.delete();
        }
        try {
            dlCopyGUI.isoCreationFinished(squashFsPath, get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
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
                    dlCopyGUI.showIsoProgressMessage(message, progress);
                } catch (NumberFormatException ex) {
                    LOGGER.log(Level.WARNING,
                            "could not parse mksquashfs progress", ex);
                }
            }
        }
    }

    private void createSquashFS(String targetDirectory)
            throws IOException, DBusException {

        dlCopyGUI.showIsoProgressMessage(
                STRINGS.getString("Mounting_Partitions"));
        // mount persistence (data partition)
        MountInfo dataMountInfo = systemSource.getDataPartition().mount();
        String dataPartitionPath = dataMountInfo.getMountPath();

        // create aufs union of squashfs files with persistence
        // ---------------------------------
        // We need an rwDir so that we can change some settings in the
        // lernstickWelcome properties below without affecting the current
        // data partition.
        // rwDir and cowDir are placed in /run/ because it is one of
        // the few directories that are not aufs itself.
        // Nested aufs is not (yet) supported...
        File runDir = new File("/run/");
        File rwDir = LernstickFileTools.createTempDirectory(runDir, "rw");
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("br=");
        stringBuilder.append(rwDir.getPath());
        stringBuilder.append(':');
        stringBuilder.append(dataPartitionPath);
        // The additional option "=ro+wh" for the data partition is
        // absolutely neccessary! Otherwise the whiteouts (info about
        // deleted files) in the data partition are not applied!!!
        stringBuilder.append("=ro+wh");
        String branchDefinition = stringBuilder.toString();
        File cowDir = LernstickFileTools.mountAufs(branchDefinition);

        // apply settings in cow directory
        Properties lernstickWelcomeProperties = new Properties();
        File propertiesFile = new File(cowDir, "/etc/lernstickWelcome");
        try (FileReader reader = new FileReader(propertiesFile)) {
            lernstickWelcomeProperties.load(reader);
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }
        lernstickWelcomeProperties.setProperty("ShowNotUsedInfo",
                showNotUsedDialog ? "true" : "false");
        lernstickWelcomeProperties.setProperty("AutoStartInstaller",
                autoStartInstaller ? "true" : "false");
        try (FileWriter writer = new FileWriter(propertiesFile)) {
            lernstickWelcomeProperties.store(
                    writer, "lernstick Welcome properties");
        } catch (IOException iOException) {
            LOGGER.log(Level.WARNING, "", iOException);
        }

        // exclude file handling
        File excludeFile;
        File defaultExcludes = new File(cowDir, "/etc/mksquashfs_exclude");

        if (defaultExcludes.exists()) {
            excludeFile = defaultExcludes;

        } else {
            excludeFile = File.createTempFile("mksquashfs_exclude", null);
            try (FileWriter writer = new FileWriter(excludeFile)) {
                writer.write("boot\n"
                        + "tmp\n"
                        + "var/log\n"
                        + "var/cache\n"
                        + "var/tmp");
            } catch (IOException iOException) {
                LOGGER.log(Level.WARNING, "", iOException);
            }
        }

        // create new squashfs image
        squashFsPath = targetDirectory + "/lernstick.squashfs";
        dlCopyGUI.showIsoProgressMessage(
                STRINGS.getString("Compressing_Filesystem"));
        PROCESS_EXECUTOR.addPropertyChangeListener(this);
        String cowPath = cowDir.getPath();
        int exitValue = PROCESS_EXECUTOR.executeProcess("mksquashfs",
                cowPath, squashFsPath,
                "-comp", "xz",
                "-ef", excludeFile.getPath());
        if (exitValue != 0) {
            throw new IOException(
                    STRINGS.getString("Error_Creating_Squashfs"));
        }
        PROCESS_EXECUTOR.removePropertyChangeListener(this);

        // cleanup
        DLCopy.umount(cowPath, dlCopyGUI);
        if (!dataMountInfo.alreadyMounted()) {
            systemSource.getDataPartition().umount();
        }
    }
}
