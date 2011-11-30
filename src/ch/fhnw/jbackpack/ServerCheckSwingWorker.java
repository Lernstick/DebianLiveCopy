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
package ch.fhnw.jbackpack;

import ch.fhnw.util.ModalDialogHandler;
import ch.fhnw.util.ProcessExecutor;
import java.awt.Window;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

/**
 * A SwingWorker used for checking a remote server
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public abstract class ServerCheckSwingWorker
        extends SwingWorker<Boolean, Object> {

    /**
     * the password of the user at the host
     */
    protected final String password;
    private final static Logger LOGGER =
            Logger.getLogger(ServerCheckSwingWorker.class.getName());
    private final static String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private final static byte WRONG_PASSWORD = 42;
    private final Window parent;
    private final ProgressDialog progressDialog;
    private final ModalDialogHandler dialogHandler;
    private final ProcessExecutor processExecutor;
    private final String user;
    private final String host;
    private final String warningMessageKey;
    private boolean wrongPassword;

    /**
     * creates a new ServerCheckSwingWorker
     * @param parent the parent window
     * @param user the username on the remote host
     * @param host the name of the remote host
     * @param password the password of the user on the remote host
     * @param warningMessageKey the ressource bundle key of the warning message
     * to show when there is no remote support for rdiff-backup
     */
    public ServerCheckSwingWorker(Window parent, String user, String host,
            String password, String warningMessageKey) {
        this.parent = parent;
        this.user = user;
        this.host = host;
        this.password = password;
        this.warningMessageKey = warningMessageKey;
        processExecutor = new ProcessExecutor();
        progressDialog = new ProgressDialog(parent, processExecutor);
        String message = BUNDLE.getString("Remote_Checking_For_RdiffBackup");
        message = MessageFormat.format(message, host);
        progressDialog.setMessage(message);
        progressDialog.setCancelButtonVisible(false);
        dialogHandler = new ModalDialogHandler(progressDialog);
        dialogHandler.show();
    }

    /**
     * runs the operation
     * @param remoteSupport if <code>true</code>, the server has remote support
     * for rdiff-backup
     */
    protected abstract void runOperation(boolean remoteSupport);

    /**
     * executes the server check in a background thread
     * @return <code>true</code>, if the server supports rdiff-backup,
     * <code>false</code> otherwise
     */
    @Override
    protected Boolean doInBackground() {
        String checkScript = "#!/usr/bin/expect -f" + LINE_SEPARATOR
                + "set password [lindex $argv 0]" + LINE_SEPARATOR
                + "spawn -ignore HUP rdiff-backup --test-server "
                + user + '@' + host + "::/" + LINE_SEPARATOR
                + "while 1 {" + LINE_SEPARATOR
                + "    expect {" + LINE_SEPARATOR
                + "        eof {" + LINE_SEPARATOR
                + "            break" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "        \"Permission denied*\" {" + LINE_SEPARATOR
                + "            exit " + WRONG_PASSWORD + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "        \"continue connecting*\" {" + LINE_SEPARATOR
                + "            send \"yes\r\"" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "        \"" + user + '@' + host + "'s password:\" {"
                + LINE_SEPARATOR
                + "            send \"$password\r\"" + LINE_SEPARATOR
                + "        }" + LINE_SEPARATOR
                + "    }" + LINE_SEPARATOR
                + "}" + LINE_SEPARATOR
                + "set ret [lindex [wait] 3]" + LINE_SEPARATOR
                + "puts \"return value: $ret\"" + LINE_SEPARATOR
                + "exit $ret";

        // set level to OFF to prevent password leaking into
        // logfiles
        Logger logger = Logger.getLogger(
                ProcessExecutor.class.getName());
        Level level = logger.getLevel();
        logger.setLevel(Level.OFF);

        int returnValue = -1;
        try {
            returnValue = processExecutor.executeScript(true, true,
                    checkScript, (password == null) ? "" : password);
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        // restore previous log level
        logger.setLevel(level);

        wrongPassword = (WRONG_PASSWORD == returnValue);

        return (returnValue == 0);
    }

    /**
     * called when the server check is finished
     */
    @Override
    protected void done() {
        dialogHandler.hide();
        try {
            if (get()) {
                // cool, we have a working rdiff-backup on the remote end
                // the backup will be fast and painless
                runOperation(true);

            } else {
                if (wrongPassword) {
                    JOptionPane.showMessageDialog(parent,
                            BUNDLE.getString("Error_Wrong_Password"),
                            BUNDLE.getString("Error"),
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                String warningMessage = BUNDLE.getString(warningMessageKey);
                warningMessage = MessageFormat.format(warningMessage, host);
                int returnValue = JOptionPane.showOptionDialog(parent,
                        warningMessage, BUNDLE.getString("Warning"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE, null, null, null);
                if (returnValue == JOptionPane.OK_OPTION) {
                    runOperation(false);
                }
            }
        } catch (InterruptedException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        } catch (ExecutionException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }
}
