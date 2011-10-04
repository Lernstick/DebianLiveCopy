/*
 * ModalDialogHandler.java
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
 * Created on 30.08.2010, 08:57:49
 */
package dlcopy.tools;

import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JDialog;

/**
 * A tool class for handling concurrent showing/hiding of modal dialogs
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class ModalDialogHandler {

    private final static Logger LOGGER =
            Logger.getLogger(ModalDialogHandler.class.getName());
    private final JDialog dialog;
    private boolean showDialog;
    private boolean closeDialog;
    private boolean isShowing;
    private Condition dialogIsShowing;
    private Lock showingLock;

    /**
     * creates a new ModalDialogHandler
     * @param dialog the dialog to show/hide
     */
    public ModalDialogHandler(JDialog dialog) {
        this.dialog = dialog;
        showDialog = true;
        closeDialog = false;
        showingLock = new ReentrantLock();
        dialogIsShowing = showingLock.newCondition();
    }

    /**
     * tries to show the dialog
     */
    public synchronized void show() {
        if (showDialog) {
            dialog.addComponentListener(new ComponentAdapter() {

                @Override
                public void componentShown(ComponentEvent e) {
                    if (e.getID() == ComponentEvent.COMPONENT_SHOWN) {
                        dialogShown();
                    }
                }
            });
            EventQueue.invokeLater(new Runnable() {

                @Override
                public void run() {
                    dialog.setVisible(true);
                }
            });
            closeDialog = true;
            LOGGER.fine("dialog set visible");
        }
    }

    /**
     * tries to hide the dialog
     */
    public synchronized void hide() {
        showDialog = false;
        if (closeDialog) {
            new Thread() {

                @Override
                public void run() {
                    LOGGER.fine("waiting for dialog to become visible");
                    showingLock.lock();
                    try {
                        while (!isShowing) {
                            dialogIsShowing.await();
                        }
                    } catch (InterruptedException ex) {
                        LOGGER.log(Level.SEVERE, null, ex);
                    } finally {
                        showingLock.unlock();
                    }
                    LOGGER.fine("dialog became visible");
                    EventQueue.invokeLater(new Runnable() {

                        @Override
                        public void run() {
                            dialog.setVisible(false);
                        }
                    });
                }
            }.start();
        }
    }

    private void dialogShown() {
        try {
            showingLock.lock();
            isShowing = true;
            LOGGER.fine("dialog is showing");
            dialogIsShowing.signalAll();
        } finally {
            showingLock.unlock();
        }
    }
}
