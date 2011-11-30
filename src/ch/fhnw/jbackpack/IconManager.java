/*
 * IconManager.java
 *
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
 *
 * Created on 26.07.2010, 12:13:46
 *
 */
package ch.fhnw.jbackpack;

import ch.fhnw.util.CurrentOperatingSystem;
import ch.fhnw.util.OperatingSystem;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.UIManager;

/**
 * Manages some icons (only necessary because of 
 * http://forums.sun.com/thread.jspa?forumID=257&threadID=518560 )
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class IconManager {

    /**
     * the error icon
     */
    public static final Icon ERROR_ICON;
    /**
     * the warning icon
     */
    public static final Icon WARNING_ICON;
    /**
     * the information icon
     */
    public static final Icon INFORMATION_ICON;
    /**
     * the file icon
     */
    public static final Icon FILE_ICON;
    /**
     * the directory icon
     */
    public static final Icon DIRECTORY_ICON;
    /**
     * the hard drive icon
     */
    public static final Icon HARD_DRIVE_ICON;
    private static final Logger LOGGER =
            Logger.getLogger(IconManager.class.getName());

    static {
        Icon errorIcon = UIManager.getIcon("OptionPane.errorIcon");
        if (errorIcon == null) {
            ERROR_ICON = new ImageIcon(IconManager.class.getResource(
                    "/ch/fhnw/jbackpack/icons/32x32/error.png"));
        } else {
            ERROR_ICON = errorIcon;
        }

        Icon warningIcon = UIManager.getIcon("OptionPane.warningIcon");
        if (warningIcon == null) {
            WARNING_ICON = new ImageIcon(IconManager.class.getResource(
                    "/ch/fhnw/jbackpack/icons/32x32/messagebox_warning.png"));
        } else {
            WARNING_ICON = warningIcon;
        }

        if (CurrentOperatingSystem.OS == OperatingSystem.Mac_OS_X) {
            // the default information icon on Mac OS X is a coffee cup :-P
            INFORMATION_ICON = new ImageIcon(IconManager.class.getResource(
                    "/ch/fhnw/jbackpack/icons/32x32/messagebox_info.png"));
        } else {
            Icon informationIcon = UIManager.getIcon(
                    "OptionPane.informationIcon");
            if (informationIcon == null) {
                INFORMATION_ICON = new ImageIcon(IconManager.class.getResource(
                        "/ch/fhnw/jbackpack/icons/32x32/messagebox_info.png"));
            } else {
                INFORMATION_ICON = informationIcon;
            }
        }

        Icon fileIcon = UIManager.getIcon("FileView.fileIcon");
        if (fileIcon == null) {
            LOGGER.warning("UIManager has no FileView.fileIcon");
            FILE_ICON = null;
        } else {
            FILE_ICON = fileIcon;
        }

        Icon directoryIcon = UIManager.getIcon("FileView.directoryIcon");
        if (directoryIcon == null) {
            LOGGER.warning("UIManager has no FileView.directoryIcon");
            DIRECTORY_ICON = null;
        } else {
            DIRECTORY_ICON = directoryIcon;
        }

        Icon hardDriveIcon = UIManager.getIcon("FileView.hardDriveIcon");
        if (hardDriveIcon == null) {
            LOGGER.warning("UIManager has no FileView.hardDriveIcon");
            HARD_DRIVE_ICON = null;
        } else {
            HARD_DRIVE_ICON = hardDriveIcon;
        }
    }
}
