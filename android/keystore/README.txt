Fixed signing keystore — REQUIRED for all builds.

The app must always be signed with the same key, otherwise Android refuses to
update an already-installed app ("App not installed as package conflicts with
an existing package").

Status: debug.keystore in this folder IS the canonical signing key of the
project (key SHA-256: 5C:C3:7D:E0:DC:C9:75:85:B2:1E:D3:AC:41:29:D7:2F:8A:5A
8E:0C:83:26:D2:B5:8C:6F:85:55:96:DC:BA:28, alias androiddebugkey).

- It originated from C:\Users\mahmu\.android\debug.keystore on the Windows
  machine and was promoted to canonical for v1.4.98 (build 616) onward.
- Google Drive syncs this file to every machine that builds the project, so
  Linux and Windows builds always produce the same signature.
- The file is gitignored on purpose (private key must not go to GitHub).

Credentials (standard Android debug keystore defaults):
    storePassword: android
    keyAlias:      androiddebugkey
    keyPassword:   android

If the credentials ever change, set them in android/gradle.properties:
    fixedKsStorePassword=...
    fixedKsKeyAlias=...
    fixedKsKeyPassword=...

android/app/build.gradle fails the build if this keystore file is missing.

History note: builds up to v1.4.97 (build 613) were signed with a different
debug keystore (~/.android/debug.keystore on the Linux machine). Devices
still on those versions must uninstall once and install v1.4.98 or newer.
