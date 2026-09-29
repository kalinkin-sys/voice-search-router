<p align="center">
  <img src="app/src/main/res/drawable-nodpi/tv_banner.png" width="640" alt="Voice Search Router">
</p>

# Voice Search Router

[Русский](README.md) · **English**

> **Open source · no root · recognized queries and microphone audio are never sent.**
> The accessibility service is used only to detect the active app and move the
> recognized text into its search UI. See [PRIVACY.md](PRIVACY.md) for details.

Voice Search Router sends queries recognized by the Android TV system Assistant
to the search interface of the app you choose.

The project addresses a common Nvidia Shield problem: the remote's hardware
microphone button works in the launcher, while voice search in third-party apps
such as SmartTube and KinoPub does not receive the recognized text.

## Features

- Routes a voice query back to the active app.
- Lets you select which apps should receive routed searches.
- Supports a configurable default search app.
- Keeps the regular Android TV search when no default app is selected.
- Dedicated routing for SmartTube and KinoPub.
- Best-effort generic routing for other Android TV apps.
- Russian and English UI with automatic system-language detection.
- One-click diagnostic reports for troubleshooting.
- No root access required.

## How it works

1. Press the hardware microphone button on the remote.
2. The Android TV system Assistant recognizes the speech.
3. Voice Search Router reads the recognized text through its accessibility service.
4. If an enabled app is currently open, the query is returned to its search UI.
5. Otherwise, the configured default app is opened.
6. If no default app is configured, the normal Android TV search remains unchanged.

## Installation

### With Downloader

1. Open the Downloader app on Android TV.
2. Enter code **9421249**, or open [aftv.news/9421249](http://aftv.news/9421249).
3. Download the APK and confirm installation.

You can also download the APK directly from [Releases](../../releases/latest).

### With ADB

```bash
adb install VoiceSearchRouter.apk
```

After installation:

1. Launch Voice Search Router.
2. Select **Open settings**.
3. Enable `Voice Search Router` under Accessibility.
4. Choose a default app.
5. Enable routing for the apps that should receive a query while active.

On Nvidia Shield, the setting is usually located at:

```text
Device Preferences → Accessibility → Voice Search Router
```

## Supported apps

Dedicated routing is implemented for:

- SmartTube;
- KinoPub.

Other apps use a generic mode that searches for an editable search field and
search controls through the Android Accessibility API. Compatibility depends on
whether the target app exposes its interface to accessibility services.

## Important limitation

Voice Search Router handles searches started with the remote's hardware
microphone button.

It cannot repair an app's own on-screen voice-search button. Those buttons use
the app's speech-recognition implementation. On Nvidia Shield, some implementations
cannot receive audio from the remote's Bluetooth microphone because of how the
system voice service is integrated.

## Diagnostics and privacy

The app can send a diagnostic report after an explicit button press. The report
contains the device model, Android and app versions, routing preferences, and
recent technical Router events.

It does not contain recognized queries, microphone audio, passwords, search-field
contents, or other user data. See the full [privacy policy](PRIVACY.md).

Reports are delivered through the project's active Cloudflare Worker. Its source
and a safe copy of its configuration are included in
[`diagnostics-worker`](diagnostics-worker) for auditing. Users do not need to
deploy their own Worker.

## Building

Requirements:

- JDK 17;
- Android SDK 35;
- Android Build Tools 35;
- Gradle 8.9 or a compatible version.

```bash
gradle :app:assembleDebug
```

The APK will be written to `app/build/outputs/apk/debug/`.

## Author

made by [KALINKIN](https://t.me/KALINKIN)

Bug reports, suggestions, and compatibility reports are welcome.

## License

Released under the [MIT License](LICENSE).
