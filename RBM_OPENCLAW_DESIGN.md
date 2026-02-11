# RBM + OpenClaw Integration Design (Android Node)

## Goal
Connect Ray-Ban Meta glasses (RBM1) to OpenClaw Android node on Samsung S25 Ultra so OpenClaw can:
- listen via glasses microphone,
- see via glasses camera continuously,
- talk via glasses speakers.

This design keeps OpenClaw gateway protocol stable and focuses integration in the Android node app.

## Chosen approach
Use OpenClaw Android node architecture (node + operator WS sessions) and add a new RBM subsystem.

Why this path:
- OpenClaw already supports camera/image attachments in `chat.send`.
- Node runtime already has Talk Mode + camera command hooks.
- We can avoid adding a new OpenClaw channel/plugin and avoid disruptive gateway protocol changes.

## Upstream references
- OpenClaw Android node runtime:
  - `/tmp/openclaw-upstream/apps/android/app/src/main/java/ai/openclaw/android/NodeRuntime.kt`
  - `/tmp/openclaw-upstream/apps/android/app/src/main/java/ai/openclaw/android/voice/TalkModeManager.kt`
  - `/tmp/openclaw-upstream/apps/android/app/src/main/java/ai/openclaw/android/node/CameraCaptureManager.kt`
- GlassesGemini Meta DAT usage:
  - `/tmp/glasses-gemini/app/src/main/java/com/glassesgemini/app/glasses/GlassesStreamManager.kt`
  - `/tmp/glasses-gemini/app/src/main/java/com/glassesgemini/app/audio/BluetoothAudioManager.kt`
  - `/tmp/glasses-gemini/app/src/main/AndroidManifest.xml`

## Architecture

### New components (Android app)
1. `RbmDatManager`
- Responsibility: Meta DAT registration, device discovery, camera permission, stream session lifecycle.
- Produces a continuous JPEG frame stream and a latest-frame cache (`FrameCache`).

2. `RbmAudioRouter`
- Responsibility: Bluetooth route management for glasses audio input/output.
- Handles SCO start/stop for input preference and audio mode transitions.

3. `RbmVisionPipeline`
- Responsibility: throttled frame handling (default 1 fps), ring buffer/latest-frame pointer, freshness checks.
- Provides `latestFrameForChat()` and `latestFrameForSnap()`.

### Existing components to integrate
1. `NodeRuntime`
- Owns lifecycle and cross-module orchestration.
- Starts/stops RBM streaming based on connection + user settings.

2. `TalkModeManager`
- On each user transcript finalization, include latest RBM frame as a `chat.send` image attachment.
- Keep existing SpeechRecognizer + ElevenLabs logic (easiest path).

3. `CameraCaptureManager`
- `camera.snap`: prefer latest RBM frame if fresh; fallback to local phone camera capture.
- `camera.clip`: keep phone camera in v1.

4. Settings UI
- Add RBM section:
  - Register/unregister with Meta AI app.
  - Camera permission status and request action.
  - Stream status and high/medium resolution toggle.
  - “Use RBM for Talk Mode vision” toggle.

## Data flow

### Listen (audio in)
RBM mic -> Bluetooth route (SCO preferred) -> Android SpeechRecognizer (Talk Mode) -> `chat.send` text

### See (continuous vision)
RBM camera (DAT stream) -> frame conversion/compression -> 1 fps cache update ->
- Talk Mode turn: attach latest image to `chat.send`
- `camera.snap`: return latest image payload

### Talk (audio out)
Assistant text -> existing TalkMode TTS (ElevenLabs/system fallback) -> Android audio output routed to glasses

## Continuous vision behavior (v1)
- Continuous frame acquisition is always-on only while:
  - RBM enabled in settings,
  - app connected to gateway,
  - glasses connected and permission granted.
- Frames are cached continuously (1 fps default).
- The freshest frame is attached automatically to each Talk Mode request.

This delivers continuous sensing without creating a new model run per frame.

## Protocol impact
No OpenClaw gateway protocol change required for v1.
- Reuse existing `chat.send` attachments path.
- Reuse existing `camera.snap` node command.

## Android build/config impact

### Dependencies
Add Meta DAT SDK artifacts in Android Gradle dependencies and GitHub Packages repository.

### Manifest additions
- DAT metadata and callback scheme.
- Any additional Bluetooth permissions required for SCO reliability on modern Android.

### App startup
Initialize Meta Wearables SDK in app startup (`NodeApp`).

## Security/secrets
- Workspace `.env` currently has `github_token` (lowercase).
- Build expects `GITHUB_TOKEN` for Meta DAT package pull.
- During implementation, map `github_token` -> `GITHUB_TOKEN` in build tooling (without exposing token values).

## Rollout plan

### Milestone 1 (core integration)
- Bring OpenClaw Android app code into workspace.
- Add DAT dependency + initialization + registration flow.
- Add RBM frame stream + cache.
- Wire `camera.snap` RBM-first fallback path.

### Milestone 2 (talk integration)
- Add RBM frame attachment to each Talk Mode `chat.send`.
- Add Bluetooth audio routing helper and hook into Talk Mode lifecycle.

### Milestone 3 (UX and hardening)
- Settings panel controls/status.
- Reconnect/backoff behavior for DAT stream and SCO.
- Telemetry/logging + error states.
- Basic tests for frame freshness selection and attachment injection.

## Known constraints
- OpenClaw `chat.send` attachment parser currently only consumes image attachments for model image context; non-image attachments are ignored by that path.
- SpeechRecognizer microphone routing with SCO is platform-dependent; we keep robust fallback behavior.

## What is still needed from user
1. Gateway connect details for end-to-end test:
- host + port,
- auth mode (token/password),
- token/password,
- TLS requirement/fingerprint policy.

Everything else needed for v1 is already provided:
- target device: S25 Ultra + RBM1,
- Meta package token in `.env`.
