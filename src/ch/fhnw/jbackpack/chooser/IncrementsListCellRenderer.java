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

import ch.fhnw.util.FileTools;
import java.awt.Component;
import java.text.DateFormat;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JList;

/**
 * A Renderer for rdiff-backup increments
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class IncrementsListCellRenderer extends DefaultListCellRenderer {

    private static final DateFormat DATE_FORMAT =
            DateFormat.getDateTimeInstance();
    private static final Icon MIRROR_ICON =
            new ImageIcon(IncrementsListCellRenderer.class.getResource(
            "/ch/fhnw/jbackpack/icons/16x16/hdd_unmount.png"));
    private static final Icon INCREMENT_ICON =
            new ImageIcon(IncrementsListCellRenderer.class.getResource(
            "/ch/fhnw/jbackpack/icons/16x16/up.png"));

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        super.getListCellRendererComponent(
                list, value, index, isSelected, cellHasFocus);
        if (value instanceof Increment) {
            Increment increment = (Increment) value;
            String text = DATE_FORMAT.format(increment.getTimestamp());
            if (increment.getSize() == null) {
                // size of this increment is still unknown
                setText(text);
            } else {
                // size of this increment is known
                String sizeString = FileTools.getDataVolumeString(
                        increment.getSize(), 1);
                setText(text + " (" + sizeString + ')');
            }
            setIcon(index == 0 ? MIRROR_ICON : INCREMENT_ICON);
        }
        return this;
    }
}
