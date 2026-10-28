# LauncherHijack integration

The combined Home TV APK incorporating LauncherHijack-derived code is distributed under GNU GPL version 3. Original CleanYourTV MIT notices and other third-party licenses remain in force for their respective components.

Adapted on 2026-10-28 from BaronKiko/LauncherHijack, commit f79aee3ddd10c053d6d7c55d6f2fc29436001537:
- HomeWatcher.java: system-dialog Home broadcast detection. Upstream credits Evan Gillogley's react-native-home-pressed project (https://github.com/evanjmg/react-native-home-pressed).
- HomePress.java: explicit launcher PendingIntent and debounce.
- AccServ.java: separate accessibility-based fallback remains in HomeKeyService.

Upstream source: https://github.com/BaronKiko/LauncherHijack
Copyright remains with the original authors and contributors. The integrated code was modified for HomeTV: fixed target, Android 7.1 Fire TV scope, explicit opt-in/stop control, monotonic debounce, boot restore, local diagnostics, and no invisible overlay or restart loop.

There is no warranty. You may redistribute and modify the combined application under GPLv3; see ../COPYING and the license included in the APK. Every published APK is accompanied by HomeTV-source.tar.gz containing the corresponding launcher source and build workflow, on the same fixed release.

The broadcast path is a compatibility experiment. It does not grant privileges, change the system default launcher, or guarantee Home interception on every Fire OS version.
