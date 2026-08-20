# Msgcore Java Panama Bindings — Build Guide

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Visual Studio | 2022 (v143) | With C++ and MFC components |
| Java JDK | 22+ | `java --version` |
| jextract | 22+ | See install notes below |
| Maven | 3.9+ | `mvn --version` |

---

## Step 1 — Add the C wrapper to the VS project

The C wrapper files were written to:

```
C:\_Dev\_ClaudeCode\MSCS\Msgcore\Msgcore_c.h
C:\_Dev\_ClaudeCode\MSCS\Msgcore\Msgcore_c.cpp
```

In Visual Studio:
1. Right-click the `Msgcore` project → **Add → Existing Item**
2. Add `Msgcore_c.h` and `Msgcore_c.cpp`
3. Verify the project is configured as a **DLL** (`Configuration Properties → General → Configuration Type = Dynamic Library (.dll)`)
4. Verify `Msgcore_EXPORTS` is defined (`C/C++ → Preprocessor → Preprocessor Definitions`)
5. Verify the build is **Unicode** (`Character Set = Use Unicode Character Set`)

---

## Step 2 — Build the DLL

```
Build → Build Solution   (or Ctrl+Shift+B)
```

This produces:
```
x64\Release\Msgcore.dll     (64-bit)
x64\Release\Msgcore.lib
```

---

## Step 3 — Run jextract. Not optional.

`java/src/main/java/com/msgcore/native_/Msgcore_c.java` **is** jextract output as
of 2026-08-20. It was a hand-written stub before that — one covering 108 of the
header's 282 entry points — and this step used to say "optional, skip if using the
hand-written stub". That is how the bindings came to be missing three quarters of
the library, including every `_u8` twin and the whole live-tree access group
without which writes from Java go nowhere. There is no stub to fall back on now.

Run bare, jextract also emits every declaration reachable through the system
headers (~40 noise files: `FILE`, `stat`, `tm`, setjmp buffers). Filter the
includes to this header and set the class name:

```bat
cd C:\_Dev\_ClaudeCode\MSCS\Msgcore

:: 1) dump every include option, then keep only the ones from THIS header
jextract --dump-includes jx_dump.txt Msgcore_c.h
findstr /R "^--include-" jx_dump.txt | findstr "Msgcore_c.h" > jx_filter.args

:: 2) generate, filtered, with the class name the wrappers expect
jextract ^
  --output <bindings>\Msgcore\java\src\main\java ^
  --target-package com.msgcore.native_ ^
  --header-class-name Msgcore_c ^
  --library Msgcore ^
  @jx_filter.args ^
  Msgcore_c.h
```

The filter should come out at **318 lines: 282 `--include-function`, 23
`--include-constant` and 13 `--include-typedef`.** If the function count is not
282, the header and this document have diverged — run `AbiCoverage`, which exists
to tell you exactly which names are missing.

Six files are written: `Msgcore_c.java`, `Msgcore_c$shared.java`, and one
upcall-stub factory per callback typedef (`msgcore_pagein_fn`,
`msgcore_pageout_fn`, `msgcore_populate_fn`, `msgcore_trigger_fn`).

> **Nothing hand-written goes in that directory.** The string helpers used to live
> inside `Msgcore_c.java`, so regenerating deleted them and broke the build — which
> is most of why nobody regenerated. They are in `com.msgcore.NativeStrings` now.

> **JDK 22/23 shim:** jextract 25's output calls `SymbolLookup.findOrThrow`, added
> in JDK 24. To build on 22/23, rewrite it after generating:
> ```powershell
> Get-ChildItem ..\java\src\main\java\com\msgcore\native_\*.java | ForEach-Object {
>   (Get-Content $_) -replace 'SYMBOL_LOOKUP\.findOrThrow\((\"[^\"]+\")\)',
>                             'SYMBOL_LOOKUP.find($1).orElseThrow()' | Set-Content $_
> }
> ```

> **Two declarations must keep their explicit `void`.** `msgcore_mgr_create(void)`
> and `msgcore_field_create(void)` said `()` until 2026-08-20. Empty parens in C
> mean an *unprototyped* function, which jextract emits as a variadic invoker class
> rather than a plain no-arg method — so the wrappers would not compile against
> real generated output. The fix is in the Msgcore header, and it changes no ABI.

---

## Step 4 — Build and run

```powershell
.\run_all.ps1                 # stage the DLL, mvn compile, run all four tests
.\run_all.ps1 -Config Debug
.\run_all.ps1 -Java "C:\path\to\jdk\bin\java.exe"
```

Or by hand:

```powershell
cd java
mvn compile
java --enable-native-access=ALL-UNNAMED -cp target\classes com.msgcore.SmokeTest
```

with the directory holding `Msgcore.dll` **on `PATH`**.

> **`-Djava.library.path` no longer locates the DLL**, and this changed under us.
> jextract 25 emits `SymbolLookup.libraryLookup(System.mapLibraryName("Msgcore"), …)`,
> which goes through the OS loader search — executable directory, System32, `PATH` —
> and does not consult `java.library.path` at all. `run_all.ps1` sets `PATH`.

