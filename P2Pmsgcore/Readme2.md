## What was produced in `C:\_Dev\_ClaudeCode\MSCS_JavaBindings\P2Pmsgcore\`

### Native layer (C wrapper — Step 2 & 3)
| File | Purpose |
|------|---------|
| `native/P2Pmsgcore_c.h` | `extern "C"` header with opaque handles and flat C functions for all four classes |
| `native/P2Pmsgcore_c.cpp` | Implementation — casts handles back to C++ pointers via `static_cast` |

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
1. Add `P2Pmsgcore_c.h` + `.cpp` to the MSVC project and rebuild `P2Pmsgcore.dll`
2. Run `jextract` as shown in `README.md` (replaces the hand-written stub with the real one)
3. `mvn compile` → run `SmokeTest` with `-Djava.library.path=<dll dir>`
