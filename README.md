<div align="center">

<img src="docs/logo.svg" width="96" height="96" alt="StreamX logo">

# StreamX

<p align="center">
A music player for Android that plays from YouTube Music, JioSaavn, your own servers<br>
and plugins, trying each source in the order you choose.
</p>

[Download](https://github.com/StreamXProject/StreamX/releases/latest) ·
[Website](https://streamxproject.github.io/StreamX/) ·
[Skins](https://github.com/StreamXProject/Skins)

</div>

## Features

- Plays from YouTube Music, JioSaavn, StreamX and CloudStream servers, addons, and plugins for Spotify, Apple Music and Amazon Music
- Sources are tried in the order you set
- Lossless, Dolby Atmos and USB DAC output where supported
- Equaliser, crossfade and Automix
- Lyrics, downloads, Listen Together and Google Cast
- Last.fm, ListenBrainz and Discord
- Skins

## Install

Download `StreamX-arm64-v8a.apk` from the [latest release](https://github.com/StreamXProject/StreamX/releases/latest). Requires Android 8.0 or later.

## Build

Requires JDK 17, the Android SDK and the NDK.

```sh
cp local.properties.example local.properties
./gradlew :app:assembleProdDebug
```

Release signing reads `keystore.properties` (see `keystore.properties.example`).

## Credits

StreamX started as a fork of [BitChord](https://github.com/kushagrasinghx/BitChord) by [@kushagrasinghx](https://github.com/kushagrasinghx) and contributors.

## License

[GPL-3.0](LICENSE)
