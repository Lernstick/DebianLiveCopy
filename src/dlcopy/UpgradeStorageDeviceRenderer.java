/*
 * UpgradeStorageDeviceRenderer.java
 *
 * Created on 16. April 2008, 13:23
 */
package dlcopy;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Point;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * A renderer for storage devices
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class UpgradeStorageDeviceRenderer
        extends JPanel
        implements ListCellRenderer {

    private final static Logger LOGGER =
            Logger.getLogger(DLCopy.class.getName());
    private final static Icon blueBox = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/blue_box.png"));
    private final static Icon greenBox = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/green_box.png"));
    private final static Icon yellowBox = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/yellow_box.png"));
    private final static Icon grayBox = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/gray_box.png"));
    private final static Icon okIcon = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/16x16/dialog-ok-apply.png"));
    private final static Icon cancelIcon = new ImageIcon(
            UpgradeStorageDeviceRenderer.class.getResource(
            "/dlcopy/icons/16x16/dialog-cancel.png"));
    private final long systemSize;
    private final Color LIGHT_BLUE = new Color(170, 170, 255);
    private long maxStorageDeviceSize;
    private StorageDevice storageDevice;

    /** Creates new form UsbRenderer
     * @param systemSize the size of the system to be copied in Byte
     */
    public UpgradeStorageDeviceRenderer(long systemSize) {
        this.systemSize = systemSize;
        initComponents();
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {

        if (value instanceof StorageDevice) {
            storageDevice = (StorageDevice) value;

            // set icon based on storage type
            if (storageDevice instanceof UsbStorageDevice) {
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png")));
            } else if (storageDevice instanceof Harddisk) {
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/drive-harddisk.png")));
            } else if (storageDevice instanceof SDStorageDevice) {
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/media-flash-sd-mmc.png")));
            } else {
                LOGGER.log(Level.WARNING,
                        "unsupported storage device: {0}", storageDevice);
            }

            // set device text
            fillDeviceText(descriptionLabel, storageDevice);

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

                // set color box
                try {
                    if (partition.isSystemPartition()) {
                        label.setIcon(blueBox);
                    } else if (partition.isPersistencyPartition()) {
                        label.setIcon(greenBox);
                    } else if (partition.getTypeID().equals("c")) {
                        label.setIcon(yellowBox);
                    } else {
                        label.setIcon(grayBox);
                    }
                } catch (DBusException ex) {
                    LOGGER.log(Level.SEVERE, "", ex);
                }

                // set text
                long partitionSize = storageDevice.getBlockSize()
                        * partition.getSectorCount();
                label.setText("<html><b>&#47;dev&#47;" + partition.getDevice()
                        + "</b> ("
                        + DLCopy.getDataVolumeString(partitionSize, 1) + ")<br>"
                        + DLCopy.STRINGS.getString("Label") + ": "
                        + partition.getLabel() + "<br>"
                        + DLCopy.STRINGS.getString("File_System") + ": "
                        + partition.getFileSystem() + "</html>");

                GridBagConstraints gridBagConstraints =
                        new GridBagConstraints();
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
            boolean systemPartitionFound = false;
            boolean sizeFits = false;
            for (Partition partition : partitions) {
                try {
                    if (partition.isSystemPartition()) {
                        systemPartitionFound = true;
                        long partitionSize = storageDevice.getBlockSize()
                                * partition.getSectorCount();
                        sizeFits = partitionSize > (systemSize / 1.1f);
                        break; // for
                    }
                } catch (DBusException ex) {
                    LOGGER.log(Level.SEVERE, null, ex);
                }
            }
            StringBuilder stringBuilder = new StringBuilder();
            if (systemPartitionFound && sizeFits) {
                upgradeInfoLabel.setIcon(okIcon);
                stringBuilder.append(
                        DLCopy.STRINGS.getString("Upgrading_Possible"));
            } else {
                upgradeInfoLabel.setIcon(cancelIcon);
                stringBuilder.append(
                        DLCopy.STRINGS.getString("Upgrading_Impossible"));
                stringBuilder.append(": ");
                if (systemPartitionFound) {
                    stringBuilder.append(DLCopy.STRINGS.getString(
                            "System_Partition_Too_Small"));
                } else {
                    stringBuilder.append(DLCopy.STRINGS.getString(
                            "No_System_Partition_Found"));
                }
            }
            upgradeInfoLabel.setText(stringBuilder.toString());

            if (isSelected) {
                setBackground(list.getSelectionBackground());
            } else {
                setBackground(list.getBackground());
            }
        }

        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // paint the partition rectangles
        // TODO: correct handling of extended partitions
        // (paint a little bit bigger, do not increase "x")

        Graphics2D graphics2D = (Graphics2D) g;
        int width = partitionGraphicsPanel.getWidth();
        int height = partitionGraphicsPanel.getHeight();
        Point location = rightPanel.getLocation();
        Point pgLocation = partitionPanel.getLocation();
        location.translate(pgLocation.x, pgLocation.y);
        Point pgpLocation = partitionGraphicsPanel.getLocation();
        location.translate(pgpLocation.x, pgpLocation.y);

        int blockSize = storageDevice.getBlockSize();
        int x = location.x;
        for (Partition partition : storageDevice.getPartitions()) {

            // width
            long partitionSize = blockSize * partition.getSectorCount();
            int partitionWidth =
                    (int) ((width * partitionSize) / maxStorageDeviceSize);

            // color
            try {
                if (partition.isSystemPartition()) {
                    graphics2D.setPaint(LIGHT_BLUE);
                } else if (partition.isPersistencyPartition()) {
                    graphics2D.setPaint(Color.GREEN);
                } else if (partition.getTypeID().equals("c")) {
                    graphics2D.setPaint(Color.YELLOW);
                } else {
                    graphics2D.setPaint(Color.GRAY);
                }
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            }

            graphics2D.fillRect(x, location.y, partitionWidth, height);
            x += partitionWidth;
        }
    }

    /**
     * sets the size of the largest USB stick
     * @param maxSize the size of the largest USB stick
     */
    public void setMaxSize(long maxSize) {
        this.maxStorageDeviceSize = maxSize;
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

        iconLabel = new javax.swing.JLabel();
        rightPanel = new javax.swing.JPanel();
        descriptionLabel = new javax.swing.JLabel();
        partitionPanel = new javax.swing.JPanel();
        partitionGraphicsPanel = new javax.swing.JPanel();
        partitionCaptionPanel = new javax.swing.JPanel();
        upgradeInfoLabel = new javax.swing.JLabel();
        separator = new javax.swing.JSeparator();

        setLayout(new java.awt.GridBagLayout());

        iconLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
        add(iconLabel, gridBagConstraints);

        rightPanel.setOpaque(false);
        rightPanel.setLayout(new java.awt.GridBagLayout());

        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(descriptionLabel.getFont().getStyle() & ~java.awt.Font.BOLD));
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("dlcopy/Strings"); // NOI18N
        descriptionLabel.setText(bundle.getString("UpgradeStorageDeviceRenderer.descriptionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        rightPanel.add(descriptionLabel, gridBagConstraints);

        partitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(new javax.swing.border.LineBorder(new java.awt.Color(0, 0, 0), 1, true), bundle.getString("UpgradeStorageDeviceRenderer.partitionPanel.border.title"), javax.swing.border.TitledBorder.DEFAULT_JUSTIFICATION, javax.swing.border.TitledBorder.DEFAULT_POSITION, new java.awt.Font("Dialog", 0, 12))); // NOI18N
        partitionPanel.setOpaque(false);
        partitionPanel.setLayout(new java.awt.GridBagLayout());

        partitionGraphicsPanel.setOpaque(false);
        partitionGraphicsPanel.setPreferredSize(new java.awt.Dimension(0, 30));

        javax.swing.GroupLayout partitionGraphicsPanelLayout = new javax.swing.GroupLayout(partitionGraphicsPanel);
        partitionGraphicsPanel.setLayout(partitionGraphicsPanelLayout);
        partitionGraphicsPanelLayout.setHorizontalGroup(
            partitionGraphicsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 409, Short.MAX_VALUE)
        );
        partitionGraphicsPanelLayout.setVerticalGroup(
            partitionGraphicsPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 30, Short.MAX_VALUE)
        );

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

    private void fillDeviceText(JLabel label, StorageDevice storageDevice) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("<html><b>");
        String vendor = storageDevice.getVendor();
        if (!vendor.isEmpty()) {
            stringBuilder.append(vendor);
            stringBuilder.append(" ");
        }
        stringBuilder.append(storageDevice.getModel());
        stringBuilder.append("</b>, ");
        stringBuilder.append(DLCopy.STRINGS.getString("Size"));
        stringBuilder.append(": ");
        stringBuilder.append(
                DLCopy.getDataVolumeString(storageDevice.getSize(), 1));
        stringBuilder.append(", ");
        stringBuilder.append(DLCopy.STRINGS.getString("Revision"));
        stringBuilder.append(": ");
        stringBuilder.append(storageDevice.getRevision());
        stringBuilder.append(", ");
        stringBuilder.append(DLCopy.STRINGS.getString("Serial"));
        stringBuilder.append(": ");
        stringBuilder.append(storageDevice.getSerial());
        stringBuilder.append(", ");
        stringBuilder.append(storageDevice.getDevice());
        stringBuilder.append("</html>");
        label.setText(stringBuilder.toString());
    }
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
