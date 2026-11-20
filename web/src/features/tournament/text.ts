// Tournament wording, as the app's ui/tournament/TournamentLabels.kt.
import type { FixtureInfo } from "@/api/types";
import type { useText } from "@/i18n";

/** Groups are lettered: 0 is "A". */
export const groupName = (group: number) => String.fromCharCode(65 + group);

/** "Group A", "Round 2", "Semi-final". */
export function stageText({ s }: ReturnType<typeof useText>, fixture: FixtureInfo): string {
  switch (fixture.stage) {
    case "LEAGUE":
      return fixture.group != null ? s("tournament_group", groupName(fixture.group)) : s("stage_league");
    case "ROUND":
      return s("stage_round", fixture.round);
    case "QUARTERFINAL":
      return s("stage_quarterfinal");
    case "SEMIFINAL":
      return s("stage_semifinal");
    case "FINAL":
      return s("stage_final");
    case "MATCH":
      return s("stage_match", fixture.round);
  }
}
