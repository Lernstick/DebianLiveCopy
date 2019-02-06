package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.DLCopy;
import ch.fhnw.util.ProcessExecutor;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;

/**
 * Shows a dialog for selecting documents to print
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class PrintSelectionDialog extends javax.swing.JDialog {

    private static final Logger LOGGER
            = Logger.getLogger(PrintSelectionDialog.class.getName());

    private final List<JCheckBox> checkBoxes;
    private final List<Path> documents;

    private boolean okPressed;

    /**
     * Creates new form PrintSelectionDialog
     *
     * @param parent the parent frame
     * @param type the type of documents to select, <tt>null</tt> if no special
     * type was selected and several types are allowed in the list
     * @param mountPath the path where the exchange partition is mounted
     * @param documents the list of documents
     */
    public PrintSelectionDialog(java.awt.Frame parent, String type,
            String mountPath, List<Path> documents) {

        super(parent, true);

        this.documents = documents;

        initComponents();

        GridBagConstraints previewConstraints = new GridBagConstraints();
        previewConstraints.anchor = GridBagConstraints.WEST;
        previewConstraints.insets = new java.awt.Insets(3, 0, 0, 0);

        GridBagConstraints checkBoxConstraints = new GridBagConstraints();
        checkBoxConstraints.anchor = GridBagConstraints.WEST;
        checkBoxConstraints.insets = new java.awt.Insets(3, 5, 0, 0);
        checkBoxConstraints.weightx = 1.0;

        GridBagConstraints dateLabelConstraints = new GridBagConstraints();
        dateLabelConstraints.anchor = GridBagConstraints.WEST;
        dateLabelConstraints.insets = new java.awt.Insets(3, 10, 0, 0);
        dateLabelConstraints.gridwidth = GridBagConstraints.REMAINDER;

        checkBoxes = new ArrayList<>();
        documents.forEach((document) -> {
            JButton previewButton = new JButton(
                    DLCopy.STRINGS.getString("Preview"));
            previewButton.addActionListener(new PreviewAction(document));
            checkBoxPanel.add(previewButton, previewConstraints);

            JCheckBox checkBox = new JCheckBox(
                    document.toString().substring(mountPath.length() + 1));
            checkBoxPanel.add(checkBox, checkBoxConstraints);
            checkBoxes.add(checkBox);

            try {
                BasicFileAttributes attributes = Files.readAttributes(
                        document, BasicFileAttributes.class);
                FileTime lastModifiedTime = attributes.lastModifiedTime();
                DateTimeFormatter formatter
                        = DateTimeFormatter.ofLocalizedDateTime(
                                FormatStyle.MEDIUM).
                                withZone(ZoneId.systemDefault());
                String timeStamp
                        = formatter.format(lastModifiedTime.toInstant());
                JLabel dateLabel = new JLabel(timeStamp);
                checkBoxPanel.add(dateLabel, dateLabelConstraints);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }
        });

        if (type == null) {
            infoLabel.setText(DLCopy.STRINGS.getString(
                    "Info_Several_Files_To_Print"));
        } else {
            infoLabel.setText(MessageFormat.format(DLCopy.STRINGS.getString(
                    "Info_Several_Files_Of_Type_To_Print"),
                    type));
        }

        pack();
        setLocationRelativeTo(parent);
    }

    /**
     * returns <code>true</code>, if the dialog was closed by pressing the OK
     * button, <code>false</code> otherwise
     *
     * @return
     */
    public boolean okPressed() {
        return okPressed;
    }

    /**
     * returns the list of selected documents
     *
     * @return the list of selected documents
     */
    public List<Path> getSelectedDocuments() {
        List<Path> selectedDocuments = new ArrayList<>();
        for (int i = 0; i < checkBoxes.size(); i++) {
            if (checkBoxes.get(i).isSelected()) {
                selectedDocuments.add(documents.get(i));
            }
        }
        return selectedDocuments;
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

        infoLabel = new javax.swing.JLabel();
        checkBoxPanel = new javax.swing.JPanel();
        okButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        setTitle(bundle.getString("PrintSelectionDialog.title")); // NOI18N
        getContentPane().setLayout(new java.awt.GridBagLayout());

        infoLabel.setText(bundle.getString("Info_Several_Files_Of_Type_To_Print")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        getContentPane().add(infoLabel, gridBagConstraints);

        checkBoxPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        getContentPane().add(checkBoxPanel, gridBagConstraints);

        okButton.setText(bundle.getString("OK")); // NOI18N
        okButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                okButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        getContentPane().add(okButton, gridBagConstraints);

        cancelButton.setText(bundle.getString("PrintSelectionDialog.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 5);
        getContentPane().add(cancelButton, gridBagConstraints);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        setVisible(false);
        dispose();
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void okButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_okButtonActionPerformed
        okPressed = true;
        setVisible(false);
        dispose();
    }//GEN-LAST:event_okButtonActionPerformed

    private class PreviewAction implements ActionListener {

        private final Path document;

        public PreviewAction(Path document) {
            this.document = document;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            // start preview nonblocking in a new thread so that we can preview
            // several documents side by side
            Thread thread = new Thread() {
                @Override
                public void run() {
                    // This line doesn't work because we are running as root:
                    // Desktop.getDesktop().open(document.toFile());
                    // Until we have a better idea we use the workaround below
                    // with a script:
                    ProcessExecutor processExecutor = new ProcessExecutor();
                    try {
                        if (document.getFileName().toString().toLowerCase()
                                .endsWith("pdf")) {
                            processExecutor.executeScript(true, true,
                                    createScript(document, "evince"));
                        } else {
                            processExecutor.executeScript(true, true,
                                    createScript(document, "libreoffice"));
                        }
                    } catch (IOException ex) {
                        LOGGER.log(Level.SEVERE, "", ex);
                    }
                }
            };
            thread.start();
        }

        private String createScript(Path document, String program) {
            String fileName = document.getFileName().toString();
            return "#!/bin/sh\n"
                    + "cp \"" + document.toString() + "\" /tmp/\n"
                    + "cd /tmp\n"
                    + "sudo -u user " + program + " \"/tmp/" + fileName + "\"";
        }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel checkBoxPanel;
    private javax.swing.JLabel infoLabel;
    private javax.swing.JButton okButton;
    // End of variables declaration//GEN-END:variables
}
