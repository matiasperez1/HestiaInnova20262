import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { resolve } from "node:path";
import { AppError } from "../types.js";
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
  for (const key of ["apiPort", "databasePort"])
    if (
      !Number.isInteger(value[key]) ||
      value[key] < 1024 ||
      value[key] > 65535
    )
      throw new AppError("INVALID_DEMO_PORT", 500);
  return {
    ...value,
    dataDir: resolve(backendRoot, ".local-demo"),
    connectionString: `postgresql://postgres@127.0.0.1:${value.databasePort}/hestia_demo`,
  };
}
