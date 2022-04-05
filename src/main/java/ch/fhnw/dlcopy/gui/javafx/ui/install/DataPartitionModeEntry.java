package ch.fhnw.dlcopy.gui.javafx.ui.install;

import ch.fhnw.dlcopy.DataPartitionMode;
import java.util.ResourceBundle;

/**
 * Wrapper around DataPartitionMode that supports translations.
 */
public class DataPartitionModeEntry {
    private DataPartitionMode mode;
    private String name;
    private ResourceBundle stringBundle = ResourceBundle.getBundle("strings/Strings");

    public DataPartitionModeEntry(DataPartitionMode mode, String name) {
        this.mode = mode;
        this.name = name;
    }

    public DataPartitionMode getMode() {
        return mode;
    }

    public String getName() {
        return name;
    }

    public String toString(){
        return stringBundle.getString(name);
    }
}
