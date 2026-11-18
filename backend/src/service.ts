import { createHash } from "node:crypto";
import {
  AppError,
  type Cipher,
  type Clock,
  type Connection,
  type Google,
  type Lease,
  type Push,
  type Queue,
  type Store,
  type User,
} from "./types.js";
import { analyze } from "./fraud.js";
import { historyId, weekBounds } from "./time.js";
export class GmailService {
  constructor(
    private db: Store,
    private google: Google,
    private cipher: Cipher,
    private queue: Queue,
    private push: Push,
    private testing = true,
    private clock: Clock = () => new Date(),
    private processingAccess?: { access(lease: Lease): Promise<string> },
  ) {}
  private async change(lease: Lease, changes: Partial<Connection>) {
    await this.db.update(lease, changes);
    Object.assign(lease.connection, changes);
  }
  private async locked<T>(
    userId: string,
    action: (lease: Lease) => Promise<T>,
  ): Promise<T> {
    const lease = await this.db.acquire(userId);
    if (!lease) throw new AppError("CONNECTION_BUSY", 409);
    let lost = false;
    const heartbeat = setInterval(() => {
      void this.db
        .renew(lease)
        .then((ok) => {
          if (!ok) lost = true;
        })
        .catch(() => {
          lost = true;
        });
    }, 30_000);
    heartbeat.unref();
    try {
      const result = await action(lease);
      if (lost) throw new AppError("LEASE_LOST");
      return result;
    } finally {
      clearInterval(heartbeat);
      await this.db.release(lease).catch(() => {});
    }
  }
  private async token(lease: Lease) {
    if (this.processingAccess) return this.processingAccess.access(lease);
    const c = lease.connection;
    if (!c.ciphertext) throw new AppError("REAUTH_REQUIRED", 409);
    if (
      this.testing &&
      c.tokenCreatedAt &&
      this.clock().getTime() - c.tokenCreatedAt.getTime() >= 7 * 86400_000
    )
      throw new AppError("REAUTH_REQUIRED", 409);
    const refresh = await this.cipher.decrypt(c.userId, c.ciphertext);
    const tokens = await this.google.refresh(refresh);
    if (tokens.refreshToken)
      await this.change(lease, {
        ciphertext: await this.cipher.encrypt(c.userId, tokens.refreshToken),
        tokenCreatedAt: this.clock(),
      });
    return tokens.accessToken;
  }
  private async failure(lease: Lease, error: unknown) {
    const reauth =
      error instanceof AppError && error.code === "REAUTH_REQUIRED";
    await this.change(lease, {
      status: reauth ? "REAUTH_REQUIRED" : "TEMP_ERROR",
      lastError: error instanceof AppError ? error.code : "TEMPORARY_ERROR",
    }).catch(() => {});
  }
  async connect(user: User, code: string, zone: string) {
    weekBounds(zone, this.clock());
    await this.db.ensure(user.id);
    return this.locked(user.id, async (lease) => {
      if (
        lease.connection.status === "ACTIVE" &&
        lease.connection.historyId &&
        lease.connection.expiration &&
        lease.connection.expiration > this.clock()
      )
        throw new AppError("ALREADY_CONNECTED", 409);
      try {
        await this.change(lease, {
          status: "ACTIVATING",
          lastError: null,
          zone,
        });
        const tokens = await this.google.exchange(code);
        const profile = await this.google.profile(tokens.accessToken);
        if (profile.email.toLowerCase() !== user.email.toLowerCase()) {
          if (tokens.refreshToken)
            await this.google.revoke(tokens.refreshToken);
          throw new AppError("GMAIL_ACCOUNT_MISMATCH", 400);
        }
        if (!tokens.refreshToken) {
          await this.change(lease, {
            status: "REAUTH_REQUIRED",
            lastError: "REFRESH_TOKEN_REQUIRED",
          });
          throw new AppError("REFRESH_TOKEN_REQUIRED", 428);
        }
        const encrypted = await this.cipher.encrypt(
          user.id,
          tokens.refreshToken,
        );
        await this.change(lease, {
          email: profile.email.toLowerCase(),
          ciphertext: encrypted,
          tokenCreatedAt: this.clock(),
        });
        const started = this.clock();
        const watch = await this.google.watch(tokens.accessToken);
        await this.change(lease, {
          status: "ACTIVE",
          historyId: watch.historyId,
          expiration: watch.expiration,
          startedAt: started,
          lastSync: this.clock(),
          lastError: null,
        });
        return lease.connection;
      } catch (error) {
        if (
          !(
            error instanceof AppError && error.code === "REFRESH_TOKEN_REQUIRED"
          )
        )
          await this.failure(lease, error);
        throw error;
      }
    });
  }
  async requestSync(userId: string) {
    const c = await this.db.find(userId);
    if (
      !c?.email ||
      !c.historyId ||
      !["ACTIVE", "TEMP_ERROR"].includes(c.status)
    )
      throw new AppError("GMAIL_NOT_ACTIVE", 409);
    await this.queue.publish(c.email, c.historyId);
  }
  async processEmail(email: string, notifiedCursor: string, force = false) {
    const c = await this.db.findEmail(email.toLowerCase());
    if (
      !c ||
      [
        "DISCONNECTED",
        "DISCONNECTED_REVOCATION_PENDING",
        "REAUTH_REQUIRED",
        "PENDING",
      ].includes(c.status)
    )
      return;
    if (c.status === "ACTIVATING")
      throw new AppError("CONNECTION_ACTIVATING", 503);
    historyId(notifiedCursor);
    if (
      !force &&
      c.historyId &&
      BigInt(notifiedCursor) <= BigInt(c.historyId)
    ) {
      await this.push.flush(c.userId);
      return;
    }
    await this.process(c.userId);
    await this.push.flush(c.userId);
  }
  async process(userId: string) {
    await this.locked(userId, async (lease) => {
      const c = lease.connection;
      if (!["ACTIVE", "TEMP_ERROR"].includes(c.status) || !c.historyId) return;
      const deadline = this.clock().getTime() + 450_000;
      let work = 0;
      const check = () => {
        if (this.clock().getTime() > deadline || work >= 500)
          throw new AppError("RETRY_BUDGET", 503);
      };
      try {
        const token = await this.token(lease);
        let finalCursor = c.historyId;
        const save = async (id: string) => {
          check();
          if (!/^[A-Za-z0-9_-]{1,256}$/.test(id))
            throw new AppError("INVALID_MESSAGE_ID");
          const hash = createHash("sha256").update(id).digest("hex");
          if (await this.db.hasEvent(c.userId, hash)) return;
          work++;
          const mail = await this.google.message(token, id);
          if (
            !mail ||
            !mail.labels.includes("INBOX") ||
            mail.labels.some((l) => ["SPAM", "TRASH", "DRAFT"].includes(l)) ||
            !c.startedAt ||
            mail.receivedAt < c.startedAt ||
            mail.receivedAt > this.clock()
          )
            return;
          const analysis = analyze(
            mail.subject,
            mail.text,
            mail.sender,
            mail.replyTo,
          );
          await this.db.insertEvent(lease, {
            userId: c.userId,
            hash,
            receivedAt: mail.receivedAt,
            analysis,
            sender:
              analysis.level === "LOW"
                ? null
                : Array.from(mail.sender).slice(0, 160).join(""),
            subject:
              analysis.level === "LOW"
                ? null
                : Array.from(mail.subject).slice(0, 120).join(""),
            limited: mail.limited,
          });
        };
        try {
          let page: string | undefined;
          const seen = new Set<string>();
          let pages = 0;
          do {
            check();
            if (++pages > 1000 || (page && seen.has(page)))
              throw new AppError("HISTORY_PAGINATION_LIMIT");
            if (page) seen.add(page);
            const result = await this.google.history(token, c.historyId, page);
            for (const id of new Set(result.ids)) await save(id);
            finalCursor = result.historyId;
            page = result.nextPage;
          } while (page);
        } catch (error) {
          if (!(error instanceof AppError && error.code === "GOOGLE_NOT_FOUND"))
            throw error;
          // Capture a barrier BEFORE resync: messages arriving during the scan remain in history.
          const profile = await this.google.profile(token);
          if (profile.email.toLowerCase() !== c.email)
            throw new AppError("GMAIL_ACCOUNT_MISMATCH");
          finalCursor = profile.historyId;
          // Reconciliation uses the connection's server-side week zone supplied at activation.
          const local = weekBounds(c.zone, this.clock());
          const start = new Date(
            Math.max(
              local.start.getTime(),
              c.startedAt?.getTime() ?? local.start.getTime(),
            ),
          );
          let page: string | undefined;
          const seen = new Set<string>();
          let pages = 0;
          do {
            check();
            if (++pages > 1000 || (page && seen.has(page)))
              throw new AppError("RESYNC_PAGINATION_LIMIT");
            if (page) seen.add(page);
            const result = await this.google.list(
              token,
              start,
              local.end,
              page,
            );
            for (const id of new Set(result.ids)) await save(id);
            page = result.nextPage;
          } while (page);
        }
        if (BigInt(historyId(finalCursor)) < BigInt(c.historyId))
          throw new AppError("HISTORY_REGRESSION");
        await this.change(lease, {
          historyId: finalCursor,
          lastSync: this.clock(),
          status: "ACTIVE",
          lastError: null,
        });
      } catch (error) {
        await this.failure(lease, error);
        throw error;
      }
    });
  }
  async renew(userId: string) {
    return this.locked(userId, async (lease) => {
      try {
        const token = await this.token(lease);
        const watch = await this.google.watch(token);
        const initial = !lease.connection.historyId;
        await this.change(lease, {
          expiration: watch.expiration,
          ...(initial
            ? {
                historyId: watch.historyId,
                startedAt: this.clock(),
                lastSync: this.clock(),
                status: "ACTIVE" as const,
              }
            : {}),
          lastError: null,
        });
      } catch (error) {
        await this.failure(lease, error);
        throw error;
      }
    });
  }
  async disconnect(userId: string, deleteEvents: boolean) {
    await this.db.ensure(userId);
    return this.locked(userId, async (lease) => {
      let confirmed = false;
      try {
        if (lease.connection.ciphertext) {
          const refresh = await this.cipher.decrypt(
            userId,
            lease.connection.ciphertext,
          );
          try {
            const access = await this.google.refresh(refresh);
            await this.google.stop(access.accessToken);
          } catch {}
          confirmed = await this.google.revoke(refresh);
        } else
          confirmed = ["PENDING", "DISCONNECTED"].includes(
            lease.connection.status,
          );
      } catch {
        confirmed = false;
      }
      await this.db.erase(lease, deleteEvents, confirmed);
      return { remoteRevocationConfirmed: confirmed };
    });
  }
  async scheduled(kind: "renew" | "reconcile") {
    const connections = await this.db.active();
    let failed = 0;
    for (const c of connections) {
      try {
        if (kind === "renew" || !c.historyId) await this.renew(c.userId);
        if (kind === "reconcile") {
          await this.process(c.userId);
          await this.push.flush(c.userId);
        }
      } catch {
        failed++;
      }
    }
    await this.db.cleanup();
    if (failed) throw new AppError("SCHEDULE_PARTIAL_FAILURE", 503);
  }
}
