# YTDLP Mobile

An Android app that downloads a YouTube video, or takes the audio out of it as an MP3. It runs yt-dlp on the phone itself. Nothing goes through a server.

## Install

Download the APK from the [latest release](../../releases/latest) and open it on your phone.

Android asks you to allow installs from your browser, and Play Protect says it does not know the developer. Both are expected for an app that is not on the Play Store. Choose "Install anyway".

Use the `arm64-v8a` file. Every Android phone sold in recent years needs that one. The `x86_64` file is for an emulator.

## What it does

- Downloads a video as MP4, at a quality you choose.
- Downloads the audio alone as an MP3.
- Continues a download that stopped. It keeps the part it already has, so it does not start again from zero.
- Keeps downloading when you leave the app, and shows the progress in a notification.
- Saves a video to Movies and an MP3 to Music, under the video title.
- Accepts a link shared from the YouTube app.

## Settings

- **yt-dlp version.** The copy inside the app goes stale within weeks, because video sites change. Update it from this page.
- **Default quality** and **MP3 quality**.
- **Cookies.** A `cookies.txt` from a signed in browser answers the robot check. The file is a live credential, so use a throwaway account.

## Build

```
./gradlew assembleDebug
```

A release build needs a signing key. Put a `keystore.properties` in the project root:

```
storeFile=/path/to/release.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Then run `./gradlew assembleRelease`. Without that file the debug build still works and the release build stays unsigned.

## Tests

```
./gradlew testDebugUnitTest
```
