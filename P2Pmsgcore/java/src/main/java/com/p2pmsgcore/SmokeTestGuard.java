package com.p2pmsgcore;

/**
 * Guard test: calling createHub WITHOUT p2pmsgcore_startup must fail cleanly
 * (return false), NOT hard-crash. Before the CreateP2PmsgHub guard + the
 * p2peerhub_create_hub try/catch, this scenario crashed in ntdll on an
 * uninitialised critical section. Deliberately does NOT call startup.
 *
 *   java --enable-native-access=ALL-UNNAMED
 *        -Djava.library.path=path\to\P2Pmsgcore.dll
 *        -cp target\classes
 *        com.p2pmsgcore.SmokeTestGuard
 */
public class SmokeTestGuard {

    public static void main(String[] args) {
        // NO p2pmsgcore_startup() on purpose.
        try (P2PeerHub hub = new P2PeerHub("GuardHub")) {
            boolean created = hub.createHub("GuardHub", 1);   // must NOT crash
            if (created) {
                System.out.println("UNEXPECTED: createHub succeeded without startup");
                System.exit(1);
            }
            System.out.println("ok  : createHub returned false without startup (no crash)");
        }
        System.out.println("SmokeTestGuard passed.");
    }
}
