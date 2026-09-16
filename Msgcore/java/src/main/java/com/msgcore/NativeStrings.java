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
package com.msgcore;

import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * {@code String} to and from the two native string forms this ABI uses.
 *
 * <p><b>Why this is its own class.</b> These four methods used to live <i>inside</i>
 * {@code native_/Msgcore_c.java}, the file jextract replaces wholesale. Regenerating
 * the bindings therefore deleted them and broke the build — which is a large part of
 * why the bindings were never regenerated, and why they sat at 108 of the library's
 * 282 entry points until 2026-08-20. Hand-written code does not go in generated
 * files; it goes here, where a regeneration cannot reach it. The Targetcore tree has
 * always had it this way round.
 */
final class NativeStrings {

    private NativeStrings() {}

    // -- wchar_t (UTF-16LE on Windows) -----------------------------------------

    /** A null-terminated wide string in {@code arena}. A null {@code s} stays NULL. */
    static MemorySegment toWStr(Arena arena, String s) {
        if (s == null) return MemorySegment.NULL;
        byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
        MemorySegment seg = arena.allocate(utf16.length + 2, 2);
        MemorySegment.copy(utf16, 0, seg, ValueLayout.JAVA_BYTE, 0, utf16.length);
        seg.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), utf16.length, (short) 0);
        return seg;
    }

    /** Reads a null-terminated wide string from a native pointer. */
    //  JAVA_SHORT_UNALIGNED, not JAVA_SHORT. A wide string handed back by the
    //  library can start at an ODD byte address -- Msgcore's live getters return
    //  pointers into a packed VBHeap image, where nothing guarantees 2-byte
    //  alignment -- and the aligned layout throws
    //  "Target offset 0 is incompatible with alignment constraint 2" rather than
    //  reading it. Measured 2026-08-20 on msgcore_field_get_wstr.
    static String fromWStr(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) return null;
        MemorySegment unbounded = ptr.reinterpret(Long.MAX_VALUE);
        long byteLen = 0;
        while (unbounded.get(ValueLayout.JAVA_SHORT_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN), byteLen) != 0)
            byteLen += 2;
        if (byteLen == 0) return "";
        byte[] bytes = unbounded.asSlice(0, byteLen).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    // -- UTF-8 (the portable _u8 surface) --------------------------------------
    // wchar_t is 16 bits on Windows and 32 on Linux, so the wide entry points
    // above cannot carry a string portably. Every string-bearing function has a
    // _u8 twin that takes and returns UTF-8 and is ABI-identical on both.

    /** A null-terminated UTF-8 string in {@code arena}. A null {@code s} stays NULL. */
    static MemorySegment toU8(Arena arena, String s) {
        if (s == null) return MemorySegment.NULL;
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(utf8.length + 1);
        MemorySegment.copy(utf8, 0, seg, ValueLayout.JAVA_BYTE, 0, utf8.length);
        seg.set(ValueLayout.JAVA_BYTE, utf8.length, (byte) 0);
        return seg;
    }

    /** Reads a null-terminated UTF-8 string from a native pointer. */
    static String fromU8(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) return null;
        MemorySegment unbounded = ptr.reinterpret(Long.MAX_VALUE);
        long len = 0;
        while (unbounded.get(ValueLayout.JAVA_BYTE, len) != 0) len++;
        if (len == 0) return "";
        byte[] bytes = unbounded.asSlice(0, len).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
