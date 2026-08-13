## What was produced in `C:\_Dev\_ClaudeCode\MSCS_JavaBindings\P2Pmsgcore\`

### Native layer (C wrapper — Step 2 & 3)

**Lives in the P2Pmsgcore repository, not here.** This tree carried a copy under
`native/` until 2026-08-14; it was deleted because two copies of an ABI definition
drift apart, and this one already had — it sat several fixes behind the library it
described. See `README.md` Step 1.

| File (in the P2Pmsgcore checkout) | Purpose |
|------|---------|
| `P2Pmsgcore_c.h` | `extern "C"` header with opaque handles and flat C functions for all four classes — 74 entry points, 21 of them `_u8` twins. **jextract reads this file** |
| `P2Pmsgcore_c.cpp` | Implementation — resolves handles through a registry of live handles rather than casting the caller's pointer, and catches `P2Pevent` at every entry point so nothing unwinds across the `extern "C"` boundary |
| `P2Pmsgcore_c_u8.cpp` | The UTF-8 `_u8` twins |

**Classes wrapped:** `P2Paddr`, `P2PeerMsg`, `P2PeerConWsa`, `P2PeerHub`

### Java layer (Panama bindings — Step 5 & 6)
| File | Purpose |
|------|---------|
| `native_/P2Pmsgcore_c.java` | Low-level jextract stub — one `MethodHandle` + typed static method per C function |
| `NativeStrings.java` | `wchar_t*` ↔ `String` (UTF-16LE) conversion |
| `P2PAddr.java` | Clean `AutoCloseable` wrapper for `P2Paddr` |
| `P2PMsg.java` | Clean wrapper for `P2PeerMsg` with `detach()` for framework ownership handoff |
| `P2PeerConWsa.java` | TCP connection wrapper with `clientFactory`/`serviceFactory` static constructors |
| `P2PeerHub.java` | Hub wrapper with spawn/close/postConnection/postMessage |
| `SmokeTest.java` | End-to-end test exercising all four wrappers |
| `pom.xml` | Maven build — Java 22+, `--enable-native-access=ALL-UNNAMED` wired in |

### To activate
1. Build `P2Pmsgcore.dll` from the P2Pmsgcore checkout — the wrapper is already one
   of its sources, so there is nothing to add to the project
2. Run `jextract` as shown in `README.md`, against the header in that checkout
   (replaces the hand-written stub with the real one)
3. `mvn compile` → run `SmokeTest` with `-Djava.library.path=<dll dir>`
