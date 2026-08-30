import test from "node:test";
import assert from "node:assert/strict";

import { postSyncState } from "../public/sync.js";

test("sync requests abort after the configured timeout and clear the timer", async () => {
  let abortRequest;
  let clearedTimer;
  const pendingFetch = (_url, options) => new Promise((_resolve, reject) => {
    options.signal.addEventListener("abort", () => reject(options.signal.reason));
  });

  const request = postSyncState({ tasks: [], taskTombstones: [], categories: [] }, {
    fetchImpl: pendingFetch,
    timeoutMs: 5_000,
    setTimeoutImpl(callback, milliseconds) {
      assert.equal(milliseconds, 5_000);
      abortRequest = callback;
      return 42;
    },
    clearTimeoutImpl(timer) {
      clearedTimer = timer;
    }
  });

  abortRequest();
  await assert.rejects(request, (error) => error?.name === "TimeoutError");
  assert.equal(clearedTimer, 42);
});

test("successful sync requests clear their timeout without aborting", async () => {
  let controllerSignal;
  let clearedTimer;
  const response = { ok: true };

  const result = await postSyncState({ tasks: [], taskTombstones: [], categories: [] }, {
    fetchImpl: async (_url, options) => {
      controllerSignal = options.signal;
      return response;
    },
    setTimeoutImpl() {
      return 84;
    },
    clearTimeoutImpl(timer) {
      clearedTimer = timer;
    }
  });

  assert.equal(result, response);
  assert.equal(controllerSignal.aborted, false);
  assert.equal(clearedTimer, 84);
});
