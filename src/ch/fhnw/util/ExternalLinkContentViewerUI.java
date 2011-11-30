/*
 * ExternalLinkContentViewerUI.java
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
 * Created on 04.08.2010, 18:24:31
 */
package ch.fhnw.util;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.help.JHelpContentViewer;
import javax.help.plaf.basic.BasicContentViewerUI;
import javax.swing.JComponent;
import javax.swing.event.HyperlinkEvent;
import javax.swing.plaf.ComponentUI;

/**
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ExternalLinkContentViewerUI extends BasicContentViewerUI {

    private static final Logger LOGGER = Logger.getLogger(
            ExternalLinkContentViewerUI.class.getName());

    /**
     * creates a new ExternalLinkContentViewerUI
     * @param viewer the JHelpContentViewer
     */
    public ExternalLinkContentViewerUI(JHelpContentViewer viewer) {
        super(viewer);
    }

    /**
     * creates the UI
     * @param component the component
     * @return the UI
     */
    public static ComponentUI createUI(JComponent component) {
        return new ExternalLinkContentViewerUI((JHelpContentViewer) component);
    }

    @Override
    public void hyperlinkUpdate(HyperlinkEvent event) {
        if (event.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
            URL url = event.getURL();
            String protocol = url.getProtocol();
            if ("mailto".equalsIgnoreCase(protocol)
                    || "http".equalsIgnoreCase(protocol)
                    || "ftp".equalsIgnoreCase(protocol)) {
                try {
                    Desktop.getDesktop().browse(url.toURI());
                } catch (URISyntaxException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                } catch (IOException ex) {
                    LOGGER.log(Level.WARNING, null, ex);
                }
                return;
            }
        }
        super.hyperlinkUpdate(event);
    }
}
