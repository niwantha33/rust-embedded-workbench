<!-- SPDX-License-Identifier: Apache-2.0 -->
# Contributing

Use small, reviewable pull requests. For Java core changes run `bash scripts/verify.sh` (Linux) or `./scripts/verify.ps1` (Windows PowerShell) with JDK 21. Link hardware claims to logs and exact device/probe versions as described in `docs/CAPABILITY_EVIDENCE.md`.

Never raise board evidence above DOCS_ONLY without recorded qualification. Avoid committing vendor SDKs, firmware binaries, credentials, driver bundles or proprietary SVD files without an explicit licence check.

Open architecture discussions and proposed vendor additions as issues before making cross-cutting changes.
