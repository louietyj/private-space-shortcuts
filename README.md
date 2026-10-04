# Private Space Shortcuts

Pin Android **private space** apps to any launcher's home screen or dock.

Android 15+ only lets the default launcher see private space, and many third-party launchers
(e.g. Octopi) can't put private apps on the home screen or dock. This app lists your private
space apps and creates ordinary pinned shortcuts for them, complete with the app's icon, name and
the system's private space badge. Tapping a shortcut opens the private copy of the app; if private
space is locked, the system's unlock prompt appears first.

## How it works

- Private space is a separate Android profile. A normal app can't see or start activities in it
  through the launcher APIs (`LauncherApps`, `CrossProfileApps`); those are reserved for the
  default launcher.
- The multi-user APIs work instead: `startActivityAsUser` and `createContextAsUser` (hidden, called
  via reflection) need only `INTERACT_ACROSS_USERS`, and `UserManager.requestQuietModeEnabled`
  needs `MODIFY_QUIET_MODE` to unlock the profile.
- Both permissions have `protectionLevel=development`, so `pm grant` can grant them. The grant is
  one-off and survives reboots, so Shizuku isn't needed afterwards.
- Each pinned shortcut points at an invisible `LaunchActivity` that unlocks private space if needed,
  then starts the app in the private profile.

## Setup

1. Install the APK in your main profile.
2. Open **Private Shortcuts** and grant the two permissions, either:
   - with [Shizuku](https://shizuku.rikka.app/) running: tap **Grant permissions with Shizuku**, or
   - over adb:
     ```bash
     adb shell pm grant com.louietyj.privatespaceshortcuts android.permission.INTERACT_ACROSS_USERS
     adb shell pm grant com.louietyj.privatespaceshortcuts android.permission.MODIFY_QUIET_MODE
     ```
3. Unlock private space if the app asks you to, tap an app, and confirm the launcher's
   "add to home screen" prompt. Drag the shortcut wherever you like, including the dock.

Shortcuts keep working while private space is locked; tapping one asks you to unlock first.

## Building

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tested on a Pixel 11 Pro running Android 17 with Octopi Launcher.

## Limitations

- Relies on hidden APIs called through reflection; a future Android release could block them.
