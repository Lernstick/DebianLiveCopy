package ch.fhnw.dlcopy;

import ch.fhnw.filecopier.FileCopier;

/**
 * A common interface for Installer and Upgrader
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public interface InstallerOrUpgrader {

    /**
     * shows that file systems are being created
     */
    public void showCreatingFileSystems();

    /**
     * shows that files are being copied
     *
     * @param fileCopier the fileCopier used to copy files
     */
    public void showCopyingFiles(FileCopier fileCopier);

    /**
     * shows that file systems are being unmounted
     */
    public void showUnmounting();

    /**
     * shows that the boot sector is written
     */
    public void showWritingBootSector();
}
