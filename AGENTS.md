# Agent Instructions

This repository contains a native Android app for a priority-based to-do system. Follow these instructions when modifying the project.

## Project Shape

- Android app module: `app/`
- Main UI: `app/src/main/java/com/tj90/prioritytodo/MainActivity.java`
- Task scoring model: `app/src/main/java/com/tj90/prioritytodo/TodoTask.java`
- Local persistence: `app/src/main/java/com/tj90/prioritytodo/TaskStore.java`
- Priority palette and rank colors: `app/src/main/java/com/tj90/prioritytodo/PriorityPalette.java`
- Reminder scheduling: `app/src/main/java/com/tj90/prioritytodo/ReminderScheduler.java`
- Notification receiver: `app/src/main/java/com/tj90/prioritytodo/ReminderReceiver.java`
- Android sync transport: `app/src/main/java/com/tj90/prioritytodo/SyncClient.java`
- Shared sync schema: `app/src/main/java/com/tj90/prioritytodo/SyncState.java`
- Android tests: `app/src/test/java/com/tj90/prioritytodo/`
- Localhost web app and sync service: `web/`
- Web merge/persistence model: `web/lib/store.js`
- Web API/static server: `web/server.js`
- Web client: `web/public/`
- Web tests: `web/test/`
- APK CI workflow: `.github/workflows/android.yml`
- Emulator smoke script: `.github/scripts/android-smoke.sh`

## Product Rules

Keep the app aligned with the handwritten priority system:

```text
Score = Urgency + 10 × (Impact / Effort) + ImpactRank
```

- Impact: High = 900, Medium = 600, Low = 300
- Effort: High = 30, Medium = 20, Low = 10
- Impact rank: High = 3, Medium = 2, Low = 1
- Urgency: Urgent = 1000, Not urgent = 0
- The `10 ×` scale preserves the original return-on-effort ordering. `ImpactRank` only breaks equal-ROI ties toward higher absolute impact; it must never be large enough to cross an ROI tier.
- All 18 Impact × Effort × Urgency combinations must have unique scores. Lowest urgent score must remain above highest non-urgent score.
- Snoozed / Later tasks receive a 5000-point penalty after active priority is calculated so they fall below every active task.
- Keep base task order by score descending, then by `createdAt` for ties.
- In the active list, incomplete tasks that are not Later and have a reminder
  within the next 48 hours appear before other tasks. This includes overdue
  reminders. Order these tasks by `reminderAt` ascending (oldest overdue first),
  then by base score descending and `createdAt` for equal reminder times.
  Reminders beyond 48 hours follow base task order until they enter the window.
  Later tasks remain below active tasks regardless of their reminder time.
- Primary MIT is the first incomplete task in this reminder-aware active order;
  a near reminder can outrank a task with a higher base score.
- Score >= 1000 is `Immediate`.
- Score >= 500 and < 1000 is `Next week`.
- Score < 500 is `Someday`.

Do not change these constants, score math, or bucket thresholds unless the user explicitly changes the priority system.

## Reminder and Recurrence Rules

- The add/edit sheet uses a compact, programmatically drawn clock-only reminder
  button. Do not restore the alarm emoji or `Add reminder` text inside the
  control.
- After a reminder date and time is set, expose `Repeats every` as a numeric
  field plus a unit dropdown. Valid intervals are `1..999`; supported persisted
  units are `none`, `hour`, `day`, `week`, `month`, and `year`.
- A one-off reminder keeps `reminderRepeatUnit = none` and
  `reminderRepeatEvery = 1`. Reminder and recurrence values never affect the
  base score or bucket; `reminderAt` affects active-list order only as specified
  above. Do not persist a time-based bonus into task or sync state.
- Re-evaluate reminder-aware order when Android resumes and when a reminder
  enters the 48-hour window while the app is open. Refresh the browser view
  when it becomes visible and at the same window transition.
- Recurrence is completion-driven. Alarm delivery, notification dismissal, app
  startup, import, and sync must not advance `reminderAt`. An overdue recurring
  occurrence remains the current occurrence until the user completes it.
- Completing a recurring occurrence retains that completed task and creates one
  active successor. Calculate the successor from the later of the scheduled
  reminder time and completion time, then add the selected calendar interval.
