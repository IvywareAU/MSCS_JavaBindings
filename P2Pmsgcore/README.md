# P2Pmsgcore – Java Panama FFI Bindings

Exposes P2Pmsgcore's flat `extern "C"` surface to Java through the Panama Foreign
Function Interface (Java 23+), with hand-written wrappers over the generated layer.

| C++ class      | Java wrapper          | Purpose                            |
|----------------|-----------------------|------------------------------------|
| `P2Paddr`      | `P2PAddr`             | P2P virtual-network address string |
| `P2PeerMsg`    | `P2PMsg`              | Addressed message with payload     |
| `P2PeerConWsa` | `P2PeerConWsa`        | Async TCP/IP connection (WSA/IOCP) |
| `P2PeerHub`    | `P2PeerHub`           | Message routing hub, authentication, receive sink |
| —              | `P2Pmsgcore`          | Process lifecycle (`startup` / `cleanup`) |
| —              | `ArmResult`, `IdResult` | The two result enums the ABI returns as `int` |

**Status: 6/6**, re-measured 2026-09-08 against `TargetCore.dll` 3.0.0.0
(**101** flat C entry points, all 101 covered) on a single **JDK 25.0.4.1** whose
bundled msvcp140 is 14.40 — see *The two requirements pinch* below, which one JDK
now satisfies on its own. Run them with `.\run_all.ps1`.

> **Renamed 2026-09-08.** The library this binds was `TargetCore.dll` and is now
> `TargetCore.dll`; `p2pmsgcore_startup` / `_cleanup` became `targetcore_startup` /
> `_cleanup`, and eight trust and link-policy entry points arrived with it. The Java
> package names are unchanged (`com.p2pmsgcore`), as is the generated class name
> `P2Pmsgcore_c`, so callers see the rename only in those two method names.

---

## Read this first: the bindings are generated, and generated things go stale

From 2026-08-14 to 2026-08-20 the committed bindings covered **72 of the library's
83** entry points. Nothing failed. `mvn compile` was green and the smoke test
printed "passed" — while printing `Hub created: false` three lines above it.

The eleven that were missing were not a random tail:

```
p2peerhub_require_auth        p2peerhub_auth_arm
p2peerhub_is_auth_required    p2peerhub_auth_arm_text
p2peerhub_set_identity        p2peerhub_auth_allow_list_path
p2peerhub_set_allow_list      p2peerhub_set_sink
p2peerhub_reload_allow_list   p2peerhub_set_sink_u8
p2peerhub_provision_auth
```

Nine are the whole authentication block and two are the receive sink. Because
authentication became **required by default** on 2026-08-18, and a hub that cannot
enforce it does not start, a Java caller holding those bindings could not start a
hub *at all* — and could not turn the requirement off either, because
`require_auth` was one of the missing eleven.

`AbiCoverage` now exists so that this fails loudly instead of quietly. It reads
`abi-flat.manifest` out of the P2Pmsgcore checkout — the file VERSIONING.md §2
names as *the* enumeration of the covered surface — and refuses to pass if any
promised name has no generated binding. It is deliberately not a copy of that list
kept here: a copy is precisely what drifted last time.

---

## Read this second: the JVM brings its own C++ runtime, and it is usually too old

**Symptom:** the first call into the library dies with `EXCEPTION_ACCESS_VIOLATION
(0xC0000005)` inside `msvcp140.dll`, on the JVM's own stack, with no diagnostic of
any kind. `hs_err_pid*.log` blames `msvcp140.dll+0x12f58`.

