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

import ch.fhnw.jbackpack.IconManager;
import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.filechooser.FileView;

/**
 * A FileView for rdiff-backup directories
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffFileView extends FileView {

    private static final Logger LOGGER =
            Logger.getLogger(RdiffFileView.class.getName());

    @Override
    public String getDescription(File f) {
        LOGGER.log(Level.INFO, "no description for {0}", f);
        return null;
    }

    @Override
    public Icon getIcon(File f) {
        Icon icon = null;
        if (f.getParentFile() == null) {
            icon = IconManager.HARD_DRIVE_ICON;
        } else if (f.isDirectory()) {
            icon = IconManager.DIRECTORY_ICON;
        } else {
            icon = IconManager.FILE_ICON;
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "icon for {0}: {1}", new Object[]{f, icon});
        }
        return icon;
    }

    @Override
    public String getName(File f) {
        String name = null;
        if ((f.getParentFile() == null) && (f instanceof RdiffFile)) {
            name = RdiffFileSystemView.getRootName((RdiffFile) f);
        } else {
            name = f.getName();
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER,
                    "name of {0} is {1}", new Object[]{f, name});
        }
        return name;
    }

    @Override
    public String getTypeDescription(File f) {
        LOGGER.log(Level.INFO, "no type description for {0}", f);
        return null;
    }

    @Override
    public Boolean isTraversable(File f) {
        boolean isTraversable = f.isDirectory();
        if (LOGGER.isLoggable(Level.FINER)) {
            if (isTraversable) {
                LOGGER.log(Level.FINER, "{0} is traversable", f);
            } else {
                LOGGER.log(Level.FINER, "{0} is NOT traversable", f);
            }
        }
        return isTraversable;
    }
}
