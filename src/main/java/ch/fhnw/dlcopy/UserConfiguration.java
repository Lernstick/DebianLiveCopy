package ch.fhnw.dlcopy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * User specific configuration
 *
 * @author Ronny Standtke <ronny.standtke@gmx.net>
 */
public class UserConfiguration {

    private static final Logger LOGGER
            = Logger.getLogger(UserConfiguration.class.getName());

    private static final String GDM_AUTO_LOGIN_KEY = "AutomaticLoginEnable=";

    private final String passwdLine;
    private final String shadowLine;
    private final String groupLine;
    private final Boolean gdmAutoLogin;
    private List<String> groups;

    /**
     * creates a new instance of UserConfiguration
     *
     * @param rootDirectory the mount point to use as root directory
     * @throws IOException if an I/O exception occurs
     */
    public UserConfiguration(String rootDirectory) throws IOException {
        String userPrefix = "user:";

        passwdLine = getFirstPrefixLine(
                Paths.get(rootDirectory, "/etc/passwd"), userPrefix);

        shadowLine = getFirstPrefixLine(
                Paths.get(rootDirectory, "/etc/shadow"), userPrefix);

        groups = new ArrayList<>();
        Path groupPath = Paths.get(rootDirectory, "/etc/group");
        groupLine = getFirstPrefixLine(groupPath, userPrefix);
        if (Files.exists(groupPath)) {
            try (Stream<String> lines = Files.lines(groupPath)) {
                groups = lines
                        .filter(line -> groupContainsUser(line, "user"))
                        .map(line -> line.split(":")[0])
                        .collect(Collectors.toList());
            }
        } else {
            LOGGER.log(Level.WARNING, "path {0} doesn't exist", groupPath);
        }

        gdmAutoLogin = isAutomaticLoginEnabled(
                Paths.get(rootDirectory, "/etc/gdm3/daemon.conf"));

        LOGGER.log(Level.INFO, "\npasswdLine:\n{0}"
                + "\nshadowLine:\n{1}"
                + "\ngroupLine:\n{2}"
                + "\ngroups:\n{3}"
                + "\ngdmAutoLogin:\n{4}", new Object[]{
                    passwdLine, shadowLine, groupLine,
                    Arrays.toString(groups.toArray()), gdmAutoLogin});
    }

    public String getPasswdLine() {
        return passwdLine;
    }

    public String getShadowLine() {
        return shadowLine;
    }

    public String getGroupLine() {
        return groupLine;
    }

    public List<String> getGroups() {
        return groups;
    }

    public Boolean getGdmAutoLogin() {
        return gdmAutoLogin;
    }

    public void apply(String rootDirectory) throws IOException {

        appendLine(Paths.get(rootDirectory, "/etc/passwd"), passwdLine);

        appendLine(Paths.get(rootDirectory, "/etc/shadow"), shadowLine);

        Path groupPath = Paths.get(rootDirectory, "/etc/group");
        appendLine(groupPath, groupLine);
        if (groups != null) {
            try (Stream<String> lines = Files.lines(groupPath)) {
                final List<String> finalGroups = groups;
                List<String> newGroup = lines
                        .map(line -> addUsertoGroup(finalGroups, line))
                        .collect(Collectors.toList());
                Files.write(groupPath, newGroup);
            }
        }

        // TODO: integrate with live-config?
        /**
         * This doesn't work yet as exptected because gdm3 is also configured by
         * live-config (especially in /lib/live/config/0080-lernstick-gdm3).
         */
//        Path gdm3ConfPath = Paths.get(cowPath, "/etc/gdm3/daemon.conf");
//        try (Stream<String> lines = Files.lines(gdm3ConfPath)) {
//            final boolean finalGdmAutoLogin = gdmAutoLogin;
//            List<String> newConfig = lines
//                    .map(line -> {
//                        if (line.startsWith(GDM_AUTO_LOGIN_KEY)) {
//                            return GDM_AUTO_LOGIN_KEY
//                                    + (finalGdmAutoLogin
//                                            ? "true" : "false");
//                        }
//                        return line;
//                    })
//                    .collect(Collectors.toList());
//            Files.write(gdm3ConfPath, newConfig);
//        }
    }

    private static Boolean isAutomaticLoginEnabled(Path path) throws IOException {

        if (!Files.exists(path)) {
            LOGGER.log(Level.WARNING, "path {0} doesn't exist", path);
            return null;
        }

        // wrap into try-with-ressources block so that the stream gets closed
        // after reading all lines
        try (Stream<String> lines = Files.lines(path)) {
            return lines
                    .filter(line -> line.startsWith(GDM_AUTO_LOGIN_KEY))
                    .findFirst()
                    .map(line -> line.toLowerCase().replaceAll("\\s+", "").endsWith("true"))
                    .orElse(null);
        }
    }

    private void appendLine(Path path, String line) throws IOException {
        if (line != null) {
            Files.write(path, line.getBytes(), StandardOpenOption.APPEND);
        }
    }

    private String addUsertoGroup(List<String> groups, String line) {
        for (String group : groups) {
            if (line.startsWith(group + ':')) {
                if (!groupContainsUser(line, "user")) {
                    line += (line.endsWith(":") ? "" : ",") + "user";
                }
            }
        }
        return line;
    }

    private String getFirstPrefixLine(Path path, String prefix)
            throws IOException {

        if (!Files.exists(path)) {
            LOGGER.log(Level.WARNING, "path {0} doesn't exist", path);
            return null;
        }

        // wrap into try-with-ressources block so that the stream gets closed
        // after reading all lines
        try (Stream<String> lines = Files.lines(path)) {
            return lines.filter(line -> line.startsWith(prefix))
                    .findFirst().orElse(null);
        }
    }

    private boolean groupContainsUser(String groupLine, String user) {
        String[] tokens = groupLine.split(":");
        if (tokens.length > 3) {
            return tokens[3].contains(user);
        }
        return false;
    }

}
