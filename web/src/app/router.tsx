import { createBrowserRouter, Navigate, Outlet } from "react-router";
import { AppShell } from "@/designsystem/components";
import { CataloguePage } from "@/features/catalogue/CataloguePage";
import { NotFoundPage } from "@/features/system/NotFoundPage";
import { useText } from "@/i18n";
import { appTabs } from "./tabs";

function TabsLayout() {
  const text = useText();
  return (
    <AppShell tabs={appTabs(text)} homeLabel={text.s("web_brand")}>
      <Outlet />
    </AppShell>
  );
}

export const router = createBrowserRouter([
  {
    element: <TabsLayout />,
    children: [
      // Until the signed-in home lands (Phase W2), the root shows the component catalogue.
      { path: "/", element: <Navigate to="/catalogue" replace /> },
      { path: "/catalogue", element: <CataloguePage /> },
    ],
  },
  { path: "*", element: <NotFoundPage /> },
]);
