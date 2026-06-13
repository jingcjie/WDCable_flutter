# Android / Flutter TODO - Transport Rewrite

Last updated: 2026-06-12

This file is self-contained. An agent working only inside `WDCable_flutter/` should be able to read this file and understand the product direction, current code shape, and next implementation milestones.

## Current Situation

This app is the Android client. Flutter/Dart owns the UI and app state. Native Android Kotlin owns Wi-Fi Direct and transport behavior through the `wifi_direct_cable` MethodChannel.

The Wi-Fi Direct layer is already well tested and should be kept unless a specific bug proves otherwise:

- `MainActivity.kt` receives `WifiP2pInfo`.
- `WiFiDirectManager.kt` handles Android Wi-Fi P2P discovery/connect/disconnect.
- `WiFiDirectBroadcastReceiver.kt` forwards Android P2P broadcasts.

The retired post-link design has been removed from the live path. The upgraded Android path now uses the session runtime for chat, file transfer, speed test, diagnostics, handshake, heartbeat, and teardown. Do not support previous wire formats and do not restore duplicate feature socket paths.

## Completed / Historical Work

- F-01 analyzer cleanup was completed. `flutter analyze` was clean at that point.
- F-02 state cleanup appears partly or mostly implemented in current code. Verify before relying on it.
- F-03 Android permission/lifecycle hardening is considered implemented, but manual testing was not good enough. Treat it as useful cleanup, not proof that the current socket transport is stable.
- F-A through F-D are implemented on Android: framed protocol, session runtime, control-channel chat, bulk file transfer, and bulk speed test.
- Android diagnostics/export work from F-F is implemented enough for Android-to-Android validation.
- Android-to-Android full flow has been manually tested and is generally good. Minor bugs remain for follow-up tuning.
- Android-to-Windows has not been manually tested yet.
- Streaming is dropped from this phase. Do not implement media streaming, microphone capture, playback, jitter buffers, streaming UI, media channels, or media capabilities.

## Target Architecture

After Wi-Fi Direct reports an IP link:

1. One Android session manager owns all app transport setup.
2. Feature services do not read or write sockets directly.
3. The session manager performs protocol handshake, heartbeat, channel setup, teardown, and error reporting.
4. Flutter reaches `Ready` only after the upgraded app protocol is negotiated.
5. Chat, file transfer, speed test, and diagnostics all use the session API.

Target channels:

- `control`: reliable small messages such as handshake, heartbeat, close, error, chat, command, ack, and feature control messages.
- `bulk`: reliable large ordered payloads such as file transfer, speed-test payloads, and diagnostics export.

Streaming scope:

- No streaming implementation work is scheduled.
- Do not add microphone capture, audio playback, codecs, sender pacing, jitter buffers, or streaming controls.
- Do not add media transport or media capability scaffolding.

## Important Source Map

Flutter/Dart:

- `lib/wifi_direct_service.dart`: MethodChannel wrapper and event classes.
- `lib/controllers/wifi_direct_controller.dart`: app state and business logic.
- `lib/models/wifi_direct_models.dart`: state, connection, chat, speed, and file models.
- `lib/widgets/*.dart`: tab UIs.
- `test/wifi_direct_state_test.dart`: current Dart tests.

Android/Kotlin:

- `android/app/src/main/kotlin/com/example/wifi_direct_cable/MainActivity.kt`: service wiring and Wi-Fi Direct callbacks.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/FlutterMethodChannelHandler.kt`: MethodChannel commands.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/WiFiDirectManager.kt`: Android Wi-Fi P2P operations.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/session/SessionManager.kt`: current app transport/session owner.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/protocol/`: Android frame codec and protocol constants.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/diagnostics/DiagnosticsLogger.kt`: Android diagnostic ring buffer/export source.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/ChatService.kt`: calls the session API for control-channel chat.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/SpeedTestService.kt`: calls the session API for bulk speed tests.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/FileTransferService.kt`: calls the session API for bulk file transfer.
- `android/app/src/main/AndroidManifest.xml`: permissions and app declarations.

## Agent Workflow Rules

- Work one milestone at a time, for example: `Do F-B only in WDCable_flutter`.
- Each milestone is intentionally large. Complete implementation, focused tests, and local verification in the same pass.
- Do not ask the user to test after scaffolding-only milestones.
- Ask the user to install/test only at manual gates listed below.
- Keep Android protocol constants, frame layout, state names, capability strings, and channel names aligned with Windows.
- If `../PROTOCOL.md` exists, follow it. If it does not exist and F-A is the current task, create it.
- Do not add feature-level socket reads/writes. All app features should call the session API.
- Do not support previous builds at the protocol layer. When a feature is migrated, delete or disconnect its old socket path.
- Do not implement streaming features in Android.

