/*
 * BackupDirSelect.java
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
 * Created on Apr 8, 2010, 8:49:53 AM
 */
package ch.fhnw.jbackpack.chooser;

import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.OperatingSystem;
import java.awt.Cursor;
import java.awt.FileDialog;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.Document;

/**
 *
 * @author mw
 */
public class SelectBackupDirectoryDialog extends JDialog
        implements DocumentListener, PropertyChangeListener {

    private final static Logger LOGGER =
            Logger.getLogger(SelectBackupDirectoryDialog.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final ChrootFileSystemView chrootFileSystemView;
    private final Document directoryTextFieldDocument;
    private final String initialDirectory;
    private String selectedPath;
    private boolean selected;

    /** Creates new form BackupDirSelect
     * @param parent the parent of this dialog
     * @param chrootFileSystemView the ChrootFileSystemView to use
     * @param initialDirectory the initial directory to check
     * @param showRadioButtons if true, the radio buttons are shown
     */
    public SelectBackupDirectoryDialog(Window parent,
            ChrootFileSystemView chrootFileSystemView, String initialDirectory,
            boolean showRadioButtons) {

        super(parent, ModalityType.APPLICATION_MODAL);
        this.chrootFileSystemView = chrootFileSystemView;
        this.initialDirectory = initialDirectory;
        initComponents();
        rdiffChooserPanel.setRadioButtonsVisible(showRadioButtons);
        directoryTextFieldDocument = directoryTextField.getDocument();
        pack();
        setLocationRelativeTo(parent);
        init();
    }

    /**
     * shows this dialog
     * @return JOptionPane.OK_OPTION when approved,
     * else JOptionPane.CANCEL_OPTION
     */
    public int showDialog() {
        setVisible(true);
        return selected ? JOptionPane.OK_OPTION : JOptionPane.CANCEL_OPTION;
    }

    /**
     * returns the selected directory
     * @return the selected directory
     */
    public String getSelectedPath() {
        return selectedPath;
    }

    /**
     * Gives notification that there was an insert into the document. The range
     * given by the DocumentEvent bounds the freshly inserted region.
     * @param e the document event
     */
    public void insertUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    /**
     * Gives notification that a portion of the document has been removed. The
     * range is given in terms of what the view last saw (that is, before
     * updating sticky positions).
     * @param e the document event
     */
    public void removeUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    /**
     * Gives notification that an attribute or set of attributes changed.
     * @param e the document event
     */
    public void changedUpdate(DocumentEvent e) {
        documentChanged(e);
    }

    /**
     * This method gets called when a bound property is changed.
     * @param evt A PropertyChangeEvent object describing the event source and
     * the property that has changed.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        String propertyName = evt.getPropertyName();
        Object newValue = evt.getNewValue();

        if (RdiffChooserPanel.CHECK_RUNNING_PROPERTY.equals(propertyName)) {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    setCursor(Cursor.getPredefinedCursor(
                            Cursor.WAIT_CURSOR));
                } else {
                    setCursor(Cursor.getPredefinedCursor(
                            Cursor.DEFAULT_CURSOR));
                }
            } else {
                LOGGER.log(Level.WARNING, "unsupported value: {0}", newValue);
            }

        } else if (RdiffChooserPanel.DIRECTORY_SELECTABLE_PROPERTY.equals(
                propertyName)) {
            if (newValue instanceof Boolean) {
                boolean directorySelectable = (Boolean) newValue;
                selectButton.setEnabled(directorySelectable);
            } else {
                LOGGER.log(Level.WARNING, "unsupported value: {0}", newValue);
            }
        }
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        directoryLabel = new javax.swing.JLabel();
        directoryTextField = new javax.swing.JTextField();
        directoryButton = new javax.swing.JButton();
        rdiffChooserPanel = new ch.fhnw.jbackpack.chooser.RdiffChooserPanel();
        selectButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        setTitle(bundle.getString("RdiffChooserPanel.selectBckDlg")); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });

        directoryLabel.setText(bundle.getString("RdiffChooserPanel.directoryLabel.text")); // NOI18N

        directoryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        directoryButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        directoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                directoryButtonActionPerformed(evt);
            }
        });

        rdiffChooserPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        selectButton.setText(bundle.getString("Select")); // NOI18N
        selectButton.setName("selectButton"); // NOI18N
        selectButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                selectButtonActionPerformed(evt);
            }
        });

        cancelButton.setText(bundle.getString("Cancel")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(rdiffChooserPanel, javax.swing.GroupLayout.PREFERRED_SIZE, 749, Short.MAX_VALUE)
                    .addGroup(layout.createSequentialGroup()
                        .addComponent(directoryLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(directoryTextField, javax.swing.GroupLayout.DEFAULT_SIZE, 573, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(directoryButton))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, layout.createSequentialGroup()
                        .addComponent(selectButton)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(cancelButton)))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                    .addComponent(directoryLabel)
                    .addComponent(directoryTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(directoryButton))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(rdiffChooserPanel, javax.swing.GroupLayout.DEFAULT_SIZE, 415, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(cancelButton)
                    .addComponent(selectButton))
                .addContainerGap())
        );
    }// </editor-fold>//GEN-END:initComponents

    private void directoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_directoryButtonActionPerformed
        showFileChooser();
}//GEN-LAST:event_directoryButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        selected = false;
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void selectButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_selectButtonActionPerformed
        selectedPath = directoryTextField.getText();
        selected = true;
        dispose();
    }//GEN-LAST:event_selectButtonActionPerformed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        directoryTextField.setText(initialDirectory);
        if ((initialDirectory == null) || initialDirectory.isEmpty()) {
            showFileChooser();
        }
    }//GEN-LAST:event_formWindowOpened

    private void showFileChooser() {
        String title = BUNDLE.getString("Select_Backup_Directory");

        // walk currentDir up to existing directory
        File currentDir = new File(directoryTextField.getText());
        for (File parentFile = currentDir.getParentFile();
                (!currentDir.exists()) && (parentFile != null);) {
            currentDir = parentFile;
            parentFile = currentDir.getParentFile();
        }

        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            FileDialog fileDialog = new FileDialog(
                    this, title, FileDialog.LOAD);
            fileDialog.setDirectory(currentDir.getPath());
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            fileDialog.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
            String directory = fileDialog.getDirectory();
            String file = fileDialog.getFile();
            if ((directory != null) && (file != null)) {
                directoryTextField.setText(
                        directory + file + File.separatorChar);
            }

        } else {
            JFileChooser directoryChooser =
                    new JFileChooser(chrootFileSystemView);
            directoryChooser.setFileSelectionMode(
                    JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setFileHidingEnabled(false);
            FileFilter noHiddenFilesFilter =
                    NoHiddenFilesSwingFileFilter.getInstance();
            directoryChooser.addChoosableFileFilter(noHiddenFilesFilter);
            directoryChooser.setFileFilter(noHiddenFilesFilter);
            if (currentDir.exists()) {
                directoryChooser.setCurrentDirectory(currentDir);
            } else {
                directoryChooser.setCurrentDirectory(
                        directoryChooser.getFileSystemView().getRoots()[0]);
            }
            directoryChooser.setDialogTitle(title);
            directoryChooser.setApproveButtonText(BUNDLE.getString("Choose"));
            if (directoryChooser.showOpenDialog(this)
                    == JFileChooser.APPROVE_OPTION) {
                String newSelectedPath =
                        directoryChooser.getSelectedFile().getPath();
                directoryTextField.setText(newSelectedPath);
            }
        }
    }

    private void init() {
        rdiffChooserPanel.addPropertyChangeListener(this);
        rdiffChooserPanel.setParentWindow(this);
        directoryTextFieldDocument.addDocumentListener(this);
        rdiffChooserPanel.setSelectedDirectory(null);
    }

    private void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        if (document == directoryTextFieldDocument) {
            // early return when there was no real change. this happens e.g.
            // when appending or removing a file separator to the currently
            // selected directory
            String newDirPath = directoryTextField.getText();
            if (selectedPath != null) {
                File oldDir = new File(selectedPath);
                File newDir = new File(newDirPath);
                if (newDir.equals(oldDir)) {
                    return;
                }
            }
            selectedPath = newDirPath;
            rdiffChooserPanel.setSelectedDirectory(selectedPath);
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton directoryButton;
    private javax.swing.JLabel directoryLabel;
    private javax.swing.JTextField directoryTextField;
    private ch.fhnw.jbackpack.chooser.RdiffChooserPanel rdiffChooserPanel;
    private javax.swing.JButton selectButton;
    // End of variables declaration//GEN-END:variables
}
