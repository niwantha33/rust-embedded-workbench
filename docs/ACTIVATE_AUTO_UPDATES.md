# Automatic updates — public channel activation

The Rust Embedded Workbench preview now uses **public Eclipse p2** with no custom Ed25519 key, private signing secret or user GitHub token.

1. CI builds and tests main, verifies the p2 site by installing into an isolated Eclipse copy, then publishes to the public updates branch using the built-in GITHUB_TOKEN. Published site: https://raw.githubusercontent.com/niwantha33/rust-embedded-workbench/updates/site/ .
2. If the publishing workflow cannot push, the owner should check GitHub Settings > Actions > General > Workflow permissions and updates branch rules. Allow the publishing workflow's contents:write scope while keeping other workflows read-only; do not enable force pushes.
3. An existing old preview still requires one manual p2 ZIP upgrade because it does not have the new public update handler. Use Help > Install New Software > Add > Archive to install the first qualified public-p2 preview.
4. After restart, select Rust Embedded > Update Settings. Startup/periodic checks are enabled by default and user approval through Eclipse's trust/licence/restart prompts is mandatory.
5. For full production security, sign artifact JARs and verify publisher identity. The public preview alone does not provide cryptographic publisher authenticity.

See [Public p2 update details](PUBLIC_P2_UPDATES.md) and [Capability evidence](CAPABILITY_EVIDENCE.md).
