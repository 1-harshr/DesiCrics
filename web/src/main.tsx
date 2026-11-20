import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "@/designsystem/tokens.css";
import "@/designsystem/base.css";
import "@/i18n";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <div />
  </StrictMode>,
);
