import { demoConfig } from "./config.js";
import { installLocalNetworkGuard } from "./network-guard.js";
const cfg = demoConfig();
installLocalNetworkGuard();
const args = process.argv.slice(2);
const value = (name: string, other: string) =>
  args.includes(name) ? (args[args.indexOf(name) + 1] ?? other) : other;
const base = `http://127.0.0.1:${cfg.apiPort}`;
async function post(path: string, data: any, token?: string) {
  const r = await fetch(base + path, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
      "X-Hestia-Demo": "true",
      ...(token ? { Authorization: "Bearer " + token } : {}),
    },
    body: JSON.stringify(data),
    signal: AbortSignal.timeout(5000),
  });
  const result = (await r.json()) as any;
  if (!r.ok) throw new Error(result.error ?? "DEMO_UNAVAILABLE");
  return result;
}
try {
  const session = await post("/demo/session", {
    persona: value("--persona", "alice"),
  });
  const path = args.includes("--repeat")
    ? "/demo/repeat"
    : args.includes("--clear")
      ? "/demo/clear"
      : "/demo/emit";
  const result = await post(
    path,
    {
      scenario: value("--scenario", "high-risk"),
      retryOnce: args.includes("--retry-once"),
      confirm: args.includes("--confirm"),
    },
    session.token,
  );
  console.log(JSON.stringify(result));
} catch {
  console.error(
    "DEMO LOCAL request failed. Start the local API/Worker; check scenario/persona and explicit confirmation for cleanup.",
  );
  process.exitCode = 1;
}
