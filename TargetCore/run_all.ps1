# Copyright © 2026 Khrustal & Mann
#              MELBOURNE, VICTORIA, AUSTRALIA, 3000
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#
#
# Build the bindings, stage the native DLLs, run every smoke test, summarise.
#
#   .\run_all.ps1
#   .\run_all.ps1 -Config Debug
#   .\run_all.ps1 -LibDir D:\path\holding\TargetCore.dll
#
# Exit codes per test: 0 PASS, 1 FAIL, 2 SETUP, 3 INCONCLUSIVE.

[CmdletBinding()]
param(
    [ValidateSet('Debug','Release')] [string] $Config = 'Release',
    [string] $LibDir  = '',
    [string] $Java    = '',
    [switch] $SkipBuild
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$bin  = Join-Path $root 'bin'

# ---------------------------------------------------------------------------
# 1. Find a JVM, and check the C++ runtime it is going to load.
#
# This is not paranoia. A JDK ships its own msvcp140.dll / vcruntime140*.dll in
# bin\, and Windows binds a DLL's imports to whatever module of that base name is
# ALREADY loaded -- which by the time Panama gets a look in is the JDK's copy, not
# the system one. TargetCore.dll is built with MSVC 14.4x and uses std::mutex,
# whose constructor became constexpr in toolset 14.40 (VS 2022 17.10); against an
# older msvcp140 the first call into the library dies with 0xC0000005 inside
# msvcp140, on the JVM's own stack, with no diagnostic at all.
#
# Measured on 2026-08-20, same DLL, same non-MFC host, only the pre-loaded runtime
# changed:  14.36 -> ACCESS VIOLATION,  14.40 -> ok,  14.44 -> ok.
# ---------------------------------------------------------------------------
function Resolve-Java {
    param([string] $Explicit)
    if ($Explicit) { return $Explicit }
    if ($env:JAVA_HOME -and (Test-Path "$env:JAVA_HOME\bin\java.exe")) { return "$env:JAVA_HOME\bin\java.exe" }
    $c = Get-Command java -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    throw "no java.exe: set JAVA_HOME or pass -Java"
}

$javaExe = Resolve-Java $Java
$javaBin = Split-Path $javaExe
Write-Host "java     : $javaExe"

$ver = & $javaExe -version 2>&1 | Select-Object -First 1
Write-Host "version  : $ver"

$crt = Join-Path $javaBin 'msvcp140.dll'
if (Test-Path $crt) {
    $v  = (Get-Item $crt).VersionInfo.FileVersion
    $mm = [version]($v -replace '^(\d+\.\d+).*','$1.0.0')
    Write-Host "bundled  : msvcp140 $v"
    if ($mm -lt [version]'14.40.0.0') {
        Write-Host ""
        Write-Host "STOP: this JDK bundles msvcp140 $v, and TargetCore.dll needs 14.40 or newer." -ForegroundColor Red
        Write-Host "      Windows will load the JDK's copy in preference to the system one, and the" -ForegroundColor Red
        Write-Host "      first call into the library will die with 0xC0000005 and no message." -ForegroundColor Red
        Write-Host "      Use a JDK 22+ whose bin\msvcp140.dll is 14.40 or later. See README.md." -ForegroundColor Red
        exit 2
    }
} else {
    Write-Host "bundled  : no msvcp140 in the JDK - the system copy will be used"
}

# ---------------------------------------------------------------------------
# 2. Stage the native DLLs.
# ---------------------------------------------------------------------------
if (-not $LibDir) {
    $candidates = @(
        "$root\..\..\MSCS\build\windows-msvc\TargetCore\$Config",
        "$root\..\..\MSCS\build-win-cmake\TargetCore\$Config",
        "$root\..\..\MSCS\TargetCore\out\x64\$Config"
    )
    foreach ($c in $candidates) {
        if (Test-Path (Join-Path $c 'targetcore.dll')) { $LibDir = $c; break }
        if (Test-Path (Join-Path $c 'TargetCore.dll')) { $LibDir = $c; break }
    }
}
if (-not $LibDir) { throw "no TargetCore.dll found; pass -LibDir" }

# Msgcore.dll must come from the SAME build as TargetCore.dll. A recursive
# search finds several copies of that name across the tree - the P2PeerFs staging
# dirs carry their own - and pairing a mismatched one produces the least helpful
# error in this whole toolchain: "Cannot open library: TargetCore.dll", which
# names the DLL that WAS found and says nothing about the dependency that was not.
$msgcoreDir = Join-Path (Split-Path (Split-Path $LibDir -Parent) -Parent) "Msgcore\$Config"
$msgcore    = Get-Item (Join-Path $msgcoreDir 'msgcore.dll') -ErrorAction SilentlyContinue
if (-not $msgcore) {
    $msgcore = Get-Item (Join-Path $LibDir 'Msgcore.dll') -ErrorAction SilentlyContinue
}
if (-not $msgcore) {
    throw "no Msgcore.dll beside the TargetCore.dll in $LibDir (tried $msgcoreDir) -- " +
          "TargetCore.dll imports it, and a copy from another build will not do"
}

New-Item -ItemType Directory -Force -Path $bin | Out-Null
Copy-Item (Join-Path $LibDir 'targetcore.dll') (Join-Path $bin 'TargetCore.dll') -Force -ErrorAction SilentlyContinue
if (-not (Test-Path (Join-Path $bin 'TargetCore.dll'))) {
    Copy-Item (Join-Path $LibDir 'TargetCore.dll') (Join-Path $bin 'TargetCore.dll') -Force
}
Copy-Item $msgcore.FullName (Join-Path $bin 'Msgcore.dll') -Force
Write-Host "staged   : $bin  (from $LibDir)"

# ---------------------------------------------------------------------------
# 3. Build.
# ---------------------------------------------------------------------------
$javaProj = Join-Path $root 'java'
if (-not $SkipBuild) {
    Push-Location $javaProj
    try {
        $env:JAVA_HOME = Split-Path $javaBin -Parent
        & mvn -q compile
        if ($LASTEXITCODE -ne 0) { throw "mvn compile failed" }
    } finally { Pop-Location }
}

# ---------------------------------------------------------------------------
# 4. Run.
# ---------------------------------------------------------------------------
# AbiCoverage reads the covered-surface manifest out of the TargetCore checkout
# rather than a copy kept here, because a copy is exactly what drifted last time.
$manifest = (Resolve-Path "$root\..\..\MSCS\TargetCore\.github\ci\abi-flat.manifest" -ErrorAction SilentlyContinue)
if (-not $manifest) { $manifest = '' }
Write-Host "manifest : $manifest"

$tests = @(
    'com.targetcore.AbiCoverage',
    'com.targetcore.SmokeTest',
    'com.targetcore.SmokeTestU8',
    'com.targetcore.SmokeTestGuard',
    'com.targetcore.SmokeTestAuth',
    'com.targetcore.SmokeTestSink'
)

$logs = Join-Path $root 'logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null

$oldPath = $env:PATH
$env:PATH = "$bin;$oldPath"
$results = @()
try {
    foreach ($t in $tests) {
        $short = $t.Split('.')[-1]
        Write-Host ""
        Write-Host "=== $short"
        $out = Join-Path $logs "$short.txt"
        & $javaExe --enable-native-access=ALL-UNNAMED `
                   "-Dtargetcore.manifest=$manifest" `
                   -cp (Join-Path $javaProj 'target\classes') $t 2>&1 |
            Tee-Object -FilePath $out
        $results += [pscustomobject]@{ Test = $short; Exit = $LASTEXITCODE }
    }
} finally { $env:PATH = $oldPath }

Write-Host ""
Write-Host "--- summary"
$verdict = @{ 0 = 'PASS'; 1 = 'FAIL'; 2 = 'SETUP'; 3 = 'INCONCLUSIVE' }
foreach ($r in $results) {
    $v = if ($verdict.ContainsKey($r.Exit)) { $verdict[$r.Exit] } else { "exit $($r.Exit)" }
    "{0,-18} {1}" -f $r.Test, $v
}
$bad = ($results | Where-Object { $_.Exit -ne 0 }).Count
"{0} of {1} passed" -f ($results.Count - $bad), $results.Count
exit ($(if ($bad -gt 0) { 1 } else { 0 }))
