package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper for the C++ P2PeerHub class.
 * A hub routes {@link P2PMsg} messages between registered peers and
 * manages the lifecycle of {@link P2PeerConWsa} connections.
 *
 * <p>Typical usage:
 * <pre>{@code
 * try (P2PeerHub hub = new P2PeerHub("MyHub")) {
 *     hub.createHub("MyHub", 1);          // create IOCP pump with 1 thread
 *     hub.spawnHub();                      // start the hub thread
 *
 *     P2PeerConWsa svc = P2PeerConWsa.serviceFactory("MyHub", 9000);
 *     hub.postConnection(svc, 0);          // hub now owns svc
 *
 *     // ... run until done ...
 *     hub.closeHub();
 * }
 * }</pre>
 */
public final class P2PeerHub implements AutoCloseable {

    private final MemorySegment handle;
    private boolean closed = false;

    /**
     * Creates a hub with the given P2P address.
     * Call {@link #createHub} before {@link #spawnHub}.
     */
    public P2PeerHub(String hubAddr) {
        try (Arena tmp = Arena.ofConfined()) {
            this.handle = P2Pmsgcore_c.p2peerhub_create(NativeStrings.toWStr(hubAddr, tmp));
        }
        if (handle == null || handle.equals(MemorySegment.NULL))
            throw new RuntimeException("p2peerhub_create returned null");
    }

    // ── Hub lifecycle ─────────────────────────────────────────────────────────

    /**
     * Initialises the hub's IO completion port and message pumps.
     *
     * @param hubAddr   P2P address for this hub
     * @param pumpsMax  number of pump threads (1 is typical)
     * @return {@code true} on success
     */
    public boolean createHub(String hubAddr, int pumpsMax) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2peerhub_create_hub(
                    handle, NativeStrings.toWStr(hubAddr, tmp), pumpsMax) != 0;
        }
    }

    /**
     * Spawns the hub processing thread.
     * Returns the Win32 thread HANDLE as a {@code long} (cast from pointer).
     */
    public long spawnHub() {
        checkOpen();
        MemorySegment threadHandle = P2Pmsgcore_c.p2peerhub_spawn_hub(handle);
        return threadHandle == null ? 0L : threadHandle.address();
    }

    /** Signals all pumps to stop and waits for shutdown. */
    public void closeHub() {
        checkOpen();
        P2Pmsgcore_c.p2peerhub_close_hub(handle);
    }

    /** Pauses the hub's processing loop. */
    public void pauseHub() {
        checkOpen();
        P2Pmsgcore_c.p2peerhub_pause_hub(handle);
    }

    /** Resumes a paused hub. */
    public void wakeupHub() {
        checkOpen();
        P2Pmsgcore_c.p2peerhub_wakeup_hub(handle);
    }

    // ── Connection management ─────────────────────────────────────────────────

    /**
     * Transfers ownership of {@code con} to this hub and registers it with
     * pump {@code pumpID}.  Do not close {@code con} after this call.
     *
     * @return {@code true} on success
     */
    public boolean postConnection(P2PeerConWsa con, int pumpID) {
        checkOpen();
        boolean ok = P2Pmsgcore_c.p2peerhub_post_con(handle, con.rawHandle(), pumpID) != 0;
        con.detach();
        return ok;
    }

    /**
     * Returns {@code true} if a connection with address {@code peerAddr}
     * is currently registered with this hub.
     */
    public boolean connectionExists(String peerAddr) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return P2Pmsgcore_c.p2peerhub_con_exists(handle, NativeStrings.toWStr(peerAddr, tmp)) != 0;
        }
    }

    // ── Message routing ───────────────────────────────────────────────────────

    /**
     * Posts {@code msg} into the hub's routing pipeline.
     * The framework takes ownership of the message; call {@link P2PMsg#detach()}
     * after this and do not close it.
     *
     * @return the posted message handle, or {@code null}
     */
    public P2PMsg postMessage(P2PMsg msg) {
        checkOpen();
        MemorySegment result = P2Pmsgcore_c.p2peerhub_post_msg(handle, msg.rawHandle());
        msg.detach();
        return result != null && !result.equals(MemorySegment.NULL) ? new P2PMsg(result) : null;
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /** Returns the numeric hub ID assigned by the framework. */
    public long hubId() {
        checkOpen();
        return Integer.toUnsignedLong(P2Pmsgcore_c.p2peerhub_get_hub_id(handle));
    }

    /** Returns the P2P address string of this hub. */
    public String address() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2peerhub_get_address(handle));
    }

    @Override
    public String toString() {
        return closed ? "<closed>" : "P2PeerHub[" + address() + " id=" + hubId() + "]";
    }

    @Override
    public void close() {
        if (!closed) {
            P2Pmsgcore_c.p2peerhub_destroy(handle);
            closed = true;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("P2PeerHub already closed");
    }
}
