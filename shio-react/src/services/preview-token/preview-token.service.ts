import axios from "@/lib/axios"

/** Result of minting a short-lived preview token (SH13). */
export interface ShPreviewToken {
  token: string
  previewUrl: string
  expiresAt: string
  ttlSeconds: number
}

/**
 * Short-lived draft-preview links (SH13, SH1042).
 *
 * One call, two destinations, and the caller does not have to know which: a site with its own front
 * end gets a `PREVIEW` token on that front end's draft-mode URL, and a site Shio renders itself gets
 * a `SHARE` link into `/preview/**` that exchanges itself for an expiring cookie on the first hop.
 *
 * **`token` is finally always present.** Until SH1042 this interface typed it as a non-nullable
 * string while the server answered `null` for an internal front end, because a credential for a
 * console-authenticated path unlocked nothing — true until a share link became the thing that
 * unlocks it. The type was wrong for as long as that branch existed and is right now.
 *
 * A console (session-authenticated) endpoint, so it uses the shared axios singleton (cookie +
 * CSRF), unlike the CDA write service. The **link it answers with is not**: it is meant to be sent
 * to somebody who has no account here, which is the whole point of it.
 */
export class ShPreviewTokenService {
  async mint(postId: string, ttlSeconds?: number): Promise<ShPreviewToken> {
    const response = await axios.post<ShPreviewToken>("/v2/preview-token", { postId, ttlSeconds })
    return response.data
  }
}
