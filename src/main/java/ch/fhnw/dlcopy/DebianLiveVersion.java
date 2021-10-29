package ch.fhnw.dlcopy;

import java.io.File;

/**
 * Supported version of Debian live installations.
 */
public enum DebianLiveVersion {
    /**
     * Debian 6 (squeeze)
     */
    DEBIAN_6("/live/image", "/usr/lib/syslinux/mbr.bin"),
    /**
     * Debian 7 (wheezy)
     */
    DEBIAN_7("/lib/live/mount/medium", "/usr/lib/syslinux/mbr.bin"),
    /**
     * Debian 10 (buster)
     * (must be probed before Debian 8 or 9 because /lib/live/mount/medium is
     * still there for compatibility reasons)
     */
    DEBIAN_10_to_11("/run/live/medium", "/usr/lib/syslinux/mbr/mbr.bin"),
    /**
     * Debian 8 (jessie) to Debian 9 (stretch)
     */
    DEBIAN_8_to_9("/lib/live/mount/medium", "/usr/lib/syslinux/mbr/mbr.bin");

    private final String liveSystemPath;
    private final String mbrFilePath;

    DebianLiveVersion(String liveSystemPath, String mbrFilePath) {
        this.liveSystemPath = liveSystemPath;
        this.mbrFilePath = mbrFilePath;
    }

    /**
     * returns the path where the live medium is mounted
     *
     * @return the path where the live medium is mounted
     */
    public String getLiveSystemPath() {
        return liveSystemPath;
    }

    /**
     * returns the path to the MBR file
     *
     * @return the path to the MBR file
     */
    public String getMbrFilePath() {
        return mbrFilePath;
    }

    /**
     * tries to detect the running Debian Live version
     *
     * @return the detected Debian Live version
     */
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
}
