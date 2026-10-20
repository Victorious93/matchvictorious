# Enabling push notifications (FCM) — steps for Don

The app and backend are already wired. Push stays **off** until these two credentials exist. Neither is ever committed.

## 1. Create the Firebase project
1. Go to https://console.firebase.google.com → **Add project** → name it `matchvictorious`. Google Analytics can be off.
2. In the project, click **Add app → Android**:
   - Package name: `com.victorious.match`
   - Nickname: MatchVictorious
   - SHA-1: optional (not needed for FCM)
3. Click **Download google-services.json**. Skip the SDK steps (already done).
4. **Project settings → Cloud Messaging**: confirm *Firebase Cloud Messaging API (V1)* is **Enabled**. The legacy API is not used.

## 2. Create the service-account key (for the server)
1. **Project settings → Service accounts → Generate new private key** → this downloads a JSON file.
2. Treat it like a password. Do not email it in plain text and do not commit it.

## 3. Hand them over
- **Service account → Supabase function secret** (you can do this yourself):
  - Supabase dashboard → project `matchvictorious` → **Edge Functions → Secrets** → add `FCM_SERVICE_ACCOUNT` = the *entire* JSON file contents.
  - Or with the CLI: `supabase secrets set --project-ref swcotjvqumzgszzhnmks FCM_SERVICE_ACCOUNT="$(cat service-account.json)"`
- **google-services.json → GitHub Actions secret**:
  - GitHub repo → Settings → Secrets and variables → Actions → **New repository secret** `GOOGLE_SERVICES_JSON` = the file contents.
  - CI writes it to `app/google-services.json` before building. The file is in `.gitignore`.
  - Locally: put it at `app/google-services.json`.
  - Without the file the build still works, with push disabled.
- Then tell the bot "Firebase is set". It will verify end-to-end (register a test device, trigger a match/message, check the `send-push` logs).

## How it works
- `device_tokens` table (RLS: own rows only). The app registers its token after sign-in and on rotation, and deletes it on sign-out and account deletion.
- DB triggers on `matches` / `messages` insert call the `send-push` Edge Function via `pg_net` with only `{type, id}`. The function re-reads the row with the service role, claims `push_sent_at` (so it sends at most once), skips blocked or unmatched pairs, and sends FCM HTTP v1 messages.
- The text is generic by default ("You have a new match" / "New message"). Names and message previews are included only if the recipient enables Settings → Notifications → Show previews.
- Until `FCM_SERVICE_ACCOUNT` is set, the function returns `{"skipped": ...}`.
