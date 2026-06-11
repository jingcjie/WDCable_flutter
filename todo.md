# Android / Flutter TODO

Step-by-step hardening roadmap for `WDCable_flutter/`.

Use this file one task at a time. Ask Codex for exactly one task, for example: `Do F-01 only in WDCable_flutter`. After Codex finishes, run the user test for that task on real Android hardware and paste the report template back.

Do not add major features, including voice streaming, until at least F-01 through F-06 and the matching Windows tasks W-01 through W-06 are stable.

## Standard User Test Report

Paste this after each task:

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

Use `not run` for scenarios you cannot test. That is better than guessing.

## Suggested Cross-Project Order

1. `F-01`, then `W-01`
2. `F-02`, then `W-02`
3. `F-03`, then `W-03`
4. `F-04`, then `W-04`
5. `F-05`, then `W-05`
6. `F-06`, then `W-06`
7. `F-07` and `W-07` together, because this changes the shared protocol
8. `F-08`, then `W-08`
9. `F-09`, then `W-09`

## F-01 - Baseline Cleanup And Analyzer Hygiene - Closed

Goal: reduce crash risk without changing Wi-Fi Direct behavior.

Codex work:

- [x] Record current `flutter analyze` output before edits.
- [x] Stabilize `pubspec.lock`; do not mix dependency upgrades with behavior fixes unless explicitly requested.
- [x] Remove unused imports and unused fields reported by analyzer.
- [x] Store and cancel the `WiFiDirectHomePage` subscription to `controller.stateStream`.
- [x] Store and cancel the `SpeedTestTab` subscription to `controller.eventStream`.
- [x] Add `mounted` guards for async UI callbacks that use `BuildContext` after `await`.
- [x] Replace production `print` calls with a small app logging helper or controller log method.
- [x] Run `flutter analyze` and report remaining issues.

Closure notes:

- Baseline `flutter analyze`: 110 issues.
- Final `flutter analyze`: no issues found.
- `pubspec.lock` already had local dependency changes before F-01; F-01 did not run dependency upgrade/get commands.
- User reported Android manual test OK.
- Repeated Connect while a peer is already invited logs a caught Android platform failure; long-term UI/state guard is tracked under F-02.

## F-02 - State Model Fixes

Goal: make Flutter state transitions reliable and clear stale state correctly.

Codex work:

- [ ] Fix `WiFiDirectState.copyWith` so nullable fields can be intentionally cleared:
  - `connectionInfo`
  - `lastSpeedTest`
  - `currentFileTransfer`
- [ ] Audit all calls that expect nullable fields to clear.
- [ ] Add small Dart tests if practical for state clearing and transfer-state transitions.
- [ ] Bound in-memory logs to a reasonable maximum count.
- [ ] Ensure disconnect/reset clears peers, connection, server-ready state, current transfer, and speed-test in-progress state consistently.
- [ ] Add explicit connection-attempt state, such as `isConnecting` and `pendingPeerAddress`, so repeated taps on an already invited peer do not send duplicate Android `connect()` calls.
- [ ] Disable or relabel peer Connect buttons while a peer is `Invited`, already `Connected`, or matches the pending connection attempt.
- [ ] Log duplicate connect attempts as a normal UI guard, such as `Connection already pending`, instead of surfacing them as platform failures.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Install on one Android phone.
- [ ] Start app with Wi-Fi enabled.
- [ ] Tap scan, stop scan, reset Wi-Fi Direct, and disconnect even when not connected.
- [ ] Move between connection, chat, speed test, file transfer, and settings tabs.

Report back:

- Whether reset/disconnect ever leaves UI showing `Connected` when no peer is connected.
- Whether stale transfer or speed-test progress remains after reset.
- Last 10 visible log lines after reset.

## F-03 - Android Permission And App Lifecycle Hardening

Goal: make permissions, broadcast receiver registration, and Android lifecycle safe.

Codex work:

- [ ] Safely register/unregister the Wi-Fi Direct broadcast receiver only when registered.
- [ ] Review automatic Wi-Fi Direct reset during Android initialization; remove or gate it behind an explicit recovery flow if it causes disconnect surprises.
- [ ] Rework `isWifiP2pEnabled`; it currently reports Wi-Fi enabled state, not a precise app readiness state.
- [ ] Align `getDeviceSettings` with what Flutter logs, or stop logging unsupported keys.
- [ ] Revisit storage permissions. Prefer Storage Access Framework behavior over broad `MANAGE_EXTERNAL_STORAGE` unless needed.
- [ ] Improve permission-denied events so UI tells the user which capability is missing.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Fresh install on one Android phone.
- [ ] Deny permissions when asked, then open app screens.
- [ ] Grant permissions from system settings and reopen app.
- [ ] Turn Wi-Fi off and on while app is open.
- [ ] Background/resume during scanning.

Report back:

- Which permissions were requested by Android.
- Whether denial caused crash or clear UI error.
- Whether granting later recovered without reinstall.
- Whether Wi-Fi off/on caused stuck scanning or stale connected status.

## F-04 - Android Current-Protocol Socket Lifecycle

Goal: improve reliability of the existing 3-port protocol without changing wire format yet.

Codex work:

