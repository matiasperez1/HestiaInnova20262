import { createServer } from "node:http";
import { installLocalNetworkGuard } from "./network-guard.js";
import { demoDatabase } from "./runtime.js";
import { demoHandler } from "./http.js";
import { demoLanInterface } from "./listen-policy.js";
import { demoConfig } from "./config.js";
import { AppError } from "../types.js";

const cfg = demoConfig();
let lan: ReturnType<typeof demoLanInterface> | undefined;
try {
  if (cfg.lanIp) lan = demoLanInterface(cfg.lanIp);
} catch (error) {
  if (!(error instanceof AppError)) throw error;
  console.error(error.code);
  process.exit(1);
}
installLocalNetworkGuard();
const { pool, db } = await demoDatabase();
const servers = [createServer(demoHandler(db, cfg.callReportThreshold))];
if (lan) servers.push(createServer(demoHandler(db, cfg.callReportThreshold, lan)));
const addresses = lan ? ["127.0.0.1", lan.address] : ["127.0.0.1"];
let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  await Promise.all(servers.map((server) => new Promise<void>((done) => server.close(() => done()))));
  await pool.end();
}
for (const signal of ["SIGINT", "SIGTERM"] as const) process.on(signal, () => void stop());
try {
  // Bind serially so a failed LAN bind also closes a successfully bound local listener.
  for (const [index, server] of servers.entries()) {
    await new Promise<void>((done, reject) => {
      server.once("error", reject);
      server.listen(cfg.apiPort, addresses[index]!, () => {
        server.off("error", reject);
        done();
      });
    });
  }
  for (const address of addresses) console.log(`DEMO LOCAL API: http://${address}:${cfg.apiPort}`);
} catch {
  console.error("DEMO_LOCAL_API_BIND_FAILED");
  process.exitCode = 1;
  await stop();
}
