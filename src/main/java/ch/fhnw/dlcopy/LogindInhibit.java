package ch.fhnw.dlcopy;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Inhibits suspend and shutdown while DLCopy is running.
 */
public class LogindInhibit {

    private final static Logger LOGGER
            = Logger.getLogger(LogindInhibit.class.getName());

    private static final String INHIBIT_WHAT = "idle:shutdown:sleep";

    private Process process;

    public LogindInhibit(String reason) {
        // Ideally, we would communicate with logind directly via DBus, but:
        //
        // dbus-java¹ does not seem to work well with logind’s Inhibit method²
        // AIUI, based on looking at a dbus-monitor session, logind calls
        // GetConnectionUnixUser and GetConnectionUnixProcessID to find the
        // UNIX user and process id of the calling process. dbus-java does not
        // seem to support this at all, hence the call never returns.
        //
        // Once a better DBus library is available in Java, the code below
        // should be replaced.
        //
        // ① http://dbus.freedesktop.org/releases/dbus-java/
        // ② https://wiki.freedesktop.org/www/Software/systemd/inhibit/

        process = null;

        try {
            // We need to use /bin/sh -c to get $PPID expanded to the PID of
            // the java process, as java does not have a way to get the PID.
            //
            // We let systemd-inhibit call /bin/sh again and check in a loop
            // whether the java process still works. This is necessary as a
            // safe-guard because systemd-inhibit does not terminate when the
            // java process is killed. If we did not do this, users might not
            // be able to shut down their computer when DLCopy crashes.
            ProcessBuilder probuilder = new ProcessBuilder(
                    "/bin/sh",
                    "-c",
                    "exec systemd-inhibit "
                    + "--mode=block "
                    + "--what=" + INHIBIT_WHAT + ' '
                    + "--who=DLCopy "
                    + "--why=\"" + reason + "\" "
                    + "/bin/sh -c \"while [ -d /proc/$PPID ] && "
                    + "[ -z \\`grep zombie /proc/$PPID/status\\` ]; "
                    + "do sleep 1; done\"");
            probuilder.inheritIO();
            process = probuilder.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE,
                    "Could not inhibit shutdowns: %s", e.getMessage());
        }
    }

    public void delete() {
        if (process == null) {
            return;
        }

        // Killing the systemd-inhibit process kills the file descriptor which
        // logind uses for the inhibit, which removes the inhibit.
        process.destroy();
        // TODO: use destroyForcibly() after switching to Java 8
        //process.destroyForcibly();
    }
}
