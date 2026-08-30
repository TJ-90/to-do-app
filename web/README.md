# Priority Todo web and sync service

A dependency-free localhost companion for the Android app. It serves a responsive browser UI and a file-backed sync API from one Node process.

## Run

Node 20 or newer is required.

```bash
cd web
npm start
```

Open `http://localhost:8787`. The server listens on all interfaces by default so an Android emulator or device on the same network can reach it. Override either setting when needed:

```bash
HOST=127.0.0.1 PORT=9000 npm start
```

State is stored atomically at `web/.data/sync-state.json`; the directory is ignored by Git. This service has no authentication or TLS and is intended only for a trusted localhost or private LAN.

## Android connection

The web client always syncs against its own origin. Point the Android app's sync-server setting at the same process:

- Android emulator: `http://10.0.2.2:8787`
- Physical device: `http://<computer-lan-ip>:8787`

Both clients send their local state to `POST /api/sync`. The response is the merged state, using `updatedAt` for task last-write-wins resolution and deletion tombstones to prevent removed tasks from returning. Older tasks without `updatedAt` use `createdAt` as their initial version timestamp.

## API

- `GET /api/health` — service health
- `GET /api/state` — current normalized state
- `POST /api/sync` — merge and return `{ tasks, taskTombstones, categories }`

## Verify

```bash
npm test
```

The tests cover scoring/order invariants, merge conflicts and tombstones, category deletion semantics, persistence, and API success/error paths.
