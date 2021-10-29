package ch.fhnw.dlcopy.gui.swing;

/**
 * a renderer for StorageDevices
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public interface StorageDeviceRenderer {

    /**
     * sets the size of the largest storage device
     *
     * @param maxSize the size of the largest storage device
     */
    public void setMaxSize(long maxSize);
}
