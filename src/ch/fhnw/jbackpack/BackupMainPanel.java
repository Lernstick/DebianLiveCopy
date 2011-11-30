/*
 * BackupMainPanel.java
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
 * Created on Apr 12, 2010, 9:03:24 AM
 */
package ch.fhnw.jbackpack;

import ch.fhnw.jbackpack.chooser.ChrootFileSystemView;
import ch.fhnw.jbackpack.chooser.Increment;
import ch.fhnw.jbackpack.chooser.NoHiddenFilesSwingFileFilter;
import ch.fhnw.jbackpack.chooser.RdiffFile;
import ch.fhnw.jbackpack.chooser.RdiffFileDatabase;
import ch.fhnw.jbackpack.chooser.SelectBackupDirectoryDialog;
import ch.fhnw.util.AutoStarter;
import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.FileTools;
import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.OperatingSystem;
import ch.fhnw.util.ProcessExecutor;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.text.ChoiceFormat;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.TimeZone;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.DefaultComboBoxModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;

/**
 *
 * @author mw
 */
public class BackupMainPanel extends JPanel implements DocumentListener {

    /**
     * preferences key for the source directory
     */
    public static final String SOURCE = "source";
    /**
     * preferences key for the destination ("local", "ssh" or "smb")
     */
    public static final String DESTINATION = "destination";
    /**
     * preferences key for the local destination directory
     */
    public static final String LOCAL_DESTINATION_DIRECTORY =
            "local_destination_directory";
    /**
     * preferences key for the ssh server
     */
    public static final String SSH_SERVER = "ssh_server";
    /**
     * preferences key for the ssh user
     */
    public static final String SSH_USER = "ssh_user";
    /**
     * preferences key for the ssh base directory
     */
    public static final String SSH_BASE = "ssh_base";
    /**
     * preferences key for the ssh directory
     */
    public static final String SSH_DIRECTORY = "ssh_directory";
    /**
     * preferences key for the smb server
     */
    public static final String SMB_SERVER = "smb_server";
    /**
     * preferences key for the smb share
     */
    public static final String SMB_SHARE = "smb_share";
    /**
     * preferences key for the smb user
     */
    public static final String SMB_USER = "smb_user";
    /**
     * preferences key for the smb directory
     */
    public static final String SMB_DIRECTORY = "smb_directory";
    /**
     * preferences key for excluding files
     */
    public static final String EXCLUDES = "excludes";
    /**
     * preferences key for the list of excluded files
     */
    public static final String EXCLUDES_LIST = "excludes_list";
    /**
     * preferences key for including files
     */
    public static final String INCLUDES = "includes";
    /**
     * preferences key for the list of included files
     */
    public static final String INCLUDES_LIST = "includes_list";
    /**
     * preferences key for excluding symlinks
     */
    public static final String EXCLUDE_SYMLINKS = "exclude_symlinks";
    /**
     * preferences key for excluding fifos
     */
    public static final String EXCLUDE_FIFOS = "exclude_fifos";
    /**
     * preferences key for excluding other filesystems
     */
    public static final String EXCLUDE_OTHER_FILESYSTEMS =
            "exclude_other_filesystems";
    /**
     * preferences key for excluding device files
     */
    public static final String EXCLUDE_DEVICE_FILES = "exclude_device_files";
    /**
     * preferences key for excluding sockets
     */
    public static final String EXCLUDE_SOCKETS = "exclude_sockets";
    /**
     * preferences key for counting files before restoring them
     */
    public static final String COUNT_FILES = "count_files";
    /**
     * preferences key for password authentication
     */
    public static final String PASSWORD_AUTHENTICATION =
            "password_authentication";
    /**
     * preferences key for plain backup warning
     */
    public static final String PLAIN_BACKUP_WARNING =
            "show_plaintext_backup_warning_dialog";
    /**
     * preferences key for the temporary directory
     */
    public static final String TEMPORARY_DIRECTORY = "temporary_directory";
    /**
     * preferences key for compressing the backup
     */
    public static final String COMPRESS_BACKUP = "compress_backup";
    /**
     * preferences key for excluding large files
     */
    public static final String EXCLUDE_LARGE_FILES = "exclude_large_files";
    /**
     * preferences key for the maximum file size
     */
    public static final String MAX_FILE_SIZE = "maximum_file_size";
    /**
     * preferences key for the maximum file size unit
     */
    public static final String MAX_FILE_SIZE_UNIT = "maximum_file_size_unit";
    /**
     * preferences key for excluding small files
     */
    public static final String EXCLUDE_SMALL_FILES = "exclude_small_files";
    /**
     * preferences key for the minimum file size
     */
    public static final String MIN_FILE_SIZE = "minimum_file_size";
    /**
     * preferences key for the minimum file size unit
     */
    public static final String MIN_FILE_SIZE_UNIT = "minimum_file_size_unit";
    /**
     * preferences key for the automatic increment deletion by number
     */
    public static final String AUTO_DELETION_BY_NUMBER =
            "auto_deletion_by_number";
    /**
     * preferences key for the automatic increment deletion number
     */
    public static final String AUTO_DELETION_NUMBER =
            "auto_deletion_number";
    /**
     * preferences key for the automatic increment deletion by age
     */
    public static final String AUTO_DELETION_BY_AGE =
            "auto_deletion_by_age";
    /**
     * preferences key for the automatic increment deletion age
     */
    public static final String AUTO_DELETION_AGE =
            "auto_deletion_age";
    /**
     * preferences key for the automatic increment deletion age unit
     */
    public static final String AUTO_DELETION_AGE_UNIT =
            "auto_deletion_age_unit";
    /**
     * preferences key for the automatic increment deletion by space
     */
    public static final String AUTO_DELETION_BY_SPACE =
            "auto_deletion_by_space";
    /**
     * preferences key for the automatic increment deletion space
     */
    public static final String AUTO_DELETION_SPACE =
            "auto_deletion_space";
    /**
     * preferences key for the automatic increment deletion space unit
     */
    public static final String AUTO_DELETION_SPACE_UNIT =
            "auto_deletion_space_unit";
    private static final Logger LOGGER =
            Logger.getLogger(BackupMainPanel.class.getName());
    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private static final String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private static final String USER_HOME = System.getProperty("user.home");
    private static final String LOGIN = "login";
    private static final String LOGOUT = "logout";
    private static final String LOCK = "lock";
    private static final String UNLOCK = "unlock";
    private static final String ENCFS_SEARCH_STRING = "jbackpack_plain_";
    private static final NoHiddenFilesSwingFileFilter NO_HIDDEN_FILES_SWING_FILE_FILTER =
            NoHiddenFilesSwingFileFilter.getInstance();
    private final DateFormat timeFormat;
    private Frame parentFrame;
    private RdiffBackupRestore rdiffBackupRestore;
    private Map<String, JTextComponent> textComponentMap;
    private Map<String, JCheckBox> checkBoxMap;
    private Preferences preferences;
    private boolean processCancelled;
    private SwingWorker currentSwingWorker;
    private final ProcessExecutor processExecutor = new ProcessExecutor();
    private boolean sshfsMounted;
    private boolean smbfsMounted;
    private boolean destinationEncrypted;
    private String encfsMountPoint;
    private boolean commonDestinationOK;
    private boolean sshfsEnabled;
    private boolean smbfsEnabled = true;
    private boolean encfsEnabled;
    private boolean plainBackupWarning;
    private boolean showReminder;
    private int reminderTimeout;

    /** Creates new form BackupMainPanel */
    public BackupMainPanel() {

        // prepare processExecutor to always use the POSIX locale
        Map<String, String> environment = new HashMap<String, String>();
        environment.put("LC_ALL", "C");
        processExecutor.setEnvironment(environment);

        initComponents();
        systemCheck();

        sshLogInOutButton.setActionCommand(LOGIN);
        smbLogInOutButton.setActionCommand(LOGIN);
        lockButton.setActionCommand(UNLOCK);

        timeFormat = new SimpleDateFormat("HH:mm:ss");
        timeFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

        textComponentMap = new HashMap<String, JTextComponent>();
        textComponentMap.put(
                LOCAL_DESTINATION_DIRECTORY, localStorageTextField);
        textComponentMap.put(SSH_SERVER, sshServerTextField);
        textComponentMap.put(SSH_USER, sshUserNameTextField);
        textComponentMap.put(SSH_BASE, sshBaseDirTextField);
        textComponentMap.put(SSH_DIRECTORY, sshStorageTextField);
        textComponentMap.put(SMB_SERVER, smbServerTextField);
        textComponentMap.put(SMB_SHARE, smbShareTextField);
        textComponentMap.put(SMB_USER, smbUserTextField);
        textComponentMap.put(SMB_DIRECTORY, smbStorageTextField);
        textComponentMap.put(EXCLUDES_LIST, excludesTextArea);
        textComponentMap.put(INCLUDES_LIST, includesTextArea);
        textComponentMap.put(MAX_FILE_SIZE, maxSizeTextField);
        textComponentMap.put(MIN_FILE_SIZE, minSizeTextField);

        checkBoxMap = new HashMap<String, JCheckBox>();
        checkBoxMap.put(EXCLUDES, excludeCheckBox);
        checkBoxMap.put(INCLUDES, includesCheckBox);
        checkBoxMap.put(EXCLUDE_LARGE_FILES, maxSizeCheckBox);
        checkBoxMap.put(EXCLUDE_SMALL_FILES, minSizeCheckBox);
        checkBoxMap.put(
                EXCLUDE_OTHER_FILESYSTEMS, excludeOtherFileSystemsCheckBox);
        checkBoxMap.put(EXCLUDE_SYMLINKS, excludeSymlinksCheckBox);
        checkBoxMap.put(EXCLUDE_DEVICE_FILES, excludeDeviceFilesCheckBox);
        checkBoxMap.put(EXCLUDE_SOCKETS, excludeSocketsCheckBox);
        checkBoxMap.put(EXCLUDE_FIFOS, excludeFifosCheckBox);
        checkBoxMap.put(AUTO_DELETION_BY_NUMBER, autoDeleteNumberCheckBox);
        checkBoxMap.put(AUTO_DELETION_BY_AGE, autoDeleteAgeCheckBox);
        checkBoxMap.put(AUTO_DELETION_BY_SPACE, autoDeletionSpaceCheckBox);

        // the default tooltip delay is too short
        ToolTipManager.sharedInstance().setDismissDelay(10000);

        restoreErrorLabel.setIcon(IconManager.ERROR_ICON);
        backupErrorLabel.setIcon(IconManager.ERROR_ICON);
        encryptionErrorLabel.setIcon(IconManager.ERROR_ICON);

        // swing fix
        progressBar.setMinimumSize(progressBar.getPreferredSize());

        String[] sizeStrings = new String[]{
            BUNDLE.getString("Byte"),
            "KiB",
            "MiB",
            "GiB",
            "TiB"
        };
        maxSizeComboBox.setModel(new DefaultComboBoxModel(sizeStrings));
        minSizeComboBox.setModel(new DefaultComboBoxModel(sizeStrings));
        autoDeletionSpaceComboBox.setModel(
                new DefaultComboBoxModel(sizeStrings));

        Dimension preferredSize = autoDeleteNumberSpinner.getPreferredSize();
        preferredSize.width = 60;
        autoDeleteNumberSpinner.setPreferredSize(preferredSize);
        autoDeleteAgeSpinner.setPreferredSize(preferredSize);
        preferredSize = autoDeletionSpaceSpinner.getPreferredSize();
        preferredSize.width = 80;
        autoDeletionSpaceSpinner.setPreferredSize(preferredSize);

        String[] ageStrings = new String[]{
            BUNDLE.getString("Days"),
            BUNDLE.getString("Weeks"),
            BUNDLE.getString("Months"),
            BUNDLE.getString("Years")
        };
        autoDeleteAgeComboBox.setModel(new DefaultComboBoxModel(ageStrings));

        preferredSize = sourceDirSeparator1.getPreferredSize();
        preferredSize.width = 20;
        sourceDirSeparator1.setPreferredSize(preferredSize);
        destinationSeparator1.setPreferredSize(preferredSize);
        autoDeletionSeparator1.setPreferredSize(preferredSize);
        tempDirSeparator1.setPreferredSize(preferredSize);
        smbRemoteSeparator.setPreferredSize(preferredSize);
        smbLocalSeparator.setPreferredSize(preferredSize);

        switch (CurrentOperatingSystem.OS) {
            case Mac_OS_X:
            case Windows:
                // on Mac OS X and Windows we can mount SMB shares without a
                // sudo password, therefore we can hide some GUI elements
                smbRemoteHeaderPanel.setVisible(false);
                smbLocalHeaderPanel.setVisible(false);
                smbSudoPasswordLabel.setVisible(false);
                smbSudoPasswordField.setVisible(false);
        }
    }

    /**
     * adds the document listeners to some textfields, loads the preferences and
     * runs some initial checks
     */
    public void init() {
        localStorageTextField.getDocument().addDocumentListener(this);
        sshStorageTextField.getDocument().addDocumentListener(this);
        smbStorageTextField.getDocument().addDocumentListener(this);
        tempDirTextField.getDocument().addDocumentListener(this);

        // set preferences
        preferences = Preferences.userNodeForPackage(JBackpack.class);
        setPreferences();

        SwingUtilities.invokeLater(new Runnable() {

            public void run() {
                // some checks
                String sourcePath = backupSourceTextField.getText();
                File sourceDirectory = new File(sourcePath);
                if (!checkSourceCommon(sourcePath, sourceDirectory)
                        || !checkDestinationCommon()
                        || !checkSourceBackup(sourceDirectory)
                        || !checkSourceRestore(sourceDirectory)) {
                    mainTabbedPane.setSelectedComponent(directoriesPanel);
                    maybeUnlock(true);
                    return;
                }
                backupButton.requestFocusInWindow();
            }
        });
    }

