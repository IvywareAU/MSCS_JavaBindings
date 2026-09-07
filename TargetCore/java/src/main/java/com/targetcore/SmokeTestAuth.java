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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The arm gate, from Java — {@code p2peerhub_require_auth} … {@code p2peerhub_auth_arm}.
 *
 * <p>These eleven entry points were added to the flat C ABI for Stage 3 step 8 and
 * were <b>absent from the generated bindings for six days</b>, because nobody
 * re-ran jextract after the surface grew. What that cost is the thing this test
 * asserts first: since 2026-08-18 authentication is required by default and a hub
 * that cannot enforce it does not start, so a Java caller holding the old bindings
 * could not start a hub <i>at all</i> — and could not turn the requirement off
 * either, because {@code require_auth} was one of the missing eleven.
 *
 * <p>Three phases against one process:
 * <ol>
 *   <li><b>unprovisioned</b> — the default. {@code authArm()} must say
 *       {@code NO_IDENTITY} and {@code createHub()} must refuse.</li>
 *   <li><b>{@code requireAuth(false)}</b> — the documented migration.
 *       {@code NOT_REQUIRED}, and the hub runs.</li>
 *   <li><b>provisioned</b> — generate an identity, publish its half, write an
 *       allow-list naming it, and watch the same hub go {@code NO_IDENTITY} →
 *       {@code NO_ALLOW_LIST} → {@code NO_REVOCATION} → {@code OK} one call at
 *       a time. The third step arrived on 2026-08-21 with the revocation
 *       gate, and it is the whole reason this test is worth having: the
 *       sequence is the operator's actual experience, so a new step in it
 *       shows up here before it shows up in production.</li>
 * </ol>
 *
 * <p>Phase 3 is the one that matters: it proves the Java side can perform the
 * whole provisioning workflow, not merely ask whether it happened.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL.
 */
public class SmokeTestAuth {

    private static int fails = 0;

    private static void check(boolean ok, String msg) {
        System.out.println((ok ? "ok  : " : "FAIL: ") + msg);
        if (!ok) fails++;
    }

    public static void main(String[] args) throws Exception {

        Path dir = Files.createTempDirectory("p2p-authsmoke-");
        try {
            TargetCore.startup(16);
            try {
                //  Each phase on its own thread, because the kernel allows one
                //  hub per thread and closing the first does not release its
                //  thread -- see P2PeerHub.createHub. Run them all on main and
                //  phase 3 fails for a reason that has nothing to do with auth.
                onOwnThread("phase1", SmokeTestAuth::phase1Unprovisioned);
                onOwnThread("phase2", SmokeTestAuth::phase2RequireAuthOff);
                onOwnThread("phase3", () -> phase3Provisioned(dir));
                onOwnThread("phase4", SmokeTestAuth::phase4SpawnIsGatedToo);
            } finally {
                TargetCore.cleanup();
            }
        } finally {
            deleteTree(dir);
        }

        System.out.println(fails == 0 ? "\nSmokeTestAuth passed."
                                      : "\nSmokeTestAuth FAILED (" + fails + ")");
        if (fails != 0) System.exit(1);
    }


    /** Runs one phase on a thread of its own. See P2PeerHub.createHub. */
    private interface Phase { void run() throws Exception; }

    private static void onOwnThread(String name, Phase phase) throws InterruptedException {
        Thread t = new Thread(() -> {
            try { phase.run(); }
            catch (Exception e) { check(false, name + " threw " + e); }
        }, name);
        t.start();
        t.join();
    }

    // -- phase 1 ---------------------------------------------------------------
    //  The default, and the state every pre-2026-08-18 example lands in.

    private static void phase1Unprovisioned() {
        System.out.println("\n[1] a hub nobody provisioned");
        try (P2PeerHub hub = new P2PeerHub("AuthSmoke.Bare")) {
            check(hub.isAuthRequired(), "auth is required by DEFAULT, with no call to say so");
            ArmResult arm = hub.authArm();
            check(arm == ArmResult.NO_IDENTITY, "authArm() = " + arm);
            check(!arm.arms(),                  "…which is a hub that will not start");
            check(hub.allowListPath() == null,  "no allow-list configured");
            //  The refusal itself. The library prints its own diagnostic here, so
            //  the [ERROR] block below this line is the expected output, not noise.
            check(!hub.createHub("AuthSmoke.Bare", 1),
                    "createHub() REFUSES rather than starting a hub that accepts anyone");
        }
    }

    // -- phase 2 ---------------------------------------------------------------
    //  The migration. One call, said deliberately.

    private static void phase2RequireAuthOff() {
        System.out.println("\n[2] requireAuth(false) - the supported migration");
        try (P2PeerHub hub = new P2PeerHub("AuthSmoke.Open")) {
            hub.requireAuth(false);
            check(!hub.isAuthRequired(), "isAuthRequired() now false");
            ArmResult arm = hub.authArm();
            check(arm == ArmResult.NOT_REQUIRED, "authArm() = " + arm);
            check(arm.arms(), "…arms, and NOT_REQUIRED is not the same as authenticating");
            //  createHub() OR spawnHub(), never both - SpawnHub asserts
            //  m_nHubID == 0. Phase 4 is the one that exercises spawnHub.
            check(hub.createHub("AuthSmoke.Open", 1), "createHub() succeeds");
            hub.closeHub();
        }
    }

