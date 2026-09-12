# Luma TV

Luma TV is a clean-room Android TV media client inspired by modern living-room interfaces. It connects directly to a user-owned Emby server, builds rich catalog rows, and plays media with Android Media3.

## Included in this MVP

- Native Android TV launcher support and D-pad navigation
- Cinematic, Apple TV-inspired home screen
- Emby username/password authentication
- Hardware-keystore encryption for the saved access token
- Continue Watching, Recently Added, Trending on Your Server, Movies, TV Shows, and Favorites
- Emby library search
- Movie/show details
- Lightweight Media3 player using hardware decoding and a `SurfaceView`
- Direct Play when Emby reports it is supported; Emby's transcoding URL otherwise
- Small player buffer tuned for fast starts without excessive memory use

## Build on Windows

1. Install the latest stable Android Studio.
2. Open this `LumaTV` folder as a project.
3. Allow Android Studio to install Android SDK 35 and sync Gradle.
4. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. Install `app-debug.apk` on the Android TV with ADB or a file-manager sideload.

The APK is normally generated at `app/build/outputs/apk/debug/app-debug.apk`.

## Connect

On first launch, enter the complete Emby address and your Emby username/password. Examples:

- Home network: `http://192.168.1.50:8096`
- Remote HTTPS: `https://media.example.com`

The password is only used during authentication and is not saved. The returned Emby token is encrypted with the Android Keystore.

## Notes

- “Trending on Your Server” is based on the Emby library's play count. This keeps the app independent of third-party API keys.
- Cleartext HTTP is enabled because many home Emby installations use LAN HTTP. Use HTTPS for any server exposed outside the home.
- This is original source code. It does not contain Strand code, branding, assets, or reverse-engineered components.
- The current UI treats series as detail items. A production follow-up should add season and episode browsing, profile selection, audio/subtitle selection, watch-progress reporting, live TV, and optional TMDB/Trakt integrations.
