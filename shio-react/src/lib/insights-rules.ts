/**
 * SH1013 — the two judgements the Insights panel makes for itself.
 *
 * Separate from `insights.service.ts` because that module imports the axios client, and a rule that
 * can only run inside a browser is a rule a test has to reconstruct rather than execute. These have
 * no imports, so the suite runs the code the console runs.
 */

/** One image as the digest inventories it — `alt` absent means the attribute is absent. */
export interface ShDigestImage {
  src: string
  alt?: string
}

/** Words a minute, the figure every reading-time estimate on the web uses. */
const READING_WPM = 200

/**
 * Images with no `alt` attribute at all.
 *
 * `alt=""` declares an image decorative and an absent attribute declares nothing: the two mean
 * different things to a screen reader, which is the whole point of the rule. Reporting both would
 * flag every deliberate decision and train the curator to ignore the group.
 */
export function imagesWithoutAlt(
  digest: { images?: ShDigestImage[] } | null,
): ShDigestImage[] {
  return (digest?.images ?? []).filter((image) => image.alt === undefined)
}

/** Minutes, rounded up, so a short page reads as "1 min" rather than "0". */
export function readingMinutes(words: number): number {
  return Math.max(1, Math.ceil(words / READING_WPM))
}
