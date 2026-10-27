Telegram Login Android SDK

Source: https://github.com/TelegramMessenger/telegram-login-android
Commit: f9d5ec36ba2433bc5f103b5cd8289f43a05f9336
License: MIT (see LICENSE). SDK Kotlin sources are vendored unchanged under
app/src/main/java/org/telegram/login to avoid requiring GitHub Packages credentials.

StreamX fetches the public client_id from the selected server's /auth/telegram/config.
In BotFather register each installed application ID and its signing certificate SHA-256,
including dev/benchmark IDs when used. The current BotFather native app registration
provides different App URLs for each certificate. Build types configure both the
SDK redirect and verified manifest App Link from the same host:
- Debug: https://app3941916923-login.tg.dev/tglogin
- Release: https://app2560996458-login.tg.dev/tglogin
Telegram hosts the domain association; no server endpoint or manual domain hosting
is needed. Both supplied registrations are for com.music.streamx (prod flavor).
Dev and benchmark packages need separate native registrations and their corresponding
hosts in app/build.gradle.kts before Telegram can verify those packages. Benchmark
currently inherits the debug host because it uses the debug signing certificate.
If BotFather gives a different App URL, update the corresponding telegramHost in
app/build.gradle.kts; it supplies both the SDK and manifest host.
No Telegram client secret is embedded in the app. The SDK uses PKCE; StreamGO verifies
the resulting ID token at /auth/telegram/validate-token before issuing an app session.
An OAuth attempt interrupted by process death must be started again.
