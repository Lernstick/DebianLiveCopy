/*
 * AutoStarter.java
 *
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
 *
 * Created on 13. Februar 2006, 10:29
 *
 */
package ch.fhnw.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jnlp.BasicService;
import javax.jnlp.ServiceManager;

/**
 * A class for configuring autostart at login time for different desktop systems
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class AutoStarter {

    private static final Logger LOGGER =
            Logger.getLogger(AutoStarter.class.getName());
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String PATH_SEPARATOR =
            System.getProperty("path.separator");
    private final static String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private static final String WINDOWS_RUN_TREE =
            "HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\"
            + "CurrentVersion\\Run";
    private static final File KDE_AUTOSTART_DIR =
            new File(USER_HOME + "/.kde/Autostart");
    private static final File FREE_DESKTOP_AUTOSTART_DIR =
            new File(USER_HOME + "/.config/autostart");
    private static final File OSX_LAUNCH_AGENTS_DIR =
            new File(USER_HOME, "Library/LaunchAgents");
    private final File osxScriptDir;
    private final File osxLaunchAgentsFile;
    private final URL codeBase;
    private final String startCommand;
    private final boolean isIDE_Start;

    /**
     * creates a new Autostarter
     * @param jnlpFileName the JNLP file name
     * @param osxDockName the application name for the Mac OS X dock
     * @param osxScriptsDirName the name of the OS X scripts directory
     * @param osxLaunchAgentsFileName the name of the OS X LauchAgent file
     * @param options the application command line options
     */
    public AutoStarter(String jnlpFileName, String osxDockName,
            String osxScriptsDirName, String osxLaunchAgentsFileName,
            String options) {

        // assemble some OS X specific variables
        osxScriptDir = new File(USER_HOME,
                "Library/Scripts/Applications/" + osxScriptsDirName);
        osxLaunchAgentsFile = new File(OSX_LAUNCH_AGENTS_DIR,
                osxLaunchAgentsFileName + ".plist");

        // test, if we are running from Java Web Start or not
        BasicService basicService = null;
        try {
            basicService = (BasicService) ServiceManager.lookup(
                    "javax.jnlp.BasicService");
        } catch (javax.jnlp.UnavailableServiceException ex) {
            LOGGER.log(Level.FINE, "not starting via Java Web Start", ex);
        }
        if (basicService == null) {
            // this is a "normal" start
            codeBase = null;
            String pathToJAR = getPathToJAR();
            isIDE_Start = pathToJAR.contains(PATH_SEPARATOR);
            switch (CurrentOperatingSystem.OS) {
                default:
                    startCommand =
                            "java -jar \"" + pathToJAR + "\""
                            + ((options == null) ? "" : ' ' + options);
                    break;

                case Mac_OS_X:
                    int appIndex = pathToJAR.indexOf(
                            ".app/Contents/Resources/Java/");
                    if (appIndex == -1) {
                        // started via JAR
                        startCommand =
                                "java -Xdock:name=" + osxDockName
                                + " -jar \"" + pathToJAR + "\""
                                + ((options == null) ? "" : ' ' + options);
                    } else {
                        // started via Mac OS X application
                        String stubPath = pathToJAR.substring(0, appIndex)
                                + ".app/Contents/MacOS/JavaApplicationStub";
                        startCommand = "exec \"" + stubPath + '\"'
                                + ((options == null) ? "" : ' ' + options);
                    }
                    break;

                case Windows:
                    startCommand =
                            "\"javaw -jar \\\""
                            + pathToJAR.replaceAll("\\\\", "\\\\\\\\") + "\\\""
                            + ((options == null) ? "" : ' ' + options) + '\"';
            }
        } else {
            // this is a Java Web Start
            codeBase = basicService.getCodeBase();
            isIDE_Start = false;
            startCommand = "javaws"
                    + ((options == null) ? "" : " -open " + options)
                    + ' ' + codeBase + jnlpFileName + ".jnlp";
        }
        LOGGER.log(Level.FINEST, "startCommand: {0}", startCommand);
    }

    /**
     * returns <code>true</code>, if the programm is running via Java Web Start,
     * <code>false</code> otherwise
     * @return <code>true</code>, if the programm is running via Java Web Start,
     * <code>false</code> otherwise
     */
    public boolean isWebStart() {
        return codeBase != null;
    }

    /**
     * checks and corrects the web start configuration
     * @param windowsRunTreeKey the key of the windows registry run tree
     * @param linuxIconSource the source of the linux icon
     * @param linuxIconFileName the file name for the linux icon
     * @param linuxDesktopFileTemplate the template for the linux autostart
     * desktop file
     * @param linuxDesktopFileName the name of the linux autostart desktop file
     */
    public void checkWebStartConfig(String windowsRunTreeKey,
            String linuxIconSource, String linuxIconFileName,
            String linuxDesktopFileTemplate, String linuxDesktopFileName) {

        String lcCodebase = codeBase.toString().toLowerCase();

        switch (CurrentOperatingSystem.OS) {
            case Linux:
                // check Linux settings
                BufferedReader reader = null;
                try {
                    File linuxDesktopFile = new File(FREE_DESKTOP_AUTOSTART_DIR,
                            linuxDesktopFileName + ".desktop");
                    reader = new BufferedReader(
                            new FileReader(linuxDesktopFile));
                    for (String line = reader.readLine(); line != null;
                            line = reader.readLine()) {
                        if (line.startsWith("Exec=")) {
                            // <javaws> <code base><application>.jnlp
                            String[] tokens = line.split(" ");
                            if (tokens.length != 2
                                    || !tokens[0].equals("javaws")
                                    || !tokens[1].toLowerCase().startsWith(
                                    lcCodebase)) {
                                // we have to (silently) correct the
                                // javaws entry
                                enableLinuxAutostart(linuxIconSource,
                                        linuxIconFileName,
                                        linuxDesktopFileTemplate,
                                        linuxDesktopFileName);
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                } finally {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, null, ex);
                    }
                }
                break;
            case Windows:
                WinRegistry winRegistry = new WinRegistry();
                String autoStartPath = winRegistry.getValue(
                        WINDOWS_RUN_TREE, "\"" + windowsRunTreeKey + "\"");
                // "javaws.exe <codeBase><application>.jnlp"
                String[] tokens = autoStartPath.split(" ");
                if (tokens.length != 2
                        || !tokens[1].toLowerCase().startsWith(lcCodebase)) {
                    // we have to (silently) correct the javaws entry
                    enableWindowsAutoStart(windowsRunTreeKey);
                }
                break;
            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported!", CurrentOperatingSystem.OS);
        }
    }

    /**
     * returns the path to the JAR that is currently autostarted
     * @param linuxDesktopFileName the name of the Linux autostart desktop file
     * @param windowsRunTreeKey the key of the windows registry run tree
     * @return the path to the JAR that is currently autostarted
     */
    public static String getCurrentAutoStartPath(
            String linuxDesktopFileName, String windowsRunTreeKey) {

        String autoStartPath = null;

        switch (CurrentOperatingSystem.OS) {
            case Linux:
                // check Linux settings
                BufferedReader reader = null;
                try {
                    File linuxDesktopFile = new File(FREE_DESKTOP_AUTOSTART_DIR,
                            linuxDesktopFileName + ".desktop");
                    reader = new BufferedReader(
                            new FileReader(linuxDesktopFile));
                    for (String line = reader.readLine(); line != null;
                            line = reader.readLine()) {
                        if (line.startsWith("Exec=")) {
                            // <java> -jar <autoStartPath>
                            int jarIndex = line.indexOf(" -jar ");
                            if (jarIndex != -1) {
                                autoStartPath = line.substring(jarIndex + 6);
                                break;
                            }
                        }
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                } finally {
                    try {
                        if (reader != null) {
                            reader.close();
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.WARNING, null, ex);
                    }
                }
                break;
            case Windows:
                WinRegistry winRegistry = new WinRegistry();
                autoStartPath = winRegistry.getValue(
                        WINDOWS_RUN_TREE, "\"" + windowsRunTreeKey + "\"");
                if (autoStartPath != null) {
                    // remove escapes
                    autoStartPath =
                            autoStartPath.replaceAll("\\\\\\\\", "\\\\");
                }
                break;
            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported!", CurrentOperatingSystem.OS);
        }

        return autoStartPath;
    }

    /**
     * switches autostart on or off
     * @param windowsRunTreeKey the key of the windows registry run tree
     * @param linuxIconSource the path to the application icon
     * (as internal ressource path)
     * @param linuxIconFileName the file name of the application icon for Linux
     * @param linuxDesktopFileTemplate a template for an autostart desktop file
     * @param linuxDesktopFileName the name of the Linux autostart desktop file
     * @return the error message, or <code>null</code> if there was no error
     * message
     */
    public String enableAutoStart(String windowsRunTreeKey,
            String linuxIconSource, String linuxIconFileName,
            String linuxDesktopFileTemplate, String linuxDesktopFileName) {

        if (isIDE_Start) {
            // this is a start from within an IDE (e.g. NetBeans)
            LOGGER.warning("this seems to be a start from within an IDE, "
                    + "autostart enabling skipped");
            return null;
        }

        String errorMessage = null;
        String unsupported = " is not supported!";
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                errorMessage = enableLinuxAutostart(linuxIconSource,
                        linuxIconFileName, linuxDesktopFileTemplate,
                        linuxDesktopFileName);
                break;

            case Windows:
                errorMessage = enableWindowsAutoStart(windowsRunTreeKey);
                break;

            case Mac_OS_X:
                enableMacOSXAutostart();
                break;

            default:
                errorMessage = CurrentOperatingSystem.OS + unsupported;
        }
        return errorMessage;
    }

    /**
     * switches autostart on or off
     * @param windowsRunTreeKey the key of the windows registry run tree
     * (as internal ressource path)
     * @param linuxIconFileName the file name of the Linux icon
     * @param linuxDesktopFileName the name of the Linux autostart desktop file
     * @return the error message, or <code>null</code> if there was no error
     * message
     */
    public String disableAutoStart(String windowsRunTreeKey,
            String linuxIconFileName, String linuxDesktopFileName) {

        String errorMessage = null;
        String unsupported = " is not supported!";
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                disableLinuxAutostart(linuxDesktopFileName, linuxIconFileName);
                break;
            case Mac_OS_X:
                disableMACOSXAutostart();
                break;
            case Windows:
                errorMessage = disableWindowsAutoStart(windowsRunTreeKey);
                break;
            default:
                errorMessage = CurrentOperatingSystem.OS + unsupported;
        }
        return errorMessage;
    }

    private String getPathToJAR() {
        String userDir = System.getProperty("user.dir");
        LOGGER.log(Level.FINEST, "userDir = {0}", userDir);
        String javaClassPath = System.getProperty("java.class.path");
        LOGGER.log(Level.FINEST, "javaClassPath = {0}", javaClassPath);

        if (CurrentOperatingSystem.OS == OperatingSystem.Windows) {
            // Windows drive letters suck!
            // We did run into the following values:
            // userDir = C:\WINDOWS, javaClassPath = D:\application.jar
            // in this case we must ignore the userDir
            if (javaClassPath.charAt(1) == ':') {
                return javaClassPath;
            }
        }

        // Command line starts:
        //      userDir   = /home/user/Programs
        //      pathToJAR = application.jar
        // Symlinks to other file systems:
        //      userDir   = /home/user
        //      pathToJAR = /shared/software/application.jar
        // Desktop starts:
        //      userDir   = /home/user
        //      pathToJAR = /home/user/Desktop/application.jar

        if (javaClassPath.startsWith(File.separator)) {
            return javaClassPath;
        } else {
            return userDir + File.separator + javaClassPath;
        }
    }

    private String enableWindowsAutoStart(String windowsRunTreeKey) {
        String errorMessage = null;
        try {
            WinRegistry winRegistry = new WinRegistry();
            errorMessage = winRegistry.setValue(WINDOWS_RUN_TREE,
                    windowsRunTreeKey, startCommand, false/*quote*/);
        } catch (IOException e) {
            errorMessage = e.getMessage();
        }
        return errorMessage;
    }

    private String disableWindowsAutoStart(String windowsRunTreeKey) {
        String errorMessage = null;
        try {
            WinRegistry winRegistry = new WinRegistry();
            errorMessage = winRegistry.removeValue(
                    WINDOWS_RUN_TREE, windowsRunTreeKey);
        } catch (IOException e) {
            errorMessage = e.getMessage();
        }
        return errorMessage;
    }

    private void enableMacOSXAutostart() {
        // create start script
        if (!osxScriptDir.exists() && !osxScriptDir.mkdirs()) {
            LOGGER.log(Level.WARNING, "could not create {0}", osxScriptDir);
            return;
        }
        File scriptFile = new File(osxScriptDir, "autostart.sh");
        FileWriter fileWriter = null;
        try {
            fileWriter = new FileWriter(scriptFile);
            fileWriter.write("#!/bin/sh" + LINE_SEPARATOR
                    + startCommand);
            fileWriter.close();
            scriptFile.setExecutable(true);

            // add script to lauchagent
            if (!OSX_LAUNCH_AGENTS_DIR.exists()
                    && !OSX_LAUNCH_AGENTS_DIR.mkdirs()) {
                LOGGER.log(Level.WARNING,
                        "could not create {0}", OSX_LAUNCH_AGENTS_DIR);
                return;
            }
            fileWriter = new FileWriter(osxLaunchAgentsFile);
            fileWriter.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + LINE_SEPARATOR
                    + "<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\" \"http://www.apple.com/DTDs/PropertyList-1.0.dtd\">" + LINE_SEPARATOR
                    + "<plist version=\"1.0\">" + LINE_SEPARATOR
                    + "<dict>" + LINE_SEPARATOR
                    + "\t<key>Label</key>" + LINE_SEPARATOR
                    + "\t<string>autostart</string>" + LINE_SEPARATOR
                    + "\t<key>ProgramArguments</key>" + LINE_SEPARATOR
                    + "\t<array>" + LINE_SEPARATOR
                    + "\t\t<string>" + scriptFile + "</string>" + LINE_SEPARATOR
                    + "\t</array>" + LINE_SEPARATOR
                    + "\t<key>RunAtLoad</key>" + LINE_SEPARATOR
                    + "\t<true/>" + LINE_SEPARATOR
                    + "</dict>" + LINE_SEPARATOR
                    + "</plist>");
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "could not enable autostart", ex);
        } finally {
            if (fileWriter != null) {
                try {
                    fileWriter.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private void disableMACOSXAutostart() {
        try {
            FileTools.recursiveDelete(osxScriptDir);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "could not delete " + osxScriptDir, ex);
        }
        deleteIfExists(osxLaunchAgentsFile);
    }

    private String enableLinuxAutostart(String iconSource,
            String iconDestination, String desktopFileTemplate,
            String desktopFileName) {

        InputStream iconInputStream = null;
        FileOutputStream iconOutputStream = null;
        try {
            // copy icon
            iconInputStream = AutoStarter.class.getResourceAsStream(iconSource);
            File iconFile = new File(iconDestination);
            if (!iconFile.exists()) {
                File parentFile = iconFile.getParentFile();
                if (!parentFile.exists() && !parentFile.mkdirs()) {
                    LOGGER.log(Level.WARNING,
                            "could not create directory {0}", parentFile);
                }
                if (!iconFile.createNewFile()) {
                    LOGGER.log(Level.WARNING, "could not create {0}", iconFile);
                }
            }
            iconOutputStream = new FileOutputStream(iconFile);
            byte[] buffer = new byte[1000];
            for (int bytesRead = iconInputStream.read(buffer); bytesRead != -1;
                    bytesRead = iconInputStream.read(buffer)) {
                iconOutputStream.write(buffer, 0, bytesRead);
            }
            LOGGER.log(Level.INFO, "icon copied to {0}", iconDestination);

            // create autostart desktop files
            String contents = MessageFormat.format(
                    desktopFileTemplate, iconDestination, startCommand);
            writeDesktopFile(FREE_DESKTOP_AUTOSTART_DIR,
                    desktopFileName, contents);
            String kdeFullSession = System.getenv("KDE_FULL_SESSION");
            if ((kdeFullSession != null) && kdeFullSession.equals("true")
                    && (System.getenv("KDE_SESSION_VERSION") == null)) {
                // this is a KDE 3 session
                // KDE 3 does not follow the freedesktop standard
                // and needs special treatment
                writeDesktopFile(KDE_AUTOSTART_DIR, desktopFileName, contents);
            }

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "could not enable Linux autostart", ex);
            return ex.getMessage();
        } finally {
            if (iconInputStream != null) {
                try {
                    iconInputStream.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if (iconOutputStream != null) {
                try {
                    iconOutputStream.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }

        return null;
    }

    private static void writeDesktopFile(File autoStartFolder,
            String fileName, String contents) throws IOException {
        if (!autoStartFolder.exists() && !autoStartFolder.mkdirs()) {
            LOGGER.log(Level.WARNING, "could not create ", autoStartFolder);
            return;
        }
        File desktopFile = new File(autoStartFolder, fileName + ".desktop");
        FileOutputStream fileOutputStream = null;
        try {
            fileOutputStream = new FileOutputStream(desktopFile);
            fileOutputStream.write(contents.getBytes());
        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
        }
        LOGGER.log(Level.INFO, "desktop file {0} created", desktopFile);
        if (!desktopFile.setExecutable(true)) {
            LOGGER.log(Level.WARNING,
                    "failed to make {0} executable", desktopFile);
        }
    }

    private static void disableLinuxAutostart(
            String desktopFileName, String iconFileName) {
        deleteIfExists(new File(FREE_DESKTOP_AUTOSTART_DIR,
                desktopFileName + ".desktop"));
        deleteIfExists(new File(KDE_AUTOSTART_DIR,
                desktopFileName + ".desktop"));
        deleteIfExists(new File(iconFileName));
    }

    private static void deleteIfExists(File file) {
        if (file.exists()) {
            if (file.delete()) {
                LOGGER.log(Level.INFO, "{0} deleted", file);
            } else {
                LOGGER.log(Level.WARNING, "could not delete {0}", file);
            }
        } else {
            LOGGER.log(Level.INFO, "{0} does not exist", file);
        }
    }
}
