import type { Analysis } from "./types.js";
// Port of domain/gmail/EmailRiskEngine.kt. Shared fixtures are checked by both runtimes.
export function analyze(
  subject: string,
  body: string,
  sender = "",
  replyTo = "",
): Analysis {
  const text = `${subject} ${body}`
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase();
  const reasons: string[] = [];
  let score = 0;
  const add = (n: number, r: string) => {
    score += n;
    reasons.push(r);
  };
  const credentials = text
    .split(/[.!?;\n]/)
    .some(
      (s) =>
        !/(?:no|nunca|jamas)\s+(?:\w+\s+){0,3}(?:compart|entreg|envi|ingres)/.test(
          s,
        ) &&
        /(?:ingresa|envia|comparte|entrega|confirma|valida|actualiza).{0,80}(?:clave|contrasena|codigo|\bpin\b|token)/.test(
          s,
        ),
    );
  if (credentials) add(35, "Solicita claves o códigos personales.");
  if (
    /urgente|hoy mismo|inmediatamente|ultimo plazo|en 24 horas|bloquearemos|suspenderemos/.test(
      text,
    )
  )
    add(15, "Presiona para actuar con urgencia.");
  if (
    /(?:transfiere|deposita|paga).{0,100}(?:activar|liberar|premio|credito|adelantado)/.test(
      text,
    )
  )
    add(30, "Pide un pago para liberar un beneficio.");
  const urls = Array.from(
    `${subject} ${body}`.matchAll(/https?:\/\/[^\s<>"']+/gi),
    (m) => m[0],
  ).slice(0, 100);
  const hosts = urls.flatMap((u) => {
    try {
      // Preserve literal authorities as java.net.URI does: WHATWG URL would canonicalize
      // 127.1/hexadecimal hosts and silently change the Android heuristic's score.
      const authority = u
        .match(/^https?:\/\/([^/?#]*)/i)?.[1]
        ?.split("@")
        .at(-1);
      if (!authority || /[\s\\]/.test(authority)) return [];
      const host = authority.startsWith("[")
        ? authority.slice(0, authority.indexOf("]") + 1)
        : authority.split(":")[0];
      return host ? [host.toLowerCase()] : [];
    } catch {
      return [];
    }
  });
  const shortened = new Set([
    "bit.ly",
    "tinyurl.com",
    "t.co",
    "cutt.ly",
    "shorturl.at",
    "is.gd",
  ]);
  if (hosts.some((h) => shortened.has(h)))
    add(15, "Incluye un enlace acortado que oculta el destino.");
  if (urls.some((u) => u.toLowerCase().startsWith("http://")))
    add(15, "Incluye un enlace sin conexión HTTPS.");
  if (hosts.some((h) => /^[0-9]+(?:\.[0-9]+){3}$/.test(h) || h.startsWith("[")))
    add(20, "Un enlace utiliza una dirección numérica.");
  if (
    hosts.some(
      (h) =>
        h.includes("xn--") ||
        Array.from(h).some((c) => c.codePointAt(0)! > 127),
    )
  )
    add(15, "Un dominio requiere revisar cuidadosamente sus caracteres.");
  const domain = (s: string) =>
    s
      .match(/@([a-zA-Z0-9.-]+)/)?.[1]
      ?.replace(/\.$/, "")
      .toLowerCase();
  const mismatch = Boolean(
    domain(sender) && domain(replyTo) && domain(sender) !== domain(replyTo),
  );
  if (mismatch) add(15, "La dirección de respuesta usa otro dominio.");
  const institution =
    /\b(?:banco|bancoestado|santander|bci|tesoreria|sii|chileatiende|sernac|institucion)\b/.test(
      text,
    );
  const unsafe =
    urls.some((u) => u.toLowerCase().startsWith("http://")) ||
    hosts.some((h) => shortened.has(h)) ||
    mismatch;
  if (institution && credentials && unsafe)
    add(
      20,
      "Se presenta como una institución y pide datos mediante un canal que requiere verificar su identidad.",
    );
  score = Math.min(score, 100);
  return {
    score,
    level: score >= 60 ? "HIGH" : score >= 25 ? "MEDIUM" : "LOW",
    reasons,
  };
}
