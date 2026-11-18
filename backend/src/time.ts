import { DateTime, IANAZone } from "luxon";
import { AppError } from "./types.js";
export function weekBounds(zone: string, now: Date) {
  if (zone.length > 80 || !IANAZone.isValidZone(zone)) throw new AppError("INVALID_TIMEZONE", 400);
  const local = DateTime.fromJSDate(now, { zone });
  if (!local.isValid) throw new AppError("INVALID_TIMEZONE", 400);
  const start = local.startOf("day").minus({ days: local.weekday - 1 });
  return {
    start: start.toJSDate(),
    end: start.plus({ weeks: 1 }).toJSDate(),
    zone,
    startDate: start.toISODate()!,
    endDate: start.plus({ days: 6 }).toISODate()!,
  };
}
export const validId = (s: string) =>
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i.test(
    s,
  );
export function historyId(s: string) {
  if (!/^[0-9]{1,30}$/.test(s)) throw new AppError("INVALID_HISTORY_ID", 400);
  return s;
}
