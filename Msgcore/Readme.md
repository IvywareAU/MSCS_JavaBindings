# Msgcore – Java Panama FFI Bindings

Exposes Msgcore's flat `extern "C"` surface to Java through the Panama Foreign
Function Interface (Java 22+), with hand-written wrappers over the generated layer.

| C++ class      | Java wrapper   | Purpose                                  |
|----------------|----------------|------------------------------------------|
| `P2PmsgMgr`    | `MsgMgr`       | The persistent store: create, load, save |
| `P3PmsgField`  | `MsgField`     | A node: name, value, children, declares  |
| `P3PmsgList`   | `MsgList`      | Doubly-linked list                       |
| `P3PmsgVect`   | `MsgVect`      | Indexed vector                           |
| `P3PmsgAttr`   | `MsgAttr`      | `@`-qualified attribute collection       |
| `P3PmsgDesc`   | `MsgDesc`      | Descendant (child) collection            |
| `P3PmsgCurs`   | `MsgCurs`      | `Iterable<MsgField>` cursor              |
| —              | `MsgDataType`, `MsgAddrMode` | The `VBLockData_*` / `VBLock_Addr*` constants |

**Status: 4/4 on Debug|x64 and 4/4 on Release|x64**, measured 2026-08-20 against
`Msgcore.dll` built from `build-win-cmake`, on JDK 23.0.2. Run them with
`.\run_all.ps1`.

---

## Read this first: what was here before 2026-08-20 was not generated code

`native_/Msgcore_c.java` was **not jextract output**. It was a hand-written stub,
and its own header said so:

> *"This stub documents the expected jextract output shape so wrapper classes can
> compile before jextract is actually run. **Replace this file entirely with the
> real jextract output once the Msgcore DLL is built.**"*

That replacement never happened. The stub hand-transcribed **108 of the header's
282** entry points, including **none** of the 65 `_u8` twins, and nothing compared
the two numbers. Two consequences worth naming:

1. **Regenerating the bindings broke the build**, which is a large part of why
   nobody did. The `toWStr`/`fromWStr` helpers had been written *inside* the file
   jextract replaces wholesale, so a regeneration deleted them. They live in
   `NativeStrings` now, where a regeneration cannot reach them — which is how the
   Targetcore tree has always had it.

2. **Every write made from Java was silently discarded.** All of `MsgMgr`'s
   convenience methods routed through `asField()`, which the C header describes in
   as many words as *"a DETACHED deep copy… mutations never reach the tree"*. The
   live-access group (`msgcore_mgr_root`, `msgcore_field_child`, the `p2pos`
   family) was among the 174 entry points the stub never had, so there was nothing
   else they could route through. `MsgcoreExample` declared three items, iterated
   and found one, saved, reloaded, and threw `item not found: Age`.

`AbiCoverage` now exists so the first of those fails loudly. The second is what
`SmokeTest` asserts.

### A weaker check than Targetcore's, and worth knowing why

`AbiCoverage` compares the generated bindings against **`Msgcore_c.h`** — the same
file jextract reads. Targetcore's equivalent compares against
`.github/ci/abi-flat.manifest`, an explicit enumeration that its VERSIONING.md
makes a promise about. **Msgcore has no such manifest.** A header tells you what
exists; a manifest tells you what was promised, and only the second can tell you
that removing something is a breaking change. Closing that gap is Msgcore's to do,
not this repository's.

---

## Read this second: the JVM brings its own C++ runtime, and it is usually too old

**Symptom:** the first call into the library dies with `EXCEPTION_ACCESS_VIOLATION
(0xC0000005)` inside `msvcp140.dll`, on the JVM's own stack, with no diagnostic.