- Successor identity must be deterministic and identical on Android and web:
  the direct successor ID is `<completed-task-id>:next`. This is required so
  concurrent offline completions converge through last-write-wins sync instead
  of creating duplicates.
- Undo/reopen restores the completed occurrence, removes and tombstones its
  direct successor, and preserves monotonic mutation ordering. Recompletion
  reuses the same successor ID with a newer `updatedAt`.
- Preserve overdue recurring values when editing unrelated task fields. Expired
  one-off reminders may be cleared, but an edit must never silently disable a
  recurring series.
- Keep Android and web reminder-aware ordering, calendar arithmetic,
  normalization, completion, Undo, and tombstone behavior aligned. Do not
  reintroduce automatic recurring rescheduling in `ReminderReceiver`.

## UI Direction

- Keep the interface restrained, simple, and functional, optimized for one-handed thumb use.
- Top zone informs; bottom zone acts.
- The primary action uses the neutral dark accent (`#3A3D49` day / `#C8CBD6` night), not priority red.
- Task priority color in the current UI is rank-based VIBGYOR: the top sorted task is red and lower-ranked tasks progress toward violet via `PriorityPalette.spectrumColor(...)`.
- Bucket colors and labels still exist in the model/palette for compatibility, tests, and any bucket-specific surfaces, but do not reintroduce noisy bucket labels into the main list without an explicit design request.
- Three-way theme: Day / Night / Auto (system).
- Handedness toggle mirrors the FAB position and swipe-to-complete direction.
- Primary interactions: tap the row circle to complete, swipe toward the thumb to complete, swipe away to send to Later, long-press a row to edit.
- Add/edit happens in a bottom sheet. Keep the sheet usable with the keyboard open; GitHub Actions smoke tests this path.
- Task details use a progressive `Add details` affordance so an empty multiline field never crowds the sheet. Details are reference material and never affect scoring; expanded rows/heroes may show capped previews.
- The sheet header always exposes one compact list picker at the top right. It supports `No list`, existing lists, and inline list creation; do not restore the old full-width LIST chip row.
- Keep the reminder clock, repeat interval field, and repeat-unit dropdown compact
  enough for the bottom sheet and keyboard-open layout. Invalid repeat values
  must remain visibly invalid and keep Save disabled; never silently coerce the
  displayed value to a different saved interval.
- Raw scores are never shown.
- Avoid decorative gradients, oversized cards, and unnecessary visual clutter.
- Prefer clear spacing, readable hierarchy, direct controls, and stable touch targets.

## Web Sync and Deployment

Keep sync local-first and optional. Android must remain fully usable when the
server or network is unavailable.

### Local development

- Run the dependency-free web service from `web/` with `npm start`.
- Default origin: `http://localhost:8787`.
- Android emulator origin: `http://10.0.2.2:8787`.
- Direct localhost/private-LAN HTTP may omit Cloudflare credentials.
- Web and Android must continue using the exact
  `{tasks, taskTombstones, categories}` schema and monotonic mutation
  timestamps.
- Deploy the updated web sync service before distributing an APK that can write
  new recurrence enum values such as `hour` or `year`. A mixed-version client or
  server can otherwise normalize an unknown unit to `none` and end the series.

### Production environment

- Browser URL: `https://tasks.thejas.space`.
- Browser authentication: Cloudflare Access email OTP restricted to
  `thejas.sushil@gmail.com`.
- Android sync URL: `https://tasks-sync.thejas.space`.
- Android authentication: Cloudflare Access Service Auth using
  `CF-Access-Client-Id` and `CF-Access-Client-Secret` headers.
- Do not configure Android to sync through `https://tasks.thejas.space`; that
  hostname requires interactive browser OTP.
- Do not create a separate Access application for
  `tasks.thejas.space/api/*`. The browser page and its same-origin API calls
  must share one Access application/audience.
- `sync.tasks.thejas.space` is retired and must not be restored. Standard
  Cloudflare wildcard certificates do not cover that two-level hostname.

### Vostro host

