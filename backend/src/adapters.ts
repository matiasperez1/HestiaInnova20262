import { createHash } from "node:crypto";
import { KeyManagementServiceClient } from "@google-cloud/kms";
import { PubSub } from "@google-cloud/pubsub";
import { OAuth2Client } from "google-auth-library";
import { initializeApp, applicationDefault } from "firebase-admin/app";
import { getMessaging } from "firebase-admin/messaging";
import type { Config } from "./config.js";
import {
  AppError,
  type Cipher,
  type Push,
  type Queue,
  type User,
} from "./types.js";
import { PgStore } from "./store.js";
export class KmsCipher implements Cipher {
  constructor(private key: string, private client: Pick<KeyManagementServiceClient, "encrypt" | "decrypt"> = new KeyManagementServiceClient()) {}
  async encrypt(userId: string, plain: string) {
    const [r] = await this.client.encrypt(
      {
        name: this.key,
        plaintext: Buffer.from(plain),
        additionalAuthenticatedData: Buffer.from("hestia:gmail:" + userId),
      },
      { timeout: 15_000 },
    );
    if (!r.ciphertext) throw new AppError("ENCRYPTION_FAILED");
    return (typeof r.ciphertext === "string" ? Buffer.from(r.ciphertext, "base64") : Buffer.from(r.ciphertext)).toString("base64");
  }
  async decrypt(userId: string, encrypted: string) {
    const [r] = await this.client.decrypt(
      {
        name: this.key,
        ciphertext: Buffer.from(encrypted, "base64"),
        additionalAuthenticatedData: Buffer.from("hestia:gmail:" + userId),
      },
      { timeout: 15_000 },
    );
    if (!r.plaintext) throw new AppError("DECRYPTION_FAILED");
    return (typeof r.plaintext === "string" ? Buffer.from(r.plaintext, "base64") : Buffer.from(r.plaintext)).toString("utf8");
  }
}
export class PubsubQueue implements Queue {
  private client: PubSub;
  constructor(private cfg: Config) {
    this.client = new PubSub({ projectId: cfg.project });
  }
  async publish(email: string, cursor: string) {
    await this.client
      .topic(this.cfg.topic)
      .publishMessage({
        json: { emailAddress: email, historyId: cursor },
        attributes: { hestiaManual: "true" },
      });
  }
}
export class IdentityVerifier {
  constructor(
    private cfg: Config,
    private google: Pick<OAuth2Client, "verifyIdToken"> = new OAuth2Client(),
    private fetcher: typeof fetch = fetch,
  ) {}
  async user(header: string | undefined): Promise<User> {
    if (!header?.startsWith("Bearer ") || header.length > 16384)
      throw new AppError("UNAUTHENTICATED", 401);
    const response = await this.fetcher(
      this.cfg.supabaseUrl + "/auth/v1/user",
      {
        headers: { Authorization: header, apikey: this.cfg.publishableKey },
        signal: AbortSignal.timeout(10_000),
        redirect: "error",
      },
    );
    if (!response.ok)
      throw new AppError(
        response.status === 401 || response.status === 403
          ? "UNAUTHENTICATED"
          : "AUTH_UNAVAILABLE",
        response.status === 401 || response.status === 403 ? 401 : 503,
      );
    const value = (await response.json()) as any;
    if (
      typeof value.id !== "string" ||
      typeof value.email !== "string" ||
      !value.email_confirmed_at ||
      !value.identities?.some((i: any) => i.provider === "google")
    )
      throw new AppError("GOOGLE_ACCOUNT_REQUIRED", 403);
    return { id: value.id, email: value.email };
  }
  async oidc(header: string | undefined, kind: "pubsub" | "scheduler") {
    if (!header?.startsWith("Bearer ") || header.length > 16384)
      throw new AppError("UNAUTHENTICATED", 401);
    try {
      const ticket = await this.google.verifyIdToken({
        idToken: header.slice(7),
        audience: this.cfg.workerAudience,
      });
      const p = ticket.getPayload();
      if (
        !p ||
        p.email !==
          (kind === "pubsub"
            ? this.cfg.pubsubEmail
            : this.cfg.schedulerEmail) ||
        !p.email_verified ||
        !p.sub ||
        !["accounts.google.com", "https://accounts.google.com"].includes(p.iss)
      )
        throw new Error();
    } catch {
      throw new AppError("FORBIDDEN", 403);
    }
  }
}
export function pushData(eventId: string, owner: string) {
  return {
    type: "hestia_gmail_alert",
    eventId,
    accountTag: createHash("sha256").update(owner).digest("hex"),
  };
}
export class FcmPush implements Push {
  private messaging;
  constructor(
    private db: PgStore,
    projectId: string,
  ) {
    this.messaging = getMessaging(
      initializeApp({ credential: applicationDefault(), projectId }),
    );
  }
  async flush(userId?: string) {
    for (let n = 0; n < 100; n++) {
      const found = await this.db.tx(async (c) => {
        const r = await c.query(
          "SELECT o.id,e.id event_id,e.user_id FROM private.gmail_notification_outbox o JOIN public.gmail_events e ON e.id=o.event_id JOIN private.gmail_connections g ON g.user_id=e.user_id WHERE o.delivered_at IS NULL AND o.next_attempt_at<=now() AND ($1::uuid IS NULL OR e.user_id=$1) AND g.connection_status IN ('ACTIVE','TEMP_ERROR') ORDER BY o.created_at LIMIT 1 FOR UPDATE OF o SKIP LOCKED",
          [userId ?? null],
        );
        const item = r.rows[0];
        if (!item) return false;
        const devices = await c.query(
          "SELECT id,fcm_token FROM private.gmail_devices WHERE user_id=$1 AND notifications_enabled AND expires_at>now()",
          [item.user_id],
        );
        let failed = false;
        for (const d of devices.rows) {
          try {
            await this.messaging.send({
              token: d.fcm_token,
              data: pushData(item.event_id, item.user_id),
              android: {
                priority: "high",
                collapseKey: item.event_id,
                ttl: 3600_000,
              },
            });
          } catch (e) {
            const code = (e as { code?: string }).code;
            if (
              [
                "messaging/registration-token-not-registered",
                "messaging/invalid-registration-token",
              ].includes(code ?? "")
            )
              await c.query("DELETE FROM private.gmail_devices WHERE id=$1", [
                d.id,
              ]);
            else failed = true;
          }
        }
        await c.query(
          "UPDATE private.gmail_notification_outbox SET attempts=attempts+1,delivered_at=CASE WHEN $2 THEN NULL ELSE now() END,next_attempt_at=now()+interval '5 minutes' WHERE id=$1",
          [item.id, failed],
        );
        return true;
      });
      if (!found) break;
    }
  }
}
