## What was produced in `C:\_Dev\_ClaudeCode\MSCS_JavaBindings\TargetCore\`

### Native layer (C wrapper — Step 2 & 3)

**Lives in the TargetCore repository, not here.** This tree carried a copy under
`native/` until 2026-08-14; it was deleted because two copies of an ABI definition
drift apart, and this one already had — it sat several fixes behind the library it
described. See `README.md` Step 1.

| File (in the TargetCore checkout) | Purpose |
|------|---------|
| `TargetCore_c.h` | `extern "C"` header with opaque handles and flat C functions for all four classes — **83** entry points, 21 of them `_u8` twins (74/21 until the authentication block and the receive sink landed; regenerated 2026-08-20). **jextract reads this file** |
| `TargetCore_c.cpp` | Implementation — resolves handles through a registry of live handles rather than casting the caller's pointer, and catches `P2Pevent` at every entry point so nothing unwinds across the `extern "C"` boundary |
| `TargetCore_c_u8.cpp` | The UTF-8 `_u8` twins |

**Classes wrapped:** `P2Paddr`, `P2PeerMsg`, `P2PeerConWsa`, `P2PeerHub`

### Java layer (Panama bindings — Step 5 & 6)
| File | Purpose |
|------|---------|
| `native_/TargetCore_c.java` | Low-level jextract output — one `MethodHandle` + typed static method per C function, all 83 |
| `native_/P2PeerHubSinkFn[U8].java` | jextract upcall-stub factories for the receive-sink typedefs |
| `NativeStrings.java` | `wchar_t*` and UTF-8 `char*` ↔ `String` conversion |
| `TargetCore.java` | Process lifecycle: `startup` / `cleanup` |
| `P2PAddr.java` | Clean `AutoCloseable` wrapper for `P2Paddr` |
| `P2PMsg.java` | Clean wrapper for `P2PeerMsg` with `detach()` for framework ownership handoff |
| `P2PeerConWsa.java` | TCP connection wrapper with `clientFactory`/`serviceFactory` static constructors |
| `P2PeerHub.java` | Hub wrapper: spawn/close/post, **the authentication block**, and **the receive sink** as a Panama upcall |
| `ArmResult.java` / `IdResult.java` | The two enums the ABI returns as `int` |
| `AbiCoverage.java` | Fails if any entry point in `abi-flat.manifest` has no generated binding |
| `SmokeTest*.java` | Five smoke tests — object model, `_u8`, the startup guard, the arm gate, the sink |
| `run_all.ps1` | Stage the DLLs, build, run all six, summarise by exit code |
| `pom.xml` | Maven build — Java 22+, `--enable-native-access=ALL-UNNAMED` wired in |

### To activate
1. Build `TargetCore.dll` from the TargetCore checkout — the wrapper is already one
   of its sources, so there is nothing to add to the project
2. Run `jextract` as shown in `README.md`, against the header in that checkout
   (replaces the hand-written stub with the real one)
3. `mvn compile` → run `SmokeTest` with `-Djava.library.path=<dll dir>`

---

## License

Copyright © 2026 Khrustal & Mann, MELBOURNE, VICTORIA, AUSTRALIA, 3000.

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for the full
text and [NOTICE](../NOTICE) for what it does and does not cover.
