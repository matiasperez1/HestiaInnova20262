import { setTimeout } from "node:timers/promises";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { PgStore } from "../store.js";
import { AppError, type User } from "../types.js";
import { fixture, scenarios } from "./fixtures.js";
export const personas = {
  alice: {
    id: "d0000000-0000-4000-8000-000000000001",
    email: "alice@example.invalid",
  },
  bob: {
    id: "d0000000-0000-4000-8000-000000000002",
    email: "bob@example.invalid",
  },
} as const;
const hash = (s: string) => createHash("sha256").update(s).digest("hex");
export class DemoStore extends PgStore {
  async identity(persona: string): Promise<User> {
    const user = personas[persona as keyof typeof personas];
    if (!user) throw new AppError("UNKNOWN_DEMO_PERSONA", 400);
    return user;
  }
  async session(persona: string) {
    const user = await this.identity(persona);
    const token = "demo-" + randomBytes(32).toString("base64url");
    await this.tx(async (c) => {
      await c.query("DELETE FROM demo.session WHERE expires_at<now()");
      await c.query(
        "INSERT INTO demo.session(token_hash,user_id) VALUES($1,$2)",
        [hash(token), user.id],
      );
    });
    return {
      token,
      userId: user.id,
      label: persona === "alice" ? "Ana · DEMO LOCAL" : "Bruno · DEMO LOCAL",
    };
  }
  async authenticate(header: string | undefined): Promise<User> {
    if (!header?.startsWith("Bearer demo-") || header.length > 256)
      throw new AppError("DEMO_UNAUTHENTICATED", 401);
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT i.user_id,i.email FROM demo.session s JOIN demo.identity i ON i.user_id=s.user_id WHERE s.token_hash=$1 AND s.expires_at>now()",
        [hash(header.slice(7))],
      );
      if (!r.rows[0]) throw new AppError("DEMO_UNAUTHENTICATED", 401);
      return { id: r.rows[0].user_id, email: r.rows[0].email };
    });
  }
  async activate(user: User) {
    await this.ensure(user.id);
    const current = await this.find(user.id);
    if (current?.startedAt) return;
    const lease = await this.acquire(user.id);
    if (!lease) {
      for (let i = 0; i < 20; i++) {
        await setTimeout(50);
        if ((await this.find(user.id))?.startedAt) return;
      }
      throw new AppError("CONNECTION_BUSY", 409);
    }
    try {
      await this.update(lease, {
        email: user.email,
        zone: "UTC",
        status: "ACTIVE",
        historyId: "0",
        startedAt: new Date(Date.now() - 1),
        lastSync: new Date(),
        ciphertext: null,
        tokenCreatedAt: null,
      });
    } finally {
      await this.release(lease);
    }
  }
  async emit(user: User, scenario: string, failOnce = false) {
    if (!scenarios.some((s) => s.id === scenario))
      throw new AppError("UNKNOWN_SCENARIO", 400);
    return this.tx(async (c) => {
      const id = randomUUID().replaceAll("-", "");
      const now = new Date();
      const r = await c.query(
        "INSERT INTO demo.mail(user_id,id,scenario,mime) VALUES($1,$2,$3,$4) RETURNING seq",
        [user.id, id, scenario, JSON.stringify(fixture(scenario, id, now))],
      );
      const eventId = randomUUID();
      const envelope = {
        subscription: "local-demo",
        message: {
          data: Buffer.from(
            JSON.stringify({
              emailAddress: user.email,
              historyId: String(r.rows[0].seq),
            }),
          ).toString("base64"),
        },
      };
      await c.query(
        "INSERT INTO demo.queue(event_id,user_id,envelope,fail_once) VALUES($1,$2,$3,$4)",
        [eventId, user.id, JSON.stringify(envelope), failOnce],
      );
      return { eventId, scenario, mode: "LOCAL_DEMO" };
    });
  }
  async repeat(userId: string) {
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT event_id,envelope FROM demo.queue WHERE user_id=$1 ORDER BY created_at DESC LIMIT 1",
        [userId],
      );
      if (!r.rows[0]) throw new AppError("NO_DEMO_EVENT", 409);
      const q = r.rows[0];
      await c.query(
        "INSERT INTO demo.queue(event_id,user_id,envelope) VALUES($1,$2,$3)",
        [q.event_id, userId, JSON.stringify(q.envelope)],
      );
      return { eventId: q.event_id, mode: "LOCAL_DEMO" };
    });
  }
  async enqueue(email: string, cursor: string) {
    await this.tx(async (c) => {
      const r = await c.query(
        "SELECT user_id FROM demo.identity WHERE email=$1",
        [email],
      );
      if (!r.rows[0]) throw new AppError("UNKNOWN_DEMO_PERSONA", 400);
      const envelope = {
        subscription: "local-demo",
        message: {
          data: Buffer.from(
            JSON.stringify({ emailAddress: email, historyId: cursor }),
          ).toString("base64"),
          attributes: { hestiaManual: "true" },
        },
      };
      await c.query(
        "INSERT INTO demo.queue(event_id,user_id,envelope) VALUES($1,$2,$3)",
        [randomUUID(), r.rows[0].user_id, JSON.stringify(envelope)],
      );
    });
  }
  async claim() {
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT * FROM demo.queue WHERE (status='PENDING' AND available_at<=now()) OR (status='PROCESSING' AND lease_until<now()) ORDER BY created_at LIMIT 1 FOR UPDATE SKIP LOCKED",
      );
      if (!r.rows[0]) return null;
      const q = r.rows[0];
      const lease = randomUUID();
      await c.query(
        "UPDATE demo.queue SET status='PROCESSING',attempts=attempts+1,lease=$2,lease_until=now()+interval '120 seconds' WHERE id=$1",
        [q.id, lease],
      );
      return { ...q, lease, attempts: q.attempts + 1 };
    });
  }
  async finish(job: any, error?: string, duplicate = false) {
    await this.tx(async (c) => {
      await c.query(
        "UPDATE demo.queue SET status=$3,last_error=$4,duplicate=$5,lease=NULL,lease_until=NULL,completed_at=CASE WHEN $3='SUCCESS' THEN now() ELSE NULL END,available_at=now()+interval '2 seconds' WHERE id=$1 AND lease=$2",
        [
          job.id,
          job.lease,
          error ? "PENDING" : "SUCCESS",
          error ?? null,
          duplicate,
        ],
      );
    });
  }
  async heartbeat() {
    await this.tx(async (c) => {
      await c.query(
        "INSERT INTO demo.worker VALUES(1,now()) ON CONFLICT(id) DO UPDATE SET heartbeat=excluded.heartbeat",
      );
    });
  }
  async notifications(userId: string, after: string | null, limit: number) {
    const encode = (seq: string) => Buffer.from(`${userId}:${seq}`).toString("base64url");
    return this.tx(async (c) => {
      await c.query("SELECT pg_advisory_xact_lock(28160222)");
      if (after === null) {
        const head = await c.query("SELECT coalesce(max(delivery_seq),0)::text cursor FROM demo.notification WHERE user_id=$1", [userId]);
        return { identity: userId, items: [], nextCursor: encode(head.rows[0].cursor), hasMore: false };
      }
      if (!/^[A-Za-z0-9_-]{1,128}$/.test(after)) throw new AppError("INVALID_CURSOR", 400);
      const decoded = Buffer.from(after, "base64url").toString("utf8");
      const prefix = userId + ":";
      const seq = decoded.startsWith(prefix) ? decoded.slice(prefix.length) : "";
      if (!/^(0|[1-9][0-9]{0,18})$/.test(seq) || BigInt(seq) > 9223372036854775807n || encode(seq) !== after) throw new AppError("INVALID_CURSOR", 400);
      const rows = await c.query("SELECT event_id,delivery_seq::text cursor FROM demo.notification WHERE user_id=$1 AND delivery_seq>$2::bigint ORDER BY delivery_seq LIMIT $3", [userId, seq, limit + 1]);
      const items = rows.rows.slice(0, limit).map((r) => ({event_id:r.event_id,cursor:encode(r.cursor)}));
      return { identity: userId, items, nextCursor: items.at(-1)?.cursor ?? after, hasMore: rows.rows.length > limit };
    });
  }
  async info(userId: string) {
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT count(*) FILTER(WHERE status='SUCCESS')::int processed,count(*) FILTER(WHERE status<>'SUCCESS')::int pending,coalesce(sum(greatest(attempts-1,0)),0)::int retries,count(*) FILTER(WHERE duplicate)::int duplicates,max(created_at) last_simulation FROM demo.queue WHERE user_id=$1",
        [userId],
      );
      const n = await c.query(
        "SELECT event_id,created_at FROM demo.notification WHERE user_id=$1 ORDER BY created_at DESC LIMIT 50",
        [userId],
      );
      const h = await c.query(
        "SELECT heartbeat>now()-interval '6 seconds' running,heartbeat FROM demo.worker WHERE id=1",
      );
      return {
        mode: "LOCAL_DEMO",
        ...r.rows[0],
        notifications: n.rows,
        notificationCount: (
          await c.query(
            "SELECT count(*)::int n FROM demo.notification WHERE user_id=$1",
            [userId],
          )
        ).rows[0].n,
        workerRunning: h.rows[0]?.running ?? false,
        workerHeartbeat: h.rows[0]?.heartbeat ?? null,
        scenarios: scenarios.map((s) => ({ id: s.id, label: s.label })),
      };
    });
  }
  async clearDemo(user: User) {
    const l = await this.acquire(user.id);
    if (!l) throw new AppError("CONNECTION_BUSY", 409);
    try {
      await this.tx(async (c) => {
        const fenced = await c.query(
          "SELECT id FROM private.gmail_connections WHERE user_id=$1 AND lease_token=$2 AND lease_until>now() FOR UPDATE",
          [user.id, l.token],
        );
        if (!fenced.rowCount) throw new AppError("LEASE_LOST");
        await c.query("DELETE FROM demo.queue WHERE user_id=$1", [user.id]);
        await c.query("DELETE FROM demo.mail WHERE user_id=$1", [user.id]);
        await c.query("DELETE FROM public.gmail_events WHERE user_id=$1", [
          user.id,
        ]);
      });
      await this.update(l, {
        status: "ACTIVE",
        historyId: "0",
        lastError: null,
        startedAt: new Date(Date.now() - 1),
        lastSync: new Date(),
      });
    } finally {
      await this.release(l);
    }
  }
}
