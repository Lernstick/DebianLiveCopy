package ch.fhnw.dlcopy.gui.swing;

import java.awt.Component;
import java.io.File;
import javax.swing.DefaultCellEditor;
import javax.swing.JFileChooser;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

/**
 * A Cell Editor for selecting files
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class FileChooserCellEditor
        extends DefaultCellEditor implements TableCellEditor {

    private final Component parent;
    private final JFileChooser fileChooser;
    private final JTextField textfield = new JTextField();
    private String selectedFile = "";

    /**
     * creates a new FileChooserCellEditor
     *
     * @param parent the parent component
     * @param dialogTitle the filechooser dialog title
     * @param approveButtonText the filechooser approve button text
     * @param fileSelectionMode the file selection mode to use for the
     * fileChooser
     */
    public FileChooserCellEditor(Component parent, String dialogTitle,
            String approveButtonText, int fileSelectionMode) {

        super(new JTextField());

        this.parent = parent;

        fileChooser = new JFileChooser();
        fileChooser.setDialogTitle(dialogTitle);
        fileChooser.setApproveButtonText(approveButtonText);
        fileChooser.setFileSelectionMode(fileSelectionMode);
    }

    @Override
    public Object getCellEditorValue() {
        return selectedFile;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
            boolean isSelected, int row, int column) {

        if (value != null) {
            selectedFile = value.toString();
        }

        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                fileChooser.setSelectedFile(new File(selectedFile));

                if (fileChooser.showOpenDialog(parent)
                        == JFileChooser.APPROVE_OPTION) {
                    selectedFile
                            = fileChooser.getSelectedFile().getAbsolutePath();
                }

                fireEditingStopped();
            }
        });

        textfield.setText(selectedFile);
        return textfield;
    }
}
