import type { Google, HistoryPage } from "./types.js";
import { AppError } from "./types.js";
import type { Config } from "./config.js";
import { parseMessage } from "./mime.js";
import { historyId } from "./time.js";
class Oversize extends Error {}
export class GoogleRest implements Google {
  constructor(
    private cfg: Pick<Config, "webClientId" | "webClientSecret" | "topic">,
    private fetcher: typeof fetch = fetch,
  ) {}
  private async json(
    url: string,
    init: RequestInit = {},
    limit = 2_000_000,
  ): Promise<any> {
    const response = await this.fetcher(url, {
      ...init,
      redirect: "error",
      signal: AbortSignal.timeout(20_000),
    });
    const reader = response.body?.getReader();
    let size = 0;
    const chunks: Uint8Array[] = [];
    if (reader)
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > limit) {
          await reader.cancel();
          throw new Oversize();
        }
        chunks.push(value);
      }
    const raw = Buffer.concat(chunks).toString("utf8");
    let data: any = {};
    try {
      data = raw ? JSON.parse(raw) : {};
    } catch {
      throw new AppError("GOOGLE_INVALID_RESPONSE");
    }
    if (!response.ok) {
      const reason =
        typeof data.error === "string"
          ? data.error
          : data.error?.errors?.[0]?.reason;
      if (reason === "invalid_grant")
        throw new AppError("REAUTH_REQUIRED", 409);
      if (response.status === 401) throw new AppError("REAUTH_REQUIRED", 409);
      if (response.status === 404) throw new AppError("GOOGLE_NOT_FOUND", 404);
      if (
        response.status === 429 ||
        reason === "rateLimitExceeded" ||
        reason === "userRateLimitExceeded"
      )
        throw new AppError("GOOGLE_QUOTA", 503);
      throw new AppError("GOOGLE_UNAVAILABLE", 503);
    }
    return data;
  }
  private async token(body: URLSearchParams) {
    const data = await this.json(
      "https://oauth2.googleapis.com/token",
      {
        method: "POST",
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body,
      },
      65536,
    );
    if (typeof data.access_token !== "string")
      throw new AppError("GOOGLE_INVALID_TOKEN");
    if (
      data.scope &&
      !String(data.scope)
        .split(" ")
        .includes("https://www.googleapis.com/auth/gmail.readonly")
    )
      throw new AppError("GMAIL_SCOPE_REQUIRED", 403);
    return {
      accessToken: data.access_token,
      refreshToken:
        typeof data.refresh_token === "string" ? data.refresh_token : undefined,
    };
  }
  exchange(code: string) {
    return this.token(
      new URLSearchParams({
        grant_type: "authorization_code",
        redirect_uri: "", // Native AuthorizationClient code has no web redirect.
        code,
        client_id: this.cfg.webClientId,
        client_secret: this.cfg.webClientSecret,
      }),
    );
  }
  refresh(refreshToken: string) {
    return this.token(
      new URLSearchParams({
        grant_type: "refresh_token",
        refresh_token: refreshToken,
        client_id: this.cfg.webClientId,
        client_secret: this.cfg.webClientSecret,
      }),
    );
  }
  private async request(
    token: string,
    path: string,
    params: URLSearchParams = new URLSearchParams(),
    body?: unknown,
  ) {
    return this.json(
      `https://gmail.googleapis.com/gmail/v1/users/me/${path}?${params}`,
      {
        method: body === undefined ? "GET" : "POST",
        headers: {
          authorization: `Bearer ${token}`,
          ...(body === undefined ? {} : { "content-type": "application/json" }),
        },
        body: body === undefined ? undefined : JSON.stringify(body),
      },
    );
  }
  async profile(token: string) {
    const d = await this.request(
      token,
      "profile",
      new URLSearchParams({ fields: "emailAddress,historyId" }),
    );
    return {
      email: String(d.emailAddress).toLowerCase(),
      historyId: historyId(String(d.historyId)),
    };
  }
  async watch(token: string) {
    const d = await this.request(token, "watch", undefined, {
      topicName: this.cfg.topic,
      labelIds: ["INBOX"],
      labelFilterBehavior: "INCLUDE",
    });
    const expiration = new Date(Number(d.expiration));
    if (expiration <= new Date() || !Number.isFinite(expiration.getTime()))
      throw new AppError("INVALID_WATCH");
    return { historyId: historyId(String(d.historyId)), expiration };
  }
  async stop(token: string) {
    await this.request(token, "stop", undefined, {});
  }
  async revoke(token: string) {
    try {
      const r = await this.fetcher("https://oauth2.googleapis.com/revoke", {
        method: "POST",
        redirect: "error",
        signal: AbortSignal.timeout(15000),
        headers: { "content-type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({ token }),
      });
      return r.ok;
    } catch {
      return false;
    }
  }
  async history(
    token: string,
    cursor: string,
    page?: string,
  ): Promise<HistoryPage> {
    const q = new URLSearchParams({
      startHistoryId: historyId(cursor),
      historyTypes: "messageAdded",
      maxResults: "100",
    });
    q.append("historyTypes", "labelAdded");
    if (page) q.set("pageToken", page);
    const d = await this.request(token, "history", q);
    const ids = new Set<string>();
    for (const h of d.history ?? []) {
      for (const a of h.messagesAdded ?? [])
        if (a.message?.id) ids.add(a.message.id);
      for (const a of h.labelsAdded ?? [])
        if (a.labelIds?.includes("INBOX") && a.message?.id)
          ids.add(a.message.id);
    }
    return {
      ids: [...ids],
      nextPage: d.nextPageToken,
      historyId: historyId(String(d.historyId)),
    };
  }
  async list(token: string, start: Date, end: Date, page?: string) {
    const q = new URLSearchParams({
      labelIds: "INBOX",
      q: `after:${Math.floor(start.getTime() / 1000) - 1} before:${Math.floor(end.getTime() / 1000)} -in:drafts`,
      maxResults: "100",
      fields: "messages(id),nextPageToken",
      includeSpamTrash: "false",
    });
    if (page) q.set("pageToken", page);
    const d = await this.request(token, "messages", q);
    return {
      ids: (d.messages ?? []).map((m: { id: string }) => m.id),
      nextPage: d.nextPageToken as string | undefined,
    };
  }
  async message(token: string, id: string) {
    if (!/^[A-Za-z0-9_-]{1,256}$/.test(id))
      throw new AppError("INVALID_MESSAGE_ID", 400);
    try {
      return parseMessage(
        await this.request(
          token,
          `messages/${id}`,
          new URLSearchParams({
            format: "full",
            fields: "id,internalDate,labelIds,payload",
          }),
        ),
      );
    } catch (error) {
      if (error instanceof AppError && error.code === "GOOGLE_NOT_FOUND")
        return null;
      if (error instanceof Oversize) {
        try {
          return parseMessage(
            await this.request(
              token,
              `messages/${id}`,
              new URLSearchParams([
                ["format", "metadata"],
                ["fields", "id,internalDate,labelIds,payload/headers"],
                ["metadataHeaders", "From"],
                ["metadataHeaders", "Subject"],
                ["metadataHeaders", "Reply-To"],
              ]),
            ),
            true,
          );
        } catch (fallback) {
          if (
            fallback instanceof AppError &&
            fallback.code === "GOOGLE_NOT_FOUND"
          )
            return null;
          throw fallback;
        }
      }
      throw error;
    }
  }
}
