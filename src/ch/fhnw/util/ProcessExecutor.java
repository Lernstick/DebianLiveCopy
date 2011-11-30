/*
 * ProcessExecutor.java
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
 * Created on 31. August 2003, 14:32
 */
package ch.fhnw.util;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that provides an easy interface for executing processes
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ProcessExecutor {

    /**
     * the property for output line changes
     */
    public final static String LINE = "line";
    private final static Logger LOGGER =
            Logger.getLogger(ProcessExecutor.class.getName());
    private static final String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private List<String> stdOut;
    private List<String> stdErr;
    private List<String> stdAll;
    private final PropertyChangeSupport propertyChangeSupport =
            new PropertyChangeSupport(this);
    private Process process;
    private Map<String, String> environment;

    /**
     * adds a PropertyChangeListener
     * @param listener the PropertyChangeListener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {
        propertyChangeSupport.addPropertyChangeListener(listener);
    }

    /**
     * removes a PropertyChangeListener
     * @param listener the PropertyChangeListener to remove
     */
    public void removePropertyChangeListener(
            PropertyChangeListener listener) {
        propertyChangeSupport.removePropertyChangeListener(listener);
    }

    /**
     * executes a script without storing the script execution output
     * @param script the script contents
     * @param parameters  the script parameters
     * @return the return value of the script execution
     * @throws IOException if the script could not be written to a temp file
     */
    public int executeScript(String script, String... parameters)
            throws IOException {
        return executeScript(false, false, script, parameters);
    }

    /**
     * executes a script
     * @param storeStdOut if <tt>true</tt>, the program stdout will be stored
     * in an internal list
     * @param storeStdErr if <tt>true</tt>, the program stderr will be stored
     * in an internal list
     * @param script the script contents
     * @param parameters the script parameters
     * @return the return value of the script execution
     * @throws IOException if the script could not be written to a temp file
     */
    public int executeScript(boolean storeStdOut, boolean storeStdErr,
            String script, String... parameters) throws IOException {
        LOGGER.log(Level.INFO, "script:\n{0}", script);
        File scriptFile = null;
        try {
            scriptFile = createScript(script);
            String scriptPath = scriptFile.getPath();
            int parametersCount = parameters.length;
            String[] commandArray = new String[1 + parametersCount];
            commandArray[0] = scriptPath;
            System.arraycopy(parameters, 0, commandArray, 1, parametersCount);
            return executeProcess(storeStdOut, storeStdErr, commandArray);
        } finally {
            if ((scriptFile != null) && !scriptFile.delete()) {
                LOGGER.log(Level.WARNING, "could not delete {0}", scriptFile);
            }
        }
    }

    /**
     * creates a script
     * @param script the script contents
     * @return the script file
     * @throws IOException if an I/O exception occurs
     */
    public File createScript(String script) throws IOException {
        File scriptFile = null;
        FileWriter fileWriter = null;
        try {
            scriptFile = File.createTempFile("processExecutor", null);
            fileWriter = new FileWriter(scriptFile);
            fileWriter.write(script);
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
        scriptFile.setExecutable(true);
        return scriptFile;
    }

    /**
     * executes the given command without storing the program output
     * @param commandArray the command and parameters
     * @return the exit value of the command
     */
    public int executeProcess(String... commandArray) {
        return executeProcess(false, false, commandArray);
    }

    /**
     * sets the environment for process execution
     * @param environment the environment for process execution
     */
    public void setEnvironment(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * executes the given command
     * @param storeStdOut if <tt>true</tt>, the program stdout will be stored
     * in an internal list
     * @param storeStdErr if <tt>true</tt>, the program stderr will be stored
     * in an internal list
     * @param commandArray the command and parameters
     * @return the exit value of the command
     */
    public int executeProcess(boolean storeStdOut, boolean storeStdErr,
            String... commandArray) {
        if (LOGGER.isLoggable(Level.FINE)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("executing \"");
            for (int i = 0; i < commandArray.length; i++) {
                stringBuilder.append(commandArray[i]);
                if (i != commandArray.length - 1) {
                    stringBuilder.append(" ");
                }
            }
            stringBuilder.append("\"");
            LOGGER.fine(stringBuilder.toString());
        }
        stdOut = new ArrayList<String>();
        stdErr = new ArrayList<String>();
        stdAll = new ArrayList<String>();
        ProcessBuilder processBuilder = new ProcessBuilder(commandArray);
        if (environment != null) {
            processBuilder.environment().putAll(environment);
        }
        try {
            process = processBuilder.start();
            StreamReader stdoutReader = new StreamReader(
                    process.getInputStream(),
                    "OUTPUT", stdOut, stdAll, storeStdOut);
            StreamReader stderrReader = new StreamReader(
                    process.getErrorStream(),
                    "ERROR", stdErr, stdAll, storeStdErr);
            stdoutReader.start();
            stderrReader.start();
            int exitValue = process.waitFor();
            LOGGER.log(Level.FINE, "exitValue = {0}", exitValue);
            // wait for readers to finish...
            if (storeStdOut) {
                stdoutReader.join();
            }
            if (storeStdErr) {
                stderrReader.join();
            }
            return exitValue;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, null, e);
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, null, e);
        }
        return -1;
    }

    /**
     * returns the program output as a single string (with linebreaks)
     * @return the program output
     */
    public String getOutput() {
        return listToString(stdAll);
    }

    /**
     * returns the standard output
     * @return the standard output
     */
    public String getStdOut() {
        return listToString(stdOut);
    }

    /**
     * returns the standard output list
     * @return the standard output list
     */
    public List<String> getStdOutList() {
        return stdOut;
    }

    /**
     * returns the standard error
     * @return the standard error
     */
    public String getStdErr() {
        return listToString(stdErr);
    }

    /**
     * returns the standard error list
     * @return the standard error list
     */
    public List<String> getStdErrList() {
        return stdErr;
    }

    /**
     * kills any running subprocess
     */
    public void destroy() {
        if (process != null) {
            process.destroy();
        }
    }

    private static String listToString(List<String> lines) {
        if (lines == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder();
        for (String line : lines) {
            stringBuilder.append(line);
            stringBuilder.append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    private class StreamReader extends Thread {

        private final InputStream inputStream;
        private final String type;
        private final List<String> output;
        private final List<String> all;
        private final boolean storeOutput;

        public StreamReader(InputStream inputStream, String type,
                List<String> output, List<String> all, boolean storeOutput) {
            super("ProcessExecutor.StreamReader");
            this.inputStream = inputStream;
            this.type = type;
            this.output = output;
            this.all = all;
            this.storeOutput = storeOutput;
        }

        @Override
        public void run() {
            BufferedReader reader = null;
            try {
                reader = new BufferedReader(new InputStreamReader(inputStream));
                for (String line; (line = reader.readLine()) != null;) {
                    propertyChangeSupport.firePropertyChange(
                            new PropertyChangeEvent(this, LINE, null, line));
                    if (storeOutput || LOGGER.isLoggable(Level.FINE)) {
                        String allLine = type + ">" + line;
                        if (storeOutput) {
                            output.add(line);
                            all.add(allLine);
                        }
                        LOGGER.fine(allLine);
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, null, e);
            } finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }
        }
    }
}
