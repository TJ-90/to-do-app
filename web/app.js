const IMPACT = { H: 900, M: 600, L: 300 };
const EFFORT = { H: 30, M: 20, L: 10 };
const LS_KEY = "priority_todo_store";
const REPEAT_UNITS = ["none", "day", "week", "month"];

function score(t) {
  let s = IMPACT[t.impact] / EFFORT[t.effort] + (t.urgent ? 1000 : 0);
  if (t.snoozed) s -= 5000;
  return s;
}

function bucket(t) {
  const s = score(t);
  if (s >= 1000) return "Immediate";
  if (s >= 500) return "Next week";
  return "Someday";
}

function newTask() {
  return {
    id: crypto.randomUUID(),
    title: "",
    notes: "",
    impact: "H",
    effort: "M",
    dependency: "None",
    urgent: false,
    quickTask: false,
    snoozed: false,
    recurringMit: false,
    completed: false,
    createdAt: Date.now(),
    reminderAt: 0,
    reminderRepeatUnit: "none",
    reminderRepeatEvery: 1,
    updatedAt: Date.now(),
    deleted: false
  };
}

// Reject foreign/hostile fields; coerce types; clamp strings.
function ptdoSanitize(raw) {
  const t = newTask();
  if (raw && typeof raw === "object") {
    t.id = typeof raw.id === "string" ? raw.id.slice(0, 64) : t.id;
    t.title = typeof raw.title === "string" ? raw.title.slice(0, 500) : "";
    t.notes = typeof raw.notes === "string" ? raw.notes.slice(0, 5000) : "";
    t.impact = ["H", "M", "L"].includes(raw.impact) ? raw.impact : "H";
    t.effort = ["H", "M", "L"].includes(raw.effort) ? raw.effort : "M";
    t.dependency = typeof raw.dependency === "string" ? raw.dependency.slice(0, 200) : "None";
    t.urgent = !!raw.urgent;
    t.quickTask = !!raw.quickTask;
    t.snoozed = !!raw.snoozed;
    t.recurringMit = !!raw.recurringMit;
    t.completed = !!raw.completed;
    t.createdAt = Number.isFinite(raw.createdAt) ? raw.createdAt : Date.now();
    t.reminderAt = Number.isFinite(raw.reminderAt) ? raw.reminderAt : 0;
    t.reminderRepeatUnit = REPEAT_UNITS.includes(raw.reminderRepeatUnit)
      ? raw.reminderRepeatUnit
      : "none";
    t.reminderRepeatEvery = Number.isFinite(raw.reminderRepeatEvery)
      ? Math.max(1, Math.floor(raw.reminderRepeatEvery))
      : 1;
    t.updatedAt = Number.isFinite(raw.updatedAt) ? raw.updatedAt : t.createdAt;
    t.deleted = !!raw.deleted;
  }
  return t;
}

function loadAll() {
  try {
    const raw = JSON.parse(localStorage.getItem(LS_KEY) || "[]");
    return Array.isArray(raw) ? raw.map(ptdoSanitize) : [];
  } catch (e) {
    return [];
  }
}

function persist(all) {
  localStorage.setItem(LS_KEY, JSON.stringify(all));
}

function visible(all) {
  return all
    .filter((t) => !t.deleted)
    .sort((a, b) => score(b) - score(a));
}

let STATE = loadAll();

function touch(t) {
  t.updatedAt = Date.now();
}

function upsert(task) {
  touch(task);
  const idx = STATE.findIndex((t) => t.id === task.id);
  if (idx >= 0) STATE[idx] = task;
  else STATE.push(task);
  persist(STATE);
  render();
}

function softDelete(id) {
  const t = STATE.find((x) => x.id === id);
  if (!t) return;
  t.deleted = true;
  touch(t);
  persist(STATE);
  render();
}

function render() {
  const list = document.getElementById("task-list");
  list.replaceChildren();
  visible(STATE).forEach((t) => {
    const row = document.createElement("div");
    row.className = "task-row bucket-" + bucket(t).replace(" ", "-").toLowerCase();

    const circle = document.createElement("button");
    circle.className = "task-circle" + (t.completed ? " done" : "");
    circle.setAttribute("aria-label", "Complete task");
    circle.addEventListener("click", () => {
      t.completed = !t.completed;
      upsert(t);
    });

    const title = document.createElement("span");
    title.className = "task-title";
    title.textContent = t.title; // XSS-safe text assignment.

    const tag = document.createElement("span");
    tag.className = "task-bucket";
    tag.textContent = bucket(t);

    const del = document.createElement("button");
    del.className = "task-del";
    del.setAttribute("aria-label", "Delete task");
    del.textContent = "×";
    del.addEventListener("click", () => softDelete(t.id));

    row.append(circle, title, tag, del);
    list.appendChild(row);
  });
  document.getElementById("mit").textContent =
    visible(STATE).find((t) => !t.completed)?.title || "No active task";
}

function wireAddForm() {
  const form = document.getElementById("add-form");
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const t = newTask();
    t.title = document.getElementById("f-title").value.trim().slice(0, 500);
    t.impact = document.getElementById("f-impact").value;
    t.effort = document.getElementById("f-effort").value;
    t.urgent = document.getElementById("f-urgent").checked;
    if (!t.title) return;
    upsert(t);
    form.reset();
  });
}

function wireSync() {
  const btn = document.getElementById("sync-btn");
  const status = document.getElementById("sync-status");
  ptdoInitAuth((err) => {
    if (err) {
      status.textContent = "Auth error";
      return;
    }
    status.textContent = "Syncing…";
    ptdoSync(STATE)
      .then((merged) => {
        STATE = merged.map(ptdoSanitize);
        persist(STATE);
        render();
        status.textContent = "Synced " + new Date().toLocaleTimeString();
      })
      .catch((e) => {
        status.textContent = "Sync failed: " + e.message;
      });
  });
  btn.addEventListener("click", () => {
    document.getElementById("sync-status").textContent = "Signing in…";
    ptdoRequestToken();
  });
}

window.addEventListener("DOMContentLoaded", () => {
  wireAddForm();
  wireSync();
  render();
});
