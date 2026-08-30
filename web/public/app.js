import {
  hasSameSyncState,
  nextMutationTimestamp,
  normalizeTask,
  shouldApplySyncResponse,
  sortTasks
} from "/model.js";
import { postSyncState } from "/sync.js";

const STORAGE_KEY = "priority-todo-sync-state-v1";
const SPECTRUM = ["#d84343", "#ef6c38", "#e5a928", "#4f9a55", "#357cbf", "#4a58a8", "#7954a4"];

const elements = {
  active: document.querySelector("#active-tasks"),
  later: document.querySelector("#later-tasks"),
  completed: document.querySelector("#completed-tasks"),
  empty: document.querySelector("#empty-state"),
  tabs: document.querySelector("#list-tabs"),
  template: document.querySelector("#task-template"),
  taskDialog: document.querySelector("#task-dialog"),
  taskForm: document.querySelector("#task-form"),
  listsDialog: document.querySelector("#lists-dialog"),
  listManager: document.querySelector("#list-manager"),
  category: document.querySelector("#task-category"),
  syncStatus: document.querySelector("#sync-status")
};

let state = loadLocalState();
let activeList = "All";
let editingId = null;
let syncTimer = null;
let syncing = false;
let syncQueued = false;
let localRevision = 0;

document.querySelector("#today-label").textContent = new Intl.DateTimeFormat(undefined, {
  weekday: "long", month: "long", day: "numeric"
}).format(new Date()).toUpperCase();

const themeSelect = document.querySelector("#theme-select");
themeSelect.value = localStorage.getItem("priority-todo-theme") || "auto";
applyTheme(themeSelect.value);
themeSelect.addEventListener("change", () => {
  localStorage.setItem("priority-todo-theme", themeSelect.value);
  applyTheme(themeSelect.value);
});

document.querySelector("#add-task").addEventListener("click", () => openTaskDialog());
document.querySelector("#cancel-task").addEventListener("click", () => elements.taskDialog.close());
document.querySelector("#manage-lists").addEventListener("click", openListsDialog);
document.querySelector("#details-toggle").addEventListener("click", () => {
  document.querySelector("#notes-field").hidden = false;
  document.querySelector("#details-toggle").hidden = true;
  document.querySelector("#task-notes").focus();
});
document.querySelector("#create-list").addEventListener("click", createList);
elements.category.addEventListener("change", () => {
  if (elements.category.value !== "__new__") return;
  const name = window.prompt("Name this list")?.trim();
  if (!name) { elements.category.value = ""; return; }
  upsertList(name);
  refreshCategoryPicker(state.categories.filter((category) => category.deletedAt < category.updatedAt), name);
  commitMutation();
});
document.querySelector("#new-list").addEventListener("keydown", (event) => {
  if (event.key === "Enter") { event.preventDefault(); createList(); }
});

elements.taskForm.addEventListener("submit", (event) => {
  event.preventDefault();
  const createdAt = Date.now();
  const updatedAt = nextMutationTimestamp(state);
  const existing = state.tasks.find((task) => task.id === editingId);
  const task = normalizeTask({
    ...(existing ?? {}),
    id: existing?.id ?? crypto.randomUUID(),
    title: document.querySelector("#task-title").value,
    notes: document.querySelector("#task-notes").value,
    impact: new FormData(elements.taskForm).get("impact"),
    effort: new FormData(elements.taskForm).get("effort"),
    category: elements.category.value || null,
    urgent: document.querySelector("#task-urgent").checked,
    quickTask: document.querySelector("#task-quick").checked,
    snoozed: existing?.snoozed ?? false,
    completed: existing?.completed ?? false,
    createdAt: existing?.createdAt ?? createdAt,
    updatedAt
  });
  if (existing) state.tasks = state.tasks.map((value) => value.id === task.id ? task : value);
  else state.tasks.push(task);
  elements.taskDialog.close();
  commitMutation();
});

render();
syncNow();
setInterval(syncNow, 5000);
window.addEventListener("online", syncNow);

