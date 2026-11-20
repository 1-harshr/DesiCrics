import { useEffect, useState } from "react";
import { Snackbar } from "@/designsystem/components";
import { useText } from "@/i18n";

/** Shares this page's link with the system share sheet, or copies it where there isn't one. */
export function useShare() {
  const { s } = useText();
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) return;
    const timer = setTimeout(() => setCopied(false), 3000);
    return () => clearTimeout(timer);
  }, [copied]);

  const share = async (title: string) => {
    const url = window.location.href;
    if (navigator.share) {
      try {
        await navigator.share({ title, url });
        return;
      } catch (e) {
        if (e instanceof DOMException && e.name === "AbortError") return;
      }
    }
    try {
      await navigator.clipboard.writeText(url);
      setCopied(true);
    } catch {
      // Clipboard blocked; the address bar still has the link.
    }
  };

  return { share, toast: copied ? <Snackbar message={s("share_copied")} /> : null };
}
