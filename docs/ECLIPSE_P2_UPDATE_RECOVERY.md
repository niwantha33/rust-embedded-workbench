<!-- SPDX-License-Identifier: Apache-2.0 -->
# Eclipse p2 reports an old Rust Embedded Workbench version

**Symptoms:** `Rust Embedded → Check for Updates` displays an old latest-published version, or detects a newer feature but the Eclipse update wizard does not offer it.

## Confirm the public release

The publisher writes `updates/public-channel.properties`. Its `version=` field is the qualified feature version. The **public preview is not publisher-signed**, so Eclipse's standard trust and unsigned-content prompts are still necessary.

The older workbench updater called `IMetadataRepositoryManager.loadRepository()` on a mutable URL (`.../updates/site/`). Eclipse explicitly permits that API to reuse cached repository objects, sometimes showing the previous p2 version even after a later release was published.

## One-time recovery on an affected Eclipse installation

1. Close any running Zephyr or Rust **debug/flash/build sessions**. Do not remove Zephyr Workbench.
2. Open **Help → Install New Software → Add** in Eclipse.
3. Give the repository a distinctive name such as `Rust Embedded Workbench latest - immutable release`.
4. Enter the **version-specific** URL: `https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/releases/0.1.0.20261112051358/` (confirmed Cargo Workbench release). Do **not** use the old `updates/site/` location for this one-time recovery.
5. Select the Rust Embedded Workbench feature if Eclipse offers it. If it is already installed, add/enable the new URL under **Window → Preferences → Install/Update → Available Software Sites**, then run **Help → Check for Updates**. Do not downgrade or uninstall Zephyr Workbench.
6. Review Eclipse's licence and unsigned-artifact trust dialogs, install, and restart when prompted. Check **Help → About Eclipse IDE → Installation Details** for the installed Rust feature version.
7. In the updated Rust plug-in, choose **Rust Embedded → Check for Updates** to exercise the new immutable release lookup. It will compare against the published channel's current release, not just the old cached mutable p2 repository.

Use a newer version-specific URL if GitHub has since published another release. The old version above is an example known to be published, not a permanent latest-version promise.

## Fix in the next released plug-in

The updated code now:

- Fetches the bounded `public-channel.properties` file with HTTPS, no redirects and a no-cache request; rejects invalid/duplicate metadata.
- Derives a new immutable `updates/releases/<qualified-version>/` p2 repository URL from strictly validated version data.
- Calls `refreshRepository()` on Eclipse's metadata and artifact managers before making an installed/available comparison, instead of retaining a cached `loadRepository()` result.
- Verifies that the discovered feature-group version equals the channel's advertised version.
- Identifies the *installed Rust Embedded Workbench* IU in the active Eclipse profile, and passes only that IU to `getUpdateOperation()`; the native p2 wizard retains trust/licence/restart approval.
- Refuses rollback and defers installation while firmware jobs or debug sessions are active.
- Records clear manual-check errors rather than claiming successful installation.

This is a public development preview without publisher signature. A successful network check is not proof of signed code authenticity. Windows Eclipse update-wizard and restart acceptance must still be tested on the installed setup; Tycho's native Linux provisioning tests do not test the full Windows wizard.
