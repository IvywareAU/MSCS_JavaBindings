// Copyright © 2026 Khrustal & Mann
//              MELBOURNE, VICTORIA, AUSTRALIA, 3000
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
// implied. See the License for the specific language governing
// permissions and limitations under the License.
//
package com.targetcore;

import com.targetcore.native_.Targetcore_c;
import com.targetcore.native_.P2PeerHubSinkFnU8;
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

    //  The sink's upcall stub lives in this arena, and the arena must be SHARED
    //  rather than confined: the stub is called on the hub's pump thread, and a
    //  confined arena throws WrongThreadException on access from any other
    //  thread -- inside an upcall, where a throw is fatal rather than catchable.
    private Arena         sinkArena = null;
    private MemorySegment sinkStub  = null;

    /**
     * Creates a hub with the given P2P address.
     * Call {@link #createHub} before {@link #spawnHub}.
     */
    public P2PeerHub(String hubAddr) {
        try (Arena tmp = Arena.ofConfined()) {
            this.handle = Targetcore_c.p2peerhub_create(NativeStrings.toWStr(hubAddr, tmp));
        }
        if (handle == null || handle.equals(MemorySegment.NULL))
            throw new RuntimeException("p2peerhub_create returned null");
    }

    /** Wraps a raw native hub handle - used by {@link #ofUtf8}. */
    private P2PeerHub(MemorySegment handle) {
        this.handle = handle;
        if (handle == null || handle.equals(MemorySegment.NULL))
            throw new RuntimeException("p2peerhub_create_u8 returned null");
    }

    // ── Hub lifecycle ─────────────────────────────────────────────────────────

    /**
     * Initialises the hub's IO completion port and message pumps.
     *
     * <p><b>ONE HUB PER THREAD.</b> {@code CreateP2PmsgHub} refuses to associate a
     * second pump with a thread that already has one — "Single P2PmsgPump per
     * thread context" — so a process that wants several hubs must call this from
     * a different thread for each of them, and closing and destroying the first
     * hub does <i>not</i> release its thread. Measured: three hubs created from
     * the main thread give {@code true, false, false}; the same three created one
     * per thread all succeed.
     *
     * <p>The reason it is worth saying here is that the flat C ABI cannot tell
     * you. The kernel raises a {@code P2Pevent} explaining exactly this, and
     * {@code p2peerhub_create_hub} catches it — deliberately, because a C++
     * exception must not unwind across an {@code extern "C"} boundary into a
     * Panama caller — so what reaches Java is a bare {@code false}.
     *
     * @param hubAddr   P2P address for this hub
     * @param pumpsMax  number of pump threads (1 is typical)
     * @return {@code true} on success; {@code false} if this thread already has a
     *         hub, if the environment was never started, or if the hub would not arm
     */
    public boolean createHub(String hubAddr, int pumpsMax) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return Targetcore_c.p2peerhub_create_hub(
                    handle, NativeStrings.toWStr(hubAddr, tmp), pumpsMax) != 0;
        }
    }

    /**
     * Spawns the hub processing thread, and creates the hub <b>inside it</b>.
     *
     * <p><b>Call this OR {@link #createHub}, never both.</b> {@code SpawnHub}
     * opens with {@code ASSERT(m_nHubID == 0)}, so a hub that was already created
     * on the calling thread trips a Debug assertion and the process stops on a
     * modal dialog; a Release build says nothing at all and carries on. The
     * bindings' own smoke test made exactly this call pair from the day it was
     * written, and only ever ran against Release.
     *
     * <p>{@code spawnHub()} is the normal path for an FFI consumer: it gives the
     * hub a thread of its own, which is also what the one-hub-per-thread rule on
     * {@link #createHub} asks for.
     *
     * @return the Win32 thread HANDLE as a {@code long} (0 on failure)
     */
    public long spawnHub() {
        checkOpen();
        MemorySegment threadHandle = Targetcore_c.p2peerhub_spawn_hub(handle);
        return threadHandle == null ? 0L : threadHandle.address();
    }

    /** Signals all pumps to stop and waits for shutdown. */
    public void closeHub() {
        checkOpen();
        Targetcore_c.p2peerhub_close_hub(handle);
    }

    /** Pauses the hub's processing loop. */
    public void pauseHub() {
        checkOpen();
        Targetcore_c.p2peerhub_pause_hub(handle);
    }

    /** Resumes a paused hub. */
    public void wakeupHub() {
        checkOpen();
        Targetcore_c.p2peerhub_wakeup_hub(handle);
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
        boolean ok = Targetcore_c.p2peerhub_post_con(handle, con.rawHandle(), pumpID) != 0;
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
            return Targetcore_c.p2peerhub_con_exists(handle, NativeStrings.toWStr(peerAddr, tmp)) != 0;
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
        MemorySegment result = Targetcore_c.p2peerhub_post_msg(handle, msg.rawHandle());
        msg.detach();
        return result != null && !result.equals(MemorySegment.NULL) ? new P2PMsg(result) : null;
    }

    // -- Authentication --------------------------------------------------------
    //  Every call here must happen BEFORE createHub / spawnHub, like every other
    //  hub setting. Since 2026-08-18 authentication is REQUIRED BY DEFAULT and a
    //  hub that cannot enforce it does not start at all -- so a Java caller that
    //  does none of this gets `createHub() == false` and an ArmResult saying why,
    //  where before it got a running hub that accepted anyone.

    /**
     * Turns peer authentication off for this hub - the supported migration for a
     * trusted segment or an in-process router, and the only way to get the
     * pre-2026-08-18 behaviour. What is no longer possible is turning it off by
     * saying nothing.
     */
    public void requireAuth(boolean require) {
        checkOpen();
        Targetcore_c.p2peerhub_require_auth(handle, require ? 1 : 0);
    }

    /** Does this hub require its peers to prove who they are? True unless turned off. */
    public boolean isAuthRequired() {
        checkOpen();
        return Targetcore_c.p2peerhub_is_auth_required(handle) != 0;
    }

    /**
     * Loads this hub's own identity key, creating it if {@code createIfAbsent} and
     * it is not there. Paths are UTF-8 on both platforms.
     */
    public IdResult setIdentity(String path, boolean createIfAbsent) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return IdResult.fromCode(Targetcore_c.p2peerhub_set_identity(
                    handle, NativeStrings.toU8(path, tmp), createIfAbsent ? 1 : 0));
        }
    }

    /** Loads the file naming the peers this hub will accept. */
    public IdResult setAllowList(String path) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return IdResult.fromCode(
                    Targetcore_c.p2peerhub_set_allow_list(handle, NativeStrings.toU8(path, tmp)));
        }
    }

    /** Re-reads the configured allow-list on a running hub. */
    public IdResult reloadAllowList() {
        checkOpen();
        return IdResult.fromCode(Targetcore_c.p2peerhub_reload_allow_list(handle));
    }

    /** The allow-list path this hub was given, or {@code null} if it was never given one. */
    public String allowListPath() {
        checkOpen();
        return NativeStrings.fromU8(Targetcore_c.p2peerhub_auth_allow_list_path(handle));
    }

    // ---- Revocation ------------------------------------------------------
    // ProductionPlan.md Stage 3 step 19. Revocation had NO flat C entry point
    // at all before 2026-08-21, which is why all four arrived at once: the
    // arming gate below would otherwise have been unsatisfiable from Java -
    // a caller could neither name a list nor decline one, and the hub would
    // simply not start.

    /**
     * Loads the file naming keys this hub will refuse, whatever the allow-list
     * says. Revocation <b>fails closed</b>: once a list is configured, a load
     * that fails does not fall back to "nothing is revoked" — every
     * verification is refused until a reload succeeds.
     */
    public IdResult setRevocationList(String path) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return IdResult.fromCode(
                    Targetcore_c.p2peerhub_set_revocation_list(handle, NativeStrings.toU8(path, tmp)));
        }
    }

    /** The revocation list path this hub was given, or {@code null}. */
    public String revocationListPath() {
        checkOpen();
        return NativeStrings.fromU8(Targetcore_c.p2peerhub_auth_revocation_list_path(handle));
    }

    /**
     * Must this hub hold a <i>position</i> on revocation before it will arm?
     * On by default since 2026-08-21.
     *
     * <p>"Position" is the word and not "list", because there are two of them
     * and both count: name a list, or call this with {@code false}. What is no
     * longer reachable is arriving at "this hub can never withdraw a key" by
     * saying nothing.
     *
     * <p>Turning it off does <b>not</b> turn revocation off. A list named
     * anyway is still loaded, still enforced and still fails closed; this
     * governs only whether the <i>absence</i> of one is permitted.
     */
    public void requireRevocation(boolean require) {
        checkOpen();
        Targetcore_c.p2peerhub_require_revocation(handle, require ? 1 : 0);
    }

    /** Does this hub demand a revocation position before arming? True unless turned off. */
    public boolean isRevocationRequired() {
        checkOpen();
        return Targetcore_c.p2peerhub_is_revocation_required(handle) != 0;
    }

    // ---- End-to-end sealing ----------------------------------------------
    // ProductionPlan.md Stage 3 step 20.

    /**
     * Loads this hub's static agreement key — the half that lets it
     * <b>open</b> a body sealed to it — creating it if {@code createIfAbsent}
     * and it is not there.
     *
     * <p>A hub that requires sealing and holds none of this still starts: a
     * relay legitimately holds no keys, since it forwards blocks it cannot
     * read. It warns once at arm time and cannot open anything addressed to
     * it.
     */
    public IdResult setAgreementKey(String path, boolean createIfAbsent) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return IdResult.fromCode(Targetcore_c.p2peerhub_set_agreement_key(
                    handle, NativeStrings.toU8(path, tmp), createIfAbsent ? 1 : 0));
        }
    }

    /**
     * Seal a body that will cross an intermediate hub, or do not send it. On
     * by default since 2026-08-21, and it <b>refuses rather than downgrades</b>.
     *
     * <p>A message whose destination is not the peer on the far end of the
     * link is sealed to that destination before it goes. If this hub holds no
     * agreement key for that destination the message is <b>dropped</b> and
     * said so — it does not travel in clear.
     *
     * <p>The break is wide: a tree that has never published agreement keys
     * stops carrying relayed traffic the moment this is on, and
     * {@code requireSeal(false)} is the migration. <b>Sealing and broadcast do
     * not compose today</b> — a broadcast has no single destination and the
     * agreement lookup is exact, so a broadcast can never be sealed and is
     * always refused while this is on.
     */
    public void requireSeal(boolean require) {
        checkOpen();
        Targetcore_c.p2peerhub_require_seal(handle, require ? 1 : 0);
    }

    /** Does this hub seal relayed bodies, refusing to send what it cannot seal? */
    public boolean isSealRequired() {
        checkOpen();
        return Targetcore_c.p2peerhub_is_seal_required(handle) != 0;
    }

    /**
     * Names a hub that may <b>also</b> read what this hub seals — the answer
     * to "an intermediate hub has to see the body".
     *
     * <p>The sender decides, and only the sender. The reader set is bound into
     * the sealed body's additional data and its signature, so no relay can add
     * itself and no policy on a relay can add it. Everything named here can
     * read <i>every</i> body this hub seals, which is a trust decision rather
     * than a routing one.
     *
     * <p>At most seven of them (the destination takes the eighth slot), each
     * resolved through the allow-list at seal time — so revoking a reader's
     * agreement key removes its access without editing this list. A name that
     * cannot be resolved then <b>refuses the send</b> rather than sealing to
     * fewer readers.
     */
    public IdResult addSealReader(String addr) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return IdResult.fromCode(
                    Targetcore_c.p2peerhub_add_seal_reader_u8(handle, NativeStrings.toU8(addr, tmp)));
        }
    }

    /** Forgets every extra reader named by {@link #addSealReader}. */
    public void clearSealReaders() {
        checkOpen();
        Targetcore_c.p2peerhub_clear_seal_readers(handle);
    }

    /** What {@link #provisionAuth} found or did. */
    public record Provisioned(IdResult result, String fingerprint, boolean created) {
        /** True if the identity is now loaded. */
        public boolean ok() { return result.ok(); }
    }

    /**
     * First run: create the identity if it is not there, write the publishable half
     * beside it as {@code <path>.pub}, and return the fingerprint an operator reads
     * aloud.
     *
     * <p>It does <b>not</b> create the allow-list, and the hub still will not arm
     * until one exists with at least one peer in it. Who to trust is not a thing a
     * library can supply, and one that wrote an empty allow-list would be answering
     * that question with "nobody" - which refuses every peer.
     */
    public Provisioned provisionAuth(String path) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            //  40 bytes is what the header asks for; the +1 is belt and braces
            //  against a terminator written at the boundary.
            MemorySegment fp      = tmp.allocate(41);
            MemorySegment created = tmp.allocate(ValueLayout.JAVA_INT);
            int rc = Targetcore_c.p2peerhub_provision_auth(
                    handle, NativeStrings.toU8(path, tmp), fp, 40, created);
            return new Provisioned(IdResult.fromCode(rc),
                                   NativeStrings.fromU8(fp),
                                   created.get(ValueLayout.JAVA_INT, 0) != 0);
        }
    }

    /**
     * Would this hub arm? A pure query, safe at any time, and the thing to call
     * before {@link #createHub} if you want to know why it is about to refuse.
     */
    public ArmResult authArm() {
        checkOpen();
        return ArmResult.fromCode(Targetcore_c.p2peerhub_auth_arm(handle));
    }

    // -- Receive sink ----------------------------------------------------------

    /**
     * A message DELIVERED to this hub. Registered with {@link #setSink}, called on
     * the hub's <b>pump thread</b> - not the registering thread - so it must not
     * block; copy what you keep and return.
     *
     * <p>{@code dst} is the hub's full address, read from the hub rather than off
     * the message. {@code data} is a copy: the native bytes are valid only for the
     * duration of the call.
     */
    @FunctionalInterface
    public interface Sink {
        /**
         * @return {@code true} if this handler consumed the message. Returning
         *         {@code false} hands it back to the framework, which for an FFI
         *         peer with no compiled message map means an undeliverable bounce
         *         per message - and that flood is what wedges {@code closeHub()}.
         */
        boolean onMessage(String src, String dst, String msgID, byte[] data);
    }

    /**
     * Installs {@code sink} as this hub's receive sink, via the UTF-8 entry point -
     * the form the header names for Panama, because the wide one carries
     * {@code wchar_t}, which is 16 bits on Windows and 32 on Linux.
     *
     * <p>Register before {@link #spawnHub} and clear after {@link #closeHub}.
     * Registering and clearing are safe against a running pump; swapping one live
     * sink for another is not, so this refuses to replace an installed sink.
     *
     * @return {@code true} if the sink was registered
     */
    public boolean setSink(Sink sink) {
        checkOpen();
        if (sink == null) return clearSink();
        if (sinkStub != null)
            throw new IllegalStateException(
                    "a sink is already installed; clearSink() first -- swapping one live "
                    + "sink for another is not safe against a running pump");

        Arena arena = Arena.ofShared();
        try {
            MemorySegment stub = P2PeerHubSinkFnU8.allocate(
                (ctx, src, dst, msgID, data, dataSize) -> {
                    //  A Java exception escaping an upcall stub does not unwind into
                    //  C++ -- it takes the whole JVM down. Report and swallow, and
                    //  report CONSUMED: handing a message back after the handler has
                    //  already failed only buys an undeliverable bounce.
                    try {
                        byte[] bytes = (dataSize > 0 && !data.equals(MemorySegment.NULL))
                                ? data.reinterpret(dataSize).toArray(ValueLayout.JAVA_BYTE)
                                : new byte[0];
                        return sink.onMessage(NativeStrings.fromU8(src),
                                              NativeStrings.fromU8(dst),
                                              NativeStrings.fromU8(msgID),
                                              bytes) ? 1 : 0;
                    } catch (Throwable t) {
                        System.err.println("P2PeerHub sink threw, message consumed: " + t);
                        t.printStackTrace();
                        return 1;
                    }
                }, arena);

            boolean ok = Targetcore_c.p2peerhub_set_sink_u8(handle, stub, MemorySegment.NULL) != 0;
            if (!ok) { arena.close(); return false; }
            this.sinkArena = arena;
            this.sinkStub  = stub;
            return true;
        } catch (RuntimeException | Error e) {
            arena.close();
            throw e;
        }
    }

    /**
     * Removes the installed sink. Safe against a running pump, but the arena
     * holding the stub is only released once the library has been told to stop
     * calling it.
     */
    public boolean clearSink() {
        checkOpen();
        if (sinkStub == null) return true;
        boolean ok = Targetcore_c.p2peerhub_set_sink_u8(
                handle, MemorySegment.NULL, MemorySegment.NULL) != 0;
        sinkArena.close();
        sinkArena = null;
        sinkStub  = null;
        return ok;
    }

    // -- UTF-8 (portable) surface ----------------------------------------------
    //  The wchar_t entry points above use each platform's native wide layout and
    //  cannot carry a string portably; these twins take and return UTF-8 and are
    //  ABI-identical on Windows and Linux.

    /** Creates a hub from a UTF-8 address, via the {@code _u8} C API. */
    public static P2PeerHub ofUtf8(String hubAddr) {
        try (Arena tmp = Arena.ofConfined()) {
            return new P2PeerHub(Targetcore_c.p2peerhub_create_u8(NativeStrings.toU8(hubAddr, tmp)));
        }
    }

    /** {@link #createHub} with a UTF-8 address. */
    public boolean createHubUtf8(String hubAddr, int pumpsMax) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return Targetcore_c.p2peerhub_create_hub_u8(
                    handle, NativeStrings.toU8(hubAddr, tmp), pumpsMax) != 0;
        }
    }

    /** {@link #connectionExists} with a UTF-8 address. */
    public boolean connectionExistsUtf8(String peerAddr) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return Targetcore_c.p2peerhub_con_exists_u8(handle, NativeStrings.toU8(peerAddr, tmp)) != 0;
        }
    }

    /** This hub's address, decoded from UTF-8. */
    public String addressUtf8() {
        checkOpen();
        return NativeStrings.fromU8(Targetcore_c.p2peerhub_get_address_u8(handle));
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /** Returns the numeric hub ID assigned by the framework. */
    public long hubId() {
        checkOpen();
        return Integer.toUnsignedLong(Targetcore_c.p2peerhub_get_hub_id(handle));
    }

    /** Returns the P2P address string of this hub. */
    public String address() {
        checkOpen();
        return NativeStrings.fromWStr(Targetcore_c.p2peerhub_get_address(handle));
    }

    @Override
    public String toString() {
        return closed ? "<closed>" : "P2PeerHub[" + address() + " id=" + hubId() + "]";
    }

    @Override
    public void close() {
        if (!closed) {
            //  Tell the library to stop calling the stub BEFORE the arena that
            //  holds it goes away, and before the hub itself is destroyed.
            if (sinkStub != null) clearSink();
            Targetcore_c.p2peerhub_destroy(handle);
            closed = true;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("P2PeerHub already closed");
    }
}
