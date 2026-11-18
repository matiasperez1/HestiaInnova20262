import test from "node:test";
import assert from "node:assert/strict";
import pg from "pg";
import { PgStore } from "../src/store.js";
const database = process.env.TEST_DATABASE_URL;
test(
  "PostgreSQL: fencing, deduplication, LOW minimization, outbox, RLS and cascade",
  { skip: !database },
  async () => {
    const pool = new pg.Pool({ connectionString: database });
    const db = new PgStore(pool);
    const u = "ac3815de-2753-497c-8e9f-02e56f034bd6";
    const other = "bc3815de-2753-497c-8e9f-02e56f034bd6";
    try {
      await db.ensure(u);
      const l = await db.acquire(u);
      assert.ok(l);
      assert.equal(await db.acquire(u), null);
      await assert.rejects(
        db.update({ ...l, token: other }, { status: "ACTIVE" }),
        { code: "LEASE_LOST" },
      );
      const e = {
        userId: u,
        hash: "a".repeat(64),
        receivedAt: new Date(),
        analysis: {
          level: "LOW" as const,
          score: 15,
          reasons: ["must not persist"],
        },
        sender: "private",
        subject: "private",
        limited: false,
      };
      assert.equal(await db.insertEvent(l, e), true);
      assert.equal(await db.insertEvent(l, e), false);
      const low = (
        await pool.query("SELECT * FROM public.gmail_events WHERE user_id=$1", [
          u,
        ])
      ).rows[0];
      assert.equal(low.score, null);
      assert.equal(low.sender, null);
      assert.equal(low.subject, null);
      assert.deepEqual(low.reasons, []);
      await db.insertEvent(l, {
        ...e,
        hash: "b".repeat(64),
        analysis: { level: "HIGH", score: 100, reasons: ["signal"] },
      });
      assert.equal(
        (
          await pool.query(
            "SELECT count(*)::int n FROM private.gmail_notification_outbox",
          )
        ).rows[0].n,
        1,
      );
      const c = await pool.connect();
      try {
        await c.query("BEGIN");
        await c.query("SET LOCAL ROLE authenticated");
        await c.query("SELECT set_config('request.jwt.claim.sub',$1,true)", [
          other,
        ]);
        assert.equal(
          (await c.query("SELECT * FROM public.gmail_events")).rowCount,
          0,
        );
        await c.query("SELECT set_config('request.jwt.claim.sub',$1,true)", [
          u,
        ]);
        assert.equal(
          (await c.query("SELECT * FROM public.gmail_events")).rowCount,
          2,
        );
        await c.query("ROLLBACK");
        for (const query of [
          "SELECT * FROM private.gmail_connections",
          "SELECT * FROM private.gmail_devices",
          "DELETE FROM public.gmail_events",
        ]) {
          await c.query("BEGIN");
          await c.query("SET LOCAL ROLE authenticated");
          await assert.rejects(c.query(query), { code: "42501" });
          await c.query("ROLLBACK");
        }
      } finally {
        c.release();
      }
      await db.release(l);
      assert.ok(await db.acquire(u));
      await pool.query("DELETE FROM auth.users WHERE id=$1", [u]);
      assert.equal(
        (
          await pool.query(
            "SELECT * FROM private.gmail_connections WHERE user_id=$1",
            [u],
          )
        ).rowCount,
        0,
      );
      assert.equal(
        (
          await pool.query(
            "SELECT * FROM public.gmail_events WHERE user_id=$1",
            [u],
          )
        ).rowCount,
        0,
      );
      assert.equal(
        (await pool.query("SELECT * FROM private.gmail_notification_outbox"))
          .rowCount,
        0,
      );
    } finally {
      await pool.end();
    }
  },
);
