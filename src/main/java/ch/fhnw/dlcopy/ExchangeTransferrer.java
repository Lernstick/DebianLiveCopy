package ch.fhnw.dlcopy;

import ch.fhnw.dlcopy.gui.DLCopyGUI;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A Transferrer for transferring the exchange partition
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ExchangeTransferrer extends Transferrer {

    private final Partition sourcePartition;
    private final Partition destinationPartition;

    private MountInfo sourceMountInfo;
    private MountInfo destinationMountInfo;

    /**
     * Creates a new ExchangeTransferrer.
     *
     * @param gui the DLCopyGUI currently in use
     * @param sourcePartition the source exchange partition
     * @param destinationPartition the destination exchange partition
     */
    public ExchangeTransferrer(DLCopyGUI gui,
            Partition sourcePartition, Partition destinationPartition) {

        super(gui);
        this.sourcePartition = sourcePartition;
        this.destinationPartition = destinationPartition;
    }

    /**
     * Transfers all data from one exchange partition to another.
     *
     * @param checkCopies if the file copies should be verified
     * @throws IOException if an I/O error occurs
     * @throws DBusException if a D-Bus exception occurs
     * @throws NoSuchAlgorithmException if FileCopier can't load its message
     * digest algorithm used for verification of the copies
     */
    public void transfer(boolean checkCopies)
            throws IOException, DBusException, NoSuchAlgorithmException {

        FileCopier fileCopier = new FileCopier();
        gui.showInstallFileCopy(fileCopier);

        mount();

        Source source = new Source(sourceMountInfo.getMountPath(), ".*");
        CopyJob copyJob = new CopyJob(
                new Source[]{source},
                new String[]{destinationMountInfo.getMountPath()});

        fileCopier.copy(checkCopies, copyJob);

        unmount();
    }

    private void mount() throws DBusException, IOException {
        sourceMountInfo = sourcePartition.mount();
        destinationMountInfo = destinationPartition.mount();
    }

    private void unmount() throws IOException, DBusException {
        unmountAndDelete(sourceMountInfo, sourcePartition);
        unmountAndDelete(destinationMountInfo, destinationPartition);
    }
}
