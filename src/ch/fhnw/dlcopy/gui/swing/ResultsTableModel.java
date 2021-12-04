package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.StorageDeviceResult;
import ch.fhnw.util.PreferredSizesTableModel;
import ch.fhnw.util.StorageDevice;
import java.awt.Dimension;
import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ResourceBundle;
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

    /**
     * the column for the duration of the operation
     */
    public static final int DURATION_COLUMN = 8;
    private static final ResourceBundle STRINGS
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");
    private List<StorageDeviceResult> resultList;
    private final DateTimeFormatter dateTimeFormatter
            = DateTimeFormatter.ofPattern("HH:mm:ss");

    /**
     * creates a new ResultsTableModel
     *
     * @param table the table for this model
     */
    public ResultsTableModel(JTable table) {
        super(table, new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
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
                return STRINGS.getString("Start");
            case 7:
                return STRINGS.getString("Finish");
            case DURATION_COLUMN:
                return STRINGS.getString("Duration");
            case 9:
                return STRINGS.getString("Status");
        }
        return null;
    }

    @Override
    public int getRowCount() {
        if (resultList == null) {
            return 0;
        }
        int rows = resultList.size();
        // show summary row only if there is more than one result
        return (rows == 1) ? 1 : rows + 1;
    }

    @Override
    public int getColumnCount() {
        return 10;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {

        if (resultList.size() == rowIndex) {
            // summary row
            switch (columnIndex) {
                case 0:
                    return STRINGS.getString("Summary");

                case 6:
                    // start
                    return (resultList.isEmpty())
                            ? ""
                            : getValueAt(0, columnIndex);

                case 7:
                    // finish
                    return (resultList.isEmpty())
                            ? ""
                            : getValueAt(rowIndex - 1, columnIndex);

                case DURATION_COLUMN:
                    if (resultList.isEmpty()) {
                        return "";
                    }
                    StorageDeviceResult lastResult
                            = resultList.get(resultList.size() - 1);
                    LocalTime finishTime = lastResult.getFinishTime();
                    if (finishTime == null) {
                        // calculate temporary duration
                        finishTime = LocalTime.now();
                    }
                    Duration duration = Duration.between(
                            resultList.get(0).getStartTime(), finishTime);
                    return LocalTime.MIDNIGHT.plus(duration).format(
                            dateTimeFormatter);
            }

        } else {
            // standard rows
            StorageDeviceResult result = resultList.get(rowIndex);

            switch (columnIndex) {
                case 0:
                    // result number
                    return rowIndex + 1;

                case 1:
                    // device
                    StorageDevice device = result.getStorageDevice();
                    return device.getFullDevice();

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
                    // start
                    return result.getStartTime().format(dateTimeFormatter);

                case 7:
                    // finish
                    LocalTime finishTime = result.getFinishTime();
                    return finishTime == null
                            ? ""
                            : finishTime.format(dateTimeFormatter);

                case DURATION_COLUMN:
                    // duration
                    Duration duration = result.getDuration();
                    if (duration == null) {
                        // calculate temporary duration
                        duration = Duration.between(
                                result.getStartTime(), LocalTime.now());
                    }
                    return LocalTime.MIDNIGHT.plus(duration).format(
                            dateTimeFormatter);

                case 9:
                    // status
                    String errorMessage = result.getErrorMessage();
                    if (errorMessage == null) {
                        if (result.getDuration() == null) {
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
