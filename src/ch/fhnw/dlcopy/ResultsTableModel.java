package ch.fhnw.dlcopy;

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
    }

    @Override
    public String getColumnName(int column) {
        switch (column) {
            case 0:
                return STRINGS.getString("Number");
            case 1:
                return STRINGS.getString("Storage_Device");
            case 2:
                return STRINGS.getString("Duration");
            case 3:
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
        return 4;
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
                return device.getVendor() + " " + device.getModel() + " "
                        + device.getSerial() + " (/dev/" + device.getDevice()
                        + ")";

            case 2:
                // duration
                return dateFormat.format(new Date(result.getDuration()));

            case 3:
                // status
                String errorMessage = result.getErrorMessage();
                if (errorMessage == null) {
                    return "<html><font color=\"green\">OK</font></html>";
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
        initSizes();
    }
}
