# MSCS_JavaBindings

Java bindings for the MSCS native libraries, built on the **Panama Foreign Function
Interface** — `jextract`-generated call layers with hand-written wrappers on top, and
no JNI anywhere.

Two independent binding projects live here, one per native library:

| Project | Binds | Java package | Status |
|---|---|---|---|
| [`Targetcore/`](Targetcore/README.md) | `Targetcore.dll` — P2P messaging: addresses, messages, connections, hubs, authentication | `com.targetcore` | 101/101 entry points bound; 6/6 tests |
| [`Msgcore/`](Msgcore/Readme.md) | `Msgcore.dll` — the persistent message store: fields, lists, vectors, attributes, cursors | `com.msgcore` | 282/282 entry points bound, 119 with friendly wrappers; 4/4 tests |

They share no code. Each has its own `pom.xml`, its own `run_all.ps1`, and its own
README with the full build, regeneration and API story — start there. What follows is
only what is true of both.

---

## Quick start

```powershell
cd Targetcore ; .\run_all.ps1        # stage DLLs, mvn compile, run every test
cd Msgcore    ; .\run_all.ps1
```

`run_all.ps1` stages the native DLLs into `bin\`, puts that directory on `PATH`,
compiles, runs each test, and reports it by exit code (0 PASS, 1 FAIL, 2 SETUP,
3 INCONCLUSIVE). Per-test output lands in `logs\`.

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| Java | **23+** for Targetcore, 22+ for Msgcore | The floor is set by the generator, not by the bindings: jextract 25 emits `SymbolLookup.findOrThrow()`, a JDK 23 method. See the Targetcore README for the JDK 22 shim. |
| Maven | 3.9+ | `mvn compile` |
| jextract | 25 | Only to regenerate — the output is committed |
| MSVC | 2022 | Builds the DLLs |
| A checkout of the native library | existing | Supplies the C wrapper sources and the header jextract reads |

**This repository contains no binaries.** `Targetcore.dll`, `Msgcore.dll` and
`P2PmsgFacade.dll` are built from their own repositories and staged at run time.

---

## Two things that bite in both projects

**The JVM brings its own C++ runtime, and it is usually too old.** A JDK ships
`msvcp140.dll` in its `bin\`, and `jvm.dll` imports it, so it is loaded before any of
your code runs — Windows then resolves the native library's imports against *that*
copy, and no `PATH` or load order can change it. Both libraries use `std::mutex`,
whose constructor became `constexpr` in toolset **14.40**; against 14.36 the first
call into the library dies with `EXCEPTION_ACCESS_VIOLATION (0xC0000005)` and no
diagnostic. Run on a JDK whose bundled `msvcp140.dll` is **14.40 or newer** —
`run_all.ps1` checks this and stops with an explanation rather than letting the JVM
crash.

**Generated bindings go stale silently.** Both projects have shipped bindings that
compiled cleanly while missing entry points the caller needed — Targetcore's covered
72 of 83 for six days, omitting the entire authentication block, and Msgcore's
`native_` layer was a hand-written stub for months rather than jextract output. Each
project now has an `AbiCoverage` test that reads the library's own ABI enumeration and
fails if a promised name has no generated binding. Run it after every regeneration;
it is the check that the regeneration was complete.

---

## Layout

```
MSCS_JavaBindings\
├── Targetcore\
│   ├── README.md                the full story: build, regenerate, lifecycle, auth, sinks
│   ├── Readme2.md               file-by-file inventory of what was produced
│   ├── run_all.ps1
│   └── java\  pom.xml, src\main\java\com\targetcore\  (native_\ is jextract output)
├── Msgcore\
│   ├── Readme.md                the full story
│   ├── BUILD.md                 step-by-step build guide
│   ├── run_all.ps1
│   └── java\  pom.xml, src\main\java\com\msgcore\    (native_\ is jextract output)
├── LICENSE
└── NOTICE
```

Everything under `native_\` is **jextract output and is replaced wholesale on every
regeneration** — do not hand-edit it. The one documented exception is the JDK 22/23
shim described in each project's README.

## License

Copyright © 2026 Khrustal & Mann, MELBOURNE, VICTORIA, AUSTRALIA, 3000.

Licensed under the Apache License, Version 2.0 — see [LICENSE](LICENSE) for the full
text and [NOTICE](NOTICE) for what it does and does not cover. In particular: a
*distribution* assembled from this repository is a different thing from the repository
itself. Anything shipping the native libraries alongside these bindings also ships
Microsoft's Visual C++ redistributable components, which are **not** under Apache-2.0
and must be labelled separately — and the debug runtimes may not be redistributed at
all. NOTICE spells out the split.
