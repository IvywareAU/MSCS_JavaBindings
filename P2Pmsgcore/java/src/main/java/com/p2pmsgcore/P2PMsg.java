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
package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;
import java.lang.foreign.*;
import java.nio.charset.StandardCharsets;

/**
 * Java wrapper for the C++ P2PeerMsg class.
 * Carries a named message with a source address, destination address,
 * optional raw-byte payload, and routing priority.
 *
 * <p>Usage:
 * <pre>{@code
 * try (P2PMsg msg = new P2PMsg("Hub1", "Hub2", "MyApp.Hello", new byte[]{1,2,3})) {
 *     hub.postMessage(msg);     // hub takes ownership; do not close afterwards
 * }
 * }</pre>
 *
 * <p><b>Ownership note:</b> when a message is handed to
 * {@link P2PeerHub#postMessage} or {@link P2PeerConWsa#postMessage} the
 * underlying framework takes ownership.  Call {@link #detach()} before
 * {@link #close()} to prevent a double-free; or simply do not call
 * {@code close()} on a posted message.
 */
public final class P2PMsg implements AutoCloseable {

    private MemorySegment handle;
    private boolean closed = false;

    /** Creates an empty message with no address or payload. */
    public P2PMsg() {
        this.handle = P2Pmsgcore_c.p2peermsg_create();
    }

    /** Creates a message with a message-ID name only (no addresses or data). */
    public P2PMsg(String msgID) {
        try (Arena tmp = Arena.ofConfined()) {
            this.handle = P2Pmsgcore_c.p2peermsg_create_msgid(NativeStrings.toWStr(msgID, tmp));
        }
    }

