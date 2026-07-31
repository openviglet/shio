import axios from "@/lib/axios"
import type { ShPostUnified } from "@/models/post-unified/post-unified"

/**
 * Universal Editor save-back service — on the console surface, on the curator's session.
 *
 * **SH335 — it used to talk to the CDA behind a WRITE-scope token.** A console feature reaching its
 * own content through a third contract is P10 read backwards, and it cost three things: the editor
 * was unusable on a fresh instance (no token exists yet, and "saves nothing silently" is how that
 * presents), the most dangerous credential in the product sat in `localStorage` doing what the
 * session cookie already authorized, and `PATCH {publish:true}` carried publish authority SH107's
 * gate never saw — that gate is enforced in `ShAgentBatchService` on the premise that "a console
 * caller has no token and is unaffected", which this path made false.
 *
 * SH233's lesson is why the fix is a verb and not a header: this file already learned that the
 * session cookie goes on same-origin requests whatever the client asks for. The honest conclusion
 * was that the session is the credential here, so the write belongs where the session already
 * writes. `ShPostDraftMergeService` is the merge, once, with the CDA's allow-list and the console's
 * ACL as two guards over it.
 */
export class ShUniversalEditorService {
  /**
   * The post-type name of one post, on the session (SH335).
   *
   * SH305 needs the *selected post's* type rather than the page's, or every row of a listing is
   * labelled from the wrong schema. That used to mean `GET /v2/cda/post/{id}/editable` behind a
   * WRITE token, which is why labels silently degraded for anyone who had not minted one. The
   * console can already read its own post; the fields then come from the post-type API, which is
   * where a label is declared and the one place it should be read from.
   */
  async postTypeOf(postId: string): Promise<string | null> {
    const response = await axios.get<ShPostUnified>(`/v2/post-unified/${postId}`)
    return response.data?.shPostType?.name ?? null
  }

  /**
   * Merge attribute changes into the post's draft (optionally publishing).
   *
   * **SH335 — the console's own verb, on the session.** This used to be the CDA's `PATCH` behind a
   * `WRITE`-scope token the curator had to mint and paste, which is P10 read backwards: a console
   * feature reaching its own content through a third contract. It made the editor unusable on a
   * fresh instance, parked the most dangerous credential in `localStorage` to do what the session
   * cookie already authorized, and carried publish authority SH107 never saw. The merge itself did
   * not move — `ShPostDraftMergeService` is one implementation with two guards.
   */
  async patch(
    postId: string,
    attrs: Record<string, unknown>,
    publish = false,
  ): Promise<ShPostUnified> {
    const response = await axios.patch<ShPostUnified>(
      `/v2/post-unified/${postId}`, { attrs, publish })
    return response.data
  }

  /** Promote the post's draft to published. */
  async publish(postId: string): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${postId}/publish`, {})
    return response.data
  }
}
