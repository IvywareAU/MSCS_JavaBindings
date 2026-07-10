package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;

/**
 * Minimal smoke test — run after the native DLL is on java.library.path.
 *
 * Launch with:
 *   java --enable-native-access=ALL-UNNAMED
 *        -Djava.library.path=path\to\P2Pmsgcore.dll
 *        -cp target\classes
 *        com.p2pmsgcore.SmokeTest
 */
public class SmokeTest {

    public static void main(String[] args) {

        // ── Lifecycle: initialise the P2Pmsg environment ONCE before any hub op ─
        // (StartupP2Pmsg sets up the shared hub/pump critical sections + hub table;
        // without it, hub creation dereferences uninitialised locks and crashes.)
        if (P2Pmsgcore_c.p2pmsgcore_startup(16) == 0)
            throw new IllegalStateException("p2pmsgcore_startup failed");

        // ── P2PAddr ───────────────────────────────────────────────────────────
        try (P2PAddr addr = new P2PAddr("TestHub.Node1")) {
            System.out.println("P2PAddr name   : " + addr.name());
            System.out.println("P2PAddr isNull : " + addr.isNull());
            System.out.println("P2PAddr isEmpty: " + addr.isEmpty());
        }

        // ── P2PMsg ────────────────────────────────────────────────────────────
        byte[] payload = "Hello P2P".getBytes();
        try (P2PMsg msg = new P2PMsg("Hub1", "Hub2", "Test.Greeting", payload)) {
            System.out.println("P2PMsg name  : " + msg.name());
            System.out.println("P2PMsg src   : " + msg.source());
            System.out.println("P2PMsg dst   : " + msg.destination());
            System.out.println("P2PMsg size  : " + msg.dataSize());
            System.out.println("P2PMsg data  : " + new String(msg.data()));
        }

        // ── P2PeerHub ─────────────────────────────────────────────────────────
        try (P2PeerHub hub = new P2PeerHub("SmokeHub")) {
            boolean created = hub.createHub("SmokeHub", 1);
            System.out.println("Hub created: " + created);
            System.out.println("Hub address: " + hub.address());
            System.out.println("Hub id     : " + hub.hubId());
            hub.spawnHub();

            // Service connection
            P2PeerConWsa svc = P2PeerConWsa.serviceFactory("SmokeHub", 19999);
            System.out.println("Con mode (before post): " + svc.mode());
            hub.postConnection(svc, 0);   // hub now owns svc

            hub.closeHub();
        }

        P2Pmsgcore_c.p2pmsgcore_cleanup();   // tear the environment back down
        System.out.println("Smoke test passed.");
    }
}
