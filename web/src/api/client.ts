import type { ApiErrorBody } from "./types";

const baseUrl = (import.meta.env.VITE_API_URL ?? "").replace(/\/$/, "");

/**
 * A failed call. [status] is 0 when the server couldn't be reached, which the UI shows as being offline
 * rather than as an error. [message] is the server's English text, for logs; screens show their own words.
 */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
  }

  get isOffline(): boolean {
    return this.status === 0;
  }

  get isNotFound(): boolean {
    return this.status === 404;
  }
}

/** Gives the signed-in user's Firebase ID token, or null when signed out. Set by the auth layer. */
let tokenProvider: () => Promise<string | null> = async () => null;

export function setTokenProvider(provider: () => Promise<string | null>) {
  tokenProvider = provider;
}

export async function api<T>(path: string, init: { method?: string; body?: unknown; signal?: AbortSignal } = {}): Promise<T> {
  const token = await tokenProvider();
  const headers: Record<string, string> = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (init.body !== undefined) headers["Content-Type"] = "application/json";

  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, {
      method: init.method ?? "GET",
      headers,
      body: init.body === undefined ? undefined : JSON.stringify(init.body),
      signal: init.signal,
    });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "Network unreachable");
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
    throw new ApiError(response.status, body?.message ?? response.statusText);
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/** `ws://` or `wss://` URL for a WebSocket path on the API. */
export function socketUrl(path: string): string {
  const http = baseUrl || window.location.origin;
  return `${http.replace(/^http/, "ws")}${path}`;
}
