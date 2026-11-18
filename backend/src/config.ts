import { AppError } from "./types.js";
export type Config = {
  project: string;
  topic: string;
  subscription: string;
  workerAudience: string;
  pubsubEmail: string;
  schedulerEmail: string;
  supabaseUrl: string;
  publishableKey: string;
  databaseUrl: string;
  kmsKey: string;
  webClientId: string;
  webClientSecret: string;
  fcmProject: string;
  testing: boolean;
  port: number;
};
export function config(env: NodeJS.ProcessEnv = process.env): Config {
  if (env.LOCAL_DEMO === "true")
    throw new AppError("DEMO_FORBIDDEN_IN_PRODUCTION_ENTRYPOINT", 500);
  const required = (key: string) => {
    const value = env[key]?.trim();
    if (!value) throw new AppError("MISSING_" + key, 500);
    return value;
  };
  const https = (key: string) => {
    const value = required(key);
    const url = new URL(value);
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      url.search ||
      url.hash
    )
      throw new AppError("INVALID_" + key, 500);
    return value.replace(/\/$/, "");
  };
  const project = required("GCP_PROJECT_ID");
  const topic = required("GMAIL_TOPIC");
  if (!topic.startsWith(`projects/${project}/topics/`))
    throw new AppError("TOPIC_PROJECT_MISMATCH", 500);
  return {
    project,
    topic,
    subscription: required("GMAIL_SUBSCRIPTION"),
    workerAudience: https("WORKER_AUDIENCE"),
    pubsubEmail: required("PUBSUB_INVOKER_EMAIL"),
    schedulerEmail: required("SCHEDULER_INVOKER_EMAIL"),
    supabaseUrl: https("SUPABASE_URL"),
    publishableKey: required("SUPABASE_PUBLISHABLE_KEY"),
    databaseUrl: required("DATABASE_URL"),
    kmsKey: required("KMS_KEY_NAME"),
    webClientId: required("GOOGLE_WEB_CLIENT_ID"),
    webClientSecret: required("GOOGLE_WEB_CLIENT_SECRET"),
    fcmProject: env.FCM_PROJECT_ID || project,
    testing: env.OAUTH_TESTING_MODE !== "false",
    port: Number(env.PORT || 8080),
  };
}
