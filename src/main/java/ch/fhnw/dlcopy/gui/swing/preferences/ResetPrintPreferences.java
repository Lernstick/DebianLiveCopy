package ch.fhnw.dlcopy.gui.swing.preferences;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.Resetter;
import java.io.File;
import javax.swing.JCheckBox;
import javax.swing.JRadioButton;
import javax.swing.JSpinner;
import javax.swing.JTextArea;

/**
 * The preferences of the reset print module.
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class ResetPrintPreferences extends DLCopySwingGUIPreferences {

    private final static String PRINT_DOCUMENTS = "printDocuments";
    private final static String DIRECTORIES = "printingDirectories";
    private final static String SCAN_DIRECTORIES_RECURSIVELY = "scanDirectoriesRecursively";
    private final static String ODT = "printODT";
    private final static String ODS = "printODS";
    private final static String ODP = "printODP";
    private final static String PDF = "printPDF";
    private final static String DOC = "printDOC";
    private final static String DOCX = "printDOCX";
    private final static String XLS = "printXLS";
    private final static String XLSX = "printXLSX";
    private final static String PPT = "printPPT";
    private final static String PPTX = "printPPTX";
    private final static String AUTO_PRINT_MODE = "autoPrintMode";
    private final static String COPIES = "printCopies";
    private final static String DUPLEX = "printDuplex";

    private final JCheckBox printDocumentsCheckBox;
    private final JTextArea directoriesTextArea;
    private final JCheckBox scanDirectoriesRecursivelyCheckBox;
    private final JCheckBox odtCheckBox;
    private final JCheckBox odsCheckBox;
    private final JCheckBox odpCheckBox;
    private final JCheckBox pdfCheckBox;
    private final JCheckBox docCheckBox;
    private final JCheckBox docxCheckBox;
    private final JCheckBox xlsCheckBox;
    private final JCheckBox xlsxCheckBox;
    private final JCheckBox pptCheckBox;
    private final JCheckBox pptxCheckBox;
    private final JRadioButton autoPrintAllDocumentsRadioButton;
    private final JRadioButton autoPrintSingleDocumentsRadioButton;
    private final JRadioButton autoPrintNoneRadioButton;
    private final JSpinner copiesSpinner;
    private final JCheckBox duplexCheckBox;

    public ResetPrintPreferences(JCheckBox printDocumentsCheckBox,
            JTextArea directoriesTextArea,
            JCheckBox scanDirectoriesRecursivelyCheckBox,
            JCheckBox odtCheckBox, JCheckBox odsCheckBox,
            JCheckBox odpCheckBox, JCheckBox pdfCheckBox,
            JCheckBox docCheckBox, JCheckBox docxCheckBox,
            JCheckBox xlsCheckBox, JCheckBox xlsxCheckBox,
            JCheckBox pptCheckBox, JCheckBox pptxCheckBox,
            JRadioButton autoPrintAllDocumentsRadioButton,
            JRadioButton autoPrintSingleDocumentsRadioButton,
            JRadioButton autoPrintNoneRadioButton,
            JSpinner copiesSpinner, JCheckBox duplexCheckBox) {

        this.printDocumentsCheckBox = printDocumentsCheckBox;
        this.directoriesTextArea = directoriesTextArea;
        this.scanDirectoriesRecursivelyCheckBox = scanDirectoriesRecursivelyCheckBox;
        this.odtCheckBox = odtCheckBox;
        this.odsCheckBox = odsCheckBox;
        this.odpCheckBox = odpCheckBox;
        this.pdfCheckBox = pdfCheckBox;
        this.docCheckBox = docCheckBox;
        this.docxCheckBox = docxCheckBox;
        this.xlsCheckBox = xlsCheckBox;
        this.xlsxCheckBox = xlsxCheckBox;
        this.pptCheckBox = pptCheckBox;
        this.pptxCheckBox = pptxCheckBox;
        this.autoPrintAllDocumentsRadioButton = autoPrintAllDocumentsRadioButton;
        this.autoPrintSingleDocumentsRadioButton = autoPrintSingleDocumentsRadioButton;
        this.autoPrintNoneRadioButton = autoPrintNoneRadioButton;
        this.copiesSpinner = copiesSpinner;
        this.duplexCheckBox = duplexCheckBox;
    }

    @Override
    public void load() {

        printDocumentsCheckBox.setSelected(
                preferences.getBoolean(PRINT_DOCUMENTS, false));

        directoriesTextArea.setText(preferences.get(DIRECTORIES,
                STRINGS.getString("Default_Backup_Directory")
                + File.separatorChar
                + STRINGS.getString("Default_Documents_Directory")));

        scanDirectoriesRecursivelyCheckBox.setSelected(
                preferences.getBoolean(SCAN_DIRECTORIES_RECURSIVELY, true));

        odtCheckBox.setSelected(preferences.getBoolean(ODT, false));
        odsCheckBox.setSelected(preferences.getBoolean(ODS, false));
        odpCheckBox.setSelected(preferences.getBoolean(ODP, false));
        pdfCheckBox.setSelected(preferences.getBoolean(PDF, false));
        docCheckBox.setSelected(preferences.getBoolean(DOC, false));
        docxCheckBox.setSelected(preferences.getBoolean(DOCX, false));
        xlsCheckBox.setSelected(preferences.getBoolean(XLS, false));
        xlsxCheckBox.setSelected(preferences.getBoolean(XLSX, false));
        pptCheckBox.setSelected(preferences.getBoolean(PPT, false));
        pptxCheckBox.setSelected(preferences.getBoolean(PPTX, false));

        Resetter.AutoPrintMode autoPrintMode = Resetter.AutoPrintMode.valueOf(
                preferences.get(AUTO_PRINT_MODE,
                        Resetter.AutoPrintMode.NONE.toString()));
        switch (autoPrintMode) {
            case ALL:
                autoPrintAllDocumentsRadioButton.setSelected(true);
                break;
            case SINGLE:
                autoPrintSingleDocumentsRadioButton.setSelected(true);
                break;
            case NONE:
                autoPrintNoneRadioButton.setSelected(true);
        }

        copiesSpinner.setValue(preferences.getInt(COPIES, 1));
        duplexCheckBox.setSelected(preferences.getBoolean(DUPLEX, false));
    }

    @Override
    public void save() {
        preferences.putBoolean(PRINT_DOCUMENTS,
                printDocumentsCheckBox.isSelected());

        preferences.put(DIRECTORIES, directoriesTextArea.getText());

        preferences.putBoolean(SCAN_DIRECTORIES_RECURSIVELY,
                scanDirectoriesRecursivelyCheckBox.isSelected());

        preferences.putBoolean(ODT, odtCheckBox.isSelected());
        preferences.putBoolean(ODS, odsCheckBox.isSelected());
        preferences.putBoolean(ODP, odpCheckBox.isSelected());
        preferences.putBoolean(PDF, pdfCheckBox.isSelected());
        preferences.putBoolean(DOC, docCheckBox.isSelected());
        preferences.putBoolean(DOCX, docxCheckBox.isSelected());
        preferences.putBoolean(XLS, xlsCheckBox.isSelected());
        preferences.putBoolean(XLSX, xlsxCheckBox.isSelected());
        preferences.putBoolean(PPT, pptCheckBox.isSelected());
        preferences.putBoolean(PPTX, pptxCheckBox.isSelected());

        preferences.put(AUTO_PRINT_MODE, getAutoPrintMode().toString());

        preferences.putInt(COPIES,
                ((Number) copiesSpinner.getValue()).intValue());

        preferences.putBoolean(DUPLEX, duplexCheckBox.isSelected());
    }

    public Resetter.AutoPrintMode getAutoPrintMode() {
        if (autoPrintAllDocumentsRadioButton.isSelected()) {
            return Resetter.AutoPrintMode.ALL;
        } else if (autoPrintSingleDocumentsRadioButton.isSelected()) {
            return Resetter.AutoPrintMode.SINGLE;
        } else {
            return Resetter.AutoPrintMode.NONE;
        }
    }
}
