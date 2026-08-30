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

Literal IP addresses and `localhost` are accepted by default. If you connect through a hostname, explicitly allow it with a comma-separated list:

```bash
ALLOWED_HOSTS=todo-box.local,my-laptop npm start
```

Requests carrying any other hostname are rejected to protect the unauthenticated local service from DNS-rebinding attacks.

### Cloudflare Access

Interactive users can protect the website with an email/OTP Access policy. The
Android client cannot complete a browser login, so protect the more-specific
`/api/*` Access application with both the user's email allow policy and a
Service Auth policy. Paste that service token's Client ID and Client Secret into
the Android sync settings. Credential-free localhost and private-LAN sync still
work as before.

Both clients send their local state to `POST /api/sync`. The response is the merged state, using `updatedAt` for task last-write-wins resolution and deletion tombstones to prevent removed tasks from returning. Older tasks without `updatedAt` use `createdAt` as their initial version timestamp.

Clients should treat timestamps as a monotonic logical clock: after each response, observe the greatest `updatedAt` or `deletedAt` value and generate subsequent mutation timestamps greater than that maximum (while still using the local clock when it is ahead). The server preserves client timestamps and returns canonical state; it does not rewrite timestamps on arrival.

## API

- `GET /api/health` — service health
- `GET /api/state` — current normalized state
- `POST /api/sync` — merge and return `{ tasks, taskTombstones, categories }`

Request bodies and compact merged responses are limited to 2 MiB. A merge that would exceed the response limit is rejected with JSON status `413` and leaves the previously persisted state unchanged.

## Verify

```bash
npm test
```

The tests cover scoring/order invariants, merge conflicts and tombstones, category deletion semantics, persistence, and API success/error paths.
