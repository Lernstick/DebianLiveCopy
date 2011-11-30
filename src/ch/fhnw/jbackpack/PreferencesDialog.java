/*
 * PreferencesDialog.java
 *
 * Copyright (C) 2010 imedias
 *
 * This file is part of JBackpack.
 *
 * JBackpack is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * JBackpack is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Created on 18. Juni 2006, 18:07
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.CurrentOperatingSystem;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.ResourceBundle;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.UIManager;

/**
 * the PGA Client settings dialog
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class PreferencesDialog extends JDialog {

    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings");
    private final static LogLevel[] LOG_LEVELS = LogLevel.values();
    private LogLevel logLevel;
    private boolean okButtonClicked;

    /**
     * Creates new form PreferencesDialog
     * @param parent the parent frame
     * @param currentLogFile the path to the current log file
     * @param logLevel the currently selected log level
     * @param showPlainWarning tt>true</tt> if the user selected to continue to
     * show this warning dialog, <tt>false</tt> otherwise
     */
    public PreferencesDialog(Frame parent, String currentLogFile,
            LogLevel logLevel, boolean showPlainWarning) {

        super(parent, true);

        initComponents();

        // fill menulist
        DefaultListModel menuListModel = new DefaultListModel();
        menuListModel.addElement(BUNDLE.getString("Logging_Level"));
        menuListModel.addElement(BUNDLE.getString("Miscellaneous"));
        menuList.setModel(menuListModel);
        List<Icon> icons = new ArrayList<Icon>();
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/32x32/toggle_log.png")));
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/32x32/misc.png")));
        menuList.setCellRenderer(new MyListCellRenderer(icons));
        menuList.setSelectedIndex(0);
        Dimension preferredSize = menuListScrollPane.getPreferredSize();
        menuListScrollPane.setPreferredSize(preferredSize);
        menuListScrollPane.setMinimumSize(preferredSize);

        if ((currentLogFile == null) || (currentLogFile.length() == 0)) {
            logFileTextField.setText(BUNDLE.getString("Unknown"));
        } else {
            logFileTextField.setText(currentLogFile);
        }

        // init log level stuff
        this.logLevel = logLevel;
        Hashtable<Integer, JComponent> loggingLabelTable =
                new Hashtable<Integer, JComponent>();
        for (int i = 0; i < 8; i++) {
            String levelText = LogLevel.values()[i].toString();
            JLabel label = new JLabel(levelText);
            // paint the "OFF" label red
            if (i == 0) {
                label.setForeground(Color.RED);
            }
            loggingLabelTable.put(i, label);
        }
        levelSlider.setLabelTable(loggingLabelTable);
        levelSlider.setValue(logLevel.ordinal());
        descriptionTextArea.setText(logLevel.getDescription());

        switch (CurrentOperatingSystem.OS) {
            case Mac_OS_X:
            case Linux:
                plainBackupWarningCheckBox.setSelected(showPlainWarning);
                break;

            default:
                // workaround for
                // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4783068
                Color disabledForeground =
                        UIManager.getColor("Button.disabledForeground");
                plainBackupWarningCheckBox.setSelected(false);
                plainBackupWarningCheckBox.setEnabled(false);
                plainBackupWarningCheckBox.setForeground(disabledForeground);
                plainBackupWarningCheckBox.setToolTipText(
                        BUNDLE.getString("Tooltip_ENCFS"));

        }

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * returns <CODE>true</CODE>, if the user clicked the OK button,
     * <CODE>false</CODE> otherwise
     * @return <CODE>true</CODE>, if the user clicked the OK button,
     * <CODE>false</CODE> otherwise
     */
    public boolean okPressed() {
        return okButtonClicked;
    }

    /**
     * returns the selected log level
     * @return the selected log level
     */
    public LogLevel getLogLevel() {
        return logLevel;
    }

    /**
     * returns <tt>true</tt> if the user selected to show a warning dialog, when
     * the destination directory is not encrypted<tt>false</tt> otherwise
     * @return <tt>true</tt> if the user selected to show a warning dialog, when
     * the destination directory is not encrypted<tt>false</tt> otherwise
     */
    public boolean isShowPlainBackupWarningSelected() {
        return plainBackupWarningCheckBox.isSelected();
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        menuListScrollPane = new javax.swing.JScrollPane();
        menuList = new javax.swing.JList();
        cardPanel = new javax.swing.JPanel();
        logPanel = new javax.swing.JPanel();
        logfilePanel = new javax.swing.JPanel();
        logFileLabel = new javax.swing.JLabel();
        logFileTextField = new javax.swing.JTextField();
        jSeparator1 = new javax.swing.JSeparator();
        levelSlider = new javax.swing.JSlider();
        descriptionScrollPane = new javax.swing.JScrollPane();
        descriptionTextArea = new javax.swing.JTextArea();
        miscPanel = new javax.swing.JPanel();
        plainBackupWarningCheckBox = new javax.swing.JCheckBox();
        buttonPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        setTitle(bundle.getString("PreferencesDialog.title")); // NOI18N
        getContentPane().setLayout(new java.awt.GridBagLayout());

        menuList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        menuList.setName("menuList"); // NOI18N
        menuList.setVisibleRowCount(4);
        menuList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                menuListValueChanged(evt);
            }
        });
        menuListScrollPane.setViewportView(menuList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(menuListScrollPane, gridBagConstraints);

        cardPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        cardPanel.setLayout(new java.awt.CardLayout());

        logPanel.setLayout(new java.awt.GridBagLayout());

        logfilePanel.setLayout(new java.awt.GridBagLayout());

        logFileLabel.setText(bundle.getString("PreferencesDialog.logFileLabel.text")); // NOI18N
        logfilePanel.add(logFileLabel, new java.awt.GridBagConstraints());

        logFileTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        logfilePanel.add(logFileTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        logPanel.add(logfilePanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        logPanel.add(jSeparator1, gridBagConstraints);

        levelSlider.setMajorTickSpacing(1);
        levelSlider.setMaximum(7);
        levelSlider.setOrientation(javax.swing.JSlider.VERTICAL);
        levelSlider.setPaintLabels(true);
        levelSlider.setPaintTicks(true);
        levelSlider.setSnapToTicks(true);
        levelSlider.setValue(0);
        levelSlider.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                levelSliderStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 10, 5);
        logPanel.add(levelSlider, gridBagConstraints);

        descriptionTextArea.setColumns(20);
        descriptionTextArea.setEditable(false);
        descriptionTextArea.setLineWrap(true);
        descriptionTextArea.setWrapStyleWord(true);
        descriptionScrollPane.setViewportView(descriptionTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 10, 10);
        logPanel.add(descriptionScrollPane, gridBagConstraints);

        cardPanel.add(logPanel, "logPanel");

        miscPanel.setLayout(new java.awt.GridBagLayout());

        plainBackupWarningCheckBox.setText(bundle.getString("PreferencesDialog.plainBackupWarningCheckBox.text")); // NOI18N
        plainBackupWarningCheckBox.setName("plainBackupWarningCheckBox"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        miscPanel.add(plainBackupWarningCheckBox, gridBagConstraints);

        cardPanel.add(miscPanel, "miscPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(cardPanel, gridBagConstraints);

        buttonPanel.setLayout(new java.awt.GridBagLayout());

        okButton.setText(bundle.getString("PreferencesDialog.okButton.text")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
        buttonPanel.add(okButton, gridBagConstraints);

        cancelButton.setText(bundle.getString("PreferencesDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        buttonPanel.add(cancelButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        getContentPane().add(buttonPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void levelSliderStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_levelSliderStateChanged
        logLevel = LOG_LEVELS[levelSlider.getValue()];
        descriptionTextArea.setText(logLevel.getDescription());
    }//GEN-LAST:event_levelSliderStateChanged

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        dispose();
        setVisible(false);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        okButtonClicked = true;
        dispose();
        setVisible(false);
    }//GEN-LAST:event_okButtonActionPerformed

    private void menuListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_menuListValueChanged
        int index = menuList.getSelectedIndex();
        switch (index) {
            case 0:
                showPanel("logPanel");
                break;
            case 1:
                showPanel("miscPanel");
                break;
            default:
            // ?
        }
    }//GEN-LAST:event_menuListValueChanged

    private void showPanel(String panel) {
        CardLayout layout = (CardLayout) cardPanel.getLayout();
        layout.show(cardPanel, panel);
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel cardPanel;
    private javax.swing.JScrollPane descriptionScrollPane;
    private javax.swing.JTextArea descriptionTextArea;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSlider levelSlider;
    private javax.swing.JLabel logFileLabel;
    private javax.swing.JTextField logFileTextField;
    private javax.swing.JPanel logPanel;
    private javax.swing.JPanel logfilePanel;
    private javax.swing.JList menuList;
    private javax.swing.JScrollPane menuListScrollPane;
    private javax.swing.JPanel miscPanel;
    private javax.swing.JButton okButton;
    private javax.swing.JCheckBox plainBackupWarningCheckBox;
    // End of variables declaration//GEN-END:variables
}
