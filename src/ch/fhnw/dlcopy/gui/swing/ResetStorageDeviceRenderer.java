/*
 * ResetStorageDeviceRenderer.java
 *
 * Created on 16. April 2008, 13:23
 */
package ch.fhnw.dlcopy.gui.swing;

import static ch.fhnw.dlcopy.DLCopy.STRINGS;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.awt.*;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;

/**
 * A renderer for storage devices to be reset
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ResetStorageDeviceRenderer extends JPanel
        implements ListCellRenderer<StorageDevice>, StorageDeviceRenderer {

    private final static Logger LOGGER
            = Logger.getLogger(DLCopySwingGUI.class.getName());
    private final static Icon BLUE_BOX = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/blue_box.png"));
    private final static Icon GREEN_BOX = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/green_box.png"));
    private final static Icon YELLOW_BOX = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/yellow_box.png"));
    private final static Icon GRAY_BOX = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/gray_box.png"));
    private final static Icon DARK_GRAY_BOX = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/dark_gray_box.png"));
    private final static Icon OK_ICON = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/16x16/dialog-ok-apply.png"));
    private final static Icon CANCEL_ICON = new ImageIcon(
            ResetStorageDeviceRenderer.class.getResource(
                    "/ch/fhnw/dlcopy/icons/16x16/dialog-cancel.png"));
    private final Color LIGHT_BLUE = new Color(170, 170, 255);
    private long maxStorageDeviceSize;
    private StorageDevice storageDevice;

    /**
     * Creates new form UsbRenderer
     */
    public ResetStorageDeviceRenderer() {
        initComponents();
    }

    @Override
    public Component getListCellRendererComponent(JList list,
            StorageDevice storageDevice, int index, boolean isSelected,
            boolean cellHasFocus) {

        this.storageDevice = storageDevice;

        // set icon based on storage type
        StorageDevice.Type deviceType = storageDevice.getType();
        switch (deviceType) {
            case HardDrive:
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/ch/fhnw/dlcopy/icons/32x32/drive-harddisk.png")));
                break;
            case SDMemoryCard:
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/ch/fhnw/dlcopy/icons/32x32/media-flash-sd-mmc.png")));
                break;
            case USBFlashDrive:
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/ch/fhnw/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png")));
                break;
            default:
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/ch/fhnw/dlcopy/icons/32x32/drive-removable-media.png")));
                LOGGER.log(Level.WARNING,
                        "unsupported deviceType:{0}", deviceType);
        }

        // set device text
        DLCopySwingGUI.setStorageDeviceLabel(
                descriptionLabel, storageDevice);

        // partition caption
        partitionCaptionPanel.removeAll();
        List<Partition> partitions = storageDevice.getPartitions();
        for (int i = 0, size = partitions.size(); i < size; i++) {
            Partition partition = partitions.get(i);
            JLabel label = new JLabel();

            // use small, non-bold font
            Font font = label.getFont();
            label.setFont(font.deriveFont(
                    font.getStyle() & ~Font.BOLD, font.getSize() - 1));

            boolean extended = partition.isExtended();

            // set color box
            try {
                if (partition.isSystemPartition()) {
                    label.setIcon(BLUE_BOX);
                } else if (partition.isPersistencePartition()) {
                    label.setIcon(GREEN_BOX);
                } else if (partition.isExchangePartition()) {
                    label.setIcon(YELLOW_BOX);
                } else if (extended) {
                    label.setIcon(DARK_GRAY_BOX);
                } else {
                    label.setIcon(GRAY_BOX);
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }

            // set text
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("<html><b>&#47;dev&#47;");
            stringBuilder.append(partition.getDeviceAndNumber());
            stringBuilder.append("</b> (");
            stringBuilder.append(LernstickFileTools.getDataVolumeString(
                    partition.getSize(), 1));
            stringBuilder.append(")<br>");
            if (extended) {
                stringBuilder.append(STRINGS.getString("Extended"));
                stringBuilder.append("<br>&nbsp;");
            } else {
                stringBuilder.append(STRINGS.getString("Label"));
                stringBuilder.append(": ");
                stringBuilder.append(partition.getIdLabel());
                stringBuilder.append("<br>");
                stringBuilder.append(STRINGS.getString("FileSystem"));
                stringBuilder.append(": ");
                stringBuilder.append(partition.getIdType());
                stringBuilder.append("<br>");
                stringBuilder.append(STRINGS.getString("Used"));
                stringBuilder.append(": ");
                try {
                    long usedSpace = partition.getUsedSpace(false);
                    if (usedSpace == -1) {
                        stringBuilder.append(STRINGS.getString("Unknown"));
                    } else {
                        stringBuilder.append(
                                LernstickFileTools.getDataVolumeString(
                                        usedSpace, 1));
                    }
                } catch (DBusExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                }
            }
            stringBuilder.append("</html>");
            label.setText(stringBuilder.toString());

            GridBagConstraints gridBagConstraints
                    = new GridBagConstraints();
            gridBagConstraints.anchor = GridBagConstraints.WEST;
            if (i == (size - 1)) {
                // last element
                gridBagConstraints.weightx = 1.0;
            } else {
                // non-last element
                gridBagConstraints.insets = new Insets(0, 0, 0, 20);
            }
            partitionCaptionPanel.add(label, gridBagConstraints);
        }

        // upgrade info text
        Partition dataPartition = storageDevice.getDataPartition();
        try {
            if ((dataPartition != null)
                    && dataPartition.isActivePersistencePartition()) {
                upgradeInfoLabel.setIcon(CANCEL_ICON);
                upgradeInfoLabel.setText(STRINGS.getString(
                        "Resetting_Impossible_Active_Data_Partition"));
            } else {
                upgradeInfoLabel.setIcon(OK_ICON);
                upgradeInfoLabel.setText(
                        STRINGS.getString("Resetting_Possible"));
            }
        } catch (DBusException | DBusExecutionException | IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
        } else {
            setBackground(list.getBackground());
        }

        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        LOGGER.log(Level.INFO,
                "maxStorageDeviceSize = {0}", maxStorageDeviceSize);

        // early return
        if (maxStorageDeviceSize == 0) {
            return;
        }

        // paint the partition rectangles
        Graphics2D graphics2D = (Graphics2D) g;
        int width = partitionGraphicsPanel.getWidth();
        int height = partitionGraphicsPanel.getHeight();
        Point location = rightPanel.getLocation();
        Point ppLocation = partitionPanel.getLocation();
        location.translate(ppLocation.x, ppLocation.y);
        Point pgpLocation = partitionGraphicsPanel.getLocation();
        location.translate(pgpLocation.x, pgpLocation.y);

        // border for storage device
        int deviceWidth = (int) ((width * storageDevice.getSize())
                / maxStorageDeviceSize);
        graphics2D.setPaint(Color.BLACK);
        graphics2D.drawRect(location.x, location.y, deviceWidth, height);

        for (Partition partition : storageDevice.getPartitions()) {

            LOGGER.log(Level.INFO,
                    "partition: {0}", partition.getDeviceAndNumber());

            // determine offset
            long partitionOffset = partition.getOffset();
            int offset = (int) ((width * partitionOffset)
                    / maxStorageDeviceSize);

            // determine width
            long partitionSize = partition.getSize();
            LOGGER.log(Level.INFO, "partitionSize = {0}", partitionSize);
            int partitionWidth
                    = (int) ((width * partitionSize) / maxStorageDeviceSize);
            LOGGER.log(Level.INFO, "partitionWidth = {0}", partitionWidth);

            // determine color
            LOGGER.log(Level.INFO, "partitionType: {0}", partition.getType());
            boolean extended = partition.isExtended();
            try {
                if (partition.isSystemPartition()) {
                    graphics2D.setPaint(LIGHT_BLUE);
                } else if (partition.isPersistencePartition()) {
                    graphics2D.setPaint(Color.GREEN);
                } else if (partition.isExchangePartition()) {
                    graphics2D.setPaint(Color.YELLOW);
                } else if (extended) {
                    graphics2D.setPaint(Color.DARK_GRAY);
                } else {
                    graphics2D.setPaint(Color.GRAY);
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }

            // paint colored partition rectangle
            int x = location.x + offset;
            int yOffset = extended ? 3 : 0;
            int y = location.y - yOffset;
            int partitionHeight = height + (2 * yOffset);
            graphics2D.fillRect(x, y, partitionWidth, partitionHeight);

            // paint partition storage space usage (if known)
            if (!extended) {
                try {
                    long usableSpace = partition.getUsedSpace(false);
                    if (usableSpace != -1) {
                        int usedWidth = (int) ((width * usableSpace)
                                / maxStorageDeviceSize);
                        graphics2D.setPaint(Color.LIGHT_GRAY);
                        int usageOffset = 4;
                        graphics2D.fillRect(x, y + usageOffset, usedWidth,
                                partitionHeight - (2 * usageOffset) + 1);
                    }
                } catch (DBusExecutionException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                }
            }

            // paint black border around partition
            graphics2D.setPaint(Color.BLACK);
            graphics2D.drawRect(x, y, partitionWidth, partitionHeight);
        }
    }

    @Override
    public void setMaxSize(long maxSize) {
        this.maxStorageDeviceSize = maxSize;
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

        iconLabel = new javax.swing.JLabel();
        rightPanel = new javax.swing.JPanel();
        descriptionLabel = new javax.swing.JLabel();
        partitionPanel = new javax.swing.JPanel();
        partitionGraphicsPanel = new javax.swing.JPanel();
        partitionCaptionPanel = new javax.swing.JPanel();
        upgradeInfoLabel = new javax.swing.JLabel();
        separator = new javax.swing.JSeparator();

        setLayout(new java.awt.GridBagLayout());

        iconLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        add(iconLabel, gridBagConstraints);

        rightPanel.setOpaque(false);
        rightPanel.setLayout(new java.awt.GridBagLayout());

        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(descriptionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings"); // NOI18N
        descriptionLabel.setText(bundle.getString("ResetStorageDeviceRenderer.descriptionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        rightPanel.add(descriptionLabel, gridBagConstraints);

        partitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true), bundle.getString("ResetStorageDeviceRenderer.partitionPanel.border.title"))); // NOI18N
        partitionPanel.setOpaque(false);
        partitionPanel.setLayout(new java.awt.GridBagLayout());

        partitionGraphicsPanel.setOpaque(false);
        partitionGraphicsPanel.setPreferredSize(new java.awt.Dimension(0, 30));
        partitionGraphicsPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
        partitionPanel.add(partitionGraphicsPanel, gridBagConstraints);

        partitionCaptionPanel.setOpaque(false);
        partitionCaptionPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 5);
        partitionPanel.add(partitionCaptionPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        rightPanel.add(partitionPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 0, 0, 0);
        rightPanel.add(upgradeInfoLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 3, 10);
        add(rightPanel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        add(separator, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel descriptionLabel;
    private javax.swing.JLabel iconLabel;
    private javax.swing.JPanel partitionCaptionPanel;
    private javax.swing.JPanel partitionGraphicsPanel;
    private javax.swing.JPanel partitionPanel;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JSeparator separator;
    private javax.swing.JLabel upgradeInfoLabel;
    // End of variables declaration//GEN-END:variables
}
