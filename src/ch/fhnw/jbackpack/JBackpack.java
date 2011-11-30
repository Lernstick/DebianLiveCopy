/**
 * Copyright (C) 2010 imedias
 *
 * This file is part of JBackpack.
 *
 * JBackpack is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * JBackpack is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.ProcessExecutor;
import java.awt.SplashScreen;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

/**
 * the JBackpack main class
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class JBackpack {

    /**
     * preferences key for the last backup timestamp
     */
    public static final String LAST_BACKUP = "last_backup";
    /**
     * preferences key for showing the reminder
     */
    public static final String SHOW_REMINDER = "show_reminder";
    /**
     * preferences key for the reminder timeout (given in days)
     */
    public static final String REMINDER_TIMEOUT = "reminder_timeout";

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        if ((args.length > 0) && "--reminder".equals(args[0])) {
            Preferences preferences =
                    Preferences.userNodeForPackage(JBackpack.class);

            // do we have to show the reminder at all?
            if (!preferences.getBoolean(SHOW_REMINDER, false)) {
                return;
            }

            // after how many days are we supposed to show a reminder?
            int reminderTimeout = preferences.getInt(
                    REMINDER_TIMEOUT, Integer.MIN_VALUE);
            if (reminderTimeout == Integer.MIN_VALUE) {
                Logger.getLogger(JBackpack.class.getName()).warning(
                        "could not find reminder timeout in preferences!");
                return;
            }

            // when was the last backup?
            long lastBackup = preferences.getLong(LAST_BACKUP, Long.MIN_VALUE);
            if (lastBackup == Long.MIN_VALUE) {
                Logger.getLogger(JBackpack.class.getName()).info(
                        "no backup timestamp found");
            }

            // how long is it ago?
            long now = System.currentTimeMillis();
            int daysSinceLastBackup =
                    (int) ((now - lastBackup) / 86400000);
            if (daysSinceLastBackup < reminderTimeout) {
                return;
            }

            // we have to show a reminder
            setLookAndFeel();
            checkJavaVersion();
            Date lastBackupDate = new Date(lastBackup);
            DateFormat dateFormat = DateFormat.getDateTimeInstance(
                    DateFormat.FULL, DateFormat.MEDIUM);
            String timeString = dateFormat.format(lastBackupDate);
            ResourceBundle bundle = ResourceBundle.getBundle(
                    "ch/fhnw/jbackpack/Strings");
            String message = bundle.getString("Reminder_Message");
            message = MessageFormat.format(message, timeString);
            Object[] options = new Object[]{
                bundle.getString("Start_Backup_Program"),
                bundle.getString("Cancel")
            };
            // keep splashscreen open as long as possible...
            SplashScreen splashScreen = SplashScreen.getSplashScreen();
            if (splashScreen != null) {
                splashScreen.close();
            }
            int returnValue = JOptionPane.showOptionDialog(
                    null, message, bundle.getString("Reminder"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE, null, options, options[0]);
            if (returnValue == JOptionPane.YES_OPTION) {
                systemCheck();
            }

        } else {
            // a normal application start without any command line arguments
            setLookAndFeel();
            checkJavaVersion();
            systemCheck();
        }
    }

    /**
     * sets the look&feel depending on the current operating system
     */
    public static void setLookAndFeel() {

        System.setProperty("awt.useSystemAAFontSettings", "on");

//        UIManager.LookAndFeelInfo[] lookAndFeelInfos =
//                UIManager.getInstalledLookAndFeels();
//        for (UIManager.LookAndFeelInfo lookAndFeelInfo : lookAndFeelInfos) {
//            System.out.println(lookAndFeelInfo.getName()
//                    + ": " + lookAndFeelInfo.getClassName());
//        }
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                try {
                    // NimbusLookAndFeel is the only acceptable LAF on Linux.
                    // The filechooser in GTK LAF is horribly broken and all
                    // other LAFs just look plain ugly.
                    UIManager.setLookAndFeel("com.sun.java.swing.plaf."
                            + "nimbus.NimbusLookAndFeel");
                } catch (Exception ex) {
                    Logger.getLogger(JBackpack.class.getName()).log(
                            Level.WARNING,
                            "failed to set look&feel", ex);
                }
                break;

            case Windows:
                // use system look&feel
                try {
                    UIManager.setLookAndFeel(
                            UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ex) {
                    Logger.getLogger(JBackpack.class.getName()).log(
                            Level.WARNING,
                            "failed to set system look&feel", ex);
                }
                break;

            case Mac_OS_X:
                System.setProperty("apple.laf.useScreenMenuBar", "true");
        }
    }

    /**
     * checks that JBackpack can be run
     */
    public static void systemCheck() {
        ProcessExecutor processExecutor = new ProcessExecutor();
        int returnValue = processExecutor.executeProcess(
                "rdiff-backup", "--version");

        if (returnValue == 0) {
            java.awt.EventQueue.invokeLater(new Runnable() {

                public void run() {
                    new BackupFrame().setVisible(true);
                }
            });

        } else {
            Logger.getLogger(JBackpack.class.getName()).log(
                    Level.INFO,
                    "return value of \"rdiff-backup --version\": {0}",
                    returnValue);
            ResourceBundle bundle = ResourceBundle.getBundle(
                    "ch/fhnw/jbackpack/Strings");
            switch (CurrentOperatingSystem.OS) {
                case Mac_OS_X:
                    MacOSXSetupHelpFrame macFrame = new MacOSXSetupHelpFrame();
                    macFrame.setVisible(true);
                    break;

                case Windows:
                    WindowsSetupHelpFrame frame = new WindowsSetupHelpFrame();
                    frame.setVisible(true);
                    break;

                default:
                    JOptionPane.showMessageDialog(null,
                            bundle.getString("Error_No_Rdiff-Backup"),
                            bundle.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                    System.exit(-1);
            }
        }
    }

    private static void checkJavaVersion() {
        String javaVersion = System.getProperty("java.version");
        String[] tokens = javaVersion.split("\\.");
        int majorVersion = Integer.parseInt(tokens[0]);
        int minorVersion = Integer.parseInt(tokens[1]);
        if (majorVersion <= 1 && minorVersion <= 5) {
            ResourceBundle bundle = ResourceBundle.getBundle(
                    "ch/fhnw/jbackpack/Strings");
            String errorMessage = bundle.getString("Error_Java_Version");
            Logger.getLogger(JBackpack.class.getName()).log(
                    Level.SEVERE, errorMessage);
            JOptionPane.showMessageDialog(null, errorMessage,
                    bundle.getString("Error"), JOptionPane.ERROR_MESSAGE);
            System.exit(-1);
        }
    }
}
