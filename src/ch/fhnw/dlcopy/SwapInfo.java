package ch.fhnw.dlcopy;

import ch.fhnw.util.LernstickFileTools;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Holds some info about swap memory
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SwapInfo {

    private static final Logger LOGGER
            = Logger.getLogger(SwapInfo.class.getName());

    private String file;
    private final long remainingFreeMemory;

    /**
     * creates a new SwapInfo
     * @param swapLine an output line of "cat /proc/swaps"
     * @throws IOException if parsing the swaps line fails
     */
    public SwapInfo(String swapLine) throws IOException {
        long swapSize = 0;
        // the swaps line has the following syntax
        // <filename> <type> <size> <used> <priority>
        // e.g.:
        // /media/live-rw/live.swp file 1048568 0 -1
        // (separation with spaces and TABs is slightly caotic, therefore we
        // use regular expressions to parse the line)
        Pattern pattern = Pattern.compile("(\\p{Graph}+)\\p{Blank}+"
                + "\\p{Graph}+\\p{Blank}+(\\p{Graph}+).*");
        Matcher matcher = pattern.matcher(swapLine);
        if (matcher.matches()) {
            file = matcher.group(1);
            swapSize = Long.valueOf(matcher.group(2)) * 1024;
        } else {
            String warningMessage
                    = "Could not parse swaps line:\n" + swapLine;
            LOGGER.warning(warningMessage);
            throw new IOException(warningMessage);
        }

        long memFree = 0;
        pattern = Pattern.compile("\\p{Graph}+\\p{Blank}+(\\p{Graph}+).*");
        List<String> meminfo = LernstickFileTools.readFile(
                new File("/proc/meminfo"));
        for (String meminfoLine : meminfo) {
            if (meminfoLine.startsWith("MemFree:")
                    || meminfoLine.startsWith("Buffers:")
                    || meminfoLine.startsWith("Cached:")
                    || meminfoLine.startsWith("SwapFree:")) {
                matcher = pattern.matcher(meminfoLine);
                if (matcher.matches()) {
                    memFree += Long.valueOf(matcher.group(1)) * 1024;
                } else {
                    String warningMessage = "Could not parse meminfo line:\n"
                            + meminfoLine;
                    LOGGER.warning(warningMessage);
                    throw new IOException(warningMessage);
                }
            }
        }
        remainingFreeMemory = memFree - swapSize;
    }

    /**
     * returns the swap file/partition
     *
     * @return the swap file/partition
     */
    public String getFile() {
        return file;
    }

    /**
     * returns the remaining free memory when this swap file/partition would be
     * switched off
     *
     * @return the remaining free memory when this swap file/partition would be
     * switched off
     */
    public long getRemainingFreeMemory() {
        return remainingFreeMemory;
    }

}
