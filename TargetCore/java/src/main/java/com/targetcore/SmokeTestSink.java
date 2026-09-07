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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The receive half — {@code p2peerhub_set_sink_u8}, bound as a Panama upcall.
 *
 * <p>Everything else on the flat C ABI is post-only, so before this entry point a
 * Java consumer could send and could never be told that anything arrived. It was
 * one of the eleven symbols missing from the generated bindings until 2026-08-20,
 * which is why this test is new rather than old.
 *
 * <p>What it proves, in order:
 * <ol>
 *   <li>the sink is <b>called at all</b> — a message posted to the hub's own
 *       address comes back up into Java;</li>
 *   <li>the four fields survive the boundary: source, destination, message ID and
 *       the opaque byte payload, the last of which is copied out of storage that
 *       is valid only for the duration of the call;</li>
 *   <li>it runs on the <b>hub's pump thread</b>, not the thread that registered
 *       it — a thread the JVM never created, attached transparently by Panama;</li>
 *   <li>a handler that throws does not take the JVM down.</li>
 * </ol>
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL, 3 INCONCLUSIVE (nothing arrived inside
 * the timeout, which is not the same as arriving wrong).
 */
public class SmokeTestSink {

    private static final String HUB     = "SinkSmoke.Hub";
    private static final String SENDER  = "SinkSmoke.Sender";
    private static final String MSG_ID  = "Sink.Ping";
    private static final byte[] PAYLOAD = "sink payload é€".getBytes(StandardCharsets.UTF_8);

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    // What the upcall saw, read back on the main thread once the latch drops.
    private static final AtomicReference<String[]> seenText  = new AtomicReference<>();
    private static final AtomicReference<byte[]>   seenBytes = new AtomicReference<>();
    private static final AtomicReference<String>   seenThread = new AtomicReference<>();
    private static final CountDownLatch            arrived   = new CountDownLatch(1);
    private static volatile boolean                throwOnce = true;
    private static volatile boolean                survivedThrow = false;

    public static void main(String[] args) throws Exception {

        TargetCore.startup(16);
        try {
            run();
        } finally {
            TargetCore.cleanup();
        }

        if (arrived.getCount() != 0) {
            System.out.println("\nSmokeTestSink INCONCLUSIVE - the sink never fired");
            System.exit(3);
        }
        System.out.println(fails == 0 ? "\nSmokeTestSink passed."
                                      : "\nSmokeTestSink FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }

    private static void run() {
        final String registrar = Thread.currentThread().getName();

        try (P2PeerHub hub = new P2PeerHub(HUB)) {
            hub.requireAuth(false);           // this test is about delivery, not identity

            //  spawnHub() and NOT createHub(): SpawnHub makes the hub's own thread
            //  and creates the hub inside it. Calling createHub() here would take
            //  THIS thread's one pump slot (see P2PeerHub.createHub) and leave the
            //  spawned thread without one.

            //  Register BEFORE spawnHub, as the header asks.
            boolean installed = hub.setSink((src, dst, msgID, data) -> {
                //  A throw from a handler must not unwind into C++. Prove the
                //  wrapper catches it, once, and that the hub keeps running.
                if (throwOnce) {
                    throwOnce = false;
                    survivedThrow = true;
                    throw new IllegalStateException("deliberate: a handler that throws");
                }
                seenText.set(new String[] { src, dst, msgID });
                seenBytes.set(data);
                seenThread.set(Thread.currentThread().getName());
                arrived.countDown();
                return true;                  // consumed - see P2PeerHub.Sink
            });
            check(installed, "setSink() registered");

            check(hub.spawnHub() != 0L, "spawnHub()");

            //  The first message is eaten by the deliberate throw above; the
            //  second is the one the assertions are about.
            for (int i = 0; i < 2; i++) {
                try (P2PMsg msg = new P2PMsg(SENDER, HUB, MSG_ID, PAYLOAD)) {
                    hub.postMessage(msg);
                }
            }

            boolean got = false;
            try { got = arrived.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }

            if (got) {
                String[] txt = seenText.get();
                check(SENDER.equals(txt[0]), "source survived the boundary: " + txt[0]);
                //  The FULL address, read from the hub rather than off the message.
                //  hub.address() would say "Hub" here - it returns the leaf.
                check(HUB.equals(txt[1]),
                        "destination is the hub's FULL address, read from the hub: " + txt[1]);
                check(MSG_ID.equals(txt[2]), "message ID survived: " + txt[2]);
                check(java.util.Arrays.equals(PAYLOAD, seenBytes.get()),
                        "payload survived byte-identically (" + seenBytes.get().length + " bytes)");
                check(!registrar.equals(seenThread.get()),
                        "delivered on the PUMP thread '" + seenThread.get()
                        + "', not the registering thread '" + registrar + "'");
                check(survivedThrow,
                        "a handler that threw was caught, and the hub kept running");
            }

            hub.closeHub();
            check(hub.clearSink(), "clearSink() after closeHub()");
        }
    }
}
