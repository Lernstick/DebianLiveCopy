package ch.fhnw.dlcopy.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;

/**
 * Parses the output of the cp command and updates a text label with the name of
 * the source file and a text label with the elapsed time.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class CpActionListener implements ActionListener {

    private final JLabel fileNameLabel;
    private final JLabel elapsedTimeLabel;
    private final long start;
    private final Pattern cpPattern = Pattern.compile("'(.*)' -> .*");
    private final int pathIndex;
    private final DateFormat timeFormat = new SimpleDateFormat("HH:mm:ss");
    private String currentLine = "";

    /**
     * creates a new CpActionListener
     * @param fileNameLabel the label for the file name
     * @param elapsedTimeLabel the label for the elapsed time
     * @param sourceMountPoint the mountpoint of the source files
     */
    public CpActionListener(JLabel fileNameLabel, JLabel elapsedTimeLabel,
            String sourceMountPoint) {
        this.fileNameLabel = fileNameLabel;
        this.elapsedTimeLabel = elapsedTimeLabel;
        pathIndex = sourceMountPoint.length();
        start = System.currentTimeMillis();
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // update file name
        Matcher matcher = cpPattern.matcher(currentLine);
        if (matcher.matches()) {
            fileNameLabel.setText(matcher.group(1).substring(pathIndex));
        }

        // update time
        long time = System.currentTimeMillis() - start;
        String timeString = timeFormat.format(new Date(time));
        elapsedTimeLabel.setText(timeString);
    }

    /**
     * sets the current cp output line
     * @param currentLine the current cp output line
     */
    public void setCurrentLine(String currentLine) {
        this.currentLine = currentLine;
    }
}
