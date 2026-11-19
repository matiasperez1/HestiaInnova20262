import type { IncomingMessage, ServerResponse } from "node:http";
import { body } from "../http.js";
import { AppError } from "../types.js";
import { validId } from "../time.js";
import { DemoStore } from "./store.js";
import { DemoCallReports } from "./call-reports.js";
import { demoLanInterface, permitsDemoClient } from "./listen-policy.js";
export function demoHandler(db: DemoStore, callReportThreshold: number, lan?: ReturnType<typeof demoLanInterface>) {
  const calls = new DemoCallReports(db, callReportThreshold);
  return async (req: IncomingMessage, res: ServerResponse) => {
    const send = (status: number, value: unknown) => {
      res.writeHead(status, {
        "Content-Type": "application/json",
        "Cache-Control": "no-store",
        "X-Hestia-Mode": "LOCAL_DEMO",
      });
      res.end(JSON.stringify(value));
    };
    try {
      if (
        req.headers.origin !== undefined ||
        req.headers["x-hestia-demo"] !== "true" ||
        !permitsDemoClient(req.socket.remoteAddress ?? "", req.headers.host ?? "", req.socket.localPort ?? 0, lan)
      )
        throw new AppError("DEMO_LOCAL_CLIENT_REQUIRED", 403);
      const url = new URL(req.url ?? "/", "http://localhost");
      const path = url.pathname;
      if (path === "/demo/health" && req.method === "GET") {
        await db.tx((client) => client.query("SELECT 1"));
        send(200, { mode: "LOCAL_DEMO", database: "ready", transport: lan ? "lan" : "loopback" });
        return;
      }
      if (path === "/demo/session" && req.method === "POST") {
        const b = await body(req);
        if (typeof b.persona !== "string")
          throw new AppError("INVALID_DEMO_PERSONA", 400);
        send(200, await db.session(b.persona));
        return;
      }
      const user = await db.authenticate(req.headers.authorization);
      if (path === "/demo/calls/reputation" && req.method === "GET") {
        send(200, await calls.snapshot(user.id));
        return;
      }
      if (path === "/demo/calls/report" && req.method === "POST") {
        await calls.save(user.id, await body(req));
        send(200, { saved: true });
        return;
      }
      if (path === "/demo/calls/withdraw" && req.method === "POST") {
        await calls.withdraw(user.id, (await body(req))?.number);
        send(200, { withdrawn: true });
        return;
      }
      if (path === "/v1/gmail/status" && req.method === "GET") {
        const c = await db.find(user.id);
        send(200, {
          status: "DEMO_LOCAL",
          automaticActive: false,
          lastSync: c?.lastSync?.toISOString(),
          watchExpiration: null,
          monitoringStartedAt: c?.startedAt?.toISOString(),
          mode: "LOCAL_DEMO",
        });
        return;
      }
      if (path === "/demo/notifications" && req.method === "GET") {
        const value = url.searchParams.get("limit") ?? "50";
        if (!/^(?:[1-9]|[1-4][0-9]|50)$/.test(value)) throw new AppError("INVALID_LIMIT", 400);
        send(200, await db.notifications(user.id, url.searchParams.get("after"), Number(value)));
        return;
      }
      if (path === "/demo/status" && req.method === "GET") {
        send(200, await db.info(user.id));
        return;
      }
      if (path === "/v1/gmail/summary" && req.method === "GET") {
        send(
          200,
          await db.summary(user.id, url.searchParams.get("timeZone") ?? "UTC"),
        );
        return;
      }
      if (path === "/v1/gmail/alerts" && req.method === "GET") {
        send(
          200,
          await db.alerts(user.id, url.searchParams.get("before") ?? undefined),
        );
        return;
      }
      if (path.startsWith("/v1/gmail/alerts/") && req.method === "GET") {
        const id = path.split("/").at(-1)!;
        if (!validId(id)) throw new AppError("INVALID_REQUEST", 400);
        const item = await db.event(user.id, id);
        if (!item) throw new AppError("NOT_FOUND", 404);
        send(200, item);
        return;
      }
      if (path === "/demo/emit" && req.method === "POST") {
        const b = await body(req);
        send(202, await db.emit(user, b.scenario, b.retryOnce === true));
        return;
      }
      if (path === "/demo/repeat" && req.method === "POST") {
        send(202, await db.repeat(user.id));
        return;
      }
      if (path === "/demo/clear" && req.method === "POST") {
        const b = await body(req);
        if (b.confirm !== true)
          throw new AppError("DEMO_CONFIRM_REQUIRED", 400);
        await db.clearDemo(user);
        send(200, { mode: "LOCAL_DEMO", cleared: true });
        return;
      }
      throw new AppError("DEMO_ROUTE_DISABLED", 404);
    } catch (e) {
      send(e instanceof AppError ? e.status : 503, {
        error: e instanceof AppError ? e.code : "DEMO_UNAVAILABLE",
        mode: "LOCAL_DEMO",
      });
    }
  };
}
