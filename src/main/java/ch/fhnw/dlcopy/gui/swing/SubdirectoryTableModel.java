package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.Subdirectory;
import ch.fhnw.util.PreferredSizesTableModel;
import java.awt.Dimension;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.JTable;

/**
 * The table model for a table of backup destination subdirectory elements
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class SubdirectoryTableModel extends PreferredSizesTableModel {

    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");

    private final List<Subdirectory> ENTRIES;

    /**
     * Creates a new SubdirectoryTableModel
     *
     * @param table the table for this model
     * @param entries the entries for this table model
     */
    public SubdirectoryTableModel(JTable table, List<Subdirectory> entries) {
        super(table, new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        // DONT! make a copy here
        // (we need the order of the entries outside of this model)
        ENTRIES = entries;
        initSizes();
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return STRINGS.getString("Selected");
            case 1:
                return STRINGS.getString("Element");
        }
        return null;
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch (column) {
            case 0:
                return Boolean.class;
            case 1:
                return String.class;
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return ENTRIES == null ? 0 : ENTRIES.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        switch (columnIndex) {
            case 0:
                return ENTRIES.get(rowIndex).isSelected();
            case 1:
                return ENTRIES.get(rowIndex).getDescription();
        }
        return null;
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
        if (column == 0) {
            boolean selected = (Boolean) value;
            if (selected) {
                ENTRIES.get(row).setSelected(true);
            } else {
                // don't allow unselecting *ALL* entries
                // otherwise we would have no subdirectories for backups
                boolean moreSelections = false;
                for (int i = 0; i < ENTRIES.size(); i++) {
                    if (i != row && ENTRIES.get(i).isSelected()) {
                        moreSelections = true;
                        break;
                    }
                }
                if (moreSelections) {
                    ENTRIES.get(row).setSelected(false);
                }
            }
        }
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return column != 1;
    }

    /**
     * moves the selected rows up
     *
     * @param selectedRows the selected rows
     */
    public void moveUp(int[] selectedRows) {
        for (int selectedRow : selectedRows) {
            // swap values with previous index
            Collections.swap(ENTRIES, selectedRow, selectedRow - 1);
        }
        fireTableDataChanged();
    }

    /**
     * moves the selected rows down
     *
     * @param selectedRows the selected rows
     */
    public void moveDown(int[] selectedRows) {
        // we have to start from the bottom in this case
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            // swap values with next index
            Collections.swap(ENTRIES, selectedRows[i], selectedRows[i] + 1);
        }
        fireTableDataChanged();
    }
}
