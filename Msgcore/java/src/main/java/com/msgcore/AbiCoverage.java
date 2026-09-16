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

import com.msgcore.native_.Msgcore_c;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Are these bindings still the whole library?
 *
 * <p>This is the check that did not exist, and its absence let something worse than
 * staleness happen. Until 2026-08-20 {@code native_/Msgcore_c.java} was <b>not
 * jextract output at all</b> — it was a hand-written stub whose own header said
 * "this stub documents the expected jextract output shape so wrapper classes can
 * compile before jextract is actually run… replace this file entirely with the real
 * jextract output once the Msgcore DLL is built." That was never done. It
 * hand-transcribed <b>108 of the header's 282</b> entry points, and nothing
 * compared the two numbers.
 *
 * <p><b>The comparison is against the header</b>, {@code Msgcore_c.h} in a Msgcore
 * checkout — the same file jextract reads. Targetcore's equivalent check compares
 * against {@code .github/ci/abi-flat.manifest}, an explicit enumeration of the
 * covered surface that VERSIONING.md makes a promise about; <b>Msgcore has no such
 * manifest</b>, so the header is the best available authority. That is a real
 * difference between the two libraries and not a shortcut taken here: a header says
 * what exists, a manifest says what is promised, and only the second can tell you
 * that removing something is a breaking change.
 *
 * <p>Point it somewhere else with {@code -Dmsgcore.header=<path to Msgcore_c.h>}.
 *
 * <p>Verdict = exit code: 0 PASS, 1 FAIL (the bindings are behind), 2 SETUP (no
 * header to compare against — not a pass).
 */
public class AbiCoverage {

    private static final String DEFAULT_HEADER = "../../../MSCS/Msgcore/Msgcore_c.h";

    public static void main(String[] args) throws Exception {

        Path header = Path.of(System.getProperty("msgcore.header", DEFAULT_HEADER));
        if (!Files.isReadable(header)) {
            System.out.println("SETUP: no header at " + header.toAbsolutePath());
            System.out.println("       pass -Dmsgcore.header=<path to Msgcore_c.h>");
            System.exit(2);
        }

        String src = Files.readString(header, StandardCharsets.UTF_8);
        //  Strip comments first, so a commented-out declaration is not counted as
        //  a promise the bindings are failing to keep.
        src = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");

        SortedSet<String> declared = new TreeSet<>();
        Matcher m = Pattern.compile("MSGCORE_C_API\\b(.*?)\\(", Pattern.DOTALL).matcher(src);
        Pattern ident = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
        while (m.find()) {
            String last = null;
            Matcher i = ident.matcher(m.group(1));
            while (i.find()) last = i.group();
            //  Skip the macro's own #define lines -- "#define MSGCORE_C_API
            //  __declspec(dllexport)" matches this pattern and would otherwise
            //  enter the set as a declared entry point called __declspec.
            if (last == null || last.startsWith("__")) continue;
            if (last.equals(last.toLowerCase(Locale.ROOT))) declared.add(last);
        }

        SortedSet<String> bound = new TreeSet<>();
        for (Method mm : Msgcore_c.class.getDeclaredMethods()) {
            String n = mm.getName();
            if (n.indexOf('$') >= 0) continue;     // name$descriptor / $handle / $address
            if (declared.contains(n)) bound.add(n);
        }

        SortedSet<String> missing = new TreeSet<>(declared);
        missing.removeAll(bound);

        System.out.println("header   : " + header.toAbsolutePath().normalize());
        System.out.println("declares : " + declared.size() + " entry points");
        System.out.println("generated: " + bound.size());

        int fails = 0;
        if (!missing.isEmpty()) {
            fails++;
            System.out.println("\nFAIL: " + missing.size()
                    + " declared entry point(s) have no generated binding.");
            System.out.println("      Re-run jextract - see BUILD.md Step 3.");
            missing.forEach(n -> System.out.println("        - " + n));
        } else {
            System.out.println("ok  : every declared entry point has a generated binding");
        }

        System.out.println(fails == 0 ? "\nAbiCoverage passed." : "\nAbiCoverage FAILED");
        if (fails != 0) System.exit(1);
    }
}
