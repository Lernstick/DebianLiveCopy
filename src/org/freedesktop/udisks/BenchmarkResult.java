package org.freedesktop.udisks;

import org.freedesktop.dbus.Position;
import org.freedesktop.dbus.Tuple;

public final class BenchmarkResult<A, B, C> extends Tuple {

    @Position(0)
    public final A readTransferResults;
    @Position(1)
    public final B writeTransferResults;
    @Position(2)
    public final C accessTimeResults;

    public BenchmarkResult(A a, B b, C c) {
        this.readTransferResults = a;
        this.writeTransferResults = b;
        this.accessTimeResults = c;
    }
}