    // -- phase 3 ---------------------------------------------------------------
    //  The whole provisioning workflow, performed from Java.

    private static void phase3Provisioned(Path dir) throws IOException {
        System.out.println("\n[3] provision an identity and an allow-list");
        final String addr    = "AuthSmoke.Real";
        final Path   keyPath = dir.resolve("authsmoke.key");
        final Path   allow   = dir.resolve("allow.txt");

        try (P2PeerHub hub = new P2PeerHub(addr)) {

            P2PeerHub.Provisioned prov = hub.provisionAuth(keyPath.toString());
            check(prov.ok(),      "provisionAuth() = " + prov.result());
            check(prov.created(), "…and it CREATED the key rather than finding one");
            check(prov.fingerprint() != null && !prov.fingerprint().isBlank(),
                    "fingerprint an operator can read aloud: " + prov.fingerprint());
            check(Files.exists(keyPath),                       "the key file exists");
            check(Files.exists(Path.of(keyPath + ".pub")),     "the publishable half exists");

            //  An identity alone is not enough, and that is deliberate: a library
            //  that invented an allow-list would be answering "who do you trust?"
            //  on the operator's behalf.
            ArmResult arm = hub.authArm();
            check(arm == ArmResult.NO_ALLOW_LIST,
                    "an identity alone does NOT arm: " + arm);

            //  The operator's step, done here in four lines: take the published
            //  point and name the peer it belongs to.
            String hex = publicPointHex(Path.of(keyPath + ".pub"));
            check(hex != null && hex.length() == 128,
                    "the .pub file carries a 128-hex public point");
            Files.writeString(allow,
                    "# written by SmokeTestAuth\n" + addr + " " + hex + "\n",
                    StandardCharsets.UTF_8);

            check(hub.setAllowList(allow.toString()) == IdResult.OK, "setAllowList()");
            check(allow.toString().equals(hub.allowListPath()),
                    "allowListPath() names the file the library actually loaded");

            //  ONE MORE STEP SINCE 2026-08-21, and the arm result says which:
            //  a hub that requires auth must also hold a POSITION on
            //  revocation. An identity and an allow-list are no longer the
            //  whole of provisioning, and this phase is the definition of the
            //  term - so it grows rather than opting out.
            arm = hub.authArm();
            check(arm == ArmResult.NO_REVOCATION,
                    "identity + allow-list is no longer enough: " + arm);

            //  The migration, said out loud. This test is not about
            //  revocation, and declaring that is one line - it does NOT turn
            //  revocation off, only permit its absence.
            hub.requireRevocation(false);
            check(!hub.isRevocationRequired(), "requireRevocation(false) took");

            arm = hub.authArm();
            check(arm == ArmResult.OK,   "authArm() = " + arm);
            check(hub.isAuthRequired(),  "…and the hub still REQUIRES auth");

            check(hub.createHub(addr, 1), "createHub() succeeds on a provisioned hub");

            check(hub.reloadAllowList() == IdResult.OK, "reloadAllowList() on a running hub");

            hub.closeHub();
        }
    }

    // -- phase 4 ---------------------------------------------------------------
    //  The gate is on BOTH entry points, not just the one phase 1 used.

    private static void phase4SpawnIsGatedToo() {
        System.out.println("\n[4] spawnHub() is gated as well as createHub()");
        try (P2PeerHub hub = new P2PeerHub("AuthSmoke.Spawn")) {
            check(hub.authArm() == ArmResult.NO_IDENTITY, "unprovisioned, as phase 1");
            //  P2PeerHub::SpawnHub calls AuthArmOrRefuse before the thread exists,
            //  which is the point: the alternative is a pump thread that starts,
            //  refuses every peer, and looks healthy from outside.
            check(hub.spawnHub() == 0L,
                    "spawnHub() REFUSES rather than starting a thread that cannot enforce");
        }
    }

    // -- helpers ---------------------------------------------------------------

    /**
     * The published half is "a hex line with a fingerprint comment", so the point
     * is the first 128-hex-character token in the file.
     */
    private static String publicPointHex(Path pub) throws IOException {
        Pattern p = Pattern.compile("\\b([0-9a-fA-F]{128})\\b");
        for (String line : Files.readAllLines(pub, StandardCharsets.UTF_8)) {
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash);
            Matcher m = p.matcher(line);
            if (m.find()) return m.group(1);
        }
        return null;
    }

    private static void deleteTree(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(x -> {
                try { Files.deleteIfExists(x); } catch (IOException ignored) { }
            });
        } catch (IOException ignored) { }
    }
}
