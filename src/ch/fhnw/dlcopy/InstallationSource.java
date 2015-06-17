package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice.Type;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Encapsulate the source of an install or update operation
 */
public interface InstallationSource {
    public final static String BOOT_COPY_PATTERN
            = "boot.*|efi.*|isolinux.*|.VolumeIcon.icns|"
            + "live/initrd.*|live/memtest|live/vmlinuz.*|efi.img";
    public final static String SYSTEM_COPY_PATTERM
            = "md5sum.txt"
            + "\\.disk.*|live/filesystem.*|md5sum.txt";
    
    public  enum DataPartitionMode {

        ReadWrite, ReadOnly, NotUsed
    }
    public String getDeviceName();
    public Type getDeviceType();
    
    public boolean hasBootPartition();
    public boolean hasExchangePartition();
    
    public DataPartitionMode getDataPartitionMode();
    
    public DebianLiveVersion getSystemVersion(); 
    public String getSystemPath();
    
    // Source definitions for copy jobs
    // Partitions may be mounted as needed if not already available
    public Source getBootCopySource() throws DBusException;
    public Source getSystemCopySource();
    public Source getPersistentCopySource();
    public Source getExchangeCopySource() throws DBusException;
    
    public Partition getBootPartition();
    public Partition getExchangePartition();
    public Partition getDataPartition();
    
    public String getMbrPath();
    
    // Unmount any partitions that were mounted by any get*CopySource()
    public void unmountTmpPartitions();
}
