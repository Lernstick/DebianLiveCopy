package ch.fhnw.dlcopy;

import java.util.Comparator;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

/**
 * a TableRowSorter for the results table
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class ResultsTableRowSorter extends TableRowSorter {

    private final Comparator sizeComparator;

    /**
     * creates a new ResultsTableRowSorter
     *
     * @param tableModel the underlying TableModel to use
     */
    public ResultsTableRowSorter(TableModel tableModel) {
        super(tableModel);
        sizeComparator = new Comparator<Long>() {
            @Override
            public int compare(Long size1, Long size2) {
                if (size1 > size2) {
                    return 1;
                } else if (size1 < size2) {
                    return -1;
                }
                return 0;
            }
        };
        setComparator(ResultsTableModel.SIZE_COLUMN, sizeComparator);
    }
}
