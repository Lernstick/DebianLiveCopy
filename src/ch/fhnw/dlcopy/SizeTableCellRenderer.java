package ch.fhnw.dlcopy;

import ch.fhnw.util.LernstickFileTools;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * a table cell renderer for size values
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class SizeTableCellRenderer extends DefaultTableCellRenderer {

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {
        long size = (long) value;
        String sizeString = LernstickFileTools.getDataVolumeString(size, 1);
        return super.getTableCellRendererComponent(table, sizeString,
                isSelected, hasFocus, row, column);
    }
}
