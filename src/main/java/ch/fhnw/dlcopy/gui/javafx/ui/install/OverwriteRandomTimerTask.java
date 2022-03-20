package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.gui.swing.*;
import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.LernstickFileTools;
import javafx.event.EventHandler;
import java.text.MessageFormat;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.event.Event;
import javafx.scene.control.ProgressBar;

/**
 * Collets the progress of overwriting the data partition with random data and
 * updates the progress bar from time to time.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class OverwriteRandomTimerTask extends TimerTask {

    private static final Logger LOGGER = Logger.getLogger(OverwriteRandomTimerTask.class.getName());

    private final ProgressBar progressBar;
    private final long size;
    private long done;

    /**
     * creates a new OverwriteRandomTimerTask
     *
     * @param progressBar the progress bar to update
     * @param size the size of the data partition
     */
    public OverwriteRandomTimerTask(ProgressBar progressBar, long size) {
        this.progressBar = progressBar;
        this.size = size;
    }


    /**
     * sets the current overwrite progress
     *
     * @param done how much random data already has been written
     */
    public void setDone(long done) {
        this.done = done;
        progressBar.setVisible(false);
    }


    @Override
    public void run() {
        LOGGER.log(Level.INFO,"done: {0}, size: {1}",new Object[]{done, size});
        /*progressBar.te(MessageFormat.format(DLCopy.STRINGS.getString(
                "OverwritingDataPartitionWithRandomData"),
                LernstickFileTools.getDataVolumeString(done, 1),
                LernstickFileTools.getDataVolumeString(size, 1)));
*/
        progressBar.setProgress((int) ((100 * done) / size));
    }
}

