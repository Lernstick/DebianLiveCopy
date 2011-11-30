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
 *
 */
package ch.fhnw.jbackpack;

import java.awt.Window;

/**
 * A SwingWorker used for checking a remote server
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class BackupServerCheckSwingWorker extends ServerCheckSwingWorker {

    private final BackupMainPanel backupMainPanel;
    private final Long minFileSize;
    private final Long maxFileSize;

    /**
     * creates a new BackupServerCheckSwingWorker
     * @param parent the parent window
     * @param user the username on the remote host
     * @param host the name of the remote host
     * @param password the password of the user on the remote host
     * @param backupMainPanel the BackupMainPanel
     * @param minFileSize the minimal file size
     * @param maxFileSize the maximum file size
     */
    public BackupServerCheckSwingWorker(Window parent, String user, String host,
            String password, BackupMainPanel backupMainPanel, Long minFileSize,
            Long maxFileSize) {
        super(parent, user, host, password, "Warning_No_Remote_Support_Backup");
        this.backupMainPanel = backupMainPanel;
        this.minFileSize = minFileSize;
        this.maxFileSize = maxFileSize;
    }

    @Override
    protected void runOperation(boolean remoteSupport) {
        backupMainPanel.runBackup(
                minFileSize, maxFileSize, remoteSupport, password);
    }
}
