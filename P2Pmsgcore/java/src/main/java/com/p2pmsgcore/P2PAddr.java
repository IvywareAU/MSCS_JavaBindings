package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper for the C++ P2Paddr class.
 * Represents a P2Peer virtual-network address of the form
 * {@code [VNetname:]RootHubname.Hubname[i]...Hubname}.
 *
 * <p>Usage:
 * <pre>{@code
 * try (P2PAddr addr = new P2PAddr("Hub1.Hub2")) {
 *     System.out.println(addr.name());   // "Hub1.Hub2"
 * }
 * }</pre>
 */
public final class P2PAddr implements AutoCloseable {

    private final Arena arena = Arena.ofConfined();
    private final MemorySegment handle;
    private boolean closed = false;

    /** Default constructor – creates an empty/null address. */
    public P2PAddr() {
        this.handle = P2Pmsgcore_c.p2paddr_create();
    }

    /** Creates an address from a string such as {@code "Hub1.Hub2"}. */
    public P2PAddr(String addr) {
        try (Arena tmp = Arena.ofConfined()) {
            this.handle = P2Pmsgcore_c.p2paddr_create_str(NativeStrings.toWStr(addr, tmp));
        }
    }

    /** Wraps a raw native handle – internal use by other wrapper classes. */
    P2PAddr(MemorySegment handle) {
        this.handle = handle;
    }

    MemorySegment rawHandle() {
        checkOpen();
        return handle;
    }

    /** Returns the string representation of this address. */
    public String name() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2paddr_c_name(handle));
    }

    /** Returns {@code true} if the address is null (unset). */
    public boolean isNull() {
        checkOpen();
        return P2Pmsgcore_c.p2paddr_is_null(handle) != 0;
    }

    /** Returns {@code true} if the address string is empty. */
    public boolean isEmpty() {
        checkOpen();
        return P2Pmsgcore_c.p2paddr_is_empty(handle) != 0;
    }

    /** Returns the serialised byte size of this address. */
    public int byteSize() {
        checkOpen();
        return Short.toUnsignedInt(P2Pmsgcore_c.p2paddr_sizeof(handle));
    }

    /**
     * Returns {@code true} if this address is a child of {@code other}
     * in the hub hierarchy.
     */
    public boolean isChild(String other) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2paddr_is_child(handle, NativeStrings.toWStr(other, tmp)) != 0;
        }
    }

    /**
     * Returns {@code true} if a message addressed to {@code other} can be
     * routed through this address.
     */
    public boolean isRable(String other) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2paddr_is_rable(handle, NativeStrings.toWStr(other, tmp)) != 0;
        }
    }

    // ── UTF-8 (portable) surface ────────────────────────────────────────────────
    // These use the _u8 C entry points, which carry the string as UTF-8 rather than
    // the platform wchar_t. Prefer them for encoding-portable behaviour (identical
    // on a Windows DLL and the Linux libp2pmsgcore.so).

    /** Creates an address from a UTF-8 string via the portable _u8 C API. */
    public static P2PAddr ofUtf8(String addr) {
        try (Arena tmp = Arena.ofConfined()) {
            return new P2PAddr(P2Pmsgcore_c.p2paddr_create_str_u8(NativeStrings.toU8(addr, tmp)));
        }
    }

    /** Returns the address's leaf name as a UTF-8-decoded String. */
    public String nameUtf8() {
        checkOpen();
        return NativeStrings.fromU8(P2Pmsgcore_c.p2paddr_c_name_u8(handle));
    }

    /** UTF-8 variant of {@link #isChild(String)}. */
    public boolean isChildUtf8(String other) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2paddr_is_child_u8(handle, NativeStrings.toU8(other, tmp)) != 0;
        }
    }

    /** UTF-8 variant of {@link #isRable(String)}. */
    public boolean isRableUtf8(String other) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2paddr_is_rable_u8(handle, NativeStrings.toU8(other, tmp)) != 0;
        }
    }

    @Override
    public String toString() {
        return closed ? "<closed>" : name();
    }

    @Override
    public void close() {
        if (!closed) {
            P2Pmsgcore_c.p2paddr_destroy(handle);
            arena.close();
            closed = true;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("P2PAddr already closed");
    }
}
