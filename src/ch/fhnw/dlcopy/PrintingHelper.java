package ch.fhnw.dlcopy;

import ch.fhnw.util.ProcessExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * A helper class for printing documents
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class PrintingHelper {

    private static final Logger LOGGER
            = Logger.getLogger(PrintingHelper.class.getName());

    /**
     * prints the document
     *
     * @param document the path to the document file
     * @param copies the number of copies to print
     * @param duplex if the document should be printed on both sides of the
     * paper
     */
    public static void print(Path document, int copies, boolean duplex) {
        String lowerCaseFileName
                = document.getFileName().toString().toLowerCase();
        if (lowerCaseFileName.endsWith("pdf")) {
            printWithLPR(document, copies, duplex);
        } else {
            printWithLibreOfficeWriter(document, copies, duplex);
        }
    }

    private static void printWithLibreOfficeWriter(
            Path document, int copies, boolean duplex) {
        /**
         * Unfortunately, command line printing via LibreOffice is very limited.
         * There are no options for the number of copies, collating or duplex
         * printing. Therefore we first convert the ODT to a PDF and print the
         * PDF with lpr instead.
         */
        try {
            Path tempDir = Files.createTempDirectory("printingHelper");
            ProcessExecutor executor = new ProcessExecutor();
            executor.executeProcess(true, true, "lowriter",
                    "--convert-to", "pdf",
                    "--outdir", tempDir.toString(),
                    document.toString());
            Stream<Path> list = Files.list(tempDir);
            printWithLPR(list.findFirst().get(), copies, duplex);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }

    private static void printWithLPR(
            Path document, int copies, boolean duplex) {
        ProcessExecutor executor = new ProcessExecutor();
        executor.executeProcess(true, true, "lpr",
                "-#", String.valueOf(copies),
                "-o", "collate=True",
                "-o sides=" + (duplex ? "two-sided-long-edge" : "one-sided"),
                document.toString());
    }
}
