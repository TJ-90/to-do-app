# Agent Instructions

This repository contains a native Android app for a priority-based to-do system. Follow these instructions when modifying the project.

## Project Shape

- Android app module: `app/`
- Main UI: `app/src/main/java/com/tj90/prioritytodo/MainActivity.java`
- Task scoring model: `app/src/main/java/com/tj90/prioritytodo/TodoTask.java`
- Local persistence: `app/src/main/java/com/tj90/prioritytodo/TaskStore.java`
- Reminder scheduling: `app/src/main/java/com/tj90/prioritytodo/ReminderScheduler.java`
- Notification receiver: `app/src/main/java/com/tj90/prioritytodo/ReminderReceiver.java`
- APK CI workflow: `.github/workflows/android.yml`

## Product Rules

Keep the app aligned with the handwritten priority system:

```text
Score = Impact / Effort + Urgency
```

- Impact: High = 900, Medium = 600, Low = 300
- Effort: High = 30, Medium = 20, Low = 10
- Urgency: Urgent = 1000, Not urgent = 0
- Sort tasks by score descending.
- Primary MIT is the highest-scoring incomplete task.
- Score >= 1000 is `Immediate`.
- Score >= 500 and < 1000 is `Next week`.
- Score < 500 is `Someday`.

Do not change these constants or buckets unless the user explicitly changes the priority system.

## UI Direction

- Keep the interface restrained, simple, and functional.
- Preserve manual day/night mode.
- Main palette:
  - Day background: white
  - Day primary text: black
  - Secondary text: light grey
  - Accent/important text/buttons: red
  - Night mode should invert the base surface while keeping red as the accent.
- Avoid decorative gradients, oversized cards, and unnecessary visual clutter.
- Prefer clear spacing, readable hierarchy, and direct controls.

## Engineering Constraints

- Do not add new dependencies without a clear reason.
- Prefer native Android SDK APIs over app frameworks for this small app.
- Keep persistence local unless the user requests sync/accounts.
- Keep reminder behavior based on `AlarmManager` and notification channels.
- Keep GitHub Actions producing a debug APK artifact named `priority-todo-debug-apk`.

## Verification

When possible, run:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
```

If local Java/Gradle is unavailable, push to GitHub and verify the `Android APK` workflow passes.

Also run lightweight checks before committing:

```bash
xmllint --noout app/src/main/AndroidManifest.xml app/src/main/res/values/*.xml app/src/main/res/drawable/*.xml
git diff --check -- .
```

## Git Notes

- Keep diffs small and focused.
- Do not commit local intake images or `.omx/`; they are intentionally ignored.
- Use commit messages that explain why the change was made.
