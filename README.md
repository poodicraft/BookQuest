# 📚 BookQuest — מסע הספרים / رحلة الكتب

A school books library app for Android. Students fill it with their own files,
read them inside the app, and level up while they do it.

The app speaks **Hebrew (default), English and Arabic**, and flips the whole
layout between right-to-left and left-to-right with the language.

## What it does

- **Load books from files** — pick one file or many with the system file
  picker. `TXT`, `PDF`, `EPUB`, `HTML` and `MD` are supported. Each book is
  copied into the app, so it stays readable offline.
- **A reader per format** — a paginated PDF viewer, a flowing EPUB/HTML reader
  and a plain text reader, all with adjustable text size and light / sepia /
  dark page styles. Hebrew and Arabic text files in legacy encodings
  (windows-1255 / windows-1256) are detected and decoded.
- **Progress that sticks** — every book remembers where you stopped, how far
  you got and how many minutes you spent in it.
- **Made to be fun** — XP for every minute read, levels, daily goals, day
  streaks, ten unlockable badges and a confetti burst when you level up.
- **Flashcards and quizzes** — write question/answer cards for any book, then
  play a flip-card quiz and earn XP for what you remember.
- **Generated covers** — no artwork needed: every book gets its own gradient
  cover from its subject and title.
- **Google sign in and cloud backup** — optional. Sign in with a Google
  account and the profile, per-book progress and flashcards are merged into
  Cloud Firestore, so a reinstall or a new phone picks up where you left off.
  Needs a Firebase project of your own: see [CLOUD_SETUP.md](CLOUD_SETUP.md).

## Versioning

The two numbers move for different reasons.

**`versionName`** is the one people see, in the app details and under
Settings → About. It moves only when the app gains something. A build that
only repairs a build already released keeps the name it is repairing — it is
the same version, fixed, not a new one.

**`versionCode`** goes up on every single build that leaves here, fix or not,
because Android refuses to install an APK over one with a higher code. Nobody
ever sees it. That is why the table below can list one name against several
codes.

| Version | What changed |
| --- | --- |
| 2.1 (13) | Fix "LocalLifecycleOwner not present" on start up: the lifecycle library and Compose UI now agree on which one to read |
| 2.1 (12) | Notifications settings for everyone, asked once on a first run; no more teacher-or-student flash at launch; deleting an account now proves who you are first |
| 2.0 (11) | Launch screen, daily reading reminder, offline banner, export and import, About with privacy policy and licences, delete your account, crash reports |
| 1.9 (10) | Remove a class with a confirmation, a full profile editor with picture and bio, teacher tips |
| 1.8 (9) | Home tab reachable again from every other tab, no daily reading goal in a teacher's settings |
| 1.7 (8) | Class stream with messages and files, homework with hand-ins and marking, teachers no longer collect XP |
| 1.6 (7) | Backup no longer erases your role and classes, real file type detection, progress starts at 0 |
| 1.5 (6) | Role chosen at sign in, classes no longer drop out mid session, reading progress reaches 100% |
| 1.4 (5) | Teacher accounts, classes with join codes, set books and quizzes, typed quiz answers |
| 1.3 (4) | Welcome screen on launch, branded Google button, email sign in |
| 1.2 (3) | Firebase configuration added, so Google sign in and cloud backup are live |
| 1.1 (2) | Google sign in and cloud backup, reworked reading screen, XP exploit fixes |
| 1.0 (1) | First release: library, importing, three readers, three languages, gamification |

## Getting the APK

Every push builds a debug APK in GitHub Actions:

1. Open the **Actions** tab → the latest **Build APK** run → download the
   `BookQuest-apk` artifact, **or**
2. Grab `BookQuest.apk` straight from the **`apk-latest`** release.

Install it on any phone running **Android 7.0 (API 24) or newer**. You will
need to allow installation from unknown sources, since this is a
self-signed debug build rather than a Play Store release.

## Building it yourself

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

Requires JDK 17 and the Android SDK (compileSdk 34). Debug builds are signed
with the committed `keystore/debug.keystore` so the signing fingerprint stays
stable for Google sign in.

## Project layout

```
app/src/main/java/com/poodicraft/bookquest/
├── data/          models, preferences and the JSON-backed library repository
├── reader/        text decoding, EPUB flattening, PDF rendering
└── ui/            Compose screens, theme and shared components
app/src/main/res/values/       Hebrew strings (the default language)
app/src/main/res/values-en/    English strings
app/src/main/res/values-ar/    Arabic strings
```

The app was first prototyped on a branch of another repository; this is its
own home from version 1.1 onwards.
