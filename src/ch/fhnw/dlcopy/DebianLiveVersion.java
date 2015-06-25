package ch.fhnw.dlcopy;

import java.io.File;

/**
 Supported version of Debian live installations.
 */
public enum DebianLiveVersion {
    /**
     * Debian 6 (squeeze)
     */
    DEBIAN_6 ("/live/image", "/usr/lib/syslinux/mbr.bin"),
    /**
     * Debian 7 (wheezy)
     */
    DEBIAN_7 ("/lib/live/mount/medium", "/usr/lib/syslinux/mbr.bin"),
    
    /**
     * Debian 8 (jessie)
     */
    DEBIAN_8 ("/lib/live/mount/medium", "/usr/lib/syslinux/mbr/mbr.bin");
    
    private final String liveSystemPath;
    private final String mbrFilePath;
    
    DebianLiveVersion(String liveSystemPath, String mbrFilePath) {
        this.liveSystemPath = liveSystemPath;
        this.mbrFilePath = mbrFilePath;
    }
    
    public String getLiveSystemPath() {
        return liveSystemPath;
    }
    
    public String getMbrFilePath() {
        return mbrFilePath;
    }
    
    public static DebianLiveVersion getRunningVersion() {
        for (DebianLiveVersion version : values()) {
            File liveDir = new File(version.liveSystemPath);
            File mbrFile = new File(version.mbrFilePath);
            if (liveDir.exists() && mbrFile.exists()) {
                return version;
            }
        }
        return null;
    }
    
    public static DebianLiveVersion getDistroVersion(String path) {
        // TODO: find out a way to determine the version from an ISO install
        // image
        return null;
    }
}