function render() {
  const visibleCategories = state.categories.filter((category) => category.deletedAt < category.updatedAt);
  if (activeList !== "All" && !visibleCategories.some((category) => category.name === activeList)) activeList = "All";
  elements.tabs.replaceChildren();
  for (const name of ["All", ...visibleCategories.map((category) => category.name)]) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = `list-tab${name === activeList ? " active" : ""}`;
    button.textContent = name;
    button.addEventListener("click", () => { activeList = name; render(); });
    elements.tabs.append(button);
  }

  const inList = (task) => activeList === "All" || task.category === activeList;
  const ordered = sortTasks(state.tasks.filter(inList));
  const active = ordered.filter((task) => !task.completed && !task.snoozed);
  const later = ordered.filter((task) => !task.completed && task.snoozed);
  const completed = ordered.filter((task) => task.completed);
  renderRows(elements.active, active, ordered);
  renderRows(elements.later, later, ordered);
  renderRows(elements.completed, completed, ordered);
  elements.empty.hidden = active.length !== 0;
  document.querySelector("#later-count").textContent = later.length ? String(later.length) : "";
  document.querySelector("#completed-count").textContent = completed.length ? String(completed.length) : "";
  document.querySelector("#later-section").hidden = later.length === 0;
  document.querySelector("#completed-section").hidden = completed.length === 0;

  const focus = document.querySelector("#focus-card");
  focus.hidden = active.length === 0;
  if (active[0]) {
    document.querySelector("#focus-title").textContent = active[0].title;
    const notes = document.querySelector("#focus-notes");
    notes.textContent = active[0].notes;
    notes.hidden = !active[0].notes;
  }
  refreshCategoryPicker(visibleCategories);
}

function renderRows(container, tasks, ordered) {
  container.replaceChildren();
  tasks.forEach((task) => {
    const row = elements.template.content.firstElementChild.cloneNode(true);
    row.style.setProperty("--priority", rankColor(ordered.indexOf(task), ordered.length));
    row.classList.toggle("completed", task.completed);
    row.querySelector(".task-title").textContent = task.title;
    const labels = [];
    if (task.category) labels.push(task.category);
    if (task.urgent) labels.push("Urgent");
    if (task.quickTask) labels.push("Quick win");
    row.querySelector(".task-meta").textContent = labels.join(" · ");
    row.querySelector(".task-meta").hidden = labels.length === 0;
    row.querySelector(".task-notes").textContent = task.notes;
    row.querySelector(".task-notes").hidden = !task.notes;
    row.querySelector(".complete-button").setAttribute("aria-label", task.completed ? "Mark incomplete" : "Complete task");
    row.querySelector(".complete-button").addEventListener("click", () => updateTask(task.id, {
      completed: !task.completed,
      snoozed: task.completed ? task.snoozed : false
    }));
    row.querySelector(".task-main").addEventListener("click", () => openTaskDialog(task));
    const later = row.querySelector(".later-button");
    later.textContent = task.snoozed ? "Now" : "Later";
    later.hidden = task.completed;
    later.addEventListener("click", () => updateTask(task.id, { snoozed: !task.snoozed }));
    row.querySelector(".delete-button").addEventListener("click", () => deleteTask(task.id));
    container.append(row);
  });
}

function openTaskDialog(task = null) {
  editingId = task?.id ?? null;
  elements.taskForm.reset();
  document.querySelector("#form-mode").textContent = task ? "EDIT TASK" : "NEW TASK";
  document.querySelector("#form-title").textContent = task ? "Make it clearer" : "What needs doing?";
  document.querySelector("#task-title").value = task?.title ?? "";
  document.querySelector("#task-notes").value = task?.notes ?? "";
  document.querySelector(`input[name="impact"][value="${task?.impact ?? "H"}"]`).checked = true;
  document.querySelector(`input[name="effort"][value="${task?.effort ?? "M"}"]`).checked = true;
  document.querySelector("#task-urgent").checked = task?.urgent ?? false;
  document.querySelector("#task-quick").checked = task?.quickTask ?? false;
  document.querySelector("#notes-field").hidden = !task?.notes;
  document.querySelector("#details-toggle").hidden = Boolean(task?.notes);
  refreshCategoryPicker(state.categories.filter((category) => category.deletedAt < category.updatedAt), task?.category ?? (activeList === "All" ? "" : activeList));
  elements.taskDialog.showModal();
  requestAnimationFrame(() => document.querySelector("#task-title").focus());
}

function refreshCategoryPicker(categories, selected = elements.category.value) {
  elements.category.replaceChildren(new Option("No list", ""));
  categories.forEach((category) => elements.category.add(new Option(category.name, category.name)));
  elements.category.add(new Option("+ New list…", "__new__"));
  elements.category.value = categories.some((category) => category.name === selected) ? selected : "";
}

