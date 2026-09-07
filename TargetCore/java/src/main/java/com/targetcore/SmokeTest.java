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

import java.nio.charset.StandardCharsets;

/**
 * The end-to-end smoke test: every wrapper class, one process.
 *
 * <p>This used to print "Smoke test passed." unconditionally, and on 2026-08-20 it
 * was doing exactly that while printing {@code Hub created: false} three lines
 * above — the hub had stopped starting when authentication became required by
 * default on 2026-08-18, and nothing here was checking. A test whose verdict does
 * not depend on the result is not a test, so every line below is now an assertion
 * and the exit code is the verdict.
 *
 * <p>Run:
 * <pre>
 *   java --enable-native-access=ALL-UNNAMED -cp target\classes com.targetcore.SmokeTest
 * </pre>
 * with the directory holding {@code TargetCore.dll} and {@code Msgcore.dll} on
 * {@code PATH} — see the README on why {@code -Djava.library.path} is not enough
 * any more.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL.
 */
public class SmokeTest {

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    public static void main(String[] args) {

        // -- P2PAddr and P2PMsg are pure object model: no startup needed --------
        try (P2PAddr addr = new P2PAddr("TestHub.Node1")) {
            check("Node1".equals(addr.name()), "P2PAddr.name() is the LEAF: " + addr.name());
            check(!addr.isNull(),              "P2PAddr.isNull() false");
            check(!addr.isEmpty(),             "P2PAddr.isEmpty() false");
            //  IsChild takes the CANDIDATE CHILD, so this asks the question in
            //  the direction the library means it. The reverse must be false.
            check(!addr.isChild("TestHub"),  "\"TestHub\" is not a child of TestHub.Node1");
            check(addr.isRable("TestHub.Node1.Leaf"),
                    "a descendant is routable through TestHub.Node1");
            check(addr.byteSize() > 0,         "P2PAddr.byteSize() = " + addr.byteSize());
        }

        byte[] payload = "Hello P2P".getBytes(StandardCharsets.UTF_8);
        try (P2PMsg msg = new P2PMsg("Hub1", "Hub2", "Test.Greeting", payload)) {
            check("Test.Greeting".equals(msg.name()), "P2PMsg.name()");
            check("Hub1".equals(msg.source()),        "P2PMsg.source()");
            check("Hub2".equals(msg.destination()),   "P2PMsg.destination()");
            check(msg.dataSize() == payload.length,   "P2PMsg.dataSize() = " + msg.dataSize());
            check(java.util.Arrays.equals(payload, msg.data()), "P2PMsg.data() round-trips");
            check(msg.byteSize() > 0,                 "P2PMsg.byteSize() = " + msg.byteSize());
        }

        // -- the hub needs the environment --------------------------------------
        TargetCore.startup(16);
        try {
            try (P2PeerHub hub = new P2PeerHub("SmokeHub")) {

                //  Auth is REQUIRED BY DEFAULT since 2026-08-18, and a hub that
                //  cannot enforce it does not start. This test is about the object
                //  model and the wiring, so it takes the documented migration and
                //  says so out loud. SmokeTestAuth is the one that provisions.
                check(hub.isAuthRequired(), "auth required by default");
                check(hub.authArm() == ArmResult.NO_IDENTITY,
                        "an unprovisioned hub would not arm: " + hub.authArm());
                hub.requireAuth(false);
                check(hub.authArm() == ArmResult.NOT_REQUIRED, "requireAuth(false) arms it");

                //  spawnHub() ALONE - not createHub() first. SpawnHub asserts
                //  m_nHubID == 0, so the pair this test used to make hung a Debug
                //  build on a modal assertion and was silently tolerated by
                //  Release, which is the only configuration it was ever run in.
                check("SmokeHub".equals(hub.address()), "hub.address() = " + hub.address());
                check(hub.spawnHub() != 0L,             "spawnHub()");
                check(hub.hubId() != 0,                 "hub.hubId() = " + hub.hubId());

                P2PeerConWsa svc = P2PeerConWsa.serviceFactory("SmokeHub", 19999);
                check(svc.mode() != 0,                  "service connection mode = " + svc.mode());
                check(hub.postConnection(svc, 0),       "postConnection() - the hub owns it now");

                hub.closeHub();
            }
        } finally {
            TargetCore.cleanup();
        }

        System.out.println(fails == 0 ? "\nSmokeTest passed." : "\nSmokeTest FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }
}
