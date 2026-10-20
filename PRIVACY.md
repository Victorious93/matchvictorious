# MatchVictorious Privacy Policy

_Last updated: 2026-10-20 (draft; legal review pending)_

## Import hub (on-device)
- The app processes only the export files you choose to import. Parsing happens on your device.
- Imports are encrypted with AES-256-GCM (Jetpack Security EncryptedFile) using a key held in the Android Keystore. Android backup and device-transfer are disabled.
- **Imported exports are never uploaded.** That includes other members' names and messages.
- The app never asks for or stores your dating-platform passwords. It uses no automation, accessibility services, notification access or background sync with other apps.

## MatchVictorious account (server)
The app now uses the **INTERNET** permission to run your MatchVictorious account on our backend (Supabase, hosted in AWS us-east-1).
- **What is collected:** your email address and a hashed password (Supabase Auth); the profile fields you save (name, birthdate, optional gender, bio, the platform you bootstrapped from); photos you upload; your likes and passes; matches; chat messages; blocks and reports you file. Supabase may keep technical logs such as IP address and timestamps.
- **What is not collected:** no phone number, no contacts, no precise location (planned as opt-in and approximate), no ad identifiers, no analytics or ad SDKs.
- **Who can see it:** other signed-in members see your profile and approved photos only if your profile is verified and neither of you has blocked the other. Messages are readable only by the two people in a match. Moderators can review reports.
- **Why:** to provide matching and chat, keep members safe (18+ only, moderation, reports) and comply with law. Apparent child sexual abuse material is reported to NCMEC as required by U.S. law.
- **Sharing:** we do not sell or share personal data for advertising. Processors: Supabase (hosting, database, storage, realtime).
- **Retention and deletion:** data is kept while your account exists. Account deletion removes your profile, photos, likes, matches and messages, except where the law requires us to keep reports. In-app and web deletion requests are planned before public launch; until then, contact the operator.
- **Your rights (GDPR/CCPA):** access, correction, deletion, portability and objection. Sensitive data such as gender or orientation is optional and used only to show you to the people you choose.

## Fleet API
An app holding the signature permission can request aggregate counts (number of imports, matches, messages), never content.

## Delete local data
Settings → Delete All Data permanently erases every import, preference and cache on the device. Uninstalling also erases them. Your server account is separate (see above).

## Affiliation
Not affiliated with or endorsed by Tinder, Hinge, Bumble, OkCupid, Match, Plenty of Fish, Grindr, Feeld, Coffee Meets Bagel or Badoo.
