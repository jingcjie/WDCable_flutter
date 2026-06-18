# Wi-Fi Direct Connection Improvement Plan - Android Flutter

Updated: 2026-06-18

This file is written for code agents. Keep Android and Windows behavior separate. Do not copy Windows advertisement/listener concepts into this Android project.

## Official Sources Checked

- Android Wi-Fi Direct guide: required permissions, broadcast actions, and Wi-Fi Direct socket use.
  https://developer.android.com/develop/connectivity/wifi/wifi-direct
- Android Wi-Fi Direct service discovery guide: `addLocalService` lets the framework respond to service discovery requests from peers.
  https://developer.android.com/develop/connectivity/wifi/nsd-wifi-direct
- Android `WifiP2pManager`: `discoverPeers`, `connect`, `requestConnectionInfo`, `requestDiscoveryState`, `startListening`, `stopListening`, `stopPeerDiscovery`, reason codes, and broadcasts.
  https://developer.android.com/reference/android/net/wifi/p2p/WifiP2pManager
- Android `Manifest.permission`: `MANAGE_WIFI_NETWORK_SELECTION` is not for third-party apps. Do not design around programmatic approval of incoming Wi-Fi Direct requests with this permission.
  https://developer.android.com/reference/android/Manifest.permission

## Required Platform Decision

Android minimum API is Android 13 / API 33.

Project setting:

```kotlin
defaultConfig {
    minSdk = 33
}
```

Reason:

- `WifiP2pManager.startListening` is API 33.
- `ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED` is API 33.
- `NEARBY_WIFI_DEVICES` is API 33 and is the correct runtime permission path for Android 13+.

Permission cleanup:

- For Wi-Fi Direct on API 33+, require `NEARBY_WIFI_DEVICES`, `ACCESS_WIFI_STATE`, `CHANGE_WIFI_STATE`, and `INTERNET`.
- Do not block Wi-Fi Direct on `ACCESS_FINE_LOCATION` when `NEARBY_WIFI_DEVICES` is declared with `android:usesPermissionFlags="neverForLocation"` and the app is not deriving location from Wi-Fi APIs.
- Keep `ACCESS_FINE_LOCATION` only if another product feature truly needs location. If it stays in the manifest for compatibility, `PermissionManager` still must not treat it as required for Wi-Fi Direct on API 33+.

Keep optional API guards:

- `ACTION_WIFI_P2P_LISTEN_STATE_CHANGED`, `EXTRA_LISTEN_STATE`, `WIFI_P2P_LISTEN_STARTED`, and `WIFI_P2P_LISTEN_STOPPED` are API 34. Guard these with `Build.VERSION.SDK_INT >= 34`.

## User Experience Goal

- Opening the Android app should put the device into a best-effort "available to nearby WDCable peers" state.
- The user can decide whether to scan for other devices.
- Scan and connect should feel predictable and should not fight each other.
- Debug output must show every native API call, callback, broadcast, reason code, and lifecycle state.

## Current Repo Facts

- Native Wi-Fi Direct owner: `android/app/src/main/kotlin/com/example/wifi_direct_cable/WiFiDirectManager.kt`.
- Broadcast receiver: `WiFiDirectBroadcastReceiver.kt`.
- Runtime receiver ownership: `WdCableRuntime.kt`.
- Flutter bridge: `FlutterMethodChannelHandler.kt` and `lib/wifi_direct_service.dart`.
- Dart state owner: `lib/controllers/wifi_direct_controller.dart`.
- Current native `getDiscoveryStatus` is hardcoded and should be replaced with real native state.
- Current Dart `discoverPeers()` sets `isDiscovering=false` when the method returns, but Android discovery continues until a broadcast says it stopped.

## Android Terms To Keep Separate

- Availability/listen: `startListening()` makes the device periodically enter Wi-Fi Direct listen state and respond to probe requests. It is not the same as Windows `WiFiDirectAdvertisementPublisher`.
- Local service: `addLocalService()` registers WDCable identity for P2P service discovery. The framework responds to service discovery requests.
- Scan: `discoverPeers()` actively scans for peers. It is user-driven.
- Service scan: `discoverServices()` actively scans for registered services. Add only after peer scan is stable.
- Connect: `connect()` starts negotiation. `onSuccess` means request initiation only, not connected.
- Connected: real connection is from `WIFI_P2P_CONNECTION_CHANGED_ACTION` plus `requestConnectionInfo()`.

## Target Android Model

