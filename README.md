<div align="center">

<img src="assets/logo.png" width="200" alt="MatchVictorious logo"/>

# MatchVictorious

**A privacy-first dating app and data-import hub for Android.**

<p>
  <a href="https://github.com/Victorious93/matchvictorious/actions/workflows/android.yml"><img src="https://img.shields.io/github/actions/workflow/status/Victorious93/matchvictorious/android.yml?branch=main&style=flat-square&labelColor=0B0F1A&logo=githubactions&logoColor=00E5FF&label=CI" alt="CI"/></a>
  <a href="https://github.com/Victorious93/matchvictorious/releases"><img src="https://img.shields.io/github/v/release/Victorious93/matchvictorious?include_prereleases&style=flat-square&labelColor=0B0F1A&color=00E5FF&label=release" alt="Release"/></a>
  <img src="https://img.shields.io/badge/platform-Android-00E5FF?style=flat-square&labelColor=0B0F1A" alt="Platform"/>
  <a href="https://victorious-framework.pages.dev"><img src="https://img.shields.io/badge/Victorious-Framework-FF2BD6?style=flat-square&labelColor=0B0F1A" alt="Victorious Framework"/></a>
</p>

<p><a href="https://github.com/Victorious93/matchvictorious/releases">Releases</a> · <a href="https://victorious-framework.pages.dev">Victorious Framework</a></p>

</div>

---

MatchVictorious is a privacy-first dating app for Android (Kotlin + Jetpack Compose) with two parts: a **MatchVictorious platform** (email account with no phone number, profiles, discovery, matches and realtime chat on Supabase) and a **data-import hub** for exports you request from other dating apps. Package id: `com.victorious.match`, version 0.11.0.

> **Status: in development.** CI builds debug APKs and runs unit tests. The repository does not claim device testing.

## Features

- **Platform:** email sign-up, profiles with photos, discovery with age/distance preferences, likes, matches (expire after 14 days), realtime chat, unmatch/block/report. Only verified profiles appear in Discover.
- **Privacy:** approximate location only (snapped to a 0.01° grid on device and by a database trigger); no phone number needed; in-app and web **account deletion**; Row Level Security enforces access.
- **Safety and trust:** safety center (SOS dialer, check-in timer, "Share my date" links), in-app moderation with an audit log, and selfie liveness verification (on-device ML Kit gesture challenge with moderator review).
- **Social:** profile prompts, moderated voice intro, like-with-comment and icebreakers, local events and group dates, optional opt-in voice/video calls via LiveKit (blurred video, limits, no recording), optional FCM push, and Victorious+ via Google Play Billing.
- **Import hub:** parses the data exports you request yourself (GDPR/CCPA) entirely on device and stores them AES-256-GCM encrypted. `TinderImportProvider` reads Tinder `data.json` or ZIP; `GenericImportProvider` is a ZIP/JSON/HTML fallback for Hinge, Bumble, OkCupid, Match, POF, Grindr, Feeld, Coffee Meets Bagel and Badoo. Imports never leave the phone and can pre-fill your profile.
- **Never:** asks for dating-app passwords, automates other apps, uses an accessibility service or reads notifications. No platform offers a public API, so there is no live sync.
- **Fleet (optional):** an `EXTERNAL_NODE` API for droidcommand-AI (`STATUS`, `PING`, `OPEN_SCREEN`) guarded by a signature permission, and a read-only VictorLaunch fleet theme client. See [docs/MANUAL.md](docs/MANUAL.md#fleet-api).

## Install

Download the APK from [Releases](https://github.com/Victorious93/matchvictorious/releases) (latest: `v0.1.0-debug`, a debug pre-release that is older than the 0.11.0 source). CI also uploads each build as the `matchvictorious-debug-apk` artifact. minSdk 26.

## Build from source

JDK 17 and the Android SDK.

```bash
git clone https://github.com/Victorious93/matchvictorious.git
cd matchvictorious
./gradlew testDebugUnitTest assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

## Permissions

`INTERNET`, `ACCESS_COARSE_LOCATION` (optional, approximate), `POST_NOTIFICATIONS`, `RECORD_AUDIO` and `CAMERA` (voice intro, calls, liveness), plus the app's own `FLEET_CONTROL` signature permission. No root.

## Signing

`app/debug.keystore` is a **debug-only** key committed on purpose so fleet builds share a stable signature (alias `androiddebugkey`, password `android`). Release keys are never committed (`*.jks` and `release*.keystore` are git-ignored). Release signing reads `MV_RELEASE_STORE_FILE`, `MV_RELEASE_STORE_PASSWORD`, `MV_RELEASE_KEY_ALIAS` and `MV_RELEASE_KEY_PASSWORD` from the environment.

## Backend (Supabase)

- SQL migrations are in `supabase/migrations`; Edge Functions are in `supabase/functions/`.
- The app reads `MV_SUPABASE_URL` and `MV_SUPABASE_PUBLISHABLE_KEY` from a Gradle property or env var. The publishable key is public by design and access is enforced by RLS. **Never** commit a `service_role` or `sb_secret_` key; the build refuses to embed one.
- **Account deletion:** in-app (Settings → Delete account) or at the [web page](https://victorious93.github.io/matchvictorious/delete-account.html); both call the `delete-account` Edge Function.
- **Push (FCM)** is optional and disabled unless `app/google-services.json` exists ([docs/setup/firebase-push.md](docs/setup/firebase-push.md)). **Calls (LiveKit)** are optional and disabled until their function secrets are set ([docs/setup/livekit-calls.md](docs/setup/livekit-calls.md)). Liveness vendor notes: [docs/setup/liveness-vendor.md](docs/setup/liveness-vendor.md).
- Moderators are added in the SQL editor (see the [manual](docs/MANUAL.md)).

## Docs

[User manual](docs/MANUAL.md) (also shown in-app under Help) · [Privacy policy](PRIVACY.md)

## Disclaimer

Not affiliated with or endorsed by any dating platform. Platform names are used only to identify export formats.

---

## Victorious fleet

MatchVictorious is part of the [Victorious Framework](https://victorious-framework.pages.dev), a family of Android and PC tools by Don Victor. **Every app works standalone; fleet integrations are optional** and never required.

Related: [VictorLaunch](https://github.com/Victorious93/victorlaunch) (fleet launcher and theme provider), [droidcommand-AI](https://github.com/Victorious93/droidcommand-AI) (fleet controller), [victorious-fleet](https://github.com/Victorious93/victorious-fleet) (index of all fleet apps). The full roster lives in [victorious-fleet](https://github.com/Victorious93/victorious-fleet), and live status is in [fleet-manifest](https://github.com/Victorious93/fleet-manifest).

## Contributing

Issues and pull requests are welcome. Keep changes focused, match the existing code style, never commit secrets or release keys, and keep any fleet integration optional so the app still works on its own. Open a PR against the default branch and wait for CI to pass.

## License

This repository does not include a LICENSE file yet, so no license is granted beyond what the author states elsewhere. Please open an issue before reusing the code.
