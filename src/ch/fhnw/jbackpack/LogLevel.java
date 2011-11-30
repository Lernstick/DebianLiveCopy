/*
 * LogLevel.java
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
 * Created on 11. März 2006, 19:51
 */
package ch.fhnw.jbackpack;

import java.util.ResourceBundle;
import java.util.logging.Level;

/**
 * A wrapper for java.util.logging.Level with i18n and descriptions
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public enum LogLevel {

    /**
     * matches Level.OFF
     */
    OFF("OFF", Level.OFF),
    /**
     * matches Level.SEVERE
     */
    SEVERE("SEVERE", Level.SEVERE),
    /**
     * matches Level.WARNING
     */
    WARNING("WARNING", Level.WARNING),
    /**
     * matches Level.INFO
     */
    INFO("INFO", Level.INFO),
    /**
     * matches Level.CONFIG
     */
    CONFIG("CONFIG", Level.CONFIG),
    /**
     * matches Level.FINER
     */
    FINE("FINE", Level.FINE),
    /**
     * matches Level.FINER
     */
    FINER("FINER", Level.FINER),
    /**
     * matches Level.FINEST
     */
    FINEST("FINEST", Level.FINEST);
    private static final ResourceBundle BUNDLE =
            ResourceBundle.getBundle("ch/fhnw/jbackpack/Strings");
    private final String key;
    private final Level level;

    private LogLevel(String key, Level level) {
        this.key = key;
        this.level = level;
    }

    @Override
    public String toString() {
        return BUNDLE.getString("Loglevel_" + key);
    }

    /**
     * returns a localized description of the log level
     * @return
     */
    public String getDescription() {
        return BUNDLE.getString("Description_" + key);
    }

    /**
     * returns the corresponding Level
     * @return the corresponding Level
     */
    public Level getLevel() {
        return level;
    }
}
