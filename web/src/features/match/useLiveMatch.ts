import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useMemo, useState } from "react";
import { api, socketUrl } from "@/api/client";
import type { MatchDetails } from "@/api/types";
import { scorecard } from "@/core";

/** Polls this often while the live socket is down, as the app does. */
const POLL_MS = 15_000;
const MAX_BACKOFF_MS = 30_000;
/** The server closes with this when the match doesn't exist; there's no point reconnecting. */
const POLICY_VIOLATION = 1008;

/**
 * A match that keeps itself up to date: loaded once, then every change arrives over the public live feed as a
 * whole scorecard. If the socket drops it reconnects with backoff and polls in the meantime.
 */
export function useLiveMatch(matchId: string) {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);
  // A socket that opened and then dropped, or never opened; not the first moments of connecting.
  const [dropped, setDropped] = useState(false);
  const query = useQuery({
    queryKey: ["public-match", matchId],
    queryFn: ({ signal }) => api<MatchDetails>(`/public/matches/${encodeURIComponent(matchId)}`, { signal }),
    refetchInterval: (q) => (!connected && q.state.data && q.state.data.match.status !== "COMPLETED" ? POLL_MS : false),
  });

  const finished = query.data?.match.status === "COMPLETED";
  const loaded = query.data != null;

  useEffect(() => {
    if (!loaded || finished) return;
    let socket: WebSocket | null = null;
    let retry: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;

    const open = () => {
      socket = new WebSocket(socketUrl(`/public/matches/${encodeURIComponent(matchId)}/live`));
      socket.onopen = () => {
        attempt = 0;
        setConnected(true);
        setDropped(false);
      };
      socket.onmessage = (event) => {
        queryClient.setQueryData(["public-match", matchId], JSON.parse(event.data as string) as MatchDetails);
      };
      socket.onclose = (event) => {
        setConnected(false);
        if (stopped || event.code === POLICY_VIOLATION) return;
        setDropped(true);
        attempt += 1;
        retry = setTimeout(open, Math.min(MAX_BACKOFF_MS, 1000 * 2 ** attempt));
      };
    };
    open();

    return () => {
      stopped = true;
      clearTimeout(retry);
      socket?.close();
      setConnected(false);
    };
  }, [matchId, loaded, finished, queryClient]);

  const card = useMemo(() => (query.data ? scorecard(query.data) : null), [query.data]);

  return {
    details: query.data,
    scorecard: card,
    isLoading: query.isPending,
    error: query.error,
    /** Showing an old score because the latest refresh failed. */
    isStale: query.data != null && query.isRefetchError,
    /** The live feed is down and being retried; the score still refreshes by polling. */
    reconnecting: dropped && !connected,
    retry: () => void query.refetch(),
  };
}
