# P2Pmsgcore – Java Panama FFI Bindings

Exposes the four core C++ classes to Java using the Panama Foreign Function Interface (Java 22+).

| C++ class      | Java wrapper          | Purpose                            |
|----------------|-----------------------|------------------------------------|
| `P2Paddr`      | `P2PAddr`             | P2P virtual-network address string |
| `P2PeerMsg`    | `P2PMsg`              | Addressed message with payload     |
| `P2PeerConWsa` | `P2PeerConWsa`        | Async TCP/IP connection (WSA/IOCP) |
| `P2PeerHub`    | `P2PeerHub`           | Message routing hub                |

---

## Prerequisites

| Tool        | Version  | Notes                              |
|-------------|----------|------------------------------------|
| Java        | 22+      | `java --version`                   |
| jextract    | 22+      | see below for install              |
| MSVC        | 2019+    | or compatible; builds the DLL      |
| Visual Studio solution | existing | `P2Pmsgcore(2022).vcxproj` |

---

## Step 1 – Add the C wrapper to the DLL build

Copy `native\P2Pmsgcore_c.h`, `native\P2Pmsgcore_c.cpp` **and
`native\P2Pmsgcore_c_u8.cpp`** into the `P2Pmsgcore` source directory
alongside the existing `.cpp` files, then add them to
`P2Pmsgcore(2022).vcxproj`. (`_u8.cpp` provides the UTF-8 entry points — see
[String encoding](#string-encoding); on the Linux port these three files are
already compiled into `libp2pmsgcore.so` by `P2Pmsgcore/CMakeLists.txt`.)

In the project's **Preprocessor Definitions** make sure
`P2Pmsgcore_EXPORTS` is defined (it already is for the DLL build target).

Rebuild the DLL:
```
msbuild P2Pmsgcore(2022).vcxproj /p:Configuration=Release /p:Platform=x64
```
The output `P2Pmsgcore.dll` goes into the build output folder (e.g.
`x64\Release\`).

---

## Step 2 – Install jextract (if not already installed)

Download the prebuilt binary for Java 22 from
https://jdk.java.net/jextract/ and add it to `PATH`.

Verify: `jextract --version`

---

## Step 3 – Run jextract

Modern jextract (22+) names the header class `<header>_h` and, run bare, also
emits every declaration reachable through `<stdint.h>`/`<wchar.h>` (~40 noise
files: `FILE`, `stat`, `tm`, setjmp buffers, …). To get a single clean class
named `P2Pmsgcore_c` (matching the wrappers), **filter the includes to this
header and set the class name**. From the `native\` directory:

```bat
:: 1) dump every include option, then keep only the ones from THIS header
jextract --dump-includes jx_dump.txt P2Pmsgcore_c.h
findstr /R "^--include-" jx_dump.txt | findstr "P2Pmsgcore_c.h" > jx_filter.args

:: 2) generate, filtered, with the class name the wrappers expect
jextract ^
  --output ..\java\src\main\java ^
  --target-package com.p2pmsgcore.native_ ^
  --header-class-name P2Pmsgcore_c ^
  --library P2Pmsgcore ^
  @jx_filter.args ^
  P2Pmsgcore_c.h
```

This **replaces** `...\native_\P2Pmsgcore_c.java` (+ a `P2Pmsgcore_c$shared.java`
split class) with the authoritative generated version — all 70 functions incl.
the 20 `_u8` twins.

> **Note (no-arg functions):** the header declares `p2paddr_create(void)` /
> `p2peermsg_create(void)` with an explicit `void`. Empty `()` in C means an
> *unprototyped* function, which jextract emits as a variadic invoker class
> rather than a plain no-arg method — keep the `void`.

> **JDK-version note:** jextract 25's output calls `SymbolLookup.findOrThrow`,
> added in **JDK 24**. To build on **JDK 22/23**, rewrite it to the equivalent
> `find(...).orElseThrow()` after generating:
> ```bat
> powershell -Command "(Get-Content ..\java\src\main\java\com\p2pmsgcore\native_\P2Pmsgcore_c.java) -replace 'SYMBOL_LOOKUP\.findOrThrow\((\"[^\"]+\")\)', 'SYMBOL_LOOKUP.find($1).orElseThrow()' | Set-Content ..\java\src\main\java\com\p2pmsgcore\native_\P2Pmsgcore_c.java"
> ```
> (do the same for `P2Pmsgcore_c$shared.java`) — or bump the pom to `release 24`+
> and build with a matching JDK. Verified: filtered regen + this shim compiles on
> JDK 22 and `SmokeTestU8` passes against the DLL.

---

## Step 4 – Build the Java project

```bat
cd java
mvn compile
```

---

## Step 5 – Run the smoke test

```bat
java --enable-native-access=ALL-UNNAMED ^
     -Djava.library.path=..\native\build ^
     -cp target\classes ^
     com.p2pmsgcore.SmokeTest