**Cause:** a JDK ships its own `msvcp140.dll` / `VCRUNTIME140.dll` in `bin\`, and
`jvm.dll` imports them, so they are loaded before any of your code runs. Windows
resolves a DLL's imports against whatever module of that base name is *already*
loaded — so `TargetCore.dll` gets the JDK's copy, not the system's, and no `PATH`,
`java.library.path` or load order can change that. P2Pmsgcore is built with MSVC
14.4x and uses `std::mutex`, whose constructor became `constexpr` in toolset
**14.40** (VS 2022 17.10); against an older runtime it dereferences null.

**Measured** on 2026-08-20 — same DLL, same non-MFC host, the pre-loaded runtime
the only variable:

| runtime in the process | first call into the library |
|---|---|
| 14.36.32532 (Oracle JDK 22 / 23, OpenJDK 22 bundle) | **0xC0000005** |
| 14.40.33810 (Temurin 21 bundle) | ok |
| 14.44.35211 (system redist) | ok |
| 14.42.34438 (**jextract 25's own bundled runtime**) | ok |

**What to do:** run on a JDK 23+ whose `bin\msvcp140.dll` is **14.40 or newer**.
`run_all.ps1` checks this before it runs anything and stops with an explanation
rather than letting the JVM crash. If your JDK is older, the supported answers are
to use a different JDK, or to build P2Pmsgcore with an older toolset — replacing
files inside a JDK is neither.

**The two requirements pinch, and on 2026-08-21 no ordinary JDK on the test
machine satisfied both.** Compiling needs **23+** (jextract 25 emits
`findOrThrow`); running needs a bundled CRT of **14.40+**. Every JDK 22/23
installed there bundles 14.36, and the one bundling 14.40 is a JDK 21. The way
out is that they need not be the same JDK: **compile with a 23+, run with
anything 23+ whose CRT is new enough.** `jextract 25's own runtime` is both —
JDK 25 with msvcp140 **14.42** — and was the run JDK through 2026-09-07:

```powershell
cd java; mvn -q compile          # any JDK 23+
cd ..
.\run_all.ps1 -SkipBuild -Java C:\path\to\jextract-25\runtime\bin\java.exe
```

`-SkipBuild` is required in that split, and the reason is worth knowing before
you hit it: `run_all.ps1` points `JAVA_HOME` at the **run** JDK before calling
Maven, and jextract's runtime is a trimmed `jlink` image with **no `javac`**, so
letting it compile fails with nothing but *"mvn compile failed"*.

**Since 2026-09-08 that split is no longer needed here.** A single JDK 25.0.4.1
bundling msvcp140 **14.40** satisfies both requirements, so `run_all.ps1` compiles
and runs on the one JVM and `-SkipBuild` can be dropped. The split above is kept
because it is still the answer on any machine whose only 23+ JDK bundles 14.36.

---

## Prerequisites

| Tool        | Version  | Notes                              |
|-------------|----------|------------------------------------|
| Java        | **23+**  | `java.lang.foreign` left preview in 22, but the floor here is **23** since 2026-08-21: jextract 25 emits `SymbolLookup.findOrThrow()`, which is a 23 method. The floor is set by the generator, not by anything these bindings need. Verified on **23.0.2**. And see the runtime note above. |
| jextract    | 25       | Only needed to regenerate; the output is committed. **Pinned rather than a floor**: jextract 22 emitted `find(...).orElseThrow()` and 25 emits `findOrThrow()`, so the tool version decides the Java floor above. Regenerating with an older one lowers it again, and that is a decision rather than an accident. |
| Maven       | 3.9+     | `mvn compile` |
| MSVC        | 2022     | builds the DLL |
| A TargetCore checkout | existing | supplies the C wrapper sources, the header jextract reads, **and** the ABI manifest `AbiCoverage` checks against |

Throughout, `<TargetCore>` means the root of that checkout — the directory holding
`TargetCore(2022).vcxproj` and `TargetCore_c.h`.

---

## Quick start

```powershell
.\run_all.ps1                      # stage DLLs, mvn compile, run all six tests
.\run_all.ps1 -Config Debug
.\run_all.ps1 -Java "C:\path\to\jdk\bin\java.exe"
.\run_all.ps1 -LibDir D:\build\TargetCore\Release
```

