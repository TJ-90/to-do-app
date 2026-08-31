const IMPACT_VALUES = Object.freeze({ H: 900, M: 600, L: 300 });
const EFFORT_VALUES = Object.freeze({ H: 30, M: 20, L: 10 });
const IMPACT_RANKS = Object.freeze({ H: 3, M: 2, L: 1 });
const REPEAT_UNITS = Object.freeze(["none", "hour", "day", "week", "month", "year"]);

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
    reminderRepeatUnit: enumOr(
      value.reminderRepeatUnit,
      REPEAT_UNITS,
      "none"
    ),
    reminderRepeatEvery: Math.max(1, Math.trunc(numberOr(value.reminderRepeatEvery, 1)))
  };
}

export function recurringSuccessorId(taskId) {
  return `${taskId}:next`;
}

export function nextRecurringOccurrence(task, completedAt, mutationTimestamp) {
  const unit = enumOr(task.reminderRepeatUnit, REPEAT_UNITS, null);
  const reminderAt = timestampOr(task.reminderAt, 0);
  if (!unit || unit === "none" || reminderAt === 0) return null;

  const every = Math.max(1, Math.trunc(numberOr(task.reminderRepeatEvery, 1)));
  const nextAt = new Date(Math.max(reminderAt, timestampOr(completedAt, 0)));
  if (unit === "hour") nextAt.setHours(nextAt.getHours() + every);
  if (unit === "day") nextAt.setDate(nextAt.getDate() + every);
  if (unit === "week") nextAt.setDate(nextAt.getDate() + (7 * every));
  if (unit === "month") addCalendarMonths(nextAt, every);
  if (unit === "year") addCalendarYears(nextAt, every);

  return normalizeTask({
    ...task,
    id: recurringSuccessorId(task.id),
    completed: false,
    snoozed: false,
    createdAt: mutationTimestamp,
    updatedAt: mutationTimestamp,
    reminderAt: nextAt.getTime(),
    reminderRepeatUnit: unit,
    reminderRepeatEvery: every
  });
}

export function completeTaskState(state, id, completedAt, mutationTimestamp) {
  const task = state.tasks.find((value) => value.id === id);
  if (!task || task.completed) return state;

  const successor = nextRecurringOccurrence(task, completedAt, mutationTimestamp);
  const tasks = state.tasks.map((value) => value.id === id
    ? { ...value, completed: true, snoozed: false, updatedAt: mutationTimestamp }
    : value);
  if (successor) {
    const existingIndex = tasks.findIndex((value) => value.id === successor.id);
    if (existingIndex >= 0) tasks[existingIndex] = successor;
    else tasks.push(successor);
  }
  return { ...state, tasks };
}

export function reopenTaskState(state, id, mutationTimestamp) {
  const task = state.tasks.find((value) => value.id === id);
  if (!task || !task.completed) return state;

  const successorId = recurringSuccessorId(id);
  const recurring = nextRecurringOccurrence(task, task.reminderAt, mutationTimestamp) !== null;
  const tasks = state.tasks
    .filter((value) => !recurring || value.id !== successorId)
    .map((value) => value.id === id
      ? { ...value, completed: false, updatedAt: mutationTimestamp }
      : value);
  if (!recurring) return { ...state, tasks };

  const taskTombstones = [...state.taskTombstones];
  const existingIndex = taskTombstones.findIndex((value) => value.id === successorId);
  if (existingIndex >= 0) {
    const existing = taskTombstones[existingIndex];
    taskTombstones[existingIndex] = {
      ...existing,
      deletedAt: Math.max(existing.deletedAt, mutationTimestamp)
    };
  } else {
    taskTombstones.push({ id: successorId, deletedAt: mutationTimestamp });
  }
  return { ...state, tasks, taskTombstones };
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

function addCalendarMonths(date, count) {
  const day = date.getDate();
  date.setDate(1);
  date.setMonth(date.getMonth() + count);
  date.setDate(Math.min(day, daysInMonth(date.getFullYear(), date.getMonth())));
}

function addCalendarYears(date, count) {
  addCalendarMonths(date, count * 12);
}

function daysInMonth(year, month) {
  return new Date(year, month + 1, 0).getDate();
}