The native Kotlin manager is the single owner of Wi-Fi Direct lifecycle. Dart observes state and sends user intent.

State names to use in diagnostics:

- `BlockedByPermission`
- `Unavailable`
- `Ready`
- `Listening`
- `ServiceRegistered`
- `Discovering`
- `Connecting`
- `Connected`
- `Disconnecting`
- `UserStoppedScan`
- `Background`
- `Error`

Keep these as native state first. Dart state should mirror native events, not infer lifecycle from method return timing.

## Startup Availability

Implement a native method like `ensureAvailable(reason)`.

Call it when:

- `WiFiDirectManager.initialize()` completes.
- Required permissions are granted.
- Activity resumes and receiver is registered.
- Wi-Fi P2P becomes enabled.

Rules:

- Do not start active scan on app open.
- Call `startListening(channel, listener)` on API 33+.
- Register local WDCable service with `addLocalService()` after the channel is ready and permissions are granted.
- Keep `listenDesired=true` while foreground and not connected/connecting.
- If start listen fails with `BUSY`, log it and retry only after a state-changing broadcast or user retry. Do not immediate-loop.
- If permissions are missing, emit `BlockedByPermission` and remember `availabilityDesired=true`; retry after permission grant.

Service identity:

- Use DNS-SD local service first, for example `_wdcable._tcp`.
- TXT fields should include protocol version, app name, platform, and optional build version.
- Do not require service discovery for connecting in the first implementation. Use it as filtering and diagnostics after peer discovery is stable.

## User Scan Flow

User scan is explicit.

Rules:

- User taps Scan -> native `startUserScan(reason)`.
- If `Connecting` or `Connected`, reject scan with a clear log.
- If already discovering, return success and log dedupe.
- Call `discoverPeers()`.
- Do not set `isDiscovering=false` on method success.
- Update discovery state only from:
  - `WIFI_P2P_DISCOVERY_CHANGED_ACTION`
  - `requestDiscoveryState()`
  - explicit stop callback when no broadcast arrives within timeout
- On `WIFI_P2P_PEERS_CHANGED_ACTION`, call `requestPeers()` and emit the full peer list.
- Keep stale peers visible during `Connecting` if a peer refresh returns empty.

Important official behavior:

- Android says discovery stops during connection setup.
- Android says trying to re-initiate discovery during connection setup can fail.
- Therefore, do not auto-restart discovery while `Connecting`.

## Connect Flow

Rules:

- User taps Connect -> native `connectToPeer(deviceAddress, reason)`.
- Reject if already `Connecting` or `Connected`.
- Set `state=Connecting`, `pendingPeerAddress=deviceAddress`, and increment an operation generation.
- Do not call `stopListening()` before `connect()`.
- Do not call `stopPeerDiscovery()` during group creation. Android documents that `stopPeerDiscovery()` can fail while a P2P group is being created.
- Let Android stop discovery during connection setup.
- Call `connect(channel, config, listener)`.
- Decode `onFailure(reasonCode)` as `ERROR(0)`, `P2P_UNSUPPORTED(1)`, `BUSY(2)`, `NO_SERVICE_REQUESTS(3)`, and API-specific values where available.
- `connect().onSuccess` means "connection request initiated". Keep `Connecting`.
- Listen for `ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED` on API 33+ and log accepted/rejected for the current request.
- The only success transition to `Connected` is `WIFI_P2P_CONNECTION_CHANGED_ACTION` followed by `requestConnectionInfo()` where `groupFormed=true`.
- If connection fails or times out, call `cancelConnect()` first for pending negotiation. Use `removeGroup()` only if group info shows a real stale group or after a real connected group existed.

Do not use `setConnectionRequestResult()` or `addExternalApprover()` for normal app flow. They require `MANAGE_WIFI_NETWORK_SELECTION`, and Android documents that permission as not for third-party apps.

## Stop Scan Flow

Rules:

- User taps Stop Scan -> call `stopPeerDiscovery()` only if state is `Discovering`.
- Do not call `stopListening()` for normal Stop Scan. `stopListening()` also stops peer discovery and service discovery.
- After stop scan, return to `Listening` or `ServiceRegistered` if `listenDesired=true`.
- If stop fails because connection setup is in progress, log and wait for connection broadcast.

## Disconnect And Cleanup

Rules:

