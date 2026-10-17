/**
 * Consent-gated Google Analytics (SH1243).
 *
 * The rule is the one www.viglet.org runs under (W42 in its roadmap): GA loads
 * only after the visitor accepts, with no consent-mode pings before that, and a
 * visitor who declines stays uncounted. Search Console is the search number.
 * So the gtag loader is not in `index.html`; it is injected here, and only on a
 * granted choice. The choice is kept in localStorage so the banner asks once.
 */

const GA_ID = "G-SMSL7R3897";
const CONSENT_KEY = "shio-analytics-consent";

/** The footer's "Cookie settings" link fires this to reopen the banner. */
export const OPEN_CONSENT_EVENT = "shio:open-consent";

export type Consent = "granted" | "denied";

export function getConsent(): Consent | null {
  try {
    const v = localStorage.getItem(CONSENT_KEY);
    return v === "granted" || v === "denied" ? v : null;
  } catch {
    return null;
  }
}

export function setConsent(consent: Consent): void {
  try {
    localStorage.setItem(CONSENT_KEY, consent);
  } catch {
    /* storage blocked: the choice holds for this page view only */
  }
  if (consent === "granted") loadAnalytics();
}

/** Let a visitor reopen the banner to change their mind. */
export function reopenConsent(): void {
  globalThis.dispatchEvent(new Event(OPEN_CONSENT_EVENT));
}

let loaded = false;

/** Inject gtag.js and configure GA. Idempotent, and only ever called after consent. */
export function loadAnalytics(): void {
  if (loaded || typeof window === "undefined") return;
  loaded = true;

  const w = window as unknown as { dataLayer: unknown[]; gtag?: (...args: unknown[]) => void };
  const script = document.createElement("script");
  script.async = true;
  script.src = `https://www.googletagmanager.com/gtag/js?id=${GA_ID}`;
  document.head.appendChild(script);

  w.dataLayer = w.dataLayer || [];
  // gtag.js reads `arguments` objects off the data layer, not arrays.
  w.gtag = function gtag() {
    // eslint-disable-next-line prefer-rest-params
    w.dataLayer.push(arguments);
  };
  w.gtag("js", new Date());
  w.gtag("config", GA_ID);
}

/** Called once on load: honour a choice already granted, and load nothing otherwise. */
export function initAnalyticsFromConsent(): void {
  if (getConsent() === "granted") loadAnalytics();
}
