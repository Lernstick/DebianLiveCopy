package ch.fhnw.dlcopy.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JList;

/**
 * Updates the list of available Btrfs snapshots.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SnapshotsUpdateActionListener implements ActionListener {

    private final File snapshotsDir;
    private final JList<String> snapshotsList;
    private final DefaultListModel<String> snapshotsListModel;

    /**
     * creates a new SnapshotsUpdateActionListener
     *
     * @param snapshotsDir the directory to scan for snapshots
     * @param snapshotsList the list of available Btrfs snapshots
     * @param snapshotsListModel the listModel of available Btrfs snapshots
     */
    public SnapshotsUpdateActionListener(File snapshotsDir,
            JList<String> snapshotsList,
            DefaultListModel<String> snapshotsListModel) {

        this.snapshotsDir = snapshotsDir;
        this.snapshotsList = snapshotsList;
        this.snapshotsListModel = snapshotsListModel;
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        if (snapshotsDir.exists()) {

            // remember selected values in the snapshots list
            List<String> selectedValues = snapshotsList.getSelectedValuesList();

            // update snapshots list
            snapshotsListModel.clear();
            snapshotsListModel.addAll(Arrays.asList(snapshotsDir.list()));

            // try to restore the previous selection in the snapshots list
            for (String selectedValue : selectedValues) {
                int index = snapshotsListModel.indexOf(selectedValue);
                if (index != -1) {
                    snapshotsList.addSelectionInterval(index, index);
                }
            }
        }
    }
}
