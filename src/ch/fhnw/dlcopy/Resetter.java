package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Resets selected storage media
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Resetter extends SwingWorker<Boolean, Void> {

    private static final Logger LOGGER
            = Logger.getLogger(Resetter.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final List<StorageDevice> deviceList;
    private final String bootDeviceName;

    private final boolean printDocuments;
    private final String printDirectory;
    private final boolean printODT;
    private final boolean printODS;
    private final boolean printPDF;
    private final boolean printDOC;
    private final boolean printDOCX;
    private final boolean printXLS;
    private final boolean printXLSX;
    private final int printCopies;
    private final boolean printDuplex;
    private final boolean backupData;
    private final String backupSource;
    private final String backupDestination;
    private final List<Subdirectory> orderedSubdirectoriesEntries;
    private final boolean formatExchangePartition;
    private final String exchangePartitionFileSystem;
    private final boolean keepExchangePartitionLabel;
    private final String newExchangePartitionLabel;
    private final boolean formatDataPartition;
    private final String dataPartitionFileSystem;
    private final boolean resetHome;
    private final boolean resetSystem;

    private int deviceListSize;
    private int batchCounter;

    /**
     * creates a new Resetter
     *
     * @param dlCopyGUI the graphical user interface
     * @param deviceList the list of StorageDevices to reset
     * @param bootDeviceName the name of the boot device
     * @param printDocuments if documents on the exchange partition should be
     * printed
     * @param printDirectory the directory where documents to be printed should
     * be searched
     * @param printODT if OpenDocument Texts should be printed
     * @param printODS if OpenDocument Texts should be printed
     * @param printPDF if Portable Document Formats should be printed
     * @param printDOC if older MS Word documents should be printed
     * @param printDOCX if newer MS Word documents should be printed
     * @param printXLS if older MS Excel documents should be printed
     * @param printXLSX if newer MS Excel documents should be printed
     * @param printCopies the number of copies to print
     * @param printDuplex if the document should be printed on both sides of the
     * paper
     * @param backupData if data should be backed up
     * @param backupSource the backup source directory
     * @param backupDestination the backup destination directory
     * @param orderedSubdirectoriesEntries the ordered list of subdirectory
     * entries
     * @param formatExchangePartition if the exchange partition should be
     * formatted
     * @param exchangePartitionFileSystem the file system of the exchange
     * partition
     * @param keepExchangePartitionLabel if <code>true</code>, the old label of
     * the exchange partition is kept, otherwise the
     * <code>newExchangePartitionLabel</code> will be set
     * @param newExchangePartitionLabel if
     * <code>keepExchangePartitionLabel</code> is <code>false</code> this string
     * will be used as the new label when reformatting the exchange partition
     * @param formatDataPartition if the data partition should be formatted
     * @param dataPartitionFileSystem the file system of the data partition
     * @param resetHome if the home directory should be reset
     * @param resetSystem if the system (without /home) should be reset
     */
    public Resetter(DLCopyGUI dlCopyGUI, List<StorageDevice> deviceList,
            String bootDeviceName, boolean printDocuments,
            String printDirectory, boolean printODT, boolean printODS,
            boolean printPDF, boolean printDOC, boolean printDOCX,
            boolean printXLS, boolean printXLSX, int printCopies,
            boolean printDuplex, boolean backupData, String backupSource,
            String backupDestination,
            List<Subdirectory> orderedSubdirectoriesEntries,
            boolean formatExchangePartition,
            String exchangePartitionFileSystem,
            boolean keepExchangePartitionLabel,
            String newExchangePartitionLabel, boolean formatDataPartition,
            String dataPartitionFileSystem, boolean resetHome,
            boolean resetSystem) {

        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.bootDeviceName = bootDeviceName;
        this.printDocuments = printDocuments;
        this.printDirectory = printDirectory;
        this.printODT = printODT;
        this.printODS = printODS;
        this.printPDF = printPDF;
        this.printDOC = printDOC;
        this.printDOCX = printDOCX;
        this.printXLS = printXLS;
        this.printXLSX = printXLSX;
        this.printCopies = printCopies;
        this.printDuplex = printDuplex;
        this.backupData = backupData;
        this.backupSource = backupSource;
        this.backupDestination = backupDestination;
        this.orderedSubdirectoriesEntries = orderedSubdirectoriesEntries;
        this.formatExchangePartition = formatExchangePartition;
        this.exchangePartitionFileSystem = exchangePartitionFileSystem;
        this.keepExchangePartitionLabel = keepExchangePartitionLabel;
        this.newExchangePartitionLabel = newExchangePartitionLabel;
        this.formatDataPartition = formatDataPartition;
        this.dataPartitionFileSystem = dataPartitionFileSystem;
        this.resetHome = resetHome;
        this.resetSystem = resetSystem;
    }

    @Override
    protected Boolean doInBackground() throws Exception {

        dlCopyGUI.showResetProgress();

        deviceListSize = deviceList.size();

        for (StorageDevice storageDevice : deviceList) {

            dlCopyGUI.resettingDeviceStarted(storageDevice);

            batchCounter++;
            LOGGER.log(Level.INFO,
                    "resetting storage device: {0} of {1} ({2})",
                    new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            Partition exchangePartition = storageDevice.getExchangePartition();
            printDocuments(exchangePartition);
            backup(storageDevice, exchangePartition);
            resetExchangePartition(exchangePartition);
            resetDataPartition(storageDevice);

            LOGGER.log(Level.INFO, "resetting of storage device finished: "
                    + "{0} of {1} ({2})", new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            if (!storageDevice.getDevice().equals(bootDeviceName)) {
                // Unmount *all* partitions so that the user doesn't have to
                // manually umount all storage devices after resetting is done.
                for (Partition partition : storageDevice.getPartitions()) {
                    DLCopy.umount(partition, dlCopyGUI);
                }
            }
        }

        return true;
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.resettingFinished(get());
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
            dlCopyGUI.resettingFinished(false);
        }
    }

    private void printDocuments(Partition exchangePartition)
            throws DBusException, IOException {

        if (!printDocuments) {
            return;
        }

        dlCopyGUI.showPrintingDocuments();

        MountInfo mountInfo = exchangePartition.mount();
        Path printDirPath = Paths.get(mountInfo.getMountPath(), printDirectory);

        if (printODT) {
            printDocumentType(printDirPath, "odt",
                    DLCopy.STRINGS.getString("OpenDocument_Text"));
        }

        if (printODS) {
            printDocumentType(printDirPath, "ods",
                    DLCopy.STRINGS.getString("OpenDocument_Spreadsheet"));
        }

        if (printPDF) {
            printDocumentType(printDirPath, "pdf",
                    DLCopy.STRINGS.getString("Portable_Document_Format"));
        }

        if (printDOC) {
            printDocumentType(printDirPath, "doc",
                    DLCopy.STRINGS.getString("MS_Word"));
        }

        if (printDOCX) {
            printDocumentType(printDirPath, "docx",
                    DLCopy.STRINGS.getString("MS_Word"));
        }

        if (printXLS) {
            printDocumentType(printDirPath, "xls",
                    DLCopy.STRINGS.getString("MS_Excel"));
        }

        if (printXLSX) {
            printDocumentType(printDirPath, "xlsx",
                    DLCopy.STRINGS.getString("MS_Excel"));
        }
    }

    private void printDocumentType(
            Path printDirPath, String suffix, String type) throws IOException {

        // search and collect wanted documents
        List<Path> documents = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                printDirPath, new MyFilter(suffix))) {
            for (Path path : stream) {
                LOGGER.log(Level.INFO, "found {0} to print: {1}",
                        new Object[]{suffix, path});
                documents.add(path);
            }
        }

        // print document(s)
        switch (documents.size()) {
            case 0:
                LOGGER.log(Level.WARNING, "found no {0} file to print", suffix);
                break;
            case 1:
                PrintingHelper.print(
                        documents.get(0), printCopies, printDuplex);
                break;
            default:
                List<Path> selectedDocuments
                        = dlCopyGUI.selectDocumentsToPrint(type, documents);
                if (selectedDocuments != null) {
                    for (Path selectedDocument : selectedDocuments) {
                        PrintingHelper.print(
                                selectedDocument, printCopies, printDuplex);
                    }
                }
        }
    }

    private void backup(StorageDevice storageDevice,
            Partition exchangePartition) throws DBusException, IOException {

        if (!backupData) {
            return;
        }

        MountInfo mountInfo = exchangePartition.mount();
        Path source = Paths.get(mountInfo.getMountPath(), backupSource);
        Path destination = Paths.get(backupDestination);

        // prepare destination directory
        StringBuilder stringBuilder = new StringBuilder();
        for (Subdirectory subdirectory : orderedSubdirectoriesEntries) {
            if (!subdirectory.isSelected()) {
                continue;
            }
            if (stringBuilder.length() > 0) {
                stringBuilder.append(' ');
            }
            String description = subdirectory.getDescription();
            if (description.equals(
                    DLCopy.STRINGS.getString("Exchange_Partition_Label"))) {
                stringBuilder.append(exchangePartition.getIdLabel());
            } else if (description.equals(
                    DLCopy.STRINGS.getString("Storage_Media_Serial_Number"))) {
                // replace all slashes because they are not allowed in directory
                // names
                stringBuilder.append(
                        storageDevice.getSerial().replaceAll("/", "-"));
            } else if (description.equals(
                    DLCopy.STRINGS.getString("Timestamp"))) {
                stringBuilder.append(DateTimeFormatter.ofPattern(
                        "uuuu-MM-dd-HH-mm-ss").format(LocalDateTime.now()));
            }
        }
        destination = destination.resolve(stringBuilder.toString());
        Files.createDirectory(destination);

        // Unfortunately, rdiffbackup looses all metainformation when backing up
        // a directory that was previously used as destination directory.
        // Therefore we just make a simple copy.
        FileCopier fileCopier = new FileCopier();
        dlCopyGUI.showResetBackup(fileCopier);

        Source[] sources = new Source[]{new Source(source.toString(), ".*")};
        String[] destinations = new String[]{destination.toString()};
        fileCopier.copy(new CopyJob(sources, destinations));
    }

    private void resetExchangePartition(Partition exchangePartition)
            throws IOException {

        if (!formatExchangePartition) {
            return;
        }

        LOGGER.log(Level.INFO,
                "formatting exchange partition: {0}", exchangePartition);
        if (exchangePartition != null) {
            dlCopyGUI.showResetFormattingExchangePartition();
            String label;
            if (keepExchangePartitionLabel) {
                label = exchangePartition.getIdLabel();
            } else {
                label = newExchangePartitionLabel;
            }
            DLCopy.formatExchangePartition(
                    "/dev/" + exchangePartition.getDeviceAndNumber(),
                    label, exchangePartitionFileSystem, dlCopyGUI);
        }
    }

    private void resetDataPartition(StorageDevice storageDevice)
            throws DBusException, IOException {

        Partition dataPartition = storageDevice.getDataPartition();

        if (dataPartition == null) {
            return;
        }

        if (formatDataPartition) {
            // format data partition
            dlCopyGUI.showResetFormattingDataPartition();
            DLCopy.formatPersistencePartition(
                    "/dev/" + dataPartition.getDeviceAndNumber(),
                    dataPartitionFileSystem, dlCopyGUI);
        } else {
            // remove files from data partition
            dlCopyGUI.showResetRemovingFiles();

            MountInfo mountInfo = dataPartition.mount();
            String mountPoint = mountInfo.getMountPath();
            String cleanupRoot = mountPoint;
            if (!Files.exists(Paths.get(mountPoint, "home"))) {
                // Debian 9 and newer
                cleanupRoot = mountPoint + "/rw";
            }
            ProcessExecutor processExecutor = new ProcessExecutor();
            if (resetSystem && resetHome) {
                // remove all files
                // but keep "/lost+found/" and "persistence.conf"
                processExecutor.executeProcess("find", mountPoint,
                        "!", "-regex", mountPoint,
                        "!", "-regex", mountPoint + "/lost\\+found",
                        "!", "-regex", mountPoint + "/persistence.conf",
                        "-exec", "rm", "-rf", "{}", ";");
            } else {
                if (resetSystem) {
                    // remove all files but keep
                    // "/lost+found/", "persistence.conf" and "/home/"
                    processExecutor.executeProcess("find", mountPoint,
                            "!", "-regex", mountPoint,
                            "!", "-regex", cleanupRoot,
                            "!", "-regex", mountPoint + "/lost\\+found",
                            "!", "-regex", mountPoint + "/persistence.conf",
                            "!", "-regex", cleanupRoot + "/home.*",
                            "-exec", "rm", "-rf", "{}", ";");
                }
                if (resetHome) {
                    // only remove "/home/user/"
                    processExecutor.executeProcess(
                            "rm", "-rf", cleanupRoot + "/home/user/");
                }
            }
            if (resetHome) {
                // restore "/home/user/" from "/etc/skel/"
                processExecutor.executeProcess("mkdir", "-p",
                        cleanupRoot + "/home/");
                processExecutor.executeProcess("cp", "-a",
                        "/etc/skel/", cleanupRoot + "/home/user/");
                processExecutor.executeProcess("chown", "-R",
                        "user.user", cleanupRoot + "/home/user/");
            }

            if (!mountInfo.alreadyMounted()) {
                dataPartition.umount();
            }
        }
    }

    // our own filter that is always case insensitive
    private class MyFilter implements DirectoryStream.Filter<Path> {

        private final String pattern;

        public MyFilter(String pattern) {
            this.pattern = pattern;
        }

        @Override
        public boolean accept(Path path) throws IOException {
            return path.toString().toLowerCase().endsWith(
                    pattern.toLowerCase());
        }
    }
}
