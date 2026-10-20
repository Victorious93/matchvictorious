<!-- victorious-fleet-readme-refresh:start -->
<p align="center">
  <img src="assets/logo.png" alt="MatchVictorious logo" width="200">
</p>

# MatchVictorious

Dating hub app in the Victorious social lane.

**Package id:** `com.victorious.match`

**Status:** scaffold / planning *(from [fleet-manifest ROADMAP](https://github.com/Victorious93/fleet-manifest/blob/main/ROADMAP.md))*

## Install / build

Kotlin Gradle (`app/build.gradle.kts`). Typical: `./gradlew :app:assembleDebug`.

_Steps above are taken from this repo’s build files / existing docs; anything not exercised in this refresh is unlabeled as verified on a device._

## Victorious fleet

- Monorepo: [https://github.com/Victorious93/victorious-fleet](https://github.com/Victorious93/victorious-fleet)
- Fleet site: [https://victorious-framework.pages.dev](https://victorious-framework.pages.dev)

<!-- victorious-fleet-readme-refresh:end -->

<p align="center"><img src="assets/logo.jpg" alt="MatchVictorious" width="320"></p>

# MatchVictorious

A privacy-first **data-import hub** for dating platforms (Android, Kotlin + Jetpack Compose, package `com.victorious.match`).

No dating platform offers a legitimate public API, so MatchVictorious works only with the **data exports you request yourself** (GDPR/CCPA). Exports are parsed on-device, stored encrypted, and never leave the phone. Imports never leave the phone. The new MatchVictorious platform (email account with no phone number, profiles, discovery, matches, realtime chat) runs on Supabase. See `supabase/migrations`.

## Features
- Screens: Profiles, Matches, Messages (read-only), Filters, Import, Request-your-data guide, Help, Settings (Delete All Data), plus a consent screen on first launch
- `DatingProvider` interface with capabilities (`canImport`, `canDeepLink`, `canLiveSync = false`)
- `TinderImportProvider`: Tinder `data.json` / ZIP (User, Messages, Usage). Tolerates missing fields
- `GenericImportProvider`: ZIP/JSON/HTML fallback for Hinge, Bumble, OkCupid, Match, POF, Grindr, Feeld, CMB, Badoo
- Deep links open the official app, or its Play Store page
- Import through the Storage Access Framework; AES-256-GCM `EncryptedFile` storage
- No passwords, automation, accessibility service or notification reading; network is used only for your MatchVictorious account
- Neon cyan-to-magenta ring theme, dark
- Fleet: EXTERNAL_NODE API for droidcommand-AI (`STATUS`, `PING`, `OPEN_SCREEN`) guarded by a signature permission. See [docs/MANUAL.md](docs/MANUAL.md#fleet-api)

## Build
    ./gradlew testDebugUnitTest assembleDebug
The APK lands in `app/build/outputs/apk/debug/`. CI (GitHub Actions) runs the unit tests, builds the debug APK and uploads it as the `matchvictorious-debug-apk` artifact.

## Signing
- `app/debug.keystore` is a **debug-only** key committed on purpose, so fleet builds share a stable signature. Store/key password: `android`, alias `androiddebugkey`.
- Release keys are never committed (`*.jks` and `release*.keystore` are in .gitignore). Release signing reads `MV_RELEASE_STORE_FILE`, `MV_RELEASE_STORE_PASSWORD`, `MV_RELEASE_KEY_ALIAS` and `MV_RELEASE_KEY_PASSWORD` from the environment.

## Docs
- [User manual](docs/MANUAL.md) (also shown in-app under Help)
- [Privacy policy](PRIVACY.md)

## Disclaimer
Not affiliated with or endorsed by any dating platform. Platform names are used only to identify export formats.

## Backend (Supabase)
- SQL migrations are in `supabase/migrations` (already applied to project `swcotjvqumzgszzhnmks`).
- The app reads `MV_SUPABASE_URL` and `MV_SUPABASE_PUBLISHABLE_KEY` from a Gradle property or env var. The defaults point to the production project. The publishable key is public by design, and access is enforced by RLS. **Never** commit a `service_role` or `sb_secret_` key; the build refuses to embed one.
- Moderators: run `insert into private.admins(user_id) select id from auth.users where email='<email>';` in the SQL editor. The in-app **More → Moderation** screen uses admin-checked security-definer RPCs (`mod_*`), and each action is audited.
- **Account deletion:** in-app (Settings → Delete account) or on the web at https://victorious93.github.io/matchvictorious/delete-account.html (`docs/delete-account.html`, GitHub Pages). Both call the `delete-account` Edge Function in `supabase/functions/`. That function validates the caller's token and uses the runtime-injected service-role key, which never lives in the app or repo.
- **Location:** coarse permission only. Location is snapped to a 0.01° grid on the device and by a DB trigger. Other users cannot read the `location` column (column grants); `discover()` returns only a rounded km distance.
- **Push (FCM):** optional. It is disabled unless `app/google-services.json` exists (CI reads it from the `GOOGLE_SERVICES_JSON` secret). Server credentials go in the Supabase function secret `FCM_SERVICE_ACCOUNT`. See `docs/setup/firebase-push.md`.
- **Calls (LiveKit):** optional. They are disabled until the `LIVEKIT_URL`, `LIVEKIT_API_KEY` and `LIVEKIT_API_SECRET` function secrets are set. See `docs/setup/livekit-calls.md`.
