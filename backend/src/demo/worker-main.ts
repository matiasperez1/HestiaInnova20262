import { setTimeout } from "node:timers/promises";
import { installLocalNetworkGuard } from "./network-guard.js";
import { demoDatabase } from "./runtime.js";
import { workOnce } from "./worker.js";
installLocalNetworkGuard();
const { cfg, pool, db } = await demoDatabase();
let running = true;
for (const signal of ["SIGINT", "SIGTERM"] as const)
  process.on(signal, () => {
    running = false;
  });
console.log("DEMO LOCAL Worker: ready");
while (running) {
  try {
    const worked = await workOnce(db);
    if (!worked) await setTimeout(cfg.workerIntervalMs);
  } catch {
    console.error("LOCAL_DEMO_WORKER_TEMPORARY_ERROR");
    await setTimeout(cfg.workerIntervalMs);
  }
}
await pool.end();
