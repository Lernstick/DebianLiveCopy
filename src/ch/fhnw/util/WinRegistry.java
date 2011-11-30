/*
 * WinRegistry.java
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
 * Created on 25. Februar 2006, 18:30
 *
 */
package ch.fhnw.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class for some Windows registry manipulations
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class WinRegistry {

    private static final Logger LOGGER =
            Logger.getLogger(WinRegistry.class.getName());

    /**
     * returns a value from the registry
     * @param tree the tree path
     * @param key the key
     * @return the value of the given key from the registry
     */
    public String getValue(String tree, String key) {
        String value = null;
        BufferedReader reader = null;
        File tempFile = null;
        try {
            // execute regedit to export the registry entry into a temp file
            tempFile = File.createTempFile(getClass().getName(), null);
            String[] exportRegistryKeyCommand = {"regedit", "/e",
                "\"" + tempFile.getPath() + "\"", "\"" + tree + "\""};
            ProcessExecutor processExecutor = new ProcessExecutor();
            int exitValue = processExecutor.executeProcess(
                    exportRegistryKeyCommand);
            if (exitValue == 0) {
                // parse the exported file
                reader = new BufferedReader(new InputStreamReader(
                        new FileInputStream(tempFile), "UTF-16LE"));
                for (String line = reader.readLine(); line != null;
                        line = reader.readLine()) {
                    if (line.startsWith(key + "=")) {
                        // cut off head <key>=
                        value = line.substring(key.length() + 1);
                        if (value.startsWith("\"\\\"")) {
                            // escaped quotes
                            // cut off head ("\") and tail (\"")
                            value = value.substring(3, value.length() - 3);
                        } else if (value.startsWith("\"")) {
                            // normal quotes
                            // cut off head (") and tail (")
                            value = value.substring(1, value.length() - 1);
                        }
                        break;
                    }
                }
            } else {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                            "Could not execute \"regedit /e {0} {1}\"!",
                            new Object[]{tempFile, tree});
                }
            }

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, null, ex);

        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
            }
            if (tempFile != null) {
                if (!tempFile.delete()) {
                    LOGGER.log(Level.WARNING, "can not delete {0}", tempFile);
                }
            }
        }

        return value;
    }

    /**
     * sets a value in the winregistry tree
     * @param tree the tree path
     * @param key the key value
     * @param value the value
     * @param quote if we have to quote
     * @return the regedit error message or <tt>null</tt> if there was none
     * @throws IOException if an I/O exception occurs
     */
    public String setValue(String tree, String key, String value, boolean quote)
            throws IOException {

        if (quote) {
            value = "\"\\\"" + value + "\\\"\"";
        }

        String registryKey = "\r\n\r\n[" + tree + "]\r\n"
                + "\"" + key + "\"=" + value + "\r\n\r\n";
        LOGGER.log(Level.FINE, "registryKey: {0}", registryKey);

        // write regfile to temporary file
        File tempFile = null;
        FileOutputStream fileOutputStream = null;
        ProcessExecutor processExecutor = new ProcessExecutor();
        try {
            tempFile = File.createTempFile(getClass().getName(), null);
            fileOutputStream = new FileOutputStream(tempFile);
            String contents =
                    "\uFEFFWindows Registry Editor Version 5.00" + registryKey;
            fileOutputStream.write(contents.getBytes("UTF-16LE"));
            fileOutputStream.close();

            // call regedit on the tempfile to change the registry
            String[] addRegistryKeyCommand = {"cmd.exe", "/c",
                "regedit", "/s", "\"" + tempFile.getPath() + "\""};
            int returnValue = processExecutor.executeProcess(
                    true, true, addRegistryKeyCommand);
            if (returnValue == 0) {
                return null;
            } else {
                return processExecutor.getOutput();
            }

        } finally {
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            if (tempFile != null) {
                tempFile.delete();
            }
        }
    }

    /**
     * removes a key from the registry
     * @param tree the tree
     * @param key the key
     * @return the regedit error message or <tt>null</tt> if there was none
     * @throws IOException if an I/O exception occurs
     */
    public String removeValue(String tree, String key) throws IOException {
        return setValue(tree, key, "-", false);
    }
}