```

Expected output:
```
P2PAddr name   : TestHub.Node1
P2PAddr isNull : false
P2PAddr isEmpty: false
P2PMsg name  : Test.Greeting
P2PMsg src   : Hub1
P2PMsg dst   : Hub2
P2PMsg size  : 9
P2PMsg data  : Hello P2P
Hub created: true
Hub address: SmokeHub
Hub id     : <number>
Con mode (before post): 2
Smoke test passed.
```

---

## File layout

```
MSCS_JavaBindings\P2Pmsgcore\
├── native\
│   ├── P2Pmsgcore_c.h          ← extern "C" wrapper header (jextract reads this)
│   └── P2Pmsgcore_c.cpp        ← extern "C" wrapper implementation
└── java\
    ├── pom.xml
    └── src\main\java\com\p2pmsgcore\
        ├── native_\
        │   └── P2Pmsgcore_c.java   ← jextract stub (replace with jextract output)
        ├── NativeStrings.java       ← wchar_t ↔ String helper
        ├── P2PAddr.java             ← clean wrapper for P2Paddr
        ├── P2PMsg.java              ← clean wrapper for P2PeerMsg
        ├── P2PeerConWsa.java        ← clean wrapper for P2PeerConWsa
        ├── P2PeerHub.java           ← clean wrapper for P2PeerHub
        └── SmokeTest.java           ← end-to-end smoke test
```

---

## Lifecycle — call startup before any hub

The P2Pmsg environment must be initialised **once per process before any
`P2PeerHub` operation** and torn down at the end:

```java
import com.p2pmsgcore.native_.P2Pmsgcore_c;

P2Pmsgcore_c.p2pmsgcore_startup(16);   // 16 = max hubs; returns 0 on failure
try {
    // ... create/spawn hubs, connections, post messages ...
} finally {
    P2Pmsgcore_c.p2pmsgcore_cleanup();
}
```

`p2pmsgcore_startup` initialises the shared hub/pump locks and the hub-manager
table (the native `StartupP2Pmsg`). **Skipping it crashes hard** in native code
on the first `createHub` — it enters uninitialised critical sections. `P2PAddr`
and `P2PMsg` (pure object model) do **not** require startup, so code that only
builds addresses/messages can skip it. `SmokeTest` shows the full pattern.

## Ownership rules

- `P2PeerHub.postConnection(con, pump)` — hub takes ownership of `con`;
  the Java wrapper calls `con.detach()` automatically.
- `P2PeerHub.postMessage(msg)` / `P2PeerConWsa.postMessage(msg)` — framework
  takes ownership of `msg`; the wrapper calls `msg.detach()` automatically.
- All other objects follow RAII: use try-with-resources.

---

## String encoding

The C++ library is built with `UNICODE` defined, so all `TCHAR` strings are
`wchar_t` (UTF-16LE on Windows).  `NativeStrings.toWStr` / `fromWStr` handle
the conversion transparently.

### UTF-8 (`_u8`) — the portable surface

`wchar_t` is 2 bytes on Windows (UTF-16) but 4 bytes on Linux (UTF-32), so the
`wchar_t` entry points are **not** portable across a Windows DLL and the Linux
`libp2pmsgcore.so`. Each string-bearing C function therefore has a `*_u8` twin
(in `native\P2Pmsgcore_c_u8.cpp`) that takes/returns **UTF-8 `char*`**, converting
at the boundary. jextract regenerates the `_u8` bindings automatically from the
header, and the Java wrappers expose them:

| wchar_t method            | UTF-8 (portable) method        |
|---------------------------|--------------------------------|
| `new P2PAddr(s)`          | `P2PAddr.ofUtf8(s)`            |
| `addr.name()`             | `addr.nameUtf8()`             |
| `addr.isChild/isRable`    | `addr.isChildUtf8/isRableUtf8`|
| `new P2PMsg(...)`         | `P2PMsg.ofUtf8(...)` / `ofMsgIdUtf8(id)` |
| `msg.source/destination/name` | `msg.sourceUtf8/destinationUtf8/nameUtf8` |
| `msg.setSource/setDestination` | `msg.setSourceUtf8/setDestinationUtf8` |

Prefer the `_u8` methods for encoding-portable behaviour. `SmokeTestU8`
exercises them with multibyte UTF-8 (é / € / astral 🚀) and mirrors the native
`p2p_u8_smoke` CTest.
