# Optional: vendor liveness (Amazon Rekognition Face Liveness)

Without these secrets the `liveness` Edge Function answers `{vendor:false}` / 503 and the app uses the on-device ML Kit challenge + moderator review. Nothing breaks.

1. AWS account → IAM: create user `mv-liveness-server` with `rekognition:CreateFaceLivenessSession`, `rekognition:GetFaceLivenessSessionResults`, `sts:AssumeRole` (on the role below). Create an access key.
2. IAM role `mv-liveness-client` trusted by that user, permission only `rekognition:StartFaceLivenessSession`.
3. Supabase → Edge Functions → Secrets (never in the app):
   `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_REGION` (e.g. `us-east-1`), `AWS_LIVENESS_ROLE_ARN`, optional `LIVENESS_MIN_CONFIDENCE` (default 90).
4. Deploy: `supabase functions deploy liveness`.
5. App: the vendor capture UI needs the AWS Amplify `FaceLivenessDetector` component (`com.amplifyframework.ui:liveness`); add it and set `LivenessApi.VENDOR_CAPTURE_UI = true`. Until then the app keeps using the on-device provider even if secrets exist.
6. Sign AWS's data processing addendum; add AWS as a biometric processor in PRIVACY.md (already named) and keep audit images disabled (`AuditImagesLimit: 0`, no S3 bucket).
