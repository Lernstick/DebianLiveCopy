
import dlcopy.tools.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.freedesktop.DBus;
import org.freedesktop.UDisks;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.exceptions.DBusException;

public class DbusTest implements PropertyChangeListener {

    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";

    public DbusTest() {
        // monitor udisks changes
        Thread udisksMonitorThread = new Thread() {

            @Override
            public void run() {
                ProcessExecutor executor = new ProcessExecutor();
                executor.addPropertyChangeListener(DbusTest.this);
                executor.executeProcess("udisks", "--monitor");
            }
        };
        udisksMonitorThread.start();

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

        } catch (DBusException ex) {
            ex.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new DbusTest();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("line".equals(evt.getPropertyName())) {
            String line = (String) evt.getNewValue();
            if (line.startsWith(UDISKS_ADDED)) {
                String path = line.substring(UDISKS_ADDED.length()).trim();
                System.out.println("path: \"" + path + '\"');
            } else if (line.startsWith(UDISKS_REMOVED)) {
                String path = line.substring(UDISKS_REMOVED.length()).trim();
                System.out.println("path: \"" + path + '\"');                
            }
        }
    }
}