It stages `TargetCore.dll` **and** the `Msgcore.dll` from the same build into
`bin\`, puts that directory on `PATH`, and reports each test by exit code
(0 PASS, 1 FAIL, 2 SETUP, 3 INCONCLUSIVE).

Both DLLs must come from the same build. A mismatched pair produces the least
helpful error in this toolchain — `Cannot open library: TargetCore.dll`, naming
the DLL that *was* found and saying nothing about the dependency that was not.

---

## Step 1 – Build the DLL

**The C wrapper lives in TargetCore, not here.** `TargetCore_c.h`,
`TargetCore_c.cpp` and `TargetCore_c_u8.cpp` are ordinary sources of that project,
listed in both `TargetCore(2022).vcxproj` and its `CMakeLists.txt`, so they are
compiled into `TargetCore.dll` and `libtargetcore.so` by an ordinary build.

> This tree used to carry its own copy under `native\`, and Step 1 used to be
> "copy these three files into TargetCore". That copy was **deleted on
> 2026-08-14**: an ABI definition duplicated across two repositories drifts, and
> this one already had — the copy here sat several fixes behind the library it
> described, including the handle registry every entry point now depends on.

In the project's **Preprocessor Definitions** make sure `TargetCore_EXPORTS` is
defined (it already is for the DLL target; the static `DebugLib`/`ReleaseLib`
configurations define `P2Pmsgcore_STATIC` instead, which expands `P2PC_API` to
nothing — those cannot be loaded by Panama, which needs a shared library).

```
cmake --build build-win-cmake --config Release --target p2pmsgcore
```

Confirm the surface really is exported — a DLL that built fine still exports
nothing if `TargetCore_EXPORTS` was missing:

```
python <TargetCore>\.github\ci\check_abi_exports.py ^
       --library build-win-cmake\TargetCore\Release\TargetCore.dll ^
       --manifest <TargetCore>\.github\ci\abi-flat.manifest ^
       --dumpbin  "<VS>\VC\Tools\MSVC\<ver>\bin\Hostx64\x64\dumpbin.exe"
```

---

## Step 2 – Install jextract

Prebuilt binaries: https://jdk.java.net/jextract/. Verify with
`jextract --version`. Only needed if you are regenerating.

---

## Step 3 – Regenerate the bindings

Run bare, jextract also emits every declaration reachable through
`<stdint.h>`/`<wchar.h>` (~40 noise files: `FILE`, `stat`, `tm`, setjmp buffers).
Filter the includes to this header and set the class name:

```bat
cd <TargetCore>

:: 1) dump every include option, then keep only the ones from THIS header
jextract --dump-includes jx_dump.txt TargetCore_c.h
findstr /R "^--include-" jx_dump.txt | findstr "TargetCore_c.h" > jx_filter.args

:: 2) generate, filtered, with the class name the wrappers expect
jextract ^
  --output <bindings>\java\src\main\java ^
  --target-package com.p2pmsgcore.native_ ^
  --header-class-name P2Pmsgcore_c ^
  --library TargetCore ^
  @jx_filter.args ^
  TargetCore_c.h
