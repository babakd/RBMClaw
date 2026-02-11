# RBMClaw Agent Context

## What this repo is
- Android OpenClaw node app, customized to integrate Ray-Ban Meta (RBM) glasses.
- Repo URL: https://github.com/babakd/RBMClaw
- Android package: `ai.openclaw.android`

## Current goal
Enable OpenClaw on Android (S25 Ultra + RBM1) to:
- listen via glasses audio path,
- see via continuous RBM camera stream,
- talk with replies routed to glasses.

## Environment and secrets
- Local workspace: `/Users/babakd/RBMClaw`
- Secret file: `/Users/babakd/RBMClaw/.env`
- `.env` contains `github_token` used for Meta DAT Maven package access.
- Never commit `.env` or token values.

## Repository layout (flattened)
- Gradle root files are at repo root:
  - `build.gradle.kts`
  - `settings.gradle.kts`
  - `gradlew`
- App module:
  - `/Users/babakd/RBMClaw/app`
- Design notes:
  - `/Users/babakd/RBMClaw/RBM_OPENCLAW_DESIGN.md`

## Build/install/test commands
- Compile:
  - `cd /Users/babakd/RBMClaw && ./gradlew :app:compileDebugKotlin`
- Unit tests:
  - `cd /Users/babakd/RBMClaw && ./gradlew :app:testDebugUnitTest`
- Install on connected device:
  - `cd /Users/babakd/RBMClaw && ./gradlew :app:installDebug`

## Device status (last verified)
- ADB device connected: Samsung S25 Ultra (`SM-S938U1`).
- App installs and launches successfully.

## Implemented RBM integration

### Build + app setup
- Meta DAT dependencies configured in:
  - `/Users/babakd/RBMClaw/settings.gradle.kts`
  - `/Users/babakd/RBMClaw/app/build.gradle.kts`
- DAT init in:
  - `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/NodeApp.kt`
- Manifest permissions + DAT metadata in:
  - `/Users/babakd/RBMClaw/app/src/main/AndroidManifest.xml`

### RBM modules
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/rbm/RbmDatManager.kt`
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/rbm/RbmAudioRouter.kt`
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/rbm/RbmFrameStore.kt`
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/rbm/RbmTalkAttachmentFactory.kt`

### Runtime integration
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/NodeRuntime.kt`
  - RBM stream lifecycle tied to settings + gateway connection.
  - RBM audio routing (SCO) when Talk Mode + RBM are enabled.
  - `camera.snap` uses fresh RBM frame first; falls back to phone camera.
  - Talk Mode gets optional RBM image attachment source.
- `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/voice/TalkModeManager.kt`
  - `chat.send` now supports attachments from runtime callback.

### UI + permissions
- DAT permission requester:
  - `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/WearablesPermissionRequester.kt`
- Hooked in:
  - `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/MainActivity.kt`
  - `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/MainViewModel.kt`
- RBM settings section:
  - `/Users/babakd/RBMClaw/app/src/main/java/ai/openclaw/android/ui/SettingsSheet.kt`

### Tests added
- `/Users/babakd/RBMClaw/app/src/test/java/ai/openclaw/android/rbm/RbmFrameStoreTest.kt`
- `/Users/babakd/RBMClaw/app/src/test/java/ai/openclaw/android/rbm/RbmTalkAttachmentFactoryTest.kt`

## Fixed issue (important)
- Issue: app opened a missing local scaffold URL and showed `ERR_FILE_NOT_FOUND`.
- Fix:
  - Added local scaffold asset:
    - `/Users/babakd/RBMClaw/app/src/main/assets/CanvasScaffold/scaffold.html`
  - Removed monorepo-only external asset source from:
    - `/Users/babakd/RBMClaw/app/build.gradle.kts`

## Current on-device debugging checklist
1. Open app -> Settings -> Ray-Ban Meta.
2. Tap `Register with Meta AI` and complete Meta flow.
3. Ensure app permissions are granted (camera/mic/bluetooth/notifications as prompted).
4. Confirm RBM status row values:
   - `Reg ...`
   - `Devices ...`
   - `Stream ...`
5. If stream does not start:
   - capture `adb logcat` filtered for `OpenClaw/`.

## Near-term next tasks
- Validate end-to-end RBM registration and streaming on real device.
- Verify Talk Mode sends image attachments from fresh RBM frames.
- Verify audio routing behavior with glasses for listen/speak loop.
- After gateway credentials are provided, run full end-to-end gateway test.

