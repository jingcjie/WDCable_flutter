# Android / Flutter TODO - Transport Rewrite

Last updated: 2026-06-12

This file is self-contained. An agent working only inside `WDCable_flutter/` should be able to read this file and understand the product direction, current code shape, and next implementation milestones.

## Current Situation

This app is the Android client. Flutter/Dart owns the UI and app state. Native Android Kotlin owns Wi-Fi Direct and transport behavior through the `wifi_direct_cable` MethodChannel.

The Wi-Fi Direct layer is already well tested and should be kept unless a specific bug proves otherwise:

- `MainActivity.kt` receives `WifiP2pInfo`.
- `WiFiDirectManager.kt` handles Android Wi-Fi P2P discovery/connect/disconnect.
- `WiFiDirectBroadcastReceiver.kt` forwards Android P2P broadcasts.

The part to replace is everything after Wi-Fi Direct creates the network link:

- `SocketConnectionManager.kt` owns three raw TCP channels.
- Chat uses port `8888`.
- Speed test uses port `8889`.
- File transfer uses port `8890`.
- `ChatService.kt`, `SpeedTestService.kt`, and `FileTransferService.kt` parse and write ad hoc string headers directly on sockets.

That raw socket feature design is the source of fragility and future upgrade cost. The rewrite should replace it directly. Do not support the previous wire protocol and do not keep duplicate feature paths.

## Completed / Historical Work

- F-01 analyzer cleanup was completed. `flutter analyze` was clean at that point.
- F-02 state cleanup appears partly or mostly implemented in current code. Verify before relying on it.
- F-03 Android permission/lifecycle hardening is considered implemented, but manual testing was not good enough. Treat it as useful cleanup, not proof that the current socket transport is stable.

## Target Architecture

After Wi-Fi Direct reports an IP link:

1. One Android session manager owns all app transport setup.
2. Feature services do not read or write sockets directly.
3. The session manager performs protocol handshake, heartbeat, channel setup, teardown, and error reporting.
4. Flutter reaches `Ready` only after the upgraded app protocol is negotiated.
5. Chat, file transfer, speed test, diagnostics, and streaming all use the session API.

Target channels:

- `control`: reliable small messages such as handshake, heartbeat, close, error, chat, command, ack, and stream start/stop.
- `bulk`: reliable large ordered payloads such as file transfer, speed-test payloads, and diagnostics export.
- `realtime`: low-latency streaming path. Audio streaming is the first streaming milestone. Keep this separate from `bulk` so file transfer does not block audio.

Streaming MVP:

