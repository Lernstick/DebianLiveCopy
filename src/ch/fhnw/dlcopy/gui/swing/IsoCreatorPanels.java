package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.dlcopy.DataPartitionMode;
import ch.fhnw.dlcopy.SystemSource;
import ch.fhnw.util.LernstickFileTools;
import java.awt.Color;
import java.io.File;
import java.text.MessageFormat;
import java.util.logging.Logger;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JFileChooser;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The panels needed for the IsoCreator
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class IsoCreatorPanels
        extends javax.swing.JPanel
        implements DocumentListener {

    private static final Logger LOGGER
            = Logger.getLogger(IsoCreatorPanels.class.getName());

    private DLCopySwingGUI dlCopySwingGUI;

    /**
     * Creates new form IsoCreatorPanels
     */
    public IsoCreatorPanels() {
        initComponents();
        isoLabelTextField.setText("lernstick");
    }

    public void init(DLCopySwingGUI dlCopySwingGUI) {
        this.dlCopySwingGUI = dlCopySwingGUI;
        tmpDirTextField.getDocument().addDocumentListener(this);
        dataPartitionModeComboBox.setModel(
                new DefaultComboBoxModel<>(DLCopy.DATA_PARTITION_MODES));
    }

    @Override
    public void insertUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public void removeUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    @Override
    public void changedUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    public void showInfoPanel() {
        DLCopySwingGUI.showCard(this, "infoPanel");
    }

    public void showSelectionPanel() {
        DLCopySwingGUI.showCard(this, "selectionPanel");
    }

    public void showProgressPanel() {
        DLCopySwingGUI.showCard(this, "progressPanel");
    }

    public void setSystemSource(SystemSource systemSource) {
        
        DataPartitionMode sourceDataPartitionMode
                = systemSource.getDataPartitionMode();
        
        if (sourceDataPartitionMode != null) {
            String selectedItem = null;
            switch (sourceDataPartitionMode) {
                case NOT_USED:
                    selectedItem = STRINGS.getString("Not_Used");
                    break;

                case READ_ONLY:
                    selectedItem = STRINGS.getString("Read_Only");
                    break;

                case READ_WRITE:
                    selectedItem = STRINGS.getString("Read_Write");
                    break;

                default:
                    LOGGER.warning("Unsupported data partition mode!");
            }
            dataPartitionModeComboBox.setSelectedItem(selectedItem);
        }
    }

    public boolean isSystemMediumSelected() {
        return systemMediumRadioButton.isSelected();
    }

    public boolean isDataPartitionSelected() {
        return dataPartitionRadioButton.isSelected();
    }

    public String getTemporaryDirectory() {
        return tmpDirTextField.getText();
    }

    public boolean isShowNotUsedDialogSelected() {
        return showNotUsedDialogCheckBox.isSelected();
    }

    public boolean isAutoStartInstallerSelected() {
        return autoStartInstallerCheckBox.isSelected();
    }

    public boolean isBootMediumSelected() {
        return bootMediumRadioButton.isSelected();
    }

    public DataPartitionMode getDataPartitionMode() {
        return DLCopySwingGUI.getDataPartitionMode(dataPartitionModeComboBox);
    }

    public String getIsoLabel() {
        return isoLabelTextField.getText();
    }

    public void checkFreeSpace() {
        File tmpDir = new File(tmpDirTextField.getText());
        if (tmpDir.exists()) {
            long freeSpace = tmpDir.getFreeSpace();
            freeSpaceTextField.setText(
                    LernstickFileTools.getDataVolumeString(freeSpace, 1));
            if (tmpDir.canWrite()) {
                writableTextField.setText(STRINGS.getString("Yes"));
                writableTextField.setForeground(Color.BLACK);
                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.enableNextButton();
                }
            } else {
                writableTextField.setText(STRINGS.getString("No"));
                writableTextField.setForeground(Color.RED);
                if (dlCopySwingGUI != null) {
                    dlCopySwingGUI.disableNextButton();
                }
            }
        } else {
            freeSpaceTextField.setText(null);
            writableTextField.setText(
                    STRINGS.getString("Directory_Does_Not_Exist"));
            writableTextField.setForeground(Color.RED);
            if (dlCopySwingGUI != null) {
                dlCopySwingGUI.disableNextButton();
            }
        }
    }

    public void showProgressMessage(final String message) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(true);
            progressBar.setString(message);
        });
    }

    public void showProgressMessage(final String message, final int value) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setIndeterminate(false);
            progressBar.setString(message);
            progressBar.setValue(value);
        });
    }

    public void isoCreationFinished(String isoPath, boolean success) {
        String message;
        if (success) {
            message = STRINGS.getString("DLCopySwingGUI.isoDoneLabel.text");
            message = MessageFormat.format(message, isoPath);
        } else {
            message = STRINGS.getString("Error_ISO_Creation");
        }
        doneLabel.setText(message);
        DLCopySwingGUI.showCard(this, "donePanel");
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        mediumButtonGroup = new javax.swing.ButtonGroup();
        infoPanel = new javax.swing.JPanel();
        infoLabel = new javax.swing.JLabel();
        selectionPanel = new javax.swing.JPanel();
        tmpDriveInfoLabel = new javax.swing.JLabel();
        gridBagPanel = new javax.swing.JPanel();
        tmpDirLabel = new javax.swing.JLabel();
        tmpDirTextField = new javax.swing.JTextField();
        tmpDirSelectButton = new javax.swing.JButton();
        freeSpaceLabel = new javax.swing.JLabel();
        freeSpaceTextField = new javax.swing.JTextField();
        writableLabel = new javax.swing.JLabel();
        writableTextField = new javax.swing.JTextField();
        isoLabelLabel = new javax.swing.JLabel();
        isoLabelTextField = new javax.swing.JTextField();
        radioButtonPanel = new javax.swing.JPanel();
        bootMediumRadioButton = new javax.swing.JRadioButton();
        dataPartitionRadioButton = new javax.swing.JRadioButton();
        systemMediumRadioButton = new javax.swing.JRadioButton();
        optionsPanel = new javax.swing.JPanel();
        dataPartitionModeLabel = new javax.swing.JLabel();
        dataPartitionModeComboBox = new javax.swing.JComboBox<>();
        optionsCardPanel = new javax.swing.JPanel();
        systemMediumPanel = new javax.swing.JPanel();
        showNotUsedDialogCheckBox = new javax.swing.JCheckBox();
        autoStartInstallerCheckBox = new javax.swing.JCheckBox();
        bootMediumPanel = new javax.swing.JPanel();
        progressPanel = new javax.swing.JPanel();
        jLabel6 = new javax.swing.JLabel();
        progressBar = new javax.swing.JProgressBar();
        donePanel = new javax.swing.JPanel();
        doneLabel = new javax.swing.JLabel();

        setLayout(new java.awt.CardLayout());

        infoPanel.setLayout(new java.awt.GridBagLayout());

        infoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        infoLabel.setText(bundle.getString("DLCopySwingGUI.toISOInfoLabel.text")); // NOI18N
        infoLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        infoLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.weightx = 1.0;
        infoPanel.add(infoLabel, gridBagConstraints);

        add(infoPanel, "infoPanel");

        selectionPanel.setLayout(new java.awt.GridBagLayout());

        tmpDriveInfoLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        tmpDriveInfoLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/file_temporary.png"))); // NOI18N
        tmpDriveInfoLabel.setText(bundle.getString("DLCopySwingGUI.tmpDriveInfoLabel.text")); // NOI18N
        tmpDriveInfoLabel.setIconTextGap(15);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        selectionPanel.add(tmpDriveInfoLabel, gridBagConstraints);

        gridBagPanel.setLayout(new java.awt.GridBagLayout());

        tmpDirLabel.setText(bundle.getString("DLCopySwingGUI.tmpDirLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        gridBagPanel.add(tmpDirLabel, gridBagConstraints);

        tmpDirTextField.setColumns(20);
        tmpDirTextField.setText("/media/");
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        gridBagPanel.add(tmpDirTextField, gridBagConstraints);

        tmpDirSelectButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/fileopen.png"))); // NOI18N
        tmpDirSelectButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        tmpDirSelectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tmpDirSelectButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        gridBagPanel.add(tmpDirSelectButton, gridBagConstraints);

        freeSpaceLabel.setText(bundle.getString("DLCopySwingGUI.freeSpaceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        gridBagPanel.add(freeSpaceLabel, gridBagConstraints);

        freeSpaceTextField.setEditable(false);
        freeSpaceTextField.setColumns(20);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        gridBagPanel.add(freeSpaceTextField, gridBagConstraints);

        writableLabel.setText(bundle.getString("DLCopySwingGUI.writableLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        gridBagPanel.add(writableLabel, gridBagConstraints);

        writableTextField.setEditable(false);
        writableTextField.setColumns(20);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        gridBagPanel.add(writableTextField, gridBagConstraints);

        isoLabelLabel.setText(bundle.getString("DLCopySwingGUI.isoLabelLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 3, 0, 0);
        gridBagPanel.add(isoLabelLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 0);
        gridBagPanel.add(isoLabelTextField, gridBagConstraints);

        radioButtonPanel.setLayout(new java.awt.GridBagLayout());

        mediumButtonGroup.add(bootMediumRadioButton);
        bootMediumRadioButton.setText(bundle.getString("DLCopySwingGUI.bootMediumRadioButton.text")); // NOI18N
        bootMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                bootMediumRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(bootMediumRadioButton, gridBagConstraints);

        mediumButtonGroup.add(dataPartitionRadioButton);
        dataPartitionRadioButton.setText(bundle.getString("DLCopySwingGUI.dataPartitionRadioButton.text")); // NOI18N
        dataPartitionRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dataPartitionRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(dataPartitionRadioButton, gridBagConstraints);

        mediumButtonGroup.add(systemMediumRadioButton);
        systemMediumRadioButton.setSelected(true);
        systemMediumRadioButton.setText(bundle.getString("DLCopySwingGUI.systemMediumRadioButton.text")); // NOI18N
        systemMediumRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                systemMediumRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        radioButtonPanel.add(systemMediumRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        gridBagPanel.add(radioButtonPanel, gridBagConstraints);

        optionsPanel.setLayout(new java.awt.GridBagLayout());

        dataPartitionModeLabel.setText(bundle.getString("DLCopySwingGUI.isoDataPartitionModeLabel.text")); // NOI18N
        optionsPanel.add(dataPartitionModeLabel, new java.awt.GridBagConstraints());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        optionsPanel.add(dataPartitionModeComboBox, gridBagConstraints);

        optionsCardPanel.setName("optionsCardPanel"); // NOI18N
        optionsCardPanel.setLayout(new java.awt.CardLayout());

        systemMediumPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("DLCopySwingGUI.systemMediumPanel.border.title"))); // NOI18N
        systemMediumPanel.setLayout(new java.awt.GridBagLayout());

        showNotUsedDialogCheckBox.setText(bundle.getString("DLCopySwingGUI.showNotUsedDialogCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        systemMediumPanel.add(showNotUsedDialogCheckBox, gridBagConstraints);

        autoStartInstallerCheckBox.setText(bundle.getString("DLCopySwingGUI.autoStartInstallerCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        systemMediumPanel.add(autoStartInstallerCheckBox, gridBagConstraints);

        optionsCardPanel.add(systemMediumPanel, "systemMediumPanel");

        bootMediumPanel.setLayout(new java.awt.GridBagLayout());
        optionsCardPanel.add(bootMediumPanel, "bootMediumPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        optionsPanel.add(optionsCardPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        gridBagPanel.add(optionsPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(30, 10, 10, 10);
        selectionPanel.add(gridBagPanel, gridBagConstraints);

        add(selectionPanel, "selectionPanel");

        progressPanel.setLayout(new java.awt.GridBagLayout());

        jLabel6.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        progressPanel.add(jLabel6, gridBagConstraints);

        progressBar.setIndeterminate(true);
        progressBar.setPreferredSize(new java.awt.Dimension(250, 25));
        progressBar.setString(bundle.getString("DLCopySwingGUI.toISOProgressBar.string")); // NOI18N
        progressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(30, 30, 0, 30);
        progressPanel.add(progressBar, gridBagConstraints);

        add(progressPanel, "progressPanel");

        donePanel.setLayout(new java.awt.GridBagLayout());

        doneLabel.setFont(doneLabel.getFont().deriveFont(doneLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        doneLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        doneLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/usb2dvd.png"))); // NOI18N
        doneLabel.setText(bundle.getString("DLCopySwingGUI.isoDoneLabel.text")); // NOI18N
        doneLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        doneLabel.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
        donePanel.add(doneLabel, new java.awt.GridBagConstraints());

        add(donePanel, "donePanel");
    }// </editor-fold>//GEN-END:initComponents

    private void tmpDirSelectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tmpDirSelectButtonActionPerformed
        selectDirectory(tmpDirTextField);
    }//GEN-LAST:event_tmpDirSelectButtonActionPerformed

    private void bootMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_bootMediumRadioButtonActionPerformed
        updateMediumPanel();
        setISOElementsEnabled(true);
    }//GEN-LAST:event_bootMediumRadioButtonActionPerformed

    private void dataPartitionRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dataPartitionRadioButtonActionPerformed
        updateMediumPanel();
        setISOElementsEnabled(false);
    }//GEN-LAST:event_dataPartitionRadioButtonActionPerformed

    private void systemMediumRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_systemMediumRadioButtonActionPerformed
        updateMediumPanel();
        setISOElementsEnabled(true);
    }//GEN-LAST:event_systemMediumRadioButtonActionPerformed

    private void selectDirectory(JTextField textField) {
        String path = textField.getText();
        JFileChooser fileChooser = new JFileChooser(path);
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            String selectedPath = fileChooser.getSelectedFile().getPath();
            textField.setText(selectedPath);
        }
    }

    private void updateMediumPanel() {
        DLCopySwingGUI.showCard(optionsCardPanel,
                bootMediumRadioButton.isSelected()
                ? "bootMediumPanel" : "systemMediumPanel");
    }

    private void setISOElementsEnabled(boolean enabled) {
        isoLabelLabel.setEnabled(enabled);
        isoLabelTextField.setEnabled(enabled);
        dataPartitionModeLabel.setEnabled(enabled);
        dataPartitionModeComboBox.setEnabled(enabled);
    }

    private void documentChanged(DocumentEvent e) {
        if (e.getDocument() == tmpDirTextField.getDocument()) {
            checkFreeSpace();
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JCheckBox autoStartInstallerCheckBox;
    private javax.swing.JPanel bootMediumPanel;
    private javax.swing.JRadioButton bootMediumRadioButton;
    private javax.swing.JComboBox<String> dataPartitionModeComboBox;
    private javax.swing.JLabel dataPartitionModeLabel;
    private javax.swing.JRadioButton dataPartitionRadioButton;
    private javax.swing.JLabel doneLabel;
    private javax.swing.JPanel donePanel;
    private javax.swing.JLabel freeSpaceLabel;
    private javax.swing.JTextField freeSpaceTextField;
    private javax.swing.JPanel gridBagPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JLabel isoLabelLabel;
    private javax.swing.JTextField isoLabelTextField;
    private javax.swing.JLabel jLabel6;
    private javax.swing.ButtonGroup mediumButtonGroup;
    private javax.swing.JPanel optionsCardPanel;
    private javax.swing.JPanel optionsPanel;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JPanel radioButtonPanel;
    private javax.swing.JPanel selectionPanel;
    private javax.swing.JCheckBox showNotUsedDialogCheckBox;
    private javax.swing.JPanel systemMediumPanel;
    private javax.swing.JRadioButton systemMediumRadioButton;
    private javax.swing.JLabel tmpDirLabel;
    private javax.swing.JButton tmpDirSelectButton;
    private javax.swing.JTextField tmpDirTextField;
    private javax.swing.JLabel tmpDriveInfoLabel;
    private javax.swing.JLabel writableLabel;
    private javax.swing.JTextField writableTextField;
    // End of variables declaration//GEN-END:variables

}
