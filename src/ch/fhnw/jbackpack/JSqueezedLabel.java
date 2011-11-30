/*
 * JSqueezedLabel.java
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
 * Created on 03.07.2010, 08:43:23
 *
 */
package ch.fhnw.jbackpack;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import javax.swing.JLabel;
import javax.swing.border.Border;

/**
 * a JLabel that can squeeze its text
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class JSqueezedLabel extends JLabel {

    private String originalText;

    /**
     * creates a new JSqueezedLabel
     */
    public JSqueezedLabel() {
        addComponentListener(new ComponentAdapter() {

            @Override
            public void componentResized(ComponentEvent evt) {
                String squeezedText = squeeze();
                JSqueezedLabel.super.setText(squeezedText);
            }
        });
    }

    @Override
    public void setText(String text) {
        originalText = text;
        String squeezedText = squeeze();
        super.setText(squeezedText);
    }

    private String squeeze() {
        if (originalText == null) {
            return null;
        }
        Font font = getFont();
        if (font == null) {
            return originalText;
        }
        FontMetrics fontMetrics = getFontMetrics(font);
        int originalWidth = fontMetrics.stringWidth(originalText);
        int width = getWidth();
        Border border = getBorder();
        if (border != null) {
            Insets borderInsets = border.getBorderInsets(this);
            width -= (borderInsets.left + borderInsets.right);
        }
        if (originalWidth < width) {
            return originalText;
        } else {
            // first guess
            int originalLength = originalText.length();
            int squeezedLength = (originalLength * width) / originalWidth;
            if (squeezedLength == 0) {
                return ".";
            } else {
                String dottedString = getDottedString(
                        originalText, squeezedLength);
                int dottedWidth = fontMetrics.stringWidth(dottedString);

                // linear convergence
                String squeezedString;
                if (dottedWidth < width) {
                    do {
                        squeezedString = dottedString;
                        squeezedLength++;
                        dottedString = getDottedString(
                                originalText, squeezedLength);
                        dottedWidth = fontMetrics.stringWidth(dottedString);
                    } while (dottedWidth < width);
                } else {
                    do {
                        squeezedLength--;
                        dottedString = getDottedString(
                                originalText, squeezedLength);
                        dottedWidth = fontMetrics.stringWidth(dottedString);
                        squeezedString = dottedString;
                    } while ((dottedWidth > width) && (squeezedLength > 0));
                }
                return squeezedString;
            }
        }
    }

    private String getDottedString(String string, int length) {
        int stringLength = string.length();
        if (stringLength < length) {
            // no need to produce a dotted string
            return string;
        } else {
            int halfLength = length / 2;
            return string.substring(0, halfLength) + "..."
                    + string.substring(stringLength - halfLength, stringLength);
        }
    }
}
