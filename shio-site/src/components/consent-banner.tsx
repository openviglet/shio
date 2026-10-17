import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { Button } from "@/components/ui/button";
import {
  getConsent,
  initAnalyticsFromConsent,
  OPEN_CONSENT_EVENT,
  setConsent,
  type Consent,
} from "@/lib/analytics";

/**
 * The consent banner (SH1243), the same one www.viglet.org shows. Nothing is
 * loaded until the visitor chooses, and Decline is as easy as Accept (LGPD:
 * refusing must cost no more than accepting). It starts hidden, so the
 * prerendered HTML and the Markdown twins never carry it, and appears after
 * hydration only when no choice is stored.
 */
export function ConsentBanner() {
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    initAnalyticsFromConsent();
    if (getConsent() === null) setVisible(true);

    const reopen = () => setVisible(true);
    globalThis.addEventListener(OPEN_CONSENT_EVENT, reopen);
    return () => globalThis.removeEventListener(OPEN_CONSENT_EVENT, reopen);
  }, []);

  if (!visible) return null;

  const choose = (consent: Consent) => {
    setConsent(consent);
    setVisible(false);
  };

  return (
    <div
      role="dialog"
      aria-label="Cookie consent"
      aria-live="polite"
      className="fixed inset-x-0 bottom-0 z-50 p-4 sm:p-6"
    >
      <div className="mx-auto max-w-3xl rounded-2xl border border-border bg-card p-5 shadow-lg sm:p-6">
        <div className="flex items-start gap-4">
          <div className="min-w-0 flex-1">
            <p className="mb-1 font-bold text-foreground">We respect your privacy</p>
            <p className="text-sm leading-relaxed text-muted-foreground">
              We use Google Analytics cookies only to see how the site is used, never for
              ads. Nothing loads until you choose, and you can change it later from the
              footer.
            </p>
          </div>
          <button
            type="button"
            onClick={() => choose("denied")}
            aria-label="Decline and close"
            className="shrink-0 text-muted-foreground transition-colors hover:text-foreground"
          >
            <X className="size-4" />
          </button>
        </div>
        <div className="mt-4 flex flex-wrap justify-end gap-3">
          <Button variant="ghost" onClick={() => choose("denied")}>
            Decline
          </Button>
          <Button onClick={() => choose("granted")}>Accept analytics</Button>
        </div>
      </div>
    </div>
  );
}
