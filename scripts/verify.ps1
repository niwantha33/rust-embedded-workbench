# SPDX-License-Identifier: Apache-2.0
$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $root
try {
    if (Test-Path '.build') { Remove-Item '.build' -Recurse -Force }
    New-Item -ItemType Directory -Path '.build/main','.build/test' -Force | Out-Null
    $sources = @(Get-ChildItem 'src/main/java' -Filter '*.java' -Recurse | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 21 -d '.build/main' $sources
    if ($LASTEXITCODE -ne 0) { throw 'Main compilation failed' }
    $tests = @(Get-ChildItem 'src/test/java' -Filter '*.java' -Recurse | ForEach-Object FullName)
    & javac -encoding UTF-8 --release 21 -cp '.build/main' -d '.build/test' $tests
    if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
    & java -cp '.build/main;.build/test' io.github.niwantha33.rew.core.BootstrapTests
    if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
    & java -cp '.build/main;.build/test' io.github.niwantha33.rew.updates.UpdateSecurityTests
    if ($LASTEXITCODE -ne 0) { throw 'Update security tests failed' }
    & java -cp '.build/main' io.github.niwantha33.rew.cli.Main boards
    if ($LASTEXITCODE -ne 0) { throw 'CLI failed' }
} finally { Pop-Location }
