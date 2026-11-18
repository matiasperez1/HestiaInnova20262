import pg from "pg";
import { config } from "./config.js";
import { PgStore } from "./store.js";
import { GoogleRest } from "./google.js";
import { GmailService } from "./service.js";
import {
  FcmPush,
  IdentityVerifier,
  KmsCipher,
  PubsubQueue,
} from "./adapters.js";
import { handler, serve } from "./http.js";
export function start(kind: "api" | "worker") {
  const cfg = config();
  const pool = new pg.Pool({
    connectionString: cfg.databaseUrl,
    max: 5,
    connectionTimeoutMillis: 10_000,
    idleTimeoutMillis: 30_000,
    ssl: { rejectUnauthorized: true },
  });
  pool.on("error", () =>
    console.error(JSON.stringify({ code: "DATABASE_CONNECTION_ERROR" })),
  );
  const db = new PgStore(pool);
  const service = new GmailService(
    db,
    new GoogleRest(cfg),
    new KmsCipher(cfg.kmsKey),
    new PubsubQueue(cfg),
    new FcmPush(db, cfg.fcmProject),
    cfg.testing,
  );
  const server = serve(
    cfg.port,
    handler(kind, cfg, service, db, new IdentityVerifier(cfg)),
  );
  process.on("SIGTERM", () => {
    server.close(() => {
      void pool.end();
    });
  });
}
