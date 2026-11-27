# Privacy

- **Offline mode**: recording, editing, noise reduction and Whisper transcription run entirely on the device.
- **Online mode**: audio is uploaded to an external service **only** if you select Online (or confirm the Auto-mode prompt) after accepting an explicit notice. Consent can be withdrawn in Settings → Privacy. Nothing is uploaded automatically or silently.
- Online providers: your own backend (which forwards to a speech-to-text provider) or a provider using *your own* API key, stored encrypted with the Android Keystore.
- Storage: app-private; Android backup disabled; no analytics, ads, Firebase or Supabase.
- Permissions: microphone (only while recording), notifications (recording status), network (model download, optional online AI, premium).
- Premium: the backend stores a random device ID, payment transaction IDs you submit, and plan status. No card/wallet credentials are collected.
- Deletion: Settings → Privacy & data deletes transcripts, recordings, models, API key, or everything local. Backend data deletion is the operator's responsibility (see backend/README.md).
