/*
 * CurrentOperatingSystem.java
 *
 * Created on 24.07.2010, 12:52:57
 *
 */
package dlcopy.tools;

/**
 * The currently running operating system
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class CurrentOperatingSystem {

    /**
     * the currently running operating system
     */
    public static final OperatingSystem OS;

    static {
        String osName = System.getProperty("os.name");
        if (osName.equals("Linux")) {
            OS = OperatingSystem.Linux;
        } else if (osName.equals("Mac OS X")) {
            OS = OperatingSystem.Mac_OS_X;
        } else if (osName.startsWith("Windows")) {
            OS = OperatingSystem.Windows;
        } else {
            OS = OperatingSystem.Unknown;
        }
    }
}
