package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.LernstickFileTools;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.MessageFormat;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JProgressBar;

/**
 * Collets the progress of overwriting the data partition with random data and
 * updates the progress bar from time to time.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class OverwriteRandomActionListener implements ActionListener {

    private static final Logger LOGGER = Logger.getLogger(
            OverwriteRandomActionListener.class.getName());

    private final JProgressBar progressBar;
    private final long size;
    private long done;

    /**
     * creates a new OverwriteRandomActionListener
     *
     * @param progressBar the progress bar to update
     * @param size the size of the data partition
     */
    public OverwriteRandomActionListener(JProgressBar progressBar, long size) {
        this.progressBar = progressBar;
        this.size = size;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        LOGGER.log(Level.INFO, "done: {0}, size: {1}",
                new Object[]{done, size});
        progressBar.setString(MessageFormat.format(DLCopy.STRINGS.getString(
                "OverwritingDataPartitionWithRandomData"),
                LernstickFileTools.getDataVolumeString(done, 1),
                LernstickFileTools.getDataVolumeString(size, 1)));
        progressBar.setValue((int) ((100 * done) / size));
    }

    /**
     * sets the current overwrite progress
     *
     * @param done how much random data already has been written
     */
    public void setDone(long done) {
        this.done = done;
    }
}
