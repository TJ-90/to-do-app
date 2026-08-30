import test from "node:test";
import assert from "node:assert/strict";
import { mkdtemp, readFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import path from "node:path";

import { createAppServer } from "../server.js";

async function withServer(run) {
  const directory = await mkdtemp(path.join(tmpdir(), "priority-todo-web-"));
  const dataFile = path.join(directory, "state.json");
  const server = createAppServer({ dataFile });
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
