import {
  AppError,
  type Google,
  type Push,
  type Queue,
  type Cipher,
} from "../types.js";
import { parseMessage } from "../mime.js";
import { DemoStore } from "./store.js";
/** Credentials are prohibited, rather than fabricated. The demo never calls refresh/exchange. */
export class LocalDemoMailbox implements Google {
  constructor(
    private db: DemoStore,
    private userId?: string,
    private failOnce = false,
  ) {}
  private noToken(token: string) {
    if (token !== "") throw new AppError("CREDENTIALS_FORBIDDEN_IN_DEMO", 400);
  }
  async exchange(): Promise<never> {
    throw new AppError("OAUTH_DISABLED_IN_DEMO", 400);
  }
  async refresh(): Promise<never> {
    throw new AppError("REFRESH_DISABLED_IN_DEMO", 400);
  }
  async profile(): Promise<never> {
    throw new AppError("PROFILE_DISABLED_IN_DEMO", 400);
  }
  async watch(): Promise<never> {
    throw new AppError("WATCH_DISABLED_IN_DEMO", 400);
  }
  async stop(): Promise<never> {
    throw new AppError("STOP_DISABLED_IN_DEMO", 400);
  }
  async revoke(): Promise<never> {
    throw new AppError("REVOKE_DISABLED_IN_DEMO", 400);
  }
  async history(token: string, cursor: string, page?: string) {
    this.noToken(token);
    const owner = this.userId;
    if (!owner) throw new AppError("DEMO_OWNER_REQUIRED");
    return this.db.tx(async (c) => {
      const rows = await c.query(
        "SELECT id,seq,max(seq) OVER() latest FROM demo.mail WHERE user_id=$1 AND seq>$2 ORDER BY seq LIMIT 101",
        [owner, page ?? cursor],
      );
      return {
        ids: rows.rows.slice(0, 100).map((r) => r.id),
        nextPage:
          rows.rows.length > 100 ? String(rows.rows[99].seq) : undefined,
        historyId: String(rows.rows[0]?.latest ?? page ?? cursor),
      };
    });
  }
  async list(): Promise<never> {
    throw new AppError("RESYNC_DISABLED_IN_DEMO", 400);
  }
  async message(token: string, id: string) {
    this.noToken(token);
    if (this.failOnce) {
      this.failOnce = false;
      throw new AppError("DEMO_RETRY_ONCE");
    }
    return this.db.tx(async (c) => {
      const r = await c.query(
        "SELECT mime FROM demo.mail WHERE user_id=$1 AND id=$2",
        [this.userId, id],
      );
      return r.rows[0] ? parseMessage(r.rows[0].mime) : null;
    });
  }
}
export class LocalPubSub implements Queue {
  constructor(private db: DemoStore) {}
  publish(email: string, cursor: string) {
    return this.db.enqueue(email, cursor);
  }
}
export class LocalDemoNotificationProvider implements Push {
  constructor(private db: DemoStore) {}
  async flush(userId?: string) {
    if (!userId) return;
    await this.db.tx(async (c) => {
      await c.query("SELECT pg_advisory_xact_lock(28160222)");
      await c.query(
        "INSERT INTO demo.notification(event_id,user_id) SELECT e.id,e.user_id FROM private.gmail_notification_outbox o JOIN public.gmail_events e ON e.id=o.event_id WHERE e.user_id=$1 AND e.risk_level='HIGH' ON CONFLICT(event_id) DO NOTHING",
        [userId],
      );
      await c.query(
        "UPDATE private.gmail_notification_outbox SET delivered_at=now() WHERE event_id IN (SELECT event_id FROM demo.notification WHERE user_id=$1)",
        [userId],
      );
    });
  }
}
export const noDemoCipher: Cipher = {
  encrypt: async () => {
    throw new AppError("CIPHER_NOT_NEEDED_IN_DEMO");
  },
  decrypt: async () => {
    throw new AppError("CIPHER_NOT_NEEDED_IN_DEMO");
  },
};
