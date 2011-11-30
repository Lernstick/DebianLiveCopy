/*
 * RdiffChooserPanel.java
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
 * Created on 31.03.2010, 11:01:10
 */
package ch.fhnw.jbackpack.chooser;

import ch.fhnw.jbackpack.ProgressDialog;
import ch.fhnw.jbackpack.RdiffBackupRestore;
import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.ProcessExecutor;
import java.awt.CardLayout;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.FileChooserUI;
import javax.swing.plaf.basic.BasicFileChooserUI;

/**
 * A panel for selecting and navigating in rdiff-backup directories
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffChooserPanel
        extends JPanel implements PropertyChangeListener {

    /**
     * Identifies if a directory check is running or not
     */
    public final static String CHECK_RUNNING_PROPERTY = "checkRunning";
    /**
     * Identifies if the current directory is selectable or not
     */
    public final static String DIRECTORY_SELECTABLE_PROPERTY =
            "directorySelectable";
    private final static Logger LOGGER =
            Logger.getLogger(RdiffChooserPanel.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final ProcessExecutor processExecutor = new ProcessExecutor();
    private final CardLayout cardLayout;
    private final CardLayout filesCardLayout;
    private final IncrementModel incrementModel = new IncrementModel();
    private final RdiffFileSystemView rdiffFileSystemView =
            new RdiffFileSystemView();
    private final FileFilter noHiddenFilesSwingFilter =
            NoHiddenFilesSwingFileFilter.getInstance();
    private final Desktop desktop;
    private final boolean desktopIsSupported;
    private List<Increment> increments = new ArrayList<Increment>();
    private Window parentWindow;
    private String selectedDirectory;
    private File[] oldSelectedFiles;
    private RdiffFileDatabase rdiffFileDatabase;

    /** Creates new form RdiffChooserPanel */
    public RdiffChooserPanel() {

        initComponents();

        fileChooser.addChoosableFileFilter(noHiddenFilesSwingFilter);
        fileChooser.setFileFilter(noHiddenFilesSwingFilter);
        fileChooser.setFileView(new RdiffFileView());

        backupsList.setModel(incrementModel);
        backupsList.setCellRenderer(new IncrementsListCellRenderer());
        cardLayout = (CardLayout) getLayout();
        filesCardLayout = (CardLayout) filesCardPanel.getLayout();

        // listen to "busy" property of the filechooser UI model to be able to
        // restore file selection
        FileChooserUI fileChooserUI = fileChooser.getUI();
        if (fileChooserUI instanceof BasicFileChooserUI) {
            BasicFileChooserUI basicFileChooserUI =
                    (BasicFileChooserUI) fileChooserUI;
            basicFileChooserUI.getModel().addPropertyChangeListener(this);
        } else {
            LOGGER.warning("can not keep file selection when switching between "
                    + "increments");
        }

        // run all processes with the posix locale because we parse
        // English output
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("LC_ALL", "C");
        processExecutor.setEnvironment(environment);

        if (Desktop.isDesktopSupported()) {
            desktop = Desktop.getDesktop();
            desktopIsSupported = desktop.isSupported(Desktop.Action.OPEN);
            if (!desktopIsSupported) {
                LOGGER.warning("desktop \"open\" action is not supported");
            }
        } else {
            desktop = null;
            desktopIsSupported = false;
            LOGGER.warning("desktop is not supported");
        }
        if (!desktopIsSupported) {
            previewButton.setToolTipText(
                    BUNDLE.getString("Preview_Disabled_Tooltip"));
        }

        // some layout fixes that can only be done dynamically
        Dimension preferredSize = backupsListScrollPane.getPreferredSize();
        preferredSize.width = 280;
        backupsListScrollPane.setMinimumSize(preferredSize);
        backupsListScrollPane.setPreferredSize(preferredSize);
    }

    /**
     * This method gets called when a bound property is changed.
     * @param evt A PropertyChangeEvent object describing the event source and
     * the property that has changed.
     */
    public void propertyChange(PropertyChangeEvent evt) {
        if ("busy".equals(evt.getPropertyName())
                && evt.getNewValue() == Boolean.FALSE) {
            // the file loading thread is done
            // -> try to restore previous file selection
            File[] newFiles = fileChooser.getCurrentDirectory().listFiles();
            Collection<File> newSelectedFilesCollection =
                    new ArrayList<File>();
            for (File newFile : newFiles) {
                for (File oldSelectedFile : oldSelectedFiles) {
                    if (newFile.getName().equals(oldSelectedFile.getName())) {
                        newSelectedFilesCollection.add(newFile);
                    }
                }
            }
            File[] newSelectedFiles =
                    newSelectedFilesCollection.toArray(
                    new File[newSelectedFilesCollection.size()]);
            fileChooser.setSelectedFiles(newSelectedFiles);
        }
    }

    /**
     * sets the parent window
     * @param parentWindow the parent window
     */
    public void setParentWindow(Window parentWindow) {
        this.parentWindow = parentWindow;
    }

    /**
     * defines, if the radio buttons are visible
     * @param visible if <tt>true</tt>, the radio buttons are visible, otherwise
     * the radio buttons are hidden
     */
    public void setRadioButtonsVisible(boolean visible) {
        allBackupRadioButton.setVisible(visible);
        backupFilesRadioButton.setVisible(visible);
    }

    /**
     * checks, if <tt>selectedDirectory</tt> is a rdiff-backup directory and
     * shows the corresponding panels
     * @param selectedDir
     */
    public void setSelectedDirectory(String selectedDir) {

        // check, if selectedDirectory is a rdiff-backup directory and show
        // the corresponding panels
        this.selectedDirectory = selectedDir;
        if ((selectedDir == null) || (selectedDir.length() == 0)) {
            dirCheckError("Error_No_Selection");
            return;
        }

        File testFile = new File(selectedDir);

        // show some space information
        FileTools.showSpaceInfo(testFile, storageUsageProgressBar);

        if (!testFile.exists()) {
            dirCheckError("Error_Directory_Does_Not_Exist");
            return;
        }

        if (!testFile.isDirectory()) {
            dirCheckError("Error_No_Directory");
            return;
        }

        if (!testFile.canRead()) {
            dirCheckError("Error_Directory_Unreadable");
            return;
        }

//        if (!FileTools.canWrite(testFile)) {
//            dirCheckError("Error_Directory_Read-Only");
//            return;
//        }
//
        File rdiffBackupDataDir = new File(testFile, "rdiff-backup-data");
        if (rdiffBackupDataDir.exists()) {
            if (!rdiffBackupDataDir.canRead()) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                            "can not read {0}{1}rdiff-backup-data",
                            new Object[]{testFile, File.separatorChar});
                }
                dirCheckError("Error_Read-Only", rdiffBackupDataDir);
                return;
            }
            if (!rdiffBackupDataDir.canExecute()) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO,
                            "can not enter directory {0}{1}rdiff-backup-data",
                            new Object[]{testFile, File.separatorChar});
                }
                dirCheckError("Error_No_Execute_Dir", rdiffBackupDataDir);
                return;
            }
        }

        // check directory for errors
        ProgressDialog dialog =
                new ProgressDialog(parentWindow, processExecutor);
        ModalDialogHandler dialogHandler = new ModalDialogHandler(dialog);
        dialog.setCancelButtonVisible(false);
        DirectoryChecker directoryChecker = new DirectoryChecker(
                dialog, dialogHandler, testFile);
        LOGGER.fine("scheduling directoryChecker for execution");
        directoryChecker.execute();
        dialogHandler.show();
    }

    /**
     * returns the selected files
     * @return the selected files
     */
    public RdiffFile[] getSelectedFiles() {
        if (backupFilesRadioButton.isSelected()) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles != null) {
                int length = selectedFiles.length;
                if (length > 0) {
                    RdiffFile[] rdiffFiles = new RdiffFile[length];
                    for (int i = 0; i < length; i++) {
                        rdiffFiles[i] = (RdiffFile) selectedFiles[i];
                    }
                    return rdiffFiles;
                } else {
                    LOGGER.warning("no files selected");
                    return null;
                }
            }
        }
        if (allBackupRadioButton.isSelected()) {
            int selectedIndex = backupsList.getSelectedIndex();
            if (selectedIndex == -1) {
                LOGGER.warning("no increment selected");
                return null;
            } else {
                Increment increment = increments.get(selectedIndex);
                RdiffFile root = increment.getRdiffRoot();
                if (root == null) {
                    LOGGER.log(Level.WARNING, "increment {0} has no root",
                            increment.getRdiffTimestamp());
                    return null;
                } else {
                    return new RdiffFile[]{root};
                }
            }
        }
        LOGGER.warning("unsupported state");
        return null;
    }

    /**
     * returns the current RdiffFileDatabase
     * @return the current RdiffFileDatabase
     */
    public RdiffFileDatabase getRdiffFileDatabase() {
        return rdiffFileDatabase;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        backupsListPopupMenu = new javax.swing.JPopupMenu();
        deleteMenuItem = new javax.swing.JMenuItem();
        backupSelectionGroup = new javax.swing.ButtonGroup();
        rdiffDirectoryPanel = new javax.swing.JPanel();
        backupsPanel = new javax.swing.JPanel();
        backupsListScrollPane = new javax.swing.JScrollPane();
        backupsList = new javax.swing.JList();
        upButton = new javax.swing.JButton();
        downButton = new javax.swing.JButton();
        backupSizesPanel = new javax.swing.JPanel();
        cumulativeSizeLabel = new javax.swing.JLabel();
        cumulativeSizeTextField = new javax.swing.JTextField();
        totalSizeLabel = new javax.swing.JLabel();
        totalSizeTextField = new javax.swing.JTextField();
        storageUsageLabel = new javax.swing.JLabel();
        storageUsageProgressBar = new javax.swing.JProgressBar();
        deleteBackupButton = new javax.swing.JButton();
        filesPanel = new javax.swing.JPanel();
        allBackupRadioButton = new javax.swing.JRadioButton();
        backupFilesRadioButton = new javax.swing.JRadioButton();
        filesCardPanel = new javax.swing.JPanel();
        selectIncrementPanel = new javax.swing.JPanel();
        selectIncrementLabel = new javax.swing.JLabel();
        fileChooser = new javax.swing.JFileChooser(rdiffFileSystemView);
        emptyPanel = new javax.swing.JPanel();
        previewButton = new javax.swing.JButton();
        noRdiffDirectoryPanel = new javax.swing.JPanel();
        noRdiffDirectoryLabel = new javax.swing.JLabel();

        deleteMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/remove.png"))); // NOI18N
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        deleteMenuItem.setText(bundle.getString("RdiffChooserPanel.deleteMenuItem.text")); // NOI18N
        deleteMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteMenuItemActionPerformed(evt);
            }
        });
        backupsListPopupMenu.add(deleteMenuItem);

        setLayout(new java.awt.CardLayout());

        rdiffDirectoryPanel.setLayout(new java.awt.GridBagLayout());

        backupsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("RdiffChooserPanel.backupsPanel.border.title"))); // NOI18N
        backupsPanel.setLayout(new java.awt.GridBagLayout());

        backupsList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        backupsList.setName("backupsList"); // NOI18N
        backupsList.setVisibleRowCount(1);
        backupsList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mousePressed(java.awt.event.MouseEvent evt) {
                backupsListMousePressed(evt);
            }
            public void mouseReleased(java.awt.event.MouseEvent evt) {
                backupsListMouseReleased(evt);
            }
        });
        backupsList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                backupsListValueChanged(evt);
            }
        });
        backupsListScrollPane.setViewportView(backupsList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        backupsPanel.add(backupsListScrollPane, gridBagConstraints);

        upButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/up.png"))); // NOI18N
        upButton.setToolTipText(bundle.getString("RdiffChooserPanel.upButton.toolTipText")); // NOI18N
        upButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        upButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                upButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 3);
        backupsPanel.add(upButton, gridBagConstraints);

        downButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/down.png"))); // NOI18N
        downButton.setToolTipText(bundle.getString("RdiffChooserPanel.downButton.toolTipText")); // NOI18N
        downButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        downButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                downButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        backupsPanel.add(downButton, gridBagConstraints);

        backupSizesPanel.setLayout(new java.awt.GridBagLayout());

        cumulativeSizeLabel.setText(bundle.getString("RdiffChooserPanel.cumulativeSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        backupSizesPanel.add(cumulativeSizeLabel, gridBagConstraints);

        cumulativeSizeTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        backupSizesPanel.add(cumulativeSizeTextField, gridBagConstraints);

        totalSizeLabel.setText(bundle.getString("RdiffChooserPanel.totalSizeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 0);
        backupSizesPanel.add(totalSizeLabel, gridBagConstraints);

        totalSizeTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        backupSizesPanel.add(totalSizeTextField, gridBagConstraints);

        storageUsageLabel.setText(bundle.getString("RdiffChooserPanel.storageUsageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 0);
        backupSizesPanel.add(storageUsageLabel, gridBagConstraints);

        storageUsageProgressBar.setFont(storageUsageProgressBar.getFont().deriveFont(storageUsageProgressBar.getFont().getStyle() & ~java.awt.Font.BOLD, storageUsageProgressBar.getFont().getSize()-1));
        storageUsageProgressBar.setString(""); // NOI18N
        storageUsageProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        backupSizesPanel.add(storageUsageProgressBar, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 3);
        backupsPanel.add(backupSizesPanel, gridBagConstraints);

        deleteBackupButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/remove.png"))); // NOI18N
        deleteBackupButton.setText(bundle.getString("RdiffChooserPanel.deleteBackupButton.text")); // NOI18N
        deleteBackupButton.setEnabled(false);
        deleteBackupButton.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
        deleteBackupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                deleteBackupButtonActionPerformed(evt);
            }
        });
        deleteBackupButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                deleteBackupButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                deleteBackupButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 5, 3);
        backupsPanel.add(deleteBackupButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        rdiffDirectoryPanel.add(backupsPanel, gridBagConstraints);

        filesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("RdiffChooserPanel.filesPanel.border.title"))); // NOI18N
        filesPanel.setLayout(new java.awt.GridBagLayout());

        backupSelectionGroup.add(allBackupRadioButton);
        allBackupRadioButton.setText(bundle.getString("RdiffChooserPanel.allBackupRadioButton.text")); // NOI18N
        allBackupRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                allBackupRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        filesPanel.add(allBackupRadioButton, gridBagConstraints);

        backupSelectionGroup.add(backupFilesRadioButton);
        backupFilesRadioButton.setSelected(true);
        backupFilesRadioButton.setText(bundle.getString("RdiffChooserPanel.backupFilesRadioButton.text")); // NOI18N
        backupFilesRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupFilesRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        filesPanel.add(backupFilesRadioButton, gridBagConstraints);

        filesCardPanel.setLayout(new java.awt.CardLayout());

        selectIncrementPanel.setLayout(new java.awt.GridBagLayout());

        selectIncrementLabel.setText(bundle.getString("RdiffChooserPanel.selectIncrementLabel.text")); // NOI18N
        selectIncrementPanel.add(selectIncrementLabel, new java.awt.GridBagConstraints());

        filesCardPanel.add(selectIncrementPanel, "selectIncrementPanel");

        fileChooser.setControlButtonsAreShown(false);
        fileChooser.setFileSelectionMode(javax.swing.JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setName("fileChooser"); // NOI18N
        fileChooser.addPropertyChangeListener(new java.beans.PropertyChangeListener() {
            public void propertyChange(java.beans.PropertyChangeEvent evt) {
                fileChooserPropertyChange(evt);
            }
        });
        filesCardPanel.add(fileChooser, "fileChooser");

        emptyPanel.setLayout(new java.awt.GridBagLayout());
        filesCardPanel.add(emptyPanel, "emptyPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        filesPanel.add(filesCardPanel, gridBagConstraints);

        previewButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/previewer.png"))); // NOI18N
        previewButton.setText(bundle.getString("RdiffChooserPanel.previewButton.text")); // NOI18N
        previewButton.setEnabled(false);
        previewButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previewButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 10);
        filesPanel.add(previewButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        rdiffDirectoryPanel.add(filesPanel, gridBagConstraints);

        add(rdiffDirectoryPanel, "rdiffDirectoryPanel");

        noRdiffDirectoryPanel.setLayout(new java.awt.GridBagLayout());

        noRdiffDirectoryLabel.setText(bundle.getString("Warning_No_Backup_Directory")); // NOI18N
        noRdiffDirectoryPanel.add(noRdiffDirectoryLabel, new java.awt.GridBagConstraints());

        add(noRdiffDirectoryPanel, "noRdiffDirectoryPanel");
    }// </editor-fold>//GEN-END:initComponents

    private void deleteMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteMenuItemActionPerformed
        deleteBackups();
    }//GEN-LAST:event_deleteMenuItemActionPerformed

    private void deleteBackupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_deleteBackupButtonActionPerformed
        deleteBackups();
}//GEN-LAST:event_deleteBackupButtonActionPerformed

    private void backupsListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_backupsListValueChanged
        if (evt.getValueIsAdjusting()) {
            return;
        }

        // make sure that all older increments are selected as well
        int[] selectedIndices = backupsList.getSelectedIndices();
        boolean mustFixSelection = false;
        int selectionLength = selectedIndices.length;
        if (selectionLength == 0) {
            return;
        }
        int topIndex = selectedIndices[0];
        int bottomIndex = selectedIndices[selectionLength - 1];
        if (bottomIndex == incrementModel.getSize() - 1) {
            // selection goes to bottom, but is it continuous?
            for (int i = 0; i < selectionLength - 1; i++) {
                if ((selectedIndices[i] + 1) != (selectedIndices[i + 1])) {
                    mustFixSelection = true;
                    break;
                }
            }
        } else {
            mustFixSelection = true;
        }
        if (mustFixSelection) {
            backupsList.setSelectionInterval(
                    backupsList.getModel().getSize() - 1, topIndex);
            return;
        }

        upButton.setEnabled(topIndex != 0);
        downButton.setEnabled(topIndex != increments.size() - 1);
        deleteBackupButton.setEnabled(topIndex != -1);
        updateSizeInformation();
        updateIncrementView();
}//GEN-LAST:event_backupsListValueChanged

    private void backupsListMouseReleased(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_backupsListMouseReleased
        maybeShowPopup(evt);
}//GEN-LAST:event_backupsListMouseReleased

    private void backupsListMousePressed(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_backupsListMousePressed
        maybeShowPopup(evt);
}//GEN-LAST:event_backupsListMousePressed

    private void allBackupRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_allBackupRadioButtonActionPerformed
        updateIncrementView();
    }//GEN-LAST:event_allBackupRadioButtonActionPerformed

    private void backupFilesRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupFilesRadioButtonActionPerformed
        updateIncrementView();
    }//GEN-LAST:event_backupFilesRadioButtonActionPerformed

    private void deleteBackupButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_deleteBackupButtonFocusGained
        getRootPane().setDefaultButton(deleteBackupButton);
    }//GEN-LAST:event_deleteBackupButtonFocusGained

    private void deleteBackupButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_deleteBackupButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_deleteBackupButtonFocusLost

    private void upButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_upButtonActionPerformed
        backupsList.setSelectedIndex(backupsList.getSelectedIndex() - 1);
    }//GEN-LAST:event_upButtonActionPerformed

    private void downButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_downButtonActionPerformed
        backupsList.setSelectedIndex(backupsList.getSelectedIndex() + 1);
    }//GEN-LAST:event_downButtonActionPerformed

    private void fileChooserPropertyChange(java.beans.PropertyChangeEvent evt) {//GEN-FIRST:event_fileChooserPropertyChange
        String propertyName = evt.getPropertyName();

        if (JFileChooser.FILE_FILTER_CHANGED_PROPERTY.equals(propertyName)) {
            fileChooser.setFileHidingEnabled(
                    fileChooser.getFileFilter() == noHiddenFilesSwingFilter);
            fileChooser.rescanCurrentDirectory();

        } else if (desktopIsSupported
                && JFileChooser.SELECTED_FILES_CHANGED_PROPERTY.equals(
                propertyName)) {
            previewButton.setEnabled(fileChooser.getSelectedFiles().length > 0);
        }
    }//GEN-LAST:event_fileChooserPropertyChange

    private void previewButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previewButtonActionPerformed
        previewFiles();
}//GEN-LAST:event_previewButtonActionPerformed

    private void dirCheckInfo(String messageKey) {
        dirCheckUnsuccessful("OptionPane.informationIcon", messageKey);
        cardLayout.show(this, "noRdiffDirectoryPanel");
    }

    private void dirCheckError(String messageKey, Object... arguments) {
        dirCheckUnsuccessful("OptionPane.errorIcon", messageKey, arguments);
    }

    private void dirCheckUnsuccessful(
            String iconKey, String messageKey, Object... arguments) {
        noRdiffDirectoryLabel.setIcon(UIManager.getIcon(iconKey));
        String message = BUNDLE.getString(messageKey);
        message = MessageFormat.format(message, arguments);
        noRdiffDirectoryLabel.setText(message);
        cardLayout.show(this, "noRdiffDirectoryPanel");
    }

    private void updateIncrementView() {

        int selectedIndex = backupsList.getSelectedIndex();
        if (allBackupRadioButton.isSelected()) {
            filesCardLayout.show(filesCardPanel, "emptyPanel");
            return;
        }

        if (selectedIndex == -1) {
            filesCardLayout.show(filesCardPanel, "selectIncrementPanel");

        } else {
            Increment increment = increments.get(selectedIndex);
            RdiffFile root = increment.getRdiffRoot();
            rdiffFileSystemView.setRoot(root);

            // store the old selected files
            // (will be tried to restore when file loading thread is done)
            oldSelectedFiles = fileChooser.getSelectedFiles();

            // try to restore the current directory
            File oldCurrentDirectory = fileChooser.getCurrentDirectory();
            if (oldCurrentDirectory == null) {
                fileChooser.setCurrentDirectory(root);
            } else {
                String currentPath = oldCurrentDirectory.getAbsolutePath();
                try {
                    File newCurrentDirectory =
                            root.getLongestMatch(currentPath);
                    fileChooser.setCurrentDirectory(newCurrentDirectory);
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }

            filesCardLayout.show(filesCardPanel, "fileChooser");
        }
    }

    private void deleteBackups() {

        final int selectedIndex = backupsList.getSelectedIndex();

        if (selectedIndex == 0) {
            // the user selected to delete all backups
            int returnValue = JOptionPane.showConfirmDialog(this,
                    BUNDLE.getString("Delete_All_Backups_Warning"),
                    BUNDLE.getString("Warning"),
                    JOptionPane.WARNING_MESSAGE);
            if (returnValue == JOptionPane.OK_OPTION) {
                final ProgressDialog dialog =
                        new ProgressDialog(parentWindow, processExecutor);
                dialog.setCancelButtonVisible(false);
                dialog.setMessage(BUNDLE.getString(
                        "Removing_Selected_Backups"));
                final ModalDialogHandler dialogHandler =
                        new ModalDialogHandler(dialog);

                SwingWorker swingWorker = new SwingWorker() {

                    @Override
                    protected Object doInBackground() {
                        // delete all files in selected backup directory
                        // do NOT(!) delete the backup directory itself, it
                        // might still be in the users config and could be
                        // reused
                        File backupDirectory = new File(selectedDirectory);
                        for (File file : backupDirectory.listFiles()) {
                            if (file.isDirectory()) {
                                deleteDirectory(file);
                            } else {
                                file.delete();
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void done() {
                        dialogHandler.hide();
                    }
                };
                swingWorker.execute();
                dialogHandler.show();

                // we are pretty sure that by now this is no longer a valid
                // rdiff-backup directory...
                dirCheckInfo("Warning_No_Backup_Directory");
            }

        } else {
            // the user selected to delete a backup increment
            final Increment previousIncrement =
                    increments.get(selectedIndex - 1);
            DateFormat dateFormat = DateFormat.getDateTimeInstance();
            Date timestamp = previousIncrement.getTimestamp();
            String formattedDate = dateFormat.format(timestamp);
            String warningMessage =
                    BUNDLE.getString("Delete_Increment_Warning");
            warningMessage =
                    MessageFormat.format(warningMessage, formattedDate);
            int returnValue = JOptionPane.showConfirmDialog(this,
                    warningMessage, BUNDLE.getString("Warning"),
                    JOptionPane.WARNING_MESSAGE);
            if (returnValue == JOptionPane.OK_OPTION) {
                final ProgressDialog dialog =
                        new ProgressDialog(parentWindow, processExecutor);
                dialog.setCancelButtonVisible(false);
                dialog.setMessage(BUNDLE.getString(
                        "Removing_Selected_Backups"));
                final ModalDialogHandler dialogHandler =
                        new ModalDialogHandler(dialog);

                SwingWorker swingWorker = new SwingWorker() {

                    @Override
                    protected Object doInBackground() {
                        // remove selected increments from rdiff-backup
                        // directory
                        String rdiffTimestamp =
                                previousIncrement.getRdiffTimestamp();
                        processExecutor.executeProcess("rdiff-backup",
                                "--force", "--remove-older-than",
                                rdiffTimestamp, selectedDirectory);
                        return null;
                    }

                    @Override
                    protected void done() {
                        dialogHandler.hide();
                        // remove increments from list
                        for (int i = increments.size() - 1;
                                i >= selectedIndex; i--) {
                            increments.remove(i);
                        }
                        incrementModel.changed();
                        backupsList.setSelectedIndex(increments.size() - 1);
                        // update free space information
                        FileTools.showSpaceInfo(new File(selectedDirectory),
                                storageUsageProgressBar);
                    }
                };
                swingWorker.execute();
                dialogHandler.show();
            }
        }
    }

    private void deleteDirectory(File directory) {
        // empty directory before deletion
        // (otherwise deletion just fails)
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                file.delete();
            }
        }
        // directory should be emtpy now
        directory.delete();
    }

    private void updateSizeInformation() {
        int selectedIndex = backupsList.getSelectedIndex();
        long cumulativeSize = 0;
        long totalSize = 0;
        boolean showCumulativeSize = true;
        boolean showTotalSize = true;
        for (int i = 0, j = increments.size(); i < j; i++) {
            Increment increment = increments.get(i);
            Long size = increment.getSize();
            if (size == null) {
                if (i >= selectedIndex) {
                    showCumulativeSize = false;
                }
                showTotalSize = false;
            } else {
                if (i >= selectedIndex) {
                    cumulativeSize += size;
                }
                totalSize += size;
            }
        }
        if (showCumulativeSize) {
            String text = FileTools.getDataVolumeString(cumulativeSize, 1);
            cumulativeSizeTextField.setText(text);
        } else {
            cumulativeSizeTextField.setText(null);
        }
        if (showTotalSize) {
            String text = FileTools.getDataVolumeString(totalSize, 1);
            totalSizeTextField.setText(text);
        } else {
            totalSizeTextField.setText(null);
        }
    }

    private void maybeShowPopup(MouseEvent e) {
        int index = backupsList.locationToIndex(e.getPoint());
        backupsList.setSelectedIndex(index);
        if (e.isPopupTrigger()) {
            backupsListPopupMenu.show(e.getComponent(), e.getX(), e.getY());
        }
    }

    /**
     * Restores selected files in a temporary directory and opens them in
     * Read-Only mode.
     */
    private void previewFiles() {
        try {
            // use temporary directory to restore files for preview
            // must *NOT* be java.io.tmpdir itself because rdiff-backup wants
            // to chmod this directory, which usually fails for non-root users
            String tmpPath = System.getProperty("java.io.tmpdir");
            File restoreDirectory = FileTools.createTempDirectory(
                    new File(tmpPath), "jbackpack_preview");
            restoreDirectory.deleteOnExit();

            RdiffBackupRestore rdiffBackupRestore = new RdiffBackupRestore();
            RdiffFile[] selectedFiles = this.getSelectedFiles();
            Increment increment = selectedFiles[0].getIncrement();

            // restore all selected files
            rdiffBackupRestore.restore(increment.getRdiffTimestamp(),
                    selectedFiles, increment.getBackupDirectory(),
                    restoreDirectory, tmpPath, false);

            // open all restored files
            for (File selectedFile : selectedFiles) {
                File restoredFile = new File(restoreDirectory,
                        selectedFile.getAbsolutePath());
                restoredFile.setReadOnly();
                restoredFile.deleteOnExit();
                try {
                    desktop.open(restoredFile);
                } catch (IOException ex) {
                    String errorMessage =
                            BUNDLE.getString("Error_Preview_File_Log");
                    errorMessage = MessageFormat.format(
                            errorMessage, restoredFile.getName());
                    LOGGER.log(Level.SEVERE, errorMessage, ex);
                    errorMessage =
                            BUNDLE.getString("Error_Preview_File_GUI");
                    errorMessage = MessageFormat.format(
                            errorMessage, restoredFile.getName(), ex);
                    showError(errorMessage);
                }
            }

        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            String errorMessage = BUNDLE.getString("Error_Preview");
            errorMessage = MessageFormat.format(errorMessage, ex);
            showError(errorMessage);
        }
    }

    private void showError(String errorMessage) {
        JOptionPane.showMessageDialog(parentWindow, errorMessage,
                BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private class IncrementModel extends DefaultListModel {

        @Override
        public int getSize() {
            return increments.size();
        }

        @Override
        public Object getElementAt(int index) {
            return increments.get(index);
        }

        public void changed() {
            fireContentsChanged(this, 0, increments.size() - 1);
        }
    }

    private class DirectoryChecker extends SwingWorker<Boolean, Void> {

        private final ProgressDialog dialog;
        private final ModalDialogHandler dialogHandler;
        private final File directory;
        private int returnValue;
        private List<String> rdiffBackupListOutput;

        public DirectoryChecker(ProgressDialog dialog,
                ModalDialogHandler dialogHandler, File directory) {
            this.dialog = dialog;
            this.dialogHandler = dialogHandler;
            this.directory = directory;
            dialog.setMessage(BUNDLE.getString("Checking_Directory"));
        }

        @Override
        protected Boolean doInBackground() {
            long start = System.currentTimeMillis();
            returnValue = processExecutor.executeProcess(true, true,
                    "rdiff-backup", "--parsable-output", "-l",
                    selectedDirectory);
            long time = System.currentTimeMillis() - start;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                        "calling \"rdiff-backup -l {0}\" took {1} ms",
                        new Object[]{selectedDirectory, time});
            }
            for (String s : processExecutor.getStdErrList()) {
                if (s.matches(".*Previous backup.*failed.*")) {
                    return false;
                }
            }
            rdiffBackupListOutput = processExecutor.getStdOutList();
            return true;
        }

        @Override
        protected void done() {
            try {
                if (get()) {
                    dialogHandler.hide();
                    if (returnValue == 0) {
                        String databasePath = null;

                        // check that database directory can be created
                        File rdiffBackupDataDir =
                                new File(directory, "rdiff-backup-data");
                        if (FileTools.canWrite(rdiffBackupDataDir)) {
                            databasePath = rdiffBackupDataDir.getPath()
                                    + File.separatorChar + "jbackpack";
                        } else {
                            // show warning, offer using temporary directory
                            String message = BUNDLE.getString(
                                    "Warning_Temporary_Database");
                            message = MessageFormat.format(
                                    message, rdiffBackupDataDir);
                            int selected = JOptionPane.showConfirmDialog(
                                    parentWindow, message,
                                    BUNDLE.getString("Warning"),
                                    JOptionPane.YES_NO_OPTION);
                            if (selected == JOptionPane.YES_OPTION) {
                                try {
                                    databasePath =
                                            FileTools.createTempDirectory(
                                            "jbackpack", null).getPath()
                                            + File.separatorChar + "jbackpack";
                                } catch (IOException ex) {
                                    LOGGER.log(Level.WARNING, null, ex);
                                    dirCheckError("Error_Database");
                                    return;
                                }
                            } else {
                                dirCheckError("Error_Database");
                                return;
                            }
                        }

                        DatabaseSyncer databaseSyncer = new DatabaseSyncer(
                                directory, databasePath, rdiffBackupListOutput);
                        LOGGER.fine("scheduling DatabaseSyncer for execution");
                        databaseSyncer.execute();
                    } else {
                        dirCheckInfo("Warning_No_Backup_Directory");
                    }
                } else {
                    dirCheckError("Error_Backup_Directory_Corrupted");
                    int selected = JOptionPane.showConfirmDialog(parentWindow,
                            BUNDLE.getString("Repair_Backup_Message"),
                            BUNDLE.getString("Repair_Backup_Title"),
                            JOptionPane.YES_NO_OPTION);
                    if (selected == JOptionPane.YES_OPTION) {
                        DirectoryFixer directoryFixer = new DirectoryFixer(
                                dialog, dialogHandler, directory);
                        LOGGER.info("scheduling directoryFixer for execution");
                        directoryFixer.execute();
                    } else {
                        dialogHandler.hide();
                    }
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    private class DirectoryFixer extends SwingWorker<Object, Object> {

        private final ProgressDialog dialog;
        private final ModalDialogHandler dialogHandler;
        private final File directory;

        public DirectoryFixer(ProgressDialog dialog,
                ModalDialogHandler dialogHandler, File directory) {
            this.dialog = dialog;
            this.dialogHandler = dialogHandler;
            this.directory = directory;
            dialog.setMessage(BUNDLE.getString(
                    "Repairing_Destination_Directory"));
        }

        @Override
        protected Object doInBackground() {
            processExecutor.executeProcess("rdiff-backup",
                    "--check-destination-dir", selectedDirectory);
            return null;
        }

        @Override
        protected void done() {
            DirectoryChecker directoryChecker = new DirectoryChecker(
                    dialog, dialogHandler, directory);
            LOGGER.info("scheduling directoryChecker for execution");
            directoryChecker.execute();
        }
    }

    private class DatabaseSyncer extends SwingWorker<Boolean, Object> {

        private final File backupDirectory;
        private final String databasePath;
        private final List<String> rdiffBackupListOutput;
        private final DatabaseSyncDialog dialog;
        private final ModalDialogHandler dialogHandler;

        public DatabaseSyncer(File backupDirectory, String databasePath,
                List<String> rdiffBackupListOutput) {
            this.backupDirectory = backupDirectory;
            this.databasePath = databasePath;
            this.rdiffBackupListOutput = rdiffBackupListOutput;
            dialog = new DatabaseSyncDialog(parentWindow);
            dialogHandler = new ModalDialogHandler(dialog);
            dialogHandler.show();
        }

        @Override
        protected Boolean doInBackground() {
            rdiffFileDatabase = RdiffFileDatabase.getInstance(
                    backupDirectory, databasePath, rdiffBackupListOutput);
            if (rdiffFileDatabase.isConnected()) {
                dialog.setDatabase(rdiffFileDatabase);
                try {
                    rdiffFileDatabase.sync();
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
                increments = rdiffFileDatabase.getIncrements();
                return true;
            }
            return false;
        }

        @Override
        protected void done() {
            LOGGER.fine("syncing database done");
            dialogHandler.hide();
            try {
                if (get()) {
                    if (increments.isEmpty()) {
                        LOGGER.log(Level.WARNING,
                                "no increments found in {0}", backupDirectory);
                        dirCheckInfo("Warning_No_Backup_Directory");
                    } else {
                        backupsList.clearSelection();
                        updateSizeInformation();
                        incrementModel.changed();
                        backupsList.setSelectedIndex(0);
                        cardLayout.show(
                                RdiffChooserPanel.this, "rdiffDirectoryPanel");
                    }
                } else {
                    if (rdiffFileDatabase.isAnotherInstanceRunning()) {
                        dirCheckError("Error_Database_In_Use");
                    } else {
                        dirCheckError("Error_Database");
                    }
                }
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JRadioButton allBackupRadioButton;
    private javax.swing.JRadioButton backupFilesRadioButton;
    private javax.swing.ButtonGroup backupSelectionGroup;
    private javax.swing.JPanel backupSizesPanel;
    private javax.swing.JList backupsList;
    private javax.swing.JPopupMenu backupsListPopupMenu;
    private javax.swing.JScrollPane backupsListScrollPane;
    private javax.swing.JPanel backupsPanel;
    private javax.swing.JLabel cumulativeSizeLabel;
    private javax.swing.JTextField cumulativeSizeTextField;
    private javax.swing.JButton deleteBackupButton;
    private javax.swing.JMenuItem deleteMenuItem;
    private javax.swing.JButton downButton;
    private javax.swing.JPanel emptyPanel;
    private javax.swing.JFileChooser fileChooser;
    private javax.swing.JPanel filesCardPanel;
    private javax.swing.JPanel filesPanel;
    private javax.swing.JLabel noRdiffDirectoryLabel;
    private javax.swing.JPanel noRdiffDirectoryPanel;
    private javax.swing.JButton previewButton;
    private javax.swing.JPanel rdiffDirectoryPanel;
    private javax.swing.JLabel selectIncrementLabel;
    private javax.swing.JPanel selectIncrementPanel;
    private javax.swing.JLabel storageUsageLabel;
    private javax.swing.JProgressBar storageUsageProgressBar;
    private javax.swing.JLabel totalSizeLabel;
    private javax.swing.JTextField totalSizeTextField;
    private javax.swing.JButton upButton;
    // End of variables declaration//GEN-END:variables
}
