import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import net from "node:net";
import pg from "pg";
import { config } from "../src/config.js";
import { demoConfig } from "../src/demo/config.js";
import { DemoStore, personas } from "../src/demo/store.js";
import { demoHandler } from "../src/demo/http.js";
import { workOnce } from "../src/demo/worker.js";
import { LocalDemoMailbox, noDemoCipher } from "../src/demo/adapters.js";
import { installLocalNetworkGuard } from "../src/demo/network-guard.js";
test("LOCAL_DEMO requires explicit opt-in and is rejected by production entrypoints", () => {
  assert.throws(() => demoConfig({}), {
    code: "LOCAL_DEMO_EXPLICIT_OPT_IN_REQUIRED",
  });
  for (const extra of [
    { NODE_ENV: "production" },
    { K_SERVICE: "service" },
    { CLOUD_RUN_JOB: "job" },
  ])
    assert.throws(() => demoConfig({ LOCAL_DEMO: "true", ...extra }), {
      code: "DEMO_FORBIDDEN_IN_PRODUCTION",
    });
  assert.throws(() => config({ LOCAL_DEMO: "true" }), {
    code: "DEMO_FORBIDDEN_IN_PRODUCTION_ENTRYPOINT",
  });
});
test("local network guard refuses external fetch and sockets before connecting", async () => {
  const restore = installLocalNetworkGuard();
  try {
    await assert.rejects(
      fetch("https://gmail.googleapis.com/"),
      /DEMO_EXTERNAL_NETWORK_BLOCKED/,
    );
    assert.throws(
      () => new net.Socket().connect(443, "googleapis.com"),
      /DEMO_EXTERNAL_NETWORK_BLOCKED/,
    );
  } finally {
    restore();
  }
});
test("demo never accepts OAuth/refresh credentials or calls KMS", async () => {
  const mailbox = new LocalDemoMailbox({} as DemoStore);
  await assert.rejects(mailbox.exchange(), { code: "OAUTH_DISABLED_IN_DEMO" });
  await assert.rejects(mailbox.refresh(), { code: "REFRESH_DISABLED_IN_DEMO" });
  await assert.rejects(noDemoCipher.decrypt("owner", "anything"), {
    code: "CIPHER_NOT_NEEDED_IN_DEMO",
  });
});
test(
  "LOCAL_DEMO complete local HTTP/queue/MIME/engine/PostgreSQL/notification flow",
  { skip: process.env.LOCAL_DEMO !== "true" },
  async (t) => {
    const cfg = demoConfig();
    const name = "hestia_demo_test_" + process.pid;
    const admin = new pg.Pool({
      connectionString: `postgresql://postgres@127.0.0.1:${cfg.databasePort}/postgres`,
    });
    const location = (
      await admin.query("SHOW data_directory")
    ).rows[0].data_directory
      .replaceAll("\\", "/")
      .toLowerCase();
    assert.equal(
      location,
      (cfg.dataDir + "/postgres").replaceAll("\\", "/").toLowerCase(),
    );
    await admin.query(`CREATE DATABASE "${name}"`);
    let pool = new pg.Pool({
      connectionString: `postgresql://postgres@127.0.0.1:${cfg.databasePort}/${name}`,
    });
    let db = new DemoStore(pool);
    let server: ReturnType<typeof createServer> | undefined;
    const restore = installLocalNetworkGuard();
    try {
      const sql = (path: string) =>
        readFileSync(new URL(path, import.meta.url), "utf8").replace(
          /^\uFEFF/,
          "",
        );
      await pool.query(
        "BEGIN;" +
          sql("../../demo/bootstrap.sql") +
          sql("../../../supabase/migrations/202611160001_gmail_automatic.sql")
            .replace(/^BEGIN;$/m, "")
            .replace(/^COMMIT;$/m, "") +
          sql("../../demo/schema.sql") + sql("../../demo/call-reports.sql") +
          "COMMIT;",
      );
      for (const u of Object.values(personas)) await db.activate(u);
      server = createServer(demoHandler(db, cfg.callReportThreshold));
      await new Promise<void>((r) => server!.listen(0, "127.0.0.1", r));
      let base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
      async function request(path: string, body?: unknown, token?: string) {
        const response = await fetch(base + path, {
          method: body === undefined ? "GET" : "POST",
          headers: {
            "X-Hestia-Demo": "true",
            ...(token ? { Authorization: "Bearer " + token } : {}),
          },
          body: body === undefined ? undefined : JSON.stringify(body),
        });
        return {
          status: response.status,
          json: (await response.json()) as any,
        };
      }
      const a = (await request("/demo/session", { persona: "alice" })).json;
      const b = (await request("/demo/session", { persona: "bob" })).json;
      await t.test("call reports share counts but only the author can read or edit their report", async () => {
        const rep = "/demo/calls/reputation";
        const save = "/demo/calls/report";
        const withdraw = "/demo/calls/withdraw";
        const number = "2025550199";
        const category = "IMPERSONATION";
        const report = { number, category, consent: true };
        const lookup = (token: string) => request(rep, undefined, token);
        assert.equal((await request(rep)).status, 401);
        assert.deepEqual((await lookup(a.token)).json, { threshold: cfg.callReportThreshold, entries: [] });
        assert.equal((await request(save, { ...report, consent: false }, a.token)).status, 400);
        for (const invalid of ["2025550200", "+56912345678", "private"])
          assert.equal((await request(save, { ...report, number: invalid }, a.token)).status, 400);
        assert.equal((await request(save, { ...report, category: "free text" }, a.token)).status, 400);
        const duplicate = await Promise.all([request(save, report, a.token), request(save, report, a.token)]);
        assert.deepEqual(duplicate.map((item) => item.status), [200, 200]);
        assert.deepEqual((await lookup(b.token)).json.entries, [{ number, count: 1, fraudCount: 1, categoryCounts: { IMPERSONATION: 1 }, ownCategory: null }]);
        assert.equal((await request(withdraw, { number, userId: a.userId }, b.token)).status, 200);
        assert.equal((await lookup(a.token)).json.entries[0].count, 1);
        assert.equal((await request(save, { ...report, category: "UNWANTED_ADS" }, b.token)).status, 200);
        assert.deepEqual((await lookup(a.token)).json.entries, [{ number, count: 2, fraudCount: 1, categoryCounts: { IMPERSONATION: 1, UNWANTED_ADS: 1 }, ownCategory: category }]);
        assert.equal((await request(save, { ...report, category: "HARASSMENT" }, a.token)).status, 409);
        assert.equal((await request(save, { ...report, category: "HARASSMENT", replace: true }, a.token)).status, 200);
        assert.deepEqual((await lookup(a.token)).json.entries, [{ number, count: 2, fraudCount: 0, categoryCounts: { HARASSMENT: 1, UNWANTED_ADS: 1 }, ownCategory: "HARASSMENT" }]);
        assert.equal((await lookup(b.token)).json.entries[0].ownCategory, "UNWANTED_ADS");
        assert.equal((await request(save, { ...report, category: "POSSIBLE_FRAUD", replace: true }, a.token)).status, 200);
        assert.equal((await request(save, { ...report, category: "IMPERSONATION", replace: true }, b.token)).status, 200);
        assert.equal((await lookup(a.token)).json.entries[0].fraudCount, 2);
        assert.equal((await request(withdraw, { number }, b.token)).status, 200);
        assert.equal((await lookup(b.token)).json.entries[0].fraudCount, 1);
        assert.equal((await lookup(b.token)).json.entries[0].ownCategory, null);
        assert.equal((await request(withdraw, { number }, a.token)).status, 200);
        assert.deepEqual((await lookup(b.token)).json.entries, []);
        for (const number of ["2025550147", "2025550148"]) {
          assert.equal((await request(save, { ...report, number, category: "OTHER_GENERAL" }, a.token)).status, 200);
          assert.equal((await lookup(a.token)).json.entries[0].fraudCount, 0);
          await request(withdraw, { number }, a.token);
        }
        const legacy = "2025550160";
        assert.equal((await request(save, { ...report, number: legacy, category: "REQUEST_FOR_CODES" }, a.token)).status, 200);
        await pool.query(sql("../../demo/call-reports.sql"));
        assert.deepEqual((await lookup(a.token)).json.entries, [{
          number: legacy, count: 1, fraudCount: 1, categoryCounts: { REQUEST_FOR_CODES: 1 }, ownCategory: "REQUEST_FOR_CODES",
        }]);
        await request(withdraw, { number: legacy }, a.token);
      });
      await t.test(
        "explicit demo sessions use fixed server identities, not arbitrary user ids",
        async () => {
          assert.equal(a.userId, personas.alice.id);
          assert.equal(
            (
              await request("/demo/session", {
                persona: "arbitrary",
                userId: personas.alice.id,
              })
            ).status,
            400,
          );
          assert.equal((await request("/demo/status")).status, 401);
          const publicCall = await fetch(base + "/demo/status");
          assert.equal(publicCall.status, 403);
        },
      );
      await t.test(
        "all eight MIME scenarios use real engine; only HIGH sends simulated notices",
        async () => {
          for (const scenario of [
            "legitimate",
            "high-risk",
            "account-block",
            "shortened-link",
            "reply-mismatch",
            "urgent-payment",
            "isolated-signal",
            "limited-content",
          ]) {
            assert.equal(
              (await request("/demo/emit", { scenario }, a.token)).status,
              202,
            );
            assert.equal(await workOnce(db), true);
          }
          const summary = (
            await request("/v1/gmail/summary?timeZone=UTC", undefined, a.token)
          ).json;
          assert.equal(summary.analyzed, 8);
          assert.equal(summary.suspicious, 5);
          const info = (await request("/demo/status", undefined, a.token)).json;
          assert.equal(info.processed, 8);
          assert.equal(info.notificationCount, 2);
          assert.equal(info.workerRunning, true);
          const lows = await pool.query(
            "SELECT * FROM public.gmail_events WHERE risk_level='LOW'",
          );
          for (const e of lows.rows) {
            assert.equal(e.sender, null);
            assert.equal(e.subject, null);
            assert.equal(e.score, null);
            assert.deepEqual(e.reasons, []);
          }
          assert.ok(lows.rows.some((e) => e.content_limited));
        },
      );
      await t.test(
        "repeated HIGH event has one result and one notification",
        async () => {
          await request("/demo/emit", { scenario: "high-risk" }, a.token);
          await workOnce(db);
          const before = await db.summary(personas.alice.id, "UTC");
          const notices = (await db.info(personas.alice.id)).notificationCount;
          await request("/demo/repeat", {}, a.token);
          await workOnce(db);
          assert.deepEqual(await db.summary(personas.alice.id, "UTC"), before);
          assert.equal(
            (await db.info(personas.alice.id)).notificationCount,
            notices,
          );
          assert.equal((await db.info(personas.alice.id)).duplicates, 1);
        },
      );
      await t.test(
        "processing error retries without advancing cursor or losing messages",
        async () => {
          const cursor = (await db.find(personas.alice.id))!.historyId;
          await request(
            "/demo/emit",
            { scenario: "high-risk", retryOnce: true },
            a.token,
          );
          await workOnce(db);
          assert.equal((await db.find(personas.alice.id))!.historyId, cursor);
          assert.equal((await db.info(personas.alice.id)).pending, 1);
          await pool.query(
            "UPDATE demo.queue SET available_at=now() WHERE status='PENDING'",
          );
          await workOnce(db);
          assert.notEqual(
            (await db.find(personas.alice.id))!.historyId,
            cursor,
          );
          assert.equal((await db.info(personas.alice.id)).pending, 0);
          assert.equal((await db.info(personas.alice.id)).retries, 1);
        },
      );
      await t.test(
        "pending delivery and stored data survive API/worker restart",
        async () => {
          await request("/demo/emit", { scenario: "urgent-payment" }, a.token);
          const claimed = await db.claim();
          assert.ok(claimed);
          await new Promise<void>((r) => server!.close(() => r()));
          await pool.end();
          pool = new pg.Pool({
            connectionString: `postgresql://postgres@127.0.0.1:${cfg.databasePort}/${name}`,
          });
          db = new DemoStore(pool);
          await pool.query(
            "UPDATE demo.queue SET lease_until=now()-interval '1 second' WHERE status='PROCESSING'",
          );
          await workOnce(db);
          server = createServer(demoHandler(db, cfg.callReportThreshold));
          await new Promise<void>((r) => server!.listen(0, "127.0.0.1", r));
          base = `http://127.0.0.1:${(server.address() as AddressInfo).port}`;
          assert.equal(
            (await request("/demo/status", undefined, a.token)).json.pending,
            0,
          );
          assert.equal(
            (await db.summary(personas.alice.id, "UTC")).analyzed,
            11,
          );
        },
      );
      await t.test(
        "owners remain separate and cleanup deletes only selected synthetic records",
        async () => {
          const alerts = (await db.alerts(personas.alice.id, undefined)).items;
          assert.ok(alerts.length);
          assert.equal(
            (
              await request(
                "/v1/gmail/alerts/" + alerts[0].id,
                undefined,
                b.token,
              )
            ).status,
            404,
          );
          assert.equal((await db.summary(personas.bob.id, "UTC")).analyzed, 0);
          await request(
            "/demo/emit",
            { scenario: "legitimate", userId: personas.alice.id },
            b.token,
          );
          await workOnce(db);
          assert.equal((await db.summary(personas.bob.id, "UTC")).analyzed, 1);
          assert.equal((await request("/demo/clear", {}, a.token)).status, 400);
          assert.equal(
            (await request("/demo/clear", { confirm: true }, a.token)).status,
            200,
          );
          assert.equal(
            (await db.summary(personas.alice.id, "UTC")).analyzed,
            0,
          );
          assert.equal((await db.summary(personas.bob.id, "UTC")).analyzed, 1);
          assert.equal((await db.info(personas.alice.id)).notificationCount, 0);
          const credentials = await pool.query(
            "SELECT encrypted_refresh_token,token_created_at FROM private.gmail_connections",
          );
          assert.ok(
            credentials.rows.every(
              (r) =>
                r.encrypted_refresh_token === null &&
                r.token_created_at === null,
            ),
          );
        },
      );
      await t.test("incremental pages resume 125 equal-time events and isolate owners", async () => {
        const initial = (await request("/demo/notifications", undefined, a.token)).json;
        const inserted = await pool.query(`INSERT INTO public.gmail_events(user_id,message_id_hash,received_at,risk_level) SELECT $1,md5('cursor-test-'||g)||md5('cursor-test-'||g),now(),'HIGH' FROM generate_series(1,125) g RETURNING id`, [personas.alice.id]);
        await pool.query(`INSERT INTO demo.notification(event_id,user_id,created_at) SELECT unnest($1::uuid[]),$2,'2026-01-01T00:00:00Z'::timestamptz`, [inserted.rows.map(r => r.id),personas.alice.id]);
        const got:string[]=[]; let cursor=initial.nextCursor; let more=true; let pages=0;
        while(more) {
          const response=(await request(`/demo/notifications?limit=50&after=${cursor}`,undefined,a.token)).json;
          assert.equal(response.identity,personas.alice.id); pages++;
          for(const item of response.items) {got.push(item.event_id);cursor=item.cursor;}
          more=response.hasMore;
          if(pages===1) assert.equal((await request(`/demo/notifications?limit=50&after=${cursor}`,undefined,a.token)).json.items.length,50);
        }
        assert.equal(pages,3);assert.equal(got.length,125);assert.equal(new Set(got).size,125);
        const expected=await pool.query("SELECT event_id FROM demo.notification WHERE user_id=$1 ORDER BY delivery_seq",[personas.alice.id]);
        assert.deepEqual(got,expected.rows.map(r=>r.event_id));
        assert.equal((await request(`/demo/notifications?after=${cursor}`,undefined,b.token)).status,400);
        assert.equal((await request(`/demo/notifications?after=${cursor}`,undefined,a.token)).json.items.length,0);
      });
    } finally {
      restore();
      if (server?.listening)
        await new Promise<void>((r) => server!.close(() => r()));
      await pool.end();
      await admin.query(`DROP DATABASE "${name}" WITH (FORCE)`);
      await admin.end();
    }
  },
);
