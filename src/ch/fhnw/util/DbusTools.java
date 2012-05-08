package ch.fhnw.util;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.freedesktop.DBus;
import org.freedesktop.dbus.DBusConnection;
import org.freedesktop.dbus.UInt64;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.udisks.Device;

/**
 * A collection of useful functions regarding dbus-java
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class DbusTools {

    private static final Logger LOGGER =
            Logger.getLogger(DbusTools.class.getName());
    private static DBusConnection dbusSystemConnection;

    static {
        try {
            dbusSystemConnection = DBusConnection.getConnection(
                    DBusConnection.SYSTEM);
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    /**
     * returns a dbus Device object for a given device
     * @param device the given device (e.g. "sda1")
     * @return a dbus Device object for a given device
     * @throws DBusException if a d-bus exception occurs
     */
    public static Device getDevice(String device) throws DBusException {
        return dbusSystemConnection.getRemoteObject("org.freedesktop.UDisks",
                "/org/freedesktop/UDisks/devices/" + device, Device.class);
    }

    /**
     * returns a property of a partition device as a string
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a string
     * @throws DBusException if a d-bus exception occurs
     */
    public static String getStringProperty(String device, String property)
            throws DBusException {
        String dbusPath = "/org/freedesktop/UDisks/devices/" + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }

    /**
     * returns a property of a partition device as a list of strings
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a list of strings
     * @throws DBusException if a d-bus exception occurs
     */
    public static List<String> getStringListProperty(String device,
            String property) throws DBusException {
        String dbusPath = "/org/freedesktop/UDisks/devices/" + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }

    /**
     * returns a property of a partition device as a long value
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a long value
     * @throws DBusException if a d-bus exception occurs
     */
    public static long getLongProperty(String device, String property)
            throws DBusException {
        String dbusPath = "/org/freedesktop/UDisks/devices/" + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        UInt64 value = deviceProperties.Get("org.freedesktop.UDisks", property);
        return value.longValue();
    }
    
    /**
     * returns a property of a partition device as a boolean value
     * @param device the device to query
     * @param property the property to query
     * @return a property of a partition device as a boolean value
     * @throws DBusException if a d-bus exception occurs
     */
    public static Boolean getBooleanProperty(String device, String property)
            throws DBusException {
        String dbusPath = "/org/freedesktop/UDisks/devices/" + device;
        LOGGER.log(Level.INFO, "dbusPath = \"{0}\", property = \"{1}\"",
                new Object[]{dbusPath, property});
        DBus.Properties deviceProperties = dbusSystemConnection.getRemoteObject(
                "org.freedesktop.UDisks", dbusPath, DBus.Properties.class);
        return deviceProperties.Get("org.freedesktop.UDisks", property);
    }
}
