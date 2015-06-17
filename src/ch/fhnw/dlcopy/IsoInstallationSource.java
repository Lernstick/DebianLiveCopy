package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.util.logging.Logger;

/**
 * Installation source from a loop mounted ISO file
 * 
 */
public class IsoInstallationSource implements InstallationSource {
    private final static Logger LOGGER
            = Logger.getLogger(IsoInstallationSource.class.getName());
    
    private final String rootPath;
    private final InstallationSource systemInstallationSource;
    
    public IsoInstallationSource(String rootPath,
            InstallationSource systemInstallationSource) {
        this.rootPath = rootPath;
        this.systemInstallationSource = systemInstallationSource;
        LOGGER.info("Install from ISO image at " + rootPath);
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
        // TODO: figure out a way to detect version from ISO image
        return DebianLiveVersion.DEBIAN_7;
    }

    @Override
    public String getSystemPath() {
        return rootPath;
    }

    @Override
    public Source getBootCopySource() {
        return new Source(rootPath,
                    "boot.*|efi.*|isolinux.*|.VolumeIcon.icns|"
                    + "live/initrd.*|live/memtest|live/vmlinuz.*|efi.img");
    }

    @Override
    public Source getSystemCopySource() {
        return new Source(rootPath, "md5sum.txt");
                    //"\\.disk.*|live/filesystem.*|md5sum.txt");
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
        return systemInstallationSource.getMbrPath();
    }

    @Override
    public void unmountTmpPartitions() {
    }
    
}
