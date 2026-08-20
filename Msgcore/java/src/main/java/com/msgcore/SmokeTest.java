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

import java.nio.file.*;

/**
 * The end-to-end smoke test: declare, iterate, save, reload, read back.
 *
 * <p>The proposition that matters is the one nothing checked before 2026-08-20:
 * <b>a write made from Java reaches the tree.</b> Every convenience method on
 * {@link MsgMgr} used to route through {@code asField()}, which the C header
 * describes as "a DETACHED deep copy... mutations never reach the tree" — so every
 * declare from Java was discarded, and {@code MsgcoreExample} crashed on the first
 * read-back after a reload. They go through {@link MsgMgr#root()} now.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL.
 */
public class SmokeTest {

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    public static void main(String[] args) throws Exception {

        Path dir  = Files.createTempDirectory("msgcore-smoke-");
        Path file = dir.resolve("smoke.p2p");
        try {
            // -- declare through the LIVE root ---------------------------------
            long agePos;
            try (MsgMgr mgr = new MsgMgr(MsgAddrMode.ADDR_32, 4096, 1024 * 1024)) {
                check(mgr.isValid(), "manager created");

                try (MsgField root = mgr.root()) {
                    root.declareInt("Age", 42, false).close();
                    root.declareString("Name", "Alice", false).close();
                    root.declareDouble("Score", 98.6, false).close();
                }

                //  Read them back on a freshly resolved live root: a handle taken
                //  before those declares could have been invalidated by a heap
                //  relocation, so this re-resolves rather than reusing one.
                try (MsgField root = mgr.root()) {
                    try (MsgField age = root.child("Age")) {
                        check(age != null && age.getInt() == 42, "Age reads back as 42");
                        agePos = age.p2pos();
                        check(agePos != 0, "Age has a positional handle: " + agePos);
                    }
                    try (MsgField name = root.child("Name")) {
                        check(name != null && "Alice".equals(name.getString()), "Name reads back as Alice");
                    }
                    check(root.child("Score") != null, "Score is there too");
                    check(root.child("Nope") == null,  "an absent child is null, not an exception");
                }

                //  The positional handle resolves back to the same node.
                try (MsgField again = mgr.fromP2pos(agePos)) {
                    check(again != null && again.getInt() == 42, "p2pos resolves back to Age");
                }
                String path = mgr.pathOf(agePos);
                check(path != null && path.contains("Age"), "p2pos2path names it: " + path);

                check(mgr.save(file.toString()), "save()");
            }

            // -- reload in a fresh manager -------------------------------------
            try (MsgMgr mgr = new MsgMgr()) {
                check(mgr.load(file.toString()), "load()");
                try (MsgField root = mgr.root()) {
                    try (MsgField age = root.child("Age")) {
                        check(age != null && age.getInt() == 42, "Age SURVIVED the round trip");
                    }
                    try (MsgField name = root.child("Name")) {
                        check(name != null && "Alice".equals(name.getString()), "Name survived");
                    }
                    try (MsgField score = root.child("Score")) {
                        check(score != null && Math.abs(score.getDouble() - 98.6) < 1e-9,
                                "Score survived");
                    }
                }
            }

            // -- and the detached copy is still detached, deliberately ----------
            try (MsgMgr mgr = new MsgMgr(MsgAddrMode.ADDR_32, 4096, 1024 * 1024)) {
                try (MsgField detached = mgr.asField()) {
                    detached.declareInt("Ghost", 7, false).close();
                }
                try (MsgField root = mgr.root()) {
                    check(root.child("Ghost") == null,
                            "a declare through asField() does NOT reach the tree - by design, "
                            + "and the reason every convenience method now uses root()");
                }
            }

        } finally {
            try (var w = Files.walk(dir)) {
                w.sorted(java.util.Comparator.reverseOrder()).forEach(x -> {
                    try { Files.deleteIfExists(x); } catch (Exception ignored) { }
                });
            }
        }

        System.out.println(fails == 0 ? "\nSmokeTest passed." : "\nSmokeTest FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }
}
