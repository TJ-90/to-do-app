import { createReadStream } from "node:fs";
import { stat } from "node:fs/promises";
import http from "node:http";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { SyncStore } from "./lib/store.js";

const WEB_ROOT = path.dirname(fileURLToPath(import.meta.url));
const DEFAULT_PUBLIC_DIR = path.join(WEB_ROOT, "public");
const DEFAULT_DATA_FILE = path.join(WEB_ROOT, ".data", "sync-state.json");
const BODY_LIMIT = 2 * 1024 * 1024;

export function createAppServer(options = {}) {
  const publicDir = path.resolve(options.publicDir ?? DEFAULT_PUBLIC_DIR);
  const store = options.store ?? new SyncStore(options.dataFile ?? DEFAULT_DATA_FILE);

  return http.createServer(async (request, response) => {
    try {
      const url = new URL(request.url, "http://localhost");
      if (url.pathname === "/api/health" && request.method === "GET") {
        return json(response, 200, { ok: true });
      }
      if (url.pathname === "/api/state" && request.method === "GET") {
        return json(response, 200, await store.read());
      }
      if (url.pathname === "/api/sync" && request.method === "POST") {
        if (!String(request.headers["content-type"] ?? "").toLowerCase().startsWith("application/json")) {
          return json(response, 415, { error: "Content-Type must be application/json" });
        }
        const body = await readJson(request);
        return json(response, 200, await store.sync(body));
      }
      if (url.pathname.startsWith("/api/")) return json(response, 404, { error: "Not found" });
      if (request.method !== "GET" && request.method !== "HEAD") return json(response, 405, { error: "Method not allowed" });
      await serveStatic(response, request.method, url.pathname, publicDir);
    } catch (error) {
      if (error instanceof ClientError || error instanceof SyntaxError || error instanceof TypeError) {
        return json(response, error.statusCode ?? 400, { error: error.message || "Invalid request" });
      }
      console.error(error);
      if (!response.headersSent) json(response, 500, { error: "Internal server error" });
      else response.destroy();
    }
  });
}

async function readJson(request) {
  const chunks = [];
  let size = 0;
  for await (const chunk of request) {
    size += chunk.length;
    if (size > BODY_LIMIT) throw new ClientError("Request body is too large", 413);
    chunks.push(chunk);
  }
  if (size === 0) throw new ClientError("Request body is required", 400);
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

async function serveStatic(response, method, pathname, publicDir) {
  let decoded;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    throw new ClientError("Invalid URL", 400);
  }
  const requested = decoded === "/" ? "index.html" : decoded.replace(/^\/+/, "");
  const filePath = path.resolve(publicDir, requested);
  if (filePath !== publicDir && !filePath.startsWith(`${publicDir}${path.sep}`)) {
    throw new ClientError("Not found", 404);
  }
  try {
    const details = await stat(filePath);
    if (!details.isFile()) throw new Error("Not a file");
  } catch {
    return json(response, 404, { error: "Not found" });
  }
  response.writeHead(200, {
    "content-type": mimeType(filePath),
    "cache-control": "no-cache",
    "x-content-type-options": "nosniff"
  });
  if (method === "HEAD") return response.end();
  createReadStream(filePath).pipe(response);
}

function json(response, statusCode, value) {
  const body = JSON.stringify(value);
  response.writeHead(statusCode, {
    "content-type": "application/json; charset=utf-8",
    "content-length": Buffer.byteLength(body),
    "cache-control": "no-store",
    "x-content-type-options": "nosniff"
  });
  response.end(body);
}

function mimeType(filePath) {
  return ({
    ".css": "text/css; charset=utf-8",
    ".html": "text/html; charset=utf-8",
    ".js": "text/javascript; charset=utf-8",
    ".json": "application/json; charset=utf-8",
    ".svg": "image/svg+xml"
  })[path.extname(filePath)] ?? "application/octet-stream";
}

class ClientError extends Error {
  constructor(message, statusCode) {
    super(message);
    this.statusCode = statusCode;
  }
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const host = process.env.HOST || "0.0.0.0";
  const port = Number.parseInt(process.env.PORT || "8787", 10);
  const server = createAppServer();
  server.listen(port, host, () => console.log(`Priority Todo web running at http://${host}:${port}`));
}
