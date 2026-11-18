import pg from "pg";
import { randomUUID } from "node:crypto";
import {
  AppError,
  type Connection,
  type Event,
  type Lease,
  type Store,
} from "./types.js";
import { weekBounds } from "./time.js";
const columns: Record<string, string> = {
  zone: "time_zone",
  email: "gmail_email",
  ciphertext: "encrypted_refresh_token",
  historyId: "history_id",
  expiration: "watch_expiration",
  startedAt: "monitoring_started_at",
  tokenCreatedAt: "token_created_at",
  status: "connection_status",
  lastSync: "last_sync_at",
  lastError: "last_error_code",
};
function connection(r: any): Connection {
  return {
    id: r.id,
    userId: r.user_id,
    zone: r.time_zone,
    email: r.gmail_email,
    ciphertext: r.encrypted_refresh_token,
    historyId: r.history_id,
    expiration: r.watch_expiration,
    startedAt: r.monitoring_started_at,
    tokenCreatedAt: r.token_created_at,
    status: r.connection_status,
    lastSync: r.last_sync_at,
    lastError: r.last_error_code,
  };
}
/** Every transaction drops to a dedicated non-owner, non-BYPASSRLS role. */
export class PgStore implements Store {
  constructor(private pool: pg.Pool) {}
  async tx<T>(run: (c: pg.PoolClient) => Promise<T>): Promise<T> {
    const c = await this.pool.connect();
    try {
      await c.query("BEGIN");
      await c.query("SET LOCAL ROLE hestia_backend");
      await c.query("SET LOCAL statement_timeout='15s'");
      const result = await run(c);
      await c.query("COMMIT");
      return result;
    } catch (e) {
      await c.query("ROLLBACK").catch(() => {});
      throw e;
    } finally {
      c.release();
    }
  }
  async ensure(userId: string) {
    await this.tx(async (c) => {
      await c.query(
        "INSERT INTO private.gmail_connections(user_id) VALUES($1) ON CONFLICT(user_id) DO NOTHING",
        [userId],
      );
    });
  }
  async find(userId: string) {
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT * FROM private.gmail_connections WHERE user_id=$1",
        [userId],
      );
      return r.rows[0] ? connection(r.rows[0]) : null;
    });
  }
  async findEmail(email: string) {
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT * FROM private.gmail_connections WHERE gmail_email=$1",
        [email],
      );
      return r.rows[0] ? connection(r.rows[0]) : null;
    });
  }
  async acquire(userId: string) {
    return this.tx(async (c) => {
      const token = randomUUID();
      const r = await c.query(
        "UPDATE private.gmail_connections SET lease_token=$2,lease_until=now()+interval '120 seconds' WHERE user_id=$1 AND (lease_until IS NULL OR lease_until<now()) RETURNING *",
        [userId, token],
      );
      return r.rows[0] ? { connection: connection(r.rows[0]), token } : null;
    });
  }
  async renew(l: Lease) {
    return this.tx(
      async (c) =>
        (
          await c.query(
            "UPDATE private.gmail_connections SET lease_until=now()+interval '120 seconds' WHERE user_id=$1 AND lease_token=$2 AND lease_until>now()",
            [l.connection.userId, l.token],
          )
        ).rowCount === 1,
    );
  }
  async release(l: Lease) {
    await this.tx(async (c) => {
      await c.query(
        "UPDATE private.gmail_connections SET lease_until=NULL,lease_token=NULL WHERE user_id=$1 AND lease_token=$2",
        [l.connection.userId, l.token],
      );
    });
  }
  private async fence(c: pg.PoolClient, l: Lease) {
    const r = await c.query(
      "SELECT id FROM private.gmail_connections WHERE user_id=$1 AND lease_token=$2 AND lease_until>now() FOR UPDATE",
      [l.connection.userId, l.token],
    );
    if (!r.rowCount) throw new AppError("LEASE_LOST");
  }
  async update(l: Lease, changes: Partial<Connection>) {
    await this.tx(async (c) => {
      await this.fence(c, l);
      const entries = Object.entries(changes).filter(([key]) => columns[key]);
      if (!entries.length) return;
      await c.query(
        "UPDATE private.gmail_connections SET " +
          entries.map(([k], i) => `${columns[k]}=$${i + 2}`).join(",") +
          ",updated_at=now() WHERE user_id=$1",
        [l.connection.userId, ...entries.map(([, v]) => v)],
      );
    });
  }
  async hasEvent(userId: string, hash: string) {
    return this.tx(
      async (c) =>
        (
          await c.query(
            "SELECT id FROM public.gmail_events WHERE user_id=$1 AND message_id_hash=$2",
            [userId, hash],
          )
        ).rowCount !== 0,
    );
  }
  async insertEvent(l: Lease, e: Event) {
    return this.tx(async (c) => {
      await this.fence(c, l);
      if (e.userId !== l.connection.userId)
        throw new AppError("OWNER_MISMATCH");
      const low = e.analysis.level === "LOW";
      const r = await c.query(
        "INSERT INTO public.gmail_events(user_id,message_id_hash,received_at,risk_level,score,reasons,sender,subject,content_limited) VALUES($1,$2,$3,$4,$5,$6,$7,$8,$9) ON CONFLICT(user_id,message_id_hash) DO NOTHING RETURNING id",
        [
          e.userId,
          e.hash,
          e.receivedAt,
          e.analysis.level,
          low ? null : e.analysis.score,
          JSON.stringify(low ? [] : e.analysis.reasons),
          low ? null : e.sender,
          low ? null : e.subject,
          e.limited,
        ],
      );
      if (r.rows[0] && e.analysis.level === "HIGH")
        await c.query(
          "INSERT INTO private.gmail_notification_outbox(event_id) VALUES($1) ON CONFLICT DO NOTHING",
          [r.rows[0].id],
        );
      return !!r.rowCount;
    });
  }
  async active() {
    return this.tx(async (c) =>
      (
        await c.query(
          "SELECT * FROM private.gmail_connections WHERE connection_status IN ('ACTIVE','TEMP_ERROR') AND encrypted_refresh_token IS NOT NULL ORDER BY updated_at",
        )
      ).rows.map(connection),
    );
  }
  async cleanup() {
    await this.tx(async (c) => {
      await c.query(
        "DELETE FROM public.gmail_events WHERE received_at<now()-interval '30 days'",
      );
      await c.query("DELETE FROM private.gmail_devices WHERE expires_at<now()");
    });
  }
  async erase(l: Lease, deleteEvents: boolean, remote: boolean) {
    await this.tx(async (c) => {
      await this.fence(c, l);
      await c.query(
        "UPDATE private.gmail_connections SET gmail_email=NULL,encrypted_refresh_token=NULL,history_id=NULL,watch_expiration=NULL,monitoring_started_at=NULL,token_created_at=NULL,last_sync_at=NULL,last_error_code=NULL,connection_status=$2,updated_at=now() WHERE user_id=$1",
        [
          l.connection.userId,
          remote ? "DISCONNECTED" : "DISCONNECTED_REVOCATION_PENDING",
        ],
      );
      await c.query("DELETE FROM private.gmail_devices WHERE user_id=$1", [
        l.connection.userId,
      ]);
      await c.query(
        "DELETE FROM private.gmail_notification_outbox WHERE event_id IN (SELECT id FROM public.gmail_events WHERE user_id=$1)",
        [l.connection.userId],
      );
      if (deleteEvents)
        await c.query("DELETE FROM public.gmail_events WHERE user_id=$1", [
          l.connection.userId,
        ]);
    });
  }
  async throttle(userId: string) {
    await this.tx(async (c) => {
      const r = await c.query(
        "UPDATE private.gmail_connections SET last_manual_request_at=now() WHERE user_id=$1 AND (last_manual_request_at IS NULL OR last_manual_request_at<now()-interval '60 seconds')",
        [userId],
      );
      if (!r.rowCount) throw new AppError("TRY_LATER", 429);
    });
  }
  async summary(userId: string, zone: string) {
    const w = weekBounds(zone, new Date());
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT count(*)::int analyzed,count(*) FILTER(WHERE risk_level<>'LOW')::int suspicious FROM public.gmail_events WHERE user_id=$1 AND received_at>=$2 AND received_at<$3",
        [userId, w.start, w.end],
      );
      return {
        ...r.rows[0],
        weekStart: w.start.toISOString(),
        weekEnd: w.end.toISOString(),
        retentionDays: 30,
      };
    });
  }
  async alerts(userId: string, before: string | undefined) {
    let received: string | null = null,
      id: string | null = null;
    if (before) {
      try {
        if (!/^[A-Za-z0-9_-]{1,256}$/.test(before)) throw Error();
        const v = JSON.parse(Buffer.from(before, "base64url").toString("utf8"));
        if (
          typeof v.received !== "string" ||
          new Date(v.received).toISOString() !== v.received ||
          typeof v.id !== "string" ||
          !v.id.match(/^[a-f0-9-]{36}$/i)
        )
          throw Error();
        received = v.received;
        id = v.id;
      } catch {
        throw new AppError("INVALID_CURSOR", 400);
      }
    }
    return this.tx(async (c) => {
      const r = await c.query(
        "SELECT id,received_at,analyzed_at,risk_level,reasons,sender,subject,content_limited FROM public.gmail_events WHERE user_id=$1 AND risk_level<>'LOW' AND ($2::timestamptz IS NULL OR (received_at,id)<($2::timestamptz,$3::uuid)) ORDER BY received_at DESC,id DESC LIMIT 51",
        [userId, received, id],
      );
      const last = r.rows[49];
      return {
        items: r.rows.slice(0, 50),
        nextCursor:
          r.rows.length > 50
            ? Buffer.from(
                JSON.stringify({
                  received: last.received_at.toISOString(),
                  id: last.id,
                }),
              ).toString("base64url")
            : null,
      };
    });
  }
  async event(userId: string, id: string) {
    return this.tx(
      async (c) =>
        (
          await c.query(
            "SELECT id,received_at,analyzed_at,risk_level,reasons,sender,subject,content_limited FROM public.gmail_events WHERE user_id=$1 AND id=$2 AND risk_level<>'LOW'",
            [userId, id],
          )
        ).rows[0] ?? null,
    );
  }
  async device(
    userId: string,
    installation: string,
    token: string,
    enabled: boolean,
  ) {
    await this.tx(async (c) => {
      // Transfer only the same installation token after authenticated ownership change.
      await c.query(
        "DELETE FROM private.gmail_devices WHERE fcm_token=$1 AND installation_id=$2 AND user_id<>$3",
        [token, installation, userId],
      );
      await c.query(
        "INSERT INTO private.gmail_devices(user_id,installation_id,fcm_token,notifications_enabled) VALUES($1,$2,$3,$4) ON CONFLICT(user_id,installation_id) DO UPDATE SET fcm_token=excluded.fcm_token,notifications_enabled=excluded.notifications_enabled,expires_at=now()+interval '30 days',updated_at=now()",
        [userId, installation, token, enabled],
      );
    });
  }
  async removeDevice(userId: string, installation: string) {
    await this.tx(async (c) => {
      await c.query(
        "DELETE FROM private.gmail_devices WHERE user_id=$1 AND installation_id=$2",
        [userId, installation],
      );
    });
  }
}
