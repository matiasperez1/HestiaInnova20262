import { isIPv4 } from "node:net";
import { networkInterfaces } from "node:os";
import { AppError } from "../types.js";

type LanInterface = { address: string; netmask: string };
const loopbackHosts = ["127.0.0.1", "localhost", "10.0.2.2"];
const loopbackClients = ["127.0.0.1", "::1", "::ffff:127.0.0.1"];

export function isPrivateIPv4(address: string): boolean {
  if (!isIPv4(address)) return false;
  const [a, b] = address.split(".").map(Number) as [number, number];
  return a === 10 || (a === 172 && b >= 16 && b <= 31) || (a === 192 && b === 168);
}

export function demoLanInterface(address: string): LanInterface {
  const iface = Object.values(networkInterfaces()).flat().find((entry) =>
    entry?.family === "IPv4" && !entry.internal && entry.address === address,
  );
  if (!isPrivateIPv4(address) || !iface || !isIPv4(iface.netmask) || iface.netmask === "0.0.0.0")
    throw new AppError("DEMO_LAN_IP_NOT_ASSIGNED", 500);
  return { address, netmask: iface.netmask };
}

function ipv4Bits(address: string): number {
  return address.split(".").reduce((bits, octet) => (bits << 8) | Number(octet), 0);
}

export function permitsDemoClient(remote: string, host: string, port: number, lan?: LanInterface): boolean {
  const matchesHost = (address: string) => host === `${address}:${port}` || (port === 80 && host === address);
  if (!lan) return loopbackClients.includes(remote) && loopbackHosts.some(matchesHost);
  const client = remote.replace(/^::ffff:/, "");
  const mask = ipv4Bits(lan.netmask);
  return matchesHost(lan.address) && isPrivateIPv4(client) &&
    (ipv4Bits(client) & mask) === (ipv4Bits(lan.address) & mask);
}
