import test from "node:test";
import assert from "node:assert/strict";

import {
  bucketForTask,
  completeTaskState,
  nextRecurringOccurrence,
  hasSameSyncState,
  nextMutationTimestamp,
  normalizeTask,
  recurringSuccessorId,
  reopenTaskState,
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

test("sync normalization preserves hourly and yearly reminder repeats", () => {
  const base = { id: "task", title: "Recurring", createdAt: 1, updatedAt: 2 };

  assert.equal(normalizeTask({ ...base, reminderRepeatUnit: "hour" }).reminderRepeatUnit, "hour");
  assert.equal(normalizeTask({ ...base, reminderRepeatUnit: "year" }).reminderRepeatUnit, "year");
});

test("completion creates a matching occurrence two days later", () => {
  const completedAt = new Date(2026, 7, 31, 13, 0, 0).getTime();
  const next = nextRecurringOccurrence({
    id: "current",
    title: "Fix shower head",
    notes: "Buy a washer",
    impact: "M",
    effort: "L",
    category: "Home",
    urgent: true,
    quickTask: true,
    snoozed: true,
    completed: false,
    reminderAt: new Date(2026, 7, 31, 9, 0, 0).getTime(),
    reminderRepeatUnit: "day",
    reminderRepeatEvery: 2,
    createdAt: 1,
    updatedAt: 2
  }, completedAt, completedAt + 1);

  assert.equal(next.id, "current:next");
  assert.equal(next.title, "Fix shower head");
  assert.equal(next.notes, "Buy a washer");
  assert.equal(next.category, "Home");
  assert.equal(next.completed, false);
  assert.equal(next.snoozed, false);
  assert.equal(next.createdAt, completedAt + 1);
  assert.equal(next.updatedAt, completedAt + 1);
  assert.equal(next.reminderAt, new Date(2026, 8, 2, 13, 0, 0).getTime());
});

test("monthly and yearly occurrences clamp to the target calendar month", () => {
  const task = {
    id: "current",
    title: "Calendar edge",
    reminderRepeatEvery: 1,
    createdAt: 1,
    updatedAt: 2
  };
  const january31 = new Date(2025, 0, 31, 10, 0, 0).getTime();
  const leapDay = new Date(2024, 1, 29, 10, 0, 0).getTime();

  const monthly = nextRecurringOccurrence({
    ...task,
    reminderAt: january31,
    reminderRepeatUnit: "month"
  }, january31, january31 + 1);
  const yearly = nextRecurringOccurrence({
    ...task,
    reminderAt: leapDay,
    reminderRepeatUnit: "year"
  }, leapDay, leapDay + 1);

  assert.equal(monthly.reminderAt, new Date(2025, 1, 28, 10, 0, 0).getTime());
  assert.equal(yearly.reminderAt, new Date(2025, 1, 28, 10, 0, 0).getTime());
});

test("complete, reopen, and recomplete reuse one deterministic successor", () => {
  const parent = normalizeTask({
    id: "series",
    title: "Water plants",
    reminderAt: 1_000,
    reminderRepeatUnit: "day",
    reminderRepeatEvery: 1,
    createdAt: 1,
    updatedAt: 2
  });
  const initial = { tasks: [parent], taskTombstones: [], categories: [] };

  const completed = completeTaskState(initial, parent.id, 1_000, 10);
  assert.equal(completed.tasks.find((task) => task.id === parent.id).completed, true);
  assert.deepEqual(completed.tasks.map((task) => task.id).sort(), ["series", "series:next"]);

  const reopened = reopenTaskState(completed, parent.id, 11);
  assert.equal(reopened.tasks.find((task) => task.id === parent.id).completed, false);
  assert.deepEqual(reopened.tasks.map((task) => task.id), ["series"]);
  assert.deepEqual(reopened.taskTombstones, [{ id: "series:next", deletedAt: 11 }]);

  const recompleted = completeTaskState(reopened, parent.id, 1_000, 12);
  assert.deepEqual(recompleted.tasks.map((task) => task.id).sort(), ["series", "series:next"]);
  assert.equal(recompleted.tasks.find((task) => task.id === "series:next").updatedAt, 12);
  assert.equal(recurringSuccessorId(parent.id), "series:next");
});