    /**
     * sets the parent frame
     * @param parentFrame the parent frame
     */
    public void setParentFrame(Frame parentFrame) {
        this.parentFrame = parentFrame;
        rdiffChooserPanel.setParentWindow(parentFrame);
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
     * resets all settings to default values
     */
    public void clearSettings() {
        compressionCheckBox.setSelected(true);
        showReminder = false;
        reminderTimeout = 7;
        updateReminderTextField();
        excludeCheckBox.setSelected(false);
        excludesTextArea.setText(null);
        includesCheckBox.setSelected(false);
        includesTextArea.setText(null);
        excludeSymlinksCheckBox.setSelected(false);
        excludeFifosCheckBox.setSelected(false);
        excludeOtherFileSystemsCheckBox.setSelected(false);
        excludeDeviceFilesCheckBox.setSelected(false);
        excludeSocketsCheckBox.setSelected(false);
        countFilesCheckBox.setSelected(true);
        backupSourceTextField.setText(null);
        localStorageTextField.setText(null);
        sshStorageTextField.setText(null);
        localRadioButton.setSelected(true);
        sshUserNameTextField.setText(null);
        sshServerTextField.setText(null);
        sshPasswordField.setText(null);

        // update GUI
        mainTabbedPaneStateChanged(null);
    }

    /**
     * sets the preferences
     */
    public final void setPreferences() {
        // batch load of all text components
        for (String key : textComponentMap.keySet()) {
            textComponentMap.get(key).setText(preferences.get(key, ""));
        }

        // batch load of all checkboxes
        for (String key : checkBoxMap.keySet()) {
            boolean value = preferences.getBoolean(key, false);
            checkBoxMap.get(key).setSelected(value);
        }
        includesCheckBoxItemStateChanged(null);
        excludeCheckBoxItemStateChanged(null);

        autoDeleteNumberSpinner.setValue(
                preferences.getInt(AUTO_DELETION_NUMBER, 100));
        autoDeleteAgeSpinner.setValue(
                preferences.getInt(AUTO_DELETION_AGE, 1));
        autoDeletionSpaceSpinner.setValue(
                preferences.getInt(AUTO_DELETION_SPACE, 10));

        showReminder = preferences.getBoolean(JBackpack.SHOW_REMINDER, false);
        reminderTimeout = preferences.getInt(
                JBackpack.REMINDER_TIMEOUT, Integer.MIN_VALUE);
        updateReminderTextField();

        // special handling of backup source (defaults to user's home)
        String source = preferences.get(SOURCE, null);
        if (source == null) {
            userHomeRadioButton.setSelected(true);
        } else {
            otherSourceRadioButton.setSelected(true);
            backupSourceTextField.setText(source);
        }
        updateSourceDirState();

        String sshUser = sshUserNameTextField.getText();
        String sshServer = sshServerTextField.getText();
        try {
            setSshMounted(FileTools.isMounted(sshUser + '@' + sshServer + ':'));
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        try {
            setSmbMounted(getSmbfsMountPoint() != null);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        // !!! this must be called after setXXXMounted()!!!
        // otherwise the check functions triggered by the selection change will
        // return false results...
        String destination = preferences.get(DESTINATION, "local");
        if ("local".equals(destination)) {
            localRadioButton.setSelected(true);
        } else if ("ssh".equals(destination)) {
            if (sshfsEnabled) {
                sshRadioButton.setSelected(true);
            } else {
                localRadioButton.setSelected(true);
            }
        } else if ("smb".equals(destination)) {
            if (smbfsEnabled) {
                smbRadioButton.setSelected(true);
            } else {
                localRadioButton.setSelected(true);
            }
        }

        if (preferences.getBoolean(PASSWORD_AUTHENTICATION, true)) {
            sshPasswordRadioButton.setSelected(true);
        } else {
            sshPublicKeyRadioButton.setSelected(true);
        }

        countFilesCheckBox.setSelected(
                preferences.getBoolean(COUNT_FILES, true));

        plainBackupWarning = preferences.getBoolean(PLAIN_BACKUP_WARNING, true);

        String temporaryDirectory = preferences.get(TEMPORARY_DIRECTORY, null);
        if (temporaryDirectory == null) {
            customTempDirRadioButton.setSelected(false);
        } else {
            customTempDirRadioButton.setSelected(true);
            tempDirTextField.setText(temporaryDirectory);
        }
        updateTempDirState();

        compressionCheckBox.setSelected(
                preferences.getBoolean(COMPRESS_BACKUP, true));

        maxSizeComboBox.setSelectedIndex(
                preferences.getInt(MAX_FILE_SIZE_UNIT, 0));
        minSizeComboBox.setSelectedIndex(
                preferences.getInt(MIN_FILE_SIZE_UNIT, 0));
        autoDeleteAgeComboBox.setSelectedIndex(
                preferences.getInt(AUTO_DELETION_AGE_UNIT, 3));
        autoDeletionSpaceComboBox.setSelectedIndex(
                preferences.getInt(AUTO_DELETION_SPACE_UNIT, 3));

        // update GUI
        mainTabbedPaneStateChanged(null);
    }

    /**
     * saves the currently configured preferences
     */
    public void savePreferences() {
        // batch save for all text components
        for (String key : textComponentMap.keySet()) {
            preferences.put(key, textComponentMap.get(key).getText());
        }

        // batch save for all checkboxes
        for (String key : checkBoxMap.keySet()) {
            preferences.putBoolean(key, checkBoxMap.get(key).isSelected());
        }

        // non-batch components
        preferences.putBoolean(COMPRESS_BACKUP,
                compressionCheckBox.isSelected());
        preferences.putInt(AUTO_DELETION_NUMBER,
                ((Number) autoDeleteNumberSpinner.getValue()).intValue());
        preferences.putInt(AUTO_DELETION_AGE,
                ((Number) autoDeleteAgeSpinner.getValue()).intValue());
        preferences.putInt(AUTO_DELETION_SPACE,
                ((Number) autoDeletionSpaceSpinner.getValue()).intValue());
        if (localRadioButton.isSelected()) {
            preferences.put(DESTINATION, "local");
        } else if (sshRadioButton.isSelected()) {
            preferences.put(DESTINATION, "ssh");
        } else if (smbRadioButton.isSelected()) {
            preferences.put(DESTINATION, "smb");
        }

        if (userHomeRadioButton.isSelected()) {
            preferences.remove(SOURCE);
        } else {
            preferences.put(SOURCE, backupSourceTextField.getText());
        }

        preferences.putBoolean(
                PASSWORD_AUTHENTICATION, sshPasswordRadioButton.isSelected());

        preferences.putBoolean(COUNT_FILES, countFilesCheckBox.isSelected());
        preferences.putBoolean(PLAIN_BACKUP_WARNING, plainBackupWarning);

        if (defaultTempDirRadioButton.isSelected()) {
            preferences.remove(TEMPORARY_DIRECTORY);
        } else {
            preferences.put(TEMPORARY_DIRECTORY, tempDirTextField.getText());
        }
        preferences.putBoolean(JBackpack.SHOW_REMINDER, showReminder);
        preferences.putInt(JBackpack.REMINDER_TIMEOUT, reminderTimeout);

        preferences.putInt(MAX_FILE_SIZE_UNIT,
                maxSizeComboBox.getSelectedIndex());
        preferences.putInt(MIN_FILE_SIZE_UNIT,
                minSizeComboBox.getSelectedIndex());
        preferences.putInt(AUTO_DELETION_AGE_UNIT,
                autoDeleteAgeComboBox.getSelectedIndex());
        preferences.putInt(AUTO_DELETION_SPACE_UNIT,
                autoDeletionSpaceComboBox.getSelectedIndex());
    }

    /**
     * returns the encfs mountpoint
     * @return the encfs mountpoint
     */
    public String getEncfsMountPoint() {
        return encfsMountPoint;
    }

    /**
     * sets the encfs mountpoint
     * @param encfsMountPoint the encfs mountpoint to set
     */
    public void setEncfsMountPoint(String encfsMountPoint) {
        this.encfsMountPoint = encfsMountPoint;
    }

    /**
     * sets the encryptionn state of the destination
     * @param destinationEncrypted if <tt>true</tt>, the destination is
     * encrypted, otherwise non-encrypted
     */
    public void setDestinationEncrypted(boolean destinationEncrypted) {
        this.destinationEncrypted = destinationEncrypted;
    }

    /**
     * executes common checks for the destination directory
     * @return <tt>true</tt> if all checks succeeded, <tt>false</tt> otherwise
     */
    public boolean checkDestinationCommon() {
        String sourcePath = backupSourceTextField.getText();
        File sourceDirectory = new File(sourcePath);

        commonDestinationOK = false;
        destinationEncrypted = false;

        /**
         * the order of the following checks matters!
         * 1) check SSHFS
         * 2) check ENCFS
         * 3) all the rest...
         */
        // sshfs checks
        if (sshRadioButton.isSelected()) {
            if (!sshfsMounted) {
                String directoriesTabName = BUNDLE.getString(
                        "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
                showErrorPanels("Error_Not_Logged_In", directoriesTabName);
                encryptionErrorLabel.setText(BUNDLE.getString("Not_Logged_In"));
                showCard(encryptionCardPanel, "encryptionErrorPanel");
                return false;
            }
            String remoteStorage = sshStorageTextField.getText();
            if (remoteStorage.length() == 0) {
                String directoriesTabName = BUNDLE.getString(
                        "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
                showErrorPanels("Error_No_Destination_Directory_Long",
                        directoriesTabName);
                encryptionErrorLabel.setText(BUNDLE.getString(
                        "Error_No_Destination_Directory"));
                showCard(encryptionCardPanel, "encryptionErrorPanel");
                return false;
            }
        }

        // smbfs checks
        if (smbRadioButton.isSelected()) {
            if (!smbfsMounted) {
                String directoriesTabName = BUNDLE.getString(
                        "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
                showErrorPanels("Error_Not_Logged_In", directoriesTabName);
                encryptionErrorLabel.setText(BUNDLE.getString("Not_Logged_In"));
                showCard(encryptionCardPanel, "encryptionErrorPanel");
                return false;
            }
            String remoteStorage = smbStorageTextField.getText();
            if (remoteStorage.length() == 0) {
                String directoriesTabName = BUNDLE.getString(
                        "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
                showErrorPanels("Error_No_Destination_Directory_Long",
                        directoriesTabName);
                encryptionErrorLabel.setText(BUNDLE.getString(
                        "Error_No_Destination_Directory"));
                showCard(encryptionCardPanel, "encryptionErrorPanel");
                return false;
            }
        }

        // encfs checks
        String destinationPath = getRawBackupDestination();
        if ((CurrentOperatingSystem.OS != OperatingSystem.Windows)
                && encfsEnabled
                && FileTools.isEncFS(destinationPath)) {
            showCard(encryptionCardPanel, "unlockPanel");
            destinationEncrypted = true;
            try {
                encfsMountPoint =
                        FileTools.getEncfsMountPoint(ENCFS_SEARCH_STRING);
                destinationPath = encfsMountPoint;
            } catch (IOException ex) {
                encfsMountPoint = null;
                LOGGER.log(Level.WARNING, null, ex);
            }
            if (encfsMountPoint == null) {
                updateLockButton(false);
                String directoriesTabName = BUNDLE.getString(
                        "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
                showErrorPanels("Error_Destination_Locked", directoriesTabName);
                return false;
            } else {
                updateLockButton(true);
            }
        } else {
            showCard(encryptionCardPanel, "encryptionPanel");
        }

        File destinationDirectory = null;
        if (destinationPath != null) {
            destinationDirectory = new File(destinationPath);
        }

        // file checks
        if (destinationDirectory == null
                || destinationDirectory.getPath().length() == 0) {
            String directoriesTabName = BUNDLE.getString(
                    "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
            showErrorPanels("Error_No_Destination_Directory_Long",
                    directoriesTabName);
            encryptionErrorLabel.setText(BUNDLE.getString(
                    "Error_No_Destination_Directory"));
            showCard(encryptionCardPanel, "encryptionErrorPanel");
            return false;
        }
        if (!destinationDirectory.exists()) {
            showErrorPanels("Error_Destination_Directory_Does_Not_Exist");
            encryptionErrorLabel.setText(BUNDLE.getString(
                    "Error_Destination_Directory_Does_Not_Exist"));
            showCard(encryptionCardPanel, "encryptionErrorPanel");
            return false;
        }
        if (!destinationDirectory.isDirectory()) {
            showErrorPanels("Error_Destination_No_Directory");
            encryptionErrorLabel.setText(BUNDLE.getString(
                    "Error_Destination_No_Directory"));
            showCard(encryptionCardPanel, "encryptionErrorPanel");
            return false;
        }
        if (!destinationDirectory.canRead()) {
            showErrorPanels("Error_Destination_Directory_Unreadable");
            encryptionErrorLabel.setText(BUNDLE.getString(
                    "Error_Destination_Directory_Unreadable"));
            showCard(encryptionCardPanel, "encryptionErrorPanel");
            return false;
        }
        if (sourceDirectory.equals(destinationDirectory)) {
            showErrorPanels("Error_Source_Equals_Destination");
            return false;
        }

        commonDestinationOK = true;
        return true;
    }

    /**
     * tries to unlock the destination directory if it is ecnrypted and locked
     * @param switchToBackup if <code>true</code>, the GUI switches to the
     * backup tab when unlocking was successful
     */
    public void maybeUnlock(boolean switchToBackup) {
        if (destinationEncrypted && (encfsMountPoint == null)) {
            unlock(switchToBackup);
        }
        mainTabbedPaneStateChanged(null);
    }

    /**
     * runs the backup process
     * @param minFileSize the minimal file size
     * @param maxFileSize the maximal file size
     * @param directSSH if <code>true</code>, SSH is used directly, not via
     * a virtual file system
     * @param sshPassword the SSH password
     */
    public void runBackup(Long minFileSize, Long maxFileSize,
            boolean directSSH, String sshPassword) {

        String sourcePath = backupSourceTextField.getText();
        String destinationPath = getBackupDestination();

        // prepare backup operation
        processCancelled = false;
        progressLabel.setText(BUNDLE.getString("Backing_Up_Files"));
        filenameLabel.setText(null);
        shutdownCheckBox.setText(BUNDLE.getString("Shutdown_After_Backup"));
        showCard(this, "progressPanel");
        cancelButton.requestFocusInWindow();
        rdiffBackupRestore = new RdiffBackupRestore();
        timeLabel.setText(timeFormat.format(new Date(0)));

        // execute backup operation outside of the Swing Event Thread
        currentSwingWorker = new BackupSwingWorker(sourcePath, destinationPath,
                maxFileSize, minFileSize, directSSH, sshPassword);
        currentSwingWorker.execute();
    }

    /**
     * returns <tt>true</tt> if the user selected to show a warning dialog, when
     * the destination directory is not encrypted<tt>false</tt> otherwise
     * @return <tt>true</tt> if the user selected to show a warning dialog, when
     * the destination directory is not encrypted<tt>false</tt> otherwise
     */
    public boolean isPlainBackupWarningSelected() {
        return plainBackupWarning;
    }

    /**
     * defines if the user selected to show a warning dialog, when
     * the destination directory is not encrypted
     * @param plainBackupWarning <tt>true</tt> if the user selected to show a
     * warning dialog, when the destination directory is not encrypted
     * <tt>false</tt> otherwise
     */
    public void setPlainBackupWarning(boolean plainBackupWarning) {
        this.plainBackupWarning = plainBackupWarning;
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

        restoreLocationButtonGroup = new javax.swing.ButtonGroup();
        sourceDirButtonGroup = new javax.swing.ButtonGroup();
        destinationLocationButtonGroup = new javax.swing.ButtonGroup();
        authenticationButtonGroup = new javax.swing.ButtonGroup();
        tempDirButtonGroup = new javax.swing.ButtonGroup();
        mainTabbedPane = new javax.swing.JTabbedPane();
        backupCardPanel = new javax.swing.JPanel();
        backupPanel = new javax.swing.JPanel();
        backupButton = new javax.swing.JButton();
        separator1 = new javax.swing.JSeparator();
        backupConfigPanel = new javax.swing.JPanel();
        backupCheckBoxPanel = new javax.swing.JPanel();
        compressionCheckBox = new javax.swing.JCheckBox();
        excludeCheckBox = new javax.swing.JCheckBox();
        backupReminderPanel = new javax.swing.JPanel();
        reminderLabel = new javax.swing.JLabel();
        reminderTextField = new javax.swing.JTextField();
        reminderButton = new javax.swing.JButton();
        backupConfigCardPanel = new javax.swing.JPanel();
        excludesPanel = new javax.swing.JPanel();
        filePatternsPanel = new javax.swing.JPanel();
        excludesScrollPane = new javax.swing.JScrollPane();
        excludesTextArea = new javax.swing.JTextArea();
        addExcludesButton = new javax.swing.JButton();
        includesCheckBox = new javax.swing.JCheckBox();
        includesPanel = new javax.swing.JPanel();
        includesScrollPane = new javax.swing.JScrollPane();
        includesTextArea = new javax.swing.JTextArea();
        addIncludesButton = new javax.swing.JButton();
        checkBoxPanel = new javax.swing.JPanel();
        maxSizeCheckBox = new javax.swing.JCheckBox();
        maxSizeTextField = new javax.swing.JTextField();
        maxSizeComboBox = new javax.swing.JComboBox();
        minSizeCheckBox = new javax.swing.JCheckBox();
        minSizeTextField = new javax.swing.JTextField();
        minSizeComboBox = new javax.swing.JComboBox();
        excludeOtherFileSystemsCheckBox = new javax.swing.JCheckBox();
        excludeSymlinksCheckBox = new javax.swing.JCheckBox();
        excludeDeviceFilesCheckBox = new javax.swing.JCheckBox();
        excludeSocketsCheckBox = new javax.swing.JCheckBox();
        excludeFifosCheckBox = new javax.swing.JCheckBox();
        noExcludesPanel = new javax.swing.JPanel();
        backupErrorPanel = new javax.swing.JPanel();
        backupErrorLabel = new javax.swing.JLabel();
        restoreCardPanel = new javax.swing.JPanel();
        restorePanel = new javax.swing.JPanel();
        rdiffChooserPanel = new ch.fhnw.jbackpack.chooser.RdiffChooserPanel();
        countFilesCheckBox = new javax.swing.JCheckBox();
        restoreButton = new javax.swing.JButton();
        restoreButtonPanel = new javax.swing.JPanel();
        sourceDirectoryRadioButton = new javax.swing.JRadioButton();
        otherDirectoryRadioButton = new javax.swing.JRadioButton();
        restoreErrorPanel = new javax.swing.JPanel();
        restoreErrorLabel = new javax.swing.JLabel();
        directoriesPanel = new javax.swing.JPanel();
        sourceDirHeaderPanel = new javax.swing.JPanel();
        sourceDirSeparator1 = new javax.swing.JSeparator();
        backupSourceLabel = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();
        sourceDirRadioButtonPanel = new javax.swing.JPanel();
        userHomeRadioButton = new javax.swing.JRadioButton();
        otherSourceRadioButton = new javax.swing.JRadioButton();
        sourceDirDetailsPanel = new javax.swing.JPanel();
        backupSourceTextField = new javax.swing.JTextField();
        backupSourceButton = new javax.swing.JButton();
        destinationDirHeaderPanel = new javax.swing.JPanel();
        destinationSeparator1 = new javax.swing.JSeparator();
        backupDestinationLabel = new javax.swing.JLabel();
        jSeparator4 = new javax.swing.JSeparator();
        destinationRadioButtonPanel = new javax.swing.JPanel();
        localRadioButton = new javax.swing.JRadioButton();
        sshRadioButton = new javax.swing.JRadioButton();
        smbRadioButton = new javax.swing.JRadioButton();
        destinationCardPanel = new javax.swing.JPanel();
        localStoragePanel = new javax.swing.JPanel();
        localStorageTextField = new javax.swing.JTextField();
        localStorageButton = new javax.swing.JButton();
        jPanel1 = new javax.swing.JPanel();
        sshStoragePanel = new javax.swing.JPanel();
        sshServerPanel = new javax.swing.JPanel();
        sshServerLabel = new javax.swing.JLabel();
        sshServerTextField = new javax.swing.JTextField();
        sshUserNameLabel = new javax.swing.JLabel();
        sshUserNameTextField = new javax.swing.JTextField();
        sshBaseDirLabel = new javax.swing.JLabel();
        sshBaseDirTextField = new javax.swing.JTextField();
        sshAuthenticationPanel = new javax.swing.JPanel();
        sshPasswordRadioButton = new javax.swing.JRadioButton();
        sshPasswordField = new javax.swing.JPasswordField();
        sshPublicKeyRadioButton = new javax.swing.JRadioButton();
        sshLogInOutButton = new javax.swing.JButton();
        sshLoginProgressBar = new javax.swing.JProgressBar();
        sshStorageLabel = new javax.swing.JLabel();
        sshStorageTextField = new javax.swing.JTextField();
        sshStorageButton = new javax.swing.JButton();
        jPanel3 = new javax.swing.JPanel();
        smbStoragePanel = new javax.swing.JPanel();
        smbRemoteHeaderPanel = new javax.swing.JPanel();
        smbRemoteSeparator = new javax.swing.JSeparator();
        smbRemoteLabel = new javax.swing.JLabel();
        jSeparator3 = new javax.swing.JSeparator();
        smbServerLabel = new javax.swing.JLabel();
        smbServerTextField = new javax.swing.JTextField();
        smbShareLabel = new javax.swing.JLabel();
        smbShareTextField = new javax.swing.JTextField();
        smbUserLabel = new javax.swing.JLabel();
        smbUserTextField = new javax.swing.JTextField();
        smbPasswordLabel = new javax.swing.JLabel();
        smbPasswordField = new javax.swing.JPasswordField();
        smbLocalHeaderPanel = new javax.swing.JPanel();
        smbLocalSeparator = new javax.swing.JSeparator();
        smbLocalLabel = new javax.swing.JLabel();
        jSeparator5 = new javax.swing.JSeparator();
        smbSudoPasswordLabel = new javax.swing.JLabel();
        smbSudoPasswordField = new javax.swing.JPasswordField();
        smbLoginPanel = new javax.swing.JPanel();
        smbLogInOutButton = new javax.swing.JButton();
        smbLoginProgressBar = new javax.swing.JProgressBar();
        smbStorageLabel = new javax.swing.JLabel();
        smbStorageTextField = new javax.swing.JTextField();
        smbStorageButton = new javax.swing.JButton();
        jPanel2 = new javax.swing.JPanel();
        encryptionCardPanel = new javax.swing.JPanel();
        encryptionErrorPanel = new javax.swing.JPanel();
        encryptionErrorLabel = new javax.swing.JLabel();
        encryptionPanel = new javax.swing.JPanel();
        encryptionButton = new javax.swing.JButton();
        unlockPanel = new javax.swing.JPanel();
        lockButton = new javax.swing.JButton();
        changePasswordButton = new javax.swing.JButton();
        decryptionButton = new javax.swing.JButton();
        advancedSettingsPanel = new javax.swing.JPanel();
        autoDeletionHeaderPanel = new javax.swing.JPanel();
        autoDeletionSeparator1 = new javax.swing.JSeparator();
        autoDeletionLabel = new javax.swing.JLabel();
        jSeparator8 = new javax.swing.JSeparator();
        autoDeletionNumberPanel = new javax.swing.JPanel();
        autoDeleteNumberCheckBox = new javax.swing.JCheckBox();
        autoDeleteNumberSpinner = new javax.swing.JSpinner();
        autoDeleteNumberLabel = new javax.swing.JLabel();
        autoDeletionAgePanel = new javax.swing.JPanel();
        autoDeleteAgeCheckBox = new javax.swing.JCheckBox();
        autoDeleteAgeSpinner = new javax.swing.JSpinner();
        autoDeleteAgeComboBox = new javax.swing.JComboBox();
        autoDeletionSpacePanel = new javax.swing.JPanel();
        autoDeletionSpaceCheckBox = new javax.swing.JCheckBox();
        autoDeletionSpaceSpinner = new javax.swing.JSpinner();
        autoDeletionSpaceComboBox = new javax.swing.JComboBox();
        tempDirHeaderPanel = new javax.swing.JPanel();
        tempDirSeparator1 = new javax.swing.JSeparator();
        tempDirLabel = new javax.swing.JLabel();
        jSeparator6 = new javax.swing.JSeparator();
        tempDirRadioButtonPanel = new javax.swing.JPanel();
        defaultTempDirRadioButton = new javax.swing.JRadioButton();
        customTempDirRadioButton = new javax.swing.JRadioButton();
        tempDirDetailsPanel = new javax.swing.JPanel();
        tempDirTextField = new javax.swing.JTextField();
        tempDirBrowseButton = new javax.swing.JButton();
        storageUsageLabel = new javax.swing.JLabel();
        storageUsageProgressBar = new javax.swing.JProgressBar();
        progressPanel = new javax.swing.JPanel();
        progressLabel = new javax.swing.JLabel();
        filenameLabel = new JSqueezedLabel();
        progressBar = new javax.swing.JProgressBar();
        timeLabel = new javax.swing.JLabel();
        shutdownPanel = new javax.swing.JPanel();
        shutdownCheckBox = new javax.swing.JCheckBox();
        shutdownLabel = new javax.swing.JLabel();
        shutdownPasswordField = new javax.swing.JPasswordField();
        cancelButton = new javax.swing.JButton();
        sessionStatisticsPanel = new javax.swing.JPanel();
        statisticsLabel = new javax.swing.JLabel();
        statisticsTextFieldScrollPane = new javax.swing.JScrollPane();
        statisticsTextField = new javax.swing.JTextArea();
        continueButton = new javax.swing.JButton();
        quitButton = new javax.swing.JButton();
        backupRestoredPanel = new javax.swing.JPanel();
        restoredLabel = new javax.swing.JLabel();
        restoredOKButton = new javax.swing.JButton();

        setLayout(new java.awt.CardLayout());

        mainTabbedPane.setName("mainTabbedPane"); // NOI18N
        mainTabbedPane.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                mainTabbedPaneStateChanged(evt);
            }
        });

        backupCardPanel.setName("backupCardPanel"); // NOI18N
        backupCardPanel.setLayout(new java.awt.CardLayout());

        backupPanel.setLayout(new java.awt.GridBagLayout());

        backupButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/32x32/kdat_backup.png"))); // NOI18N
        backupButton.setMnemonic(java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings").getString("BackupMainPanel.backupButton.mnemonic").charAt(0));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        backupButton.setText(bundle.getString("BackupMainPanel.backupButton.text")); // NOI18N
        backupButton.setName("backupButton"); // NOI18N
        backupButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupButtonActionPerformed(evt);
            }
        });
        backupButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                backupButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                backupButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 0, 5);
        backupPanel.add(backupButton, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        backupPanel.add(separator1, gridBagConstraints);

        backupConfigPanel.setLayout(new java.awt.GridBagLayout());

        backupCheckBoxPanel.setLayout(new java.awt.GridBagLayout());

        compressionCheckBox.setSelected(true);
        compressionCheckBox.setText(bundle.getString("BackupMainPanel.compressionCheckBox.text")); // NOI18N
        compressionCheckBox.setToolTipText(bundle.getString("BackupMainPanel.compressionCheckBox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        backupCheckBoxPanel.add(compressionCheckBox, gridBagConstraints);

        excludeCheckBox.setText(bundle.getString("BackupMainPanel.excludeCheckBox.text")); // NOI18N
        excludeCheckBox.setName("excludeCheckBox"); // NOI18N
        excludeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                excludeCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 0);
        backupCheckBoxPanel.add(excludeCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 15, 0, 0);
        backupConfigPanel.add(backupCheckBoxPanel, gridBagConstraints);

        backupReminderPanel.setLayout(new java.awt.GridBagLayout());

        reminderLabel.setText(bundle.getString("BackupMainPanel.reminderLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 0);
        backupReminderPanel.add(reminderLabel, gridBagConstraints);

        reminderTextField.setColumns(10);
        reminderTextField.setEditable(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 0);
        backupReminderPanel.add(reminderTextField, gridBagConstraints);

        reminderButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/configure.png"))); // NOI18N
        reminderButton.setToolTipText(bundle.getString("BackupMainPanel.reminderButton.toolTipText")); // NOI18N
        reminderButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        reminderButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                reminderButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 0);
        backupReminderPanel.add(reminderButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        backupConfigPanel.add(backupReminderPanel, gridBagConstraints);

        backupConfigCardPanel.setLayout(new java.awt.CardLayout());

        excludesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.excludesPanel.border.title"))); // NOI18N
        excludesPanel.setName("excludesPanel"); // NOI18N
        excludesPanel.setLayout(new java.awt.GridBagLayout());

        filePatternsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.filePatternsPanel.border.title"))); // NOI18N
        filePatternsPanel.setLayout(new java.awt.GridBagLayout());

        excludesScrollPane.setViewportView(excludesTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 0);
        filePatternsPanel.add(excludesScrollPane, gridBagConstraints);

        addExcludesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/add.png"))); // NOI18N
        addExcludesButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        addExcludesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addExcludesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        filePatternsPanel.add(addExcludesButton, gridBagConstraints);

        includesCheckBox.setText(bundle.getString("BackupMainPanel.includesCheckBox.text")); // NOI18N
        includesCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                includesCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        filePatternsPanel.add(includesCheckBox, gridBagConstraints);

        includesPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.includesPanel.border.title"))); // NOI18N
        includesPanel.setLayout(new java.awt.GridBagLayout());

        includesScrollPane.setViewportView(includesTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 2, 2, 0);
        includesPanel.add(includesScrollPane, gridBagConstraints);

        addIncludesButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/add.png"))); // NOI18N
        addIncludesButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        addIncludesButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addIncludesButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
        includesPanel.add(addIncludesButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 5);
        filePatternsPanel.add(includesPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        excludesPanel.add(filePatternsPanel, gridBagConstraints);

        checkBoxPanel.setLayout(new java.awt.GridBagLayout());

        maxSizeCheckBox.setText(bundle.getString("BackupMainPanel.maxSizeCheckBox.text")); // NOI18N
        maxSizeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                maxSizeCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(maxSizeCheckBox, gridBagConstraints);

        maxSizeTextField.setColumns(6);
        maxSizeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        maxSizeTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 15, 0, 0);
        checkBoxPanel.add(maxSizeTextField, gridBagConstraints);

        maxSizeComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 0, 5);
        checkBoxPanel.add(maxSizeComboBox, gridBagConstraints);

        minSizeCheckBox.setText(bundle.getString("BackupMainPanel.minSizeCheckBox.text")); // NOI18N
        minSizeCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                minSizeCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(minSizeCheckBox, gridBagConstraints);

        minSizeTextField.setColumns(6);
        minSizeTextField.setHorizontalAlignment(javax.swing.JTextField.TRAILING);
        minSizeTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(2, 15, 0, 0);
        checkBoxPanel.add(minSizeTextField, gridBagConstraints);

        minSizeComboBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(2, 5, 0, 5);
        checkBoxPanel.add(minSizeComboBox, gridBagConstraints);

        excludeOtherFileSystemsCheckBox.setText(bundle.getString("BackupMainPanel.excludeOtherFileSystemsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(excludeOtherFileSystemsCheckBox, gridBagConstraints);

        excludeSymlinksCheckBox.setText(bundle.getString("BackupMainPanel.excludeSymlinksCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(excludeSymlinksCheckBox, gridBagConstraints);

        excludeDeviceFilesCheckBox.setText(bundle.getString("BackupMainPanel.excludeDeviceFilesCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(excludeDeviceFilesCheckBox, gridBagConstraints);

        excludeSocketsCheckBox.setText(bundle.getString("BackupMainPanel.excludeSocketsCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        checkBoxPanel.add(excludeSocketsCheckBox, gridBagConstraints);

        excludeFifosCheckBox.setText(bundle.getString("BackupMainPanel.excludeFifosCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        checkBoxPanel.add(excludeFifosCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 5);
        excludesPanel.add(checkBoxPanel, gridBagConstraints);

        backupConfigCardPanel.add(excludesPanel, "excludesPanel");

        noExcludesPanel.setLayout(new java.awt.GridBagLayout());
        backupConfigCardPanel.add(noExcludesPanel, "noExcludesPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        backupConfigPanel.add(backupConfigCardPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        backupPanel.add(backupConfigPanel, gridBagConstraints);

        backupCardPanel.add(backupPanel, "backupPanel");

        backupErrorPanel.setLayout(new java.awt.GridBagLayout());

        backupErrorLabel.setText(bundle.getString("Error_Not_Logged_In")); // NOI18N
        backupErrorLabel.setName("backupErrorLabel"); // NOI18N
        backupErrorPanel.add(backupErrorLabel, new java.awt.GridBagConstraints());

        backupCardPanel.add(backupErrorPanel, "backupErrorPanel");

        mainTabbedPane.addTab(bundle.getString("BackupMainPanel.backupCardPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/kdat_backup.png")), backupCardPanel); // NOI18N

        restoreCardPanel.setName("restoreCardPanel"); // NOI18N
        restoreCardPanel.setLayout(new java.awt.CardLayout());

        restorePanel.setLayout(new java.awt.GridBagLayout());

        rdiffChooserPanel.setName("rdiffChooserPanel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        restorePanel.add(rdiffChooserPanel, gridBagConstraints);

        countFilesCheckBox.setText(bundle.getString("BackupMainPanel.countFilesCheckBox.text")); // NOI18N
        countFilesCheckBox.setToolTipText(bundle.getString("BackupMainPanel.countFilesCheckBox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        restorePanel.add(countFilesCheckBox, gridBagConstraints);

        restoreButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/32x32/kdat_restore.png"))); // NOI18N
        restoreButton.setMnemonic(java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings").getString("Restore.mnemonic").charAt(0));
        restoreButton.setText(bundle.getString("Restore")); // NOI18N
        restoreButton.setName("restoreButton"); // NOI18N
        restoreButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoreButtonActionPerformed(evt);
            }
        });
        restoreButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                restoreButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                restoreButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        restorePanel.add(restoreButton, gridBagConstraints);

        restoreButtonPanel.setLayout(new java.awt.GridBagLayout());

        restoreLocationButtonGroup.add(sourceDirectoryRadioButton);
        sourceDirectoryRadioButton.setSelected(true);
        sourceDirectoryRadioButton.setText(bundle.getString("BackupMainPanel.sourceDirectoryRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        restoreButtonPanel.add(sourceDirectoryRadioButton, gridBagConstraints);

        restoreLocationButtonGroup.add(otherDirectoryRadioButton);
        otherDirectoryRadioButton.setText(bundle.getString("BackupMainPanel.otherDirectoryRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        restoreButtonPanel.add(otherDirectoryRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 0);
        restorePanel.add(restoreButtonPanel, gridBagConstraints);

        restoreCardPanel.add(restorePanel, "restorePanel");

        restoreErrorPanel.setLayout(new java.awt.GridBagLayout());

        restoreErrorLabel.setText(bundle.getString("Error_Not_Logged_In")); // NOI18N
        restoreErrorPanel.add(restoreErrorLabel, new java.awt.GridBagConstraints());

        restoreCardPanel.add(restoreErrorPanel, "restoreErrorPanel");

        mainTabbedPane.addTab(bundle.getString("BackupMainPanel.restoreCardPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/kdat_restore.png")), restoreCardPanel); // NOI18N

        directoriesPanel.setName("directoriesPanel"); // NOI18N
        directoriesPanel.setLayout(new java.awt.GridBagLayout());

        sourceDirHeaderPanel.setLayout(new java.awt.GridBagLayout());
        sourceDirHeaderPanel.add(sourceDirSeparator1, new java.awt.GridBagConstraints());

        backupSourceLabel.setFont(backupSourceLabel.getFont().deriveFont(backupSourceLabel.getFont().getStyle() | java.awt.Font.BOLD));
        backupSourceLabel.setText(bundle.getString("BackupMainPanel.backupSourceLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        sourceDirHeaderPanel.add(backupSourceLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        sourceDirHeaderPanel.add(jSeparator2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        directoriesPanel.add(sourceDirHeaderPanel, gridBagConstraints);

        sourceDirRadioButtonPanel.setLayout(new java.awt.GridBagLayout());

        sourceDirButtonGroup.add(userHomeRadioButton);
        userHomeRadioButton.setSelected(true);
        userHomeRadioButton.setText(bundle.getString("BackupMainPanel.userHomeRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        sourceDirRadioButtonPanel.add(userHomeRadioButton, gridBagConstraints);

        sourceDirButtonGroup.add(otherSourceRadioButton);
        otherSourceRadioButton.setText(bundle.getString("BackupMainPanel.otherSourceRadioButton.text")); // NOI18N
        otherSourceRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                otherSourceRadioButtonItemStateChanged(evt);
            }
        });
        otherSourceRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                otherSourceRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        sourceDirRadioButtonPanel.add(otherSourceRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        directoriesPanel.add(sourceDirRadioButtonPanel, gridBagConstraints);

        sourceDirDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        sourceDirDetailsPanel.setLayout(new java.awt.GridBagLayout());

        backupSourceTextField.setName("backupSourceTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        sourceDirDetailsPanel.add(backupSourceTextField, gridBagConstraints);

        backupSourceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        backupSourceButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        backupSourceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupSourceButtonActionPerformed(evt);
            }
        });
        backupSourceButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                backupSourceButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                backupSourceButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        sourceDirDetailsPanel.add(backupSourceButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        directoriesPanel.add(sourceDirDetailsPanel, gridBagConstraints);

        destinationDirHeaderPanel.setLayout(new java.awt.GridBagLayout());
        destinationDirHeaderPanel.add(destinationSeparator1, new java.awt.GridBagConstraints());

        backupDestinationLabel.setFont(backupDestinationLabel.getFont().deriveFont(backupDestinationLabel.getFont().getStyle() | java.awt.Font.BOLD));
        backupDestinationLabel.setText(bundle.getString("BackupMainPanel.backupDestinationLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        destinationDirHeaderPanel.add(backupDestinationLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        destinationDirHeaderPanel.add(jSeparator4, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        directoriesPanel.add(destinationDirHeaderPanel, gridBagConstraints);

        destinationRadioButtonPanel.setLayout(new java.awt.GridBagLayout());

        destinationLocationButtonGroup.add(localRadioButton);
        localRadioButton.setSelected(true);
        localRadioButton.setText(bundle.getString("BackupMainPanel.localRadioButton.text")); // NOI18N
        localRadioButton.setName("localRadioButton"); // NOI18N
        localRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                localRadioButtonItemStateChanged(evt);
            }
        });
        localRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        destinationRadioButtonPanel.add(localRadioButton, gridBagConstraints);

        destinationLocationButtonGroup.add(sshRadioButton);
        sshRadioButton.setText(bundle.getString("BackupMainPanel.sshRadioButton.text")); // NOI18N
        sshRadioButton.setName("sshRadioButton"); // NOI18N
        sshRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sshRadioButtonItemStateChanged(evt);
            }
        });
        sshRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        destinationRadioButtonPanel.add(sshRadioButton, gridBagConstraints);

        destinationLocationButtonGroup.add(smbRadioButton);
        smbRadioButton.setText(bundle.getString("BackupMainPanel.smbRadioButton.text")); // NOI18N
        smbRadioButton.setName("smbRadioButton"); // NOI18N
        smbRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                smbRadioButtonItemStateChanged(evt);
            }
        });
        smbRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        destinationRadioButtonPanel.add(smbRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        directoriesPanel.add(destinationRadioButtonPanel, gridBagConstraints);

        destinationCardPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        destinationCardPanel.setName("destinationCardPanel"); // NOI18N
        destinationCardPanel.setLayout(new java.awt.CardLayout());

        localStoragePanel.setLayout(new java.awt.GridBagLayout());

        localStorageTextField.setName("localStorageTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        localStoragePanel.add(localStorageTextField, gridBagConstraints);

        localStorageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        localStorageButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        localStorageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                localStorageButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 5);
        localStoragePanel.add(localStorageButton, gridBagConstraints);

        jPanel1.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        localStoragePanel.add(jPanel1, gridBagConstraints);

        destinationCardPanel.add(localStoragePanel, "localStoragePanel");

        sshStoragePanel.setLayout(new java.awt.GridBagLayout());

        sshServerPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.sshServerPanel.border.title"))); // NOI18N
        sshServerPanel.setLayout(new java.awt.GridBagLayout());

        sshServerLabel.setText(bundle.getString("BackupMainPanel.sshServerLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        sshServerPanel.add(sshServerLabel, gridBagConstraints);

        sshServerTextField.setName("sshServerTextField"); // NOI18N
        sshServerTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshServerTextFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        sshServerPanel.add(sshServerTextField, gridBagConstraints);

        sshUserNameLabel.setText(bundle.getString("BackupMainPanel.sshUserNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        sshServerPanel.add(sshUserNameLabel, gridBagConstraints);

        sshUserNameTextField.setName("sshUserNameTextField"); // NOI18N
        sshUserNameTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshUserNameTextFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 10);
        sshServerPanel.add(sshUserNameTextField, gridBagConstraints);

        sshBaseDirLabel.setText(bundle.getString("BackupMainPanel.sshBaseDirLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
        sshServerPanel.add(sshBaseDirLabel, gridBagConstraints);

        sshBaseDirTextField.setName("sshBaseDirTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 10);
        sshServerPanel.add(sshBaseDirTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        sshStoragePanel.add(sshServerPanel, gridBagConstraints);

        sshAuthenticationPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.sshAuthenticationPanel.border.title"))); // NOI18N
        sshAuthenticationPanel.setLayout(new java.awt.GridBagLayout());

        authenticationButtonGroup.add(sshPasswordRadioButton);
        sshPasswordRadioButton.setSelected(true);
        sshPasswordRadioButton.setText(bundle.getString("BackupMainPanel.sshPasswordRadioButton.text")); // NOI18N
        sshPasswordRadioButton.setName("sshPasswordRadioButton"); // NOI18N
        sshPasswordRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                sshPasswordRadioButtonItemStateChanged(evt);
            }
        });
        sshPasswordRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshPasswordRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        sshAuthenticationPanel.add(sshPasswordRadioButton, gridBagConstraints);

        sshPasswordField.setName("sshPasswordField"); // NOI18N
        sshPasswordField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshPasswordFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 10);
        sshAuthenticationPanel.add(sshPasswordField, gridBagConstraints);

        authenticationButtonGroup.add(sshPublicKeyRadioButton);
        sshPublicKeyRadioButton.setText(bundle.getString("BackupMainPanel.sshPublicKeyRadioButton.text")); // NOI18N
        sshPublicKeyRadioButton.setName("sshPublicKeyRadioButton"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        sshAuthenticationPanel.add(sshPublicKeyRadioButton, gridBagConstraints);

        sshLogInOutButton.setText(bundle.getString("Login")); // NOI18N
        sshLogInOutButton.setName("sshLogInOutButton"); // NOI18N
        sshLogInOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshLogInOutButtonActionPerformed(evt);
            }
        });
        sshLogInOutButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                sshLogInOutButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                sshLogInOutButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        sshAuthenticationPanel.add(sshLogInOutButton, gridBagConstraints);

        sshLoginProgressBar.setName("sshLoginProgressBar"); // NOI18N
        sshLoginProgressBar.setString(bundle.getString("Not_Logged_In")); // NOI18N
        sshLoginProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 10);
        sshAuthenticationPanel.add(sshLoginProgressBar, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        sshStoragePanel.add(sshAuthenticationPanel, gridBagConstraints);

        sshStorageLabel.setText(bundle.getString("BackupMainPanel.sshStorageLabel.text")); // NOI18N
        sshStorageLabel.setEnabled(false);
        sshStorageLabel.setName("sshStorageLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 8, 5, 0);
        sshStoragePanel.add(sshStorageLabel, gridBagConstraints);

        sshStorageTextField.setEnabled(false);
        sshStorageTextField.setName("sshStorageTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        sshStoragePanel.add(sshStorageTextField, gridBagConstraints);

        sshStorageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        sshStorageButton.setEnabled(false);
        sshStorageButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        sshStorageButton.setName("sshStorageButton"); // NOI18N
        sshStorageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sshStorageButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        sshStoragePanel.add(sshStorageButton, gridBagConstraints);

        jPanel3.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.weighty = 1.0;
        sshStoragePanel.add(jPanel3, gridBagConstraints);

        destinationCardPanel.add(sshStoragePanel, "sshStoragePanel");

        smbStoragePanel.setLayout(new java.awt.GridBagLayout());

        smbRemoteHeaderPanel.setLayout(new java.awt.GridBagLayout());
        smbRemoteHeaderPanel.add(smbRemoteSeparator, new java.awt.GridBagConstraints());

        smbRemoteLabel.setFont(smbRemoteLabel.getFont().deriveFont(smbRemoteLabel.getFont().getStyle() | java.awt.Font.BOLD));
        smbRemoteLabel.setText(bundle.getString("BackupMainPanel.smbRemoteLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        smbRemoteHeaderPanel.add(smbRemoteLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        smbRemoteHeaderPanel.add(jSeparator3, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        smbStoragePanel.add(smbRemoteHeaderPanel, gridBagConstraints);

        smbServerLabel.setText(bundle.getString("BackupMainPanel.smbServerLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbStoragePanel.add(smbServerLabel, gridBagConstraints);

        smbServerTextField.setName("smbServerTextField"); // NOI18N
        smbServerTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbServerTextFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbStoragePanel.add(smbServerTextField, gridBagConstraints);

        smbShareLabel.setText(bundle.getString("BackupMainPanel.smbShareLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbStoragePanel.add(smbShareLabel, gridBagConstraints);

        smbShareTextField.setName("smbShareTextField"); // NOI18N
        smbShareTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbShareTextFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbStoragePanel.add(smbShareTextField, gridBagConstraints);

        smbUserLabel.setText(bundle.getString("BackupMainPanel.smbUserLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbStoragePanel.add(smbUserLabel, gridBagConstraints);

        smbUserTextField.setName("smbUserTextField"); // NOI18N
        smbUserTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbUserTextFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbStoragePanel.add(smbUserTextField, gridBagConstraints);

        smbPasswordLabel.setText(bundle.getString("BackupMainPanel.smbPasswordLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbStoragePanel.add(smbPasswordLabel, gridBagConstraints);

        smbPasswordField.setName("smbPasswordField"); // NOI18N
        smbPasswordField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbPasswordFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbStoragePanel.add(smbPasswordField, gridBagConstraints);

        smbLocalHeaderPanel.setLayout(new java.awt.GridBagLayout());
        smbLocalHeaderPanel.add(smbLocalSeparator, new java.awt.GridBagConstraints());

        smbLocalLabel.setFont(smbLocalLabel.getFont().deriveFont(smbLocalLabel.getFont().getStyle() | java.awt.Font.BOLD));
        smbLocalLabel.setText(bundle.getString("BackupMainPanel.smbLocalLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        smbLocalHeaderPanel.add(smbLocalLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        smbLocalHeaderPanel.add(jSeparator5, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        smbStoragePanel.add(smbLocalHeaderPanel, gridBagConstraints);

        smbSudoPasswordLabel.setText(bundle.getString("BackupMainPanel.smbSudoPasswordLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbStoragePanel.add(smbSudoPasswordLabel, gridBagConstraints);

        smbSudoPasswordField.setName("smbSudoPasswordField"); // NOI18N
        smbSudoPasswordField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbSudoPasswordFieldActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbStoragePanel.add(smbSudoPasswordField, gridBagConstraints);

        smbLoginPanel.setLayout(new java.awt.GridBagLayout());

        smbLogInOutButton.setText(bundle.getString("Login")); // NOI18N
        smbLogInOutButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbLogInOutButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbLoginPanel.add(smbLogInOutButton, gridBagConstraints);

        smbLoginProgressBar.setString(bundle.getString("Not_Logged_In")); // NOI18N
        smbLoginProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbLoginPanel.add(smbLoginProgressBar, gridBagConstraints);

        smbStorageLabel.setText(bundle.getString("BackupMainPanel.smbStorageLabel.text")); // NOI18N
        smbStorageLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        smbLoginPanel.add(smbStorageLabel, gridBagConstraints);

        smbStorageTextField.setEnabled(false);
        smbStorageTextField.setName("smbStorageTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        smbLoginPanel.add(smbStorageTextField, gridBagConstraints);

        smbStorageButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        smbStorageButton.setEnabled(false);
        smbStorageButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                smbStorageButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        smbLoginPanel.add(smbStorageButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        smbStoragePanel.add(smbLoginPanel, gridBagConstraints);

        jPanel2.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.weighty = 1.0;
        smbStoragePanel.add(jPanel2, gridBagConstraints);

        destinationCardPanel.add(smbStoragePanel, "smbStoragePanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 10);
        directoriesPanel.add(destinationCardPanel, gridBagConstraints);

        encryptionCardPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("BackupMainPanel.encryptionCardPanel.border.title"))); // NOI18N
        encryptionCardPanel.setName("encryptionCardPanel"); // NOI18N
        encryptionCardPanel.setLayout(new java.awt.CardLayout());

        encryptionErrorPanel.setLayout(new java.awt.GridBagLayout());

        encryptionErrorLabel.setText(bundle.getString("BackupMainPanel.encryptionErrorLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 3);
        encryptionErrorPanel.add(encryptionErrorLabel, gridBagConstraints);

        encryptionCardPanel.add(encryptionErrorPanel, "encryptionErrorPanel");

        encryptionPanel.setLayout(new java.awt.GridBagLayout());

        encryptionButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/folder_locked.png"))); // NOI18N
        encryptionButton.setText(bundle.getString("BackupMainPanel.encryptionButton.text")); // NOI18N
        encryptionButton.setName("encryptionButton"); // NOI18N
        encryptionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                encryptionButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 0);
        encryptionPanel.add(encryptionButton, gridBagConstraints);

        encryptionCardPanel.add(encryptionPanel, "encryptionPanel");

        unlockPanel.setLayout(new java.awt.GridBagLayout());

        lockButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/password.png"))); // NOI18N
        lockButton.setText(bundle.getString("Unlock")); // NOI18N
        lockButton.setName("lockButton"); // NOI18N
        lockButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                lockButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 5, 0);
        unlockPanel.add(lockButton, gridBagConstraints);

        changePasswordButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/reload.png"))); // NOI18N
        changePasswordButton.setText(bundle.getString("BackupMainPanel.changePasswordButton.text")); // NOI18N
        changePasswordButton.setName("changePasswordButton"); // NOI18N
        changePasswordButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                changePasswordButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        unlockPanel.add(changePasswordButton, gridBagConstraints);

        decryptionButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/folder.png"))); // NOI18N
        decryptionButton.setText(bundle.getString("BackupMainPanel.decryptionButton.text")); // NOI18N
        decryptionButton.setName("decryptionButton"); // NOI18N
        decryptionButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                decryptionButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 5, 0);
        unlockPanel.add(decryptionButton, gridBagConstraints);

        encryptionCardPanel.add(unlockPanel, "unlockPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridx = 1;
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 10, 8);
        directoriesPanel.add(encryptionCardPanel, gridBagConstraints);

        mainTabbedPane.addTab(bundle.getString("BackupMainPanel.directoriesPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/folder.png")), directoriesPanel); // NOI18N

        advancedSettingsPanel.setLayout(new java.awt.GridBagLayout());

        autoDeletionHeaderPanel.setLayout(new java.awt.GridBagLayout());
        autoDeletionHeaderPanel.add(autoDeletionSeparator1, new java.awt.GridBagConstraints());

        autoDeletionLabel.setFont(autoDeletionLabel.getFont().deriveFont(autoDeletionLabel.getFont().getStyle() | java.awt.Font.BOLD));
        autoDeletionLabel.setText(bundle.getString("BackupMainPanel.autoDeletionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionHeaderPanel.add(autoDeletionLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionHeaderPanel.add(jSeparator8, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        advancedSettingsPanel.add(autoDeletionHeaderPanel, gridBagConstraints);

        autoDeletionNumberPanel.setLayout(new java.awt.GridBagLayout());

        autoDeleteNumberCheckBox.setText(bundle.getString("BackupMainPanel.autoDeleteNumberCheckBox.text")); // NOI18N
        autoDeletionNumberPanel.add(autoDeleteNumberCheckBox, new java.awt.GridBagConstraints());

        autoDeleteNumberSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
        autoDeleteNumberSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                autoDeleteNumberSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionNumberPanel.add(autoDeleteNumberSpinner, gridBagConstraints);

        autoDeleteNumberLabel.setText(bundle.getString("AutoDeleteBackup")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionNumberPanel.add(autoDeleteNumberLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 0);
        advancedSettingsPanel.add(autoDeletionNumberPanel, gridBagConstraints);

        autoDeletionAgePanel.setLayout(new java.awt.GridBagLayout());

        autoDeleteAgeCheckBox.setText(bundle.getString("BackupMainPanel.autoDeleteAgeCheckBox.text")); // NOI18N
        autoDeletionAgePanel.add(autoDeleteAgeCheckBox, new java.awt.GridBagConstraints());

        autoDeleteAgeSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionAgePanel.add(autoDeleteAgeSpinner, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionAgePanel.add(autoDeleteAgeComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        advancedSettingsPanel.add(autoDeletionAgePanel, gridBagConstraints);

        autoDeletionSpacePanel.setLayout(new java.awt.GridBagLayout());

        autoDeletionSpaceCheckBox.setText(bundle.getString("BackupMainPanel.autoDeletionSpaceCheckBox.text")); // NOI18N
        autoDeletionSpacePanel.add(autoDeletionSpaceCheckBox, new java.awt.GridBagConstraints());

        autoDeletionSpaceSpinner.setModel(new javax.swing.SpinnerNumberModel(Integer.valueOf(1), Integer.valueOf(1), null, Integer.valueOf(1)));
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionSpacePanel.add(autoDeletionSpaceSpinner, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        autoDeletionSpacePanel.add(autoDeletionSpaceComboBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        advancedSettingsPanel.add(autoDeletionSpacePanel, gridBagConstraints);

        tempDirHeaderPanel.setLayout(new java.awt.GridBagLayout());
        tempDirHeaderPanel.add(tempDirSeparator1, new java.awt.GridBagConstraints());

        tempDirLabel.setFont(tempDirLabel.getFont().deriveFont(tempDirLabel.getFont().getStyle() | java.awt.Font.BOLD));
        tempDirLabel.setText(bundle.getString("BackupMainPanel.tempDirLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        tempDirHeaderPanel.add(tempDirLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        tempDirHeaderPanel.add(jSeparator6, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        advancedSettingsPanel.add(tempDirHeaderPanel, gridBagConstraints);

        tempDirRadioButtonPanel.setLayout(new java.awt.GridBagLayout());

        tempDirButtonGroup.add(defaultTempDirRadioButton);
        defaultTempDirRadioButton.setSelected(true);
        defaultTempDirRadioButton.setText(bundle.getString("BackupMainPanel.defaultTempDirRadioButton.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        tempDirRadioButtonPanel.add(defaultTempDirRadioButton, gridBagConstraints);

        tempDirButtonGroup.add(customTempDirRadioButton);
        customTempDirRadioButton.setText(bundle.getString("BackupMainPanel.customTempDirRadioButton.text")); // NOI18N
        customTempDirRadioButton.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                customTempDirRadioButtonItemStateChanged(evt);
            }
        });
        customTempDirRadioButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                customTempDirRadioButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        tempDirRadioButtonPanel.add(customTempDirRadioButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        advancedSettingsPanel.add(tempDirRadioButtonPanel, gridBagConstraints);

        tempDirDetailsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(""));
        tempDirDetailsPanel.setName("tempDirDetailsPanel"); // NOI18N
        tempDirDetailsPanel.setLayout(new java.awt.GridBagLayout());

        tempDirTextField.setName("tempDirTextField"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = 2;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        tempDirDetailsPanel.add(tempDirTextField, gridBagConstraints);

        tempDirBrowseButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        tempDirBrowseButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        tempDirBrowseButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                tempDirBrowseButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 5);
        tempDirDetailsPanel.add(tempDirBrowseButton, gridBagConstraints);

        storageUsageLabel.setText(bundle.getString("BackupMainPanel.storageUsageLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        tempDirDetailsPanel.add(storageUsageLabel, gridBagConstraints);

        storageUsageProgressBar.setFont(storageUsageProgressBar.getFont().deriveFont(storageUsageProgressBar.getFont().getStyle() & ~java.awt.Font.BOLD));
        storageUsageProgressBar.setStringPainted(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 5, 0);
        tempDirDetailsPanel.add(storageUsageProgressBar, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 10);
        advancedSettingsPanel.add(tempDirDetailsPanel, gridBagConstraints);

        mainTabbedPane.addTab(bundle.getString("BackupMainPanel.advancedSettingsPanel.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/configure.png")), advancedSettingsPanel); // NOI18N

        add(mainTabbedPane, "mainTabbedPane");

        progressPanel.setLayout(new java.awt.GridBagLayout());

        progressLabel.setText(bundle.getString("BackupMainPanel.progressLabel.text")); // NOI18N
        progressLabel.setName("progressLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        progressPanel.add(progressLabel, gridBagConstraints);

        filenameLabel.setFont(filenameLabel.getFont().deriveFont(filenameLabel.getFont().getStyle() & ~java.awt.Font.BOLD, filenameLabel.getFont().getSize()-1));
        filenameLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        filenameLabel.setText(bundle.getString("BackupMainPanel.filenameLabel.text")); // NOI18N
        filenameLabel.setName("filenameLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        progressPanel.add(filenameLabel, gridBagConstraints);

        progressBar.setIndeterminate(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        progressPanel.add(progressBar, gridBagConstraints);

        timeLabel.setText(bundle.getString("BackupMainPanel.timeLabel.text")); // NOI18N
        timeLabel.setName("timeLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        progressPanel.add(timeLabel, gridBagConstraints);

        shutdownCheckBox.setText(bundle.getString("Shutdown_After_Backup")); // NOI18N
        shutdownCheckBox.setName("shutdownCheckBox"); // NOI18N
        shutdownCheckBox.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                shutdownCheckBoxStateChanged(evt);
            }
        });

        shutdownLabel.setText(bundle.getString("BackupMainPanel.shutdownLabel.text")); // NOI18N
        shutdownLabel.setEnabled(false);

        shutdownPasswordField.setColumns(10);
        shutdownPasswordField.setEnabled(false);
        shutdownPasswordField.setName("shutdownPasswordField"); // NOI18N

        javax.swing.GroupLayout shutdownPanelLayout = new javax.swing.GroupLayout(shutdownPanel);
        shutdownPanel.setLayout(shutdownPanelLayout);
        shutdownPanelLayout.setHorizontalGroup(
            shutdownPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(shutdownPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addGroup(shutdownPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(shutdownPanelLayout.createSequentialGroup()
                        .addGap(21, 21, 21)
                        .addComponent(shutdownLabel)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(shutdownPasswordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(shutdownCheckBox))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );
        shutdownPanelLayout.setVerticalGroup(
            shutdownPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(shutdownPanelLayout.createSequentialGroup()
                .addContainerGap()
                .addComponent(shutdownCheckBox)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(shutdownPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(shutdownLabel)
                    .addComponent(shutdownPasswordField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        progressPanel.add(shutdownPanel, gridBagConstraints);

        cancelButton.setText(bundle.getString("BackupMainPanel.cancelButton.text")); // NOI18N
        cancelButton.setName("cancelButton"); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        cancelButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                cancelButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                cancelButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        progressPanel.add(cancelButton, gridBagConstraints);

        add(progressPanel, "progressPanel");

        sessionStatisticsPanel.setLayout(new java.awt.GridBagLayout());

        statisticsLabel.setText(bundle.getString("BackupMainPanel.statisticsLabel.text")); // NOI18N
        statisticsLabel.setName("statisticsLabel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        sessionStatisticsPanel.add(statisticsLabel, gridBagConstraints);

        statisticsTextFieldScrollPane.setName("statisticsTextFieldScrollPane"); // NOI18N

        statisticsTextField.setColumns(35);
        statisticsTextField.setEditable(false);
        statisticsTextField.setRows(5);
        statisticsTextFieldScrollPane.setViewportView(statisticsTextField);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        sessionStatisticsPanel.add(statisticsTextFieldScrollPane, gridBagConstraints);

        continueButton.setText(bundle.getString("BackupMainPanel.continueButton.text")); // NOI18N
        continueButton.setName("continueButton"); // NOI18N
        continueButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                continueButtonActionPerformed(evt);
            }
        });
        continueButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                continueButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                continueButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 5);
        sessionStatisticsPanel.add(continueButton, gridBagConstraints);

        quitButton.setText(bundle.getString("BackupMainPanel.quitButton.text")); // NOI18N
        quitButton.setName("quitButton"); // NOI18N
        quitButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quitButtonActionPerformed(evt);
            }
        });
        quitButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                quitButtonFocusGained(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        sessionStatisticsPanel.add(quitButton, gridBagConstraints);

        add(sessionStatisticsPanel, "sessionStatisticsPanel");

        backupRestoredPanel.setLayout(new java.awt.GridBagLayout());

        restoredLabel.setText(bundle.getString("Restoring_Successfull")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        backupRestoredPanel.add(restoredLabel, gridBagConstraints);

        restoredOKButton.setText(bundle.getString("BackupMainPanel.restoredOKButton.text")); // NOI18N
        restoredOKButton.setName("restoredOKButton"); // NOI18N
        restoredOKButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restoredOKButtonActionPerformed(evt);
            }
        });
        restoredOKButton.addFocusListener(new java.awt.event.FocusAdapter() {
            public void focusGained(java.awt.event.FocusEvent evt) {
                restoredOKButtonFocusGained(evt);
            }
            public void focusLost(java.awt.event.FocusEvent evt) {
                restoredOKButtonFocusLost(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(15, 0, 0, 0);
        backupRestoredPanel.add(restoredOKButton, gridBagConstraints);

        add(backupRestoredPanel, "backupRestoredPanel");
    }// </editor-fold>//GEN-END:initComponents

    private void restoreButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restoreButtonActionPerformed

        final RdiffFile[] selectedFiles = rdiffChooserPanel.getSelectedFiles();
        if (selectedFiles == null) {
            showError("Error_No_Files_Selected");
            return;
        }

        if (sourceDirectoryRadioButton.isSelected()) {
            boolean showWarning = false;
            File sourceDirectory = new File(backupSourceTextField.getText());
            if ((selectedFiles.length != 1)
                    || (selectedFiles[0].getParentFile() != null)) {
                // The user selected some files (not "whole backup").
                // We have to test if we are overwriting files...
                for (RdiffFile selectedFile : selectedFiles) {
                    File testFile = new File(
                            sourceDirectory, selectedFile.getPath());
                    if (testFile.exists()) {
                        showWarning = true;
                        break;
                    }
                }
            }

            // show big fat warning dialog that files can be overwritten
            if (showWarning && (JOptionPane.YES_OPTION
                    != JOptionPane.showConfirmDialog(parentFrame,
                    BUNDLE.getString("Restore_Warning"),
                    BUNDLE.getString("Warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE))) {
                return;
            }
            restore(selectedFiles, sourceDirectory);

        } else {
            SelectRestoreDirectoryDialog dialog =
                    new SelectRestoreDirectoryDialog(parentFrame);
            dialog.setVisible(true);
            if (dialog.restoreSelected()) {
                restore(selectedFiles, dialog.getSelectedDirectory());
            }
        }
}//GEN-LAST:event_restoreButtonActionPerformed

    private void backupButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupButtonActionPerformed

        boolean exclude = excludeCheckBox.isSelected();

        // check file size exclusions
        final Long maxFileSize = (exclude && maxSizeCheckBox.isSelected())
                ? getFileSize(maxSizeTextField, maxSizeComboBox)
                : null;
        final Long minFileSize = (exclude && minSizeCheckBox.isSelected())
                ? getFileSize(minSizeTextField, minSizeComboBox)
                : null;
        if ((maxFileSize != null) && (maxFileSize <= 0)) {
            maxSizeTextField.requestFocusInWindow();
            maxSizeTextField.selectAll();
            JOptionPane.showMessageDialog(parentFrame,
                    BUNDLE.getString("Error_Max_File_Size"),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if ((minFileSize != null) && (minFileSize <= 0)) {
            minSizeTextField.requestFocusInWindow();
            minSizeTextField.selectAll();
            JOptionPane.showMessageDialog(parentFrame,
                    BUNDLE.getString("Error_Min_File_Size"),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if ((maxFileSize != null) && (minFileSize != null)
                && (minFileSize >= maxFileSize)) {
            maxSizeTextField.requestFocusInWindow();
            maxSizeTextField.selectAll();
            JOptionPane.showMessageDialog(parentFrame,
                    BUNDLE.getString("Error_Min_Max_File_Size"),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        String sshUserName = sshUserNameTextField.getText();
        String sshServerName = sshServerTextField.getText();
        String sshPassword = getSshPassword(sshUserName, sshServerName);

        if (destinationEncrypted) {
            runBackup(minFileSize, maxFileSize, false, sshPassword);

        } else {
            if (encfsEnabled && plainBackupWarning) {
                PlaintextBackupWarningDialog dialog =
                        new PlaintextBackupWarningDialog(parentFrame);
                dialog.setVisible(true);
                plainBackupWarning = dialog.isShowWarningSelected();
                if (dialog.isOkPressed()) {
                    mainTabbedPane.setSelectedComponent(directoriesPanel);
                    encrypt();
                    return;
                }
            }
            if (sshfsMounted) {
                // check if rdiff-backup is usable on the remote server
                BackupServerCheckSwingWorker backupServerCheckSwingWorker =
                        new BackupServerCheckSwingWorker(parentFrame,
                        sshUserName, sshServerName, sshPassword,
                        this, minFileSize, maxFileSize);
                backupServerCheckSwingWorker.execute();
            } else {
                runBackup(minFileSize, maxFileSize, false, sshPassword);
            }
        }
}//GEN-LAST:event_backupButtonActionPerformed

    private void continueButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_continueButtonActionPerformed
        showCard(this, "mainTabbedPane");
    }//GEN-LAST:event_continueButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        Object[] options = new Object[]{
            BUNDLE.getString("Yes"), BUNDLE.getString("No")};
        JOptionPane optionPane = new JOptionPane(
                BUNDLE.getString("Cancel_Question"),
                JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION,
                null, options, options[1]);
        Dialog dialog = optionPane.createDialog(
                parentFrame, BUNDLE.getString("Question"));
        dialog.setVisible(true);
        if (options[0] == optionPane.getValue()) {
            processCancelled = true;
            rdiffBackupRestore.cancelRdiffOperation();
            currentSwingWorker.cancel(true);
            showCard(BackupMainPanel.this, "mainTabbedPane");
        }
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void restoredOKButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restoredOKButtonActionPerformed
        showCard(this, "mainTabbedPane");
        mainTabbedPane.requestFocusInWindow();
    }//GEN-LAST:event_restoredOKButtonActionPerformed

    private void backupSourceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupSourceButtonActionPerformed

        String title = BUNDLE.getString("Select_Source_Directory");
        String currentPath = backupSourceTextField.getText();

        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            FileDialog fileDialog = new FileDialog(
                    parentFrame, title, FileDialog.LOAD);
            fileDialog.setDirectory(currentPath);
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            fileDialog.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
            String directory = fileDialog.getDirectory();
            String file = fileDialog.getFile();
            if ((directory != null) && (file != null)) {
                backupSourceTextField.setText(
                        directory + file + File.separatorChar);
            }

        } else {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fileChooser.setFileHidingEnabled(false);
            fileChooser.setApproveButtonText(BUNDLE.getString("Select"));
            fileChooser.addChoosableFileFilter(
                    NO_HIDDEN_FILES_SWING_FILE_FILTER);
            fileChooser.setFileFilter(NO_HIDDEN_FILES_SWING_FILE_FILTER);
            fileChooser.setDialogTitle(title);
            fileChooser.setCurrentDirectory(new File(currentPath));
            int selectedOption = fileChooser.showOpenDialog(parentFrame);
            if (selectedOption == JFileChooser.APPROVE_OPTION) {
                String selectedPath = fileChooser.getSelectedFile().getPath();
                backupSourceTextField.setText(selectedPath);
            }
        }
}//GEN-LAST:event_backupSourceButtonActionPerformed

    private void sshLogInOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshLogInOutButtonActionPerformed
        if (LOGIN.equals(sshLogInOutButton.getActionCommand())) {
            sshLogin(false);
        } else {
            try {
                // close connection to database (if any)
                RdiffFileDatabase rdiffFileDatabase =
                        rdiffChooserPanel.getRdiffFileDatabase();
                if (rdiffFileDatabase != null) {
                    rdiffFileDatabase.close();
                }

                // umount
                String mountPoint = getSshfsMountPoint();
                if ((mountPoint != null)
                        && FileTools.umountFUSE(new File(mountPoint), true)) {
                    setSshMounted(false);
                    destinationChanged();
                    sshPasswordField.requestFocusInWindow();
                } else {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Logout_Failed"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_sshLogInOutButtonActionPerformed

    private void addExcludesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addExcludesButtonActionPerformed
        addSelectedFiles(BUNDLE.getString("Select_Files_To_Exclude"),
                new File(backupSourceTextField.getText()), excludesTextArea);
}//GEN-LAST:event_addExcludesButtonActionPerformed

    private void addIncludesButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addIncludesButtonActionPerformed
        addSelectedFiles(BUNDLE.getString("Select_Exceptions"),
                new File(backupSourceTextField.getText()), includesTextArea);
}//GEN-LAST:event_addIncludesButtonActionPerformed

    private void mainTabbedPaneStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_mainTabbedPaneStateChanged

        int selectedIndex = mainTabbedPane.getSelectedIndex();
        switch (selectedIndex) {
            case 0:
                // backup tab
                String sourcePath = backupSourceTextField.getText();
                File sourceDirectory = new File(sourcePath);
                if (checkSourceCommon(sourcePath, sourceDirectory)
                        && checkSourceBackup(sourceDirectory)
                        && checkDestinationCommon()
                        && checkDestinationBackup()
                        && checkTempDirectory()) {
                    showCard(backupCardPanel, "backupPanel");
                }
                break;

            case 1:
                // restore tab
                sourcePath = backupSourceTextField.getText();
                sourceDirectory = new File(sourcePath);
                if (!checkSourceCommon(sourcePath, sourceDirectory)
                        || !checkSourceRestore(sourceDirectory)
                        || !checkDestinationCommon()
                        || !checkTempDirectory()) {
                    break;
                }
                showCard(restoreCardPanel, "restorePanel");
                rdiffChooserPanel.setSelectedDirectory(getBackupDestination());
                break;

            case 2:
                // directories tab
                // just some focus handling
                directoriesTabFocusHandling();
                break;

            case 3:
                // advanced settings tab
                // (nothing to do here...)
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "unhandled tab index {0}", selectedIndex);
        }
    }//GEN-LAST:event_mainTabbedPaneStateChanged

    private void backupButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_backupButtonFocusGained
        getRootPane().setDefaultButton(backupButton);
    }//GEN-LAST:event_backupButtonFocusGained

    private void backupSourceButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_backupSourceButtonFocusGained
        getRootPane().setDefaultButton(backupSourceButton);
    }//GEN-LAST:event_backupSourceButtonFocusGained

    private void restoreButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_restoreButtonFocusGained
        getRootPane().setDefaultButton(restoreButton);
    }//GEN-LAST:event_restoreButtonFocusGained

    private void cancelButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cancelButtonFocusGained
        getRootPane().setDefaultButton(cancelButton);
    }//GEN-LAST:event_cancelButtonFocusGained

    private void continueButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_continueButtonFocusGained
        getRootPane().setDefaultButton(continueButton);
    }//GEN-LAST:event_continueButtonFocusGained

    private void restoredOKButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_restoredOKButtonFocusGained
        getRootPane().setDefaultButton(restoredOKButton);
    }//GEN-LAST:event_restoredOKButtonFocusGained

    private void backupButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_backupButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_backupButtonFocusLost

    private void backupSourceButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_backupSourceButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_backupSourceButtonFocusLost

    private void restoreButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_restoreButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_restoreButtonFocusLost

    private void cancelButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_cancelButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_cancelButtonFocusLost

    private void continueButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_continueButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_continueButtonFocusLost

    private void restoredOKButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_restoredOKButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_restoredOKButtonFocusLost

    private void sshLogInOutButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_sshLogInOutButtonFocusGained
        getRootPane().setDefaultButton(sshLogInOutButton);
    }//GEN-LAST:event_sshLogInOutButtonFocusGained

    private void sshLogInOutButtonFocusLost(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_sshLogInOutButtonFocusLost
        getRootPane().setDefaultButton(null);
    }//GEN-LAST:event_sshLogInOutButtonFocusLost

    private void encryptionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_encryptionButtonActionPerformed
        encrypt();
    }//GEN-LAST:event_encryptionButtonActionPerformed

    private void localStorageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localStorageButtonActionPerformed
        String selectedPath = localStorageTextField.getText();
        SelectBackupDirectoryDialog dialog = new SelectBackupDirectoryDialog(
                parentFrame, null, selectedPath, false);
        if (dialog.showDialog() == JOptionPane.OK_OPTION) {
            localStorageTextField.setText(dialog.getSelectedPath());
        }
    }//GEN-LAST:event_localStorageButtonActionPerformed

    private void sshStorageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshStorageButtonActionPerformed
        try {
            selectRemoteDirectory(sshServerTextField.getText(),
                    sshStorageTextField, getSshfsMountPoint());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_sshStorageButtonActionPerformed

    private void decryptionButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_decryptionButtonActionPerformed
        String cipherPath = getRawBackupDestination();
        if (encfsMountPoint == null) {
            // encfs is not mounted
            DecryptEncfsDialog dialog = new DecryptEncfsDialog(parentFrame);
            if (JOptionPane.OK_OPTION == dialog.showDialog()) {
                String password = dialog.getPassword();
                try {
                    encfsMountPoint = FileTools.createTempDirectory(
                            ENCFS_SEARCH_STRING, null).getPath();
                    if (!FileTools.mountEncFs(
                            cipherPath, encfsMountPoint, password)) {
                        LOGGER.log(Level.WARNING,
                                "could not mount {0}", cipherPath);
                        // TODO: error handling
                        return;
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                    // TODO: error handling
                    return;
                }
            } else {
                return;
            }

        } else {
            // encfs is already mounted
            Object[] options = new Object[]{
                BUNDLE.getString("Yes"), BUNDLE.getString("No")};
            JOptionPane optionPane = new JOptionPane(
                    BUNDLE.getString("Decryption_Warning"),
                    JOptionPane.WARNING_MESSAGE, JOptionPane.YES_NO_OPTION,
                    null, options, options[1]);
            Dialog dialog = optionPane.createDialog(
                    parentFrame, BUNDLE.getString("Warning"));
            dialog.setVisible(true);
            if (options[0] != optionPane.getValue()) {
                return;
            }
        }

        File encfsPlainDir = new File(encfsMountPoint);
        File encfsCipherDir = new File(cipherPath);
        final DirectoryCheckDialog directoryCheckDialog =
                new DirectoryCheckDialog(parentFrame);
        directoryCheckDialog.setFilenameCheckEnabled(false, 0);
        ModalDialogHandler dialogHandler =
                new ModalDialogHandler(directoryCheckDialog);
        DecryptionCheckSwingWorker decryptionCheckSwingWorker =
                new DecryptionCheckSwingWorker(parentFrame, this,
                directoryCheckDialog, dialogHandler,
                encfsCipherDir, encfsPlainDir);
        if (FileTools.isSpaceKnown(encfsCipherDir)) {
            long usableSpace = encfsCipherDir.getUsableSpace();
            directoryCheckDialog.setFreeSpaceKnown(true, usableSpace);
            decryptionCheckSwingWorker.setUsableSpace(usableSpace);
        }
        decryptionCheckSwingWorker.execute();
        dialogHandler.show();
    }//GEN-LAST:event_decryptionButtonActionPerformed

    private void lockButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_lockButtonActionPerformed
        if (UNLOCK.equals(lockButton.getActionCommand())) {
            unlock(false);
        } else {
            lock();
        }
    }//GEN-LAST:event_lockButtonActionPerformed

    private void changePasswordButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changePasswordButtonActionPerformed
        ChangePasswordDialog dialog = new ChangePasswordDialog(parentFrame);
        if (JOptionPane.OK_OPTION == dialog.showDialog()) {
            String oldPassword = dialog.getOldPassword();
            String newPassword = dialog.getNewPassword();
            String changePasswordScript =
                    "#!/usr/bin/expect -f" + LINE_SEPARATOR
                    + "set oldPassword [lindex $argv 0]" + LINE_SEPARATOR
                    + "set newPassword [lindex $argv 1]" + LINE_SEPARATOR
                    + "spawn encfsctl passwd \""
                    + getRawBackupDestination() + '\"' + LINE_SEPARATOR
                    + "expect \"EncFS Password: \"" + LINE_SEPARATOR
                    + "send \"$oldPassword\r\"" + LINE_SEPARATOR
                    + "expect \"New Encfs Password: \"" + LINE_SEPARATOR
                    + "send \"$newPassword\r\"" + LINE_SEPARATOR
                    + "expect \"Verify Encfs Password: \"" + LINE_SEPARATOR
                    + "send \"$newPassword\r\"" + LINE_SEPARATOR
                    + "expect eof" + LINE_SEPARATOR
                    + "set ret [lindex [wait] 3]" + LINE_SEPARATOR
                    + "puts \"return value: $ret\"" + LINE_SEPARATOR
                    + "exit $ret";

            // set level to OFF to prevent password leaking into logfiles
            Logger logger = Logger.getLogger(ProcessExecutor.class.getName());
            Level level = logger.getLevel();
            logger.setLevel(Level.OFF);

            int returnValue = -1;
            try {
                returnValue = processExecutor.executeScript(
                        changePasswordScript, oldPassword, newPassword);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            // restore previous log level
            logger.setLevel(level);

            if (returnValue == 0) {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Password_Changed"),
                        BUNDLE.getString("Information"),
                        JOptionPane.INFORMATION_MESSAGE,
                        IconManager.INFORMATION_ICON);
            } else {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Password_Not_Changed"),
                        BUNDLE.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }//GEN-LAST:event_changePasswordButtonActionPerformed

    private void sshServerTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshServerTextFieldActionPerformed
        sshUserNameTextField.selectAll();
        sshUserNameTextField.requestFocusInWindow();
    }//GEN-LAST:event_sshServerTextFieldActionPerformed

    private void shutdownCheckBoxStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_shutdownCheckBoxStateChanged
        boolean enabled = shutdownCheckBox.isSelected();
        shutdownLabel.setEnabled(enabled);
        shutdownPasswordField.setEnabled(enabled);
    }//GEN-LAST:event_shutdownCheckBoxStateChanged

    private void sshUserNameTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshUserNameTextFieldActionPerformed
        sshPasswordField.selectAll();
        sshPasswordField.requestFocusInWindow();
    }//GEN-LAST:event_sshUserNameTextFieldActionPerformed

    private void sshPasswordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshPasswordFieldActionPerformed
        sshLogin(true);
    }//GEN-LAST:event_sshPasswordFieldActionPerformed

    private void includesCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_includesCheckBoxItemStateChanged
        includesPanel.setVisible(includesCheckBox.isSelected());
    }//GEN-LAST:event_includesCheckBoxItemStateChanged

    private void excludeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_excludeCheckBoxItemStateChanged
        if (excludeCheckBox.isSelected()) {
            showCard(backupConfigCardPanel, "excludesPanel");
        } else {
            showCard(backupConfigCardPanel, "noExcludesPanel");
        }
    }//GEN-LAST:event_excludeCheckBoxItemStateChanged

    private void tempDirBrowseButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_tempDirBrowseButtonActionPerformed

        String title = BUNDLE.getString("Select_Temp_Directory");

        // walk currentDir up to existing directory
        File currentDir = new File(tempDirTextField.getText());
        for (File parentFile = currentDir.getParentFile();
                (!currentDir.exists()) && (parentFile != null);) {
            currentDir = parentFile;
            parentFile = currentDir.getParentFile();
        }

        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            FileDialog fileDialog = new FileDialog(
                    parentFrame, title, FileDialog.LOAD);
            fileDialog.setDirectory(currentDir.getPath());
            System.setProperty("apple.awt.fileDialogForDirectories", "true");
            fileDialog.setVisible(true);
            System.setProperty("apple.awt.fileDialogForDirectories", "false");
            String directory = fileDialog.getDirectory();
            String file = fileDialog.getFile();
            if ((directory != null) && (file != null)) {
                tempDirTextField.setText(
                        directory + file + File.separatorChar);
            }

        } else {
            JFileChooser directoryChooser = new JFileChooser();
            directoryChooser.setFileSelectionMode(
                    JFileChooser.DIRECTORIES_ONLY);
            directoryChooser.setFileHidingEnabled(false);
            FileFilter noHiddenFilesSwingFilter =
                    NoHiddenFilesSwingFileFilter.getInstance();
            directoryChooser.addChoosableFileFilter(noHiddenFilesSwingFilter);
            directoryChooser.setFileFilter(noHiddenFilesSwingFilter);
            directoryChooser.setCurrentDirectory(currentDir);
            directoryChooser.setDialogTitle(title);
            directoryChooser.setApproveButtonText(BUNDLE.getString("Choose"));
            if (directoryChooser.showOpenDialog(this)
                    == JFileChooser.APPROVE_OPTION) {
                String selectedPath = directoryChooser.getSelectedFile().getPath();
                tempDirTextField.setText(selectedPath);
            }
        }
}//GEN-LAST:event_tempDirBrowseButtonActionPerformed

    private void localRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_localRadioButtonItemStateChanged
        if (localRadioButton.isSelected()) {
            showCard(destinationCardPanel, "localStoragePanel");
            destinationChanged();
        }
    }//GEN-LAST:event_localRadioButtonItemStateChanged

    private void sshPasswordRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sshPasswordRadioButtonItemStateChanged
        boolean selected = sshPasswordRadioButton.isSelected();
        sshPasswordField.setEnabled(selected);
        sshPasswordField.setEditable(selected);
    }//GEN-LAST:event_sshPasswordRadioButtonItemStateChanged

    private void customTempDirRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_customTempDirRadioButtonItemStateChanged
        updateTempDirState();
    }//GEN-LAST:event_customTempDirRadioButtonItemStateChanged

    private void sshPasswordRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshPasswordRadioButtonActionPerformed
        sshPasswordField.requestFocusInWindow();
    }//GEN-LAST:event_sshPasswordRadioButtonActionPerformed

    private void customTempDirRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_customTempDirRadioButtonActionPerformed
        tempDirTextField.requestFocusInWindow();
    }//GEN-LAST:event_customTempDirRadioButtonActionPerformed

    private void localRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_localRadioButtonActionPerformed
        localStorageTextField.requestFocusInWindow();
    }//GEN-LAST:event_localRadioButtonActionPerformed

    private void sshRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sshRadioButtonActionPerformed
        if (!sshFocusHandling() && sshStorageTextField.isEnabled()) {
            sshStorageTextField.requestFocusInWindow();
        }
    }//GEN-LAST:event_sshRadioButtonActionPerformed

    private void reminderButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_reminderButtonActionPerformed
        EditReminderDialog dialog = new EditReminderDialog(
                parentFrame, showReminder, reminderTimeout);
        dialog.setVisible(true);
        if (!dialog.okSelected()) {
            return;
        }

        reminderTimeout = dialog.getReminderTimeout();
        // the following operation is expensive, therefore we only trigger it
        // when there was a change in the "showReminder" setting
        boolean newShowReminder = dialog.isReminderSelected();
        if (showReminder != newShowReminder) {
            String packageName = JBackpack.class.getPackage().getName();
            String linuxIconFileName = USER_HOME + "/.java/.userPrefs/"
                    + packageName.replace(".", "/") + "/jbackpack.png";
            AutoStarter autoStarter = new AutoStarter("jbackpack",
                    "JBackpack", "JBackpack", "JBackpack", "--reminder");
            showReminder = newShowReminder;
            if (showReminder) {
                String desktopFileTemplate =
                        "[Desktop Entry]\n"
                        + "Type=Application\n"
                        + "Name=JBackpack Reminder\n"
                        + "Name[de]=JBackpack-Erinnerung\n"
                        + "Icon={0}\n"
                        + "Exec={1}\n";

                autoStarter.enableAutoStart("jbackpack", BackupFrame.ICON_PATH,
                        linuxIconFileName, desktopFileTemplate,
                        "jbackpack-reminder");
            } else {
                autoStarter.disableAutoStart("jbackpack",
                        linuxIconFileName, "jbackpack-reminder");
            }
        }
        updateReminderTextField();
    }//GEN-LAST:event_reminderButtonActionPerformed

    private void maxSizeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_maxSizeCheckBoxItemStateChanged
        boolean enabled = maxSizeCheckBox.isSelected();
        maxSizeTextField.setEnabled(enabled);
        maxSizeComboBox.setEnabled(enabled);
    }//GEN-LAST:event_maxSizeCheckBoxItemStateChanged

    private void minSizeCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_minSizeCheckBoxItemStateChanged
        boolean enabled = minSizeCheckBox.isSelected();
        minSizeTextField.setEnabled(enabled);
        minSizeComboBox.setEnabled(enabled);
    }//GEN-LAST:event_minSizeCheckBoxItemStateChanged

    private void otherSourceRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_otherSourceRadioButtonActionPerformed
        backupSourceTextField.requestFocusInWindow();
    }//GEN-LAST:event_otherSourceRadioButtonActionPerformed

    private void otherSourceRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_otherSourceRadioButtonItemStateChanged
        updateSourceDirState();
    }//GEN-LAST:event_otherSourceRadioButtonItemStateChanged

    private void quitButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quitButtonActionPerformed
        savePreferences();
        System.exit(0);
    }//GEN-LAST:event_quitButtonActionPerformed

    private void quitButtonFocusGained(java.awt.event.FocusEvent evt) {//GEN-FIRST:event_quitButtonFocusGained
        getRootPane().setDefaultButton(quitButton);
    }//GEN-LAST:event_quitButtonFocusGained

    private void autoDeleteNumberSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_autoDeleteNumberSpinnerStateChanged
        int value = (Integer) autoDeleteNumberSpinner.getValue();
        autoDeleteNumberLabel.setText(BUNDLE.getString(
                value == 1 ? "AutoDeleteBackup" : "AutoDeleteBackups"));
    }//GEN-LAST:event_autoDeleteNumberSpinnerStateChanged

    private void sshRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_sshRadioButtonItemStateChanged
        if (sshRadioButton.isSelected()) {
            showCard(destinationCardPanel, "sshStoragePanel");
            destinationChanged();
        }
    }//GEN-LAST:event_sshRadioButtonItemStateChanged

    private void smbRadioButtonItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_smbRadioButtonItemStateChanged
        if (smbRadioButton.isSelected()) {
            showCard(destinationCardPanel, "smbStoragePanel");
            destinationChanged();
        }
    }//GEN-LAST:event_smbRadioButtonItemStateChanged

    private void smbServerTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbServerTextFieldActionPerformed
        smbShareTextField.requestFocusInWindow();
    }//GEN-LAST:event_smbServerTextFieldActionPerformed

    private void smbUserTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbUserTextFieldActionPerformed
        smbPasswordField.requestFocusInWindow();
    }//GEN-LAST:event_smbUserTextFieldActionPerformed

    private void smbPasswordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbPasswordFieldActionPerformed
        switch (CurrentOperatingSystem.OS) {
            case Mac_OS_X:
            case Windows:
                smbLogin(false);
                break;

            default:
                smbSudoPasswordField.requestFocusInWindow();
        }
    }//GEN-LAST:event_smbPasswordFieldActionPerformed

    private void smbLogInOutButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbLogInOutButtonActionPerformed
        if (LOGIN.equals(smbLogInOutButton.getActionCommand())) {
            smbLogin(false);
        } else {
            try {
                String mountPoint = getSmbfsMountPoint();
                boolean umounted = false;
                switch (CurrentOperatingSystem.OS) {
                    case Linux:
                        String sudoPassword = new String(
                                smbSudoPasswordField.getPassword());
                        umounted = FileTools.umountSudo(
                                mountPoint, sudoPassword);
                        break;

                    case Mac_OS_X:
                        int returnValue = processExecutor.executeProcess(
                                "umount", mountPoint);
                        umounted = (returnValue == 0);
                        break;

                    case Windows:
                        umounted = FileTools.umountWin(mountPoint);
                        break;

                    default:
                        LOGGER.log(Level.WARNING, "{0} is not supported",
                                CurrentOperatingSystem.OS);
                }

                if (umounted) {
                    setSmbMounted(false);
                    destinationChanged();
                    smbPasswordField.requestFocusInWindow();
                } else {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Logout_Failed"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }//GEN-LAST:event_smbLogInOutButtonActionPerformed

    private void smbStorageButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbStorageButtonActionPerformed
        try {
            selectRemoteDirectory(smbServerTextField.getText(),
                    smbStorageTextField, getSmbfsMountPoint());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_smbStorageButtonActionPerformed

    private void smbShareTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbShareTextFieldActionPerformed
        smbUserTextField.requestFocusInWindow();
    }//GEN-LAST:event_smbShareTextFieldActionPerformed

    private void smbSudoPasswordFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbSudoPasswordFieldActionPerformed
        smbLogin(false);
    }//GEN-LAST:event_smbSudoPasswordFieldActionPerformed

    private void smbRadioButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_smbRadioButtonActionPerformed
        smbFocusHandling();
    }//GEN-LAST:event_smbRadioButtonActionPerformed

    private String getSshPassword(String sshUserName, String sshServerName) {
        if (sshRadioButton.isSelected()
                && sshPasswordRadioButton.isSelected()) {
            // check if ssh password is still known
            char[] sshPassword = sshPasswordField.getPassword();
            if ((sshPassword == null) || (sshPassword.length == 0)) {
                SshPasswordDialog passwordDialog = new SshPasswordDialog(
                        parentFrame, sshUserName, sshServerName);
                passwordDialog.setVisible(true);
                if (passwordDialog.okPressed()) {
                    sshPassword = passwordDialog.getPassword();
                }
            }
            return String.valueOf(sshPassword);
        }
        return null;
    }

    private void selectRemoteDirectory(String server,
            JTextField directoryTextField, String mountPoint) {
        String selectedPath = directoryTextField.getText();
        try {
            selectedPath = mountPoint
                    + (selectedPath.startsWith(File.separator)
                    ? "" : File.separator) + selectedPath;
            File mountDir = new File(mountPoint);
            ChrootFileSystemView chrootFileSystemView =
                    new ChrootFileSystemView(mountDir, server);
            SelectBackupDirectoryDialog dialog =
                    new SelectBackupDirectoryDialog(
                    parentFrame, chrootFileSystemView, selectedPath, false);
            if (dialog.showDialog() == JOptionPane.OK_OPTION) {
                String newPath = dialog.getSelectedPath();
                File newDir = new File(newPath).getCanonicalFile();
                newPath = newDir.getPath();
                if (FileTools.isSubDir(mountDir, newDir)) {
                    // cut off mountpoint
                    newPath = newPath.substring(mountPoint.length());
                    directoryTextField.setText(newPath);
                } else {
                    String errorMessage = BUNDLE.getString(
                            "Error_Selected_Directory_Not_On_Server");
                    errorMessage = MessageFormat.format(
                            errorMessage, newPath, server);
                    JOptionPane.showMessageDialog(parentFrame,
                            errorMessage, BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private void autoDeletion(File destinationDirectory) throws IOException {
        List<Increment> increments = null;

        if (autoDeleteNumberCheckBox.isSelected()) {
            // auto deletion by increment numbers
            RdiffFileDatabase rdiffFileDatabase =
                    rdiffChooserPanel.getRdiffFileDatabase();
            increments = rdiffFileDatabase.getIncrements();
            Number autoDeleteNumber =
                    (Number) autoDeleteNumberSpinner.getValue();
            int autoDeleteCount = autoDeleteNumber.intValue();
            if (increments.size() > autoDeleteCount) {
                Increment lastGoodIncrement =
                        increments.get(autoDeleteCount - 1);
                String rdiffTimestamp = lastGoodIncrement.getRdiffTimestamp();
                processExecutor.executeProcess("rdiff-backup",
                        "--force", "--remove-older-than", rdiffTimestamp,
                        destinationDirectory.getPath());
            }
        }

        if (autoDeleteAgeCheckBox.isSelected()) {
            int age = ((Number) autoDeleteAgeSpinner.getValue()).intValue();
            String unit = null;
            int selectedIndex = autoDeleteAgeComboBox.getSelectedIndex();
            switch (selectedIndex) {
                case 0:
                    unit = "D";
                    break;
                case 1:
                    unit = "W";
                    break;
                case 2:
                    unit = "M";
                    break;
                case 3:
                    unit = "Y";
                    break;
                default:
                    LOGGER.log(Level.WARNING,
                            "unsupported age unit {0}", selectedIndex);
                    return;
            }
            processExecutor.executeProcess("rdiff-backup",
                    "--force", "--remove-older-than", age + unit,
                    destinationDirectory.getPath());
        }

        if (autoDeletionSpaceCheckBox.isSelected()) {
            if (increments == null) {
                RdiffFileDatabase rdiffFileDatabase =
                        rdiffChooserPanel.getRdiffFileDatabase();
                increments = rdiffFileDatabase.getIncrements();
            }

            Number sizeNumber = (Number) autoDeletionSpaceSpinner.getValue();
            int index = autoDeletionSpaceComboBox.getSelectedIndex();
            long maxSize = sizeNumber.longValue()
                    * (long) Math.pow(1024, index);

            long size = 0;
            for (int i = 0, j = increments.size(); i < j; i++) {
                Increment increment = increments.get(i);
                size += increment.getSize();
                if (size > maxSize) {
                    // delete increments
                    String rdiffTimestamp = null;
                    boolean showMirrorWarning = false;
                    if (i == 0) {
                        rdiffTimestamp = increment.getRdiffTimestamp();
                        showMirrorWarning = true;
                    } else {
                        Increment youngerIncrement =
                                increment.getYoungerIncrement();
                        rdiffTimestamp = youngerIncrement.getRdiffTimestamp();
                    }
                    if (showMirrorWarning && !shutdownCheckBox.isSelected()) {
                        String warningMessage = BUNDLE.getString(
                                "Warning_Auto_Delete_Space");
                        String sizeString =
                                FileTools.getDataVolumeString(size, 1);
                        String maxSizeString = sizeNumber.toString()
                                + autoDeletionSpaceComboBox.getSelectedItem();
                        warningMessage = MessageFormat.format(
                                warningMessage, sizeString, maxSizeString);
                        JOptionPane.showMessageDialog(parentFrame,
                                warningMessage, BUNDLE.getString("Warning"),
                                JOptionPane.WARNING_MESSAGE);
                    }
                    processExecutor.executeProcess("rdiff-backup",
                            "--force", "--remove-older-than", rdiffTimestamp,
                            destinationDirectory.getPath());
                    break;
                }
            }
        }
    }

    private long getFileSize(JTextField textField, JComboBox comboBox) {
        try {
            long size = Long.parseLong(textField.getText());
            long factor = (long) Math.pow(1024, comboBox.getSelectedIndex());
            return size * factor;
        } catch (NumberFormatException ex) {
        }
        return 0;
    }

    private void updateReminderTextField() {
        if (showReminder) {
            double[] timeouts = {1, 2};
            String[] timeoutStrings = {
                BUNDLE.getString("Every_Day"),
                BUNDLE.getString("Every_X_Days")
            };
            ChoiceFormat choiceFormat =
                    new ChoiceFormat(timeouts, timeoutStrings);
            MessageFormat messageFormat = new MessageFormat("{0}");
            messageFormat.setFormat(0, choiceFormat);
            Object[] arguments = {reminderTimeout};
            reminderTextField.setText(messageFormat.format(arguments));

        } else {
            reminderTextField.setText(BUNDLE.getString("None"));
        }
    }

    private void updateSourceDirState() {
        boolean customDir = otherSourceRadioButton.isSelected();
        if (!customDir) {
            backupSourceTextField.setText(USER_HOME);
        }
        backupSourceTextField.setEditable(customDir);
        backupSourceButton.setEnabled(customDir);
    }

    private void updateTempDirState() {
        boolean customDir = customTempDirRadioButton.isSelected();
        if (!customDir) {
            tempDirTextField.setText(System.getProperty("java.io.tmpdir"));
        }
        tempDirTextField.setEditable(customDir);
        tempDirBrowseButton.setEnabled(customDir);
    }

    private void restore(RdiffFile[] selectedFiles, File restoreDestination) {
        if (!destinationEncrypted && sshfsMounted) {
            // TODO: check for remote rdiff-backup
            runRestore(selectedFiles, restoreDestination);
        } else {
            runRestore(selectedFiles, restoreDestination);
        }
    }

    private void runRestore(
            RdiffFile[] selectedFiles, File restoreDestination) {

        rdiffBackupRestore = new RdiffBackupRestore();
        processCancelled = false;
        shutdownCheckBox.setText(BUNDLE.getString("Shutdown_After_Restore"));
        progressLabel.setText(BUNDLE.getString("Restoring_Files"));
        filenameLabel.setText(null);
        progressBar.setIndeterminate(true);
        timeLabel.setText(" ");
        showCard(this, "progressPanel");
        cancelButton.requestFocusInWindow();

        // execute restore operation outside of the Swing Event Thread
        currentSwingWorker = new RestoreSwingWorker(selectedFiles,
                new File(getBackupDestination()), restoreDestination);
        currentSwingWorker.execute();
    }

    private void encrypt() {
        NewEncfsDialog passwordDialog = new NewEncfsDialog(parentFrame);
        if (JOptionPane.OK_OPTION != passwordDialog.showDialog()) {
            return;
        }

        String destinationPath = getRawBackupDestination();
        if (destinationPath == null) {
            return;
        }

        final File destinationDirectory = new File(destinationPath);

        try {
            // setup temporary encfs
            final File encfsDir = FileTools.createTempDirectory(
                    ENCFS_SEARCH_STRING, null);
            String tmpEncfsMountPoint = encfsDir.getPath();
            File parentDir = destinationDirectory.getParentFile();
            final File tmpCipherDir = FileTools.createTempDirectory(parentDir,
                    destinationDirectory.getName() + ".cipher");
            String tmpCipherPath = tmpCipherDir.getPath();
            final String password = passwordDialog.getPassword();
            File passwordScript = processExecutor.createScript(
                    "#!/bin/sh" + LINE_SEPARATOR
                    + "echo \"" + password + '"');
            String passwordScriptPath = passwordScript.getPath();
            String setupScript = "#!/bin/sh" + LINE_SEPARATOR
                    + "echo \"\" | encfs --extpass=" + passwordScriptPath
                    + " \"" + tmpCipherPath + "\" " + tmpEncfsMountPoint;
            // the return value of the script is of no use...
            processExecutor.executeScript(setupScript);
            if (!passwordScript.delete()) {
                LOGGER.log(Level.WARNING,
                        "could not delete {0}", passwordScript);
            }
            // test, if setup above succeeded
            if (!FileTools.isEncFS(tmpCipherPath)) {
                LOGGER.log(Level.WARNING,
                        "failed to setup encfs in {0}", tmpCipherPath);
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Encryption_Failed"),
                        BUNDLE.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            final FilenameCheckSwingWorker filenameCheckSwingWorker =
                    new FilenameCheckSwingWorker(parentFrame, encfsDir);
            filenameCheckSwingWorker.execute();

            new Thread() {

                @Override
                public void run() {
                    try {
                        // determine maximum filename length in encfs
                        // rsync uses the following schema for temporary files
                        // during tansfer: ".<filename>.abcdef"
                        // therefore we need to subtract another eight
                        // characters
                        int maxFilenameLength =
                                filenameCheckSwingWorker.get() - 8;
                        // check source directory
                        final DirectoryCheckDialog directoryCheckDialog =
                                new DirectoryCheckDialog(parentFrame);
                        ModalDialogHandler dialogHandler =
                                new ModalDialogHandler(directoryCheckDialog);
                        directoryCheckDialog.setFilenameCheckEnabled(
                                true, maxFilenameLength);
                        EncryptionCheckSwingWorker encryptionCheckSwingWorker =
                                new EncryptionCheckSwingWorker(parentFrame,
                                BackupMainPanel.this, directoryCheckDialog,
                                dialogHandler, destinationDirectory,
                                tmpCipherDir, encfsDir, password,
                                maxFilenameLength);
                        if (FileTools.isSpaceKnown(destinationDirectory)) {
                            long usableSpace =
                                    destinationDirectory.getUsableSpace();
                            directoryCheckDialog.setFreeSpaceKnown(
                                    true, usableSpace);
                            encryptionCheckSwingWorker.setUsableSpace(
                                    usableSpace);
                        }
                        encryptionCheckSwingWorker.execute();
                        dialogHandler.show();
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    } catch (ExecutionException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                }
            }.start();

        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
    }

    private void checkForShutdown() {
        if (shutdownCheckBox.isSelected()) {
            savePreferences();
            String password = String.valueOf(
                    shutdownPasswordField.getPassword());
            String shutdownScript = "#!/bin/sh" + LINE_SEPARATOR
                    + "echo " + password + " | sudo -S shutdown -h now";
            try {
                processExecutor.executeScript(shutdownScript);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
            new Thread() {

                @Override
                public void run() {
                    // if we survive the next five seconds,
                    // shutdown (most probably) failed
                    try {
                        sleep(5000);
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }
                    SwingUtilities.invokeLater(new Runnable() {

                        public void run() {
                            JOptionPane.showMessageDialog(parentFrame,
                                    BUNDLE.getString("Shutdown_Failed"),
                                    BUNDLE.getString("Error"),
                                    JOptionPane.ERROR_MESSAGE);
                        }
                    });
                }
            }.start();
        }
    }

    private void systemCheck() {
        sshfsEnabled = true;
        encfsEnabled = true;
        switch (CurrentOperatingSystem.OS) {
            case Mac_OS_X:
            case Linux:
                // expect is necessary for both sshfs and encfs password changes
                int returnValue = processExecutor.executeProcess(
                        "which", "expect");
                if (returnValue == 0) {
                    returnValue = processExecutor.executeProcess(
                            "sshfs", "--version");
                    if (returnValue != 0) {
                        JEditorPane editorPane = new JEditorPane("text/html",
                                BUNDLE.getString(
                                (CurrentOperatingSystem.OS
                                == OperatingSystem.Mac_OS_X)
                                ? "Warning_No_SSHFS_OSX" : "Warning_No_SSHFS"));
                        Color background = UIManager.getDefaults().getColor(
                                "Panel.background");
                        editorPane.setBackground(background);
                        JOptionPane.showMessageDialog(parentFrame,
                                editorPane, BUNDLE.getString("Warning"),
                                JOptionPane.WARNING_MESSAGE);
                        disableSshfs();
                        sshRadioButton.setToolTipText(
                                BUNDLE.getString("Warning_No_SSHFS"));
                    }
                } else {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Warning_No_Expect"),
                            BUNDLE.getString("Warning"),
                            JOptionPane.WARNING_MESSAGE);
                    disableSshfs();
                    sshRadioButton.setToolTipText(
                            BUNDLE.getString("Tooltip_No_Expect_SSH"));
                    changePasswordButton.setEnabled(false);
                    changePasswordButton.setToolTipText(
                            BUNDLE.getString("Tooltip_No_Expect_Password"));
                }

                // encryption checks
                returnValue = processExecutor.executeProcess(
                        "which", "encfs");
                if (returnValue == 0) {
                    returnValue = processExecutor.executeProcess(
                            "rsync", "--version");
                    if (returnValue != 0) {
                        JOptionPane.showMessageDialog(parentFrame,
                                BUNDLE.getString("Warning_No_Rsync"),
                                BUNDLE.getString("Warning"),
                                JOptionPane.WARNING_MESSAGE);
                        disableEncfs();
                        encryptionButton.setToolTipText(
                                BUNDLE.getString("Warning_No_Rsync"));
                    }
                } else {
                    JEditorPane editorPane = new JEditorPane("text/html",
                            BUNDLE.getString(
                            (CurrentOperatingSystem.OS
                            == OperatingSystem.Mac_OS_X)
                            ? "Warning_No_ENCFS_OSX" : "Warning_No_ENCFS"));
                    Color background = UIManager.getDefaults().getColor(
                            "Panel.background");
                    editorPane.setBackground(background);
                    JOptionPane.showMessageDialog(parentFrame,
                            editorPane, BUNDLE.getString("Warning"),
                            JOptionPane.WARNING_MESSAGE);
                    disableEncfs();
                    encryptionButton.setToolTipText(
                            BUNDLE.getString("Warning_No_ENCFS"));
                }
                break;

            default:
                disableSshfs();
                sshRadioButton.setToolTipText(
                        BUNDLE.getString("Tooltip_SSHFS"));
                disableEncfs();
                encryptionButton.setToolTipText(
                        BUNDLE.getString("Tooltip_ENCFS"));
                shutdownCheckBox.setEnabled(false);
                shutdownCheckBox.setToolTipText(
                        BUNDLE.getString("Tooltip_Shutdown"));
        }
    }

    private void disableSshfs() {
        sshfsEnabled = false;
        localRadioButton.setSelected(true);
        sshRadioButton.setEnabled(false);
    }

    private void disableEncfs() {
        encfsEnabled = false;
        encryptionButton.setEnabled(false);
    }

    private void lock() {
        RdiffFileDatabase rdiffFileDatabase =
                rdiffChooserPanel.getRdiffFileDatabase();
        if (rdiffFileDatabase != null) {
            rdiffFileDatabase.close();
        }
        if (FileTools.umountFUSE(new File(encfsMountPoint), true)) {
            encfsMountPoint = null;
            updateLockButton(false);
        } else {
            // TODO: error processing
        }
    }

    private boolean unlock(boolean switchToBackup) {
        UnlockEncfsDialog dialog = new UnlockEncfsDialog(parentFrame);
        if (JOptionPane.OK_OPTION == dialog.showDialog()) {
            String password = dialog.getPassword();
            try {
                File backupDir = new File(getRawBackupDestination());
                File encfsMountDir = FileTools.createTempDirectory(
                        ENCFS_SEARCH_STRING, null);
                encfsMountPoint = encfsMountDir.getPath();
                if (FileTools.mountEncFs(
                        backupDir.getPath(), encfsMountPoint, password)) {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Destination_Unlocked"),
                            BUNDLE.getString("Information"),
                            JOptionPane.INFORMATION_MESSAGE,
                            IconManager.INFORMATION_ICON);
                    // do not call destinationChanged() !!!
                    // (it just locks the directory again...)
                    checkDestinationCommon();
                    if (switchToBackup) {
                        mainTabbedPane.setSelectedComponent(backupCardPanel);
                    }
                    return true;
                } else {
                    encfsMountDir.delete();
                    encfsMountPoint = null;
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Unlocking_Error"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                    // retry recursively
                    return unlock(switchToBackup);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        return false;
    }

    private boolean checkSourceCommon(String sourcePath, File sourceDirectory) {
        if (sourcePath.length() == 0) {
            String directoriesTabName = BUNDLE.getString(
                    "BackupMainPanel.directoriesPanel.TabConstraints.tabTitle");
            showErrorPanels("Error_No_Source_Directory", directoriesTabName);
            return false;
        }
        if (!sourceDirectory.exists()) {
            showErrorPanels("Error_Source_Directory_Does_Not_Exist");
            return false;
        }
        if (!sourceDirectory.isDirectory()) {
            showErrorPanels("Error_Source_No_Directory");
            return false;
        }
        return true;
    }

    private boolean checkSourceBackup(File sourceDirectory) {
        if (!sourceDirectory.canRead()) {
            showBackupErrorPanel(
                    BUNDLE.getString("Error_Source_Directory_Unreadable"));
            return false;
        }
        return true;
    }

    private boolean checkSourceRestore(File sourceDirectory) {
        if (!FileTools.canWrite(sourceDirectory)) {
            showRestoreErrorPanel(BUNDLE.getString("Error_Source_Read-Only"));
            return false;
        }

        // check that user can change permissions of this directory
        if (sourceDirectory.setWritable(false)) {
            sourceDirectory.setWritable(true);
        } else {
            showRestoreErrorPanel(BUNDLE.getString(
                    "Error_Source_Directory_Unmodifiable"));
            return false;
        }

        return true;
    }

    private boolean checkDestinationBackup() {
        String destinationPath = getBackupDestination();
        File destinationDirectory = null;
        if (destinationPath == null) {
            return false;
        } else {
            destinationDirectory = new File(destinationPath);
            if (!FileTools.canWrite(destinationDirectory)) {
                showBackupErrorPanel(
                        BUNDLE.getString("Error_Destination_Read-Only"));
                return false;
            }
        }

        // rdiff-backup checks
        File rdiffBackupDataDir =
                new File(destinationDirectory, "rdiff-backup-data");
        if (rdiffBackupDataDir.exists()
                && (!rdiffBackupDataDir.canRead()
                || !FileTools.canWrite(rdiffBackupDataDir))) {
            if (LOGGER.isLoggable(Level.INFO)) {
                LOGGER.log(Level.INFO, "can not access {0}{1}rdiff-backup-data",
                        new Object[]{destinationDirectory, File.separatorChar});
            }
            showBackupErrorPanel(BUNDLE.getString("Error_Accessing_Backup"));
            return false;
        }

        return true;
    }

    private boolean checkTempDirectory() {
        String tempPath = tempDirTextField.getText();
        if (tempPath.isEmpty()) {
            showErrorPanels("Error_No_Temp_Directory");
            return false;
        }
        File tempDir = new File(tempPath);
        if (!tempDir.exists()) {
            showErrorPanels("Error_Temp_Directory_Does_Not_Exist");
            return false;
        }
        if (!tempDir.isDirectory()) {
            showErrorPanels("Error_Temp_No_Directory");
            return false;
        }
        if (!tempDir.canRead()) {
            showErrorPanels("Error_Temp_Directory_Unreadable");
            return false;
        }
        if (!FileTools.canWrite(tempDir)) {
            showErrorPanels("Error_Temp_Read-Only");
            return false;
        }
        return true;
    }

    private void showErrorPanels(String errorMessageKey, Object... arguments) {
        String errorMessage = BUNDLE.getString(errorMessageKey);
        errorMessage = MessageFormat.format(errorMessage, arguments);
        showBackupErrorPanel(errorMessage);
        showRestoreErrorPanel(errorMessage);
    }

    private void showBackupErrorPanel(String errorMessage) {
        backupErrorLabel.setText(errorMessage);
        showCard(backupCardPanel, "backupErrorPanel");
    }

    private void showRestoreErrorPanel(String errorMessage) {
        restoreErrorLabel.setText(errorMessage);
        showCard(restoreCardPanel, "restoreErrorPanel");
    }

    private void updateLockButton(boolean mounted) {
        if (mounted) {
            lockButton.setText(BUNDLE.getString("Lock"));
            lockButton.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/jbackpack/icons/16x16/encrypted.png")));
            lockButton.setActionCommand(LOCK);
        } else {
            lockButton.setText(BUNDLE.getString("Unlock"));
            lockButton.setIcon(new ImageIcon(getClass().getResource(
                    "/ch/fhnw/jbackpack/icons/16x16/password.png")));
            lockButton.setActionCommand(UNLOCK);
        }
    }

    private String getBackupDestination() {
        if (destinationEncrypted) {
            return encfsMountPoint;
        }
        return getRawBackupDestination();
    }

    private String getRawBackupDestination() {
        if (localRadioButton.isSelected()) {
            return localStorageTextField.getText();

        } else if (sshRadioButton.isSelected()) {
            try {
                return getSshfsMountPoint() + File.separatorChar
                        + sshStorageTextField.getText();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

        } else if (smbRadioButton.isSelected()) {
            try {
                return getSmbfsMountPoint() + File.separatorChar
                        + smbStorageTextField.getText();
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        return null;
    }

    private void directoriesTabFocusHandling() {
        if (backupSourceTextField.getText().length() == 0) {
            backupSourceTextField.requestFocusInWindow();
        } else if (localRadioButton.isSelected()
                && localStorageTextField.getText().length() == 0) {
            localStorageTextField.requestFocusInWindow();
        } else if (sshRadioButton.isSelected()) {
            if (sshfsMounted || !sshFocusHandling()) {
                if (sshStorageTextField.getText().length() == 0) {
                    sshStorageTextField.requestFocusInWindow();
                } else {
                    backupSourceTextField.requestFocusInWindow();
                }
            }
        } else if (smbRadioButton.isSelected()) {
            if (smbfsMounted || !smbFocusHandling()) {
                if (smbStorageTextField.getText().length() == 0) {
                    smbStorageTextField.requestFocusInWindow();
                } else {
                    backupSourceTextField.requestFocusInWindow();
                }
            }
        }
    }

    private boolean sshFocusHandling() {
        if (sshServerTextField.getText().length() == 0) {
            sshServerTextField.requestFocusInWindow();
            return true;
        } else if (sshUserNameTextField.getText().length() == 0) {
            sshUserNameTextField.requestFocusInWindow();
            return true;
        } else if (sshPasswordRadioButton.isSelected()
                && sshPasswordField.getPassword().length == 0) {
            sshPasswordField.requestFocusInWindow();
            return true;
        } else if (sshLogInOutButton.isEnabled()
                && LOGIN.equals(sshLogInOutButton.getActionCommand())) {
            sshLogInOutButton.requestFocusInWindow();
            return true;
        }
        return false;
    }

    private boolean smbFocusHandling() {
        if (smbServerTextField.getText().length() == 0) {
            smbServerTextField.requestFocusInWindow();
            return true;
        } else if (smbShareTextField.getText().length() == 0) {
            smbShareTextField.requestFocusInWindow();
            return true;
        } else if (smbUserTextField.getText().length() == 0) {
            smbUserTextField.requestFocusInWindow();
            return true;
        } else if (smbPasswordField.getPassword().length == 0) {
            smbPasswordField.requestFocusInWindow();
            return true;
        } else if (smbSudoPasswordField.getPassword().length == 0) {
            smbSudoPasswordField.requestFocusInWindow();
            return true;
        } else if (smbLogInOutButton.isEnabled()
                && LOGIN.equals(smbLogInOutButton.getActionCommand())) {
            smbLogInOutButton.requestFocusInWindow();
            return true;
        }
        return false;
    }

    private boolean checkFUSE() {
        if (CurrentOperatingSystem.OS == OperatingSystem.Linux) {
            // test, if /dev/fuse is readable AND writable
            File devFuse = new File("/dev/fuse");
            if (!devFuse.canRead() || !devFuse.canWrite()) {
                JOptionPane.showMessageDialog(parentFrame,
                        BUNDLE.getString("Error_Fuse"),
                        BUNDLE.getString("Error"),
                        JOptionPane.ERROR_MESSAGE);
                return false;
            }
        }
        return true;
    }

    private void smbLogin(final boolean switchToBackup) {

        // sanity checks
        String host = smbServerTextField.getText();
        if ((host == null) || host.isEmpty()) {
            showError("Error_No_Server");
            smbServerTextField.requestFocusInWindow();
            return;
        }
        String share = smbShareTextField.getText();
        if ((share == null) || share.isEmpty()) {
            showError("Error_No_Share");
            smbShareTextField.requestFocusInWindow();
            return;
        }

        smbPasswordField.setEnabled(false);
        smbSudoPasswordField.setEnabled(false);
        smbLogInOutButton.setEnabled(false);
        smbLoginProgressBar.setIndeterminate(true);

        // execute the blocking SMB login process in a background thread
        SmbLoginSwingWorker smbLoginSwingWorker =
                new SmbLoginSwingWorker(host, share, switchToBackup);
        smbLoginSwingWorker.execute();
    }

    private void sshLogin(boolean switchToBackup) {

        if (!checkFUSE()) {
            return;
        }

        // sanity checks
        String host = sshServerTextField.getText();
        if (host == null || host.isEmpty()) {
            showError("Error_No_Server");
            sshServerTextField.requestFocusInWindow();
            return;
        }
        String user = sshUserNameTextField.getText();
        if (user == null || user.isEmpty()) {
            showError("Error_No_User");
            sshUserNameTextField.requestFocusInWindow();
            return;
        }

        sshPasswordField.setEnabled(false);
        sshLogInOutButton.setEnabled(false);
        sshLoginProgressBar.setIndeterminate(true);

        // execute the blocking SSH login process in a background thread
        SshLoginSwingWorker sshLoginSwingWorker =
                new SshLoginSwingWorker(host, user, switchToBackup);
        sshLoginSwingWorker.execute();
    }

    private String getSshfsMountPoint() throws IOException {
        String user = sshUserNameTextField.getText();
        String server = sshServerTextField.getText();
        return FileTools.getMountPoint(user + '@' + server + ':');
    }

    private String getSmbfsMountPoint() throws IOException {
        String server = smbServerTextField.getText();
        String share = smbShareTextField.getText();
        String searchString = null;
        switch (CurrentOperatingSystem.OS) {
            case Linux:
                searchString = "//" + server + '/' + share;
                break;

            case Mac_OS_X:
                searchString = "//";
                String smbUser = smbUserTextField.getText();
                if ((smbUser != null) && !smbUser.isEmpty()) {
                    searchString += (smbUser + "@");
                }
                searchString += (server + '/' + share);
                break;

            case Windows:
                searchString = "\\\\" + server + '\\' + share;
                break;

            default:
                LOGGER.log(Level.WARNING,
                        "{0} is not supported", CurrentOperatingSystem.OS);
        }
        return FileTools.getMountPoint(searchString);
    }

    private void setSshMounted(boolean mounted) {
        sshfsMounted = mounted;
        sshServerTextField.setEditable(!mounted);
        sshUserNameTextField.setEditable(!mounted);
        sshBaseDirTextField.setEditable(!mounted);
        sshPasswordRadioButton.setEnabled(!mounted);
        sshPasswordField.setEditable(!mounted);
        sshPasswordField.setEnabled(!mounted);
        sshPublicKeyRadioButton.setEnabled(!mounted);
        if (mounted) {
            sshLogInOutButton.setText(BUNDLE.getString("Logout"));
            sshLogInOutButton.setActionCommand(LOGOUT);
            sshLoginProgressBar.setString(BUNDLE.getString("Logged_In"));
        } else {
            sshLogInOutButton.setText(BUNDLE.getString("Login"));
            sshLogInOutButton.setActionCommand(LOGIN);
            sshLoginProgressBar.setString(BUNDLE.getString("Not_Logged_In"));
        }
        sshStorageLabel.setEnabled(mounted);
        sshStorageTextField.setEnabled(mounted);
        sshStorageButton.setEnabled(mounted);
    }

    private void setSmbMounted(boolean mounted) {
        smbfsMounted = mounted;
        smbServerTextField.setEditable(!mounted);
        smbShareTextField.setEditable(!mounted);
        smbUserTextField.setEditable(!mounted);
        smbPasswordField.setEditable(!mounted);
        smbPasswordField.setEnabled(!mounted);
        if (mounted) {
            smbLogInOutButton.setText(BUNDLE.getString("Logout"));
            smbLogInOutButton.setActionCommand(LOGOUT);
            smbLoginProgressBar.setString(BUNDLE.getString("Logged_In"));
        } else {
            smbLogInOutButton.setText(BUNDLE.getString("Login"));
            smbLogInOutButton.setActionCommand(LOGIN);
            smbLoginProgressBar.setString(BUNDLE.getString("Not_Logged_In"));
        }
        smbStorageLabel.setEnabled(mounted);
        smbStorageTextField.setEnabled(mounted);
        smbStorageButton.setEnabled(mounted);
    }

    private void showError(String messageKey) {
        showError(messageKey, null);
    }

    private void showError(String messageKey, JTextField textField) {
        JOptionPane.showMessageDialog(parentFrame,
                BUNDLE.getString(messageKey),
                BUNDLE.getString("Error"),
                JOptionPane.ERROR_MESSAGE);
        if (textField != null) {
            mainTabbedPane.setSelectedComponent(directoriesPanel);
            textField.selectAll();
            textField.requestFocusInWindow();
        }
    }

    private void showCard(Container container, String cardName) {
        LayoutManager layoutManager = container.getLayout();
        if (layoutManager instanceof CardLayout) {
            CardLayout cardLayout = (CardLayout) layoutManager;
            cardLayout.show(container, cardName);
        }
    }

    private void addSelectedFiles(String fileChooserDialogTitle,
            File baseDirectory, JTextArea list) {
        final JFileChooser fileChooser = new JFileChooser();
        fileChooser.addPropertyChangeListener(new PropertyChangeListener() {

            public void propertyChange(PropertyChangeEvent evt) {
                if (evt.getPropertyName().equals(
                        JFileChooser.FILE_FILTER_CHANGED_PROPERTY)) {
                    fileChooser.setFileHidingEnabled(
                            fileChooser.getFileFilter()
                            == NO_HIDDEN_FILES_SWING_FILE_FILTER);
                    fileChooser.rescanCurrentDirectory();
                }
            }
        });
        fileChooser.setDialogTitle(fileChooserDialogTitle);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        fileChooser.addChoosableFileFilter(NO_HIDDEN_FILES_SWING_FILE_FILTER);
        fileChooser.setFileFilter(NO_HIDDEN_FILES_SWING_FILE_FILTER);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setCurrentDirectory(baseDirectory);
        fileChooser.setApproveButtonText(BUNDLE.getString("Choose"));
        if (JFileChooser.APPROVE_OPTION
                == fileChooser.showOpenDialog(parentFrame)) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            Document document = list.getDocument();
            for (File selectedFile : selectedFiles) {
                try {
                    int length = document.getLength();
                    if (length != 0) {
                        document.insertString(length, LINE_SEPARATOR, null);
                        length = document.getLength();
                    }
                    String path = RdiffBackupRestore.quoteBackup(
                            baseDirectory.getPath(), selectedFile.getPath());
                    document.insertString(length, path, null);
                } catch (BadLocationException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
        }
    }

    private String getStatisticsLine(Map<String, String> content,
            String mapKey, NumberFormat numberFormat, String bundlekey) {
        String rawNumberString = content.get(mapKey);
        long number = Long.parseLong(rawNumberString);
        String formattedNumberString = numberFormat.format(number);
        String line = BUNDLE.getString(bundlekey);
        return MessageFormat.format(line, formattedNumberString);
    }

    private void fillStatisticsTextField(
            Map<String, String> content, String timeString) {

        NumberFormat numberFormat = NumberFormat.getInstance();
        numberFormat.setGroupingUsed(true);
        StringBuilder stringBuilder = new StringBuilder();
        String newFilesLine = getStatisticsLine(
                content, "NewFiles", numberFormat, "New_Files");
        stringBuilder.append(newFilesLine);
        stringBuilder.append(LINE_SEPARATOR);
        String deletedFilesLine = getStatisticsLine(
                content, "DeletedFiles", numberFormat, "Deleted_Files");
        stringBuilder.append(deletedFilesLine);
        stringBuilder.append(LINE_SEPARATOR);
        String changedFilesLine = getStatisticsLine(
                content, "ChangedFiles", numberFormat, "Changed_Files");
        stringBuilder.append(changedFilesLine);
        stringBuilder.append(LINE_SEPARATOR);
        String rawChangeString = content.get("TotalDestinationSizeChange");
        long totalChange = Long.parseLong(rawChangeString);
        String formattedTotalChange =
                FileTools.getDataVolumeString(totalChange, 2);
        String totalChangeLine = BUNDLE.getString("Total_Changed_Size");
        totalChangeLine =
                MessageFormat.format(totalChangeLine, formattedTotalChange);
        stringBuilder.append(totalChangeLine);
        stringBuilder.append(LINE_SEPARATOR);
        String elapsedTimeLine = BUNDLE.getString("Elapsed_Time");
        elapsedTimeLine = MessageFormat.format(elapsedTimeLine, timeString);
        stringBuilder.append(elapsedTimeLine);
        statisticsTextField.setText(stringBuilder.toString());
    }

    private void documentChanged(DocumentEvent e) {
        Document document = e.getDocument();
        if (document.equals(tempDirTextField.getDocument())) {
            // show some space information
            File testFile = new File(tempDirTextField.getText());
            FileTools.showSpaceInfo(testFile, storageUsageProgressBar);
        } else {
            destinationChanged();
        }
    }

    private void destinationChanged() {
        if (destinationEncrypted && (encfsMountPoint != null)) {
            lock();
        }
        checkDestinationCommon();
    }

    private class SshLoginSwingWorker extends SwingWorker<Boolean, Void> {

        private final String host;
        private final String user;
        private final boolean switchToBackup;

        public SshLoginSwingWorker(String host, String user,
                boolean switchToBackup) {
            this.host = host;
            this.user = user;
            this.switchToBackup = switchToBackup;
        }

        @Override
        protected Boolean doInBackground() {
            try {
                String mountPoint = FileTools.createMountPoint(
                        new File(USER_HOME), host).getPath();
                String userHostDir = user + '@' + host + ':';
                String baseDir = sshBaseDirTextField.getText();
                if (!baseDir.isEmpty()) {
                    if (!baseDir.startsWith("/")) {
                        userHostDir += '/';
                    }
                    userHostDir += baseDir;
                }

                if (sshPublicKeyRadioButton.isSelected()) {
                    // collect output for error reporting
                    int returnValue = processExecutor.executeProcess(true,
                            true, "sshfs", "-o", "ServerAliveInterval=15",
                            "-o", "workaround=rename,idmap=user",
                            userHostDir, mountPoint);
                    return (returnValue == 0);

                } else {
                    // authentication with username and password
                    String password = new String(
                            sshPasswordField.getPassword());
                    String loginScript = "#!/usr/bin/expect -f" + LINE_SEPARATOR
                            + "set password [lindex $argv 0]" + LINE_SEPARATOR
                            + "spawn -ignore HUP sshfs -o "
                            + "workaround=rename,idmap=user "
                            + userHostDir + " " + mountPoint + LINE_SEPARATOR
                            + "while 1 {" + LINE_SEPARATOR
                            + "    expect {" + LINE_SEPARATOR
                            + "        eof {" + LINE_SEPARATOR
                            + "            break" + LINE_SEPARATOR
                            + "        }" + LINE_SEPARATOR
                            + "        \"continue connecting*\" {"
                            + LINE_SEPARATOR
                            + "            send \"yes\r\"" + LINE_SEPARATOR
                            + "        }" + LINE_SEPARATOR
                            + "        \"password:\" {" + LINE_SEPARATOR
                            + "            send \"$password\r\""
                            + LINE_SEPARATOR
                            + "        }" + LINE_SEPARATOR
                            + "    }" + LINE_SEPARATOR
                            + "}" + LINE_SEPARATOR
                            + "set ret [lindex [wait] 3]" + LINE_SEPARATOR
                            + "puts \"return value: $ret\"" + LINE_SEPARATOR
                            + "exit $ret";

                    // set level to OFF to prevent password leaking into
                    // logfiles
                    Logger logger = Logger.getLogger(
                            ProcessExecutor.class.getName());
                    Level level = logger.getLevel();
                    logger.setLevel(Level.OFF);

                    // TODO: the loginScript blocks when storing any output...
                    int returnValue = processExecutor.executeScript(
                            loginScript, password);

                    // restore previous log level
                    logger.setLevel(level);

                    return (returnValue == 0);
                }
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "SSH login failed", exception);
            }
            return false;
        }

        @Override
        protected void done() {

            sshLogInOutButton.setEnabled(true);
            sshLoginProgressBar.setIndeterminate(false);

            boolean success = false;
            try {
                success = get();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            if (success) {
                setSshMounted(true);
                destinationChanged();
                if (destinationEncrypted
                        && (encfsMountPoint == null)
                        && (!unlock(switchToBackup))) {
                    return;
                }
                if (switchToBackup
                        && commonDestinationOK
                        && checkDestinationBackup()) {
                    mainTabbedPane.setSelectedComponent(backupCardPanel);
                }

            } else {
                sshPasswordField.setEnabled(true);
                String errorMessage = processExecutor.getOutput();
                if ((errorMessage != null) && (errorMessage.length() > 0)) {
                    ErrorDialog dialog = new ErrorDialog(parentFrame,
                            BUNDLE.getString("Error_Login_Failed"),
                            errorMessage);
                    dialog.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Login_Failed"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private class SmbLoginSwingWorker extends SwingWorker<Boolean, Void> {

        private final String host;
        private final String share;
        private final boolean switchToBackup;

        public SmbLoginSwingWorker(String host, String share,
                boolean switchToBackup) {
            this.host = host;
            this.share = share;
            this.switchToBackup = switchToBackup;
        }

        @Override
        protected Boolean doInBackground() {
            String user = smbUserTextField.getText();
            String smbPassword = new String(smbPasswordField.getPassword());

            int returnValue = -1;
            try {
                switch (CurrentOperatingSystem.OS) {
                    case Linux:
                        String sudoPassword = new String(
                                smbSudoPasswordField.getPassword());
                        returnValue = FileTools.mountSmbLinux(host, share,
                                user, smbPassword, sudoPassword);
                        break;

                    case Mac_OS_X:
                        returnValue = FileTools.mountSmbMacOSX(host, share,
                                user, smbPassword);
                        break;

                    case Windows:
                        returnValue = FileTools.mountSmbWindows(host, share,
                                user, smbPassword);
                        break;

                    default:
                        LOGGER.log(Level.WARNING, "{0} is not supported",
                                CurrentOperatingSystem.OS);
                }
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "SMB login failed", exception);
            }
            return (returnValue == 0);
        }

        @Override
        protected void done() {

            smbLogInOutButton.setEnabled(true);
            smbLoginProgressBar.setIndeterminate(false);

            boolean success = false;
            try {
                success = get();
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }

            if (success) {
                setSmbMounted(true);
                destinationChanged();
                if (destinationEncrypted
                        && (encfsMountPoint == null)
                        && (!unlock(switchToBackup))) {
                    return;
                }
                if (switchToBackup
                        && commonDestinationOK
                        && checkDestinationBackup()) {
                    mainTabbedPane.setSelectedComponent(backupCardPanel);
                }

            } else {
                smbPasswordField.setEnabled(true);
                String errorMessage = processExecutor.getOutput();
                if ((errorMessage != null) && (errorMessage.length() > 0)) {
                    ErrorDialog dialog = new ErrorDialog(parentFrame,
                            BUNDLE.getString("Error_Login_Failed"),
                            errorMessage);
                    dialog.setVisible(true);
                } else {
                    JOptionPane.showMessageDialog(parentFrame,
                            BUNDLE.getString("Login_Failed"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
            smbSudoPasswordField.setEnabled(true);
        }
    }

    private class BackupSwingWorker extends SwingWorker<Boolean, Void> {

        private final long start;
        private final File sourceDirectory;
        private final File destinationDirectory;
        private final String destinationPath;
        private final Long maxFileSize;
        private final Long minFileSize;
        private final Timer backupTimer;
        private final boolean directSSH;
        private final String sshPassword;

        public BackupSwingWorker(String sourcePath, String destinationPath,
                Long maxFileSize, Long minFileSize,
                boolean directSSH, String sshPassword) {

            this.sourceDirectory = new File(sourcePath);
            this.destinationDirectory = new File(destinationPath);
            this.destinationPath = destinationPath;
            this.maxFileSize = maxFileSize;
            this.minFileSize = minFileSize;
            this.directSSH = directSSH;
            this.sshPassword = sshPassword;

            start = System.currentTimeMillis();
            backupTimer = new Timer(1000, new BackupActionListener(start));
            backupTimer.setInitialDelay(0);
            backupTimer.start();
        }

        @Override
        protected Boolean doInBackground() {
            try {
                return runJob();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "backup failed", exception);
            }
            return false;
        }

        @Override
        protected void done() {
            backupTimer.stop();
            try {
                if (get()) {

                    // update timestamp for JBackpack reminder
                    long now = System.currentTimeMillis();
                    preferences.putLong(JBackpack.LAST_BACKUP, now);
                    try {
                        LOGGER.log(Level.INFO, "flushing preferences");
                        preferences.flush();
                    } catch (BackingStoreException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    }

                    // automatic deletion of certain increments
                    autoDeletion(destinationDirectory);

                    // automatic system shutdown (if selected)
                    checkForShutdown();

                    long time = now - start;
                    String timeString = timeFormat.format(new Date(time));
                    Map<String, String> backupSessionStatistics =
                            rdiffBackupRestore.getBackupSessionStatistics(
                            destinationPath);
                    fillStatisticsTextField(
                            backupSessionStatistics, timeString);
                    showCard(BackupMainPanel.this,
                            "sessionStatisticsPanel");
                    quitButton.requestFocusInWindow();

                } else {
                    if (!processCancelled) {
                        String errorMessage =
                                rdiffBackupRestore.getStdErr();
                        ErrorDialog dialog = new ErrorDialog(parentFrame,
                                BUNDLE.getString(
                                "Error_Rdiffbackup_Failed"), errorMessage);
                        dialog.setVisible(true);
                    }
                    showCard(BackupMainPanel.this, "mainTabbedPane");
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (CancellationException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }

        private Boolean runJob() throws IOException {
            boolean exclude = excludeCheckBox.isSelected();
            // some preliminary exclude checks
            String excludes = exclude ? excludesTextArea.getText() : "";
            String subDirCheckPath = null;
            if (localRadioButton.isSelected()) {
                subDirCheckPath = destinationPath;
            } else if (sshRadioButton.isSelected()) {
                subDirCheckPath = getSshfsMountPoint();
            } else if (smbRadioButton.isSelected()) {
                subDirCheckPath = getSmbfsMountPoint();
            }
            File subDirCheckFile = new File(subDirCheckPath);
            if (FileTools.isSubDir(sourceDirectory, subDirCheckFile)) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.log(Level.INFO, "{0} is a subdirectory of {1}, "
                            + "adding {0} to exclusions",
                            new Object[]{subDirCheckFile, sourceDirectory});
                }
                if ((excludes == null) || excludes.isEmpty()) {
                    excludes = subDirCheckPath;
                } else {
                    excludes += (LINE_SEPARATOR + subDirCheckPath);
                }
            }

            boolean include = exclude && includesCheckBox.isSelected();
            if (directSSH) {
                String sshStorage = sshStorageTextField.getText();
                while (sshStorage.startsWith(File.separator)) {
                    sshStorage = sshStorage.substring(File.separator.length());
                }
                String sshBaseDirectory = sshBaseDirTextField.getText();
                if (!sshBaseDirectory.isEmpty()) {
                    sshStorage = sshBaseDirectory + '/'
                            + sshStorageTextField.getText();
                }
                return rdiffBackupRestore.backupViaSSH(sourceDirectory,
                        sshUserNameTextField.getText(),
                        sshServerTextField.getText(), sshStorage,
                        sshPassword, tempDirTextField.getText(),
                        excludes,
                        include ? includesTextArea.getText() : "",
                        compressionCheckBox.isSelected(),
                        maxFileSize, minFileSize,
                        exclude && excludeDeviceFilesCheckBox.isSelected(),
                        exclude && excludeFifosCheckBox.isSelected(),
                        exclude && excludeOtherFileSystemsCheckBox.isSelected(),
                        exclude && excludeSocketsCheckBox.isSelected(),
                        exclude && excludeSymlinksCheckBox.isSelected());
            } else {
                return rdiffBackupRestore.backupViaFileSystem(
                        sourceDirectory, destinationDirectory,
                        tempDirTextField.getText(), excludes,
                        include ? includesTextArea.getText() : "",
                        compressionCheckBox.isSelected(),
                        maxFileSize, minFileSize,
                        exclude && excludeDeviceFilesCheckBox.isSelected(),
                        exclude && excludeFifosCheckBox.isSelected(),
                        exclude && excludeOtherFileSystemsCheckBox.isSelected(),
                        exclude && excludeSocketsCheckBox.isSelected(),
                        exclude && excludeSymlinksCheckBox.isSelected());
            }
        }
    }

    private class BackupActionListener implements ActionListener {

        private final long start;

        public BackupActionListener(long start) {
            this.start = start;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            long fileCounter = rdiffBackupRestore.getFileCounter();
            if (fileCounter > 0) {
                String string = BUNDLE.getString("Backing_Up_File");
                string = MessageFormat.format(string, fileCounter);
                progressLabel.setText(string);
                String currentFile = rdiffBackupRestore.getCurrentFile();
                filenameLabel.setText(currentFile);
            }
            // update time information
            long time = System.currentTimeMillis() - start;
            String timeString = timeFormat.format(new Date(time));
            timeLabel.setText(timeString);
        }
    }

    private class RestoreSwingWorker extends SwingWorker<Boolean, Void> {

        private final long start;
        private final RdiffFile[] selectedFiles;
        private final File backupDirectory;
        private final File restoreDirectory;
        private final boolean countFiles;
        private final Timer timer;

        public RestoreSwingWorker(RdiffFile[] selectedFiles,
                File backupDirectory, File restoreDirectory) {
            this.start = System.currentTimeMillis();
            this.selectedFiles = selectedFiles;
            this.backupDirectory = backupDirectory;
            this.restoreDirectory = restoreDirectory;
            this.countFiles = countFilesCheckBox.isSelected();
            timer = new Timer(
                    1000, new RestoreActionListener(start, countFiles));
            timer.setInitialDelay(0);
            timer.start();
        }

        @Override
        protected Boolean doInBackground() {
            try {
                Increment increment = selectedFiles[0].getIncrement();
                return rdiffBackupRestore.restore(increment.getRdiffTimestamp(),
                        selectedFiles, backupDirectory,
                        restoreDirectory, tempDirTextField.getText(),
                        countFiles);
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "restore failed", exception);
            }
            return false;
        }

        @Override
        protected void done() {

            timer.stop();

            try {
                if (get()) {
                    checkForShutdown();
                    long time = System.currentTimeMillis() - start;
                    String timeString = timeFormat.format(new Date(time));
                    String text = BUNDLE.getString("Restoring_Successfull");
                    text = MessageFormat.format(text, timeString);
                    restoredLabel.setText(text);
                    showCard(BackupMainPanel.this, "backupRestoredPanel");
                    restoredOKButton.requestFocusInWindow();

                } else {
                    if (processCancelled) {
                        LOGGER.warning("restore operation was cancelled");
                    } else {
                        String errorMessage =
                                rdiffBackupRestore.getStdErr();
                        ErrorDialog dialog = new ErrorDialog(parentFrame,
                                BUNDLE.getString(
                                "Error_Rdiffbackup_Failed"), errorMessage);
                        dialog.setVisible(true);
                    }
                    showCard(BackupMainPanel.this, "mainTabbedPane");
                }
            } catch (CancellationException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            } catch (ExecutionException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
    }

    private class RestoreActionListener implements ActionListener {

        private final long start;
        private final boolean countFiles;

        public RestoreActionListener(long start, boolean countFiles) {
            this.start = start;
            this.countFiles = countFiles;
        }

        public void actionPerformed(ActionEvent e) {
            // update progress information
            String currentFile = rdiffBackupRestore.getCurrentFile();
            long restoreCounter = rdiffBackupRestore.getRestoreCounter();

            RdiffBackupRestore.RestoreState restoreState =
                    rdiffBackupRestore.getRestoreState();
            if (restoreState != null) {
                switch (restoreState) {
                    case Counting:
                        progressBar.setIndeterminate(true);
                        String string = BUNDLE.getString("Counting_Files");
                        string = MessageFormat.format(
                                string, restoreCounter);
                        progressLabel.setText(string);
                        filenameLabel.setText(currentFile);
                        break;

                    case Restoring:
                        long fileCounter = rdiffBackupRestore.getFileCounter();
                        if (fileCounter > 0) {
                            if (countFiles) {
                                progressBar.setIndeterminate(false);
                                string = BUNDLE.getString(
                                        "Restoring_File_Counted");
                                string = MessageFormat.format(string,
                                        fileCounter, restoreCounter);
                                progressLabel.setText(string);
                                filenameLabel.setText(currentFile);
                                if (restoreCounter > 0) {
                                    progressBar.setValue(
                                            (int) ((fileCounter * 100)
                                            / restoreCounter));
                                }
                            } else {
                                string = BUNDLE.getString(
                                        "Restoring_File_Not_Counted");
                                string = MessageFormat.format(
                                        string, fileCounter);
                                progressLabel.setText(string);
                                filenameLabel.setText(currentFile);
                            }
                        }
                        break;

                    default:
                        LOGGER.log(Level.WARNING,
                                "unsupported restoreState: {0}", restoreState);
                }
            }

            // update time information
            long time = System.currentTimeMillis() - start;
            String timeString = timeFormat.format(new Date(time));
            timeLabel.setText(timeString);
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addExcludesButton;
    private javax.swing.JButton addIncludesButton;
    private javax.swing.JPanel advancedSettingsPanel;
    private javax.swing.ButtonGroup authenticationButtonGroup;
    private javax.swing.JCheckBox autoDeleteAgeCheckBox;
    private javax.swing.JComboBox autoDeleteAgeComboBox;
    private javax.swing.JSpinner autoDeleteAgeSpinner;
    private javax.swing.JCheckBox autoDeleteNumberCheckBox;
    private javax.swing.JLabel autoDeleteNumberLabel;
    private javax.swing.JSpinner autoDeleteNumberSpinner;
    private javax.swing.JPanel autoDeletionAgePanel;
    private javax.swing.JPanel autoDeletionHeaderPanel;
    private javax.swing.JLabel autoDeletionLabel;
    private javax.swing.JPanel autoDeletionNumberPanel;
    private javax.swing.JSeparator autoDeletionSeparator1;
    private javax.swing.JCheckBox autoDeletionSpaceCheckBox;
    private javax.swing.JComboBox autoDeletionSpaceComboBox;
    private javax.swing.JPanel autoDeletionSpacePanel;
    private javax.swing.JSpinner autoDeletionSpaceSpinner;
    private javax.swing.JButton backupButton;
    private javax.swing.JPanel backupCardPanel;
    private javax.swing.JPanel backupCheckBoxPanel;
    private javax.swing.JPanel backupConfigCardPanel;
    private javax.swing.JPanel backupConfigPanel;
    private javax.swing.JLabel backupDestinationLabel;
    private javax.swing.JLabel backupErrorLabel;
    private javax.swing.JPanel backupErrorPanel;
    private javax.swing.JPanel backupPanel;
    private javax.swing.JPanel backupReminderPanel;
    private javax.swing.JPanel backupRestoredPanel;
    private javax.swing.JButton backupSourceButton;
    private javax.swing.JLabel backupSourceLabel;
    private javax.swing.JTextField backupSourceTextField;
    private javax.swing.JButton cancelButton;
    private javax.swing.JButton changePasswordButton;
    private javax.swing.JPanel checkBoxPanel;
    private javax.swing.JCheckBox compressionCheckBox;
    private javax.swing.JButton continueButton;
    private javax.swing.JCheckBox countFilesCheckBox;
    private javax.swing.JRadioButton customTempDirRadioButton;
    private javax.swing.JButton decryptionButton;
    private javax.swing.JRadioButton defaultTempDirRadioButton;
    private javax.swing.JPanel destinationCardPanel;
    private javax.swing.JPanel destinationDirHeaderPanel;
    private javax.swing.ButtonGroup destinationLocationButtonGroup;
    private javax.swing.JPanel destinationRadioButtonPanel;
    private javax.swing.JSeparator destinationSeparator1;
    private javax.swing.JPanel directoriesPanel;
    private javax.swing.JButton encryptionButton;
    private javax.swing.JPanel encryptionCardPanel;
    private javax.swing.JLabel encryptionErrorLabel;
    private javax.swing.JPanel encryptionErrorPanel;
    private javax.swing.JPanel encryptionPanel;
    private javax.swing.JCheckBox excludeCheckBox;
    private javax.swing.JCheckBox excludeDeviceFilesCheckBox;
    private javax.swing.JCheckBox excludeFifosCheckBox;
    private javax.swing.JCheckBox excludeOtherFileSystemsCheckBox;
    private javax.swing.JCheckBox excludeSocketsCheckBox;
    private javax.swing.JCheckBox excludeSymlinksCheckBox;
    private javax.swing.JPanel excludesPanel;
    private javax.swing.JScrollPane excludesScrollPane;
    private javax.swing.JTextArea excludesTextArea;
    private javax.swing.JPanel filePatternsPanel;
    private javax.swing.JLabel filenameLabel;
    private javax.swing.JCheckBox includesCheckBox;
    private javax.swing.JPanel includesPanel;
    private javax.swing.JScrollPane includesScrollPane;
    private javax.swing.JTextArea includesTextArea;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JSeparator jSeparator4;
    private javax.swing.JSeparator jSeparator5;
    private javax.swing.JSeparator jSeparator6;
    private javax.swing.JSeparator jSeparator8;
    private javax.swing.JRadioButton localRadioButton;
    private javax.swing.JButton localStorageButton;
    private javax.swing.JPanel localStoragePanel;
    private javax.swing.JTextField localStorageTextField;
    private javax.swing.JButton lockButton;
    private javax.swing.JTabbedPane mainTabbedPane;
    private javax.swing.JCheckBox maxSizeCheckBox;
    private javax.swing.JComboBox maxSizeComboBox;
    private javax.swing.JTextField maxSizeTextField;
    private javax.swing.JCheckBox minSizeCheckBox;
    private javax.swing.JComboBox minSizeComboBox;
    private javax.swing.JTextField minSizeTextField;
    private javax.swing.JPanel noExcludesPanel;
    private javax.swing.JRadioButton otherDirectoryRadioButton;
    private javax.swing.JRadioButton otherSourceRadioButton;
    private javax.swing.JProgressBar progressBar;
    private javax.swing.JLabel progressLabel;
    private javax.swing.JPanel progressPanel;
    private javax.swing.JButton quitButton;
    private ch.fhnw.jbackpack.chooser.RdiffChooserPanel rdiffChooserPanel;
    private javax.swing.JButton reminderButton;
    private javax.swing.JLabel reminderLabel;
    private javax.swing.JTextField reminderTextField;
    private javax.swing.JButton restoreButton;
    private javax.swing.JPanel restoreButtonPanel;
    private javax.swing.JPanel restoreCardPanel;
    private javax.swing.JLabel restoreErrorLabel;
    private javax.swing.JPanel restoreErrorPanel;
    private javax.swing.ButtonGroup restoreLocationButtonGroup;
    private javax.swing.JPanel restorePanel;
    private javax.swing.JLabel restoredLabel;
    private javax.swing.JButton restoredOKButton;
    private javax.swing.JSeparator separator1;
    private javax.swing.JPanel sessionStatisticsPanel;
    private javax.swing.JCheckBox shutdownCheckBox;
    private javax.swing.JLabel shutdownLabel;
    private javax.swing.JPanel shutdownPanel;
    private javax.swing.JPasswordField shutdownPasswordField;
    private javax.swing.JPanel smbLocalHeaderPanel;
    private javax.swing.JLabel smbLocalLabel;
    private javax.swing.JSeparator smbLocalSeparator;
    private javax.swing.JButton smbLogInOutButton;
    private javax.swing.JPanel smbLoginPanel;
    private javax.swing.JProgressBar smbLoginProgressBar;
    private javax.swing.JPasswordField smbPasswordField;
    private javax.swing.JLabel smbPasswordLabel;
    private javax.swing.JRadioButton smbRadioButton;
    private javax.swing.JPanel smbRemoteHeaderPanel;
    private javax.swing.JLabel smbRemoteLabel;
    private javax.swing.JSeparator smbRemoteSeparator;
    private javax.swing.JLabel smbServerLabel;
    private javax.swing.JTextField smbServerTextField;
    private javax.swing.JLabel smbShareLabel;
    private javax.swing.JTextField smbShareTextField;
    private javax.swing.JButton smbStorageButton;
    private javax.swing.JLabel smbStorageLabel;
    private javax.swing.JPanel smbStoragePanel;
    private javax.swing.JTextField smbStorageTextField;
    private javax.swing.JPasswordField smbSudoPasswordField;
    private javax.swing.JLabel smbSudoPasswordLabel;
    private javax.swing.JLabel smbUserLabel;
    private javax.swing.JTextField smbUserTextField;
    private javax.swing.ButtonGroup sourceDirButtonGroup;
    private javax.swing.JPanel sourceDirDetailsPanel;
    private javax.swing.JPanel sourceDirHeaderPanel;
    private javax.swing.JPanel sourceDirRadioButtonPanel;
    private javax.swing.JSeparator sourceDirSeparator1;
    private javax.swing.JRadioButton sourceDirectoryRadioButton;
    private javax.swing.JPanel sshAuthenticationPanel;
    private javax.swing.JLabel sshBaseDirLabel;
    private javax.swing.JTextField sshBaseDirTextField;
    private javax.swing.JButton sshLogInOutButton;
    private javax.swing.JProgressBar sshLoginProgressBar;
    private javax.swing.JPasswordField sshPasswordField;
    private javax.swing.JRadioButton sshPasswordRadioButton;
    private javax.swing.JRadioButton sshPublicKeyRadioButton;
    private javax.swing.JRadioButton sshRadioButton;
    private javax.swing.JLabel sshServerLabel;
    private javax.swing.JPanel sshServerPanel;
    private javax.swing.JTextField sshServerTextField;
    private javax.swing.JButton sshStorageButton;
    private javax.swing.JLabel sshStorageLabel;
    private javax.swing.JPanel sshStoragePanel;
    private javax.swing.JTextField sshStorageTextField;
    private javax.swing.JLabel sshUserNameLabel;
    private javax.swing.JTextField sshUserNameTextField;
    private javax.swing.JLabel statisticsLabel;
    private javax.swing.JTextArea statisticsTextField;
    private javax.swing.JScrollPane statisticsTextFieldScrollPane;
    private javax.swing.JLabel storageUsageLabel;
    private javax.swing.JProgressBar storageUsageProgressBar;
    private javax.swing.JButton tempDirBrowseButton;
    private javax.swing.ButtonGroup tempDirButtonGroup;
    private javax.swing.JPanel tempDirDetailsPanel;
    private javax.swing.JPanel tempDirHeaderPanel;
    private javax.swing.JLabel tempDirLabel;
    private javax.swing.JPanel tempDirRadioButtonPanel;
    private javax.swing.JSeparator tempDirSeparator1;
    private javax.swing.JTextField tempDirTextField;
    private javax.swing.JLabel timeLabel;
    private javax.swing.JPanel unlockPanel;
    private javax.swing.JRadioButton userHomeRadioButton;
    // End of variables declaration//GEN-END:variables
}
