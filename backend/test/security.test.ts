import test from "node:test";
import assert from "node:assert/strict";
import { createServer } from "node:http";
import type { AddressInfo } from "node:net";
import { IdentityVerifier, pushData, KmsCipher } from "../src/adapters.js";
import { handler } from "../src/http.js";
import { AppError } from "../src/types.js";
import type { Config } from "../src/config.js";
import type { PgStore } from "../src/store.js";
import type { GmailService } from "../src/service.js";
import type { OAuth2Client } from "google-auth-library";
const cfg = {
  workerAudience: "https://worker.run.app",
  pubsubEmail: "pubsub@example.org",
  schedulerEmail: "scheduler@example.org",
  subscription: "projects/test/subscriptions/worker",
  supabaseUrl: "https://supabase.example.org",
  publishableKey: "public",
} as Config;
test("OIDC rejects wrong audience/email/verification, accepts configured identity", async () => {
  const payload: any = {
    email: cfg.pubsubEmail,
    email_verified: true,
    sub: "123",
    iss: "https://accounts.google.com",
  };
  const google = {
    verifyIdToken: async (options: any) => {
      assert.equal(options.audience, cfg.workerAudience);
      return { getPayload: () => payload };
    },
  } as unknown as Pick<OAuth2Client, "verifyIdToken">;
  const identity = new IdentityVerifier(cfg, google);
  await identity.oidc("Bearer synthetic", "pubsub");
  await assert.rejects(identity.oidc("Bearer synthetic", "scheduler"), {
    code: "FORBIDDEN",
  });
  payload.email_verified = false;
  await assert.rejects(identity.oidc("Bearer synthetic", "pubsub"), {
    code: "FORBIDDEN",
  });
  await assert.rejects(identity.oidc(undefined, "pubsub"), {
    code: "UNAUTHENTICATED",
  });
});
test("Supabase verifies token remotely and supplies owner, never client email", async () => {
  const identity = new IdentityVerifier(cfg, undefined, async (url, init) => {
    assert.equal(url, "https://supabase.example.org/auth/v1/user");
    assert.equal((init?.headers as any).Authorization, "Bearer synthetic");
    return new Response(
      JSON.stringify({
        id: "server-owner",
        email: "server@example.org",
        email_confirmed_at: "now",
        identities: [{ provider: "google" }],
      }),
    );
  });
  assert.deepEqual(await identity.user("Bearer synthetic"), {
    id: "server-owner",
    email: "server@example.org",
  });
  await assert.rejects(identity.user(undefined), { code: "UNAUTHENTICATED" });
});
test("HIGH push data contains opaque identifiers only", () => {
  const data = pushData("26e06ced-e116-43cf-8486-49c49bfbd061", "owner");
  assert.deepEqual(Object.keys(data).sort(), ["accountTag", "eventId", "type"]);
  assert.equal(data.accountTag.length, 64);
  assert.equal(JSON.stringify(data).includes("owner"), false);
});
async function server(
  kind: "api" | "worker",
  run: (base: string, calls: any[]) => Promise<void>,
) {
  const calls: any[] = [];
  const service = {
    processEmail: async (...v: any[]) => {
      calls.push(v);
    },
    connect: async (...v: any[]) => {
      calls.push(v);
      return {};
    },
    requestSync: async () => {},
    scheduled: async () => {},
  } as unknown as GmailService;
  const db = {
    find: async () => null,
    throttle: async () => {},
  } as unknown as PgStore;
  const identity = {
    user: async (header: string) => {
      if (header !== "Bearer allowed")
        throw new AppError("UNAUTHENTICATED", 401);
      return { id: "server-owner", email: "server@example.org" };
    },
    oidc: async (header: string) => {
      if (header !== "Bearer allowed") throw new AppError("FORBIDDEN", 403);
    },
  } as unknown as IdentityVerifier;
  const s = createServer(handler(kind, cfg, service, db, identity));
  await new Promise<void>((r) => s.listen(0, "127.0.0.1", r));
  try {
    await run(`http://127.0.0.1:${(s.address() as AddressInfo).port}`, calls);
  } finally {
    await new Promise<void>((r) => s.close(() => r()));
  }
}
test("API refuses unauthenticated request and does not mutate via GET", async () =>
  server("api", async (base, calls) => {
    assert.equal((await fetch(base + "/v1/gmail/status")).status, 401);
    assert.equal(
      (
        await fetch(base + "/v1/gmail/connect", {
          headers: { Authorization: "Bearer allowed" },
        })
      ).status,
      404,
    );
    assert.equal(calls.length, 0);
  }));
test("API connection binds to server owner and validated time zone", async () =>
  server("api", async (base, calls) => {
    const r = await fetch(base + "/v1/gmail/connect", {
      method: "POST",
      headers: { Authorization: "Bearer allowed" },
      body: JSON.stringify({
        serverAuthCode: "one-use",
        timeZone: "America/Santiago",
        userId: "attacker",
        email: "attacker@example.org",
      }),
    });
    assert.equal(r.status, 200);
    assert.deepEqual(calls[0], [
      { id: "server-owner", email: "server@example.org" },
      "one-use",
      "America/Santiago",
    ]);
  }));
test("worker rejects public, wrong subscription and malformed push", async () =>
  server("worker", async (base, calls) => {
    const path = base + "/webhooks/pubsub/gmail";
    assert.equal(
      (await fetch(path, { method: "POST", body: "{}" })).status,
      403,
    );
    assert.equal(
      (
        await fetch(path, {
          method: "POST",
          headers: { Authorization: "Bearer allowed" },
          body: JSON.stringify({
            subscription: "wrong",
            message: { data: "e30=" },
          }),
        })
      ).status,
      400,
    );
    assert.equal(calls.length, 0);
  }));
test("worker acknowledges valid at-least-once notification", async () =>
  server("worker", async (base, calls) => {
    const r = await fetch(base + "/webhooks/pubsub/gmail", {
      method: "POST",
      headers: { Authorization: "Bearer allowed" },
      body: JSON.stringify({
        subscription: cfg.subscription,
        message: {
          data: Buffer.from(
            JSON.stringify({ emailAddress: "a@example.org", historyId: "123" }),
          ).toString("base64"),
        },
      }),
    });
    assert.equal(r.status, 200);
    assert.deepEqual(calls[0], ["a@example.org", "123", false]);
  }));

for(const wire of ["binary","base64"])test("KMS adapter binds owner AAD and decodes "+wire+" correctly",async()=>{
 const bytes=Buffer.from([0,255,34,128,7]);const owner="ac3815de-2753-497c-8e9f-02e56f034bd6";
 const transport={encrypt:async(r:any)=>{assert.equal(r.additionalAuthenticatedData.toString(),"hestia:gmail:"+owner);assert.equal(r.plaintext.toString(),"synthetic-refresh");return[{ciphertext:wire==="binary"?bytes:bytes.toString("base64")}];},decrypt:async(r:any)=>{assert.deepEqual(r.ciphertext,bytes);assert.equal(r.additionalAuthenticatedData.toString(),"hestia:gmail:"+owner);return[{plaintext:wire==="binary"?Buffer.from("synthetic-refresh"):Buffer.from("synthetic-refresh").toString("base64")}];}};
 const cipher=new KmsCipher("projects/synthetic/locations/test/keyRings/test/cryptoKeys/test",transport as any);const encrypted=await cipher.encrypt(owner,"synthetic-refresh");assert.equal(encrypted,bytes.toString("base64"));assert.equal(await cipher.decrypt(owner,encrypted),"synthetic-refresh");
});
