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

import java.util.Date;

/**
 * A rdiff-backup timestamp
 * @author Ronny Standtke <ronny.standtke@fhnw.ch>
 */
public class RdiffTimestamp {

    private final Date timestamp;
    private final String filestamp;

    /**
     * creates a new RdiffTimestamp
     * @param timestamp the timestamp
     * @param filestamp the filename suffix used for this timestamp
     */
    public RdiffTimestamp(Date timestamp, String filestamp) {
        this.timestamp = timestamp;
        this.filestamp = filestamp;
    }

    /**
     * returns the timestamp
     * @return the timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * returns the filestamp
     * @return the filestamp
     */
    public String getFilestamp() {
        return filestamp;
    }
}
