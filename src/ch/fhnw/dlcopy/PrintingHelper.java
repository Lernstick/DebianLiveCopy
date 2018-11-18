package ch.fhnw.dlcopy;

import ch.fhnw.util.ProcessExecutor;
import java.nio.file.Path;

/**
 * A helper class for printing documents
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class PrintingHelper {

    /**
     * prints the document
     * @param document the path to the document file
     * @param copies the number of copies to print
     */
    public static void print(Path document, int copies) {
        String lowerCaseFileName
                = document.getFileName().toString().toLowerCase();
        if (lowerCaseFileName.endsWith("pdf")) {
            printWithLPR(document, copies);
        } else {
            printWithLibreOfficeWriter(document, copies);
        }
    }

    private static void printWithLibreOfficeWriter(Path document, int copies) {
        for (int i = 0; i < copies; i++) {
            ProcessExecutor executor = new ProcessExecutor();
            executor.executeProcess(true, true,
                    "lowriter", "-p", document.toString());
        }
    }

    private static void printWithLPR(Path document, int copies) {
        ProcessExecutor executor = new ProcessExecutor();
        executor.executeProcess(true, true, "lpr", "-#", String.valueOf(copies),
                "-o", "Collate=True", document.toString());
    }
}
