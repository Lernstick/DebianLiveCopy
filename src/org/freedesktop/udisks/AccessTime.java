package org.freedesktop.udisks;

import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt64;

public final class AccessTime extends Struct {

    @Position(0)
    public final UInt64 offset;
    @Position(1)
    public final double accessTime; // given in seconds

    public AccessTime(UInt64 a, double accessTime) {
        this.offset = a;
        this.accessTime = accessTime;
    }
}