- User disconnect calls session disconnect first, then Wi-Fi Direct cleanup.
- If `Connecting`, call `cancelConnect()`.
- If `Connected` or group info shows a group, call `removeGroup()`.
- After cleanup, return to startup availability: `startListening()` and `addLocalService()` if permissions and foreground state allow it.
- Do not clear local services on every disconnect unless channel is being recreated. Clear/re-add services only during channel reset or service identity change.

## Receiver And Channel Lifecycle

Rules:

- Keep one `WifiP2pManager.Channel` for the Flutter engine/activity lifecycle.
- Pass a real `ChannelListener` to `initialize()`. If the channel disconnects, log it, close old state, and reinitialize only when not `Connecting` or `Connected`.
- Keep receiver registration tied to `WdCableRuntime` owners.
- Register these actions:
  - `WIFI_P2P_STATE_CHANGED_ACTION`
  - `WIFI_P2P_PEERS_CHANGED_ACTION`
  - `WIFI_P2P_CONNECTION_CHANGED_ACTION`
  - `WIFI_P2P_THIS_DEVICE_CHANGED_ACTION`
  - `WIFI_P2P_DISCOVERY_CHANGED_ACTION`
  - `ACTION_WIFI_P2P_REQUEST_RESPONSE_CHANGED` on API 33+
  - `ACTION_WIFI_P2P_LISTEN_STATE_CHANGED` on API 34+
- On resume, call `requestP2pState()`, `requestDiscoveryState()`, `requestDeviceInfo()`, and `requestConnectionInfo()` to hydrate state because some Wi-Fi P2P broadcasts are not sticky on modern Android.

## Dart/Flutter Changes

Rules:

- Add native events for discovery state, listen state, service registration state, operation ID, and decoded reason codes.
- `WiFiDirectController.discoverPeers()` should not clear `isDiscovering` in `finally`.
- `isDiscovering` must follow native discovery events.
- `getDiscoveryStatus()` must return real native state.
- UI should show:
  - `Available nearby` when listening/service registered.
  - `Scan running` only when discovery broadcast says started.
  - `Connecting to <peer>` while native state is connecting.
  - decoded native error text when connect/discover/stop fails.
- Copy diagnostics should include both native `DiagnosticsLogger` history and current Dart state.

## Diagnostics Required

Every log line should include:

```text
timestamp | platform=android | opId | state | api | callback | broadcast | result | reasonCode | reasonName | peerAddress | peerName | discoveryState | listenState | groupFormed | isGroupOwner | groupOwnerAddress
```

Minimum events to log:

- manager initialized and channel created/lost
- permission check result
- receiver registered/unregistered and owner
- start listening requested/succeeded/failed
- listen-state broadcast on API 34+
- local service add/remove/clear requested/succeeded/failed
- user scan requested
- discoverPeers requested/succeeded/failed
- discovery changed broadcast
- peers changed broadcast and peer count
- connect requested/succeeded/failed
- request response changed broadcast on API 33+
- connection changed broadcast
- requestConnectionInfo result
- cancelConnect result
- removeGroup result
- disconnect cleanup start/complete

## Implementation Order

1. Raise `minSdk` to 33 and remove code paths only needed for Android 12 and lower.
2. Fix permission gating so Wi-Fi Direct on API 33+ does not require location when `NEARBY_WIFI_DEVICES` with `neverForLocation` is available.
3. Add native lifecycle diagnostics and real discovery/listen state events.
4. Replace hardcoded `getDiscoveryStatus`.
5. Add startup `ensureAvailable()` with `startListening()` and local service registration.
6. Fix Dart discovery state so it follows native events.
7. Update connect flow to stop auto-restarting discovery while connecting.
8. Add request response broadcast logging.
9. Add conservative cleanup: `cancelConnect()` for pending, `removeGroup()` only for real/stale group.
10. Add service discovery filtering after peer discovery/connect is stable.
11. Test Android-to-Windows, Windows-to-Android, scan without connecting, connect while scan is running, reject/timeout on Windows prompt, disconnect and reconnect, permission denial/grant, Wi-Fi off/on.

## Do Not Do

- Do not implement a Windows-style advertisement publisher on Android.
- Do not auto-start `discoverPeers()` on app open.
- Do not call `stopListening()` before `connect()`.
- Do not call `stopListening()` for normal Stop Scan.
- Do not auto-restart discovery while `Connecting`.
- Do not treat `connect().onSuccess` as connected.
- Do not use `MANAGE_WIFI_NETWORK_SELECTION`; it is not for third-party apps.
- Do not let Dart infer long-lived discovery state from method return timing.
