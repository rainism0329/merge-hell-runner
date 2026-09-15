# Releasing Merge Hell Runner

`gradle.properties` is the single source of truth for the plugin version and IntelliJ
build compatibility. The plugin descriptor receives those values during packaging.

## Local release build

Prerequisites: JDK 17 and a clean checkout.

```bash
./gradlew clean test buildPlugin verifyPlugin --no-daemon
```

The installable archive is written to:

```text
build/distributions/merge-hell-runner-<version>.zip
```

Create a SHA-256 file next to it:

```bash
cd build/distributions
sha256sum merge-hell-runner-<version>.zip > merge-hell-runner-<version>.zip.sha256
```

On Windows PowerShell, use:

```powershell
$zip = "build/distributions/merge-hell-runner-<version>.zip"
$hash = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
"$hash  $([IO.Path]::GetFileName($zip))" | Set-Content "$zip.sha256"
```

## Release checklist

- [ ] Update `pluginVersion` in `gradle.properties`.
- [ ] Add the dated release to `CHANGELOG.md`.
- [ ] Add `docs/releases/<version>.md` for the GitHub Release body.
- [ ] Keep the Marketplace HTML identical to the descriptor's change notes.
- [ ] Confirm Marketplace description and change notes in `plugin.xml`.
- [ ] Run `clean test buildPlugin verifyPlugin` on JDK 17.
- [ ] Install the generated ZIP into IntelliJ IDEA 2023.2 and the newest supported IDE.
- [ ] Smoke-test start, all mission transitions, Lab `T` + `L`, final victory, and restart.
- [ ] Commit the release changes and push the commit.
- [ ] Tag the exact release commit with `v<version>` and push the tag.
- [ ] Verify the GitHub Release archive and SHA-256 checksum.
- [ ] Verify the new version in JetBrains Marketplace before announcing it.

## Automated release

Pushing a tag such as `v2.0.0` starts `.github/workflows/release.yml`. The workflow:

1. Rejects the tag if it does not match `pluginVersion`.
2. Runs all tests and validates the plugin archive.
3. Builds the installable ZIP and SHA-256 checksum.
4. Creates or updates the matching GitHub Release with `docs/releases/<version>.md`.
5. Publishes a signed build to JetBrains Marketplace when all four required repository
   secrets are present:

- `JETBRAINS_MARKETPLACE_TOKEN`
- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`

The Gradle signing and publishing tasks read credentials only from environment variables;
secrets must never be committed to the repository.

## Manual Marketplace publication

Set `PUBLISH_TOKEN`, `CERTIFICATE_CHAIN`, `PRIVATE_KEY`, and `PRIVATE_KEY_PASSWORD` in
the environment, then run:

```bash
./gradlew publishPlugin --no-daemon
```

Do not reuse a version that has already been uploaded to Marketplace. Increment
`pluginVersion` for every subsequent upload, including corrections.

## Prepared 2.0.0 materials

See the [2.0.0 publishing checklist](docs/releases/2.0.0-publishing.md) for the concise English
release notes, Marketplace HTML, candidate archive and remaining publication steps.
