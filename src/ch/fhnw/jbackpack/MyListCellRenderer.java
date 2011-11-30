/*
 * MyListCellRenderer.java
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
 * Created on 22. Februar 2004, 14:35
 */
package ch.fhnw.jbackpack;

import java.awt.Component;
import java.util.List;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import javax.swing.border.EmptyBorder;

/**
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class MyListCellRenderer extends JLabel implements ListCellRenderer {

    private final List<Icon> icons;

    /** Creates a new instance of MyListCellRenderer
     * @param icons the list of icons
     */
    public MyListCellRenderer(List<Icon> icons) {
        this.icons = icons;
        setHorizontalTextPosition(CENTER);
        setVerticalTextPosition(BOTTOM);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
        setOpaque(true);
        setBorder(new EmptyBorder(5, 5, 5, 5));
    }

    @Override
    public Component getListCellRendererComponent(JList list, Object value,
            int index, boolean isSelected, boolean cellHasFocus) {
        setText(value.toString());
        if (index < icons.size()) {
            setIcon(icons.get(index));
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }
        setEnabled(list.isEnabled());

        return this;
    }
}
