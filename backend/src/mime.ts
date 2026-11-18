import type { Mail } from "./types.js";
type Part = {
  mimeType?: string;
  filename?: string;
  headers?: { name: string; value: string }[];
  parts?: Part[];
  body?: { data?: string; attachmentId?: string };
};
export function parseMessage(json: unknown, forceLimited = false): Mail {
  const value = json as {
    id: string;
    internalDate: string;
    labelIds?: string[];
    payload?: Part;
  };
  const payload = value.payload ?? {};
  const header = (p: Part, n: string) =>
    p.headers?.find((h) => h.name.toLowerCase() === n.toLowerCase())?.value ??
    "";
  let text = "",
    limited = forceLimited,
    visited = 0;
  const visit = (part: Part, depth: number) => {
    if (++visited > 200 || depth > 15 || text.length >= 65536) {
      limited = true;
      return;
    }
    const type = part.mimeType?.toLowerCase() ?? "";
    if (
      part.filename ||
      type === "message/rfc822" ||
      header(part, "Content-Disposition")
        .trimStart()
        .toLowerCase()
        .startsWith("attachment")
    ) {
      limited = true;
      return;
    }
    for (const child of part.parts ?? []) {
      if (visited >= 200) {
        limited = true;
        break;
      }
      visit(child, depth + 1);
    }
    if (!part.parts && !["text/plain", "text/html"].includes(type))
      limited = true;
    if (type === "text/plain" || type === "text/html") {
      const data = part.body?.data;
      if (!data) {
        if (part.body?.attachmentId) limited = true;
        return;
      }
      try {
        if (!/^[A-Za-z0-9_-]*={0,2}$/.test(data) || data.length % 4 === 1)
          throw Error();
        const charset =
          header(part, "Content-Type").match(
            /charset=["']?([^\s;"']+)/i,
          )?.[1] ?? "utf-8";
        let decoder: TextDecoder;
        try {
          decoder = new TextDecoder(charset);
        } catch {
          decoder = new TextDecoder("utf-8");
        }
        const decoded = decoder.decode(Buffer.from(data, "base64url"));
        const remaining = 65536 - text.length;
        if (decoded.length > remaining) limited = true;
        text += decoded.slice(0, remaining);
        if (text.length < 65536) text += " ";
      } catch {
        limited = true;
      }
    }
  };
  visit(payload, 0);
  const receivedAt = new Date(Number(value.internalDate));
  if (!value.id || !Number.isFinite(receivedAt.getTime()))
    throw Error("INVALID_MESSAGE");
  return {
    id: value.id,
    receivedAt,
    labels: value.labelIds ?? [],
    sender: header(payload, "From"),
    subject: header(payload, "Subject"),
    replyTo: header(payload, "Reply-To"),
    text,
    limited: limited || !text.trim(),
  };
}
