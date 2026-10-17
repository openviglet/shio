import BUDGETS from "./token-budgets.generated.json";

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * SH925: a token figure in the copy is read from here, never typed into a
 * sentence.
 *
 * SH533 built the generator for the four bars on the landing chart and stopped
 * there, so the two figures that live inside prose (the manifest check on
 * /claude-code and the pair in the agent-gateway code sample) stayed literals.
 * Both had drifted by the time anyone looked: 298 and 2343 were measured at
 * SH174, and the ceilings the build now enforces are 390 and 2820. A number in
 * a sentence is true the day it is typed and wrong in silence afterwards.
 *
 * These are ceilings (what the build refuses to exceed), so the copy says "at
 * most". A measurement would be the more flattering number and the one nothing
 * can check.
 * ─────────────────────────────────────────────────────────────────────────────
 */

/** The enforced ceiling for one budget key, or a build failure naming the key. */
export function ceiling(key: string): number {
  const found = BUDGETS.ceilings.find((c) => c.key === key);
  if (!found) {
    throw new Error(
      `token-budgets: no ceiling '${key}'. The copy quotes it, so a renamed key fails the `
        + "build rather than leaving a figure on the page that nothing enforces.",
    );
  }
  return found.tokens;
}
