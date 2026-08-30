import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import http from "node:http";
import { tmpdir } from "node:os";
import path from "node:path";
import { Readable } from "node:stream";

import { createAppServer } from "../server.js";
import { MAX_SYNC_STATE_BYTES } from "../lib/store.js";

async function withServer(run, options = {}) {
  const directory = await mkdtemp(path.join(tmpdir(), "priority-todo-web-"));
  const dataFile = path.join(directory, "state.json");
  const server = createAppServer({ dataFile, ...options });
  await new Promise((resolve) => server.listen(0, "127.0.0.1", resolve));
  const address = server.address();
  try {
    await run(`http://127.0.0.1:${address.port}`, dataFile);
  } finally {
    await new Promise((resolve, reject) => server.close((error) => error ? reject(error) : resolve()));
  }
}

test("health, state, sync, and atomic persistence happy path", async () => {
  await withServer(async (baseUrl, dataFile) => {
    const health = await fetch(`${baseUrl}/api/health`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { ok: true });

    const response = await fetch(`${baseUrl}/api/sync`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        tasks: [{ id: "1", title: "Ship it", impact: "H", effort: "L", createdAt: 1, updatedAt: 2 }],
        taskTombstones: [],
        categories: [{ name: "Work", updatedAt: 2, deletedAt: 0 }]
      })
    });
    assert.equal(response.status, 200);
    assert.equal((await response.json()).tasks[0].title, "Ship it");

    const stateResponse = await fetch(`${baseUrl}/api/state`);
    assert.equal(stateResponse.status, 200);
    assert.equal((await stateResponse.json()).tasks.length, 1);
    assert.equal(JSON.parse(await readFile(dataFile, "utf8")).tasks.length, 1);
  });
});

test("sync rejects malformed JSON and invalid state shapes", async () => {
  await withServer(async (baseUrl) => {
    const malformed = await fetch(`${baseUrl}/api/sync`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "{"
    });
    assert.equal(malformed.status, 400);

    const invalid = await fetch(`${baseUrl}/api/sync`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ tasks: "not-an-array", taskTombstones: [], categories: [] })
    });
    assert.equal(invalid.status, 400);
  });
});

test("sync rejects unsupported content types and oversized request bodies", async () => {
  await withServer(async (baseUrl) => {
    const unsupported = await fetch(`${baseUrl}/api/sync`, {
      method: "POST",
      headers: { "content-type": "text/plain" },
      body: "{}"
    });
    assert.equal(unsupported.status, 415);

    const oversized = await fetch(`${baseUrl}/api/sync`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: "x".repeat(MAX_SYNC_STATE_BYTES + 1)
    });
    assert.equal(oversized.status, 413);
    assert.match((await oversized.json()).error, /too large/i);
  });
});

test("an oversized merged state is rejected without changing persisted state", async () => {
  await withServer(async (baseUrl, dataFile) => {
    const firstState = stateWithNotes("first", "a".repeat(1_100_000));
    const secondState = stateWithNotes("second", "b".repeat(1_100_000));
    assert.ok(Buffer.byteLength(JSON.stringify(firstState)) < MAX_SYNC_STATE_BYTES);
    assert.ok(Buffer.byteLength(JSON.stringify(secondState)) < MAX_SYNC_STATE_BYTES);

    const first = await postState(baseUrl, firstState);
    assert.equal(first.status, 200);
    const persistedBefore = await readFile(dataFile, "utf8");

    const second = await postState(baseUrl, secondState);
    assert.equal(second.status, 413);
    assert.match((await second.json()).error, /too large/i);
    assert.equal(await readFile(dataFile, "utf8"), persistedBefore);

    const state = await (await fetch(`${baseUrl}/api/state`)).json();
    assert.deepEqual(state.tasks.map((task) => task.id), ["first"]);
  });
});

test("unknown API routes return JSON 404", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(`${baseUrl}/api/nope`);
    assert.equal(response.status, 404);
    assert.match(response.headers.get("content-type"), /application\/json/);
  });
});

test("root serves the web client", async () => {
  await withServer(async (baseUrl) => {
    const response = await fetch(baseUrl);
    assert.equal(response.status, 200);
    assert.match(response.headers.get("content-type"), /text\/html/);
    assert.match(await response.text(), /Priority Todo/);
  });
});

test("JavaScript assets are served with JavaScript MIME types", async () => {
  await withServer(async (baseUrl) => {
    const app = await fetch(`${baseUrl}/app.js`);
    assert.equal(app.status, 200);
    assert.match(app.headers.get("content-type"), /text\/javascript/);
    assert.match(await app.text(), /syncNow/);

    const model = await fetch(`${baseUrl}/model.js`);
    assert.equal(model.status, 200);
    assert.match(model.headers.get("content-type"), /text\/javascript/);
    assert.match(await model.text(), /scoreTask/);
  });
});

test("Host validation accepts localhost, literal IPs, and configured names", async () => {
  await withServer(async (baseUrl) => {
    for (const host of ["localhost", "127.0.0.1", "192.168.1.20", "todo-box.local"]) {
      const response = await requestWithHost(baseUrl, "/api/health", host);
      assert.equal(response.statusCode, 200, host);
    }
  }, { allowedHosts: ["todo-box.local"] });
});

test("Host validation rejects unconfigured hostnames", async () => {
  await withServer(async (baseUrl) => {
    const response = await requestWithHost(baseUrl, "/api/state", "attacker.example");
    assert.equal(response.statusCode, 421);
    assert.match(JSON.parse(response.body).error, /host/i);
  });
});

test("a static read failure only terminates that response and leaves the server healthy", async () => {
  let failNextRead = true;
  await withServer(async (baseUrl) => {
    await assert.rejects(fetch(`${baseUrl}/app.js`));
    const health = await fetch(`${baseUrl}/api/health`);
    assert.equal(health.status, 200);
    assert.deepEqual(await health.json(), { ok: true });
  }, {
    createStaticReadStream(filePath) {
      if (failNextRead) {
        failNextRead = false;
        return new Readable({
          read() {
            this.destroy(new Error(`Injected read failure for ${filePath}`));
          }
        });
      }
      throw new Error("Unexpected second static read");
    }
  });
});

function stateWithNotes(id, notes) {
  return {
    tasks: [{ id, title: id, notes, impact: "H", effort: "M", createdAt: 1, updatedAt: 1 }],
    taskTombstones: [],
    categories: []
  };
}

function postState(baseUrl, state) {
  return fetch(`${baseUrl}/api/sync`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(state)
  });
}

function requestWithHost(baseUrl, pathname, hostHeader) {
  const target = new URL(baseUrl);
  return new Promise((resolve, reject) => {
    const request = http.request({
      hostname: target.hostname,
      port: target.port,
      path: pathname,
      headers: { host: hostHeader }
    }, (response) => {
      const chunks = [];
      response.on("data", (chunk) => chunks.push(chunk));
      response.on("end", () => resolve({
        statusCode: response.statusCode,
        body: Buffer.concat(chunks).toString("utf8")
      }));
    });
    request.on("error", reject);
    request.end();
  });
}
