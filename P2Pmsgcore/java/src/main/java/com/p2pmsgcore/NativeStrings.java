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

import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

// Utility: convert Java String ↔ wchar_t* (UTF-16LE, Windows UNICODE build).
final class NativeStrings {

    private NativeStrings() {}

    // Allocates a null-terminated UTF-16LE wide string in the given arena.
    static MemorySegment toWStr(String s, Arena arena) {
        if (s == null) s = "";
        byte[] utf16 = s.getBytes(StandardCharsets.UTF_16LE);
        // +2 bytes for the null wide-char terminator
        MemorySegment seg = arena.allocate(utf16.length + 2, 2);
        MemorySegment.copy(utf16, 0, seg, ValueLayout.JAVA_BYTE, 0, utf16.length);
        seg.set(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), utf16.length, (short) 0);
        return seg;
    }

    // Reads a null-terminated UTF-16LE wide string from a native pointer.
    static String fromWStr(MemorySegment ptr) {
        if (ptr == null || ptr.equals(MemorySegment.NULL)) return null;
        // Reinterpret with unbounded size so we can scan for the null terminator.
        MemorySegment unbounded = ptr.reinterpret(Long.MAX_VALUE);
        long byteLen = 0;
        while (unbounded.get(ValueLayout.JAVA_SHORT.withOrder(ByteOrder.LITTLE_ENDIAN), byteLen) != 0) {
            byteLen += 2;
        }
        if (byteLen == 0) return "";
        byte[] bytes = unbounded.asSlice(0, byteLen).toArray(ValueLayout.JAVA_BYTE);
        return new String(bytes, StandardCharsets.UTF_16LE);
    }

    // ── UTF-8 (portable) variants for the _u8 C API ─────────────────────────────
    // The _u8 entry points take/return UTF-8 char*, so a Java String round-trips
    // regardless of the native wchar_t width (UTF-16 on Windows, UTF-32 on Linux).
    // Prefer these when loading the Linux libp2pmsgcore.so, or for any code that
    // wants encoding-portable behaviour.

    // Allocates a null-terminated UTF-8 string in the given arena.
    static MemorySegment toU8(String s, Arena arena) {
        if (s == null) s = "";
        byte[] utf8 = s.getBytes(StandardCharsets.UTF_8);
        MemorySegment seg = arena.allocate(utf8.length + 1);   // +1 for the NUL
        MemorySegment.copy(utf8, 0, seg, ValueLayout.JAVA_BYTE, 0, utf8.length);
        seg.set(ValueLayout.JAVA_BYTE, utf8.length, (byte) 0);
        return seg;
    }

    // Reads a null-terminated UTF-8 string from a native pointer.
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