- [ ] Make socket cleanup idempotent in Kotlin.
- [ ] Add bounded timeouts for connect, accept, header read, payload read, and send flush where safe.
- [ ] Make retry status visible in Flutter logs.
- [ ] Ensure chat, speed-test, and file-transfer listener threads stop on disconnect and app destroy.
- [ ] Gate send operations on current connected state and valid socket state.
- [ ] Add clearer error events for socket closed, retry exhausted, and peer not ready.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] If you have two Android phones, test Android-to-Android.
- [ ] If not, wait until W-04 is done and test Android-to-Windows.
- [ ] Connect, send one chat message each direction.
- [ ] Disconnect and reconnect 5 times.
- [ ] Force close the peer app while connected.
- [ ] Turn Wi-Fi off on one device while connected.

Report back:

- Pairing direction: which device initiated.
- Reconnect success count out of 5.
- Whether the app detected peer app close.
- Whether any tab became permanently stuck.
- Last visible connection/socket log lines for any failure.

## F-05 - Android File Transfer Reliability

Goal: make file transfer fail cleanly and handle edge cases.

Codex work:

- [ ] Handle content URI files with unknown or `0` size.
- [ ] Handle zero-byte files.
- [ ] Sanitize incoming file names.
- [ ] Generate collision-safe received file names.
- [ ] Clean up partial files after cancellation or failure.
- [ ] Add transfer status states: queued, sending, receiving, completed, failed, cancelled.
- [ ] Prevent simultaneous sends from corrupting the single file-transfer socket.
- [ ] Consider persisting recent transfer records on Android.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Send a small text file.
- [ ] Send a zero-byte file if your file picker can choose one.
- [ ] Send a photo or media file from a content picker.
- [ ] Send the same filename twice.
- [ ] Interrupt one transfer by turning Wi-Fi off or closing the peer app.

Report back:

- File types and sizes tested.
- Whether received files opened successfully.
- Whether duplicate filenames were preserved safely.
- Whether interrupted transfers left partial files visible.
- Whether progress reached 100 percent only when the file was actually usable.

## F-06 - Android Chat And Speed-Test Reliability

Goal: prevent chat and speed-test operations from wedging sockets.

Codex work:

- [ ] Prevent concurrent speed tests from running at the same time.
- [ ] Add timeout and failure states for upload and download tests.
- [ ] Ensure unsolicited speed-test data is discarded without breaking the next test.
- [ ] Standardize speed units in UI and logs: clearly label Mbps vs MB/s.
- [ ] Add send-failure status for chat messages instead of always treating optimistic UI as success.
- [ ] Preserve peer timestamps consistently.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Connect to a peer.
- [ ] Send 10 chat messages quickly from Android.
- [ ] Send 10 chat messages quickly from peer to Android.
- [ ] Run speed test 5 times.
- [ ] Try tapping speed-test start repeatedly.
- [ ] Disconnect during a speed test.

Report back:

- Chat messages sent/received counts.
- Whether any duplicate or missing messages appeared.
- Speed-test success count out of 5.
- Whether repeated taps started more than one test.
- Whether disconnect during test recovered after reconnect.

## F-07 - Android Protocol V2 Implementation

Goal: replace fragile string headers with a framed protocol. This must be coordinated with W-07.

Do not start this unless Windows W-07 is scheduled next or compatibility mode is included.

Codex work:

- [ ] Add a protocol document or reference implementation for frame encoding/decoding.
- [ ] Implement frame format with:
  - magic/version
  - message type
  - header length
  - JSON metadata
  - payload length
  - payload bytes
- [ ] Add Android protocol frame tests where practical.
- [ ] Add handshake, heartbeat, chat, file metadata, file chunk, speed-test request/data, cancel, ack, error, and close frame types.
- [ ] Include `sessionId` and `transferId`/`testId`.
- [ ] Keep compatibility with the old protocol only if explicitly planned.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Test Android-to-Android first if both Android phones have the F-07 build.
- [ ] Do not judge Android-to-Windows until W-07 is also done.
- [ ] Connect, chat, transfer small file, run speed test, disconnect, reconnect.

Report back:

- Whether both sides ran protocol v2 build.
- Whether handshake reached ready.
- Which operations succeeded: chat, file, speed test.
- Any protocol error text or diagnostic log.

## F-08 - Android Localization And Display Cleanup

Goal: remove text corruption and make version/text accurate.

Codex work:

- [ ] Fix mojibake/encoding corruption in Flutter ARB files.
- [ ] Remove duplicate ARB keys such as repeated `speedTest`.
- [ ] Regenerate localization files from clean ARB sources.
- [ ] Localize hard-coded Dart and Kotlin user-facing strings.
- [ ] Align displayed version with `pubspec.yaml`.
- [ ] Run `flutter analyze`.

User test after Codex finishes:

- [ ] Switch to English and inspect all tabs.
- [ ] Switch to Chinese and inspect all tabs.
- [ ] Open dialogs, snackbars, file transfer messages, and errors where practical.

Report back:

- Any garbled Chinese text.
- Any untranslated English in Chinese mode.
- Any text overflow or clipped labels.
- Displayed app version.

## F-09 - Android Tests, Diagnostics, And Release Gate

Goal: make future Codex work safer.

Codex work:

- [ ] Add Dart tests for state models and controller event handling.
- [ ] Add protocol tests if F-07 is complete.
- [ ] Add diagnostics export or copy-log support.
- [ ] Add a concise Android manual QA checklist to project docs.
- [ ] Make `flutter analyze` clean enough to be a release gate.

User test after Codex finishes:

- [ ] Trigger a normal connection and export/copy logs.
- [ ] Trigger one failure, such as peer app closed, and export/copy logs.
- [ ] Run the manual Android QA checklist.

Report back:

- Whether logs include timestamp, platform, role, session id, channel, and last error.
- Whether the manual checklist has unclear or missing steps.