function updateTask(id, changes) {
  const updatedAt = nextMutationTimestamp(state);
  state.tasks = state.tasks.map((task) => task.id === id ? { ...task, ...changes, updatedAt } : task);
  commitMutation();
}

function deleteTask(id) {
  const deletedAt = nextMutationTimestamp(state);
  state.tasks = state.tasks.filter((task) => task.id !== id);
  const existing = state.taskTombstones.find((item) => item.id === id);
  if (existing) existing.deletedAt = Math.max(existing.deletedAt, deletedAt);
  else state.taskTombstones.push({ id, deletedAt });
  commitMutation();
}

function openListsDialog() {
  renderListManager();
  elements.listsDialog.showModal();
}

function renderListManager() {
  elements.listManager.replaceChildren();
  const visible = state.categories.filter((category) => category.deletedAt < category.updatedAt);
  if (!visible.length) {
    const empty = document.createElement("p");
    empty.className = "empty-state";
    empty.textContent = "No lists yet.";
    elements.listManager.append(empty);
  }
  visible.forEach((category) => {
    const row = document.createElement("div");
    row.className = "list-manager-row";
    const name = document.createElement("span");
    name.textContent = category.name;
    const remove = document.createElement("button");
    remove.type = "button";
    remove.textContent = "×";
    remove.setAttribute("aria-label", `Delete ${category.name} list`);
    remove.addEventListener("click", () => deleteList(category.name));
    row.append(name, remove);
    elements.listManager.append(row);
  });
}

function createList() {
  const input = document.querySelector("#new-list");
  const name = input.value.trim();
  if (!name) return;
  upsertList(name);
  input.value = "";
  commitMutation();
  renderListManager();
}

function upsertList(name) {
  const updatedAt = nextMutationTimestamp(state);
  const existing = state.categories.find((category) => category.name.toLocaleLowerCase() === name.toLocaleLowerCase());
  if (existing) Object.assign(existing, { name, updatedAt, deletedAt: 0 });
  else state.categories.push({ name, updatedAt, deletedAt: 0 });
}

function deleteList(name) {
  const mutationTimestamp = nextMutationTimestamp(state);
  state.categories = state.categories.map((category) => category.name === name ? { ...category, deletedAt: mutationTimestamp } : category);
  state.tasks = state.tasks.map((task) => task.category === name ? { ...task, category: null, updatedAt: mutationTimestamp } : task);
  if (activeList === name) activeList = "All";
  commitMutation();
  renderListManager();
}

function commitMutation() {
  localRevision += 1;
  saveLocalState();
  render();
  clearTimeout(syncTimer);
  syncTimer = setTimeout(syncNow, 120);
}

async function syncNow() {
  clearTimeout(syncTimer);
  syncTimer = null;
  if (syncing) { syncQueued = true; return; }
  syncing = true;
  syncQueued = false;
  const requestedRevision = localRevision;
  setSyncStatus("Syncing…");
  try {
    const response = await postSyncState(state);
    if (!response.ok) throw new Error(`Sync failed (${response.status})`);
    const responseState = await response.json();
    if (shouldApplySyncResponse(requestedRevision, localRevision)) {
      if (!hasSameSyncState(state, responseState)) {
        state = responseState;
        saveLocalState();
        render();
      }
      setSyncStatus("Synced");
    } else {
      syncQueued = true;
    }
  } catch (error) {
    setSyncStatus(navigator.onLine ? "Sync unavailable" : "Offline", true);
    console.warn(error);
  } finally {
    syncing = false;
    if (syncQueued) syncNow();
  }
}

function loadLocalState() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (Array.isArray(parsed.tasks) && Array.isArray(parsed.taskTombstones) && Array.isArray(parsed.categories)) return parsed;
  } catch {}
  return { tasks: [], taskTombstones: [], categories: [] };
}

function saveLocalState() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function setSyncStatus(message, isError = false) {
  elements.syncStatus.textContent = message;
  elements.syncStatus.classList.toggle("error", isError);
}

function applyTheme(theme) {
  if (theme === "auto") document.documentElement.removeAttribute("data-theme");
  else document.documentElement.dataset.theme = theme;
}

function rankColor(index, count) {
  if (count <= 1) return SPECTRUM[0];
  const spectrumIndex = Math.round((index / (count - 1)) * (SPECTRUM.length - 1));
  return SPECTRUM[spectrumIndex];
}
