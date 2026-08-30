export async function postSyncState(state, options = {}) {
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  const timeoutMs = options.timeoutMs ?? 5_000;
  const setTimeoutImpl = options.setTimeoutImpl ?? globalThis.setTimeout;
  const clearTimeoutImpl = options.clearTimeoutImpl ?? globalThis.clearTimeout;
  const controller = new AbortController();
  const timer = setTimeoutImpl(() => {
    controller.abort(new DOMException("Sync request timed out", "TimeoutError"));
  }, timeoutMs);

  try {
    return await fetchImpl("/api/sync", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify(state),
      signal: controller.signal
    });
  } finally {
    clearTimeoutImpl(timer);
  }
}