- SSH target: `admin@100.126.128.15` (`vostro2520`).
- Deployment directory: `/home/admin/Downloads/Scripts/to-do-app`.
- Node origin: `127.0.0.1:8787`.
- User services: `to-do-app.service` and `cloudflared-tasks.service`.
- User-managed tunnel: `tasks-vostro`.
- Tunnel config: `/home/admin/.cloudflared/tasks-config.yml`.
- Persistent sync state: `/home/admin/Downloads/Scripts/to-do-app/.data/sync-state.json`.
- Preserve `.data/` when deploying updates. Restart `to-do-app.service` after
  runtime changes and verify `/api/health` locally before checking Cloudflare.

### Credential safety

- Never commit, print, log, or paste Cloudflare Client IDs, Client Secrets, API
  tokens, tunnel credentials, or generated credential files into the repo.
- Never compile the Cloudflare Client Secret into the APK.
- Android stores Access credentials in the private
  `priority_todo_sync_credentials` preferences file; backup and device transfer
  must continue excluding it.
- Cloudflare credentials must be supplied as a complete pair, may only be sent
  to the HTTPS origin they were saved for, and must be cleared when the server
  origin changes.
- Keep the browser email/OTP policy and Android service-token policy separate;
  a machine token must not bypass the human website login.
- Private credential handoff files live outside the repository. Do not move
  them into project documentation or fixtures.

## Engineering Constraints

- Do not add new dependencies without a clear reason and explicit user approval.
- Prefer native Android SDK APIs over app frameworks for this small app.
- The UI is built programmatically; there are no XML layouts for the main screen.
- Keep persistence local-first. Optional sync must fail safely without blocking
  local task creation, editing, completion, reminders, import, or export.
- Keep reminder behavior based on `AlarmManager` and notification channels.
- Keep GitHub Actions producing a debug APK artifact named `priority-todo-debug-apk`.
- There is no `gradlew` wrapper in the repo; CI provisions Gradle 9.4.1.

## Verification

When local JDK 17, Android SDK, and Gradle are available, run:

```bash
gradle --no-daemon testDebugUnitTest assembleDebug
cd web && npm test
```

Before committing, also run lightweight checks when applicable:

```bash
xmllint --noout app/src/main/AndroidManifest.xml app/src/main/res/values/*.xml app/src/main/res/drawable/*.xml app/src/main/res/xml/*.xml
git diff --check -- .
```

If local Android tooling is unavailable, push to a `main` or `codex/**` branch and verify the `Android APK` workflow passes. That workflow runs:

- `testDebugUnitTest`
- `assembleDebug`
- exact scoring-matrix, 18-way uniqueness, urgency-band, ROI-order, bucket, and snooze-boundary unit tests
- reminder interval validation, hour/day/week/month/year calendar behavior,
  deterministic successor identity, completion/reopen/recompletion, concurrent
  merge convergence, and successor-tombstone tests
- reminder-aware ordering tests for the 48-hour boundary, overdue and nearer
  reminders, unchanged base scores, and Later/completed exclusions
- Cloudflare Access header, redirect, HTTPS-only, complete-pair, and
  origin-binding unit tests
- localhost web API, merge, tombstone, size-boundary, Host-header, timeout, and
  static-asset tests
- emulator smoke screenshots
- legacy persistence migration, live Android-to-web and web-to-Android sync
- launch, large-font, three-field sync setup, add-sheet keyboard,
  multiline-details persistence, inline list creation, task movement, task-add,
  and completion checks

For production checks, verify all of these without exposing credentials:

- `tasks.thejas.space` redirects anonymous browser traffic to Cloudflare Access.
- `tasks.thejas.space/` and `tasks.thejas.space/api/*` share the same Access
  audience so an authenticated browser can sync.
- `tasks-sync.thejas.space` rejects anonymous traffic.
- `tasks-sync.thejas.space/api/health` returns `200` only with the Android
  service-token headers.
- The Android service token does not bypass login on `tasks.thejas.space`.

## Git Notes

- Keep diffs small and focused.
- Do not commit local intake images, `.omx/`, `.omc/`, or generated scratch artifacts unless the user explicitly requests them.
- Do not commit credential Markdown/text/JSON files or anything under personal
  key directories.
- Preserve unrelated dirty work in the tree.
- Use commit messages that explain why the change was made.
