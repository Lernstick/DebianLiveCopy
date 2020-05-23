/*
 * UpgradeStorageDeviceRenderer.java
 *
 * Created on 23. Mai 2020, 15:55
 */
package ch.fhnw.dlcopy.gui.swing;

import ch.fhnw.dlcopy.SystemSource;

/**
 * A renderer for storage devices to upgrade
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UpgradeStorageDeviceRenderer
        extends DetailedStorageDeviceRenderer {

    public UpgradeStorageDeviceRenderer(SystemSource source) {
        super(source, true);
    }
}
