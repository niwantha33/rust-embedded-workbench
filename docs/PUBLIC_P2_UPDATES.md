# Public Eclipse p2 updates — no custom signing key (development preview)

Rust Embedded Workbench uses a public update site on the dedicated [updates branch](https://github.com/niwantha33/rust-embedded-workbench/tree/updates). No GitHub account, PAT, local checkout, local Maven build or custom Ed25519 key is required **to receive updates**.

## Public update location

Repository: https://github.com/niwantha33/rust-embedded-workbench

p2 site URL: https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/site/

The repository is served over HTTPS. A public GitHub repository does not prove that every distributed artifact is trustworthy. This development channel does not assert publisher-signed JARs. **Never suppress Eclipse's unsigned-artifact, trust-authority, licence or restart prompts.** Eclipse p2 performs those checks and the user decides whether to install.

## Automatic publication

On a qualifying main-branch change, the read-only GitHub Actions jobs compile the Java core, execute Windows/Linux regression checks, build the Tycho p2 site and install it in an isolated Eclipse copy downloaded with a pinned SHA-512 checksum. A separate write-scoped publishing job verifies the exact transferred release and current main SHA, rejects rollback or overwrite of previous version folders, then fast-forwards the updates branch using its ephemeral built-in GITHUB_TOKEN.

The branch holds the latest p2 repository in site/, previous immutable copies in releases/<version>/, and public-channel.properties containing source SHA, version and deterministic artifact digest. No private signing secret is needed or committed. Only CI builds should modify updates; enable branch rules, restrict writes to the release workflow and require PR reviews/checks on main.

## Eclipse user experience

1. Install the **first new preview** using Help > Install New Software > Add > Archive and an actual p2 ZIP from a verified GitHub Actions run. The old screenshot-era preview does not contain this public p2 updater and needs one manual replacement.
2. Restart Eclipse and select Rust Embedded > Update Settings. Automatic checks default to enabled, starting about 20 seconds after Eclipse opens and repeating about every six hours.
3. When a newer Rust Workbench feature is found on the fixed public site, the IDE offers to open the native Eclipse p2 update wizard. It never silently applies an update.
4. Check Eclipse's trust and unsigned-content warnings, approve the installation if you trust the source, and restart when requested. Active firmware builds/debug sessions postpone updates.

The public update branch must contain a qualified p2 site before checks can succeed; an empty bootstrap README is not an installable release. The tool does not update firmware, SDKs, drivers, Cargo.lock, probe firmware or user projects.

## Security trade-off and future stable releases

This no-custom-key approach protects the transport with HTTPS, restricts publishing to CI and relies on native Eclipse p2 approval. **It does not provide the same publisher authenticity as signed JARs or our optional Ed25519 channel.** For stable releases, sign artifacts and verify publisher identity; a public repository alone is not a cryptographic signature.

See [Eclipse's artifact trust documentation](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.user/reference/ref-p2-trust.htm) and [automatic-update settings](https://help.eclipse.org/latest/topic/org.eclipse.platform.doc.user/reference/ref-p2-autoupdate.htm).
