import pg from "pg";
import { demoConfig } from "./config.js";
import { DemoStore, personas } from "./store.js";
export async function demoDatabase() {
  const cfg = demoConfig();
  const pool = new pg.Pool({
    connectionString: cfg.connectionString,
    max: 5,
    connectionTimeoutMillis: 3000,
  });
  pool.on("error", () => console.error("LOCAL_DEMO_DATABASE_UNAVAILABLE"));
  const db = new DemoStore(pool);
  for (const user of Object.values(personas)) await db.activate(user);
  return { cfg, pool, db };
}