```

The filter should come out at **107 lines: 101 `--include-function` and 6
`--include-typedef`.** If the function count is not 101, the header and this
document have diverged — check the manifest. It was 83 until 2026-08-21, when
the revocation and sealing defaults added ten entry points (TargetCore
ProductionPlan.md Stage 3 steps 19 and 20), and 93 until 2026-09-08, when the
rename brought `targetcore_startup` / `_cleanup` and eight trust and link-policy
entry points.

That writes four files into `...\native_\`:

| file | what it is |
|---|---|
| `P2Pmsgcore_c.java` | one `MethodHandle` + typed static method per C function |
| `P2Pmsgcore_c$shared.java` | the layout constants split out |
| `P2PeerHubSinkFnU8.java` | upcall-stub factory for the UTF-8 receive-sink typedef |
| `P2PeerHubSinkFn.java` | the same for the `wchar_t` sink |

Then run `AbiCoverage` — it is the check that the regeneration was complete.

> **No-arg functions:** the header declares `p2paddr_create(void)` /
> `p2peermsg_create(void)` with an explicit `void`. Empty `()` in C means an
> *unprototyped* function, which jextract emits as a variadic invoker class rather
> than a plain no-arg method — keep the `void`.

> **JDK 22 shim:** jextract 25's output calls `SymbolLookup.findOrThrow`, added
> in **JDK 23** — measured rather than read: this project compiles on JDK 23.0.2 with
> the pom at `release 23`. *(An earlier draft of this note said 24. It is 23, and 22 is
> the only version that needs the shim.)* To build on **22**, rewrite it after generating:
> ```powershell
> Get-ChildItem ..\java\src\main\java\com\p2pmsgcore\native_\*.java | ForEach-Object {
>   (Get-Content $_) -replace 'SYMBOL_LOOKUP\.findOrThrow\((\"[^\"]+\")\)',
>                             'SYMBOL_LOOKUP.find($1).orElseThrow()' | Set-Content $_
> }
> ```
> — or leave the pom at `release 23` and build with a matching JDK, which is what it does.

---

## Step 4 – Build and run

```
cd java
mvn compile
```

```
java --enable-native-access=ALL-UNNAMED -cp target\classes com.p2pmsgcore.SmokeTest
```

with the directory holding both DLLs **on `PATH`**.

> **`-Djava.library.path` is no longer enough**, and this changed under us.
> jextract 25 emits `SymbolLookup.libraryLookup(System.mapLibraryName("P2Pmsgcore"), …)`,
> which goes through the OS loader search — executable directory, System32, `PATH` —
> and does not consult `java.library.path` at all. A wrong path now produces
> `IllegalArgumentException: Cannot open library: TargetCore.dll` from a static
> initialiser. `run_all.ps1` sets `PATH` for you.

---

## Lifecycle — call startup before any hub

```java
P2Pmsgcore.startup(16);          // 16 = max hubs; throws if it fails
try {
    // ... create/spawn hubs, connections, post messages ...
} finally {
    P2Pmsgcore.cleanup();
}
```

`startup` initialises the shared hub/pump locks and the hub-manager table (the
native `StartupP2Pmsg`). **Skipping it used to crash hard** in native code on the
first `createHub`; it now fails cleanly, and `SmokeTestGuard` is the test that says
so. `P2PAddr` and `P2PMsg` are pure object model and do not need it.

**Once per process.** `cleanup()` is terminal — a second `startup()` after it does
not restore a working environment.

### Two threading rules the C header does not state

Both were measured here on 2026-08-20, and both are invisible from Java because
`p2peerhub_create_hub` catches the `P2Pevent` that explains them — deliberately,
since a C++ exception must not unwind across an `extern "C"` boundary — and returns
a bare `0`.

1. **One hub per thread.** `CreateP2PmsgHub` refuses to associate a second pump
   with a thread that already has one ("Single P2PmsgPump per thread context"), and
   closing and destroying the first hub does *not* release its thread. Three hubs
   created from the main thread give `true, false, false`; the same three created
   one per thread all succeed.

2. **`createHub()` OR `spawnHub()`, never both.** `SpawnHub` opens with
   `ASSERT(m_nHubID == 0)`, so the pair trips a Debug assertion — a modal dialog
   that stops the process — while Release tolerates it silently. This tree's own
   smoke test made exactly that call pair from the day it was written, and had only
   ever been run against Release. `spawnHub()` alone is the normal path: it gives
   the hub a thread of its own, which is also what rule 1 wants.

---

## Authentication

Auth is **required by default** since 2026-08-18 and a hub that cannot enforce it
does not start — `createHub()` and `spawnHub()` both refuse before a pump thread
exists, because the alternative is a hub that starts, refuses every peer, and looks
healthy from outside.

```java
try (P2PeerHub hub = new P2PeerHub("Mesh.Node")) {

    var prov = hub.provisionAuth("node.key");     // creates the key + node.key.pub
    System.out.println(prov.fingerprint());       // read this aloud to the operator

    hub.setAllowList("allow.txt");                // who this hub will accept

    ArmResult arm = hub.authArm();                // would it start? and why not?
    if (!arm.arms()) throw new IllegalStateException(arm.text());

    hub.spawnHub();
}
```

`provisionAuth` does **not** create the allow-list, and the hub will not arm until
one exists with at least one peer in it. Who to trust is not a thing a library can
supply, and one that wrote an empty allow-list would be answering that question
with "nobody" — which refuses every peer.

The migration for a trusted segment or an in-process router is one deliberate call:

```java
hub.requireAuth(false);       // ArmResult.NOT_REQUIRED - starts, and accepts anyone
```

`ArmResult.text()` is read back through `p2peerhub_auth_arm_text`, so a Java
diagnostic and the C++ one for the same state cannot disagree. `IdResult` has no
such entry point on the ABI, so those fourteen names are transcribed from
`P2PIdentityStore.h` and nothing checks that they still line up — see the class
javadoc.

---

## The receive sink

Everything else on this ABI is post-only. `setSink` is how an FFI consumer learns
that a message was **delivered** to its hub.

```java
hub.setSink((src, dst, msgID, data) -> {
    queue.add(new Frame(src, msgID, data));   // copy and return; do not block
    return true;                              // consumed
});
hub.spawnHub();
```

* It runs on the **hub's pump thread** — a thread the JVM never created, attached
  transparently by Panama on the way into the upcall.
* `dst` is the hub's **full** address, read from the hub rather than off the message
  (`hub.address()` returns the leaf).
* `data` is copied out before the handler sees it; the native bytes are valid only
  for the duration of the call.
* Returning `false` hands the message back to the framework, which for a peer with
  no compiled message map means an undeliverable bounce per message — and that
  flood is what wedges `closeHub()`.
* A handler that throws is caught and reported, and the message counts as consumed.
  An exception escaping an upcall stub does not unwind into C++; it takes the whole
  JVM down.
* The arena holding the stub is `Arena.ofShared()`, not `ofConfined()`: a confined
  arena throws `WrongThreadException` when touched from the pump thread, and that
  throw happens *inside* the upcall, where it is fatal rather than catchable.

Register before `spawnHub()` and clear after `closeHub()`. Swapping one live sink
for another is not safe against a running pump, so `setSink` refuses to replace one.

---

## String encoding

The library is built with `UNICODE`, so `TCHAR` is `wchar_t` — UTF-16LE on Windows,
UTF-32 on Linux. Each string-bearing C function therefore has a `*_u8` twin that
takes and returns **UTF-8 `char*`**, converting at the boundary, and those are
ABI-identical on both platforms.

| `wchar_t` method | UTF-8 (portable) method |
|---|---|
| `new P2PAddr(s)` | `P2PAddr.ofUtf8(s)` |
| `addr.name()` / `isChild` / `isRable` | `nameUtf8()` / `isChildUtf8` / `isRableUtf8` |
| `new P2PMsg(...)` | `P2PMsg.ofUtf8(...)` / `ofMsgIdUtf8(id)` |
| `msg.source/destination/name` | `sourceUtf8/destinationUtf8/nameUtf8` |
| `msg.responseFactory/redirectFactory` | `responseFactoryUtf8/redirectFactoryUtf8` |
| `P2PeerConWsa.clientFactory/serviceFactory` | `clientFactoryUtf8/serviceFactoryUtf8` |
| `con.address()` | `con.addressUtf8()` |
| `new P2PeerHub(a)` / `createHub` / `connectionExists` / `address` | `P2PeerHub.ofUtf8(a)` / `createHubUtf8` / `connectionExistsUtf8` / `addressUtf8` |

Prefer the `_u8` methods for encoding-portable behaviour. `SmokeTestU8` exercises
them with multibyte UTF-8 (é / € / astral 🚀) and mirrors the native `p2p_u8_smoke`
CTest.

Paths — identity, allow-list — are UTF-8 on both platforms and have no wide twin.

**Sizes are `uint32_t`, and used not to be.** They were `unsigned short` until
2026-08-14, which put a silent 64 KB wrap in front of every FFI caller. The
wrappers here clamped payloads to `Short.MAX_VALUE`, reintroducing that truncation
one layer up in Java where the C side could no longer see it; they now pass the
length the caller passed. The cap is still `MAX_P2Psize` (32768) and an over-cap
message is refused downstream — which surfaces as a null handle, not a short
payload.

**`isChild` takes the candidate child.** `addr.isChild(x)` asks whether *x* is a
child of *addr*, not the other way round. This javadoc had it backwards until
2026-08-20; an inverted hierarchy predicate reads as a permission bug much later.

---

## Tests

| Test | What it proves | Exit codes |
|---|---|---|
| `AbiCoverage` | every entry point in `abi-flat.manifest` has a generated binding | 0/1/2 |
| `SmokeTest` | every wrapper class end to end, with assertions | 0/1 |
| `SmokeTestU8` | the `_u8` surface round-trips multibyte UTF-8 | 0/1 |
| `SmokeTestGuard` | `createHub` without `startup` fails cleanly instead of crashing in ntdll | 0/1 |
| `SmokeTestAuth` | the arm gate: unprovisioned refuses, `requireAuth(false)` runs, provisioning from Java arms it, and `spawnHub` is gated too | 0/1 |
| `SmokeTestSink` | a delivered message reaches Java, on the pump thread, with its four fields intact, and a throwing handler does not kill the JVM | 0/1/3 |

`SmokeTest` used to print "Smoke test passed." unconditionally. On 2026-08-20 it
was doing that while printing `Hub created: false` — the hub had stopped starting
two days earlier and nothing was checking. Every line of it is now an assertion.

---

## File layout

The C wrapper is **not** in this repository — it is part of P2Pmsgcore:

```
<TargetCore>\
├── TargetCore_c.h               <- extern "C" wrapper header (jextract reads this)
├── TargetCore_c.cpp
├── TargetCore_c_u8.cpp          <- the _u8 entry points
└── .github\ci\abi-flat.manifest <- what AbiCoverage checks against
```

```
MSCS_JavaBindings\P2Pmsgcore\
├── run_all.ps1                  stage + build + run + summarise
├── bin\                         staged DLLs (generated; not committed)
├── logs\                        per-test output (generated; not committed)
└── java\
    ├── pom.xml
    └── src\main\java\com\p2pmsgcore\
        ├── native_\             GENERATED by jextract - do not hand-edit
        ├── NativeStrings.java   wchar_t / UTF-8 <-> String
        ├── P2Pmsgcore.java      startup / cleanup
        ├── P2PAddr.java  P2PMsg.java  P2PeerConWsa.java  P2PeerHub.java
        ├── ArmResult.java  IdResult.java
        └── SmokeTest*.java  AbiCoverage.java
```

## Ownership rules

- `P2PeerHub.postConnection(con, pump)` — the hub takes ownership of `con`; the
  wrapper calls `con.detach()` for you.
- `P2PeerHub.postMessage(msg)` / `P2PeerConWsa.postMessage(msg)` — the framework
  takes ownership of `msg`; the wrapper calls `msg.detach()` for you.
- Everything else is RAII: use try-with-resources.

## License

Copyright © 2026 Khrustal & Mann, MELBOURNE, VICTORIA, AUSTRALIA, 3000.

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for the
full text and [NOTICE](../NOTICE) for what it does and does not cover — in
particular the jextract output under `native_\`, which carries no per-file header
because it is replaced wholesale every time the bindings are regenerated.
