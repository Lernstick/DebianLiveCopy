package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.util.StorageDevice;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * Updates the list of available Btrfs snapshots.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SnapshotsUpdateActionListener implements ActionListener {

    private final JList<String> snapshotsList;
    private final DefaultListModel<String> snapshotsListModel;

    /**
     * creates a new SnapshotsUpdateActionListener
     *
     * @param snapshotsList the list of available Btrfs snapshots
     * @param snapshotsListModel the listModel of available Btrfs snapshots
     */
    public SnapshotsUpdateActionListener(JList<String> snapshotsList,
            DefaultListModel<String> snapshotsListModel) {

        this.snapshotsList = snapshotsList;
        this.snapshotsListModel = snapshotsListModel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        List<String> selectedValues = snapshotsList.getSelectedValuesList();

        snapshotsListModel.clear();
        snapshotsListModel.addAll(Arrays.asList(
                new File("/snapshots/").list()));
        
        // try to restore the previous selection
        for (String selectedValue : selectedValues) {
            int index = snapshotsListModel.indexOf(selectedValue);
            if (index != -1) {
                snapshotsList.addSelectionInterval(index, index);
            }
        }
    }
}
