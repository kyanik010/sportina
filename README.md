# SportivTV

**Premium Arabic-first IPTV for Android phones and Android TV.**

SportivTV uses a deep navy interface with warm-gold accents, RTL-aware layouts, and a subscription-first onboarding flow. The app is built for ARM64 Android devices.

## Product experience

### First launch
- The phone home screen presents a clear welcome card and a direct **Add IPTV subscription** action instead of an unexplained empty channel grid.
- Android TV shows a focusable, D-pad accessible **Add IPTV subscription** action when no playlist is configured.
- After a successful connection, the channel browser is populated from the saved playlist.

### Subscription sign-in
The subscription screen provides:
- A display name for the subscription.
- An Xtream-compatible server URL with username and password.
- Direct M3U/M3U8 playlist URLs when username and password are left empty.
- An optional XMLTV/EPG URL.
- A connection check before a subscription is saved.
- A saved-subscriptions list with connection testing and deletion.

For Xtream-style credentials, enter the provider's base server URL, username, and password. The app builds the standard M3U endpoint used by the existing playlist parser. For M3U, paste the complete playlist URL and leave both credential fields empty.

Passwords, usernames, and playlist endpoints are stored using AndroidX EncryptedSharedPreferences. Subscription metadata is persisted separately. The subscription list displays the server host rather than the full playlist URL so query-string credentials are not exposed in the UI.

## Branding and interface

- Dark navy base surfaces and warm-gold primary actions.
- Arabic-first RTL layout with English resource fallbacks.
- Custom launcher foreground and Android TV banner.
- Rounded, outlined form fields and clear hierarchy for sign-in.
- Phone navigation for channels, favorites, subscriptions, and settings.
- Android TV Leanback browse experience with D-pad focus navigation.

## Playback architecture — protected scope

The subscription and interface work does not intentionally change the player implementation.

- **Media3 / ExoPlayer:** primary video playback.
- **Media3 audio-only session:** independent external audio source.
- **OkHttp:** provider and playlist HTTP access.
- **M3U / M3U8:** playlist parsing.
- **XMLTV / EPG:** programme guide parsing.

External audio synchronization must still be validated against real provider streams, including long-duration playback and reconnects. A separate audio session alone does not guarantee perfect synchronization between independent live feeds.

## Build

### Requirements
- JDK 17
- Android SDK Platform 35
- Android Gradle Plugin 8.7.2
- Gradle Wrapper 8.12

Do not commit local.properties, signing keys, or credentials.

### Commands

Windows PowerShell:
```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
.\gradlew.bat test
```

Linux / macOS:
```bash
./gradlew assembleDebug
./gradlew assembleRelease
./gradlew test
```

## ABI target

Gradle is configured for arm64-v8a only. Confirm the APK's native libraries with apkanalyzer or unzip -l after each release build; the Gradle ABI filter alone is not a substitute for inspecting the packaged artifact.

## CI

GitHub Actions builds the debug APK and runs local unit tests. A green CI run confirms those tasks completed; it does not replace hands-on testing on physical phones and Android TV devices.

## Security and release checklist

- Never commit provider credentials, playlist URLs containing credentials, signing material, or local SDK paths.
- Verify the subscription flow against both a valid and invalid provider account.
- Confirm that no credential-bearing URL is printed in logs, toasts, or subscription cards.
- Test phone layouts in portrait and landscape, Arabic RTL and English LTR.
- Test Android TV navigation with a physical remote, including focus visibility and keyboard input.
- Inspect the final APK for the intended ABI and test installation on an ARM64 device.
- Validate player regressions separately; do not change playback libraries as part of UI work.
