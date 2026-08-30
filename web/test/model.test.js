import test from "node:test";
import assert from "node:assert/strict";

import {
  bucketForTask,
  hasSameSyncState,
  nextMutationTimestamp,
  scoreTask,
  shouldApplySyncResponse,
  sortTasks
} from "../public/model.js";

test("scoring matches the Android priority formula and buckets", () => {
  assert.equal(scoreTask({ impact: "H", effort: "L", urgent: true }), 1903);
  assert.equal(scoreTask({ impact: "L", effort: "H", urgent: false }), 101);
  assert.equal(scoreTask({ impact: "H", effort: "L", urgent: true, snoozed: true }), -3097);
  assert.equal(bucketForTask({ impact: "H", effort: "M", urgent: true }), "Immediate");
  assert.equal(bucketForTask({ impact: "H", effort: "L", urgent: false }), "Next week");
  assert.equal(bucketForTask({ impact: "L", effort: "H", urgent: false }), "Someday");
});

test("all 18 active combinations are unique and urgency bands do not overlap", () => {
  const scores = [];
  for (const impact of ["H", "M", "L"]) {
    for (const effort of ["H", "M", "L"]) {
      for (const urgent of [false, true]) {
        scores.push(scoreTask({ impact, effort, urgent }));
      }
    }
  }
  assert.equal(new Set(scores).size, 18);
  const urgentScores = scores.filter((_, index) => index % 2 === 1);
  const regularScores = scores.filter((_, index) => index % 2 === 0);
  assert.ok(Math.min(...urgentScores) > Math.max(...regularScores));
});

test("sorting uses score descending then creation time ascending", () => {
  const tasks = [
    { id: "new", impact: "M", effort: "M", createdAt: 20 },
    { id: "high", impact: "H", effort: "L", createdAt: 30 },
    { id: "old", impact: "M", effort: "M", createdAt: 10 }
  ];
  assert.deepEqual(sortTasks(tasks).map((task) => task.id), ["high", "old", "new"]);
});

test("a stale sync response cannot replace a newer local mutation", () => {
  assert.equal(shouldApplySyncResponse(4, 4), true);
  assert.equal(shouldApplySyncResponse(4, 5), false);
});

test("unchanged sync states do not require storage or DOM work", () => {
  const first = { tasks: [{ id: "a" }], taskTombstones: [], categories: [] };
  const same = { tasks: [{ id: "a" }], taskTombstones: [], categories: [] };
  const changed = { tasks: [{ id: "b" }], taskTombstones: [], categories: [] };

  assert.equal(hasSameSyncState(first, same), true);
  assert.equal(hasSameSyncState(first, changed), false);
});

test("mutation timestamps advance past remote state when the local clock is slow", () => {
  const state = {
    tasks: [{ id: "task", updatedAt: 9_000 }],
    taskTombstones: [{ id: "deleted-task", deletedAt: 10_000 }],
    categories: [
      { name: "Current", updatedAt: 11_000, deletedAt: 0 },
      { name: "Deleted", updatedAt: 8_000, deletedAt: 12_000 }
    ]
  };

  assert.equal(nextMutationTimestamp(state, 1_000), 12_001);
});

test("mutation timestamps use wall-clock time when it is ahead", () => {
  const state = {
    tasks: [{ id: "task", updatedAt: 9_000 }],
    taskTombstones: [],
    categories: []
  };

  assert.equal(nextMutationTimestamp(state, 20_000), 20_000);
});
