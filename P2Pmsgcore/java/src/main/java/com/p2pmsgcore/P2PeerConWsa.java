package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;
import java.lang.foreign.*;

/**
 * Java wrapper for the C++ P2PeerConWsa class.
 * Manages a single asynchronous TCP/IP P2Peer connection backed by
 * Windows Sockets (WSA) and IO Completion Ports.
 *
 * <p>Create via the static factories then hand the connection to a
 * {@link P2PeerHub} with {@link P2PeerHub#postConnection}:
 *
 * <pre>{@code
 * P2PeerConWsa client = P2PeerConWsa.clientFactory("Server", "192.168.1.10", 9000);
 * hub.postConnection(client, 0);   // hub takes ownership; do NOT close client
 * }</pre>
 */
public final class P2PeerConWsa implements AutoCloseable {

    // ── Connection mode constants (mirrors P2PeerConMode_e) ───────────────────
    public static final int MODE_UNKNOWN = 0;
    public static final int MODE_CLIENT  = 1;
    public static final int MODE_SERVICE = 2;
    public static final int MODE_ACCEPT  = 3;

    // ── State bit-masks (mirrors ConState_* constants) ────────────────────────
    public static final int STATE_BCASTS       = 1 << 0;
    public static final int STATE_UCASTS       = 1 << 1;
    public static final int STATE_RECV         = 1 << 2;
    public static final int STATE_SEND         = 1 << 3;
    public static final int STATE_PKEY         = 1 << 4;
    public static final int STATE_LOGIN        = 1 << 5;
    public static final int STATE_CLOSE_ON_IDLE= 1 << 6;

    private MemorySegment handle;
    private boolean closed = false;

    private P2PeerConWsa(MemorySegment handle) {
        this.handle = handle;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Creates an outbound (client) TCP connection to {@code ipAddress:port}.
     *
     * @param peerAddr  P2P address of the remote peer
     * @param ipAddress IPv4/IPv6 address string
     * @param port      TCP port number
     */
    public static P2PeerConWsa clientFactory(String peerAddr, String ipAddress, int port) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment h = P2Pmsgcore_c.p2peerconwsa_client_factory(
                    NativeStrings.toWStr(peerAddr,   tmp),
                    NativeStrings.toWStr(ipAddress,  tmp),
                    (short) port);
            if (h == null || h.equals(MemorySegment.NULL))
                throw new RuntimeException("p2peerconwsa_client_factory returned null");
            return new P2PeerConWsa(h);
        }
    }

    /**
     * Creates a passive (service / listen) connection on {@code port}.
     *
     * @param peerAddr P2P address label for incoming connections
     * @param port     TCP port to listen on
     */
    public static P2PeerConWsa serviceFactory(String peerAddr, int port) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment h = P2Pmsgcore_c.p2peerconwsa_service_factory(
                    NativeStrings.toWStr(peerAddr, tmp),
                    (short) port);
            if (h == null || h.equals(MemorySegment.NULL))
                throw new RuntimeException("p2peerconwsa_service_factory returned null");
            return new P2PeerConWsa(h);
        }
    }

    // ── Operations ────────────────────────────────────────────────────────────

    /**
     * Initiates an asynchronous TCP connect.
     * Result arrives in the hub's {@code On_ConConnect} handler.
     */
    public boolean connect() {
        checkOpen();
        return P2Pmsgcore_c.p2peerconwsa_connect(handle) != 0;
    }

    /**
     * Starts listening for inbound connections.
     * Accepted connections arrive in the hub's {@code On_ConAccept} handler.
     */
    public boolean listen() {
        checkOpen();
        return P2Pmsgcore_c.p2peerconwsa_listen(handle) != 0;
    }

    /** Closes the connection gracefully. */
    public void closeConnection() {
        checkOpen();
        P2Pmsgcore_c.p2peerconwsa_close(handle);
    }

    // ── State queries ─────────────────────────────────────────────────────────

    /**
     * Returns the state bits that are currently set, masked by {@code mask}.
     * Use the {@code STATE_*} constants as the mask.
     */
    public int getState(int mask) {
        checkOpen();
        return P2Pmsgcore_c.p2peerconwsa_get_state(handle, mask);
    }

    /** Returns {@code true} if all bits in {@code mask} are set. */
    public boolean hasState(int mask) {
        checkOpen();
        return P2Pmsgcore_c.p2peerconwsa_has_state(handle, mask) != 0;
    }

    /**
     * Returns the connection mode: {@link #MODE_CLIENT}, {@link #MODE_SERVICE},
     * {@link #MODE_ACCEPT}, or {@link #MODE_UNKNOWN}.
     */
    public int mode() {
        checkOpen();
        return P2Pmsgcore_c.p2peerconwsa_get_mode(handle);
    }

    /** Returns the P2P address string associated with this connection. */
    public String address() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2peerconwsa_get_address(handle));
    }

    // ── Message posting ───────────────────────────────────────────────────────

    /**
     * Posts {@code msg} to this connection's send queue.
     * The framework takes ownership of the message; call {@link P2PMsg#detach()}
     * on {@code msg} after this call and do not close it.
     *
     * @return the same message handle (or {@code null} on failure)
     */
    public P2PMsg postMessage(P2PMsg msg) {
        checkOpen();
        MemorySegment result = P2Pmsgcore_c.p2peerconwsa_post_msg(handle, msg.rawHandle());
        msg.detach();
        return result != null && !result.equals(MemorySegment.NULL) ? new P2PMsg(result) : null;
    }

    // ── Handle management ─────────────────────────────────────────────────────

    MemorySegment rawHandle() {
        checkOpen();
        return handle;
    }

    /**
     * Detaches this wrapper from its native object.
     * Call this after handing the connection to a hub via
     * {@link P2PeerHub#postConnection} so that the hub owns the lifetime.
     */
    public void detach() {
        closed = true;
    }

    @Override
    public void close() {
        if (!closed) {
            P2Pmsgcore_c.p2peerconwsa_destroy(handle);
            closed = true;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("P2PeerConWsa already closed or detached");
    }
}
