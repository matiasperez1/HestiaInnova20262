import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { AppError } from "../types.js";
import { isPrivateIPv4 } from "./listen-policy.js";
export const backendRoot = fileURLToPath(new URL("../../../", import.meta.url));
export function demoConfig(env: NodeJS.ProcessEnv = process.env) {
  if (env.LOCAL_DEMO !== "true")
    throw new AppError("LOCAL_DEMO_EXPLICIT_OPT_IN_REQUIRED", 500);
  if (env.NODE_ENV === "production" || env.K_SERVICE || env.CLOUD_RUN_JOB)
    throw new AppError("DEMO_FORBIDDEN_IN_PRODUCTION", 500);
  const value = JSON.parse(
    readFileSync(resolve(backendRoot, "demo.config.json"), "utf8").replace(
      /^\uFEFF/,
      "",
    ),
  );
  const apiPortText = env.LOCAL_DEMO_API_PORT;
  const apiPort = apiPortText === undefined ? value.apiPort : /^\d{4,5}$/.test(apiPortText) ? Number(apiPortText) : NaN;
  for (const port of [apiPort, value.databasePort])
    if (
      !Number.isInteger(port) || port < 1024 || port > 65535
    )
      throw new AppError("INVALID_DEMO_PORT", 500);
  const lanIp = env.LOCAL_DEMO_LAN_IP;
  if (lanIp !== undefined && !isPrivateIPv4(lanIp))
    throw new AppError("INVALID_DEMO_LAN_IP", 500);
  const callReportThreshold: unknown = value.callReportThreshold;
  if (typeof callReportThreshold !== "number" || !Number.isInteger(callReportThreshold) || callReportThreshold < 2 || callReportThreshold > 2_147_483_647)
    throw new AppError("INVALID_CALL_REPORT_THRESHOLD", 500);
  return {
    ...value,
    apiPort,
    lanIp,
    callReportThreshold,
    dataDir: resolve(backendRoot, ".local-demo"),
    connectionString: `postgresql://postgres@127.0.0.1:${value.databasePort}/hestia_demo`,
  };
}
