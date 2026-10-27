# Public downloads

Repository layout:

- `StreamXProject/StreamX`: public app source, builds and tests (visibility migration pending; see below).
- `StreamXProject/Plugins`: private plugin implementations.
- `StreamXProject/Skins`: public skin implementations and downloads.
- `StreamXProject/StreamX-Releases`: public website in `docs/`, APK releases and optional Automix packages.

Website: https://streamxproject.github.io/StreamX-Releases/

The existing StreamX repository is still private because its Git history includes `extensions/plugins/`. Changing visibility directly would publish those plugin implementations. A clean source snapshot is prepared locally. Preserve the old history in a private archive and create a clean public app repository after the migration is approved; do not change visibility before separating the histories.

## One-time credential setup

Create a fine-grained personal access token with **StreamXProject** as resource owner. Select only **StreamX-Releases** and grant **Contents: Read and write**. Approve the token in the organization if required. Save it in the **StreamX source repository** under Settings → Secrets and variables → Actions as `PUBLIC_RELEASE_TOKEN`. Rotate it before expiry.

Do not put the token in the public repository, app configuration or APK. The source workflow's built-in token reads source release assets; the restricted token writes public assets.

## Publishing

The existing **Build StreamX** manual workflow builds, tests, signs and publishes a source preview release. Its completion starts **Publish public downloads**, which mirrors the exact release tag. Ordinary push and PR builds do not publish publicly. Releases created by Actions with `GITHUB_TOKEN` do not start another `release` workflow; the `workflow_run` trigger handles that case.

Publishing a source release through GitHub also triggers the mirror. To retry or mirror a historical release, run **Publish public downloads** manually with the source tag (for example `v1.8` or `automix-1`). For releases created by other automated workflows using `GITHUB_TOKEN`, dispatch the public publishing workflow explicitly.

App tags must start with `v` and a numeric dotted version. Stable versions are marked latest; previews preserve prerelease status and never become latest. The public download filename `StreamX.apk` is an ARM64 alias; architecture-specific names are retained for the in-app updater. Each release includes freshly calculated `SHA256SUMS.txt`.

Private release notes are deliberately replaced with public installation notes; add public changelog text to the public release separately after publishing. Retrying a publish replaces these notes. Source commit references and private Actions links are not copied. Public tags point to public website commits.

## Automix

Run `./gradlew :app:packageAutomix`. Upload **only** these files from `app/build/automix-package/` to a source release tagged `automix-N`, where N matches `automixPackageVersion`:

- `automix-manifest.json`
- `streamx-automix-N-arm64-v8a.zip`
- `streamx-automix-N-armeabi-v7a.zip`
- `streamx-automix-N-x86_64.zip`

Publish that release, or manually dispatch the mirror if it was created by an Actions token. The mirror verifies archive checksums, all internal file checksums, the package version, runtime and ABI before publishing. It never marks Automix as the latest app release.

The default app URL is pinned to public `automix-1`. When bumping `automixPackageVersion`, also update that URL (or set `STREAMX_AUTOMIX_BASE_URL`). The public mirror keeps filenames and manifest bytes unchanged. The app retains its compiled native engine pins and runtime compatibility checks. An explicit empty environment/local property disables downloads.

Existing installed APKs retain their compiled URLs; install a build containing the new public URLs to use anonymous in-app downloads and updates. Moving release files cannot change an APK already installed.

## Website maintenance

The public repository's GitHub Pages configuration serves `/docs` from `main`. Its JavaScript reads public release metadata to display versions, dates, sizes and package links. Static GitHub links remain available if JavaScript is disabled or the API is rate limited. No source checkout is deployed publicly.

## Public plugins and skins from private source

`StreamXProject/Plugins` stays private and builds four plugins plus eighteen skins. Its internal manifest is retained for build verification. A separate public mirror publishes only tested APKs and a filtered v1 manifest to `StreamXProject/StreamX-Releases`.

Both official extension screens now read `https://streamxproject.github.io/StreamX-Releases/extensions/index.json`, filtering on each entry's `type`. Every download URL in that manifest points to a public `StreamX-Releases` asset. The app sends no GitHub credential to the public manifest or these public APKs. Users need no GitHub account or token. Legacy private Plugins URLs remain supported only for older catalogue entries, and public Skins URLs remain supported for existing skin releases.

The private repository's **Publish public extension downloads** workflow runs after a successful combined build, or can mirror the current tested release manually without rebuilding. Set `PUBLIC_RELEASE_TOKEN` in the private **Plugins** repository to a fine-grained token restricted to **StreamX-Releases**, with Contents read/write. This credential is for the publishing service only; it is never included in the public site, manifest or APK.

Public extension releases use `extensions-v…` tags and never become the latest app release. The mirror checks source identity, signing pins, APK sizes and SHA-256 checksums, uploads APKs, then commits only `docs/extensions/index.json` to the public repository. It refuses a manifest that downgrades or removes already published packages.

The website reads the same public manifest for its plugin download list, complete skin downloads and current skin APK links. The eight illustrative skin previews retain bundled fallback links if the manifest is unavailable.