> **The JVM's own C++ runtime has to be new enough.** A JDK ships `msvcp140.dll` in
> `bin\` and `jvm.dll` loads it before your code runs, so Windows resolves
> `Msgcore.dll`'s imports against that copy. Measured 2026-08-20: **14.36 →
> `0xC0000005` on the first call, with no diagnostic; 14.40 → ok; 14.44 → ok.**
> `run_all.ps1` refuses to run rather than let the JVM crash. See Readme.md.

Expected summary:

```
AbiCoverage        PASS
SmokeTest          PASS
SmokeTestU8        PASS
MsgcoreExample     PASS
4 of 4 passed
```

---

## File Layout

```
C:\_Dev\_ClaudeCode\MSCS\Msgcore\
├── Msgcore_c.h          ← C wrapper header  (new — add to VS project)
├── Msgcore_c.cpp        ← C wrapper impl    (new — add to VS project)
└── ... (existing C++ source files)

C:\_Dev\_ClaudeCode\MSCS_JavaBindings\Msgcore\
├── BUILD.md             ← this file
└── java\
    ├── pom.xml
    └── src\main\java\com\msgcore\
        ├── native_\               ← GENERATED by jextract - do not hand-edit
        │   ├── Msgcore_c.java      ← one MethodHandle + method per C function
        │   ├── Msgcore_c$shared.java
        │   └── msgcore_*_fn.java   ← upcall-stub factories for the callback typedefs
        ├── NativeStrings.java      ← wchar_t / UTF-8 <-> String (NOT in native_)
        ├── AbiCoverage.java        ← fails if a declared entry point has no binding
        ├── SmokeTest.java          ← a write from Java reaches the tree
        ├── SmokeTestU8.java        ← the _u8 surface, through a save/reload
        ├── MsgMgr.java             ← P2PmsgMgr wrapper
        ├── MsgField.java           ← P3PmsgField / P3PmsgItem wrapper
        ├── MsgList.java            ← P3PmsgList wrapper
        ├── MsgVect.java            ← P3PmsgVect wrapper
        ├── MsgAttr.java            ← P3PmsgAttr wrapper
        ├── MsgDesc.java            ← P3PmsgDesc wrapper
        ├── MsgCurs.java            ← P3PmsgCurs wrapper (Iterable<MsgField>)
        ├── MsgAddrMode.java        ← VBLock_Addr* constants
        ├── MsgDataType.java        ← VBLockData_* type constants
        └── MsgcoreExample.java     ← smoke test / usage example
```

---

## Type Mapping Reference

| C / C++ type       | Java type         | Notes |
|--------------------|-------------------|-------|
| `void*` handle     | `MemorySegment`   | Opaque pointer |
| `int`              | `int`             | |
| `long long`        | `long`            | INT64 |
| `double`           | `double`          | |
| `unsigned char`    | `byte` (unsigned via `Byte.toUnsignedInt`) | |
| `const wchar_t*`   | `MemorySegment` / `String` | UTF-16LE; helpers in `NativeStrings.toWStr` / `fromWStr` |
| `const char*` (`_u8`) | `MemorySegment` / `String` | UTF-8, ABI-identical on Windows and Linux; `NativeStrings.toU8` / `fromU8`. **Prefer these.** |
| `BOOL` (int)       | `boolean` (wrapped) | 0 = false, non-zero = true |

---

## jextract Install (Windows)

1. Download from https://jdk.java.net/jextract/
2. Extract to e.g. `C:\Tools\jextract-22`
3. Add `C:\Tools\jextract-22\bin` to `PATH`
4. Verify: `jextract --version`

---

## Troubleshooting

| Symptom | Fix |
|---------|-----|
| `IllegalArgumentException: Cannot open library: Msgcore.dll` | Put the directory holding `Msgcore.dll` on **`PATH`**. `java.library.path` is not consulted - see Step 4 |
| `EXCEPTION_ACCESS_VIOLATION` in `msvcp140.dll` on the first call, no message | The JDK bundles a C++ runtime older than 14.40. Use a newer JDK - see Step 4 |
| `Target offset 0 is incompatible with alignment constraint 2` | A wide string at an odd address. Read it with `JAVA_SHORT_UNALIGNED`; `NativeStrings` does |
| A declare from Java appears to do nothing | It went through a **detached** handle (`asField` / `selectItem`). Use `mgr.root()` / `field.child()` - see Readme.md |
| `UnsatisfiedLinkError: unresolved symbol msgcore_mgr_create` | Rebuild the DLL with `Msgcore_c.cpp` included; check `dumpbin /exports Msgcore.dll` |
| Crash on first call | MFC requires the app to call `AfxWinInit`; if testing outside an MFC app, add an `AfxWinInit` call or link against the shared MFC DLL |
| Wide string garbage | Ensure the JVM and DLL agree on `wchar_t` being 2 bytes (guaranteed on Windows) |

---

## License

Copyright © 2026 Khrustal & Mann, MELBOURNE, VICTORIA, AUSTRALIA, 3000.

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for the full
text and [NOTICE](../NOTICE) for what it does and does not cover.
