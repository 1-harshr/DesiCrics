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

/** GETs JSON from the server's public, read-only API. The web app never signs in or changes anything. */
export async function api<T>(path: string, init: { signal?: AbortSignal } = {}): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${baseUrl}${path}`, { signal: init.signal });
  } catch (e) {
    if (e instanceof DOMException && e.name === "AbortError") throw e;
    throw new ApiError(0, "Network unreachable");
  }
  if (!response.ok) {
    const body = (await response.json().catch(() => null)) as ApiErrorBody | null;
    throw new ApiError(response.status, body?.message ?? response.statusText);
  }
  return (await response.json()) as T;
}

/** `ws://` or `wss://` URL for a WebSocket path on the API. */
export function socketUrl(path: string): string {
  const http = baseUrl || window.location.origin;
  return `${http.replace(/^http/, "ws")}${path}`;
}
