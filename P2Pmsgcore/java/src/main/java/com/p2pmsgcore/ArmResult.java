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
package com.p2pmsgcore;

import com.p2pmsgcore.native_.P2Pmsgcore_c;

/**
 * The answer to "would this hub arm?" — {@code p2pauth::ArmResult} as it crosses
 * the flat C ABI ({@code p2peerhub_auth_arm}).
 *
 * <p>Since 2026-08-18 authentication is <b>required by default</b>, and a hub that
 * cannot enforce what it requires does not start: {@code createHub} and
 * {@code spawnHub} both refuse before a pump thread exists. Anything other than
 * {@link #OK} or {@link #NOT_REQUIRED} is therefore a hub that will not run, and
 * {@link #text()} is the library's own one-line explanation of why.
 *
 * <p>{@link #NOT_REQUIRED} is <b>not</b> the same as armed-and-authenticating. It
 * means somebody called {@code requireAuth(false)}, and the hub will start and
 * accept anyone.
 *
 * <p><b>Since 2026-08-21 the gate asks a second question</b> (P2Pmsgcore
 * ProductionPlan.md Stage 3 step 19): a hub that requires authentication must
 * also hold a <i>position</i> on revocation. Name a list, or say
 * {@code requireRevocation(false)}. A hub that does neither reports
 * {@link #NO_REVOCATION} and does not start — which is a wider break than the
 * 2026-08-18 one, because the state it refuses is a <i>working</i> hub rather
 * than one that would have refused every peer anyway. It is a gate regardless,
 * because revocation is the only mechanism in the tree for <b>withdrawing</b>
 * trust already granted: an allow-list only ever adds.
 *
 * <p>{@link #REVOCATION_UNUSABLE} is the other half and rests on the older
 * argument exactly — a configured list that will not load makes every key read
 * as revoked, so that hub refuses <i>everyone</i>. It used to do that at the
 * first login, having started happily; it now does it at startup. There is no
 * opt-out for that one, and {@code requireRevocation(false)} does not excuse it.
 */
public enum ArmResult {

    /** Provisioned: an identity key and a non-empty allow-list are both loaded. */
    OK(0),
    /** {@code requireAuth(false)} — nothing to check, so the hub arms and accepts anyone. */
    NOT_REQUIRED(1),
    /** No identity key: this hub cannot prove itself to a peer that requires auth. */
    NO_IDENTITY(2),
    /** {@code setAllowList()} was never called: this hub trusts nobody in particular. */
    NO_ALLOW_LIST(3),
    /** An allow-list is configured and the last load of it failed — missing, unreadable, or one bad line. */
    ALLOW_UNUSABLE(4),
    /** The allow-list loads and parses, and names nobody. */
    EMPTY_ALLOW(5),
    /**
     * No revocation list is configured, and {@code requireRevocation(false)}
     * was never called to say that was deliberate. New 2026-08-21.
     */
    NO_REVOCATION(6),
    /**
     * A revocation list is configured and the last load of it failed. That
     * state fails closed — every key reads as revoked — so the hub would
     * refuse every peer. New 2026-08-21.
     */
    REVOCATION_UNUSABLE(7);

    private final int code;

    ArmResult(int code) { this.code = code; }

    /** The raw {@code p2pauth::ArmResult} value this constant stands for. */
    public int code() { return code; }

    /** Would a hub in this state start? True only for {@link #OK} and {@link #NOT_REQUIRED}. */
    public boolean arms() { return this == OK || this == NOT_REQUIRED; }

    /**
     * The library's own one-line text for this result, read back through
     * {@code p2peerhub_auth_arm_text} rather than restated here — so a Java
     * diagnostic and the C++ diagnostic for the same state cannot disagree.
     */
    public String text() {
        return NativeStrings.fromU8(P2Pmsgcore_c.p2peerhub_auth_arm_text(code));
    }

    /** Maps a raw {@code ArmResult} int to a constant. */
    public static ArmResult fromCode(int code) {
        for (ArmResult r : values()) if (r.code == code) return r;
        throw new IllegalArgumentException("unknown ArmResult " + code
                + " -- p2pauth::ArmResult has grown and this enum has not");
    }

    @Override
    public String toString() { return name() + " (" + text() + ")"; }
}
