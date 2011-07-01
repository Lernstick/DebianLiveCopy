package org.freedesktop.udisks;

import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt64;

public final class TransferRate extends Struct {

    @Position(0)
    public final UInt64 offset;
    @Position(1)
    public final double transferRate; // given in bytes per second

    public TransferRate(UInt64 offset, double transferRate) {
        this.offset = offset;
        this.transferRate = transferRate;
    }
}
