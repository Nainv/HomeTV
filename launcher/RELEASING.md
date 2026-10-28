# Home TV releases

The `Publish signed Home TV update` workflow builds the `publish` variant from this project and publishes two files to the project's GitHub Pages site:

- `tv.apk`: the signed Home TV installer.
- `release.json`: version code, version name, download URL and SHA-256 digest.

The workflow expects these repository Actions secrets: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, and `ANDROID_KEY_PASSWORD`. The alias for the Zikra signing key is `zikra`. Keep the keystore and passwords out of Git; do not commit them.

Published builds use a monotonically increasing version code. The launcher checks for updates when it resumes, downloads and verifies the APK, then asks Android to show its installer. Android still requires the user to confirm installation.

The first switch from an APK signed with another key requires removing that old installation once. Later updates signed with this same keystore can install over it.
