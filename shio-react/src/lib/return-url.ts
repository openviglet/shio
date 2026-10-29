/**
 * SH1299 — where the login sends somebody after they sign in, from a `returnUrl` anybody can write.
 *
 * A path on this host and nothing else. `/sites/**` sends a visitor turned away from a members-only
 * page here with the page as its way back, so the parameter now arrives in links the console did not
 * build; an absolute URL (`https://elsewhere`) or a protocol-relative one (`//elsewhere`, `/\elsewhere`)
 * would make the login an open redirect. Anything else falls back to `fallback`.
 */
export function safeReturnUrl(value: string | null | undefined, fallback: string): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return fallback;
  }
  return value;
}
