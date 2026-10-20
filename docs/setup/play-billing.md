# Victorious+ via Google Play Billing — setup

Victorious+ is **convenience only**: see who liked you, rewind your last pass (24h), extra filters (has voice intro / has prompts).
It never changes ranking or visibility. Until the steps below are done, the app shows "Victorious+ isn't available yet" and
the `play-verify` function returns `503 {disabled:true}`.

## 1. Play Console
1. Create the app `com.victorious.match` in Play Console and upload a signed release (internal testing track is enough) — Billing only works for builds installed from Play.
2. Set up a **payments profile** (Monetize → Payments profile).
3. Monetize → Products → **Subscriptions** → Create subscription, Product ID **`victorious_plus`**. Add one base plan (e.g. monthly, auto-renewing) and activate it.
4. Add license testers (Setup → License testing) so test purchases are free.

## 2. Service account for server-side verification
1. Google Cloud console (any project) → enable **Google Play Android Developer API**.
2. IAM → Service accounts → create one (e.g. `play-verify`) → Keys → Add key → JSON. Keep the file private; never commit it.
3. Play Console → Users and permissions → Invite new users → the service account email → app permissions for `com.victorious.match`: **View financial data** and **Manage orders and subscriptions**.

## 3. Supabase secrets (never in the repo/app)
```
supabase secrets set --project-ref swcotjvqumzgszzhnmks \
  PLAY_PACKAGE_NAME=com.victorious.match \
  PLAY_PRODUCT_IDS=victorious_plus \
  PLAY_SERVICE_ACCOUNT="$(cat play-verify-key.json)"
```
(or Dashboard → Edge Functions → Secrets).

## How it works
- The app launches the Play purchase with `obfuscatedAccountId = your user id`.
- After purchase the app sends the purchase token to `play-verify`, which calls the Play Developer API (`purchases.subscriptionsv2.get`),
  checks product + account id, refuses tokens already bound to another user, acknowledges the purchase, and writes `public.entitlements`.
- Users can read only their own entitlement (not the token) and cannot write it. Perks are enforced in the database (`private.has_plus`).
- "Restore purchase" re-sends owned subscriptions. Optional later: Real-time developer notifications (Pub/Sub) to refresh renewals/cancellations automatically; until then status refreshes whenever the user opens Victorious+ → Restore.
