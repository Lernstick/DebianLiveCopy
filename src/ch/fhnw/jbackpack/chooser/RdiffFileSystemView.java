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

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Icon;
import javax.swing.UIManager;
import javax.swing.filechooser.FileSystemView;

/**
 * A FileSystemView for rdiff-backup directories
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffFileSystemView extends FileSystemView {

    private final static Logger LOGGER =
            Logger.getLogger(RdiffFileSystemView.class.getName());
    private final static ResourceBundle BUNDLE = ResourceBundle.getBundle(
            "ch/fhnw/jbackpack/Strings");
    private static final DateFormat DATE_FORMAT =
            DateFormat.getDateTimeInstance();
    private final static String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private final static String DOT_SUFFIX = File.separatorChar + ".";
    private final static String DOTDOT_SUFFIX = File.separatorChar + "..";
    private final File[] roots = new File[1];
    private RdiffFile root;

    /**
     * sets the root of this RdiffFileSystemView
     * @param root the root of this RdiffFileSystemView
     */
    public void setRoot(RdiffFile root) {
        this.root = root;
        roots[0] = root;
    }

    @Override
    public File createFileObject(File dir, String filename) {

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "dir: \"{0}\" filename: \"{1}\"",
                    new Object[]{dir, filename});
        }

        File fileObject = null;
        try {
            if (".".equals(filename)) {
                // return directory itself
                if (dir instanceof RdiffFile) {
                    if (dir.getName().equals(".")) {
                        // dir is already a dot directory
                        // no need to add another dot...
                        fileObject = dir;
                    } else {
                        if (dir.getName().equals("..")) {
                            dir = dir.getCanonicalFile();
                        }
                        RdiffFile rdiffFile = (RdiffFile) dir;
                        fileObject = rdiffFile.getDotDirectory();
                    }
                } else {
                    fileObject = dir;
                }

            } else if ("..".equals(filename)) {
                // return parent directory
                if (dir instanceof RdiffFile) {
                    RdiffFile rdiffFile = (RdiffFile) dir;
                    fileObject = rdiffFile.getDotDotDirectory();
                } else {
                    if (dir != null) {
                        fileObject = dir.getParentFile();
                    }
                }

            } else {
                // return specified file
                String fullPath = ((dir == null) ? "" : dir.getPath())
                        + File.separatorChar + filename;
                fileObject = root.getChild(fullPath);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "returning \"{0}\"",
                    fileObject == null ? null : fileObject.getAbsolutePath());
        }
        return fileObject;
    }

    @Override
    public File createFileObject(String path) {
        LOGGER.log(Level.FINE, "path: \"{0}\"", path);

        File fileObject = null;
        try {
            if (path.endsWith(DOT_SUFFIX)) {
                String canonicalPath = RdiffFile.canonicalize(path);
                RdiffFile directory = root.getChild(canonicalPath);
                if (directory != null) {
                    fileObject = directory.getDotDirectory();
                }

            } else if (path.endsWith(DOTDOT_SUFFIX)) {
                String childPath = null;
                int previousSeparatorIndex =
                        path.lastIndexOf(File.separatorChar,
                        path.length() - DOTDOT_SUFFIX.length() - 1);
                if (previousSeparatorIndex < 1) {
                    childPath = "/";
                } else {
                    childPath = path.substring(0, previousSeparatorIndex);
                }
                RdiffFile directory = root.getChild(childPath);
                fileObject = directory.getDotDotDirectory();

            } else {
                fileObject = root.getChild(path);
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "returning \"{0}\"",
                    fileObject == null ? null : fileObject.getAbsolutePath());
        }

        return fileObject;
    }

    @Override
    protected File createFileSystemRoot(File f) {
        LOGGER.log(Level.INFO, "createFileSystemRoot({0}): returning root", f);
        return root;
    }

    @Override
    public File createNewFolder(File containingDir) throws IOException {
        throw new UnsupportedOperationException(
                "Can not create folders in rdiff file system views");
    }

    @Override
    public File getChild(File parent, String fileName) {
        File child = null;

        File[] children = getFiles(parent, false);
        for (int i = 0; i < children.length; i++) {
            File tmpChild = children[i];
            if (tmpChild.getName().equals(fileName)) {
                child = tmpChild;
                break;
            }
        }

        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "getChild({0},{1}): returning {2}",
                    new Object[]{parent, fileName, child});
        }
        return child;
    }

    @Override
    public File getDefaultDirectory() {
        LOGGER.log(Level.FINEST, "returning root");
        return root;
    }

    @Override
    public File[] getFiles(File dir, boolean useFileHiding) {
        File[] files = useFileHiding
                ? dir.listFiles(NoHiddenFilesFileFilter.getInstance())
                : dir.listFiles();
        if (LOGGER.isLoggable(Level.FINEST)) {
            String fileList = fileArrayToString(files, "\t");
            LOGGER.log(Level.FINEST, "getFiles({0},{1}): returning {2}{3}",
                    new Object[]{dir, useFileHiding, LINE_SEPARATOR, fileList});
        }
        return files;
    }

    /**
     * returns the root directory of this rdiff-backup directory
     * @return the root directory of this rdiff-backup directory
     */
    @Override
    public File getHomeDirectory() {
        LOGGER.log(Level.FINEST, "returning root");
        return root;
    }

    @Override
    public File getParentDirectory(File dir) {
        if (dir == null) {
            LOGGER.log(Level.INFO, "directory is null -> returning null");
            return null;
        }
        File parentDirectory = dir.getParentFile();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "returning {0} as parent directory of {1}",
                    new Object[]{parentDirectory, dir});
        }
        return parentDirectory;
    }

    @Override
    public File[] getRoots() {
        LOGGER.log(Level.FINEST, "returning roots");
        return roots;
    }

    @Override
    public String getSystemDisplayName(File f) {
        String name = f.getName();
        if ((name.length() == 0) && (f instanceof RdiffFile)) {
            name = getRootName((RdiffFile) f);
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER,
                    "returning \"{0}\" as system display name for file \"{1}\"",
                    new Object[]{name, f});
        }
        return name;
    }

    @Override
    public Icon getSystemIcon(File f) {
        return UIManager.getIcon(f.isDirectory()
                ? "FileView.directoryIcon"
                : "FileView.fileIcon");
    }

    @Override
    public String getSystemTypeDescription(File f) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "returning \"null\" as system type "
                    + "description for file \"{0}\"", f);
        }
        return null;
    }

    @Override
    public boolean isComputerNode(File dir) {
        LOGGER.log(Level.FINEST, "{0} is no computer node", dir);
        return false;
    }

    @Override
    public boolean isDrive(File dir) {
        boolean isDrive = (dir.getParentFile() == null);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "{0} is{1} drive",
                    new Object[]{dir, isDrive ? " a" : " no"});
        }
        return isDrive;
    }

    @Override
    public boolean isFileSystem(File f) {
        LOGGER.log(Level.FINEST, "{0} is a real file or directory", f);
        return true;
    }

    @Override
    public boolean isFileSystemRoot(File dir) {
        boolean isFileSystemRoot = (dir == root);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "{0} is{1} a file system root",
                    new Object[]{dir, isFileSystemRoot ? "" : " NOT"});
        }
        return isFileSystemRoot;
    }

    @Override
    public boolean isFloppyDrive(File dir) {
        LOGGER.log(Level.FINEST, "{0} is no floppy drive", dir);
        return false;
    }

    @Override
    public boolean isHiddenFile(File f) {
        LOGGER.log(Level.INFO, "{0} is not hidden", f);
        return false;
    }

    @Override
    public boolean isParent(File folder, File file) {
        boolean isParent = false;
        if ((folder != null) && (file != null)) {
            String folderPath = folder.getAbsolutePath();
            if ("/".equals(folderPath)) {
                File parentFile = file.getParentFile();
                isParent =
                        ((parentFile == null) || (folder.equals(parentFile)));
            } else {
                isParent = folder.equals(file.getParentFile());
            }
        }

        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "{0} is{1} the parent of {2}",
                    new Object[]{folder, isParent ? "" : " NOT", file});
        }
        return isParent;
    }

    @Override
    public boolean isRoot(File f) {
        boolean isRoot = super.isRoot(f);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "{0} is{1} root",
                    new Object[]{f, isRoot ? "" : " NOT"});
        }
        return isRoot;
    }

    @Override
    public Boolean isTraversable(File f) {
        boolean isTraversable = false;
        if (f instanceof RdiffFile) {
            isTraversable = super.isTraversable(f);
        } else if (f == null) {
            isTraversable = false;
        } else {
            String path = f.getPath();
            if (path.startsWith(File.separator)) {
                path = path.substring(1);
            }
            try {
                RdiffFile rdiffFile = root.getChild(path);
                if (rdiffFile == null) {
                    LOGGER.log(Level.WARNING,
                            "could not find \"{0}\" in file system", path);
                } else {
                    isTraversable = super.isTraversable(rdiffFile);
                }
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0} is{1} traversable",
                    new Object[]{f, isTraversable ? "" : " NOT"});
        }
        return isTraversable;
    }

    /**
     * returns the name of a root file
     * @param root the root file
     * @return the name of the root file
     */
    public static String getRootName(RdiffFile root) {
        String name = null;
        Increment increment = root.getIncrement();
        if (increment != null) {
            Date timestamp = increment.getTimestamp();
            name = BUNDLE.getString("Backup_Of");
            name = MessageFormat.format(
                    name, DATE_FORMAT.format(timestamp));
        }
        return name;
    }

    private static String fileArrayToString(
            File[] files, String indentation) {
        // prefix every token with the indentation and postfix all but the last
        // token with the line separator
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0, length = files.length; i < length; i++) {
            stringBuilder.append(indentation);
            stringBuilder.append(files[i]);
            if (i < length - 1) {
                stringBuilder.append(LINE_SEPARATOR);
            }
        }
        return stringBuilder.toString();
    }
}
