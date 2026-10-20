# Enabling in-app calls (LiveKit Cloud): steps for Don

Calls are built and deployed but **disabled** until three Supabase function secrets exist. Until then the app says "Calls aren't available yet".

## 1. Create a LiveKit Cloud project
1. Sign up at https://cloud.livekit.io (GitHub or Google login). The free **Build** plan includes 5,000 WebRTC participant-minutes/month. *Ship* is $50/mo with 150k minutes (see platform-plan.md §3b).
2. **Create project** → name it `matchvictorious` and pick the region closest to your launch city (e.g. US East).
3. Open **Settings → Keys** (API Keys) → **Create key**. Copy:
   - **URL**: `wss://<project>.livekit.cloud`
   - **API Key**: `API...`
   - **API Secret**: shown once. Treat it like a password.
4. Leave Egress/Recording **off**. The app never records and tokens never grant recording.

## 2. Hand the values over as Supabase secrets (never commit them)
Supabase dashboard → project `matchvictorious` → **Edge Functions → Secrets** → add:
- `LIVEKIT_URL` = `wss://<project>.livekit.cloud`
- `LIVEKIT_API_KEY` = the key
- `LIVEKIT_API_SECRET` = the secret

Or with the CLI: `supabase secrets set --project-ref swcotjvqumzgszzhnmks LIVEKIT_URL=... LIVEKIT_API_KEY=... LIVEKIT_API_SECRET=...`

No app rebuild is needed. Tell the bot "LiveKit is set" and it will run an end-to-end test.

## How it works
- Calls exist only between matched users, and only after **both** tap "Allow calls" (`call_consents`).
- `start_call` / `respond_call` / `end_call` / `set_video_ok` RPCs enforce: membership, no blocks, a 60 s ring timeout, one live call per match, and **20 started calls per user per 24 h**. Blocking ends live calls.
- `livekit-token` Edge Function checks the caller's session and call membership, then mints a 10-minute token for that one room. It has publish/subscribe grants only (no recording/admin). Audio-only calls can't publish camera.
- Audio first: the camera is off until you turn it on. Remote video stays covered until **both** people tap "Accept video".
- An in-call **Report** button reports + blocks + ends the call.
