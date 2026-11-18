import { AppError } from "../types.js";
import type { DemoStore } from "./store.js";

const fraudCategories = ["POSSIBLE_FRAUD", "IMPERSONATION", "REQUEST_FOR_MONEY", "REQUEST_FOR_CODES", "OTHER"];
const categories = [...fraudCategories, "UNWANTED_ADS", "HARASSMENT", "OTHER_GENERAL"];

function syntheticNumber(value: unknown): string {
  if (typeof value !== "string" || !/^20255501[0-9]{2}$/.test(value))
    throw new AppError("INVALID_SYNTHETIC_CALL_NUMBER", 400);
  return value;
}

export class DemoCallReports {
  constructor(private readonly db: DemoStore, private readonly threshold: number) {}

  async snapshot(owner: string) {
    return this.db.tx(async (client) => {
      const { rows } = await client.query(
        `WITH totals AS (
             SELECT number, category, count(*)::int AS total, bool_or(user_id = $1) AS own
             FROM demo.call_report GROUP BY number, category
         ) SELECT number, sum(total)::int AS count,
                  coalesce(sum(total) FILTER (WHERE category = ANY($2::text[])), 0)::int AS "fraudCount",
                  jsonb_object_agg(category, total) AS "categoryCounts",
                  max(category) FILTER (WHERE own) AS "ownCategory"
           FROM totals GROUP BY number ORDER BY number`,
        [owner, fraudCategories],
      );
      return { threshold: this.threshold, entries: rows };
    });
  }

  async save(owner: string, body: Record<string, unknown> | null) {
    if (body?.consent !== true) throw new AppError("CALL_REPORT_CONSENT_REQUIRED", 400);
    const number = syntheticNumber(body.number);
    const category = body.category;
    if (typeof category !== "string" || !categories.includes(category))
      throw new AppError("INVALID_CALL_REPORT_CATEGORY", 400);
    await this.db.tx(async (client) => {
      if (body.replace === true) {
        const result = await client.query(
          "UPDATE demo.call_report SET category = $3 WHERE number = $1 AND user_id = $2", [number, owner, category],
        );
        if (!result.rowCount) throw new AppError("CALL_REPORT_NOT_FOUND", 404);
        return;
      }
      const result = await client.query(
        `INSERT INTO demo.call_report (number, user_id, category) VALUES ($1, $2, $3)
         ON CONFLICT (number, user_id) DO NOTHING`, [number, owner, category],
      );
      if (result.rowCount) return;
      const existing = await client.query(
        "SELECT category FROM demo.call_report WHERE number = $1 AND user_id = $2", [number, owner],
      );
      if (existing.rows[0]?.category !== category) throw new AppError("CALL_REPORT_ALREADY_EXISTS", 409);
    });
  }

  async withdraw(owner: string, value: unknown) {
    const number = syntheticNumber(value);
    await this.db.tx((client) => client.query(
      "DELETE FROM demo.call_report WHERE number = $1 AND user_id = $2", [number, owner],
    ));
  }
}
