import net from "node:net";
import { syncBuiltinESMExports } from "node:module";
/** Defense in depth for the local demo processes: reject egress before DNS/connect. */
export function installLocalNetworkGuard() {
  const connect = net.Socket.prototype.connect;
  const originalFetch = globalThis.fetch;
  net.Socket.prototype.connect = function (this: net.Socket, ...args: any[]) {
    const first = Array.isArray(args[0]) ? args[0][0] : args[0];
    const host =
      typeof first === "object"
        ? (first.host ?? first.hostname ?? "localhost")
        : typeof args[1] === "string"
          ? args[1]
          : "localhost";
    if (!["127.0.0.1", "::1", "localhost"].includes(host) || first?.path)
      throw new Error("DEMO_EXTERNAL_NETWORK_BLOCKED");
    return (connect as any).apply(this, args);
  } as typeof connect;
  globalThis.fetch = ((input: any, init: any) => {
    const url = new URL(
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.href
          : input.url,
    );
    if (!["127.0.0.1", "[::1]", "localhost"].includes(url.hostname))
      return Promise.reject(new Error("DEMO_EXTERNAL_NETWORK_BLOCKED"));
    return originalFetch(input, { ...init, redirect: "error" });
  }) as typeof fetch;
  syncBuiltinESMExports();
  return () => {
    net.Socket.prototype.connect = connect;
    globalThis.fetch = originalFetch;
    syncBuiltinESMExports();
  };
}
