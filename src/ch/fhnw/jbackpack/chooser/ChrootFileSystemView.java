/**
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
 */
package ch.fhnw.jbackpack.chooser;

import java.io.File;
import java.io.IOException;
import java.util.ResourceBundle;
import javax.swing.filechooser.FileSystemView;

/**
 * A FileSystemView that "chroot"s into a certain directory
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class ChrootFileSystemView extends FileSystemView {

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final File[] roots;
    private final String rootDisplayName;

    /**
     * creates a new ChrootFileSystemView
     * @param root the directory to "chroot" into
     * @param rootDisplayName the display name of the root
     */
    public ChrootFileSystemView(File root, String rootDisplayName) {
        roots = new File[1];
        roots[0] = root;
        this.rootDisplayName = rootDisplayName;
    }

    @Override
    public File createNewFolder(File containingDir) throws IOException {
        File newFolder = new File(
                containingDir, BUNDLE.getString("New_Folder"));
        newFolder.mkdir();
        return newFolder;
    }

    @Override
    public File[] getRoots() {
        return roots;
    }

    /**
     * returns the home directory
     * @return the home directory
     */
    @Override
    public File getHomeDirectory() {
        return roots[0];
    }

    @Override
    public String getSystemDisplayName(File file) {
        if (file.equals(roots[0])) {
            return rootDisplayName;
        }
        return super.getSystemDisplayName(file);
    }
}
