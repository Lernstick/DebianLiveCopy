/*
 * UpgradeStorageDeviceRenderer.java
 *
 * Created on 16. April 2008, 13:23
 */
package dlcopy;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
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

            // set icon and device text based on storage type
            String description = null;
            long usbStorageSize = storageDevice.getSize();
            if (storageDevice instanceof UsbStorageDevice) {
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/drive-removable-media-usb-pendrive.png")));
                UsbStorageDevice usbStorageDevice =
                        (UsbStorageDevice) storageDevice;

                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("<html><b>");
                stringBuilder.append(usbStorageDevice.getVendor());
                stringBuilder.append(" ");
                stringBuilder.append(usbStorageDevice.getModel());
                stringBuilder.append(" ");
                stringBuilder.append(
                        DLCopy.getDataVolumeString(usbStorageSize, 1));
                stringBuilder.append(" (");
                stringBuilder.append(usbStorageDevice.getDevice());
                stringBuilder.append(")</b><br><small>");
                stringBuilder.append(DLCopy.STRINGS.getString("Revision"));
                stringBuilder.append(": ");
                stringBuilder.append(usbStorageDevice.getRevision());
                stringBuilder.append(", ");
                stringBuilder.append(DLCopy.STRINGS.getString("Serial"));
                stringBuilder.append(": ");
                stringBuilder.append(usbStorageDevice.getSerial());
                stringBuilder.append("</small></html>");

                description = stringBuilder.toString();

            } else if (storageDevice instanceof Harddisk) {
                Harddisk harddisk = (Harddisk) storageDevice;
                description = harddisk.getVendor() + " "
                        + harddisk.getModel() + ", "
                        + DLCopy.getDataVolumeString(usbStorageSize, 1) + " ("
                        + harddisk.getDevice() + ")";
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/drive-harddisk.png")));
            } else if (storageDevice instanceof SDStorageDevice) {
                SDStorageDevice sdStorageDevice =
                        (SDStorageDevice) storageDevice;
                description = sdStorageDevice.getName() + " "
                        + DLCopy.getDataVolumeString(usbStorageSize, 1) + " ("
                        + sdStorageDevice.getDevice() + ")";
                iconLabel.setIcon(new ImageIcon(getClass().getResource(
                        "/dlcopy/icons/32x32/media-flash-sd-mmc.png")));
            } else {
                LOGGER.log(Level.WARNING,
                        "unsupported storage device: {0}", storageDevice);
            }
            descriptionLabel.setText(description);

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
        
        Graphics2D graphics2D = (Graphics2D) g;
        int width = partitionPanel.getWidth();
        int height = partitionPanel.getHeight();
        Point location = rightPanel.getLocation();
        Point ppLocation = partitionPanel.getLocation();
        location.translate(ppLocation.x, ppLocation.y);

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
        partitionDescriptionPanel = new javax.swing.JPanel();
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
        gridBagConstraints.insets = new java.awt.Insets(3, 3, 0, 0);
        rightPanel.add(descriptionLabel, gridBagConstraints);

        partitionPanel.setOpaque(false);
        partitionPanel.setPreferredSize(new java.awt.Dimension(0, 30));

        javax.swing.GroupLayout partitionPanelLayout = new javax.swing.GroupLayout(partitionPanel);
        partitionPanel.setLayout(partitionPanelLayout);
        partitionPanelLayout.setHorizontalGroup(
            partitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 434, Short.MAX_VALUE)
        );
        partitionPanelLayout.setVerticalGroup(
            partitionPanelLayout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 30, Short.MAX_VALUE)
        );

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 5);
        rightPanel.add(partitionPanel, gridBagConstraints);

        partitionDescriptionPanel.setOpaque(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        rightPanel.add(partitionDescriptionPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
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
    private javax.swing.JPanel partitionDescriptionPanel;
    private javax.swing.JPanel partitionPanel;
    private javax.swing.JPanel rightPanel;
    private javax.swing.JSeparator separator;
    // End of variables declaration//GEN-END:variables
}
