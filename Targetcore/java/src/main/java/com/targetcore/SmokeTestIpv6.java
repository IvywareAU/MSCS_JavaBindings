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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * IPv6 over the flat C ABI -- {@code p2peerconwsa_set_family} /
 * {@code _get_family}, added 2026-10-05.
 *
 * <p>Until then the family setting existed only on the C++ class, so a Java
 * consumer could not listen on IPv6 at all and a client given "::1" failed at
 * the resolver. What it proves, in order:
 * <ol>
 *   <li>a client made with an IPv6 literal starts in {@code FAMILY_IPV6} and one
 *       made with a dotted quad keeps {@code FAMILY_IPV4};</li>
 *   <li>a value outside 0..2 is refused rather than reaching the kernel;</li>
 *   <li>a message crosses a v6-only service from a client dialling ::1;</li>
 *   <li>a DUAL service is reached by a client dialling 127.0.0.1 -- one socket,
 *       both families.</li>
 * </ol>
 * The kernel half (IPV6_V6ONLY in both directions, the v4-mapped peer) is
 * MscsUnitTests' p2p_ipv6 family; this is the binding reaching it.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL, 2 SETUP (this host will not bind ::1),
 * 3 INCONCLUSIVE (nothing arrived inside the timeout).
 */
public class SmokeTestIpv6 {

    private static final String TOP   = "V6Smoke";
    private static final String SIX   = "V6Smoke.Six";
    private static final String FOUR  = "V6Smoke.Four";
    private static final int    PORT6 = 19996;
    private static final int    PORTD = 19997;

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    private static final CountDownLatch fromSix  = new CountDownLatch(1);
    private static final CountDownLatch fromFour = new CountDownLatch(1);

    public static void main(String[] args) throws Exception {

        if (!hostHasIPv6()) {
            System.out.println("SETUP: this host will not bind ::1 - no IPv6 stack to test");
            System.exit(2);
        }

        Targetcore.startup(16);
        boolean arrived;
        try {
            arrived = run();
        } finally {
            Targetcore.cleanup();
        }

        if (!arrived) {
            System.out.println("\nSmokeTestIpv6 INCONCLUSIVE - a message never arrived");
            System.exit(3);
        }
        System.out.println(fails == 0 ? "\nSmokeTestIpv6 passed."
                                      : "\nSmokeTestIpv6 FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }

    // A v6-disabled Windows image is a supported deployment: SETUP, not FAIL.
    private static boolean hostHasIPv6() {
        try (ServerSocket s = new ServerSocket()) {
            InetAddress lo = InetAddress.getByName("::1");
            if (!(lo instanceof Inet6Address)) return false;
            s.bind(new InetSocketAddress(lo, 0));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean run() throws InterruptedException {

        // -- the family a client starts in, and the range check -----------------
        try (P2PeerConWsa c6 = P2PeerConWsa.clientFactory(TOP, "::1", PORT6);
             P2PeerConWsa c4 = P2PeerConWsa.clientFactory(TOP, "127.0.0.1", PORT6)) {
            check(c6.family() == P2PeerConWsa.FAMILY_IPV6, "a ::1 client starts in IPv6");
            check(c4.family() == P2PeerConWsa.FAMILY_IPV4, "a 127.0.0.1 client keeps IPv4");
            boolean refused = false;
            try { c4.setFamily(7); } catch (IllegalArgumentException e) { refused = true; }
            check(refused && c4.family() == P2PeerConWsa.FAMILY_IPV4,
                    "setFamily(7) is refused and changes nothing");
        }

        // -- three hubs, two links -------------------------------------------------
        try (P2PeerHub top  = new P2PeerHub(TOP);
             P2PeerHub six  = new P2PeerHub(SIX);
             P2PeerHub four = new P2PeerHub(FOUR)) {

            for (P2PeerHub h : new P2PeerHub[] { top, six, four })
                h.requireAuth(false);         // this test is about the family, not identity

            check(top.setSink((src, dst, msgID, data) -> {
                if (SIX.equals(src))  fromSix.countDown();
                if (FOUR.equals(src)) fromFour.countDown();
                return true;
            }), "setSink() on the listening hub");

            check(top.spawnHub()  != 0L, "spawnHub() " + TOP);
            check(six.spawnHub()  != 0L, "spawnHub() " + SIX);
            check(four.spawnHub() != 0L, "spawnHub() " + FOUR);

            P2PeerConWsa svc6 = P2PeerConWsa.serviceFactory(SIX, PORT6);
            svc6.setFamily(P2PeerConWsa.FAMILY_IPV6);
            check(top.postConnection(svc6, 0), "an IPv6-only service posted");

            P2PeerConWsa svcD = P2PeerConWsa.serviceFactory(FOUR, PORTD);
            svcD.setFamily(P2PeerConWsa.FAMILY_DUAL);
            check(top.postConnection(svcD, 0), "a dual-stack service posted");

            check(six.postConnection (P2PeerConWsa.clientFactory(TOP, "::1",       PORT6), 0),
                    "a client dialling ::1 posted");
            check(four.postConnection(P2PeerConWsa.clientFactory(TOP, "127.0.0.1", PORTD), 0),
                    "a client dialling 127.0.0.1 posted");

            //  The links come up on the pumps, not here. Posting EARLY is not a
            //  harmless loss: an application message that reaches the service before
            //  login completes is a protocol violation and the service drops the
            //  link (P2PeerCon::GateAppMsgInbound), and a ClientFactory connection
            //  dials once. So give login its time first, then retry for stragglers.
            Thread.sleep(3000);
            byte[] ping = "v6".getBytes(StandardCharsets.UTF_8);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
            while (System.nanoTime() < deadline
                    && (fromSix.getCount() != 0 || fromFour.getCount() != 0)) {
                if (fromSix.getCount() != 0)
                    try (P2PMsg m = new P2PMsg(SIX, TOP, "V6.Ping", ping)) { six.postMessage(m); }
                if (fromFour.getCount() != 0)
                    try (P2PMsg m = new P2PMsg(FOUR, TOP, "V6.Ping", ping)) { four.postMessage(m); }
                Thread.sleep(250);
            }
            check(fromSix.getCount()  == 0, "a message crossed the IPv6-only link over ::1");
            check(fromFour.getCount() == 0, "an IPv4 client reached the dual-stack service");

            four.closeHub();
            six.closeHub();
            top.closeHub();
            top.clearSink();
        }
        return fromSix.getCount() == 0 && fromFour.getCount() == 0;
    }
}
