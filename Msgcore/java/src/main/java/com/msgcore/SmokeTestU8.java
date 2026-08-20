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

/**
 * The UTF-8 ({@code _u8}) surface — the Java counterpart of the native
 * {@code msgcore_u8_smoke} CTest.
 *
 * <p>{@code wchar_t} is 16 bits on Windows and 32 on Linux, so the wide entry
 * points cannot carry a string portably through an FFI; the {@code _u8} twins take
 * and return UTF-8 and are ABI-identical on both. There are <b>65</b> of them and
 * the bindings had <b>none</b> until 2026-08-20 — the hand-written stub that stood
 * in for jextract output covered no part of the portable surface at all.
 *
 * <p>Exercised with multibyte UTF-8 — 2-byte é, 3-byte €, 4-byte astral 🚀 — in
 * both the <i>name</i> and the <i>value</i>, because they cross the boundary by
 * different routes, and across a save/reload so the encoding is proven through the
 * on-disk image rather than only in memory.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL.
 */
public class SmokeTestU8 {

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    public static void main(String[] args) throws Exception {

        final String nameAcute = "Café";        // 2-byte é
        final String nameEuro  = "Prix€";       // 3-byte €
        final String nameRocket = "Ping🚀";     // astral, a surrogate pair in Java
        final String valueMixed = "é € 🚀 done";

        java.nio.file.Path dir  = java.nio.file.Files.createTempDirectory("msgcore-u8-");
        java.nio.file.Path file = dir.resolve("u8.p2p");
        try {
            try (MsgMgr mgr = new MsgMgr(MsgAddrMode.ADDR_32, 4096, 1024 * 1024)) {
                try (MsgField root = mgr.root()) {
                    root.declareIntUtf8(nameAcute, 42, false).close();
                    root.declareDoubleUtf8(nameEuro, 3.5, false).close();
                    root.declareStringUtf8(nameRocket, valueMixed, false).close();
                }

                try (MsgField root = mgr.root()) {
                    try (MsgField f = root.childUtf8(nameAcute)) {
                        check(f != null && f.getInt() == 42, "name with é round-trips");
                        check(f != null && nameAcute.equals(f.getNameUtf8()),
                                "getNameUtf8 gives it back: " + (f == null ? "-" : f.getNameUtf8()));
                    }
                    try (MsgField f = root.childUtf8(nameEuro)) {
                        check(f != null && Math.abs(f.getDouble() - 3.5) < 1e-9, "name with € round-trips");
                    }
                    try (MsgField f = root.childUtf8(nameRocket)) {
                        check(f != null, "name with an astral 🚀 round-trips");
                        check(f != null && valueMixed.equals(f.getStringUtf8()),
                                "and its VALUE round-trips byte-identically");
                    }
                    check(root.existsUtf8(nameAcute), "existsUtf8");
                    check(!root.existsUtf8("Nope€"), "existsUtf8 says no when it means no");
                }

                check(mgr.save(file.toString()), "save()");
            }

            //  Through the on-disk image, not just memory.
            try (MsgMgr mgr = new MsgMgr()) {
                check(mgr.load(file.toString()), "load()");
                try (MsgField root = mgr.root();
                     MsgField f = root.childUtf8(nameRocket)) {
                    check(f != null && valueMixed.equals(f.getStringUtf8()),
                            "the astral value survived the file");
                }
                //  The wide and UTF-8 halves must agree about the same node.
                try (MsgField root = mgr.root();
                     MsgField f = root.child(nameAcute)) {
                    check(f != null && nameAcute.equals(f.getName()),
                            "the wchar_t getter agrees with the _u8 one");
                }
            }
        } finally {
            try (var w = java.nio.file.Files.walk(dir)) {
                w.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                    try { java.nio.file.Files.deleteIfExists(x); } catch (Exception ignored) { }
                });
            }
        }

        System.out.println(fails == 0 ? "\nSmokeTestU8 passed." : "\nSmokeTestU8 FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }
}
