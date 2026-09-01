# Working in this repository

## Versioning

`versionName` moves only when the app gains something. **A build that only
fixes a build already released keeps the same `versionName`** — it is that
version, repaired, not a new one. So a crash fix on top of 2.1 ships as 2.1.

`versionCode` goes up on every build that leaves here, fix or not, because
Android refuses to install an APK over one with a higher code. It is never
shown to anyone, so one `versionName` may cover several codes.

Both live in `app/build.gradle.kts`. The table in `README.md` lists every
build, so a fix adds a row under the same name.

## Before pushing

There is no Android SDK here — `dl.google.com` is blocked by the network
policy, so the app cannot be built locally. CI builds it. That makes these
cheap checks worth running every time, because the alternative is finding out
four minutes into a Gradle run:

```bash
python3 tools/check_strings.py   # duplicate keys, key parity, format specifiers
python3 tools/check_kotlin.py    # conflicting declarations, missing R.string keys
```

A syntax pass is also possible without the SDK: download kotlinc, compile every
source with no classpath, and compare the *categories* of error against the
same run on the previous commit. Everything unresolvable (`androidx`, `android`,
Firebase) is noise; a new category is a real mistake.

## Distribution

CI publishes exactly one APK to the rolling `apk-latest` release: the ordinary
build. The minified release variant is compiled to keep its configuration and
keep rules honest, **but it is not published** — a green build proves nothing
about R8, which breaks things at run time. It crashed on a real phone once
already. Nothing goes out for install until it has been run.

The debug keystore in `keystore/` is committed on purpose: Google sign in is
registered against its SHA-1, so the fingerprint has to be stable across
machines and CI runs. Its password is `android`, which is the Android default
and public by design. `app/google-services.json` is likewise not a secret — it
ships inside every APK.

## Firestore rules

Anything touching shared classroom data needs the rules in `CLOUD_SETUP.md`
replaced by hand in the Firebase console. When a change needs new permissions,
update that file's complete rule set and say so in the release notes.

## Languages

Hebrew is the default. Every string needs all three of `values/` (Hebrew),
`values-en/` and `values-ar/`, with matching format specifiers.
