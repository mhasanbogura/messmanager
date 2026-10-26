Fixed signing keystore — REQUIRED for all builds.

The app must always be signed with the same key, otherwise Android refuses to
update an already-installed app ("App not installed as package conflicts with
an existing package").

Status: the canonical signing key is the APP STORE project's debug keystore,
shared across all apps so every app in the store signs identically:

    Canonical source: <Drive>/OpenCode/App Store/debug.keystore
    Local copy:       android/keystore/debug.keystore
    Key SHA-256:      27:39:EF:85:3F:3C:F1:A1:E2:EE:C1:37:8A:51:97:11:86:
                      E6:54:F5:08:F6:2A:77:4A:65:4A:DE:C0:CA:F5:9D
    Alias:            androiddebugkey

How it is enforced (android/app/build.gradle):
- `gradlew syncKeystore` copies the canonical key to
  android/keystore/debug.keystore, and runs automatically before every build
  (preBuild depends on it).
- signingConfigs.fixed is applied to BOTH debug and release build types.
- The build fails if neither the canonical source nor the local copy exists.
- Alternate canonical path can be forced with -PcanonicalKeystore=<path>.

Candidates searched (first hit wins):
1. -PcanonicalKeystore property
2. C:/Google Drive/mahmudulhasandhk70/OpenCode/App Store/debug.keystore (Windows)
3. /home/mhasanbogura/Google Drive/mahmudulhasandhk70/OpenCode/App Store/debug.keystore (Linux)

Credentials (standard Android debug keystore defaults):
    storePassword: android
    keyAlias:      androiddebugkey
    keyPassword:   android
Override in android/gradle.properties if they ever change:
    fixedKsStorePassword=...
    fixedKsKeyAlias=...
    fixedKsKeyPassword=...

Notes:
- The keystore file is gitignored (private key must not go to GitHub); it
  syncs between machines via Google Drive together with the source project.
- Google Drive can also overwrite android/local.properties with another
  machine's sdk.dir; settings.gradle detects a stale path and heals it
  automatically on the next build.
- Key history: builds up to v1.4.97 (build 613) were signed with the Linux
  machine's ~/.android/debug.keystore; the temporary Windows key (5C:C3:…)
  signed an early v1.4.98 artifact. Devices on those builds must uninstall
  once and install v1.4.98 (build 616, re-signed with the App Store key) or
  newer.
