#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
set -euo pipefail
cd "$(dirname "$0")/.."
rm -rf .build
mkdir -p .build/main .build/test
find src/main/java -name '*.java' -print > .build/main-sources.txt
javac -encoding UTF-8 --release 21 -d .build/main @.build/main-sources.txt
find src/test/java -name '*.java' -print > .build/test-sources.txt
javac -encoding UTF-8 --release 21 -cp .build/main -d .build/test @.build/test-sources.txt
java -cp .build/main:.build/test io.github.niwantha33.rew.cargo.CargoTests
java -cp .build/main:.build/test io.github.niwantha33.rew.setup.SetupTests
java -cp .build/main:.build/test io.github.niwantha33.rew.core.BootstrapTests
java -cp .build/main:.build/test io.github.niwantha33.rew.updates.UpdateSecurityTests
java -cp .build/main io.github.niwantha33.rew.cli.Main boards
java -cp .build/main io.github.niwantha33.rew.cli.Main plan mspm0g3507-launchpad
