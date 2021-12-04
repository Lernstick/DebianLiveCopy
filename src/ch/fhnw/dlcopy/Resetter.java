package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.dlcopy.gui.swing.OverwriteEntry;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.NoSuchAlgorithmException;
import java.text.MessageFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.function.BiPredicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.swing.SwingWorker;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Resets selected storage media
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class Resetter extends SwingWorker<Boolean, Void> {

    /**
     * different modes to print found documents automatically
     */
    public static enum AutoPrintMode {

        /**
         * all documents
         */
        ALL,
        /**
         * single documents of a certain format
         */
        SINGLE,
        /**
         * no document
         */
        NONE
    }

    private static final Logger LOGGER
            = Logger.getLogger(Resetter.class.getName());

    private final DLCopyGUI dlCopyGUI;
    private final List<StorageDevice> deviceList;
    private final String bootDeviceName;

    private final boolean printDocuments;
    private final String printDirectories;
    private final boolean scanDirectoriesRecursively;
    private final boolean printODT;
    private final boolean printODS;
    private final boolean printODP;
    private final boolean printPDF;
    private final boolean printDOC;
    private final boolean printDOCX;
    private final boolean printXLS;
    private final boolean printXLSX;
    private final boolean printPPT;
    private final boolean printPPTX;
    private final AutoPrintMode autoPrintMode;
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
    private final boolean deleteOnDataPartition;
    private final boolean formatDataPartition;
    private final String dataPartitionFileSystem;
    private final boolean resetHome;
    private final boolean resetSystem;
    private final boolean restoreData;
    private final List<OverwriteEntry> overwriteEntries;
    private final Lock lock;

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
     * @param printDirectories the directories where documents to be printed
     * should be searched
     * @param scanDirectoriesRecursively if the directories should be scanned
     * recursively
     * @param printODT if OpenDocument Texts should be printed
     * @param printODS if OpenDocument Spreadsheets should be printed
     * @param printODP if OpenDocument Presentations should be printed
     * @param printPDF if Portable Document Formats should be printed
     * @param printDOC if older MS Word documents should be printed
     * @param printDOCX if newer MS Word documents should be printed
     * @param printXLS if older MS Excel documents should be printed
     * @param printXLSX if newer MS Excel documents should be printed
     * @param printPPT if older MS PowerPoint documents should be printed
     * @param printPPTX if newer MS PowerPoint documents should be printed
     * @param autoPrintMode the mode of automatic document printing
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
     * @param deleteOnDataPartition if data on the data partition should be
     * deleted at all
     * @param formatDataPartition if the data partition should be formatted
     * @param dataPartitionFileSystem the file system of the data partition
     * @param resetHome if the home directory should be reset
     * @param resetSystem if the system (without /home) should be reset
     * @param restoreData if data should be restored at all
     * @param overwriteEntries the list of entries to overwrite (if restoreData
     * is true)
     * @param lock the lock to aquire before executing in background
     */
    public Resetter(DLCopyGUI dlCopyGUI, List<StorageDevice> deviceList,
            String bootDeviceName, boolean printDocuments,
            String printDirectories, boolean scanDirectoriesRecursively,
            boolean printODT, boolean printODS, boolean printODP,
            boolean printPDF, boolean printDOC, boolean printDOCX,
            boolean printXLS, boolean printXLSX, boolean printPPT,
            boolean printPPTX, AutoPrintMode autoPrintMode, int printCopies,
            boolean printDuplex, boolean backupData, String backupSource,
            String backupDestination,
            List<Subdirectory> orderedSubdirectoriesEntries,
            boolean formatExchangePartition,
            String exchangePartitionFileSystem,
            boolean keepExchangePartitionLabel,
            String newExchangePartitionLabel, boolean deleteOnDataPartition,
            boolean formatDataPartition, String dataPartitionFileSystem,
            boolean resetHome, boolean resetSystem, boolean restoreData,
            List<OverwriteEntry> overwriteEntries, Lock lock) {

        this.dlCopyGUI = dlCopyGUI;
        this.deviceList = deviceList;
        this.bootDeviceName = bootDeviceName;
        this.printDocuments = printDocuments;
        this.printDirectories = printDirectories;
        this.scanDirectoriesRecursively = scanDirectoriesRecursively;
        this.printODT = printODT;
        this.printODS = printODS;
        this.printODP = printODP;
        this.printPDF = printPDF;
        this.printDOC = printDOC;
        this.printDOCX = printDOCX;
        this.printXLS = printXLS;
        this.printXLSX = printXLSX;
        this.printPPT = printPPT;
        this.printPPTX = printPPTX;
        this.autoPrintMode = autoPrintMode;
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
        this.deleteOnDataPartition = deleteOnDataPartition;
        this.formatDataPartition = formatDataPartition;
        this.dataPartitionFileSystem = dataPartitionFileSystem;
        this.resetHome = resetHome;
        this.resetSystem = resetSystem;
        this.restoreData = restoreData;
        this.overwriteEntries = overwriteEntries;
        this.lock = lock;
    }

    @Override
    protected Boolean doInBackground() throws Exception {
        LOGGER.info("trying to acquire lock...");
        lock.lock();
        LOGGER.info("lock aquired");
        try {
            Thread.currentThread().setName(getClass().getName());

            dlCopyGUI.showResetProgress();

            deviceListSize = deviceList.size();

            for (StorageDevice storageDevice : deviceList) {
                resetStorageDevice(storageDevice);
            }

            return true;

        } finally {
            LOGGER.info("releasing lock...");
            lock.unlock();
            LOGGER.info("unlocked");
        }
    }

    @Override
    protected void done() {
        try {
            dlCopyGUI.resettingFinished(get());
        } catch (Exception ex) {
            // don't catch more specific exceptions, otherwise we will miss
            // occuring runtime exceptions in the LOGGER
            LOGGER.log(Level.SEVERE, "", ex);
            dlCopyGUI.resettingFinished(false);
        }
    }

    private void resetStorageDevice(StorageDevice storageDevice)
            throws DBusException, IOException, NoSuchAlgorithmException {

        try {
            dlCopyGUI.resettingDeviceStarted(storageDevice);

            batchCounter++;
            LOGGER.log(Level.INFO,
                    "resetting storage device: {0} of {1} ({2})",
                    new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

            Partition exchangePartition = storageDevice.getExchangePartition();
            Partition dataPartition = storageDevice.getDataPartition();

            printDocuments(storageDevice, exchangePartition);
            try {
                backup(storageDevice, exchangePartition);
            } catch (Exception exception) {
                // don't catch more specific exceptions, otherwise we will miss
                // occuring runtime exceptions
                String errorMessage
                        = DLCopy.STRINGS.getString("Error_Reset_Backup");
                errorMessage = MessageFormat.format(errorMessage,
                        exception.getMessage(), exchangePartition.getIdLabel(),
                        storageDevice.getSerial());
                dlCopyGUI.showErrorMessage(errorMessage);
                throw exception;
            }
            resetExchangePartition(exchangePartition);
            resetDataPartition(storageDevice.getSystemPartition(),
                    dataPartition);
            restoreFiles(dataPartition);

            LOGGER.log(Level.INFO, "resetting of storage device finished: "
                    + "{0} of {1} ({2})", new Object[]{
                        batchCounter, deviceListSize, storageDevice
                    });

        } finally {
            if (!storageDevice.getDevice().equals(bootDeviceName)) {
                // Unmount *all* partitions so that the user doesn't have to
                // manually umount all storage devices after resetting is done.
                for (Partition partition : storageDevice.getPartitions()) {
                    DLCopy.umount(partition, dlCopyGUI);
                }
            }
        }
    }

    private void printDocuments(StorageDevice storageDevice,
            Partition exchangePartition) throws DBusException, IOException {

        if (!printDocuments) {
            return;
        }

        dlCopyGUI.showPrintingDocuments();

        if (exchangePartition == null) {
            String errorMessage = DLCopy.STRINGS.getString(
                    "Error_No_Exchange_Partition");
            errorMessage = MessageFormat.format(
                    errorMessage, storageDevice.getSerial());
            dlCopyGUI.showErrorMessage(errorMessage);
            throw new IOException(errorMessage);
        }

        MountInfo mountInfo = exchangePartition.mount();
        String[] printDirs = printDirectories.split(System.lineSeparator());

        // sanity check
        boolean dirExists = false;
        for (String printDir : printDirs) {
            Path printDirPath = Paths.get(mountInfo.getMountPath(), printDir);
            if (Files.exists(printDirPath)) {
                dirExists = true;
                break;
            }
        }
        if (!dirExists) {
            String errorMessage = DLCopy.STRINGS.getString(
                    "Error_Print_Directories_Not_Found");
            errorMessage = MessageFormat.format(errorMessage,
                    printDirectories.replaceAll(System.lineSeparator(), "<br>"),
                    exchangePartition.getIdLabel(), storageDevice.getSerial());
            dlCopyGUI.showErrorMessage(errorMessage);
            throw new IOException(printDirectories + " don't exist");
        }

        // search, collect and print wanted documents
        switch (autoPrintMode) {
            case ALL:
                for (Path document : getAllDocuments(mountInfo, printDirs)) {
                    PrintingHelper.print(document, printCopies, printDuplex);
                }
                break;

            case SINGLE:
                autoPrintSingleTypes(mountInfo, printDirs);
                break;

            case NONE:
                List<Path> documents = getAllDocuments(mountInfo, printDirs);
                if (!documents.isEmpty()) {
                    List<Path> selectedDocuments
                            = dlCopyGUI.selectDocumentsToPrint(null/*no type*/,
                                    mountInfo.getMountPath(), documents);
                    if (selectedDocuments != null) {
                        selectedDocuments.forEach(document
                                -> PrintingHelper.print(document,
                                        printCopies, printDuplex));
                    }
                }
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "unsupported autoPrintMode \"{0}\"", autoPrintMode);
        }
    }

    private void autoPrintType(MountInfo mountInfo, String[] printDirs,
            String type, String suffix) throws IOException {

        List<Path> documents = getAllDocumentsOfType(
                mountInfo, printDirs, suffix);

        switch (documents.size()) {
            case 0:
                LOGGER.log(Level.WARNING, "found no {0} file to print", suffix);
                break;
            case 1:
                PrintingHelper.print(
                        documents.get(0), printCopies, printDuplex);
                break;
            default:
                List<Path> selectedDocuments = dlCopyGUI.selectDocumentsToPrint(
                        DLCopy.STRINGS.getString(type),
                        mountInfo.getMountPath(), documents);
                if (selectedDocuments != null) {
                    selectedDocuments.forEach(document -> PrintingHelper.print(
                            document, printCopies, printDuplex));
                }
        }
    }

    private void autoPrintSingleTypes(MountInfo mountInfo, String[] printDirs)
            throws IOException {

        if (printODT) {
            autoPrintType(mountInfo, printDirs, "OpenDocument_Text", "odt");
        }
        if (printODS) {
            autoPrintType(mountInfo, printDirs,
                    "OpenDocument_Spreadsheet", "ods");
        }
        if (printODP) {
            autoPrintType(mountInfo, printDirs,
                    "OpenDocument_Presentation", "odp");
        }
        if (printPDF) {
            autoPrintType(mountInfo, printDirs,
                    "Portable_Document_Format", "pdf");
        }
        if (printDOC) {
            autoPrintType(mountInfo, printDirs, "MS_Word", "doc");
        }
        if (printDOCX) {
            autoPrintType(mountInfo, printDirs, "MS_Word", "docx");
        }
        if (printXLS) {
            autoPrintType(mountInfo, printDirs, "MS_Excel", "xls");
        }
        if (printXLSX) {
            autoPrintType(mountInfo, printDirs, "MS_Excel", "xlsx");
        }
        if (printPPT) {
            autoPrintType(mountInfo, printDirs, "MS_PowerPoint", "ppt");
        }
        if (printPPTX) {
            autoPrintType(mountInfo, printDirs, "MS_PowerPoint", "pptx");
        }
    }

    private List<Path> getAllDocumentsOfType(MountInfo mountInfo,
            String[] printDirs, String suffix) throws IOException {

        List<Path> documents = new ArrayList<>();

        String mountPath = mountInfo.getMountPath();
        for (String printDir : printDirs) {
            Path printDirPath = Paths.get(mountPath, printDir);
            documents.addAll(searchDocuments(printDirPath, suffix));
        }

        return documents;
    }

    private List<Path> getAllDocuments(MountInfo mountInfo, String[] printDirs)
            throws IOException {

        List<Path> documents = new ArrayList<>();

        String mountPath = mountInfo.getMountPath();
        for (String printDir : printDirs) {
            Path printDirPath = Paths.get(mountPath, printDir);
            if (printODT) {
                documents.addAll(searchDocuments(printDirPath, "odt"));
            }
            if (printODS) {
                documents.addAll(searchDocuments(printDirPath, "ods"));
            }
            if (printODP) {
                documents.addAll(searchDocuments(printDirPath, "odp"));
            }
            if (printPDF) {
                documents.addAll(searchDocuments(printDirPath, "pdf"));
            }
            if (printDOC) {
                documents.addAll(searchDocuments(printDirPath, "doc"));
            }
            if (printDOCX) {
                documents.addAll(searchDocuments(printDirPath, "docx"));
            }
            if (printXLS) {
                documents.addAll(searchDocuments(printDirPath, "xls"));
            }
            if (printXLSX) {
                documents.addAll(searchDocuments(printDirPath, "xlsx"));
            }
            if (printPPT) {
                documents.addAll(searchDocuments(printDirPath, "ppt"));
            }
            if (printPPTX) {
                documents.addAll(searchDocuments(printDirPath, "pptx"));
            }
        }

        return documents;
    }

    private List<Path> searchDocuments(Path printDirPath, String suffix)
            throws IOException {

        List<Path> documents = new ArrayList<>();

        if (scanDirectoriesRecursively) {
            BiPredicate<Path, BasicFileAttributes> predicate = (path, attributes)
                    -> path.toString().toLowerCase().endsWith(
                            suffix.toLowerCase());
            try (Stream<Path> stream
                    = Files.find(printDirPath, Integer.MAX_VALUE, predicate)) {
                stream.forEach(path -> {
                    LOGGER.log(Level.INFO, "found {0} to print: {1}",
                            new Object[]{suffix, path});
                    documents.add(path);
                });
            } catch (NoSuchFileException e) {
                // not important, sanity check is done earlier
                LOGGER.log(Level.INFO, "", e);
            }

        } else {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(
                    printDirPath, new MyFilter(suffix))) {
                for (Path path : stream) {
                    LOGGER.log(Level.INFO, "found {0} to print: {1}",
                            new Object[]{suffix, path});
                    documents.add(path);
                }
            } catch (NoSuchFileException e) {
                // not important, sanity check is done earlier
                LOGGER.log(Level.INFO, "", e);
            }
        }

        return documents;
    }

    private void backup(StorageDevice storageDevice,
            Partition exchangePartition)
            throws DBusException, IOException, NoSuchAlgorithmException {

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
        Files.createDirectories(destination);

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
                    exchangePartition.getFullDeviceAndNumber(),
                    label, exchangePartitionFileSystem, dlCopyGUI);
        }
    }

    private void resetDataPartition(
            Partition systemPartition, Partition dataPartition)
            throws DBusException, IOException {

        if (dataPartition == null || !deleteOnDataPartition) {
            return;
        }

        ProcessExecutor processExecutor = new ProcessExecutor();
        String cleanupRoot = null;

        if (formatDataPartition) {
            // format data partition
            dlCopyGUI.showResetFormattingDataPartition();

            // TODO: support encryption
            DLCopy.formatPersistencePartition(
                    dataPartition.getFullDeviceAndNumber(), false, null,
                    false, null, false, dataPartitionFileSystem, dlCopyGUI);

            cleanupRoot = dataPartition.mount().getMountPath() + "/rw";

        } else if (resetSystem || resetHome) {
            // remove files from data partition
            dlCopyGUI.showResetRemovingFiles();

            MountInfo mountInfo = dataPartition.mount();
            String mountPoint = mountInfo.getMountPath();
            cleanupRoot = mountPoint;
            if (!Files.exists(Paths.get(mountPoint, "home"))) {
                // Debian 9 and newer
                cleanupRoot = mountPoint + "/rw";
            }
            if (resetSystem && resetHome) {
                // remove all files
                // but keep "/lost+found/" and "persistence.conf"
                processExecutor.executeProcess("find", mountPoint,
                        "!", "-regex", mountPoint,
                        "!", "-regex", mountPoint + "/lost\\+found",
                        "!", "-regex", mountPoint + "/persistence.conf",
                        "-delete");
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
                            "-delete");
                }
                if (resetHome) {
                    // only remove "/home/user/"
                    processExecutor.executeProcess(
                            "rm", "-rf", cleanupRoot + "/home/user/");
                }
            }
        }

        /**
         * Restore /home/user also after formatting the data partition.
         * Otherwise, when restoring files into /home/user here at the Resetter
         * the home directory would already be there after reboot and the user
         * setup scripts would not populate it from /etc/skel and we end up with
         * a very incomplete /home/user folder.
         */
        if (formatDataPartition || resetHome) {

            /**
             * We have to assemble the union here so that we can copy
             * "etc/skel/" from the storage media that is currently reset. (We
             * can't use "etc/skel/" from the currently running system as this
             * might have a completely different configuration!)
             */
            // union squashfs with data partition
            MountInfo systemMountInfo = systemPartition.mount();
            List<String> readOnlyMountPoints
                    = LernstickFileTools.mountAllSquashFS(
                            systemMountInfo.getMountPath());
            File overlayDir = LernstickFileTools.mountOverlay(
                    dataPartition.getMountPath(), readOnlyMountPoints, true);
            String cowPath = new File(overlayDir, "merged").getPath();

            // restore "/home/user/" from "/etc/skel/"
            processExecutor.executeProcess("mkdir", "-p",
                    cleanupRoot + "/home/");
            processExecutor.executeProcess("cp", "-a",
                    cowPath + "/etc/skel/", cleanupRoot + "/home/user/");
            processExecutor.executeProcess("chown", "-R",
                    "user.user", cleanupRoot + "/home/user/");

            // disassemble union
            try {
                TimeUnit.SECONDS.sleep(5);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
            DLCopy.umount(cowPath, dlCopyGUI);
            for (String readOnlyMountPoint : readOnlyMountPoints) {
                DLCopy.umount(readOnlyMountPoint, dlCopyGUI);
            }
            if (!systemMountInfo.alreadyMounted()) {
                DLCopy.umount(systemPartition, dlCopyGUI);
            }
        }
    }

    private void restoreFiles(Partition dataPartition)
            throws IOException, DBusException, NoSuchAlgorithmException {

        if (!restoreData || overwriteEntries.isEmpty()) {
            return;
        }

        MountInfo mountInfo = dataPartition.mount();
        String mountPoint = mountInfo.getMountPath();
        String restoreRoot = mountPoint;
        if (!Files.exists(Paths.get(mountPoint, "home"))) {
            // Debian 9 and newer
            restoreRoot = mountPoint + "/rw";
        }

        for (OverwriteEntry overwriteEntry : overwriteEntries) {

            FileCopier fileCopier = new FileCopier();
            dlCopyGUI.showResetRestore(fileCopier);

            String sourceString = overwriteEntry.getSource();
            Source[] sources = new Source[]{
                Files.isDirectory(Paths.get(sourceString))
                ? new Source(sourceString, ".*")
                : new Source(sourceString)
            };

            String destination
                    = restoreRoot + '/' + overwriteEntry.getDestination();
            if (Files.isDirectory(Paths.get(destination))) {
                LernstickFileTools.recursiveDelete(
                        new File(destination), false);
            }
            String[] destinations = new String[]{destination};

            fileCopier.copy(new CopyJob(sources, destinations));

            // TODO: Above we are copying files as root so that the normal user
            // has no access. We "fix" this by blindly chowning the destination
            // below. This must be configurable somehow.
            ProcessExecutor executor = new ProcessExecutor();
            executor.executeProcess("chown", "-R", "user.user", destination);
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
