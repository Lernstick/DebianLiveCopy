package ch.fhnw.dlcopy.gui.swing;

import java.io.File;
import java.util.ResourceBundle;
import javax.swing.filechooser.FileFilter;

/**
 * a FileFilter that does not accept hidden files
 *
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class NoHiddenFilesSwingFileFilter extends FileFilter {

    private final static ResourceBundle BUNDLE
            = ResourceBundle.getBundle("ch/fhnw/dlcopy/Strings");
    private final static NoHiddenFilesSwingFileFilter INSTANCE
            = new NoHiddenFilesSwingFileFilter();

    private NoHiddenFilesSwingFileFilter() {
    }

    /**
     * returns an instance of NoHiddenFilesFileFilter
     *
     * @return an instance of NoHiddenFilesFileFilter
     */
    public static NoHiddenFilesSwingFileFilter getInstance() {
        return INSTANCE;
    }

    @Override
    public boolean accept(File file) {
        return !file.isHidden();
    }

    @Override
    public String getDescription() {
        return BUNDLE.getString("No_Hidden_Files");
    }
}