## Standard Verification Commands

Run from `WDCable_flutter/` unless noted:

```powershell
flutter analyze
flutter test
```

If Kotlin JVM/unit tests are added and Gradle supports them:

```powershell
cd android
.\gradlew.bat testDebugUnitTest
```

If a command cannot run in the local environment, report the reason clearly.

## Manual Test Report Template

Use this only when a milestone says "Manual test gate".

```text
Task ID:
Build installed: yes/no
Android device A: model, Android version
Android device B: model, Android version, or not used
Windows peer used: yes/no, Windows version, app build, or not used
Test scenarios run:
Results:
Crash/hang/stuck: yes/no
If stuck, last visible status/log line:
Screenshots/logs attached: yes/no
Notes:
```

## F-A - Protocol Spec And Android Frame Codec

Goal: define the shared protocol and implement Android frame encode/decode before replacing live feature paths.

No manual device test required.

Codex work:

- [x] Read the retired post-link transport, chat, speed, and file implementations.
- [x] Create or update shared `../PROTOCOL.md` if missing.
- [x] Define Android constants matching the shared spec:
  - magic
  - protocol version
  - frame header size
  - max metadata bytes
  - max payload bytes per frame
  - channel names
  - frame type names/ids
  - capability strings
- [x] Add Kotlin protocol classes in a focused package or files, for example:
  - `ProtocolFrame`
  - `ProtocolFrameType`
  - `ProtocolChannel`
  - `ProtocolError`
  - `ProtocolCodec`
- [x] Implement binary frame encode/decode:
  - fixed magic/version
  - frame type
  - flags
  - channel id or stream id
  - sequence number
  - correlation id
  - metadata length
  - payload length
  - UTF-8 JSON metadata
  - payload bytes
- [x] Enforce maximum metadata and payload sizes.
- [x] Reject malformed magic/version and invalid lengths with typed protocol errors.
- [x] Add tests for valid frames, partial reads, malformed headers, oversized metadata, oversized payload, zero-length payload, and JSON metadata round trip.
- [x] Do not wire new code into live feature flows yet unless the touched feature is fully replaced in the same milestone.
- [x] Run `flutter analyze`.
- [x] Run protocol tests if available.

Done means:

- Android has a tested frame codec.
- `../PROTOCOL.md` documents exactly what Android implemented.
- No agent is instructed to preserve the current wire protocol.

## F-B - Android Session Runtime And Transport Adapter

Goal: create one Android owner for transport/session lifecycle after Wi-Fi Direct connects.

No manual device test required unless the agent has both peer builds available. Local build/test verification is enough for this milestone.

Codex work:

- [x] Add an Android `SessionManager` or equivalent single owner for:
  - current session id
  - peer info
  - role
  - control/bulk sockets or channels
  - accept/connect retries
  - handshake
  - heartbeat
  - teardown
  - disconnect reason
- [x] Add a transport abstraction so raw sockets are hidden behind:
  - connect
  - accept
  - read frame
  - write frame
  - close
  - cancel
- [x] Route post-link setup from `MainActivity.kt` to the session manager.
- [x] Implement connection phases:
  - `WifiDirectConnected`
  - `ConnectingTransport`
  - `Handshaking`
  - `Ready`
  - `Degraded`
  - `Disconnecting`
  - `Disconnected`
  - `Failed`
- [x] Implement handshake with app id, protocol min/max, platform, app version, device name, role, session id, capabilities, and port/channel map.
- [x] Implement heartbeat and timeout.
- [x] Make cleanup idempotent.
- [x] Ensure all long-running reads/writes stop during disconnect, app destroy, or session replacement.
- [x] Add MethodChannel events for session state, session ready, session failed, peer not running app, and disconnect reason.
- [x] Move post-link transport responsibilities to the session manager. Do not leave duplicate lifecycle owners.
- [x] Add focused tests for state transitions where practical.
- [x] Run `flutter analyze`.
- [x] Run `flutter test`.

Done means:

- Android owns app transport lifecycle in one session manager.
- Feature services no longer need to know about sockets.
- Flutter can display the new connection/session states when wired later.

## F-C - Android UI State And Chat On Control Channel

Goal: make `Ready` mean the upgraded handshake is complete, then replace chat with `control` frames.

