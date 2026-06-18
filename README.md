# WiFi Direct Cable

<p align="center">
  <img src="assets/icon.png" alt="WiFi Direct Cable icon" width="96">
</p>

<p align="center">
  <strong>Offline peer-to-peer transfer for files, chat, speed tests, and audio over Wi-Fi Direct.</strong>
</p>

<p align="center">
  No internet. No router. No hotspot. Just a direct device-to-device connection.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT license">
  <img src="https://img.shields.io/badge/platform-Android%20%7C%20Windows-brightgreen" alt="Android and Windows">
  <img src="https://img.shields.io/badge/Made%20with-Flutter-blue.svg" alt="Made with Flutter">
</p>

<p align="center">
  <img src="assets/demonstration.png" alt="WiFi Direct Cable demonstration" width="760">
</p>

## What It Does

WiFi Direct Cable creates a private local link between nearby devices using Wi-Fi Direct. Once connected, the devices share one negotiated app session for chat, file transfer, speed testing, and audio.

This repository contains the Flutter Android client. A separate Windows companion app is available here: [WDCableWUI](https://github.com/jingcjie/WDCableWUI).

## Highlights

- Direct device-to-device connection without internet access.
- High-speed file transfer for local sharing.
- Real-time chat between connected peers.
- Audio Link for live microphone audio over the peer session.
- Built-in upload and download speed tests.
- Shared session protocol for chat, files, speed tests, and audio.
- Android client with a separate Windows companion app.

## Audio Link Use Case

Use an Android device as a mobile microphone sender and the Windows client as the receiver. This is useful for quick local audio sharing, device-to-PC testing, and offline peer-to-peer audio experiments.

| Android Audio Link | Windows Audio Link |
| :---: | :---: |
| <img src="assets/android_audio.png" alt="Android Audio Link screen" width="260"> | <img src="assets/winui_audio.png" alt="Windows Audio Link screen" width="520"> |

## Screenshots

| Connection | Chat | Speed Test | File Transfer |
| :---: | :---: | :---: | :---: |
| <img src="assets/s1.jpg" alt="Connection tab" width="200"> | <img src="assets/s2.jpg" alt="Chat tab" width="200"> | <img src="assets/s3.jpg" alt="Speed Test tab" width="200"> | <img src="assets/s4.jpg" alt="File Transfer tab" width="200"> |

## Platform Support

| Platform | Status | Repository |
| --- | --- | --- |
| Android | Supported | This repository |
| Windows | Separate companion client | [WDCableWUI](https://github.com/jingcjie/WDCableWUI) |

## Getting Started

### Prerequisites

- Flutter SDK: [installation guide](https://flutter.dev/docs/get-started/install)
- Android Studio or VS Code
- An Android device with Wi-Fi Direct support

### Run Locally

```sh
git clone https://github.com/jingcjie/WDCable_flutter.git
cd WDCable_flutter
flutter pub get
flutter run
```

## Troubleshooting

- Make sure Wi-Fi is enabled on both devices.
- Grant nearby device and location permissions when Android asks.
- Keep both devices on the connection screen while pairing.
- If discovery fails, turn Wi-Fi off and on, then scan again.
- For Android-to-Windows testing, run the Windows companion app from [WDCableWUI](https://github.com/jingcjie/WDCableWUI).

## Coming Soon

- Better audio codec options and quality tuning.
- Camera and video streaming over Wi-Fi Direct.
- More detailed connection diagnostics.
- Smoother Android-to-Windows setup flow.

## Contributing

Contributions are welcome. For larger changes, please open an issue first so the implementation can be discussed before a pull request.

```sh
git checkout -b feature/your-feature
git commit -m "Add your feature"
git push origin feature/your-feature
```

Then open a pull request.

## License

Distributed under the MIT License. See [LICENSE](LICENSE) for more information.

<details>
<summary>Android Release QA Checklist</summary>

Use this checklist before coordinated Windows validation:

- Android-to-Android: connect both initiation directions and confirm both reach `Ready`.
- Chat: send 10 messages each direction.
- File: send a small text file, the same filename twice, a zero-byte file, and a media/content URI.
- Speed: run download/upload speed tests 5 times.
- Failure: close the peer app during file transfer, turn Wi-Fi off during speed test, then reconnect.
- Missing peer app: connect to a Wi-Fi Direct peer without the upgraded WDCable protocol and confirm a clear protocol-missing failure.
- Diagnostics: use the copy icon in the Connection tab logs header after one success and one failure, then save the copied text with the test report.

</details>
