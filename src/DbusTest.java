
import java.util.List;
import org.freedesktop.DBus;
import org.freedesktop.UDisks;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.Path;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.udisks.Device;

public class DbusTest {

    public static void main(String[] args) {
        try {
            DBusConnection connection =
                    DBusConnection.getConnection(DBusConnection.SYSTEM);
            UDisks uDisks = (UDisks) connection.getRemoteObject(
                    "org.freedesktop.UDisks", "/org/freedesktop/UDisks");

            // test properties
            DBus.Properties properties = connection.getRemoteObject(
                    "org.freedesktop.UDisks",
                    "/org/freedesktop/UDisks", DBus.Properties.class);
            Boolean supportsLuksDevices = properties.Get(
                    "org.freedesktop.UDisks", "SupportsLuksDevices");
            System.out.println("SupportsLuksDevices: " + supportsLuksDevices);

            List<Path> devicePaths = uDisks.EnumerateDevices();
            for (Path devicePath : devicePaths) {
                String path = devicePath.getPath();
                DBus.Properties deviceProperties = connection.getRemoteObject(
                        "org.freedesktop.UDisks", path,
                        DBus.Properties.class);
                Boolean isDrive = deviceProperties.Get(
                        "org.freedesktop.UDisks", "DeviceIsDrive");
                UInt64 size64 = deviceProperties.Get(
                        "org.freedesktop.UDisks", "DeviceSize");
                long size = size64.longValue();
                if (isDrive && (size > 0)) {
                    System.out.println();
                    System.out.println(path);
                    System.out.println(" size: " + size);
                    String driveVendor = deviceProperties.Get(
                            "org.freedesktop.UDisks", "DriveVendor");
                    System.out.println(" driveVendor: " + driveVendor);
                    Boolean removable = deviceProperties.Get(
                            "org.freedesktop.UDisks", "DeviceIsRemovable");
                    System.out.println(" removable: " + removable);
                    Device device = connection.getRemoteObject(
                            "org.freedesktop.UDisks", path, Device.class);
                }
            }
        } catch (DBusException ex) {
            ex.printStackTrace();
        }
    }
}
