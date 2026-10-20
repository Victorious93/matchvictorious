# MatchVictorious Manual

MatchVictorious is a privacy-first hub for the data exports you request from dating platforms. It reads exports on your phone, encrypts them and shows your profiles, matches and messages in one place.

## Why import, not sync?
None of the supported platforms (Tinder, Hinge, Bumble, OkCupid, Match, Plenty of Fish, Grindr, Feeld, Coffee Meets Bagel, Badoo) offers a public API or third-party login. Their terms forbid bots and unofficial clients. The only legitimate path is your legal right to a copy of your data (GDPR/CCPA). So MatchVictorious:
- never asks for your dating-app passwords
- never automates other apps, uses no accessibility service and reads no notifications
- has no live sync and no internet permission

## First launch
Read the consent screen, tick the box and tap Continue. You can withdraw consent at any time with Settings → Delete All Data.

## Getting your export
Open More → Request your data. Each platform card explains the official steps and links to the official help page or portal.
- Tinder: account.gotinder.com/download. The emailed link expires after 24 hours.
- Hinge and Grindr: in-app Settings → Download My Data.
- Bumble and Badoo: contact support and request your data.
- Others: privacy request through the platform's help center.
Exports take anywhere from minutes to 30 days.

## Importing
1. Tap Import.
2. Choose the platform.
3. Tap Pick file and select the ZIP, JSON or HTML export from your storage (Storage Access Framework).
4. The file is parsed on the device and saved encrypted. The original file is not copied or changed.

Tinder exports have a dedicated parser (data.json: User, Messages, Usage). Other platforms use a generic parser that picks up profile fields and anything that looks like messages. Notes on the Profiles card say what was and was not recognised.

## Screens
### Profiles
Your own profile from each import, plus usage totals (app opens, swipes, matches).
### Matches
Conversations found in your exports. Tinder exports identify matches only as "Match N".
### Messages
A read-only view of imported messages. Exports usually contain only your side of each chat.
### Filters
Filter by platform, search text, and show only matches that have messages. Filters apply to Profiles, Matches and Messages.
### Import
Pick a file, open the official app, or delete individual imports.
### Settings
A privacy summary, fleet API info, and **Delete All Data**.

## Privacy and security
- Storage: AES-256-GCM EncryptedFile (Jetpack Security). The key lives in the Android Keystore.
- Backups and device transfer are disabled.
- No INTERNET permission, so the app cannot upload anything.
- Exports contain other people's personal data. Keep them private.
- Not affiliated with or endorsed by any dating platform.

## Fleet API
MatchVictorious is an EXTERNAL_NODE in the Victorious fleet and can be controlled by droidcommand-AI. Every entry point requires the signature-level permission

    com.victorious.match.permission.FLEET_CONTROL

Only apps signed with the same certificate as MatchVictorious hold it. In this repo that is the committed fleet debug key; release builds use whatever key you sign with.

### STATUS (broadcast)
    adb shell am broadcast -a com.victorious.match.action.STATUS -n com.victorious.match/.fleet.FleetReceiver
From code, send an ordered broadcast and read the result extras, or pass `reply_package` to get a `com.victorious.match.action.STATUS_REPLY` broadcast sent to that package (also guarded by FLEET_CONTROL).
Result extras (aggregate counts only, never names or message text): node, role=EXTERNAL_NODE, version, consent, imports, matches, messages, live_sync=false, network=false.

### PING (broadcast)
Same as STATUS. Use it as a liveness check.

### OPEN_SCREEN (activity)
    adb shell am start -a com.victorious.match.action.OPEN_SCREEN -n com.victorious.match/.fleet.OpenScreenAlias --es screen matches
Screens: profiles, matches, messages, filters, import, guide, help, settings. If consent has not been given yet, the consent screen shows first.

Kotlin example (in droidcommand-AI, which must declare `<uses-permission android:name="com.victorious.match.permission.FLEET_CONTROL"/>`):

    context.sendOrderedBroadcast(Intent("com.victorious.match.action.STATUS").setPackage("com.victorious.match"),
        "com.victorious.match.permission.FLEET_CONTROL", resultReceiver, null, 0, null, null)

Note: adb shell holds no app signature, so the adb commands above work only on debuggable/rooted setups. Use them as reference.

## Troubleshooting
- "data.json not found in ZIP": pick the original Tinder ZIP, or extract data.json and import that.
- Generic import found nothing: the platform's format is undocumented. The notes show what was read.
- Files larger than 200 MB are rejected. Remove the photo folders from the ZIP first.
