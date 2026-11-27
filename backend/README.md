# ECHORA backend

Node 20 service: device entitlements (signed JWT), manual-payment workflow with admin verification, and an optional
transcription proxy so the provider key never ships in the APK.

```bash
cd backend && cp .env.example .env     # set JWT_SECRET, ADMIN_TOKEN (32+ chars each), OPENAI_API_KEY (optional)
npm install && npm test && npm start   # serve ONLY behind HTTPS (reverse proxy / platform TLS)
docker build -t echora-backend . && docker run -p 8080:8080 --env-file .env -v echora-data:/app/data echora-backend
```
Endpoints: `GET /v1/entitlement`, `GET /v1/plans`, `POST|GET /v1/payments`, `POST /v1/transcribe`,
admin `GET /admin/payments`, `POST /admin/payments/:id/{verify|reject|refund}`, `GET /healthz`.

Security: helmet, 10 KB JSON limit, per-route rate limits, strict input validation, parameterised SQL, constant-time admin
token compare, unique transaction references (duplicate protection), idempotency keys, status history table, 72 h pending expiry,
upload cap 25 MB. Limits: identity is an anonymous device ID (no accounts), so a plan follows the app install; add accounts before
selling at scale. Tests (`npm test`) require `npm install` and were **not run** where this was authored (no network).
Verify the OpenAI endpoint/model names against https://developers.openai.com/api/docs/guides/speech-to-text before launch.