    /**
     * Creates a fully addressed message with a byte-array payload.
     *
     * @param src      source P2P address string
     * @param dst      destination P2P address string
     * @param msgID    message name / ID
     * @param data     raw payload (may be {@code null} for zero-length)
     */
    public P2PMsg(String src, String dst, String msgID, byte[] data) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment dataSeg = data != null && data.length > 0
                    ? tmp.allocateFrom(ValueLayout.JAVA_BYTE, data)
                    : MemorySegment.NULL;
            //  uint32_t since 2026-08-14. This used to clamp to Short.MAX_VALUE,
            //  which is the very truncation the widening removed from the ABI --
            //  reintroduced one layer up, in Java, where the C side could no longer
            //  see it. Pass the length the caller actually passed: the cap is still
            //  MAX_P2Psize (32768) and an over-cap message is REFUSED downstream,
            //  which surfaces here as a null handle rather than a short payload.
            int size = data != null ? data.length : 0;
            this.handle = P2Pmsgcore_c.p2peermsg_create_full(
                    NativeStrings.toWStr(src,   tmp),
                    NativeStrings.toWStr(dst,   tmp),
                    NativeStrings.toWStr(msgID, tmp),
                    dataSeg, size);
        }
    }

    /** Wraps a raw native handle – internal use by other wrapper classes. */
    P2PMsg(MemorySegment handle) {
        this.handle = handle;
    }

    MemorySegment rawHandle() {
        checkOpen();
        return handle;
    }

    /**
     * Detaches this wrapper from the native object so that {@link #close()}
     * will not destroy it.  Call this after handing the message to the
     * framework (hub / connection post).
     */
    public void detach() {
        closed = true;  // suppress destruction; framework now owns the object
    }

    // ── Properties ────────────────────────────────────────────────────────────

    /** Returns the message name / ID string. */
    public String name() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2peermsg_c_name(handle));
    }

    /** Returns the source P2P address string. */
    public String source() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2peermsg_get_source(handle));
    }

    /** Sets the source P2P address. */
    public void setSource(String src) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            P2Pmsgcore_c.p2peermsg_set_source(handle, NativeStrings.toWStr(src, tmp));
        }
    }

    /** Returns the destination P2P address string. */
    public String destination() {
        checkOpen();
        return NativeStrings.fromWStr(P2Pmsgcore_c.p2peermsg_get_destin(handle));
    }

    /** Sets the destination P2P address. */
    public void setDestination(String dst) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            P2Pmsgcore_c.p2peermsg_set_destin(handle, NativeStrings.toWStr(dst, tmp));
        }
    }

    /**
     * Returns a copy of the raw payload bytes, or an empty array if there
     * is no payload.
     */
    public byte[] data() {
        checkOpen();
        long size = P2Pmsgcore_c.p2peermsg_data_size(handle);
        if (size <= 0) return new byte[0];
        MemorySegment ptr = P2Pmsgcore_c.p2peermsg_data(handle);
        return ptr.reinterpret(size).toArray(ValueLayout.JAVA_BYTE);
    }

    /** Returns the payload size in bytes. */
    public long dataSize() {
        checkOpen();
        return P2Pmsgcore_c.p2peermsg_data_size(handle);
    }

    /** Returns the message routing priority (0 = flush, 7 = normal). */
    public int priority() {
        checkOpen();
        return Byte.toUnsignedInt(P2Pmsgcore_c.p2peermsg_priority(handle));
    }

    /** Sets the message routing priority. Returns the previous value. */
    public int setPriority(int priority) {
        checkOpen();
        return Byte.toUnsignedInt(P2Pmsgcore_c.p2peermsg_set_priority(handle, (byte) priority));
    }

    /** Returns {@code true} if this message is wrapped inside another. */
    public boolean isWrapped() {
        checkOpen();
        return P2Pmsgcore_c.p2peermsg_is_wrapped(handle) != 0;
    }

    /** Returns {@code true} if this message has been reflected. */
    public boolean isReflected() {
        checkOpen();
        return P2Pmsgcore_c.p2peermsg_is_reflected(handle) != 0;
    }

    // ── Factories ─────────────────────────────────────────────────────────────

    /**
     * Creates a response message: source and destination are swapped from
     * this message, the new name and data are applied.
     * The returned message is owned by the caller and must be closed.
     */
    public P2PMsg responseFactory(String msgID, byte[] data) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment dataSeg = data != null && data.length > 0
                    ? tmp.allocateFrom(ValueLayout.JAVA_BYTE, data)
                    : MemorySegment.NULL;
            //  uint32_t since 2026-08-14. This used to clamp to Short.MAX_VALUE,
            //  which is the very truncation the widening removed from the ABI --
            //  reintroduced one layer up, in Java, where the C side could no longer
            //  see it. Pass the length the caller actually passed: the cap is still
            //  MAX_P2Psize (32768) and an over-cap message is REFUSED downstream,
            //  which surfaces here as a null handle rather than a short payload.
            int size = data != null ? data.length : 0;
            MemorySegment h = P2Pmsgcore_c.p2peermsg_response_factory(
                    handle, NativeStrings.toWStr(msgID, tmp), dataSeg, size);
            return new P2PMsg(h);
        }
    }

    /**
     * Creates a copy of this message re-addressed to {@code dstAddr}.
     * The returned message is owned by the caller and must be closed.
     */
    public P2PMsg redirectFactory(String dstAddr) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment h = P2Pmsgcore_c.p2peermsg_redirect_factory(
                    handle, NativeStrings.toWStr(dstAddr, tmp));
            return new P2PMsg(h);
        }
    }

    // ── UTF-8 (portable) surface ────────────────────────────────────────────────
    // These use the _u8 C entry points, which carry strings as UTF-8 rather than
    // the platform wchar_t. Prefer them for encoding-portable behaviour (identical
    // on a Windows DLL and the Linux libp2pmsgcore.so). The byte payload is opaque
    // binary and unaffected by encoding, so data()/dataSize() are shared.

    /** Creates a message with a UTF-8 message-ID name only, via the _u8 C API. */
    public static P2PMsg ofMsgIdUtf8(String msgID) {
        try (Arena tmp = Arena.ofConfined()) {
            return new P2PMsg(P2Pmsgcore_c.p2peermsg_create_msgid_u8(NativeStrings.toU8(msgID, tmp)));
        }
    }

    /** Creates a fully addressed message from UTF-8 strings, via the _u8 C API. */
    public static P2PMsg ofUtf8(String src, String dst, String msgID, byte[] data) {
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment dataSeg = data != null && data.length > 0
                    ? tmp.allocateFrom(ValueLayout.JAVA_BYTE, data)
                    : MemorySegment.NULL;
            //  uint32_t since 2026-08-14. This used to clamp to Short.MAX_VALUE,
            //  which is the very truncation the widening removed from the ABI --
            //  reintroduced one layer up, in Java, where the C side could no longer
            //  see it. Pass the length the caller actually passed: the cap is still
            //  MAX_P2Psize (32768) and an over-cap message is REFUSED downstream,
            //  which surfaces here as a null handle rather than a short payload.
            int size = data != null ? data.length : 0;
            return new P2PMsg(P2Pmsgcore_c.p2peermsg_create_full_u8(
                    NativeStrings.toU8(src,   tmp),
                    NativeStrings.toU8(dst,   tmp),
                    NativeStrings.toU8(msgID, tmp),
                    dataSeg, size));
        }
    }

    /** The serialised byte size of this message. */
    public int byteSize() {
        checkOpen();
        //  uint32_t since 2026-08-14 (was unsigned short).
        return P2Pmsgcore_c.p2peermsg_sizeof(handle);
    }

    /** {@link #responseFactory} over the UTF-8 {@code _u8} C API. */
    public P2PMsg responseFactoryUtf8(String msgID, byte[] data) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            MemorySegment dataSeg = data != null && data.length > 0
                    ? tmp.allocateFrom(ValueLayout.JAVA_BYTE, data)
                    : MemorySegment.NULL;
            int size = data != null ? data.length : 0;
            return new P2PMsg(P2Pmsgcore_c.p2peermsg_response_factory_u8(
                    handle, NativeStrings.toU8(msgID, tmp), dataSeg, size));
        }
    }

    /** {@link #redirectFactory} over the UTF-8 {@code _u8} C API. */
    public P2PMsg redirectFactoryUtf8(String dstAddr) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            return new P2PMsg(P2Pmsgcore_c.p2peermsg_redirect_factory_u8(
                    handle, NativeStrings.toU8(dstAddr, tmp)));
        }
    }

    /** Returns the message name / ID as a UTF-8-decoded String. */
    public String nameUtf8() {
        checkOpen();
        return NativeStrings.fromU8(P2Pmsgcore_c.p2peermsg_c_name_u8(handle));
    }

    /** Returns the source address as a UTF-8-decoded String. */
    public String sourceUtf8() {
        checkOpen();
        return NativeStrings.fromU8(P2Pmsgcore_c.p2peermsg_get_source_u8(handle));
    }

    /** Sets the source address from a UTF-8 String. */
    public void setSourceUtf8(String src) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            P2Pmsgcore_c.p2peermsg_set_source_u8(handle, NativeStrings.toU8(src, tmp));
        }
    }

    /** Returns the destination address as a UTF-8-decoded String. */
    public String destinationUtf8() {
        checkOpen();
        return NativeStrings.fromU8(P2Pmsgcore_c.p2peermsg_get_destin_u8(handle));
    }

    /** Sets the destination address from a UTF-8 String. */
    public void setDestinationUtf8(String dst) {
        checkOpen();
        try (Arena tmp = Arena.ofConfined()) {
            P2Pmsgcore_c.p2peermsg_set_destin_u8(handle, NativeStrings.toU8(dst, tmp));
        }
    }

    @Override
    public String toString() {
        return closed ? "<closed>" : "P2PMsg[" + name() + " " + source() + " -> " + destination() + "]";
    }

    @Override
    public void close() {
        if (!closed) {
            P2Pmsgcore_c.p2peermsg_destroy(handle);
            closed = true;
        }
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("P2PMsg already closed or detached");
    }
}
