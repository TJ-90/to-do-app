# Priority Todo

Priority Todo is a native Android app based on a handwritten priority-index system. It records tasks, computes each task's score, keeps the list sorted from highest score to lowest score, highlights the current primary MIT, and can schedule date/time reminders.

## Scoring

The app uses the notes' formula:

```text
Score = Impact / Effort + Urgency
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

Buckets:
- Score >= 1000: Immediate
- Score >= 500 and < 1000: Next week
- Score < 500: Someday

## Features

- Add, edit, complete, restore, and delete tasks.
- Automatic score calculation and descending score sort.
- Primary MIT display based on the highest scoring incomplete task.
- Impact, effort, urgency, dependency, quick-task, and recurring-MIT fields.
- Date and time reminder picker.
- Android notifications through `AlarmManager` and `NotificationManager`.
- Local persistence through `SharedPreferences`.
- Manual day/night mode toggle.
- Minimal black, white, light-grey, and red UI treatment.

## Sync & Web

Priority Todo now syncs Android and web through your own Google Drive.

- One private JSON file (`priority-todo.json`) lives in Drive's hidden `appDataFolder` — only this app, signed in as you, can read it. No server, no shared database.
- OAuth scope is `drive.appdata` only: the app cannot see any other file in your Drive.
- Web app is a static page under `web/`, deployed to GitHub Pages.
- To share with a friend: they open the same web URL and sign in with THEIR Google account, or install the APK and sign in. Their tasks stay in their own Drive — fully isolated from yours.

Setup (one-time Google Cloud config): see [web/SETUP.md](web/SETUP.md).

## Build

The repository includes a GitHub Actions workflow that builds the debug APK on every push to `main`, pull request, or manual workflow dispatch.

Locally, with JDK 17 and Gradle installed:

```bash
gradle --no-daemon assembleDebug
```

The APK is produced at:

```text
app/build/outputs/apk/debug/app-debug.apk
```
