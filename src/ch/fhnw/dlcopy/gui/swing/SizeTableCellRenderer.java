package ch.fhnw.dlcopy.gui.swing;

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
        String sizeString = "";
        if (value != null) {
            long size = (long) value;
            sizeString = LernstickFileTools.getDataVolumeString(size, 1);
        }
        return super.getTableCellRendererComponent(table, sizeString,
                isSelected, hasFocus, row, column);
    }
}