- Bidirectional audio streaming over Wi-Fi Direct.
- Start with PCM16 mono audio if that is the fastest stable cross-platform path.
- Add Opus or another codec only after Android and Windows dependency choices are explicit.
- Do not start video or screen streaming until audio is stable.

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
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/SocketConnectionManager.kt`: current raw socket owner. Replace it with the session runtime.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/ChatService.kt`: current chat protocol. Replace its socket path.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/SpeedTestService.kt`: current speed protocol. Replace its socket path.
- `android/app/src/main/kotlin/com/example/wifi_direct_cable/FileTransferService.kt`: current file protocol. Replace its socket path.
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

- [ ] Read the current protocol in `SocketConnectionManager.kt`, `ChatService.kt`, `SpeedTestService.kt`, and `FileTransferService.kt`.
- [ ] Create or update shared `../PROTOCOL.md` if missing.
- [ ] Define Android constants matching the shared spec:
  - magic
  - protocol version
  - frame header size
  - max metadata bytes
  - max payload bytes per frame
  - channel names
  - frame type names/ids
  - capability strings
- [ ] Add Kotlin protocol classes in a focused package or files, for example:
  - `ProtocolFrame`
  - `ProtocolFrameType`
  - `ProtocolChannel`
  - `ProtocolError`
  - `ProtocolCodec`
- [ ] Implement binary frame encode/decode:
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
- [ ] Enforce maximum metadata and payload sizes.
- [ ] Reject malformed magic/version and invalid lengths with typed protocol errors.
- [ ] Add tests for valid frames, partial reads, malformed headers, oversized metadata, oversized payload, zero-length payload, and JSON metadata round trip.
- [ ] Do not wire new code into live feature flows yet unless the touched feature is fully replaced in the same milestone.
- [ ] Run `flutter analyze`.
- [ ] Run protocol tests if available.

Done means:

- Android has a tested frame codec.
- `../PROTOCOL.md` documents exactly what Android implemented.
- No agent is instructed to preserve the current wire protocol.

## F-B - Android Session Runtime And Transport Adapter

Goal: create one Android owner for transport/session lifecycle after Wi-Fi Direct connects.

No manual device test required unless the agent has both peer builds available. Local build/test verification is enough for this milestone.

Codex work:

- [ ] Add an Android `SessionManager` or equivalent single owner for:
  - current session id
  - peer info
  - role
  - control/bulk/realtime sockets or channels
  - accept/connect retries
  - handshake
  - heartbeat
  - teardown
  - disconnect reason
- [ ] Add a transport abstraction so raw sockets are hidden behind:
  - connect
  - accept
  - read frame
  - write frame
  - close
  - cancel
- [ ] Route post-link setup from `MainActivity.kt` to the session manager.
- [ ] Implement connection phases:
  - `WifiDirectConnected`
  - `ConnectingTransport`
  - `Handshaking`
  - `Ready`
  - `Degraded`
  - `Disconnecting`
  - `Disconnected`
  - `Failed`
- [ ] Implement handshake with app id, protocol min/max, platform, app version, device name, role, session id, capabilities, and port/channel map.
- [ ] Implement heartbeat and timeout.
- [ ] Make cleanup idempotent.
- [ ] Ensure all long-running reads/writes stop during disconnect, app destroy, or session replacement.
- [ ] Add MethodChannel events for session state, session ready, session failed, peer not running app, and disconnect reason.
- [ ] Start replacing `SocketConnectionManager` responsibilities with the session manager. Do not leave duplicate raw socket lifecycle owners.
- [ ] Add focused tests for state transitions where practical.
- [ ] Run `flutter analyze`.
- [ ] Run `flutter test`.

Done means:

- Android owns app transport lifecycle in one session manager.
- Feature services no longer need to know about sockets.
- Flutter can display the new connection/session states when wired later.

## F-C - Android UI State And Chat On Control Channel

Goal: make `Ready` mean the upgraded handshake is complete, then replace chat with `control` frames.

Manual test gate only after matching Windows support exists, or when testing Android-to-Android with two upgraded Android builds.

Codex work:

- [ ] Extend Dart models/events for session state and disconnect reasons.
- [ ] Update `WiFiDirectController` so feature tabs are enabled only after `Ready`.
- [ ] Keep Wi-Fi Direct "connected" separate from app "ready".
- [ ] Replace chat send/receive with `control` frames.
- [ ] Include message id, timestamp, sender platform, and session id in chat metadata.
- [ ] Add chat send result/failure handling instead of assuming optimistic send success.
- [ ] Preserve message ordering per session.
- [ ] Show a clear error when the peer is connected by Wi-Fi Direct but not running the upgraded WDCable protocol.
- [ ] Delete the old chat socket path from the live app flow.
- [ ] Add Dart tests for controller handling of ready/fail/chat events.
- [ ] Run `flutter analyze`.
- [ ] Run `flutter test`.

Manual test gate:

- [ ] Android-to-Android or Android-to-Windows reaches `Ready`.
- [ ] Send 10 chat messages each direction.
- [ ] Disconnect and reconnect 5 times.
- [ ] Connect to a peer that has Wi-Fi Direct but not the upgraded WDCable protocol and confirm clear failure.

Done means:

- Chat no longer depends on newline JSON over a feature-owned socket.
- UI no longer treats Wi-Fi Direct alone as full app readiness.

## F-D - Android Bulk Channel For File Transfer And Speed Test

Goal: replace file transfer and speed test with `bulk` streams.

Manual test gate only after matching peer support exists.

Codex work:

- [ ] Implement reliable bulk stream API:
  - open stream
  - send metadata
  - send chunks
  - ack start
  - ack complete
  - cancel
  - error
  - close
- [ ] Replace Android file send/receive:
  - transfer id
  - safe file name metadata
  - unknown-size content URI support
  - zero-byte file support
  - duplicate filename handling
  - partial-file cleanup
  - checksum or hash on complete when practical
- [ ] Replace Android speed test:
  - test id
  - upload/download direction
  - timeout
  - cancel
  - failure result
  - no concurrent tests on the same session unless explicitly supported
- [ ] Remove delimiter-sensitive `FILE:name:size` parsing from the live path.
- [ ] Remove `SPEED_TEST_*` string headers from the live path.
- [ ] Update Flutter transfer/speed states from protocol progress events.
- [ ] Run `flutter analyze`.
- [ ] Run `flutter test`.

Manual test gate:

- [ ] Transfer a small text file.
- [ ] Transfer a duplicate filename twice.
- [ ] Transfer a zero-byte file if available.
- [ ] Transfer Android content URI media.
- [ ] Interrupt transfer by closing peer app or turning Wi-Fi off.
- [ ] Run upload and download speed tests 5 times.
- [ ] Disconnect during speed test and reconnect.

Done means:

- File and speed no longer depend on feature-owned sockets.
- Interrupted transfers/tests fail cleanly and do not corrupt the next operation.

## F-F - Android Diagnostics And Release Gate

Goal: make Android diagnosable and ready for cross-device beta/store validation.

Manual test gate required.

Codex work:

- [ ] Add structured ring-buffer logging for:
  - Wi-Fi Direct
  - session
  - transport
  - protocol
  - control
  - bulk
  - realtime
  - chat
  - file
  - speed
  - permissions
  - UI
- [ ] Add export/copy logs from Android UI.
- [ ] Include timestamp, session id, peer platform, role, channel, stream id, transfer id/test id, and disconnect reason in important logs.
- [ ] Add protocol and session tests where practical.
- [ ] Delete remaining raw socket feature code and stale scaffolding after chat/file/speed/streaming use the new runtime.
- [ ] Ensure `flutter analyze` is clean.
- [ ] Ensure `flutter test` passes.
- [ ] Update README or release notes with the new manual QA checklist.

Manual test gate:

- [ ] Android-to-Android full flow.
- [ ] Android-to-Windows full flow in both initiation directions.
- [ ] Chat, file, speed, and audio streaming.
- [ ] Peer app missing.
- [ ] Outdated app build shows a clear upgrade-required failure.
- [ ] Reconnect loop 10 times.
- [ ] Wi-Fi off mid-transfer and mid-stream.
- [ ] Export logs after one success and one failure.

Done means:

- Android is ready for coordinated release validation with Windows.
