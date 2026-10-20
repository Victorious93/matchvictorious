# MatchVictorious Manual

MatchVictorious is a privacy-first dating platform. It has two parts:
- **The platform** (Discover, Chats, Profile). This is a MatchVictorious account that uses an email address. No phone number is ever needed.
- **The import hub**. It reads the data exports you request from other dating apps on your phone and stores them encrypted. You can use one to pre-fill your MatchVictorious profile.

## Why import, not sync?
None of the supported platforms (Tinder, Hinge, Bumble, OkCupid, Match, Plenty of Fish, Grindr, Feeld, Coffee Meets Bagel, Badoo) offers a public API or third-party login. Their terms forbid bots and unofficial clients. The only legitimate path is your legal right to a copy of your data (GDPR/CCPA). So MatchVictorious:
- never asks for your dating-app passwords
- never automates other apps, uses no accessibility service and reads no notifications
- has no live sync with other platforms. Imports never leave your phone.

## First launch
Read the consent screen, tick the box and tap Continue. You can withdraw consent at any time with Settings → Delete All Data.

## Your MatchVictorious account
1. Open Discover, Chats or Profile and enter an email address and password (8+ characters). Tap **Create account**. If email confirmation is on, confirm the link in your inbox and then tap **Sign in**.
2. Open **Profile**. Enter your name, birthdate (YYYY-MM-DD, 18+ only), optional gender and a bio, then tap **Save**. **Add photo** uploads a picture (JPEG, PNG or WebP, up to 5 MB) to private storage. Photos are visible only after moderation.
3. **Bootstrap from an import:** go to More → Imported profiles and tap **Use for my MatchVictorious profile** on an import. Name, birthdate, gender and bio are copied into the Profile form. Nothing is uploaded until you review the form and tap Save. Your matches and messages from other apps are never uploaded.
4. **Discover** shows verified members who match your preferences. Tap Like or Pass. When two people like each other they **match**. A match expires after 14 days.
5. **Chats** lists your matches. Open one to chat in real time. Use **Safety** in a chat to Unmatch, Block, or Report & block.
6. **Location (optional):** on Profile tap **Use my approximate location**, read the explanation and allow *approximate* location. It is rounded to about 1 km on the phone and again on the server. If you deny the permission or prefer not to share, tap **Choose city**. Other members see only an approximate distance ("About 7 km away") and never coordinates.
7. **Discovery preferences:** set an age range and maximum distance on Profile and tap Save preferences. Discover respects both.
8. **Photos in Discover:** approved photos appear as a swipeable carousel through short-lived private links. A profile without photos shows its initial instead.
9. **Delete account:** More → Settings → **Delete account**, type DELETE and confirm. This removes your account, profile, photos (including the files), likes, matches, messages and location from the server. Safety reports are kept without your account link. Web alternative: https://victorious93.github.io/matchvictorious/delete-account.html
10. **Avatars:** Chats shows *New matches* (no messages yet) as a row of avatars and *Chats* as a list. The chat header shows the other person's first approved photo through a short-lived private link, or their initial.
11. **Notifications:** after sign-in, Android 13+ asks for notification permission (after an explanation). Notifications say only "You have a new match" or "New message". Turn on **Settings → Notifications → Show previews** to include names and message text. Nothing is sent to blocked or unmatched people. Signing out or deleting the account removes this device's push token. Builds without Firebase configured show "Push notifications are not configured in this build".
12. Verification: only verified profiles appear in Discover. During the beta a moderator verifies profiles. Selfie liveness verification is planned.

## Prompts, voice intro and icebreakers
- **Prompts:** on Profile, choose up to 3 prompts from the curated list, answer each (max 200 characters) and tap Save prompts.
- **Voice intro:** tap Record (microphone permission). Recording stops automatically at 30 seconds. The clip is stored privately and reviewed by moderators before others can hear it. You can play it, re-record it or delete it.
- **Like with comment:** in Discover, tap **♥ Like this answer** under someone's prompt and optionally add a comment (max 300 characters). The comment is revealed only if you match: it becomes the first message in the chat.
- **Icebreakers:** an empty chat shows suggested openers based on the other person's prompts. Tapping one fills the message box. Nothing is sent until you tap Send.

## Voice and video calls
- In a chat, tap 📞 (voice) or 🎥 (video). The first time, you're asked to **Allow calls** with that match. A call can only start once you **both** allow calls. You can withdraw at any time.
- The other person gets an incoming-call prompt while the app is open, and can Accept or Decline. Unanswered calls stop ringing after 60 seconds.
- Audio first: your camera stays off until you tap **Camera on**. The other person's video is covered until you **both** tap **Accept video**.
- **Report** during a call reports and blocks the person and ends the call. Calls are never recorded. Each person can start up to 20 calls per day.
- If calls aren't set up on the server yet, you'll see "Calls aren't available yet".

## Victorious+
- More → **Victorious+**: see who liked you, rewind your last pass (within 24h; also the ↶ Rewind button in Discover), and extra filters (only people with a voice intro / prompts).
- Convenience only: it never boosts you or changes the order anyone sees.
- Purchases go through Google Play and are verified on our server. Manage/cancel in Google Play → Subscriptions. **Restore purchase** re-checks your subscription.
- Setup for the owner: docs/setup/play-billing.md.

