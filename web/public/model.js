const IMPACT_VALUES = Object.freeze({ H: 900, M: 600, L: 300 });
const EFFORT_VALUES = Object.freeze({ H: 30, M: 20, L: 10 });
const IMPACT_RANKS = Object.freeze({ H: 3, M: 2, L: 1 });

export function scoreTask(task) {
  const impact = IMPACT_VALUES[task.impact] ?? IMPACT_VALUES.H;
  const effort = EFFORT_VALUES[task.effort] ?? EFFORT_VALUES.M;
  const rank = IMPACT_RANKS[task.impact] ?? IMPACT_RANKS.H;
  const activeScore = (task.urgent ? 1000 : 0) + (10 * (impact / effort)) + rank;
  return task.snoozed ? activeScore - 5000 : activeScore;
}

export function bucketForTask(task) {
  const score = scoreTask(task);
  if (score >= 1000) return "Immediate";
  if (score >= 500) return "Next week";
  return "Someday";
}

export function sortTasks(tasks) {
  return [...tasks].sort((left, right) => {
    const byScore = scoreTask(right) - scoreTask(left);
    if (byScore !== 0) return byScore;
    return numberOr(left.createdAt, 0) - numberOr(right.createdAt, 0);
  });
}

export function shouldApplySyncResponse(requestedRevision, currentRevision) {
  return requestedRevision === currentRevision;
}

export function hasSameSyncState(left, right) {
  return JSON.stringify(left) === JSON.stringify(right);
}

export function nextMutationTimestamp(state, now = Date.now()) {
  let greatestObserved = 0;
  for (const task of state.tasks ?? []) greatestObserved = Math.max(greatestObserved, timestampOr(task.updatedAt, 0));
  for (const tombstone of state.taskTombstones ?? []) greatestObserved = Math.max(greatestObserved, timestampOr(tombstone.deletedAt, 0));
  for (const category of state.categories ?? []) {
    greatestObserved = Math.max(
      greatestObserved,
      timestampOr(category.updatedAt, 0),
      timestampOr(category.deletedAt, 0)
    );
  }
  return Math.max(timestampOr(now, 0), greatestObserved + 1);
}

export function normalizeTask(value, now = Date.now()) {
  const createdAt = timestampOr(value.createdAt, now);
  const updatedAt = timestampOr(value.updatedAt, createdAt);
  const category = typeof value.category === "string" && value.category.trim()
    ? value.category.trim()
    : null;

  return {
    ...value,
    id: value.id.trim(),
    title: stringOr(value.title, "").trim(),
    notes: stringOr(value.notes, ""),
    impact: enumOr(value.impact, ["H", "M", "L"], "H"),
    effort: enumOr(value.effort, ["H", "M", "L"], "M"),
    category,
    urgent: Boolean(value.urgent),
    quickTask: Boolean(value.quickTask),
    snoozed: Boolean(value.snoozed),
    recurringMit: Boolean(value.recurringMit),
    completed: Boolean(value.completed),
    createdAt,
    updatedAt,
    reminderAt: timestampOr(value.reminderAt, 0),
    reminderRepeatUnit: enumOr(value.reminderRepeatUnit, ["none", "day", "week", "month"], "none"),
    reminderRepeatEvery: Math.max(1, Math.trunc(numberOr(value.reminderRepeatEvery, 1)))
  };
}

export function validateSyncState(value) {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    throw new TypeError("Sync body must be an object");
  }
  for (const field of ["tasks", "taskTombstones", "categories"]) {
    if (!Array.isArray(value[field])) throw new TypeError(`${field} must be an array`);
  }
  for (const task of value.tasks) {
    if (!task || typeof task !== "object" || Array.isArray(task) || typeof task.id !== "string" || !task.id.trim()) {
      throw new TypeError("Every task must have a non-empty string id");
    }
    if (task.updatedAt !== undefined && !validTimestamp(task.updatedAt)) {
      throw new TypeError("Task updatedAt must be a non-negative number");
    }
  }
  for (const tombstone of value.taskTombstones) {
    if (!tombstone || typeof tombstone !== "object" || typeof tombstone.id !== "string" || !tombstone.id.trim() || !validTimestamp(tombstone.deletedAt)) {
      throw new TypeError("Every task tombstone needs id and deletedAt");
    }
  }
  for (const category of value.categories) {
    if (!category || typeof category !== "object" || typeof category.name !== "string" || !category.name.trim()) {
      throw new TypeError("Every category needs a non-empty name");
    }
    if (!validTimestamp(category.updatedAt) || !validTimestamp(category.deletedAt)) {
      throw new TypeError("Category timestamps must be non-negative numbers");
    }
  }
}

function validTimestamp(value) {
  return typeof value === "number" && Number.isFinite(value) && value >= 0;
}

function timestampOr(value, fallback) {
  return validTimestamp(value) ? Math.trunc(value) : fallback;
}

function numberOr(value, fallback) {
  return typeof value === "number" && Number.isFinite(value) ? value : fallback;
}

function stringOr(value, fallback) {
  return typeof value === "string" ? value : fallback;
}

function enumOr(value, allowed, fallback) {
  return allowed.includes(value) ? value : fallback;
}
