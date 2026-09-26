import axios from "@/lib/axios"
import type { ShPostUnified } from "@/models/post-unified/post-unified"
import type { InsertableContainer } from "@/models/universal-editor/universal-editor.model"
import { ShPostUnifiedService } from "@/services/post-unified/post-unified.service"
import type { Pattern, PlacementPage } from "@/app/editor/pattern-placement"

/** SH1108 — one pushed pattern, as `GET /agent/patterns` lists it. */
export type PatternSummary = {
  name: string
  title: string | null
  from: string | null
  sections: string[]
  params: string[]
  digest: string
  pushed: string
}

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
  /** The console's own post service, so there is one caller of each post endpoint (SH985). */
  private readonly posts = new ShPostUnifiedService()

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
   * SH1039 — what may be added to this page, and where, asked of the one thing that knows.
   *
   * The agent surface on the curator's own session, not a second implementation of the question.
   * The catalogue is read off the *rendered* page — which container composes sections, what its
   * template declares it accepts, and each of those types resolved against this instance — and the
   * console cannot read the framed document, because it is another origin. `ShPostUnifiedService`
   * already reads `/v2/agent/read` this way for SH996's publish routes, so the precedent and the
   * `id:` address are both established (SH172's escape hatch: the console holds an id and the agent
   * surface is addressed).
   *
   * A failure answers an empty list rather than throwing: the catalogue is what the console offers
   * on top of an editor that works without it, and an insert rail that cannot load must not take
   * the rest of the panel with it.
   */
  async insertable(pageId: string): Promise<InsertableContainer[]> {
    try {
      const response = await axios.get<{ containers?: InsertableContainer[] }>("/v2/agent/editable",
        { params: { address: `id:${pageId}`, include: "insertable" } })
      return response.data?.containers ?? []
    } catch {
      return []
    }
  }

  /**
   * SH1105 — where one section of `type` is drawn as it would look on this page.
   *
   * A URL and not a fetch: the answer is a document for a sandboxed frame, and the frame loading it
   * is what carries the session. It is the preview path cropped to one section, so the rail shows
   * the shape the page will draw rather than a second rendering of it.
   */
  sectionPreviewUrl(pageId: string, field: string, type: string): string {
    return axios.getUri({ url: "/v2/render/section-preview", params: { page: pageId, field, type } })
  }

  /**
   * The row a write would be composed against: the draft, or the live one where there is no draft.
   *
   * SH1038 needs the Page's own ordered section array before it can replace it, and the preview in
   * the frame is a render of the *published* row — so reading that one would compose a reorder
   * against a list an agent may already have changed in the draft, and the patch would put the
   * stale order back. The draft is the subject of every write on this surface (`patch` merges into
   * it), so it is the honest thing to read. The fallback is the ordinary case of a page that has
   * never been edited since it was published, where the two rows say the same thing anyway.
   */
  async editable(postId: string): Promise<ShPostUnified> {
    try {
      return await this.posts.get(postId, "DRAFT")
    } catch {
      return await this.posts.get(postId, "PUBLISHED")
    }
  }

  /**
   * SH1039 — create the section an insert places, as a draft.
   *
   * Through the console's own post service, so there is one caller of that endpoint (SH985) and one
   * idea of what creating a post means. A draft rather than published: a section nobody has written
   * a word into is not something to put in front of visitors, and the curator publishes the page
   * when it says what they meant.
   */
  async create(
    req: { folderId: string; postType: string; data: Record<string, unknown> },
  ): Promise<ShPostUnified> {
    return this.posts.create({ ...req, state: "DRAFT" })
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
  /**
   * SH1101 — an independent copy of one post, in `folderId`, and what it is called now.
   *
   * The object copy the content browser's Clone uses, which since SH1101 answers with the posts it
   * made. SH202 may have numbered the title and the friendly URL, so the caller reads both back
   * rather than assuming the source's.
   */
  async copy(postId: string, folderId: string): Promise<{ id: string; title?: string; furl?: string; address?: string }> {
    const response = await axios.put<{ posts?: { id: string; title?: string; furl?: string; address?: string }[] }>(
      `/v2/object/copyto/${folderId}`, [postId])
    const [made] = response.data?.posts ?? []
    if (!made) throw new Error(`copy of ${postId} answered with no post`)
    return made
  }

  async patch(
    postId: string,
    attrs: Record<string, unknown>,
    publish = false,
  ): Promise<ShPostUnified> {
    // SH985 — one caller of the endpoint, so the version guard is not a property of which service
    // you happened to reach it through. This editor writes the draft from a *published* page it
    // never read as a draft, so it holds no version for that row and the write goes out
    // unconditional, exactly as before. That is the honest answer: a conditional write needs a read
    // to be conditional on, and inventing one here would be a round trip spent on a promise nobody
    // could keep.
    return this.posts.patch(postId, attrs, publish)
  }

  /** SH1108 — the patterns pushed to this instance; a failure is an empty rail, not a broken panel. */
  async patterns(): Promise<PatternSummary[]> {
    try {
      const response = await axios.get<PatternSummary[]>("/v2/agent/patterns")
      return Array.isArray(response.data) ? response.data : []
    } catch {
      return []
    }
  }

  /** SH1108 — one pushed pattern, whole. */
  async pattern(name: string): Promise<Pattern> {
    const response = await axios.get<{ pattern: Pattern }>(`/v2/agent/patterns/${encodeURIComponent(name)}`)
    return response.data.pattern
  }

  /** SH1108 — the page as `/agent/read` answers it, which is what the placement document is built on. */
  async agentRead(postId: string): Promise<PlacementPage> {
    const response = await axios.get<PlacementPage[]>("/v2/agent/read", { params: { address: `id:${postId}` } })
    const [page] = response.data ?? []
    if (!page) throw new Error(`nothing at id:${postId}`)
    return page
  }

  /**
   * SH1108 — make a desired-state document true: the endpoint `shio patterns apply` uses.
   *
   * A pattern's document declares no `prune`, so it can delete nothing and needs no confirm token
   * (SH108) — which is the same reason the CLI sends it in one call.
   */
  async applyDocument(document: object): Promise<{ changed?: number }> {
    const response = await axios.post<{ changed?: number }>("/v2/agent/apply", document)
    return response.data
  }

  /** Promote the post's draft to published. */
  async publish(postId: string): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${postId}/publish`, {})
    return response.data
  }
}
