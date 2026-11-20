import { createBrowserRouter, Outlet } from "react-router";
import { AppShell } from "@/designsystem/components";
import { CataloguePage } from "@/features/catalogue/CataloguePage";
import { HomePage } from "@/features/home/HomePage";
import { JoinPage } from "@/features/join/JoinPage";
import { MatchPage } from "@/features/match/MatchPage";
import { PlayerPage } from "@/features/player/PlayerPage";
import { SearchPage } from "@/features/search/SearchPage";
import { NotFoundPage } from "@/features/system/NotFoundPage";
import { TeamPage } from "@/features/team/TeamPage";
import { TournamentPage } from "@/features/tournament/TournamentPage";
import { TournamentsPage } from "@/features/tournament/TournamentsPage";
import { useText } from "@/i18n";
import { TopBar } from "./GetApp";
import { appTabs } from "./tabs";

function Layout() {
  const text = useText();
  return (
    <AppShell tabs={appTabs(text)} homeLabel={text.s("web_brand")}>
      <TopBar />
      <Outlet />
    </AppShell>
  );
}

export const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { path: "/", element: <HomePage /> },
      { path: "/tournaments", element: <TournamentsPage /> },
      { path: "/search", element: <SearchPage /> },
      { path: "/teams/:teamId", element: <TeamPage /> },
      { path: "/players/:profileId", element: <PlayerPage /> },
      // Shared links: the same paths the app and the server's old pages use.
      { path: "/m/:matchId", element: <MatchPage /> },
      { path: "/t/:tournamentId", element: <TournamentPage /> },
      { path: "/join/:code", element: <JoinPage /> },
      { path: "/catalogue", element: <CataloguePage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);
