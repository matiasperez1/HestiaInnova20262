import test from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { analyze } from "../src/fraud.js";
import { weekBounds } from "../src/time.js";
import { parseMessage } from "../src/mime.js";
import { GoogleRest } from "../src/google.js";
const fixtures = JSON.parse(
  readFileSync(
    new URL("../../../shared/email-risk-fixtures.json", import.meta.url),
    "utf8",
  ).replace(/^\uFEFF/, ""),
);
for (const f of fixtures)
  test("shared heuristic: " + f.name, () => {
    const a = analyze(f.subject, f.body, f.sender, f.replyTo);
    assert.equal(a.score, f.score);
    assert.equal(a.level, f.level);
  });
test("local week crosses DST with calendar boundaries", () => {
  const w = weekBounds("America/New_York", new Date("2026-03-08T15:00:00Z"));
  assert.equal(w.start.toISOString(), "2026-03-02T05:00:00.000Z");
  assert.equal(w.end.toISOString(), "2026-03-09T04:00:00.000Z");
  assert.throws(() => weekBounds("invalid", new Date()));
});
test("mime ignores attachments and never executes HTML", () => {
  const m = parseMessage({
    id: "a",
    internalDate: "1794830000000",
    labelIds: ["INBOX"],
    payload: {
      mimeType: "multipart/mixed",
      headers: [{ name: "Subject", value: "Hola" }],
      parts: [
        {
          mimeType: "text/plain",
          body: { data: Buffer.from("Hola").toString("base64url") },
        },
        {
          mimeType: "text/plain",
          filename: "secret.txt",
          body: { data: Buffer.from("Ingresa tu clave").toString("base64url") },
        },
      ],
    },
  });
  assert.equal(m.text.trim(), "Hola");
  assert.equal(m.limited, true);
});
test("history consumes additions only, includes INBOX label additions", async () => {
  const api = new GoogleRest(
    { webClientId: "id", webClientSecret: "secret", topic: "topic" },
    async () =>
      new Response(
        JSON.stringify({
          historyId: "12",
          history: [
            {
              messages: [{ id: "removed" }],
              messagesAdded: [{ message: { id: "a" } }],
              labelsAdded: [
                { message: { id: "b" }, labelIds: ["INBOX"] },
                { message: { id: "c" }, labelIds: ["STARRED"] },
              ],
            },
          ],
        }),
      ),
  );
  const result = await api.history("token", "10");
  assert.deepEqual(result.ids, ["a", "b"]);
});
test("invalid_grant is a reconnect state", async () => {
  const api = new GoogleRest(
    { webClientId: "id", webClientSecret: "secret", topic: "topic" },
    async () => new Response('{"error":"invalid_grant"}', { status: 400 }),
  );
  await assert.rejects(api.refresh("token"), { code: "REAUTH_REQUIRED" });
});

test("native offline code exchange uses existing web credentials and empty redirect",async()=>{
 let posted:URLSearchParams|undefined;const api=new GoogleRest({webClientId:"existing-client",webClientSecret:"synthetic-secret",topic:"topic"},async(url,init)=>{posted=init?.body as URLSearchParams;return new Response(JSON.stringify({access_token:"short",refresh_token:"long",scope:"https://www.googleapis.com/auth/gmail.readonly"}));});
 await api.exchange("one-use");assert.equal(posted?.get("code"),"one-use");assert.equal(posted?.get("redirect_uri"),"");assert.equal(posted?.get("client_id"),"existing-client");
});