## Events and group dates
- More → **Events & group dates** lists upcoming events. Filter by city with the city button.
- Tap an event, then **RSVP** (verified members only; capacity limits apply). After you RSVP you can see who else is going and join the event's **group chat**. People you've blocked (or who blocked you) are hidden from each other.
- **Cancel RSVP** removes you from the list and the chat.
- Moderators create events in Moderation → **Events** (title, description, city, venue, start/end, capacity). Creating events is audited.

## Safety center
Open it from More → Safety center (or Safety → Share my date inside a chat).
- **SOS:** opens your phone dialer with 911 filled in. You press call. **Share my location** opens the Android share sheet with your approximate location (a map link) for you to send to someone you trust. Nothing is ever sent automatically.
- **Check-in timer:** set a reminder (0.5–6 h). When it fires, a notification asks "Are you OK?". Tap **I'm safe** to cancel it early. Nobody is contacted automatically.
- **Share my date:** pick a match (optional), enter who, where and when, then tap Create link & share. MatchVictorious creates a private link (https://victorious93.github.io/matchvictorious/date.html?t=…) showing only those details. The link expires 12 hours after the date starts, and the share sheet lets *you* send it. Creating the link also sets a check-in reminder.
- **Safety tips** for meeting safely.

## Moderation (moderators only)
**Make yourself a moderator (Don, once):**
1. Create your MatchVictorious account in the app (Profile → Create account) and save a profile.
2. In the Supabase dashboard → SQL Editor for project `matchvictorious`, run:

    insert into private.admins(user_id) select id from auth.users where email='<your email>';

3. Restart the app. **More → Moderation** now appears. It is hidden for everyone else, and every moderation action is re-checked on the server, so a modified app gains nothing. To remove a moderator, run `delete from private.admins where user_id = '<uuid>';`.

**Queues:**
- **Profiles:** unverified or pending profiles with their photos. **Verify** makes the profile visible in Discover. **Reject** needs a reason, and the user sees it on their Profile screen.
- **Voice:** pending voice intros. **Listen**, then **Approve**, or **Reject** with a reason.
- **Photos:** pending uploads. **Approve** makes a photo visible to others. **Reject** needs a reason.
- **Reports:** open reports with the reason, details and a snapshot of the reported name and bio taken when the report was filed (kept even if the account is deleted).
  - **Dismiss** closes the report.
  - **Warn** adds a warning and shows the user your note.
  - **Ban** suspends the account. Banned users disappear from Discover and from other people's chats.
  - **Unban** reinstates the account.

Every action is written to the audit log (`private.audit_log`). Users see verification results, warnings and suspensions in a "Message from moderators" card on their Profile screen.

## Getting your export
Open More → Request your data. Each platform card explains the official steps and links to the official help page or portal.
- Tinder: account.gotinder.com/download. The emailed link expires after 24 hours.
- Hinge and Grindr: in-app Settings → Download My Data.
- Bumble and Badoo: contact support and request your data.
- Match: help.match.com → Requesting Your Data (Contact Us → I have a Safety or Privacy concern → I want to access my personal data).
- Others: privacy request through the platform's help center.
Exports take anywhere from minutes to 30 days.

## Importing
1. Tap Import.
2. Choose the platform.
3. Tap Pick file and select the ZIP, JSON or HTML export from your storage (Storage Access Framework).
4. The file is parsed on the device and saved encrypted. The original file is not copied or changed.

Tinder exports have a dedicated parser (data.json: User, Messages, Usage). Other platforms use a generic parser that picks up profile fields and anything that looks like messages. Notes on the Profiles card say what was and was not recognised.

## Screens
### Discover / Chats / Profile
The platform screens described above.
### Imported profiles (More)
Your own profile from each import, plus usage totals (app opens, swipes, matches).
### Imported matches (More)
Conversations found in your exports. Tinder exports identify matches only as "Match N".
### Imported messages (More)
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
- The app uses the INTERNET permission only for your MatchVictorious account (Supabase backend over HTTPS). Imported exports are never uploaded.
- Server access is limited by row-level security. Other members see your profile only if it is verified and neither of you has blocked the other. Only the two people in a match can read its messages.
- No phone number is collected anywhere.
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

## Get verified (selfie liveness)
More → **Get verified**. Read and accept the biometric consent, allow the camera, then follow three random prompts (turn left/right, smile, blink) with only your face in frame. When the prompts are done, look straight at the camera and a selfie is taken automatically. You have 60 seconds per attempt and 5 attempts per day. A moderator compares the selfie with your photos; you see the result on your Profile card. The selfie is deleted as soon as they decide (at most 30 days if never reviewed).

**Moderators:** Moderation → **Selfies** shows the live selfie next to the profile photos and the gesture results. "Same person — verify" or "Reject" (reason required) deletes the selfie right away. Use "Purge expired selfies" occasionally to sweep any leftover objects.

**Vendor option:** if AWS Rekognition Face Liveness secrets are set (docs/setup/liveness-vendor.md) the server-side vendor check is enabled; until then the on-device check is used.
