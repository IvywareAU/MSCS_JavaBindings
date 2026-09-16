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

/**
 * Process-wide lifecycle for the P2Pmsg environment.
 *
 * <p>{@link #startup} must be called <b>once per process, before any
 * {@link P2PeerHub} operation</b>, and {@link #cleanup} at the end. Skipping it
 * does not fail politely in the kernel — {@code CreateP2PmsgHub} enters
 * uninitialised critical sections — which is why the C entry point guards it and
 * why {@code SmokeTestGuard} exists to prove the guard holds.
 *
 * <p>{@link P2PAddr} and {@link P2PMsg} are pure object model and do not need it;
 * code that only builds addresses and messages can skip startup entirely.
 *
 * <pre>{@code
 * Targetcore.startup(16);
 * try {
 *     // ... hubs, connections, messages ...
 * } finally {
 *     Targetcore.cleanup();
 * }
 * }</pre>
 */
public final class Targetcore {

    private Targetcore() {}

    /**
     * Initialises the shared hub/pump locks and the hub-manager table.
     *
     * @param maxHubs the size of the hub table
     * @throws IllegalStateException if the environment could not be initialised
     */
    public static void startup(int maxHubs) {
        if (Targetcore_c.targetcore_startup(maxHubs) == 0)
            throw new IllegalStateException("targetcore_startup(" + maxHubs + ") failed");
    }

    /** Tears the environment back down. Safe to call once, at the end. */
    public static void cleanup() {
        Targetcore_c.targetcore_cleanup();
    }
}
