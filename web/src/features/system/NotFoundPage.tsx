import { ErrorState, Page, PrimaryButton } from "@/designsystem/components";
import { useText } from "@/i18n";

export function NotFoundPage() {
  const { s } = useText();
  return (
    <Page>
      <ErrorState
        missing
        title={s("web_not_found_title")}
        body={s("web_not_found_body")}
        action={<PrimaryButton to="/">{s("web_not_found_action")}</PrimaryButton>}
      />
    </Page>
  );
}
