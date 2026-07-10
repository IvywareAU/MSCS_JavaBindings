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

## Step 3 — Run jextract (optional — skip if using the hand-written stub)

The file `java/src/main/java/com/msgcore/native_/Msgcore_c.java` is a
**hand-written stub** that matches what jextract would generate.  You can
regenerate it with the real jextract once the DLL is built:

```powershell
jextract `
  --output java\src\main\java `
  --target-package com.msgcore.native_ `
  --library Msgcore `
  C:\_Dev\_ClaudeCode\MSCS\Msgcore\Msgcore_c.h
```

> **Note:** jextract needs a compilable header.  The header includes
> Windows types via `<windows.h>` implicitly through Msgcore.h.
> You may need to pass `--include-dir` pointing to the Windows SDK headers.
> Alternatively, keep the hand-written stub (it is functionally equivalent).

---

## Step 4 — Build the Java project

```powershell
cd C:\_Dev\_ClaudeCode\MSCS_JavaBimdings\Msgcore\java
mvn package
```

This produces `target/msgcore-java-1.0.0.jar`.

---

## Step 5 — Run the smoke test

```powershell
java `
  --enable-native-access=ALL-UNNAMED `
  -Djava.library.path=C:\_Dev\_ClaudeCode\MSCS\x64\Release `
  -cp target\msgcore-java-1.0.0.jar `
  com.msgcore.MsgcoreExample
```

Expected output:
```
Manager created, valid=true
Declared Age=42
Declared Name=Alice
Declared Score=98.6

All items:
  Age           type=5   value=42
  Name          type=26  value=Alice
  Score         type=10  value=98.6

Saved to C:\...\msgcore_test.p2p: true

Reloaded from C:\...\msgcore_test.p2p
Age after reload = 42
Name after reload = Alice

Native call succeeded!
```

---

## File Layout

```
C:\_Dev\_ClaudeCode\MSCS\Msgcore\
├── Msgcore_c.h          ← C wrapper header  (new — add to VS project)
├── Msgcore_c.cpp        ← C wrapper impl    (new — add to VS project)
└── ... (existing C++ source files)

C:\_Dev\_ClaudeCode\MSCS_JavaBimdings\Msgcore\
├── BUILD.md             ← this file
└── java\
    ├── pom.xml
    └── src\main\java\com\msgcore\
        ├── native_\
        │   └── Msgcore_c.java      ← Panama binding layer (jextract output / stub)
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
| `const wchar_t*`   | `MemorySegment` / `String` | UTF-16LE; helpers in `Msgcore_c.toWStr` / `fromWStr` |
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
| `UnsatisfiedLinkError: no Msgcore in java.library.path` | Set `-Djava.library.path` to the directory containing `Msgcore.dll` |
| `UnsatisfiedLinkError: unresolved symbol msgcore_mgr_create` | Rebuild the DLL with `Msgcore_c.cpp` included; check `dumpbin /exports Msgcore.dll` |
| Crash on first call | MFC requires the app to call `AfxWinInit`; if testing outside an MFC app, add an `AfxWinInit` call or link against the shared MFC DLL |
| Wide string garbage | Ensure the JVM and DLL agree on `wchar_t` being 2 bytes (guaranteed on Windows) |
