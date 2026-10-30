/**
 * Browser-side API client. Same-origin requests, session cookie auth, CSRF header on mutations and RFC 9457
 * problem responses mapped to ApiError. Never stores tokens in localStorage.
 */
export const API_BASE = "/api/v1";

export interface FieldError {
  field: string;
  message: string;
}

export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
  code?: string;
  errors?: FieldError[];
  errorId?: string;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string;
  readonly errors: FieldError[];

  constructor(problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `Request failed with status ${problem.status}`);
    this.name = "ApiError";
    this.status = problem.status;
    this.code = problem.code ?? "UNKNOWN";
    this.detail = problem.detail ?? "";
    this.errors = problem.errors ?? [];
  }
}

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);
const CSRF_COOKIE = "XSRF-TOKEN";
const CSRF_HEADER = "X-XSRF-TOKEN";

export function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined;
  const match = document.cookie
    .split("; ")
    .find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.substring(name.length + 1)) : undefined;
}

export interface ApiRequestInit extends Omit<RequestInit, "body"> {
  /** JSON payload; serialised and sent with the correct content type. */
  json?: unknown;
  /** Raw body (for example FormData for uploads). */
  body?: BodyInit | null;
}

export async function apiFetch<T>(path: string, init: ApiRequestInit = {}): Promise<T> {
  const method = (init.method ?? "GET").toUpperCase();
  const headers = new Headers(init.headers);
  headers.set("Accept", "application/json, application/problem+json");

  let body: BodyInit | null | undefined = init.body;
  if (init.json !== undefined) {
    headers.set("Content-Type", "application/json");
    body = JSON.stringify(init.json);
  }

  if (!SAFE_METHODS.has(method)) {
    let token = readCookie(CSRF_COOKIE);
    if (!token) {
      await fetch(`${API_BASE}/auth/csrf`, { credentials: "same-origin" });
      token = readCookie(CSRF_COOKIE);
    }
    if (token) headers.set(CSRF_HEADER, token);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    method,
    headers,
    body,
    credentials: "same-origin",
  });

  if (response.status === 204) return undefined as T;

  const contentType = response.headers.get("content-type") ?? "";
  const payload = contentType.includes("json") ? await response.json() : undefined;

  if (!response.ok) {
    throw new ApiError(payload ?? { status: response.status, title: response.statusText });
  }
  return payload as T;
}

export function isApiError(error: unknown, code?: string): error is ApiError {
  return error instanceof ApiError && (code === undefined || error.code === code);
}