**Cause:** a JDK ships its own `msvcp140.dll` / `VCRUNTIME140.dll` in `bin\`, and
`jvm.dll` imports them, so they are loaded before any of your code runs. Windows
resolves a DLL's imports against whatever module of that base name is *already*
loaded — so `Msgcore.dll` gets the JDK's copy, not the system's, and no `PATH` or
load order can change that. The library is built with MSVC 14.4x and uses
`std::mutex`, whose constructor became `constexpr` in toolset **14.40**.

**Measured** 2026-08-20, same DLL, same non-MFC host, the pre-loaded runtime the
only variable: **14.36 → `0xC0000005`; 14.40 → ok; 14.44 → ok.**

**What to do:** run on a JDK 22+ whose `bin\msvcp140.dll` is 14.40 or newer.
`run_all.ps1` checks before it runs anything and stops with an explanation.

---

## Quick start

```powershell
.\run_all.ps1                 # stage the DLL, mvn compile, run all four
.\run_all.ps1 -Config Debug
.\run_all.ps1 -Java "C:\path\to\jdk\bin\java.exe"
```

Build and regeneration steps are in [BUILD.md](BUILD.md).

---

## Live handles and detached copies — the distinction the API turns on

```java
try (MsgMgr mgr = new MsgMgr(MsgAddrMode.ADDR_32, 4096, 1024 * 1024)) {

    try (MsgField root = mgr.root()) {          // LIVE - writes reach the tree
        root.declareInt("Age", 42, false).close();
        root.declareString("Name", "Alice", false).close();
    }

    try (MsgField root = mgr.root();            // re-resolve: see below
         MsgField age = root.child("Age")) {    // null if absent, not an exception
        System.out.println(age.getInt());       // 42
    }

    mgr.save("store.p2p");
}
```

| | `mgr.root()` / `field.child()` | `mgr.asField()` / `field.selectItem()` |
|---|---|---|
| what it is | a handle **aliasing** the live node | a **detached deep copy** |
| writes through it | reach the tree, and survive `save()` | go nowhere |
| survives a mutation | **no** — see below | yes |
| absent name | `null` | throws |

**A live handle does not survive a mutation.** Any call that can grow the heap may
relocate the base image, and a handle taken before such a call must not be used
after it. Re-resolve per operation, or hold `field.p2pos()` — a heap offset rather
than an address, so it still denotes the same node after a relocation, and the
natural inode number for a filesystem layer. It is **not** an identity: delete the
node and the offset can be handed out again, so a stale one resolves to whatever
now occupies that block rather than failing. Obtain, use, discard.

`mgr.fromP2pos(pos)` resolves one back to a live field; `mgr.pathOf(pos)` gives its
full path.

---

## String encoding

`TCHAR` is `wchar_t` — UTF-16LE on Windows, UTF-32 on Linux — so the wide entry
points cannot carry a string portably through an FFI. **65** string-bearing
functions have a `_u8` twin taking and returning UTF-8, ABI-identical on both
platforms, and the bindings had none of them until 2026-08-20.

| `wchar_t` method | UTF-8 (portable) method |
|---|---|
| `field.child(name)` | `childUtf8(name)` |
| `field.exists(name)` | `existsUtf8(name)` |
| `field.declareInt/Double/String` | `declareIntUtf8/declareDoubleUtf8/declareStringUtf8` |
| `field.getName()` / `getString()` | `getNameUtf8()` / `getStringUtf8()` |

`SmokeTestU8` exercises them with 2-byte é, 3-byte € and an astral 🚀 — in both the
*name* and the *value*, since those cross the boundary by different routes — and
across a save/reload, so the encoding is proven through the on-disk image rather
than only in memory. It is the Java counterpart of the native `msgcore_u8_smoke`
CTest.

**One fix that came out of writing it:** `fromWStr` read the terminator with an
*aligned* `JAVA_SHORT`, and Msgcore's live getters return pointers into a packed
VBHeap image where nothing guarantees 2-byte alignment. An odd address threw
`Target offset 0 is incompatible with alignment constraint 2` instead of reading
the string. Both trees use `JAVA_SHORT_UNALIGNED` now.

---

## Tests

| Test | What it proves | Exit codes |
|---|---|---|
| `AbiCoverage` | every entry point declared in `Msgcore_c.h` has a generated binding | 0/1/2 |
| `SmokeTest` | a write from Java reaches the tree, survives save/reload, and `p2pos` round-trips — and that a write through a *detached* handle still, deliberately, does not | 0/1 |
| `SmokeTestU8` | the `_u8` surface round-trips multibyte UTF-8 in names and values, through the file | 0/1 |
| `MsgcoreExample` | the worked example, which now runs to completion | 0/1 |

## Coverage, stated plainly

- **Generated layer: 282 of 282.** `AbiCoverage` fails if that stops being true.
- **Friendly wrappers: 119 of 282.** The 163 with no hand-written wrapper are
  reachable through `com.msgcore.native_.Msgcore_c` — they are bound, just not
  dressed. By family: `field` 43, `list` 20, `mgr` 18, `vect` 18, `stck` 17,
  `recurs` 16, `attr` 14, `desc` 11, and 58 of the 163 are `_u8` twins of methods
  whose wide form *is* wrapped.

---

## License

Copyright © 2026 Khrustal & Mann, MELBOURNE, VICTORIA, AUSTRALIA, 3000.

Licensed under the Apache License, Version 2.0. See [LICENSE](../LICENSE) for the
full text and [NOTICE](../NOTICE) for what it does and does not cover.
