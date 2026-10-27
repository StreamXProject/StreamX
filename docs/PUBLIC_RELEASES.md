# Publishing StreamX

- **StreamX** owns app source, numbered app releases and the website in `docs/`.
- **Skins** owns public skin APKs, artwork and its discovery index.
- **Plugins** remains private. Its publisher mirrors only plugin APKs and icons to **StreamX-Releases**.
- **StreamX-Releases** retains the unified extension catalogue, historical APKs and existing Automix packages. Its former website redirects to StreamX.

Website: https://streamxproject.github.io/StreamX/

## App releases

Start **Build StreamX** manually on `main`. The workflow builds and tests a signed ARM64 APK, using `1.8.<run number>` and version code `260000 + run number`, then publishes a normal latest release in StreamX. Local builds stay at version 1.8 / code 26. No app mirror or cross-repository publishing token is needed.

The website and app updater read StreamX releases directly. Historical app releases in StreamX-Releases remain available to older installed versions.

## Extensions

Both official extension screens continue using the backwards-compatible public catalogue:
https://streamxproject.github.io/StreamX-Releases/extensions/index.json

Skins builds export extension-owned icons and publish a signed APK catalogue in the `repo` release. **Sync direct skin links** in StreamX-Releases reads that index hourly, or on manual dispatch, and updates only its skin entries. APKs and icons link directly to Skins releases; they are not copied. The sync refuses missing or downgraded skins and untrusted download URLs or signers.

Private Plugins publishing preserves these direct skin entries. Its `PUBLIC_RELEASE_TOKEN` is restricted to Contents read/write on StreamX-Releases. No user credential is embedded in the app or catalogue.

## Website

GitHub Pages serves `/docs` from `main` in StreamX. Preview locally with:

```sh
python3 -m http.server 8000 --directory docs
```

The site reads app releases from StreamX, the combined public extension catalogue from StreamX-Releases, and existing Automix releases from StreamX-Releases. Screenshot instructions live in `docs/assets/screens/README.md`.

## Legacy Automix packages

Existing Automix URLs remain unchanged. **Mirror legacy Automix packages** is a manual workflow accepting only `automix-N` tags; it requires `PUBLIC_RELEASE_TOKEN` for StreamX-Releases. It validates archive and internal checksums before publication. App releases do not trigger it.
