import test from "node:test";
import assert from "node:assert/strict";

import { mergeState } from "../lib/store.js";

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
