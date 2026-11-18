export type Risk = "LOW" | "MEDIUM" | "HIGH";
export type ConnectionStatus =
  | "PENDING"
  | "ACTIVATING"
  | "ACTIVE"
  | "TEMP_ERROR"
  | "REAUTH_REQUIRED"
  | "DISCONNECTED"
  | "DISCONNECTED_REVOCATION_PENDING";
export type User = { id: string; email: string };
export type Analysis = { score: number; level: Risk; reasons: string[] };
export type Mail = {
  id: string;
  receivedAt: Date;
  labels: string[];
  sender: string;
  subject: string;
  replyTo: string;
  text: string;
  limited: boolean;
};
export type Connection = {
  zone: string;
  id: string;
  userId: string;
  email: string | null;
  ciphertext: string | null;
  historyId: string | null;
  expiration: Date | null;
  startedAt: Date | null;
  tokenCreatedAt: Date | null;
  status: ConnectionStatus;
  lastSync: Date | null;
  lastError: string | null;
};
export type Lease = { connection: Connection; token: string };
export type HistoryPage = {
  ids: string[];
  nextPage?: string;
  historyId: string;
};
export type Event = {
  id?: string;
  userId: string;
  hash: string;
  receivedAt: Date;
  analysis: Analysis;
  sender: string | null;
  subject: string | null;
  limited: boolean;
};
export class AppError extends Error {
  constructor(
    public readonly code: string,
    public readonly status = 503,
  ) {
    super(code);
    this.name = "AppError";
  }
}
export interface Google {
  exchange(
    code: string,
  ): Promise<{ accessToken: string; refreshToken?: string }>;
  refresh(
    token: string,
  ): Promise<{ accessToken: string; refreshToken?: string }>;
  profile(token: string): Promise<{ email: string; historyId: string }>;
  watch(token: string): Promise<{ historyId: string; expiration: Date }>;
  stop(token: string): Promise<void>;
  revoke(token: string): Promise<boolean>;
  history(token: string, cursor: string, page?: string): Promise<HistoryPage>;
  list(
    token: string,
    start: Date,
    end: Date,
    page?: string,
  ): Promise<{ ids: string[]; nextPage?: string }>;
  message(token: string, id: string): Promise<Mail | null>;
}
export interface Cipher {
  encrypt(userId: string, plain: string): Promise<string>;
  decrypt(userId: string, encrypted: string): Promise<string>;
}
export interface Store {
  ensure(userId: string): Promise<void>;
  find(userId: string): Promise<Connection | null>;
  findEmail(email: string): Promise<Connection | null>;
  acquire(userId: string): Promise<Lease | null>;
  renew(lease: Lease): Promise<boolean>;
  release(lease: Lease): Promise<void>;
  update(lease: Lease, changes: Partial<Connection>): Promise<void>;
  hasEvent(userId: string, hash: string): Promise<boolean>;
  insertEvent(lease: Lease, event: Event): Promise<boolean>;
  active(): Promise<Connection[]>;
  cleanup(): Promise<void>;
  erase(
    lease: Lease,
    deleteEvents: boolean,
    remoteConfirmed: boolean,
  ): Promise<void>;
}
export interface Queue {
  publish(email: string, cursor: string): Promise<void>;
}
export interface Push {
  flush(userId?: string): Promise<void>;
}
export type Clock = () => Date;
