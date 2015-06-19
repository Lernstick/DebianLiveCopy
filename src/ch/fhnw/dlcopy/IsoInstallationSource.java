package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.ProcessExecutor;
import ch.fhnw.util.StorageDevice;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Installation source from a loop mounted ISO file
 *
 */
public class IsoInstallationSource implements InstallationSource {

    private final static Logger LOGGER
            = Logger.getLogger(IsoInstallationSource.class.getName());

    private final String mediaPath;
    private final ProcessExecutor processExecutor;
    private final DebianLiveVersion version;

    private String rootFsPath = null;

    public IsoInstallationSource(String mediaPath,
            ProcessExecutor processExecutor) {
        this.mediaPath = mediaPath;
        this.processExecutor = processExecutor;
        this.version = validateIsoImage();

        if (version == null) {
            throw new IllegalStateException(mediaPath
                    + " is not a valid LernStick distribution image");
        }

        LOGGER.log(Level.INFO, "Install from ISO image at '{0}'", mediaPath);
        LOGGER.log(Level.INFO, "ISO system version {0}", version);
    }

    @Override
    public String getDeviceName() {
        return "";
    }

    @Override
    public StorageDevice.Type getDeviceType() {
        return StorageDevice.Type.OpticalDisc;
    }

    @Override
    public boolean hasBootPartition() {
        return false;
    }

    @Override
    public boolean hasExchangePartition() {
        return false;
    }

    @Override
    public DataPartitionMode getDataPartitionMode() {
        return DataPartitionMode.NotUsed;
    }

    @Override
    public DebianLiveVersion getSystemVersion() {
        return version;
    }

    @Override
    public String getSystemPath() {
        return mediaPath;
    }

    @Override
    public Source getBootCopySource() {
        return new Source(mediaPath,
                InstallationSource.BOOT_COPY_PATTERN);
    }

    @Override
    public Source getExchangeBootCopySource() {
        return new Source(mediaPath,
                InstallationSource.EXCHANGE_BOOT_COPY_PATTERN);
    }

    @Override
    public Source getSystemCopySource() {
        return new Source(mediaPath,
                InstallationSource.SYSTEM_COPY_PATTERM);
    }

    @Override
    public Source getPersistentCopySource() {
        return null;
    }

    @Override
    public Source getExchangeCopySource() {
        return null;
    }

    @Override
    public Partition getBootPartition() {
        return null;
    }

    @Override
    public Partition getExchangePartition() {
        return null;
    }

    @Override
    public Partition getDataPartition() {
        return null;
    }

    @Override
    public String getMbrPath() {
        return version.getMbrFilePath();
    }

    @Override
    public void unmountTmpPartitions() {
    }

    private DebianLiveVersion validateIsoImage() {
        try {
            File infoFile = new File(mediaPath + "/.disk/info");
            if (!infoFile.exists()) {
                LOGGER.severe("Can't find .disk/info file"
                        + "- not a valid Lernstick image");
                return null;
            }
            String signature = LernstickFileTools.readFile(infoFile).get(0);
            if (signature.contains("Wheezy")) {
                return DebianLiveVersion.DEBIAN_7;
            } else if (signature.contains("Jessie")) {
                return DebianLiveVersion.DEBIAN_8;
            }
            LOGGER.log(Level.SEVERE, "Invalid version signature:  {0}",
                    signature);
            return null;
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "Could not read .disk/info", ex);
            return null;
        }
    }

}
