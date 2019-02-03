package ch.fhnw.dlcopy.gui.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.logging.Logger;
import javax.swing.table.AbstractTableModel;

/**
 * An ActionListener that updates changing duration table cells. Currently this
 * is the last duration cell and the summary duration cell.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpdateChangingDurationsTableActionListener
        implements ActionListener {

    private static final Logger LOGGER = Logger.getLogger(
            UpdateChangingDurationsTableActionListener.class.getName());
    private final AbstractTableModel model;

    /**
     * Creates a new UpdateTableActionListener
     *
     * @param model the model to update
     */
    public UpdateChangingDurationsTableActionListener(
            AbstractTableModel model) {
        this.model = model;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        int rowCount = model.getRowCount();
        updateDurationCell(rowCount - 1);
        updateDurationCell(rowCount - 2);
    }

    private void updateDurationCell(int row) {
        model.fireTableCellUpdated(row, ResultsTableModel.DURATION_COLUMN);
    }
}
