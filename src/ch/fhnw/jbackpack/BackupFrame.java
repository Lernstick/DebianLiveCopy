/*
 * BackupFrame.java
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
 * Created on Apr 12, 2010, 6:02:47 PM
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.OperatingSystem;
import ch.fhnw.util.ProcessExecutor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.Image;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import javax.help.HelpBroker;
import javax.help.HelpSet;
import javax.help.SwingHelpUtilities;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.KeyStroke;

/**
 * a frame around the BackupMainPanel
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class BackupFrame extends javax.swing.JFrame {

    /**
     * the path to the icon ressource
     */
    public static final String ICON_PATH =
            "/ch/fhnw/jbackpack/icons/32x32/icon.png";
    private static final Logger LOGGER = Logger.getLogger(
            BackupFrame.class.getName());
    private static final Logger UTIL_LOGGER = Logger.getLogger(
            ProcessExecutor.class.getPackage().getName());
    private static final Logger GLOBAL_LOGGER = Logger.getLogger(
            BackupFrame.class.getPackage().getName());
    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings");
    private static final String PROFILES_PATH = "profiles_path";
    private static final String RECENT_PROFILE = "recent_profile_";
    private static final String LOGGING_LEVEL = "logging_level";
    private static final String USER_HOME = System.getProperty("user.home");
    private static final int RECENT_PROFILES_LIMIT = 4;
    private final ConsoleHandler consoleHandler;
    private final List<String> recentProfiles;
    private final Preferences preferences;
    private FileHandler fileHandler;
    private LogLevel logLevel;
    private String profilesPath;
    private boolean recentProfilesHaveChanged;
    private HelpBroker helpbroker;

    /** Creates new form BackupFrame */
    public BackupFrame() {
        preferences = Preferences.userNodeForPackage(JBackpack.class);
        int logLevelOrdinal = preferences.getInt(
                LOGGING_LEVEL, LogLevel.INFO.ordinal());

        // do not use parent handler
        // (otherwise we would get many double log entries on the console)
        GLOBAL_LOGGER.setUseParentHandlers(false);
        UTIL_LOGGER.setUseParentHandlers(false);

        // log to console
        SimpleFormatter formatter = new SimpleFormatter();
        consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        GLOBAL_LOGGER.addHandler(consoleHandler);
        UTIL_LOGGER.addHandler(consoleHandler);

        // log into a rotating temporaty file of max 50 MB
        try {
            // prevent logfile name clashes on multi-user systems by throwing
            // the user's name into the mix
            fileHandler = new FileHandler("%t/jbackpack_"
                    + System.getProperty("user.name"), 50000000, 2, true);
            fileHandler.setFormatter(formatter);
            GLOBAL_LOGGER.addHandler(fileHandler);
            UTIL_LOGGER.addHandler(fileHandler);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        setLogLevel(LogLevel.values()[logLevelOrdinal]);
        LOGGER.info("*********** Starting JBackpack ***********");

        recentProfiles = new ArrayList<String>();

        initComponents();

        // configure all accelerators
        int menuShortcutKeyMask =
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
        newProfileMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_N, menuShortcutKeyMask));
        openProfileMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, menuShortcutKeyMask));
        saveProfileMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_S, menuShortcutKeyMask));
        quitMenuItem.setAccelerator(
                KeyStroke.getKeyStroke(KeyEvent.VK_Q, menuShortcutKeyMask));

        if (CurrentOperatingSystem.OS != OperatingSystem.Mac_OS_X) {
            // using mnemonics on Mac OS X is discouraged
            fileMenu.setMnemonic(BUNDLE.getString(
                    "BackupFrame.fileMenu.mnemonic").charAt(0));
            newProfileMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.newProfileMenuItem.mnemonic").charAt(0));
            openProfileMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.openProfileMenuItem.mnemonic").charAt(0));
            recentProfilesMenu.setMnemonic(BUNDLE.getString(
                    "BackupFrame.recentProfilesMenu.mnemonic").charAt(0));
            saveProfileMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.saveProfileMenuItem.mnemonic").charAt(0));
            preferencesMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.preferencesMenuItem.mnemonic").charAt(0));
            quitMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.quitMenuItem.mnemonic").charAt(0));
            helpMenu.setMnemonic(BUNDLE.getString(
                    "BackupFrame.helpMenu.mnemonic").charAt(0));
            helpMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.helpMenuItem.mnemonic").charAt(0));
            aboutMenuItem.setMnemonic(BUNDLE.getString(
                    "BackupFrame.aboutMenuItem.mnemonic").charAt(0));
        }

        List<Image> icons = new ArrayList<Image>();
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/16x16/icon.png")).getImage());
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/32x32/icon.png")).getImage());
        Image dockImage = new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/128x128/icon.png")).getImage();
        icons.add(dockImage);
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/256x256/icon.png")).getImage());
        icons.add(new ImageIcon(getClass().getResource(
                "/ch/fhnw/jbackpack/icons/512x512/icon.png")).getImage());
        setIconImages(icons);

        setupOSXApplication(dockImage);

        init();

        // load preferences
        profilesPath = preferences.get(PROFILES_PATH, USER_HOME);
        for (int i = 0; i < RECENT_PROFILES_LIMIT; i++) {
            String recentProfile = preferences.get(RECENT_PROFILE + i, null);
            if (recentProfile == null) {
                break;
            } else {
                recentProfiles.add(recentProfile);
                recentProfilesHaveChanged = true;
            }
        }

        // activate help
        try {
            SwingHelpUtilities.setContentViewerUI(
                    "ch.fhnw.util.ExternalLinkContentViewerUI");
            ClassLoader classLoader =
                    Thread.currentThread().getContextClassLoader();
            String plaf = null;
            switch (CurrentOperatingSystem.OS) {
                case Mac_OS_X:
                    plaf = "aqua";
                    break;
                case Windows:
                    plaf = "windows";
                    break;
                default:
                    plaf = "nimbus";
            }
            URL helpSetURL = HelpSet.findHelpSet(classLoader,
                    "ch/fhnw/jbackpack/help/" + plaf + "/HelpSet.hs");
            HelpSet helpSet = new HelpSet(classLoader, helpSetURL);
            helpbroker = helpSet.createHelpBroker();
        } catch (Exception exception) {
            LOGGER.log(Level.SEVERE, "could not init Java help", exception);
        }

        setLocationRelativeTo(null);
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

        backupMainPanel = new ch.fhnw.jbackpack.BackupMainPanel();
        menuBar = new javax.swing.JMenuBar();
        fileMenu = new javax.swing.JMenu();
        newProfileMenuItem = new javax.swing.JMenuItem();
        openProfileMenuItem = new javax.swing.JMenuItem();
        recentProfilesMenu = new javax.swing.JMenu();
        saveProfileMenuItem = new javax.swing.JMenuItem();
        jSeparator1 = new javax.swing.JPopupMenu.Separator();
        preferencesMenuItem = new javax.swing.JMenuItem();
        jSeparator2 = new javax.swing.JPopupMenu.Separator();
        quitMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        helpMenuItem = new javax.swing.JMenuItem();
        aboutMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        setTitle(bundle.getString("BackupFrame.title")); // NOI18N
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        backupMainPanel.setName("backupMainPanel"); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(backupMainPanel, gridBagConstraints);

        fileMenu.setText(bundle.getString("BackupFrame.fileMenu.text")); // NOI18N
        fileMenu.setName("fileMenu"); // NOI18N

        newProfileMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/filenew.png"))); // NOI18N
        newProfileMenuItem.setText(bundle.getString("BackupFrame.newProfileMenuItem.text")); // NOI18N
        newProfileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                newProfileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(newProfileMenuItem);

        openProfileMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/fileopen.png"))); // NOI18N
        openProfileMenuItem.setText(bundle.getString("BackupFrame.openProfileMenuItem.text")); // NOI18N
        openProfileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                openProfileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(openProfileMenuItem);

        recentProfilesMenu.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/bookmark.png"))); // NOI18N
        recentProfilesMenu.setText(bundle.getString("BackupFrame.recentProfilesMenu.text")); // NOI18N
        recentProfilesMenu.addMenuListener(new javax.swing.event.MenuListener() {
            public void menuCanceled(javax.swing.event.MenuEvent evt) {
            }
            public void menuDeselected(javax.swing.event.MenuEvent evt) {
            }
            public void menuSelected(javax.swing.event.MenuEvent evt) {
                recentProfilesMenuMenuSelected(evt);
            }
        });
        fileMenu.add(recentProfilesMenu);

        saveProfileMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/filesaveas.png"))); // NOI18N
        saveProfileMenuItem.setText(bundle.getString("BackupFrame.saveProfileMenuItem.text")); // NOI18N
        saveProfileMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                saveProfileMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(saveProfileMenuItem);
        fileMenu.add(jSeparator1);

        preferencesMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/configure.png"))); // NOI18N
        preferencesMenuItem.setText(bundle.getString("BackupFrame.preferencesMenuItem.text")); // NOI18N
        preferencesMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                preferencesMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(preferencesMenuItem);
        fileMenu.add(jSeparator2);

        quitMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/exit.png"))); // NOI18N
        quitMenuItem.setText(bundle.getString("BackupFrame.quitMenuItem.text")); // NOI18N
        quitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                quitMenuItemActionPerformed(evt);
            }
        });
        fileMenu.add(quitMenuItem);

        menuBar.add(fileMenu);

        helpMenu.setText(bundle.getString("BackupFrame.helpMenu.text")); // NOI18N
        helpMenu.setName("helpMenu"); // NOI18N

        helpMenuItem.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F1, 0));
        helpMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/help.png"))); // NOI18N
        helpMenuItem.setText(bundle.getString("BackupFrame.helpMenuItem.text")); // NOI18N
        helpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                helpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(helpMenuItem);

        aboutMenuItem.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/16x16/info.png"))); // NOI18N
        aboutMenuItem.setText(bundle.getString("BackupFrame.aboutMenuItem.text")); // NOI18N
        aboutMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                aboutMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(aboutMenuItem);

        menuBar.add(helpMenu);

        setJMenuBar(menuBar);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void quitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_quitMenuItemActionPerformed
        exit();
    }//GEN-LAST:event_quitMenuItemActionPerformed

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        exit();
    }//GEN-LAST:event_formWindowClosed

    private void saveProfileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveProfileMenuItemActionPerformed

        String title = BUNDLE.getString("Save_Profile");

        File selectedFile = null;
        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            FileDialog fileDialog =
                    new FileDialog(this, title, FileDialog.SAVE);
            fileDialog.setDirectory(profilesPath);
            fileDialog.setVisible(true);
            String directory = fileDialog.getDirectory();
            String file = fileDialog.getFile();
            if ((directory != null) && (file != null)) {
                selectedFile = new File(directory + file);
            }
        } else {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(title);
            fileChooser.setCurrentDirectory(new File(profilesPath));
            if (JFileChooser.APPROVE_OPTION
                    == fileChooser.showSaveDialog(this)) {
                selectedFile = fileChooser.getSelectedFile();
            }
        }

        if (selectedFile != null) {
            backupMainPanel.savePreferences();
            try {
                FileOutputStream fileOutputStream =
                        new FileOutputStream(selectedFile);
                preferences.exportNode(fileOutputStream);
                addToRecentProFiles(selectedFile.getPath());
                JOptionPane.showMessageDialog(this,
                        BUNDLE.getString("Profile_Saved"),
                        BUNDLE.getString("Information"),
                        JOptionPane.INFORMATION_MESSAGE,
                        IconManager.INFORMATION_ICON);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                savingProfileFailed(ex);
            } catch (BackingStoreException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
                savingProfileFailed(ex);
            }
            profilesPath = selectedFile.getParent();
        }
    }//GEN-LAST:event_saveProfileMenuItemActionPerformed

    private void openProfileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_openProfileMenuItemActionPerformed
        String title = BUNDLE.getString("Load_Profile");
        File selectedFile = null;
        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            FileDialog fileDialog =
                    new FileDialog(this, title, FileDialog.LOAD);
            fileDialog.setDirectory(profilesPath);
            fileDialog.setVisible(true);
            String directory = fileDialog.getDirectory();
            String file = fileDialog.getFile();
            if ((directory != null) && (file != null)) {
                selectedFile = new File(directory + file);
            }
        } else {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle(title);
            fileChooser.setCurrentDirectory(new File(profilesPath));
            if (JFileChooser.APPROVE_OPTION
                    == fileChooser.showOpenDialog(this)) {
                selectedFile = fileChooser.getSelectedFile();
            }
        }

        if (selectedFile != null) {
            openProfile(selectedFile);
        }
    }//GEN-LAST:event_openProfileMenuItemActionPerformed

    private void recentProfilesMenuMenuSelected(javax.swing.event.MenuEvent evt) {//GEN-FIRST:event_recentProfilesMenuMenuSelected
        if (recentProfilesHaveChanged) {
            // remove old recent file entries
            recentProfilesMenu.removeAll();

            // add recent file entries
            for (String recentProfile : recentProfiles) {
                JMenuItem newRecentProFileMenuItem =
                        new JMenuItem(recentProfile);
                newRecentProFileMenuItem.addActionListener(
                        new java.awt.event.ActionListener() {

                            public void actionPerformed(ActionEvent event) {
                                JMenuItem menuItem = (JMenuItem) event.getSource();
                                String path = menuItem.getText();
                                File file = new File(path);
                                openProfile(file);
                            }
                        });
                recentProfilesMenu.add(newRecentProFileMenuItem);
            }
            recentProfilesHaveChanged = false;
        }
    }//GEN-LAST:event_recentProfilesMenuMenuSelected

    private void newProfileMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_newProfileMenuItemActionPerformed
        backupMainPanel.clearSettings();
    }//GEN-LAST:event_newProfileMenuItemActionPerformed

    private void preferencesMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_preferencesMenuItemActionPerformed
        configure();
    }//GEN-LAST:event_preferencesMenuItemActionPerformed

    private void helpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_helpMenuItemActionPerformed
        helpbroker.setCurrentID("JBackpack");
        helpbroker.setSize(new Dimension(800, 600));
        helpbroker.setDisplayed(true);
    }//GEN-LAST:event_helpMenuItemActionPerformed

    private void aboutMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_aboutMenuItemActionPerformed
        about();
    }//GEN-LAST:event_aboutMenuItemActionPerformed

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // check if we must open the first time wizard
        String localDestination = preferences.get(
                BackupMainPanel.LOCAL_DESTINATION_DIRECTORY, null);
        String remoteDestination = preferences.get(
                BackupMainPanel.SSH_DIRECTORY, null);
        if ((localDestination == null) && (remoteDestination == null)) {
            FirstTimeDialog dialog = new FirstTimeDialog(this);
            dialog.setVisible(true);
        }
    }//GEN-LAST:event_formWindowOpened

    private void configure() {
        String logFileName = null;
        try {
            // there is no API to get the current file name frome FileHandler
            // we must crack it up via reflection
            Field filesField =
                    FileHandler.class.getDeclaredField("files");
            filesField.setAccessible(true);
            File[] files = (File[]) filesField.get(fileHandler);
            logFileName = files[0].getPath();
        } catch (IllegalArgumentException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (NoSuchFieldException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (SecurityException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        PreferencesDialog preferencesDialog =
                new PreferencesDialog(this, logFileName, logLevel,
                backupMainPanel.isPlainBackupWarningSelected());
        preferencesDialog.setVisible(true);

        if (preferencesDialog.okPressed()) {
            setLogLevel(preferencesDialog.getLogLevel());
            backupMainPanel.setPlainBackupWarning(
                    preferencesDialog.isShowPlainBackupWarningSelected());
        }
    }

    private void about() {
        AboutDialog aboutDialog = new AboutDialog(this);
        aboutDialog.setVisible(true);
    }

    @SuppressWarnings("unchecked")
    private void setupOSXApplication(Image image) {

        if (CurrentOperatingSystem.OS != OperatingSystem.Mac_OS_X) {
            return;
        }

        try {
            Class applicationClass =
                    Class.forName("com.apple.eawt.Application");
            Class applicationListenerClass =
                    Class.forName("com.apple.eawt.ApplicationListener");
            Object application = applicationClass.newInstance();
            Object listener = Proxy.newProxyInstance(
                    applicationListenerClass.getClassLoader(),
                    new Class[]{applicationListenerClass},
                    new InvocationHandler() {

                        @Override
                        public Object invoke(
                                Object proxy, Method method, Object[] args) {
                            if (method.getName().equals("handleQuit")) {
                                exit();
                            }
                            if (method.getName().equals("handlePreferences")) {
                                configure();
                            }
                            if (method.getName().equals("handleAbout")) {
                                about();
                                setHandled(args[0], Boolean.TRUE);
                            }
                            return null;
                        }

                        private void setHandled(Object event, Boolean value) {
                            try {
                                Method handleMethod =
                                        event.getClass().getMethod("setHandled",
                                        new Class[]{boolean.class});
                                handleMethod.invoke(event, new Object[]{value});
                            } catch (Exception exception) {
                                LOGGER.log(Level.WARNING, null, exception);
                            }
                        }
                    });

            Method addApplicationListenerMethod =
                    applicationClass.getMethod(
                    "addApplicationListener", applicationListenerClass);
            addApplicationListenerMethod.invoke(application, listener);

            Method enablePreferenceMethod = applicationClass.getMethod(
                    "setEnabledPreferencesMenu", new Class[]{boolean.class});
            enablePreferenceMethod.invoke(
                    application, new Object[]{Boolean.TRUE});

            Method setDockIconImageMethod = applicationClass.getMethod(
                    "setDockIconImage", Image.class);
            setDockIconImageMethod.invoke(application, image);

            fileMenu.remove(jSeparator1);
            fileMenu.remove(preferencesMenuItem);
            fileMenu.remove(jSeparator2);
            fileMenu.remove(quitMenuItem);
            helpMenu.remove(aboutMenuItem);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, null, ex);
        }
    }

    private void savingProfileFailed(Exception exception) {
        String errorMessage = BUNDLE.getString("Error_Saving_Profile");
        errorMessage = MessageFormat.format(errorMessage, exception.toString());
        JOptionPane.showMessageDialog(this, errorMessage,
                BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);
    }

    private void setLogLevel(LogLevel logLevel) {
        this.logLevel = logLevel;
        Level level = logLevel.getLevel();
        GLOBAL_LOGGER.setLevel(level);
        UTIL_LOGGER.setLevel(level);
        consoleHandler.setLevel(level);
        if (fileHandler != null) {
            fileHandler.setLevel(level);
        }
    }

    private void init() {
        backupMainPanel.setParentFrame(this);
        backupMainPanel.init();
    }

    private void addToRecentProFiles(String path) {
        // look if entry already exists in list
        boolean entryExists = false;
        int existingIndex = 0;
        for (int i = 0; i < recentProfiles.size(); i++) {
            if (path.equals(recentProfiles.get(i))) {
                entryExists = true;
                existingIndex = i;
                break;
            }
        }

        if (entryExists) {
            // move to top
            recentProfiles.add(0, recentProfiles.remove(existingIndex));
        } else {
            // add entry
            recentProfiles.add(0, path);
            int size = recentProfiles.size();
            if (size > RECENT_PROFILES_LIMIT) {
                recentProfiles.remove(size - 1);
            }
        }

        recentProfilesHaveChanged = true;
        savePreferences();
    }

    private void openProfile(File profile) {
        try {
            FileInputStream fileInputStream = new FileInputStream(profile);
            preferences.clear();
            Preferences.importPreferences(fileInputStream);
            backupMainPanel.setPreferences();
            backupMainPanel.maybeUnlock(false);
            addToRecentProFiles(profile.getPath());
        } catch (BackingStoreException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            openProfileFailed(profile.getPath());
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            openProfileFailed(profile.getPath());
        } catch (InvalidPreferencesFormatException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
            openProfileFailed(profile.getPath());
        }
    }

    private void openProfileFailed(String path) {
        String errorMessage = BUNDLE.getString("Error_Opening_Settings");
        errorMessage = MessageFormat.format(errorMessage, path);
        JOptionPane.showMessageDialog(this, errorMessage,
                BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);
        recentProfiles.remove(path);
        recentProfilesHaveChanged = true;
    }

    private void exit() {
        backupMainPanel.savePreferences();
        savePreferences();
        System.exit(0);
    }

    private void savePreferences() {
        preferences.put(PROFILES_PATH, profilesPath);
        for (int i = 0; i < recentProfiles.size(); i++) {
            String recentFile = recentProfiles.get(i);
            preferences.put(RECENT_PROFILE + i, recentFile);
        }
        for (int i = recentProfiles.size(); i < RECENT_PROFILES_LIMIT; i++) {
            preferences.remove(RECENT_PROFILE + i);
        }
        preferences.putInt(LOGGING_LEVEL, logLevel.ordinal());
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem aboutMenuItem;
    private ch.fhnw.jbackpack.BackupMainPanel backupMainPanel;
    private javax.swing.JMenu fileMenu;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JMenuItem helpMenuItem;
    private javax.swing.JPopupMenu.Separator jSeparator1;
    private javax.swing.JPopupMenu.Separator jSeparator2;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem newProfileMenuItem;
    private javax.swing.JMenuItem openProfileMenuItem;
    private javax.swing.JMenuItem preferencesMenuItem;
    private javax.swing.JMenuItem quitMenuItem;
    private javax.swing.JMenu recentProfilesMenu;
    private javax.swing.JMenuItem saveProfileMenuItem;
    // End of variables declaration//GEN-END:variables
}
