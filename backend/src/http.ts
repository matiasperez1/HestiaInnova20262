import {
  createServer,
  type IncomingMessage,
  type ServerResponse,
} from "node:http";
import { randomUUID } from "node:crypto";
import { AppError } from "./types.js";
import { validId, historyId, weekBounds } from "./time.js";
import type { GmailService } from "./service.js";
import type { PgStore } from "./store.js";
import type { IdentityVerifier } from "./adapters.js";
import type { Config } from "./config.js";
export async function body(req: IncomingMessage): Promise<any> {
  let bytes = 0;
  const chunks: Buffer[] = [];
  for await (const chunk of req) {
    bytes += chunk.length;
    if (bytes > 16384) throw new AppError("BODY_TOO_LARGE", 413);
    chunks.push(chunk);
  }
  try {
    return JSON.parse(Buffer.concat(chunks).toString("utf8"));
  } catch {
    throw new AppError("INVALID_JSON", 400);
  }
}
function string(v: unknown, max: number) {
  if (typeof v !== "string" || !v.trim() || v.length > max)
    throw new AppError("INVALID_REQUEST", 400);
  return v;
}
function send(res: ServerResponse, status: number, value: unknown) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store",
    "X-Content-Type-Options": "nosniff",
  });
  res.end(JSON.stringify(value));
}
export function handler(
  kind: "api" | "worker",
  cfg: Config,
  service: GmailService,
  db: PgStore,
  identity: IdentityVerifier,
) {
  return async (req: IncomingMessage, res: ServerResponse) => {
    const requestId = randomUUID();
    try {
      const url = new URL(req.url ?? "/", "https://localhost");
      const path = url.pathname;
      if (req.method === "GET" && path === "/health") {
        send(res, 200, { ok: true });
        return;
      }
      if (kind === "worker") {
        if (req.method !== "POST") throw new AppError("NOT_FOUND", 404);
        if (path === "/webhooks/pubsub/gmail") {
          await identity.oidc(req.headers.authorization, "pubsub");
          const b = await body(req);
          if (
            b.subscription !== cfg.subscription ||
            typeof b.message?.data !== "string" ||
            b.message.data.length > 8192 ||
            !/^[A-Za-z0-9+/=_-]+$/.test(b.message.data)
          )
            throw new AppError("INVALID_PUSH", 400);
          let d: any;
          try {
            d = JSON.parse(
              Buffer.from(b.message.data, "base64").toString("utf8"),
            );
          } catch {
            throw new AppError("INVALID_PUSH", 400);
          }
          await service.processEmail(
            string(d.emailAddress, 254),
            historyId(string(d.historyId, 30)),
            b.message.attributes?.hestiaManual === "true",
          );
          send(res, 200, { ok: true });
          return;
        }
        if (
          [
            "/internal/gmail/renew-watches",
            "/internal/gmail/reconcile",
          ].includes(path)
        ) {
          await identity.oidc(req.headers.authorization, "scheduler");
          await service.scheduled(
            path.endsWith("renew-watches") ? "renew" : "reconcile",
          );
          send(res, 200, { ok: true });
          return;
        }
        throw new AppError("NOT_FOUND", 404);
      }
      if (!path.startsWith("/v1/")) throw new AppError("NOT_FOUND", 404);
      const user = await identity.user(req.headers.authorization);
      if (path === "/v1/gmail/connect" && req.method === "POST") {
        const b = await body(req);
        const zone = string(b.timeZone, 80);
        weekBounds(zone, new Date());
        send(
          res,
          200,
          status(
            await service.connect(user, string(b.serverAuthCode, 4096), zone),
          ),
        );
        return;
      }
      if (path === "/v1/gmail/status" && req.method === "GET") {
        send(res, 200, status(await db.find(user.id)));
        return;
      }
      if (path === "/v1/gmail/sync" && req.method === "POST") {
        await db.throttle(user.id);
        await service.requestSync(user.id);
        send(res, 202, { queued: true });
        return;
      }
      if (path === "/v1/gmail/disconnect" && req.method === "POST") {
        const b = await body(req);
        if (typeof b.deleteEvents !== "boolean")
          throw new AppError("INVALID_REQUEST", 400);
        send(res, 200, await service.disconnect(user.id, b.deleteEvents));
        return;
      }
      if (path === "/v1/gmail/summary" && req.method === "GET") {
        send(
          res,
          200,
          await db.summary(
            user.id,
            string(url.searchParams.get("timeZone"), 80),
          ),
        );
        return;
      }
      if (path === "/v1/gmail/alerts" && req.method === "GET") {
        const b = url.searchParams.get("before") ?? undefined;
        if (b && !/^[A-Za-z0-9_-]{1,256}$/.test(b))
          throw new AppError("INVALID_REQUEST", 400);
        send(res, 200, await db.alerts(user.id, b));
        return;
      }
      if (path.startsWith("/v1/gmail/alerts/") && req.method === "GET") {
        const id = path.split("/").at(-1)!;
        if (!validId(id)) throw new AppError("INVALID_REQUEST", 400);
        const event = await db.event(user.id, id);
        if (!event) throw new AppError("NOT_FOUND", 404);
        send(res, 200, event);
        return;
      }
      if (
        path === "/v1/devices/fcm-token" &&
        ["POST", "DELETE"].includes(req.method ?? "")
      ) {
        const b = await body(req);
        if (!validId(b.installationId))
          throw new AppError("INVALID_REQUEST", 400);
        if (req.method === "POST") {
          const token = string(b.token, 4096);
          if (token.length < 20 || typeof b.notificationsEnabled !== "boolean")
            throw new AppError("INVALID_REQUEST", 400);
          const c = await db.find(user.id);
          if (!c || !["ACTIVE", "TEMP_ERROR"].includes(c.status))
            throw new AppError("GMAIL_NOT_ACTIVE", 409);
          await db.device(
            user.id,
            b.installationId,
            token,
            b.notificationsEnabled,
          );
        } else await db.removeDevice(user.id, b.installationId);
        send(res, 200, { ok: true });
        return;
      }
      throw new AppError("NOT_FOUND", 404);
    } catch (e) {
      const known = e instanceof AppError;
      const code = known ? e.code : "TEMPORARY_ERROR";
      const statusCode = known ? e.status : 503;
      console.error(JSON.stringify({ requestId, code, status: statusCode }));
      send(res, statusCode, { error: code, requestId });
    }
  };
}
export function status(c: any) {
  return {
    status: c?.status ?? "DISCONNECTED",
    email: c?.email ?? null,
    watchExpiration: c?.expiration?.toISOString() ?? null,
    lastSync: c?.lastSync?.toISOString() ?? null,
    monitoringStartedAt: c?.startedAt?.toISOString() ?? null,
    lastError: c?.lastError ?? null,
    automaticActive:
      c?.status === "ACTIVE" &&
      !!c?.ciphertext &&
      !!c?.historyId &&
      c?.expiration > new Date(),
  };
}
export function serve(port: number, listener: ReturnType<typeof handler>) {
  const s = createServer(listener);
  s.requestTimeout = 540_000;
  s.headersTimeout = 15_000;
  s.listen(port);
  return s;
}
