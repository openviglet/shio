import axios from "@/lib/axios"

/** Result of minting a short-lived preview token (SH13). */
export interface ShPreviewToken {
  token: string
  previewUrl: string
  expiresAt: string
  ttlSeconds: number
}

/**
 * Short-lived draft-preview tokens (SH13). Mints an expiring PREVIEW-env CDA
 * token for one post and returns a ready-to-open preview URL at the site
 * front-end. This is a console (session-authenticated) endpoint, so it uses the
 * shared axios singleton (cookie + CSRF), unlike the CDA write service.
 */
export class ShPreviewTokenService {
  async mint(postId: string, ttlSeconds?: number): Promise<ShPreviewToken> {
    const response = await axios.post<ShPreviewToken>("/v2/preview-token", { postId, ttlSeconds })
    return response.data
  }
}
