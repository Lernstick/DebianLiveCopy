package ch.fhnw.dlcopy.gui.swing;

import java.util.Comparator;
import javax.swing.table.TableRowSorter;

/**
 * a TableRowSorter for the results table
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class ResultsTableRowSorter extends TableRowSorter<ResultsTableModel> {

    private final Comparator sizeComparator;

    /**
     * creates a new ResultsTableRowSorter
     *
     * @param resultsTableModel the underlying ResultsTableModel to use
     */
    public ResultsTableRowSorter(ResultsTableModel resultsTableModel) {
        super(resultsTableModel);
        sizeComparator = (Comparator<Long>) (Long size1, Long size2) -> {
            if (size1 > size2) {
                return 1;
            } else if (size1 < size2) {
                return -1;
            }
            return 0;
        };
        setComparator(ResultsTableModel.SIZE_COLUMN, sizeComparator);
    }
}
