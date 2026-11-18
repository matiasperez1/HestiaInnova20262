import { GmailService } from "../service.js";
import { AppError } from "../types.js";
import { historyId } from "../time.js";
import { DemoStore } from "./store.js";
import {
  LocalDemoMailbox,
  LocalDemoNotificationProvider,
  LocalPubSub,
  noDemoCipher,
} from "./adapters.js";
export async function workOnce(db: DemoStore) {
  await db.heartbeat();
  const job = await db.claim();
  if (!job) return false;
  try {
    const identity = await db.tx(
      async (c) =>
        (
          await c.query("SELECT email FROM demo.identity WHERE user_id=$1", [
            job.user_id,
          ])
        ).rows[0],
    );
    const envelope = job.envelope;
    if (envelope.subscription !== "local-demo")
      throw new AppError("INVALID_DEMO_ENVELOPE");
    const data = JSON.parse(
      Buffer.from(envelope.message.data, "base64").toString("utf8"),
    );
    if (data.emailAddress !== identity?.email)
      throw new AppError("DEMO_OWNER_MISMATCH");
    const cursor = historyId(String(data.historyId));
    const before = await db.find(job.user_id);
    const duplicate =
      !!before?.historyId &&
      BigInt(cursor) <= BigInt(before.historyId) &&
      !envelope.message.attributes?.hestiaManual;
    const service = new GmailService(
      db,
      new LocalDemoMailbox(
        db,
        job.user_id,
        job.fail_once && job.attempts === 1,
      ),
      noDemoCipher,
      new LocalPubSub(db),
      new LocalDemoNotificationProvider(db),
      false,
      () => new Date(),
      { access: async () => "" },
    );
    await service.processEmail(
      data.emailAddress,
      cursor,
      envelope.message.attributes?.hestiaManual === "true",
    );
    await db.finish(job, undefined, duplicate);
    console.log(
      JSON.stringify({
        mode: "LOCAL_DEMO",
        worker: "processed",
        eventId: job.event_id,
        duplicate,
        attempt: job.attempts,
      }),
    );
  } catch (e) {
    const code = e instanceof AppError ? e.code : "DEMO_PROCESSING_ERROR";
    await db.finish(job, code);
    console.error(
      JSON.stringify({
        mode: "LOCAL_DEMO",
        worker: "retry",
        code,
        eventId: job.event_id,
      }),
    );
  }
  return true;
}
