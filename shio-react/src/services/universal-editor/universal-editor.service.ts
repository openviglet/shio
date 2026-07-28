import axios from "@/lib/axios"
import type { ShCdaEditablePost, ShCdaPost } from "@/models/universal-editor/universal-editor.model"

/**
 * Universal Editor (SH10) save-back service. Talks to the CDA write surface
 * (`/api/v2/cda`) authenticated by a WRITE-scope API token sent in the `Key` header.
 *
 * **SH233 — this used to dodge the session, and could not.** It built its own
 * credential-less axios instance on the premise that the CDA filter authenticates the token
 * only when nothing else has, so the console's session cookie must not be sent. The reasoning
 * was right and the mechanism did not implement it: `withCredentials` governs **cross-origin**
 * requests only, and the console is same-origin with the API both in dev (behind the Vite proxy)
 * and in production behind one host — so the cookie went anyway, the session was restored before
 * `ShApiTokenFilter` ran, the `Key` header was never read, and the PATCH authenticated as a
 * curator with no `ROLE_CDA_WRITE`. A 403 that looked like a permissions problem and was a
 * plumbing one.
 *
 * Both halves are fixed on the honest side. The server lets an explicitly presented `Key` win
 * over an existing session, and this client uses the **shared** axios singleton so the request
 * carries the CSRF token: since SH167 a session-bearing caller on the CDA path is CSRF-checked,
 * and the cookie is going to be sent whatever this file asks for. The private instance also
 * silently opted out of the shared interceptors — CSRF, the 403 retry, the offline banner —
 * because `axios.create()` does not inherit them.
 */
function keyHeader(token: string) {
  return { headers: { Key: token } }
}

export class ShUniversalEditorService {
  /** Draft values + post-type field schema for the properties panel. */
  async getEditable(postId: string, token: string): Promise<ShCdaEditablePost> {
    const response = await axios.get<ShCdaEditablePost>(
      `/v2/cda/post/${postId}/editable`, keyHeader(token))
    return response.data
  }

  /** Merge attribute changes into the post's draft (optionally publishing). */
  async patch(
    postId: string,
    attrs: Record<string, unknown>,
    token: string,
    publish = false,
  ): Promise<ShCdaPost> {
    const response = await axios.patch<ShCdaPost>(
      `/v2/cda/post/${postId}`, { attrs, publish }, keyHeader(token))
    return response.data
  }

  /** Promote the post's draft to published. */
  async publish(postId: string, token: string): Promise<ShCdaPost> {
    const response = await axios.post<ShCdaPost>(
      `/v2/cda/post/${postId}/publish`, {}, keyHeader(token))
    return response.data
  }
}
