/*
 * RsyncCopyDialog.java
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
 * Created on 28.06.2010, 17:17:15
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.ProcessExecutor;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.JDialog;
import javax.swing.Timer;

/**
 * A dialog for rsync copy operations
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class RsyncCopyDialog extends JDialog implements PropertyChangeListener {

    private final static Logger LOGGER =
            Logger.getLogger(RsyncCopyDialog.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private static final Pattern overallProgressPattern =
            Pattern.compile(".*to-check=(.*)/(.*)\\)");
    private static final Pattern fileProgressPattern =
            Pattern.compile(".* (.*)% .*/s .*:.*:.*");
    private final ProcessExecutor processExecutor;
    private int overallProgress;
    private long fileCounter;
    private String currentFileName;
    private int fileProgress;
    private boolean cancelPressed;

    /** Creates new form RsyncCopyDialog
     * @param parent the parent frame
     * @param titleText the title text
     * @param titleIcon the title icon
     * @param processExecutor the ProcessExecutor with the running rsync process
     */
    public RsyncCopyDialog(Frame parent, String titleText, Icon titleIcon,
            ProcessExecutor processExecutor) {
        super(parent, true);
        this.processExecutor = processExecutor;
        initComponents();
        titleLabel.setText(titleText);
        titleLabel.setIcon(titleIcon);
        setLocationRelativeTo(parent);
        setFileCount(0);
        setCurrentFile(" ");
        Timer statusUpdateTimer = new Timer(300, new ActionListener() {

            public void actionPerformed(ActionEvent e) {
                setOverallProgress(overallProgress);
                setFileCount(fileCounter);
                setCurrentFile(currentFileName);
                setFileProgress(fileProgress);
            }
        });
        statusUpdateTimer.setInitialDelay(0);
        statusUpdateTimer.start();
    }

    /**
     * sets the overall progress
     * @param progress the overall progress
     */
    public void setOverallProgress(int progress) {
        overallProgressBar.setValue(progress);
        overallProgressBar.setString(progress + "%");
    }

    /**
     * sets the number of alredy checked files
     * @param fileCount the number of alredy checked files
     */
    public final void setFileCount(long fileCount) {
        String text = BUNDLE.getString("Copying_File");
        text = MessageFormat.format(text, fileCount);
        fileCountLabel.setText(text);
    }

    /**
     * sets the currently checked file
     * @param currentFile the currently checked file
     */
    public final void setCurrentFile(String currentFile) {
        fileLabel.setText(currentFile);
    }

    /**
     * sets the overall progress
     * @param progress the overall progress
     */
    public void setFileProgress(int progress) {
        fileProgressBar.setValue(progress);
        fileProgressBar.setString(progress + "%");
    }

    /**
     * This method gets called when a bound property is changed.
     * @param evt A PropertyChangeEvent object describing the event source and
     * the property that has changed.
     */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {


        String propertyName = evt.getPropertyName();

        if (ProcessExecutor.LINE.equals(propertyName)) {
            // rsync updates
            // parse lines that end with "... to-check=100/800)"
            final String line = (String) evt.getNewValue();
            if (line.length() == 0) {
                return;
            }
            if (line.startsWith("sent ")) {
                // we assume that this is a line of the rsync summary that reads
                // sent 329490 bytes  received 31 bytes  659042.00 bytes/sec
                return;
            }
            if (line.startsWith("total size is ")) {
                // we assume that this is a line of the rsync summary that reads
                // total size is 329356  speedup is 1.00
                return;
            }
            if (line.startsWith("rsync error: received ")) {
                // this happens when we press the cancel button
                return;
            }

            Matcher matcher = overallProgressPattern.matcher(line);
            try {
                if (matcher.matches()) {
                    // this line shows the overall rsync progress
                    String toCheckString = matcher.group(1);
                    String fileCountString = matcher.group(2);
                    int filesToCheck = Integer.parseInt(toCheckString);
                    int fileCount = Integer.parseInt(fileCountString);
                    overallProgress =
                            (fileCount - filesToCheck) * 100 / fileCount;
                } else {
                    matcher = fileProgressPattern.matcher(line);
                    if (matcher.matches()) {
                        // this line shows the progress of a single file
                        String progressString = matcher.group(1);
                        fileProgress = Integer.parseInt(progressString);
                    } else {
                        // this line shows the name of the current file
                        fileCounter++;
                        currentFileName = line;
                    }
                }
            } catch (NumberFormatException ex) {
                LOGGER.log(Level.WARNING,
                        "could not parse rsync output", ex);
            }
        }
    }

    /**
     * returns <tt>true</tt> if the cancel button was pressed,
     * <tt>false</tt> otherwise
     * @return <tt>true</tt> if the cancel button was pressed,
     * <tt>false</tt> otherwise
     */
    public boolean isCancelPressed() {
        return cancelPressed;
    }

    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        titleLabel = new javax.swing.JLabel();
        overallProgressLabel = new javax.swing.JLabel();
        overallProgressBar = new javax.swing.JProgressBar();
        fileCountLabel = new javax.swing.JLabel();
        fileLabel = new JSqueezedLabel();
        fileProgressBar = new javax.swing.JProgressBar();
        buttonPanel = new javax.swing.JPanel();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings"); // NOI18N
        setTitle(bundle.getString("RsyncCopyDialog.title")); // NOI18N

        titleLabel.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        titleLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/jbackpack/icons/encryption_animation.gif"))); // NOI18N
        titleLabel.setText(bundle.getString("Encrypting_Destination_Directory")); // NOI18N
        titleLabel.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
        titleLabel.setVerticalTextPosition(javax.swing.SwingConstants.TOP);

        overallProgressLabel.setText(bundle.getString("RsyncCopyDialog.overallProgressLabel.text")); // NOI18N

        overallProgressBar.setStringPainted(true);

        fileCountLabel.setText(bundle.getString("Copying_File")); // NOI18N

        fileLabel.setFont(fileLabel.getFont().deriveFont(fileLabel.getFont().getStyle() & ~java.awt.Font.BOLD, fileLabel.getFont().getSize()-1));
        fileLabel.setText(bundle.getString("RsyncCopyDialog.fileLabel.text")); // NOI18N

        fileProgressBar.setStringPainted(true);

        buttonPanel.setLayout(new java.awt.GridBagLayout());

        cancelButton.setText(bundle.getString("RsyncCopyDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        buttonPanel.add(cancelButton, new java.awt.GridBagConstraints());

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(titleLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                    .addComponent(overallProgressLabel)
                    .addComponent(overallProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                    .addComponent(fileCountLabel)
                    .addComponent(fileProgressBar, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                    .addComponent(buttonPanel, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE)
                    .addComponent(fileLabel, javax.swing.GroupLayout.DEFAULT_SIZE, 401, Short.MAX_VALUE))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(titleLabel)
                .addGap(18, 18, 18)
                .addComponent(overallProgressLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(overallProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(fileCountLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fileLabel)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(fileProgressBar, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addGap(18, 18, 18)
                .addComponent(buttonPanel, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        cancelPressed = true;
        processExecutor.destroy();
        // disable cancelButton so that no other processes are cancelled by
        // accident
        cancelButton.setEnabled(false);
    }//GEN-LAST:event_cancelButtonActionPerformed
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JPanel buttonPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JLabel fileCountLabel;
    private javax.swing.JLabel fileLabel;
    private javax.swing.JProgressBar fileProgressBar;
    private javax.swing.JProgressBar overallProgressBar;
    private javax.swing.JLabel overallProgressLabel;
    private javax.swing.JLabel titleLabel;
    // End of variables declaration//GEN-END:variables
}
