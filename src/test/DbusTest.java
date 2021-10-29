
import ch.fhnw.util.ProcessExecutor;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.DBusInterface;
import org.freedesktop.dbus.ObjectManager;
import org.freedesktop.dbus.Variant;
import org.freedesktop.dbus.exceptions.DBusException;

public class DbusTest implements PropertyChangeListener {

    private final static String UDISKS_ADDED = "added:";
    private final static String UDISKS_REMOVED = "removed:";

    public DbusTest() {

        String binaryName = null;
        String parameter = null;
        Runtime runtime = Runtime.getRuntime();
        try {
            runtime.exec("udisks -h");
            binaryName = "udisks";
            parameter = "--monitor";
        } catch (IOException ex) {
            try {
                runtime.exec("udisksctl help");
                binaryName = "udisksctl";
                parameter = "monitor";
            } catch (IOException ex1) {
            }
        }
        final String finalBinaryName = binaryName;
        final String finalParameter = parameter;

        // monitor udisks changes
        Thread udisksMonitorThread = new Thread() {

            @Override
            public void run() {
                ProcessExecutor executor = new ProcessExecutor();
                executor.addPropertyChangeListener(DbusTest.this);
                executor.executeProcess(finalBinaryName, finalParameter);
            }
        };
        udisksMonitorThread.start();

        try {
            DBusConnection connection
                    = DBusConnection.getConnection(DBusConnection.SYSTEM);

            // test method call
            ObjectManager objectManager = connection.getRemoteObject(
                    "org.freedesktop.UDisks2", "/org/freedesktop/UDisks2",
                    ObjectManager.class);
            Map<DBusInterface, Map<String, Map<String, Variant>>> managedObjects
                    = objectManager.GetManagedObjects();
            
            // this fails!!!
//            Set<DBusInterface> keySet = managedObjects.keySet();
//            for (DBusInterface key : keySet) {
//                System.out.println("key: " + key);
//            }

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
