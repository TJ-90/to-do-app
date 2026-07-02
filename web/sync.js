const PTDO_SCHEMA = 1;
const TOMBSTONE_TTL_MS = 30 * 24 * 60 * 60 * 1000;

let ptdoToken = null;
let ptdoTokenClient = null;

function ptdoInitAuth(onReady) {
  ptdoTokenClient = google.accounts.oauth2.initTokenClient({
    client_id: window.PTDO_CONFIG.CLIENT_ID,
    scope: window.PTDO_CONFIG.SCOPE,
    callback: (resp) => {
      if (resp.error) {
        onReady(new Error(resp.error));
        return;
      }
      ptdoToken = resp.access_token;
      onReady(null);
    }
  });
}

function ptdoRequestToken() {
  ptdoTokenClient.requestAccessToken({ prompt: ptdoToken ? "" : "consent" });
}

function ptdoRevoke() {
  if (ptdoToken) {
    google.accounts.oauth2.revoke(ptdoToken, () => {});
    ptdoToken = null;
  }
}

function ptdoAuthHeaders(extra) {
  return Object.assign({ Authorization: "Bearer " + ptdoToken }, extra || {});
}

async function ptdoFindFileId() {
  const q = encodeURIComponent(
    "name='" + window.PTDO_CONFIG.FILE_NAME + "' and trashed=false"
  );
  const url =
    "https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&fields=files(id,name)&q=" +
    q;
  const res = await fetch(url, { headers: ptdoAuthHeaders() });
  if (!res.ok) throw new Error("Drive list " + res.status);
  const data = await res.json();
  return data.files && data.files.length ? data.files[0].id : null;
}

async function ptdoDownload(fileId) {
  const url =
    "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";
  const res = await fetch(url, { headers: ptdoAuthHeaders() });
  if (!res.ok) throw new Error("Drive get " + res.status);
  return res.json();
}

async function ptdoCreate(payload) {
  const boundary = "ptdo_boundary_7f3a";
  const meta = JSON.stringify({
    name: window.PTDO_CONFIG.FILE_NAME,
    parents: ["appDataFolder"]
  });
  const body =
    "--" + boundary + "\r\n" +
    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
    meta + "\r\n" +
    "--" + boundary + "\r\n" +
    "Content-Type: application/json; charset=UTF-8\r\n\r\n" +
    JSON.stringify(payload) + "\r\n" +
    "--" + boundary + "--";
  const res = await fetch(
    "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
    {
      method: "POST",
      headers: ptdoAuthHeaders({
        "Content-Type": "multipart/related; boundary=" + boundary
      }),
      body
    }
  );
  if (!res.ok) throw new Error("Drive create " + res.status);
}

async function ptdoUpdate(fileId, payload) {
  const res = await fetch(
    "https://www.googleapis.com/upload/drive/v3/files/" +
      fileId +
      "?uploadType=media",
    {
      method: "PATCH",
      headers: ptdoAuthHeaders({ "Content-Type": "application/json; charset=UTF-8" }),
      body: JSON.stringify(payload)
    }
  );
  if (!res.ok) throw new Error("Drive update " + res.status);
}

function ptdoMerge(local, remote) {
  const byId = new Map();
  local.forEach((t) => byId.set(t.id, t));
  remote.forEach((t) => {
    const cur = byId.get(t.id);
    if (!cur || t.updatedAt >= cur.updatedAt) byId.set(t.id, t);
  });
  const cutoff = Date.now() - TOMBSTONE_TTL_MS;
  const merged = [];
  byId.forEach((t) => {
    if (t.deleted && t.updatedAt < cutoff) return;
    merged.push(t);
  });
  return merged;
}

// local = full task list INCLUDING tombstones. Returns merged full list.
async function ptdoSync(local) {
  const fileId = await ptdoFindFileId();
  let merged;
  if (!fileId) {
    merged = local;
    await ptdoCreate(ptdoWrap(merged));
  } else {
    const remote = await ptdoDownload(fileId);
    const remoteTasks = Array.isArray(remote.tasks) ? remote.tasks.map(ptdoSanitize) : [];
    merged = ptdoMerge(local, remoteTasks);
    await ptdoUpdate(fileId, ptdoWrap(merged));
  }
  return merged;
}

function ptdoWrap(tasks) {
  return { schema: PTDO_SCHEMA, updatedAt: Date.now(), tasks };
}
