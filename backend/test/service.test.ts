import test from "node:test";
import assert from "node:assert/strict";
import { randomUUID } from "node:crypto";
import { GmailService } from "../src/service.js";
import {
  AppError,
  type Connection,
  type Event,
  type Google,
  type Lease,
  type Store,
} from "../src/types.js";
const now = new Date("2026-11-16T15:00:00Z");
const uid = "ac3815de-2753-497c-8e9f-02e56f034bd6";
class Memory implements Store {
  c: Connection = {
    id: randomUUID(),
    userId: uid,
    zone: "America/Santiago",
    email: "a@example.org",
    ciphertext: "cipher",
    historyId: "10",
    expiration: new Date(now.getTime() + 86400000),
    startedAt: new Date("2026-11-16T12:00:00Z"),
    tokenCreatedAt: now,
    status: "ACTIVE",
    lastSync: null,
    lastError: null,
  };
  events = new Map<string, Event>();
  busy = false;
  async ensure() {}
  async find() {
    return this.c;
  }
  async findEmail(email: string) {
    return this.c.email === email ? this.c : null;
  }
  async acquire() {
    if (this.busy) return null;
    this.busy = true;
    return { connection: { ...this.c }, token: "lease" };
  }
  async renew() {
    return true;
  }
  async release() {
    this.busy = false;
  }
  async update(l: Lease, v: Partial<Connection>) {
    Object.assign(this.c, v);
  }
  async hasEvent(u: string, h: string) {
    return this.events.has(h);
  }
  async insertEvent(l: Lease, e: Event) {
    if (this.events.has(e.hash)) return false;
    this.events.set(e.hash, e);
    return true;
  }
  async active() {
    return [this.c];
  }
  async cleanup() {}
  async erase(l: Lease, del: boolean, ok: boolean) {
    Object.assign(this.c, {
      status: ok ? "DISCONNECTED" : "DISCONNECTED_REVOCATION_PENDING",
      ciphertext: null,
      historyId: null,
    });
    if (del) this.events.clear();
  }
}
function setup() {
  const db = new Memory();
  let historyCalls = 0,
    messageCalls = 0,
    pushes = 0;
  const g: Google = {
    exchange: async () => ({ accessToken: "access", refreshToken: "refresh" }),
    refresh: async () => ({ accessToken: "access" }),
    profile: async () => ({ email: "a@example.org", historyId: "20" }),
    watch: async () => ({
      historyId: "20",
      expiration: new Date(now.getTime() + 86400000),
    }),
    stop: async () => {},
    revoke: async () => true,
    history: async () => {
      historyCalls++;
      return { ids: ["m1", "m1"], historyId: "20" };
    },
    list: async () => ({ ids: ["m1"] }),
    message: async (id) => {
      messageCalls++;
      return {
        id,
        receivedAt: new Date("2026-11-16T14:00:00Z"),
        labels: ["INBOX"],
        sender: "a@b.cl",
        replyTo: "",
        subject: "Banco urgente",
        text: "Ingresa tu clave en http://bit.ly/test",
        limited: false,
      };
    },
  };
  const service = new GmailService(
    db,
    g,
    { encrypt: async (u, p) => "cipher:" + p, decrypt: async () => "refresh" },
    { publish: async () => {} },
    {
      flush: async () => {
        pushes++;
      },
    },
    true,
    () => now,
  );
  return {
    db,
    g,
    service,
    counts: () => ({ historyCalls, messageCalls, pushes }),
  };
}
test("initial watch starts now and never scans historical inbox", async () => {
  const x = setup();
  x.db.c.status = "PENDING";
  await x.service.connect(
    { id: uid, email: "a@example.org" },
    "single-use",
    "America/Santiago",
  );
  assert.equal(x.db.c.historyId, "20");
  assert.equal(x.db.c.startedAt?.toISOString(), now.toISOString());
  assert.equal(x.counts().historyCalls, 0);
  assert.equal(x.counts().messageCalls, 0);
});
test("rejects mismatched authenticated mailbox", async () => {
  const x = setup();
  x.db.c.status = "PENDING";
  await assert.rejects(
    x.service.connect({ id: uid, email: "other@example.org" }, "code", "UTC"),
    { code: "GMAIL_ACCOUNT_MISMATCH" },
  );
  assert.equal(x.db.c.status, "TEMP_ERROR");
});
test("missing refresh token requires explicit reconsent", async () => {
  const x = setup();
  x.db.c.status = "PENDING";
  x.g.exchange = async () => ({ accessToken: "access" });
  await assert.rejects(
    x.service.connect({ id: uid, email: "a@example.org" }, "code", "UTC"),
    { code: "REFRESH_TOKEN_REQUIRED" },
  );
  assert.equal(x.db.c.status, "REAUTH_REQUIRED");
});
test("duplicates and out of order events never duplicate records", async () => {
  const x = setup();
  await x.service.processEmail("a@example.org", "20");
  await x.service.processEmail("a@example.org", "19");
  assert.equal(x.db.events.size, 1);
  assert.equal(x.counts().messageCalls, 1);
  assert.equal(x.db.c.historyId, "20");
});
test("cursor preserved after partial failure, successful events retained for retry", async () => {
  const x = setup();
  x.g.history = async (t, c, p) => {
    if (p) throw new AppError("GOOGLE_QUOTA");
    return { ids: ["m1"], historyId: "20", nextPage: "next" };
  };
  await assert.rejects(x.service.process(uid));
  assert.equal(x.db.c.historyId, "10");
  assert.equal(x.db.events.size, 1);
  x.g.history = async () => ({ ids: ["m1", "m2"], historyId: "21" });
  await x.service.process(uid);
  assert.equal(x.db.events.size, 2);
  assert.equal(x.counts().messageCalls, 2);
  assert.equal(x.db.c.historyId, "21");
});
test("watch renewal must not advance processed history", async () => {
  const x = setup();
  await x.service.renew(uid);
  assert.equal(x.db.c.historyId, "10");
});
test("expired history resync uses barrier captured before scan", async () => {
  const x = setup();
  x.g.history = async () => {
    throw new AppError("GOOGLE_NOT_FOUND", 404);
  };
  await x.service.process(uid);
  assert.equal(x.db.c.historyId, "20");
  assert.equal(x.db.events.size, 1);
});
test("current SPAM/TRASH/DRAFT or removed INBOX not persisted", async () => {
  for (const labels of [
    ["SPAM", "INBOX"],
    ["TRASH", "INBOX"],
    ["DRAFT", "INBOX"],
    ["SENT"],
  ]) {
    const x = setup();
    const original = x.g.message;
    x.g.message = async (t, id) => ({ ...(await original(t, id))!, labels });
    await x.service.process(uid);
    assert.equal(x.db.events.size, 0);
  }
});
test("concurrent processing rejected by connection lease", async () => {
  const x = setup();
  x.db.busy = true;
  await assert.rejects(x.service.process(uid), { code: "CONNECTION_BUSY" });
});
test("invalid grant produces reconnect, keeps cursor", async () => {
  const x = setup();
  x.g.refresh = async () => {
    throw new AppError("REAUTH_REQUIRED", 409);
  };
  await assert.rejects(x.service.process(uid));
  assert.equal(x.db.c.status, "REAUTH_REQUIRED");
  assert.equal(x.db.c.historyId, "10");
});
test("testing refresh grant expires after seven days", async () => {
  const x = setup();
  x.db.c.tokenCreatedAt = new Date(now.getTime() - 7 * 86400000);
  await assert.rejects(x.service.process(uid), { code: "REAUTH_REQUIRED" });
});
test("disconnect erases credentials even if remote revoke fails", async () => {
  const x = setup();
  x.g.revoke = async () => false;
  const result = await x.service.disconnect(uid, true);
  assert.equal(result.remoteRevocationConfirmed, false);
  assert.equal(x.db.c.ciphertext, null);
  assert.equal(x.db.c.status, "DISCONNECTED_REVOCATION_PENDING");
});

test("repeating local disconnect never upgrades pending remote revocation", async () => {
 const x=setup();x.db.c.ciphertext=null;x.db.c.status="DISCONNECTED_REVOCATION_PENDING";
 const result=await x.service.disconnect(uid,true);assert.equal(result.remoteRevocationConfirmed,false);assert.equal(x.db.c.status,"DISCONNECTED_REVOCATION_PENDING");
});
