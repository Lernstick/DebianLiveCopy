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

import ch.fhnw.util.CurrentOperatingSystem;
import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A file in a rdiff-backup directory
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffFile extends File {

    private final static Logger LOGGER =
            Logger.getLogger(RdiffFile.class.getName());
    private final static String LINE_SEPARATOR =
            System.getProperty("line.separator");
    private final Increment increment;
    private final RdiffFile parent;
    private final String absolutePath;
    private final String name;
    private final long fileSize;
    private final boolean directory;
    private final RdiffFileDatabase rdiffFileDatabase;
    private long modTime;

    /**
     * creates a new RdiffFile
     * @param rdiffFileDatabase the rdiff file database
     * @param increment the increment of this RdiffFile
     * @param parent the parent of this file
     * @param name the name of this file
     * @param length the size of this file
     * @param modTime the modification time of this file given in milliseconds
     * since epoch
     * @param directory if <code>true</code>, this file is a directory
     */
    public RdiffFile(RdiffFileDatabase rdiffFileDatabase, Increment increment,
            RdiffFile parent, String name, long length, long modTime,
            boolean directory) {

        super(parent, name);

        this.rdiffFileDatabase = rdiffFileDatabase;
        this.increment = increment;
        this.parent = parent;
        this.name = name;
        this.fileSize = length;
        this.modTime = modTime;
        this.directory = directory;
        if (parent == null) {
            // this is the root file
            name = ".";
            absolutePath = name;
        } else {
            // this are all non-root files
            if (parent.getParentFile() == null) {
                // nodes directly under the root node
                absolutePath = name;
            } else {
                // all other nodes
                String parentPath = parent.getPath();
                absolutePath = parentPath + separatorChar + name;
            }
        }
    }

    @Override
    public boolean canExecute() {
        LOGGER.log(Level.INFO, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public boolean canRead() {
        LOGGER.log(Level.INFO, "{0}: returning true", absolutePath);
        return true;
    }

    @Override
    public boolean canWrite() {
        LOGGER.log(Level.FINEST, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public int compareTo(File file) {
        int returnValue = absolutePath.compareTo(file.getPath());
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "{0} compareTo({1}) = {2}",
                    new Object[]{absolutePath, file, returnValue});
        }
        return returnValue;
    }

    @Override
    public boolean createNewFile() throws IOException {
        throw new IOException("can not alter rdiff-backup directories");
    }

    @Override
    public boolean delete() {
        LOGGER.log(Level.INFO, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public void deleteOnExit() {
        LOGGER.log(Level.INFO, "{0}: impossible", absolutePath);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            LOGGER.log(Level.FINEST, "{0} is the same object", obj);
            return true;
        }

        if (obj == null) {
            LOGGER.log(Level.FINEST, "{0}: other object is null", absolutePath);
            return false;
        }

        Class otherClass = obj.getClass();
        if (getClass() != otherClass) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "{0}: {1} is of class {2}",
                        new Object[]{absolutePath, obj, otherClass.getName()});
            }
            return false;
        }

        final RdiffFile other = (RdiffFile) obj;
        if ((increment != other.increment)
                && (increment == null || !increment.equals(other.increment))) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST, "{0}: {1} is of other incrememt",
                        new Object[]{absolutePath, obj});
            }
            return false;
        }

        if ((absolutePath == null)
                ? (other.absolutePath != null)
                : !canonicalize(absolutePath).equals(
                canonicalize(other.absolutePath))) {
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.log(Level.FINEST,
                        "{0}: {1} has other path ({2})",
                        new Object[]{absolutePath, obj, other.absolutePath});
            }
            return false;
        }

        return true;
    }

    @Override
    public boolean exists() {
        LOGGER.log(Level.FINEST, "{0}: returning true", absolutePath);
        return true;
    }

    @Override
    public File getAbsoluteFile() {
        LOGGER.log(Level.INFO, "{0}: returning this", absolutePath);
        return this;
    }

    @Override
    public String getAbsolutePath() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, absolutePath});
        }
        return absolutePath;
    }

    @Override
    public File getCanonicalFile() throws IOException {
        String canonicalPath = getCanonicalPath();

        RdiffFile root = this;
        for (RdiffFile tmp; (tmp = root.getParentRdiffFile()) != null;) {
            root = tmp;
        }
        File canonicalFile = root.getChild(canonicalPath);
        if (canonicalFile == null) {
            canonicalFile = this;
        }

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0}: returning {1}",
                    new Object[]{absolutePath, canonicalFile});
        }
        return canonicalFile;
    }

    /**
     * canonicalizes a path
     * @param path the path to canonicalize
     * @return the canonicalized path
     */
    public static String canonicalize(String path) {

        // handle intermediate "."
        // e.g. in Unix "/./home"
        String pattern = Pattern.quote(separatorChar + "." + separatorChar);
        path = path.replaceAll(pattern, Matcher.quoteReplacement(separator));

        // handle trailing "."
        // e.g. in Unix "/home/."
        String trailingDot = separatorChar + ".";
        if (path.endsWith(trailingDot)) {
            path = path.substring(0, path.length() - trailingDot.length());
        }

        // handle intermediate ".."
        // e.g. in Unix "/home/../bin"
        String doubleDot = separatorChar + ".." + separatorChar;
        for (int index = 0;
                (index = path.indexOf(doubleDot)) != -1;) {
            // compute prefix to keep
            String prefix = "";
            int previousSeparatorIndex =
                    path.lastIndexOf(separatorChar, index - 1);
            if (previousSeparatorIndex != -1) {
                prefix = path.substring(0, previousSeparatorIndex + 1);
            }
            // compute postfix to keep
            String postfix = path.substring(index + doubleDot.length());
            // join prefix and postfix
            path = prefix + postfix;
        }

        // handle trailing ".."
        // e.g. in Unix "/home/.."
        String trailingDoubleDot = separatorChar + "..";
        if (path.endsWith(trailingDoubleDot)) {
            int previousSeparatorIndex =
                    path.lastIndexOf(separatorChar,
                    path.length() - trailingDoubleDot.length() - 1);
            if (previousSeparatorIndex < 1) {
                path = separator;
            } else {
                path = path.substring(0, previousSeparatorIndex);
            }
        }

        return path;
    }

    @Override
    public String getCanonicalPath() throws IOException {
        return canonicalize(absolutePath);
    }

    @Override
    public long getFreeSpace() {
        LOGGER.log(Level.INFO, "{0}: returning 0", absolutePath);
        return 0;
    }

    @Override
    public String getName() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, name});
        }
        return name;
    }

    @Override
    public String getParent() {
        String parentPath = (parent == null) ? null : parent.getPath();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, parentPath});
        }
        return parentPath;
    }

    @Override
    public File getParentFile() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, parent});
        }
        return parent;
    }

    @Override
    public String getPath() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, absolutePath});
        }
        return absolutePath;
    }

    @Override
    public long getTotalSpace() {
        // TODO: implement?
        LOGGER.log(Level.INFO, "{0}: returning 0", absolutePath);
        return 0;
    }

    @Override
    public long getUsableSpace() {
        LOGGER.log(Level.INFO, "{0}: returning 0", absolutePath);
        return 0;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (increment == null ? 0 : increment.hashCode());
        hash = 71 * hash + (absolutePath == null ? 0 : absolutePath.hashCode());
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER,
                    "{0}: returning {1}", new Object[]{absolutePath, hash});
        }
        return hash;
    }

    @Override
    public boolean isAbsolute() {
        LOGGER.log(Level.FINEST, "{0}: returning true", absolutePath);
        return true;
    }

    @Override
    public boolean isDirectory() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            if (directory) {
                LOGGER.log(Level.FINEST, "{0} is a directory", absolutePath);
            } else {
                LOGGER.log(Level.FINEST,
                        "{0} is NOT a directory", absolutePath);
            }
        }
        return directory;
    }

    @Override
    public boolean isFile() {
        boolean isFile = !isDirectory();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "{0}: returning {1}", new Object[]{absolutePath, isFile});
        }
        return isFile;
    }

    @Override
    public boolean isHidden() {
        boolean hidden = false;
        switch (CurrentOperatingSystem.OS) {
            case Linux:
            case Mac_OS_X:
                hidden = (name.length() > 0) && name.charAt(0) == '.';
            // TODO: support for other operating systems
        }
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER,
                    "{0}: returning {1}", new Object[]{absolutePath, hidden});
        }
        return hidden;
    }

    @Override
    public long lastModified() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "{0}: returning {1}", new Object[]{absolutePath, modTime});
        }
        return modTime;
    }

    @Override
    public long length() {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST,
                    "{0}: returning {1}", new Object[]{absolutePath, fileSize});
        }
        return fileSize;
    }

    @Override
    public String[] list() {
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        int childrenCount = children.size();
        String[] childrenNames = new String[childrenCount];
        int i = 0;
        for (RdiffFile child : children) {
            childrenNames[i++] = child.getPath();
        }
        if (LOGGER.isLoggable(Level.INFO)) {
            String names = stringArrayToString(childrenNames, "\t");
            LOGGER.log(Level.INFO, "{0}: returning{1}{2}",
                    new Object[]{absolutePath, LINE_SEPARATOR, names});
        }
        return childrenNames;
    }

    @Override
    public String[] list(FilenameFilter filter) {
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        if (filter == null) {
            LOGGER.log(Level.INFO, "{0}: no filter", absolutePath);
            return list();
        }
        List<String> acceptedNames = new ArrayList<String>();
        for (RdiffFile child : children) {
            String childName = child.getName();
            if (filter.accept(this, childName)) {
                acceptedNames.add(childName);
            }
        }
        String[] namesArray =
                acceptedNames.toArray(new String[acceptedNames.size()]);
        if (LOGGER.isLoggable(Level.INFO)) {
            String names = stringArrayToString(namesArray, "\t");
            LOGGER.log(Level.INFO, "{0}: returning{1}{2}",
                    new Object[]{absolutePath, LINE_SEPARATOR, names});
        }
        return namesArray;
    }

    @Override
    public File[] listFiles() {
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        if (LOGGER.isLoggable(Level.FINER)) {
            String fileList = fileListToString(children, "\t");
            LOGGER.log(Level.FINER, "{0}: returning {1}{2}",
                    new Object[]{absolutePath, LINE_SEPARATOR, fileList});
        }
        return children.toArray(new File[children.size()]);
    }

    @Override
    public File[] listFiles(FilenameFilter filter) {
        if (filter == null) {
            LOGGER.log(Level.FINE, "{0}: no filter", absolutePath);
            return listFiles();
        }
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        List<RdiffFile> acceptedFiles = new ArrayList<RdiffFile>();
        for (RdiffFile child : children) {
            if (filter.accept(this, child.getName())) {
                acceptedFiles.add(child);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            String fileList = fileListToString(acceptedFiles, "\t");
            LOGGER.log(Level.FINE, "{0}: returning {1}{2}",
                    new Object[]{absolutePath, LINE_SEPARATOR, fileList});
        }
        return acceptedFiles.toArray(new File[acceptedFiles.size()]);
    }

    @Override
    public File[] listFiles(FileFilter filter) {
        if (filter == null) {
            LOGGER.log(Level.FINE, "{0}: no filter", absolutePath);
            return listFiles();
        }
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        List<RdiffFile> acceptedFiles = new ArrayList<RdiffFile>();
        for (RdiffFile child : children) {
            if (filter.accept(child)) {
                acceptedFiles.add(child);
            }
        }
        if (LOGGER.isLoggable(Level.FINE)) {
            String fileList = fileListToString(acceptedFiles, "\t");
            LOGGER.log(Level.FINE, "{0}: returning {1}{2}",
                    new Object[]{absolutePath, LINE_SEPARATOR, fileList});
        }
        return acceptedFiles.toArray(new File[acceptedFiles.size()]);
    }

    @Override
    public boolean mkdir() {
        LOGGER.log(Level.INFO, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public boolean mkdirs() {
        LOGGER.log(Level.INFO, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public boolean renameTo(File dest) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "{0}: can not rename to {1}, returning false",
                    new Object[]{absolutePath, dest});
        }
        return false;
    }

    @Override
    public boolean setExecutable(boolean executable, boolean ownerOnly) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "{0} setExecutable({1},{2}): returning false",
                    new Object[]{absolutePath, executable, ownerOnly});
        }
        return false;
    }

    @Override
    public boolean setExecutable(boolean executable) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0} setExecutable({1}): returning false",
                    new Object[]{absolutePath, executable});
        }
        return false;
    }

    @Override
    public boolean setLastModified(long time) {
        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "{0} setLastModified({1})",
                    new Object[]{absolutePath, time});
        }
        this.modTime = time;
        return true;
    }

    @Override
    public boolean setReadOnly() {
        LOGGER.log(Level.INFO, "{0}: returning false", absolutePath);
        return false;
    }

    @Override
    public boolean setReadable(boolean readable, boolean ownerOnly) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "{0} setReadable({1},{2}): returning false",
                    new Object[]{absolutePath, readable, ownerOnly});
        }
        return false;
    }

    @Override
    public boolean setReadable(boolean readable) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0} setReadable({1}): returning false",
                    new Object[]{absolutePath, readable});
        }
        return false;
    }

    @Override
    public boolean setWritable(boolean writable, boolean ownerOnly) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO,
                    "{0} setWritable({1},{2}): returning false",
                    new Object[]{absolutePath, writable, ownerOnly});
        }
        return false;
    }

    @Override
    public boolean setWritable(boolean writable) {
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0} setWritable({1}): returning false",
                    new Object[]{absolutePath, writable});
        }
        return false;
    }

    @Override
    public String toString() {
        /**
         * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
         * ! logging here leads to deadlocks between the AWT-EventQueue !
         * ! and the Basic L&F File Loading Thread                      !
         * !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
         */
//        if (LOGGER.isLoggable(Level.FINEST)) {
//            LOGGER.log(Level.FINEST, "{0}: returning \"{1}\"",
//                    new Object[]{absolutePath, absolutePath});
//        }
        return absolutePath;
    }

    @Override
    public URI toURI() {
        URI uri = super.toURI();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, uri});
        }
        return uri;
    }

    @Override
    @SuppressWarnings("deprecation")
    public URL toURL() throws MalformedURLException {
        URL url = super.toURL();
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(Level.INFO, "{0}: returning \"{1}\"",
                    new Object[]{absolutePath, url});
        }
        return url;
    }

    /**
     * returns the increment of this RdiffFile
     * @return the increment of this RdiffFile
     */
    public Increment getIncrement() {
        return increment;
    }

    /**
     * returns the "." file of this directory
     * @return the "." file of this directory
     * @throws IOException if an I/O exception occurs
     */
    public RdiffFile getDotDirectory() throws IOException {
        return new RdiffFile(rdiffFileDatabase, increment, parent, ".",
                fileSize, modTime, directory);
    }

    /**
     * returns the ".." file of this directory
     * @return the ".." file of this directory
     * @throws IOException if an I/O exception occurs
     */
    public RdiffFile getDotDotDirectory() throws IOException {
        if (parent == null) {
            return new RdiffFile(rdiffFileDatabase, increment, null, "..", 0,
                    modTime, true);
        } else {
            return new RdiffFile(rdiffFileDatabase, increment, this, "..", 0,
                    parent.lastModified(), true);
        }
    }

    /**
     * returns the parent RdiffFile
     * @return the parent RdiffFile
     */
    public RdiffFile getParentRdiffFile() {
        return parent;
    }

    /**
     * returns a file in this directory with the longest common match with a
     * given path
     * @param path the given path
     * @return the file in this directory with the longest common match with a
     * given path
     * @throws IOException if an I/O exception occurs
     */
    public File getLongestMatch(String path) throws IOException {
        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        // determine the name of the wanted child
        String wantedChild = path;
        int separatorIndex = path.indexOf(separatorChar);
        if (separatorIndex != -1) {
            wantedChild = path.substring(0, separatorIndex);
        }

        RdiffFile foundChild = findChild(children, wantedChild);
        if (foundChild == null) {
            // the wanted child does not exist
            // the longest match is this directory
            LOGGER.log(Level.FINE, "{0}: returning this", absolutePath);
            return this;
        } else {
            // recursively continue with remainder of the path
            return foundChild.getLongestMatch(
                    path.substring(separatorIndex + 1));
        }
    }

    private static String stringArrayToString(
            String[] strings, String indentation) {
        // prefix every token with the indentation and postfix all but the last
        // token with the line separator
        StringBuilder stringBuilder = new StringBuilder();
        for (int i = 0, length = strings.length; i < length; i++) {
            stringBuilder.append(indentation);
            stringBuilder.append(strings[i]);
            if (i < length - 1) {
                stringBuilder.append(LINE_SEPARATOR);
            }
        }
        return stringBuilder.toString();
    }

    private static String fileListToString(
            Collection<RdiffFile> files, String indentation) {
        // prefix every file with the indentation and postfix all but the last
        // file with the line separator
        StringBuilder stringBuilder = new StringBuilder();
        for (RdiffFile file : files) {
            stringBuilder.append(indentation);
            stringBuilder.append(file.getName());
            stringBuilder.append(LINE_SEPARATOR);
        }
        return stringBuilder.toString();
    }

    /**
     * returns a child with a given path
     * @param path the path
     * @return a child with a given path
     * @throws IOException if an I/O exception occurs
     */
    public RdiffFile getChild(String path) throws IOException {
        LOGGER.log(Level.FINE, "path: \"{0}\"", path);
        if (path.equals(absolutePath)) {
            return this;
        }

        List<RdiffFile> children = rdiffFileDatabase.listFiles(increment, this);
        int separatorIndex = path.indexOf(separatorChar);
        if (separatorIndex == -1) {
            // The given path is just a simple file.
            // Check if a child with this name already exists.
            return findChild(children, path);

        } else {
            // The given path is a directory. Cut off the first path element and
            // check if a given child with this name already exists.
            String childName = path.substring(0, separatorIndex);
            RdiffFile child = findChild(children, childName);
            if (child == null) {
                return null;
            } else {
                return child.getChild(path.substring(separatorIndex + 1));
            }
        }
    }

    private RdiffFile findChild(List<RdiffFile> children, String name) {
        if (children != null) {
            for (RdiffFile child : children) {
                if (child.getName().equals(name)) {
                    return child;
                }
            }
        }
        return null;
    }
}
