package dlcopy;

import static org.junit.Assert.*;
import ch.fhnw.filecopier.CopyJob;
import ch.fhnw.filecopier.FileCopier;
import ch.fhnw.filecopier.Source;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * tests for dlcopy
 * @author ronny
 */
public class LoggingTest {

    /**
     * Test that logging information from FileCopier shows up in logs
     */
    @Test
    public void testLogging() throws Exception {

        // delete all old log files
        File tmpDir = new File("/tmp");
        File[] tmpFiles = tmpDir.listFiles();
        for (int i = 0; i < tmpFiles.length; i++) {
            File file = tmpFiles[i];
            if (file.getName().startsWith("DebianLiveCopy")) {
                file.delete();
            }
        }

        String[] arguments = new String[]{
            "--variant", "lernstick",
            "--boot", "/dev/sdb1",
            "--systemsize", "2500000000"
        };
        DLCopy dlCopy = new DLCopy(arguments);
        dlCopy.setVisible(true);
        CopyJob copyJob = new CopyJob(
                new Source[]{new Source("/etc/passwd")},
                new String[]{"/tmp"});
        FileCopier fileCopier = new FileCopier();
        fileCopier.copy(copyJob);

        Thread.sleep(1000);
        boolean logInfoFound = false;
        File logFile = new File("/tmp/DebianLiveCopy.0");
        List<String> logLines = readFile(logFile);
        for (String logLine : logLines) {
            if (logLine.contains("transferredBytes")) {
                logInfoFound = true;
            }
        }

        assertTrue("FileCopier logging not visible", logInfoFound);
    }


    private static List<String> readFile(File file) throws IOException {
        List<String> lines = new ArrayList<String>();
        BufferedReader reader =
                new BufferedReader(new FileReader(file));
        for (String line = reader.readLine(); line != null;
                line = reader.readLine()) {
            lines.add(line);
        }
        reader.close();
        return lines;
    }
}