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

/**
 * UTF-8 (_u8) smoke test — the Java counterpart of the native p2p_u8_smoke ctest.
 * Exercises the portable _u8 surface (P2PAddr.ofUtf8 / nameUtf8, P2PMsg.ofUtf8 /
 * sourceUtf8 / destinationUtf8 / nameUtf8) with multibyte UTF-8 — 2-byte é,
 * 3-byte €, 4-byte astral 🚀 — proving a Java String round-trips through the
 * native C API regardless of the platform wchar_t width.
 *
 * Run after the native DLL/so is on java.library.path:
 *   java --enable-native-access=ALL-UNNAMED
 *        -Djava.library.path=path\to\Targetcore.dll
 *        -cp target\classes
 *        com.targetcore.SmokeTestU8
 */
public class SmokeTestU8 {

    private static int fails = 0;
    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    public static void main(String[] args) {
        final String leaf = "Café";                 // café (2-byte é)
        final String addr = "Mesh." + leaf;
        final String src  = "cli.noé";
        final String dst  = "srv.€";                // € (3-byte)
        final String msgID = "Ping🚀";         // 🚀 (astral, surrogate pair)

        // ── P2PAddr via _u8 ───────────────────────────────────────────────────
        try (P2PAddr a = P2PAddr.ofUtf8(addr)) {
            check(leaf.equals(a.nameUtf8()), "P2PAddr.nameUtf8 leaf round-trip (é): " + a.nameUtf8());
        }

        // ── P2PMsg via _u8 ────────────────────────────────────────────────────
        try (P2PMsg m = P2PMsg.ofUtf8(src, dst, msgID, null)) {
            check(src.equals(m.sourceUtf8()),        "P2PMsg.sourceUtf8 round-trip (é)");
            check(dst.equals(m.destinationUtf8()),   "P2PMsg.destinationUtf8 round-trip (€)");
            check(msgID.equals(m.nameUtf8()),        "P2PMsg.nameUtf8 round-trip (astral 🚀)");
            m.setSourceUtf8(dst);
            check(dst.equals(m.sourceUtf8()),        "P2PMsg.setSourceUtf8 round-trip (€)");
        }

        System.out.println(fails == 0 ? "\nSmokeTestU8 passed." : "\nSmokeTestU8 FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }
}
