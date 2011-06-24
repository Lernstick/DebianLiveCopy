/*
 * ProcessExecutor.java
 *
 * Created on 31. August 2003, 14:32
 */
package dlcopy;

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
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A class that provides an easy interface for executing processes
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ProcessExecutor {

    private final static Logger LOGGER =
            Logger.getLogger(ProcessExecutor.class.getName());
    private boolean storeOutput;
    private List<String> stdOut;
    private List<String> stdErr;
    private List<String> stdAll;
    private final PropertyChangeSupport propertyChangeSupport =
            new PropertyChangeSupport(this);

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
     * creates a script in a temporary directory
     * @param script the script contents
     * @return the path to the executable script
     * @throws IOException
     */
    public String createScript(String script) throws IOException {
        LOGGER.info("script:\n" + script);
        FileWriter fileWriter = null;
        try {
            File tmpFile = File.createTempFile("processExecutor", null);
            fileWriter = new FileWriter(tmpFile);
            fileWriter.write(script);
            String scriptPath = tmpFile.getPath();
            executeProcess("chmod", "+x", scriptPath);
            return scriptPath;
        } finally {
            if (fileWriter != null) {
                fileWriter.close();
            }
        }
    }

    /**
     * executes the given command without storing the program output
     * @param commandArray the command and parameters
     * @return the exit value of the command
     */
    public int executeProcess(String... commandArray) {
        return executeProcess(false, commandArray);
    }

    /**
     * executes the given command
     * @param storeOutput if <tt>true</tt>, the program output will be stored
     * in an internal list
     * @param commandArray the command and parameters
     * @return the exit value of the command
     */
    public int executeProcess(boolean storeOutput, String... commandArray) {
        this.storeOutput = storeOutput;
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
        try {
            Process process = processBuilder.start();
            StreamReader stdoutReader = new StreamReader(
                    process.getInputStream(), "OUTPUT", stdOut, stdAll);
            StreamReader stderrReader = new StreamReader(
                    process.getErrorStream(), "ERROR", stdErr, stdAll);
            stdoutReader.start();
            stderrReader.start();
            int exitValue = process.waitFor();
            // wait for readers to finish...
            stdoutReader.join();
            stderrReader.join();
            return exitValue;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * returns the program output as a single string (with linebreaks)
     * @return the program output
     */
    public String getOutput() {
        StringBuilder stringBuilder = new StringBuilder();
        for (String string : stdAll) {
            stringBuilder.append(string);
            stringBuilder.append('\n');
        }
        return stringBuilder.toString();
    }

    /**
     * returns the output
     * @return the output
     */
    public List<String> getStdOut() {
        return stdOut;
    }

    /**
     * returns the output
     * @return the output
     */
    public List<String> getStdErr() {
        return stdErr;
    }

    private class StreamReader extends Thread {

        private final InputStream inputStream;
        private final String type;
        private final List<String> output;
        private final List<String> all;

        public StreamReader(InputStream inputStream, String type,
                List<String> output, List<String> all) {
            super("ProcessExecutor.StreamReader");
            this.inputStream = inputStream;
            this.type = type;
            this.output = output;
            this.all = all;
        }

        @Override
        public void run() {
            try {
                InputStreamReader isReader = new InputStreamReader(inputStream);
                BufferedReader reader = new BufferedReader(isReader);
                String line;
                while ((line = reader.readLine()) != null) {
                    propertyChangeSupport.firePropertyChange(
                            new PropertyChangeEvent(this, "line", null, line));
                    String allLine = type + ">" + line;
                    if (storeOutput) {
                        output.add(line);
                        all.add(allLine);
                    }
                    LOGGER.info(allLine);
                }
                reader.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
