import axios from "axios"
import type { ShCdaEditablePost, ShCdaPost } from "@/models/universal-editor/universal-editor.model"

/**
 * Universal Editor (SH10) save-back service. Talks to the CDA write surface
 * (/api/v2/cda) authenticated by a WRITE-scope API token sent in the `Key`
 * header.
 *
 * It deliberately uses a dedicated, credential-less axios instance (NOT the
 * shared @/lib/axios singleton): the CDA filter only authenticates the token
 * when no session is already present, so the console session cookie must NOT be
 * sent — otherwise the request would authenticate as the logged-in console user
 * (who lacks ROLE_CDA_WRITE) and get a 403. The CDA write endpoints are also
 * CSRF-exempt, so no XSRF token is needed.
 */
function client(token: string) {
  return axios.create({
    baseURL: axios.defaults.baseURL ?? "",
    withCredentials: false,
    headers: { Key: token },
  })
}

export class ShUniversalEditorService {
  /** Draft values + post-type field schema for the properties panel. */
  async getEditable(postId: string, token: string): Promise<ShCdaEditablePost> {
    const response = await client(token).get<ShCdaEditablePost>(`/v2/cda/post/${postId}/editable`)
    return response.data
  }

  /** Merge attribute changes into the post's draft (optionally publishing). */
  async patch(
    postId: string,
    attrs: Record<string, unknown>,
    token: string,
    publish = false,
  ): Promise<ShCdaPost> {
    const response = await client(token).patch<ShCdaPost>(`/v2/cda/post/${postId}`, { attrs, publish })
    return response.data
  }

  /** Promote the post's draft to published. */
  async publish(postId: string, token: string): Promise<ShCdaPost> {
    const response = await client(token).post<ShCdaPost>(`/v2/cda/post/${postId}/publish`, {})
    return response.data
  }
}
