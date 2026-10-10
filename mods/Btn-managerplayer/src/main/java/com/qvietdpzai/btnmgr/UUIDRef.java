package com.qvietdpzai.btnmgr;

import java.util.UUID;

/** Định danh người chơi bất biến theo UUID. */
public final class UUIDRef {
    private final UUID id;

    public UUIDRef(UUID id) { this.id = id; }

    public UUID id() { return id; }

    @Override public boolean equals(Object o) {
        return o instanceof UUIDRef && id.equals(((UUIDRef) o).id);
    }

    @Override public int hashCode() { return id.hashCode(); }

    @Override public String toString() { return id.toString(); }
}
