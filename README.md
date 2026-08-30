# Priority Todo

Priority Todo is a native Android app based on a handwritten priority-index system. It records tasks, computes each task's score, keeps the list sorted from highest score to lowest score, highlights the current primary MIT, and can schedule date/time reminders.

## Scoring

The app uses the notes' formula:

```text
Score = Urgency + 10 × (Impact / Effort) + ImpactRank
```

Impact:
- High = 900
- Medium = 600
- Low = 300

Effort:
- High = 30
- Medium = 20
- Low = 10

Urgency:
- Urgent = 1000
- Not urgent = 0

Impact rank breaks equal return-on-effort ties without crossing ROI tiers:
- High = 3
- Medium = 2
- Low = 1

Buckets:
- Score >= 1000: Immediate
- Score >= 500 and < 1000: Next week
- Score < 500: Someday

## Features

- Add, edit, complete, restore, and delete tasks.
- Automatic score calculation and descending score sort.
- Primary MIT display based on the highest scoring incomplete task.
- Impact, effort, urgency, quick-task, and recurring-MIT fields.
- Date and time reminder picker.
- Android notifications through `AlarmManager` and `NotificationManager`.
- Local persistence through `SharedPreferences`.
- Manual day/night mode toggle.
- Minimal black, white, light-grey, and red UI treatment.

## Local web sync

The dependency-free web companion runs on Node 20 or newer and keeps its data in
`web/.data/sync-state.json`:

```bash
cd web
npm start
```

Open `http://localhost:8787`. In the Android app, open **More options → Sync
with web** and use:

- Emulator: `http://10.0.2.2:8787`
- Physical device: `http://<computer-lan-ip>:8787`

After setup, the APK syncs on resume and after local edits; **Sync now** remains
available in the same menu. The app remains fully usable offline. The local
service has no authentication or TLS, so expose it only on localhost or a
trusted private network.

When the server is protected by Cloudflare Access, the browser continues to use
interactive email login. Android cannot complete that browser flow, so its sync
settings also accept a Cloudflare Access Client ID and Client Secret. The app
sends them only in the standard `CF-Access-Client-Id` and
`CF-Access-Client-Secret` headers. Credentials are stored in the app's private
preferences and excluded from Android backup and device transfer.

## Build

The repository includes a GitHub Actions workflow that tests the web companion
and builds the debug APK on every push to `main` or `codex/**`, pull request, or
manual workflow dispatch.

Locally, with JDK 17 and Gradle installed:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

The APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
