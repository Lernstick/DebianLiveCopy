package org.freedesktop.udisks;

import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Struct;
import org.freedesktop.dbus.UInt32;

public final class OpenFile extends Struct {

    @Position(0)
    public final UInt32 pid;
    @Position(1)
    public final UInt32 uid;
    @Position(2)
    public final String commandLine;

    public OpenFile(UInt32 pid, UInt32 uid, String commandLine) {
        this.pid = pid;
        this.uid = uid;
        this.commandLine = commandLine;
    }
}
