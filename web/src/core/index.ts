import { WebCore } from "desicrics-core";
import type { MatchDetails } from "@/api/types";
import type { ScorecardView } from "./types";

export type * from "./types";

/** The scorecard for a match, computed by the same Kotlin engine the app and server use. */
export function scorecard(details: MatchDetails): ScorecardView {
  return JSON.parse(WebCore.getInstance().scorecard(JSON.stringify(details))) as ScorecardView;
}
