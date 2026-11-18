import { createServer } from "node:http";
import { installLocalNetworkGuard } from "./network-guard.js";
import { demoDatabase } from "./runtime.js";
import { demoHandler } from "./http.js";
installLocalNetworkGuard();
const { cfg, pool, db } = await demoDatabase();
const server = createServer(demoHandler(db, cfg.callReportThreshold));
server.listen(cfg.apiPort, "127.0.0.1", () =>
  console.log(`DEMO LOCAL API: http://127.0.0.1:${cfg.apiPort}`),
);
for (const signal of ["SIGINT", "SIGTERM"] as const)
  process.on(signal, () =>
    server.close(() => {
      void pool.end();
    }),
  );
