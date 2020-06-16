package ch.fhnw.dlcopy.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * An ActionListener that updates changing duration table cells. Currently this
 * is the last duration cell and the summary duration cell.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpdateChangingDurationsTableActionListener
        implements ActionListener {

    private final ResultsTableModel model;

    /**
     * Creates a new UpdateChangingDurationsTableActionListener
     *
     * @param model the model to update
     */
    public UpdateChangingDurationsTableActionListener(ResultsTableModel model) {
        this.model = model;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        int rowCount = model.getRowCount();
        updateDurationCell(rowCount - 1);
        if (rowCount > 1) {
            updateDurationCell(rowCount - 2);
        }
    }

    private void updateDurationCell(int row) {
        model.fireTableCellUpdated(row, ResultsTableModel.DURATION_COLUMN);
    }
}
