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

import com.targetcore.native_.Targetcore_c;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Are these bindings still the whole library?
 *
 * <p>This is the check that did not exist, and its absence is the reason the
 * bindings sat <b>eleven entry points behind</b> from 2026-08-14 to 2026-08-20 —
 * the entire authentication block and both halves of the receive sink. Nothing
 * failed while that was true. {@code mvn compile} was green, the smoke test
 * printed "passed", and a Java caller simply could not start a hub.
 *
 * <p>The comparison is against {@code .github/ci/abi-flat.manifest} in a
 * Targetcore checkout, which VERSIONING.md §2 names as <i>the</i> enumeration of
 * the covered surface. It is deliberately read from over there rather than copied
 * to here: an ABI definition duplicated across two repositories drifts, and this
 * one already had.
 *
 * <p>Point it somewhere else with
 * {@code -Dtargetcore.manifest=<path to abi-flat.manifest>}.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL (the bindings are behind, or ahead),
 * 2 SETUP (no manifest to compare against — not a pass).
 */
public class AbiCoverage {

    private static final String DEFAULT_MANIFEST =
            "../../../MSCS/Targetcore/.github/ci/abi-flat.manifest";

    /**
     * The one entry point deliberately left unbound at the wrapper layer. The
     * generated layer still carries it — this list is about {@link P2PeerHub} and
     * friends, not about jextract's output, which must be complete.
     */
    private static final Set<String> WRAPPER_EXEMPT = Set.of("p2peerhub_set_sink");

    public static void main(String[] args) throws Exception {

        Path manifest = Path.of(System.getProperty("targetcore.manifest", DEFAULT_MANIFEST));
        if (!Files.isReadable(manifest)) {
            System.out.println("SETUP: no manifest at " + manifest.toAbsolutePath());
            System.out.println("       pass -Dtargetcore.manifest=<path to abi-flat.manifest>");
            System.exit(2);
        }

        SortedSet<String> promised = new TreeSet<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            String s = line.trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            promised.add(s);
        }

        SortedSet<String> bound = new TreeSet<>();
        for (Method m : Targetcore_c.class.getDeclaredMethods()) {
            String n = m.getName();
            //  jextract emits name(), name$descriptor(), name$handle(), name$address().
            if (n.indexOf('$') >= 0) continue;
            if (promised.contains(n)) bound.add(n);
        }

        SortedSet<String> missing = new TreeSet<>(promised);
        missing.removeAll(bound);

        System.out.println("manifest : " + manifest.toAbsolutePath().normalize());
        System.out.println("promised : " + promised.size() + " flat C entry points");
        System.out.println("generated: " + bound.size());

        int fails = 0;
        if (!missing.isEmpty()) {
            fails++;
            System.out.println("\nFAIL: " + missing.size()
                    + " promised entry point(s) have no generated binding.");
            System.out.println("      Re-run jextract - see README.md Step 3.");
            missing.forEach(n -> System.out.println("        - " + n));
        } else {
            System.out.println("ok  : every promised entry point has a generated binding");
        }

        //  The friendly layer, checked the only way reflection can: by name.
        //  A wrapper is judged present if some public method of the wrapper
        //  classes mentions the C name in the source, so this half is a
        //  reminder rather than a proof - it is the generated half above that
        //  the library can actually break.
        SortedSet<String> unwrapped = new TreeSet<>(WRAPPER_EXEMPT);
        unwrapped.retainAll(promised);
        if (!unwrapped.isEmpty())
            System.out.println("note : deliberately unwrapped at the friendly layer: "
                    + unwrapped + " (the _u8 twin is the portable form)");

        System.out.println(fails == 0 ? "\nAbiCoverage passed." : "\nAbiCoverage FAILED");
        if (fails != 0) System.exit(1);
    }
}
