import { mkdir, readFile, rename, writeFile } from "node:fs/promises";
import path from "node:path";

import { normalizeTask, validateSyncState } from "../public/model.js";

export const MAX_SYNC_STATE_BYTES = 2 * 1024 * 1024;

export class SyncStateTooLargeError extends Error {
  constructor() {
    super("Merged sync state is too large");
    this.statusCode = 413;
  }
}

export const EMPTY_STATE = Object.freeze({
  tasks: Object.freeze([]),
  taskTombstones: Object.freeze([]),
  categories: Object.freeze([])
});

export function normalizeState(state) {
  const tasks = new Map();
  for (const rawTask of state.tasks ?? []) {
    if (!rawTask || typeof rawTask.id !== "string" || !rawTask.id.trim()) continue;
    const task = normalizeTask(rawTask);
    const previous = tasks.get(task.id);
    if (!previous || task.updatedAt >= previous.updatedAt) tasks.set(task.id, task);
  }

  const tombstones = new Map();
  for (const value of state.taskTombstones ?? []) {
    if (!value || typeof value.id !== "string" || !value.id.trim()) continue;
    const deletedAt = finiteTimestamp(value.deletedAt);
    const id = value.id.trim();
    if (deletedAt >= (tombstones.get(id)?.deletedAt ?? -1)) tombstones.set(id, { id, deletedAt });
  }

  const categories = new Map();
  for (const value of state.categories ?? []) {
    if (!value || typeof value.name !== "string" || !value.name.trim()) continue;
    const category = {
      name: value.name.trim(),
      updatedAt: finiteTimestamp(value.updatedAt),
      deletedAt: finiteTimestamp(value.deletedAt)
    };
    const key = category.name.toLocaleLowerCase();
    const eventAt = Math.max(category.updatedAt, category.deletedAt);
    const previous = categories.get(key);
    const previousEventAt = previous ? Math.max(previous.updatedAt, previous.deletedAt) : -1;
    if (!previous || eventAt >= previousEventAt) categories.set(key, category);
  }

  for (const [id, task] of tasks) {
    if ((tombstones.get(id)?.deletedAt ?? -1) >= task.updatedAt) tasks.delete(id);
  }

  return {
    tasks: [...tasks.values()].sort((a, b) => a.id.localeCompare(b.id)),
    taskTombstones: [...tombstones.values()].sort((a, b) => a.id.localeCompare(b.id)),
    categories: [...categories.values()].sort((a, b) => a.name.localeCompare(b.name))
  };
}

export function mergeState(current, incoming) {
  return normalizeState({
    tasks: [...(current.tasks ?? []), ...(incoming.tasks ?? [])],
    taskTombstones: [...(current.taskTombstones ?? []), ...(incoming.taskTombstones ?? [])],
    categories: [...(current.categories ?? []), ...(incoming.categories ?? [])]
  });
}

export function maxObservedTimestamp(state) {
  let maximum = 0;
  for (const task of state.tasks ?? []) maximum = Math.max(maximum, finiteTimestamp(task?.updatedAt));
  for (const tombstone of state.taskTombstones ?? []) maximum = Math.max(maximum, finiteTimestamp(tombstone?.deletedAt));
  for (const category of state.categories ?? []) {
    maximum = Math.max(maximum, finiteTimestamp(category?.updatedAt), finiteTimestamp(category?.deletedAt));
  }
  return maximum;
}

export class SyncStore {
  #dataFile;
  #queue = Promise.resolve();

  constructor(dataFile) {
    this.#dataFile = dataFile;
  }

  async read() {
    try {
      return normalizeState(JSON.parse(await readFile(this.#dataFile, "utf8")));
    } catch (error) {
      if (error.code === "ENOENT") return structuredClone(EMPTY_STATE);
      throw error;
    }
  }

  sync(incoming) {
    validateSyncState(incoming);
    const operation = this.#queue.then(async () => {
      const state = mergeState(await this.read(), incoming);
      if (Buffer.byteLength(JSON.stringify(state)) > MAX_SYNC_STATE_BYTES) {
        throw new SyncStateTooLargeError();
      }
      await this.#writeAtomically(state);
      return state;
    });
    this.#queue = operation.catch(() => {});
    return operation;
  }

  async #writeAtomically(state) {
    const directory = path.dirname(this.#dataFile);
    await mkdir(directory, { recursive: true });
    const temporary = `${this.#dataFile}.${process.pid}.${Date.now()}.tmp`;
    await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, { encoding: "utf8", mode: 0o600 });
    await rename(temporary, this.#dataFile);
  }
}

function finiteTimestamp(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? Math.trunc(value) : 0;
}
