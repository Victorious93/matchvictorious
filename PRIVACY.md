# MatchVictorious Privacy Policy

_Last updated: 2026-10-20 (draft; legal review pending). Account deletion: https://victorious93.github.io/matchvictorious/delete-account.html_

## Import hub (on-device)
- The app processes only the export files you choose to import. Parsing happens on your device.
- Imports are encrypted with AES-256-GCM (Jetpack Security EncryptedFile) using a key held in the Android Keystore. Android backup and device-transfer are disabled.
- **Imported exports are never uploaded.** That includes other members' names and messages.
- The app never asks for or stores your dating-platform passwords. It uses no automation, accessibility services, notification access or background sync with other apps.

## MatchVictorious account (server)
The app now uses the **INTERNET** permission to run your MatchVictorious account on our backend (Supabase, hosted in AWS us-east-1).
- **What is collected:** your email address and a hashed password (Supabase Auth); the profile fields you save (name, birthdate, optional gender, bio, the platform you bootstrapped from); photos you upload; prompt answers; an optional voice intro (up to 30 s audio, private storage, moderated); comments attached to likes (revealed only on a match); your likes and passes; matches; chat messages; blocks and reports you file. Supabase may keep technical logs such as IP address and timestamps.
- **What is not collected:** no phone number, no contacts, no precise location. Location is optional: an approximate point rounded to about 1 km (from Android's coarse location) or a city you pick. Other members see only an approximate distance, no ad identifiers, no analytics or ad SDKs.
- **Who can see it:** other signed-in members see your profile and approved photos only if your profile is verified and neither of you has blocked the other. Messages are readable only by the two people in a match. Moderators can review reports.
- **Why:** to provide matching and chat, keep members safe (18+ only, moderation, reports) and comply with law. Apparent child sexual abuse material is reported to NCMEC as required by U.S. law.
- **Victorious+ purchases:** Google Play handles payment; we never see card details. We store your subscription status, expiry, product and the Play purchase token/order id (to verify renewals). It's deleted with your account.
- **Events:** RSVPs and event group-chat messages are stored. Only verified people who RSVP'd to the same event can see the attendee list and chat. Blocked pairs are hidden from each other.
- **Calls:** voice/video calls run through LiveKit Cloud (a processor) using WebRTC. They are **never recorded**. We store only call metadata (who started it, which match, time, status, video consent) to enforce limits and safety.
- **Safety tools:** Share my date stores only what you enter (name, place, time, check-in time) and is readable through its private link until it expires (≤ 24 h after the date) or you revoke it. SOS location and check-in timers stay on your phone. Nothing is sent without you choosing a recipient in the share sheet.
- **Push notifications:** if enabled, we store this device's Firebase Cloud Messaging token and send notifications through Google FCM. By default notifications contain no names or message text. Previews are opt-in. Tokens are deleted on sign-out and account deletion.
- **Sharing:** we do not sell or share personal data for advertising. Processors: Supabase (hosting, database, storage, realtime) Google Firebase Cloud Messaging (push delivery) and LiveKit (call media relay).
- **Retention and deletion:** data is kept while your account exists. Delete your account in the app (More → Settings → Delete account) or on the web at https://victorious93.github.io/matchvictorious/delete-account.html. Deletion immediately removes your account, profile, location, preferences, photos (records and files), likes, matches, messages, blocks and call records. Safety reports are kept for trust & safety and legal reasons, with the account link removed. Reports about you keep a snapshot of the reported name and bio. Provider backups expire on their schedule.
- **Your rights (GDPR/CCPA):** access, correction, deletion, portability and objection. Sensitive data such as gender or orientation is optional and used only to show you to the people you choose.

## Selfie verification & biometric data
Selfie verification is optional and starts only after you accept a separate biometric consent screen (consent version and time are recorded with each check; this is the written release required by the Illinois Biometric Information Privacy Act (BIPA), Texas CUBI, Washington RCW 19.375 and similar laws).
- **What we collect.** On-device check: Google ML Kit runs on your phone and measures head angle, smile and eye-open probabilities for a random gesture challenge (turn head / smile / blink). Only pass/fail per gesture is sent, plus one selfie. Optional vendor check (Amazon Rekognition Face Liveness, only if enabled): video frames stream to AWS for a liveness score; AWS audit images are disabled and the reference image is discarded. We keep only pass/fail and a confidence number.
- **No face templates.** We never create, store or compare face templates, embeddings or face geometry on our servers. A human moderator compares your selfie with your profile photos.
- **Purpose.** Only to confirm you are a real person who matches your photos. Never for advertising, never sold, leased, traded or otherwise profited from, never disclosed except to the processor named above or when legally required.
- **Retention & destruction.** The selfie is stored in a private bucket readable only by you and moderators, and is deleted immediately after the moderator's decision. Undecided selfies are expired after 30 days (daily job) and swept from storage; deleting your account deletes them too. Check records (status, gesture booleans or confidence, consent version) are deleted with your account. This retention schedule is our public biometric data policy.
- **Withdraw.** Delete your account (Profile → Delete account) or email the privacy contact; any pending selfie is destroyed.

## Fleet API
An app holding the signature permission can request aggregate counts (number of imports, matches, messages), never content.

## Delete local data
Settings → Delete All Data permanently erases every import, preference and cache on the device. Uninstalling also erases them. Your server account is separate (see above).

## Affiliation
Not affiliated with or endorsed by Tinder, Hinge, Bumble, OkCupid, Match, Plenty of Fish, Grindr, Feeld, Coffee Meets Bagel or Badoo.
