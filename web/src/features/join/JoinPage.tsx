import { useState } from "react";
import { useParams } from "react-router";
import { playStoreUrl, PublicLayout } from "@/app/PublicLayout";
import { Card, IconTile, Page, PrimaryButton, TonalButton } from "@/designsystem/components";
import { useText } from "@/i18n";
import styles from "./JoinPage.module.css";

/**
 * A team invite link. Phones with the app open it there; everyone else learns how to join in the app, since
 * the web app is view only. The code is cleaned the same way the server's old page did.
 */
export function JoinPage() {
  const { code: raw = "" } = useParams();
  const { s } = useText();
  const code = raw.replace(/[^A-Za-z0-9]/g, "").slice(0, 8).toUpperCase();
  const [copied, setCopied] = useState(false);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(code);
      setCopied(true);
    } catch {
      // Clipboard blocked; the code is on screen to type.
    }
  };

  return (
    <PublicLayout>
      <Page>
        <Card className={styles.card}>
          <IconTile icon="group_add" />
          <h1 className="title-lg">{s("web_join_title")}</h1>
          <p className="muted">{s("web_join_body")}</p>
          <div className={styles.code} aria-label={s("teams_join_code")}>
            {code}
          </div>
          <TonalButton icon={copied ? "check_circle" : "content_copy"} onClick={copy} full>
            {s(copied ? "web_join_copied" : "web_join_copy")}
          </TonalButton>
          <ol className={styles.steps}>
            <li>{s("web_join_step_install")}</li>
            <li>{s("web_join_step_open")}</li>
            <li>{s("web_join_step_enter")}</li>
          </ol>
          <PrimaryButton href={playStoreUrl} trailingIcon="north_east" full>
            {s("web_get_app_action")}
          </PrimaryButton>
        </Card>
      </Page>
    </PublicLayout>
  );
}
