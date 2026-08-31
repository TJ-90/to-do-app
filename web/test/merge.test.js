import test from "node:test";
import assert from "node:assert/strict";

import { maxObservedTimestamp, mergeState, normalizeState } from "../lib/store.js";
import { completeTaskState, reopenTaskState } from "../public/model.js";

test("merge is last-write-wins for tasks and retains the newest tombstone", () => {
  const current = {
    tasks: [{ id: "a", title: "old", createdAt: 1, updatedAt: 10 }],
    taskTombstones: [{ id: "gone", deletedAt: 5 }],
    categories: []
  };
  const incoming = {
    tasks: [
      { id: "a", title: "stale", createdAt: 1, updatedAt: 9 },
      { id: "b", title: "removed", createdAt: 2, updatedAt: 20 }
    ],
    taskTombstones: [{ id: "b", deletedAt: 20 }, { id: "gone", deletedAt: 8 }],
    categories: []
  };

  const merged = mergeState(current, incoming);
  assert.deepEqual(merged.tasks.map(({ id, title }) => ({ id, title })), [{ id: "a", title: "old" }]);
  assert.deepEqual(merged.taskTombstones, [
    { id: "b", deletedAt: 20 },
    { id: "gone", deletedAt: 8 }
  ]);
});

test("newer task can supersede an older tombstone", () => {
  const merged = mergeState(
    { tasks: [], taskTombstones: [{ id: "a", deletedAt: 10 }], categories: [] },
    { tasks: [{ id: "a", title: "restored", createdAt: 1, updatedAt: 11 }], taskTombstones: [], categories: [] }
  );
  assert.equal(merged.tasks[0].title, "restored");
});

test("categories merge using their latest update or deletion event", () => {
  const merged = mergeState(
    { tasks: [], taskTombstones: [], categories: [{ name: "Work", updatedAt: 20, deletedAt: 0 }] },
    { tasks: [], taskTombstones: [], categories: [
      { name: "Work", updatedAt: 10, deletedAt: 30 },
      { name: "Home", updatedAt: 15, deletedAt: 0 }
    ] }
  );
  assert.deepEqual(merged.categories, [
    { name: "Home", updatedAt: 15, deletedAt: 0 },
    { name: "Work", updatedAt: 10, deletedAt: 30 }
  ]);
});

test("normalization preserves client timestamps and exposes the observed logical-clock maximum", () => {
  const state = normalizeState({
    tasks: [{ id: "a", title: "Task", createdAt: 10, updatedAt: 500 }],
    taskTombstones: [{ id: "gone", deletedAt: 700 }],
    categories: [{ name: "Work", updatedAt: 600, deletedAt: 0 }]
  });

  assert.equal(state.tasks[0].updatedAt, 500);
  assert.equal(state.taskTombstones[0].deletedAt, 700);
  assert.equal(state.categories[0].updatedAt, 600);
  assert.equal(maxObservedTimestamp(state), 700);
});

test("a monotonic client timestamp lets a slow-clock delete supersede the observed task", () => {
  const current = normalizeState({
    tasks: [{ id: "a", title: "Edited elsewhere", createdAt: 1, updatedAt: 10_000 }],
    taskTombstones: [],
    categories: []
  });
  const nextTimestamp = maxObservedTimestamp(current) + 1;

  const merged = mergeState(current, {
    tasks: [],
    taskTombstones: [{ id: "a", deletedAt: nextTimestamp }],
    categories: []
  });

  assert.deepEqual(merged.tasks, []);
  assert.deepEqual(merged.taskTombstones, [{ id: "a", deletedAt: 10_001 }]);
});

test("concurrent recurring completions converge on one successor", () => {
  const parent = {
    id: "daily",
    title: "Daily task",
    completed: false,
    reminderAt: 1_000,
    reminderRepeatUnit: "day",
    reminderRepeatEvery: 1,
    createdAt: 1,
    updatedAt: 2
  };
  const base = { tasks: [parent], taskTombstones: [], categories: [] };
  const android = completeTaskState(base, parent.id, 2_000, 10);
  const browser = completeTaskState(base, parent.id, 2_000, 11);

  const merged = mergeState(android, browser);

  assert.equal(merged.tasks.filter((task) => task.id === "daily:next").length, 1);
  assert.equal(merged.tasks.find((task) => task.id === "daily:next").updatedAt, 11);
});

test("recurring undo tombstone removes the successor after sync", () => {
  const completed = completeTaskState({
    tasks: [{
      id: "weekly",
      title: "Weekly task",
      completed: false,
      reminderAt: 1_000,
      reminderRepeatUnit: "week",
      reminderRepeatEvery: 1,
      createdAt: 1,
      updatedAt: 2
    }],
    taskTombstones: [],
    categories: []
  }, "weekly", 2_000, 10);
  const reopened = reopenTaskState(completed, "weekly", 11);

  const merged = mergeState(completed, reopened);

  assert.deepEqual(merged.tasks.map((task) => task.id), ["weekly"]);
  assert.equal(merged.tasks[0].completed, false);
  assert.deepEqual(merged.taskTombstones, [{ id: "weekly:next", deletedAt: 11 }]);
});
