/*
 * CurrentOperatingSystem.java
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
 * Created on 24.07.2010, 12:52:57
 *
 */
package ch.fhnw.util;

/**
 * The currently running operating system
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class CurrentOperatingSystem {

    /**
     * the currently running operating system
     */
    public static final OperatingSystem OS;

    static {
        String osName = System.getProperty("os.name");
        if (osName.equals("Linux")) {
            OS = OperatingSystem.Linux;
        } else if (osName.equals("Mac OS X")) {
            OS = OperatingSystem.Mac_OS_X;
        } else if (osName.startsWith("Windows")) {
            OS = OperatingSystem.Windows;
        } else {
            OS = OperatingSystem.Unknown;
        }
    }
}
