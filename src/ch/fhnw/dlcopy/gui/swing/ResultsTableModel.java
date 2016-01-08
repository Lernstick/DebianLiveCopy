package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.util.PreferredSizesTableModel;
import ch.fhnw.util.StorageDevice;
import java.awt.Dimension;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TimeZone;
import javax.swing.JTable;

/**
 * The table model for a table of operation results
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class ResultsTableModel extends PreferredSizesTableModel {

    /**
     * the column for the storage device size
     */
    public static final int SIZE_COLUMN = 5;
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");
    private List<StorageDeviceResult> resultList;
    private final DateFormat dateFormat;

    /**
     * creates a new ResultsTableModel
     *
     * @param table the table for this model
     */
    public ResultsTableModel(JTable table) {
        super(table, new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        dateFormat = DateFormat.getTimeInstance(DateFormat.MEDIUM);
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        initSizes();
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return STRINGS.getString("Number");
            case 1:
                return STRINGS.getString("Device");
            case 2:
                return STRINGS.getString("Vendor");
            case 3:
                return STRINGS.getString("Model");
            case 4:
                return STRINGS.getString("Serial_Number");
            case SIZE_COLUMN:
                return STRINGS.getString("Size");
            case 6:
                return STRINGS.getString("Duration");
            case 7:
                return STRINGS.getString("Status");
        }
        return null;
    }

    @Override
    public int getRowCount() {
        if (resultList == null) {
            return 0;
        }
        return resultList.size();
    }

    @Override
    public int getColumnCount() {
        return 8;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

        StorageDeviceResult result = resultList.get(rowIndex);

        switch (columnIndex) {
            case 0:
                // result number
                return rowIndex + 1;

            case 1:
                // device
                StorageDevice device = result.getStorageDevice();
                return "/dev/" + device.getDevice();

            case 2:
                // vendor
                device = result.getStorageDevice();
                return device.getVendor();

            case 3:
                // model
                device = result.getStorageDevice();
                return device.getModel();

            case 4:
                // serial number
                device = result.getStorageDevice();
                return device.getSerial();

            case SIZE_COLUMN:
                // size
                device = result.getStorageDevice();
                return device.getSize();

            case 6:
                // duration
                long duration = result.getDuration();
                if (duration == -1) {
                    // in progress
                    return "";
                }
                return dateFormat.format(new Date(duration));

            case 7:
                // status
                String errorMessage = result.getErrorMessage();
                if (errorMessage == null) {
                    if (result.getDuration() == -1) {
                        return "<html><font color=\"green\">"
                                + STRINGS.getString("In_Progress")
                                + "</font></html>";
                    } else {
                        return "<html><font color=\"green\">"
                                + STRINGS.getString("OK")
                                + "</font></html>";
                    }
                } else {
                    return "<html><font color=\"red\">"
                            + errorMessage + "</font></html>";
                }
        }

        return null;
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    /**
     * sets the list of results to display
     *
     * @param resultList the list of results to display
     */
    public void setList(List<StorageDeviceResult> resultList) {
        this.resultList = resultList;
        fireTableDataChanged();
        updateTableColumnWidths();
    }
}
