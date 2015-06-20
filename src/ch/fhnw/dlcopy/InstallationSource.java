package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.Source;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice.Type;
import java.io.IOException;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * Encapsulate the source of an install or update operation
 */
public interface InstallationSource {

    public final static String EXCHANGE_BOOT_COPY_PATTERN
            = "boot.*|efi.*|live/initrd.*|live/vmlinuz.*"
            + "|efi.img|lernstick.ico|autorun.inf";
    public final static String BOOT_COPY_PATTERN
            = EXCHANGE_BOOT_COPY_PATTERN
            + "|isolinux.*|.VolumeIcon.icns|live/memtest";
    public final static String SYSTEM_COPY_PATTERM
            = "\\.disk.*|live/filesystem.*|md5sum.txt";

    public enum DataPartitionMode {

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

    public Source getExchangeBootCopySource() throws DBusException;

    public Source getSystemCopySource();

    public Source getPersistentCopySource();

    public Source getExchangeCopySource() throws DBusException;

    public Partition getBootPartition();

    public Partition getExchangePartition();

    public Partition getDataPartition();

    public String getMbrPath();

    public int installSyslinux(String bootDevice) throws IOException;

    // Unmount any partitions that were mounted by any get*CopySource()
    public void unmountTmpPartitions();
}