Manual test gate only after matching Windows support exists, or when testing Android-to-Android with two upgraded Android builds.

Codex work:

- [x] Extend Dart models/events for session state and disconnect reasons.
- [x] Update `WiFiDirectController` so feature tabs are enabled only after `Ready`.
- [x] Keep Wi-Fi Direct "connected" separate from app "ready".
- [x] Replace chat send/receive with `control` frames.
- [x] Include message id, timestamp, sender platform, and session id in chat metadata.
- [x] Add chat send result/failure handling instead of assuming optimistic send success.
- [x] Preserve message ordering per session.
- [x] Show a clear error when the peer is connected by Wi-Fi Direct but not running the upgraded WDCable protocol.
- [x] Delete the old chat socket path from the live app flow.
- [x] Add Dart tests for controller handling of ready/fail/chat events.
- [x] Run `flutter analyze`.
- [x] Run `flutter test`.

Manual test gate:

- [x] Android-to-Android reaches `Ready`.
- [ ] Android-to-Windows reaches `Ready`.
- [x] Send chat messages each direction on Android-to-Android.
- [x] Disconnect and reconnect on Android-to-Android.
- [ ] Connect to a peer that has Wi-Fi Direct but not the upgraded WDCable protocol and confirm clear failure.

Done means:

- Chat no longer depends on newline JSON over a feature-owned socket.
- UI no longer treats Wi-Fi Direct alone as full app readiness.

## F-D - Android Bulk Channel For File Transfer And Speed Test

Goal: replace file transfer and speed test with `bulk` streams.

Manual test gate only after matching peer support exists.

Codex work:

- [x] Implement reliable bulk stream API:
  - open stream
  - send metadata
  - send chunks
  - best-effort ack/error on `control`
  - cancel
  - error
  - close
- [x] Replace Android file send/receive:
  - transfer id
  - safe file name metadata
  - unknown-size content URI support
  - zero-byte file support
  - duplicate filename handling
  - partial-file cleanup
  - checksum or hash on complete when practical
- [x] Replace Android speed test:
  - test id
  - upload/download direction
  - timeout
  - cancel
  - failure result
  - no concurrent tests on the same session unless explicitly supported
- [x] Remove delimiter-sensitive file header parsing from the live path.
- [x] Remove speed-test string headers from the live path.
- [x] Update Flutter transfer/speed states from protocol progress events.
- [x] Run `flutter analyze`.
- [x] Run `flutter test`.

Manual test gate:

- [x] Transfer files Android-to-Android.
- [x] Transfer a duplicate filename twice Android-to-Android.
- [x] Transfer Android content URI media Android-to-Android.
- [ ] Transfer a zero-byte file if available.
- [ ] Interrupt transfer by closing peer app or turning Wi-Fi off.
- [x] Run upload and download speed tests Android-to-Android.
- [x] Disconnect during speed test and reconnect Android-to-Android.

Done means:

- File and speed no longer depend on feature-owned sockets.
- Interrupted transfers/tests fail cleanly and do not corrupt the next operation.

## F-F - Android Diagnostics And Release Gate

Goal: make Android diagnosable and ready for cross-device beta/store validation.

Manual test gate required.

Codex work:

- [x] Add structured ring-buffer logging for:
  - Wi-Fi Direct
  - session
  - transport
  - protocol
  - control
  - bulk
  - chat
  - file
  - speed
  - permissions
  - UI
- [x] Add export/copy logs from Android UI.
- [x] Include timestamp, session id, peer platform, role, channel, stream id, transfer id/test id, and disconnect reason in important logs.
- [x] Add protocol and session tests where practical.
- [x] Delete remaining raw socket feature code and stale scaffolding after chat/file/speed use the new runtime.
- [x] Ensure `flutter analyze` is clean.
- [x] Ensure `flutter test` passes.
- [x] Update README or release notes with the new manual QA checklist.

Manual test gate:

- [x] Android-to-Android full flow.
- [ ] Android-to-Windows full flow in both initiation directions.
- [x] Chat, file, and speed on Android-to-Android.
- [ ] Peer app missing.
- [ ] Outdated app build shows a clear upgrade-required failure.
- [ ] Reconnect loop 10 times.
- [ ] Wi-Fi off mid-transfer and during speed test.
- [x] Export logs available after one success and one failure.

Done means:

- Android-to-Android chat/file/speed validation is complete enough for follow-up tuning.
- Android-to-Windows coordinated release validation is still pending.
