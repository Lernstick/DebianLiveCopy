package org.freedesktop.udisks;

import java.util.List;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.DBusInterfaceName;
import org.freedesktop.dbus.DBusSignal;
import org.freedesktop.dbus.UInt32;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;

@DBusInterfaceName("org.freedesktop.UDisks.Device")
public interface Device extends DBusInterface {

    public static class JobChanged extends DBusSignal {

        public final boolean a;
        public final String b;
        public final UInt32 c;
        public final boolean d;
        public final double e;

        public JobChanged(String path, boolean a, String b, UInt32 c, boolean d, 
                double e) throws DBusException {
            super(path, a, b, c, d, e);
            this.a = a;
            this.b = b;
            this.c = c;
            this.d = d;
            this.e = e;
        }
    }

    public static class Changed extends DBusSignal {

        public Changed(String path) throws DBusException {
            super(path);
        }
    }

    public BenchmarkResult<List<TransferRate>, List<TransferRate>, List<AccessTime>> DriveBenchmark(
            boolean do_write_benchmark, List<String> options);

    public void DriveAtaSmartInitiateSelftest(
            String test, List<String> options);

    public void DriveAtaSmartRefreshData(List<String> options);

    public void DriveUnsetSpindownTimeout(String cookie);

    public String DriveSetSpindownTimeout(
            int timeout_seconds, List<String> options);

    public void DriveDetach(List<String> options);

    public void DriveEject(List<String> options);

    public void DrivePollMedia();

    public void DriveUninhibitPolling(String cookie);

    public String DriveInhibitPolling(List<String> options);

    public UInt64 LinuxMdCheck(List<String> options);

    public void LinuxLvm2LVStop(List<String> options);

    public void LinuxMdStop(List<String> options);

    public void LinuxMdRemoveComponent(
            DBusInterface component, List<String> options);

    public void LinuxMdExpand(
            List<DBusInterface> components, List<String> options);

    public void LinuxMdAddSpare(DBusInterface component, List<String> options);

    public void LuksChangePassphrase(
            String current_passphrase, String new_passphrase);

    public void LuksLock(List<String> options);

    public DBusInterface LuksUnlock(String passphrase, List<String> options);

    public List<OpenFile> FilesystemListOpenFiles();

    public boolean FilesystemCheck(List<String> options);

    public void FilesystemUnmount(List<String> options);

    public String FilesystemMount(String filesystem_type, List<String> options);

    public void FilesystemSetLabel(String new_label);

    public void FilesystemCreate(String fstype, List<String> options);

    public void PartitionModify(String type, String label, List<String> flags);

    public DBusInterface PartitionCreate(UInt64 offset, UInt64 size, 
            String type, String label, List<String> flags, List<String> options, 
            String fstype, List<String> fsoptions);

    public void PartitionDelete(List<String> options);

    public void PartitionTableCreate(String scheme, List<String> options);

    public void JobCancel();
}
