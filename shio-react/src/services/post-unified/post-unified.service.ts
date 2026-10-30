import axios from "axios"
import type {
  ShPostDiff,
  ShPostState,
  ShPostUnified,
  ShPublishPlan,
  ShPublishRoutes,
  ShRevisionRow,
} from "@/models/post-unified/post-unified"
import type { ShRead } from "@/lib/read-options"

/**
 * The create body. The post-type is named either way: `postType` is its API name
 * (SH117 — what the console has after reading the name-keyed `/api/v2/post-type`),
 * `postTypeId` its id, and the server prefers the id when both are sent. Exactly
 * one is required; neither is a teaching 400 listing the installed types.
 */
/**
 * SH1021 — the change set the agent's batch already returns, which is what a bulk verb answers.
 *
 * <p>An outcome and a problem per item, never a bare 200 over a partly-failed run: a selection
 * where one post is refused publishes the rest and says which one did not, with the fix its refusal
 * carries. That is the shape the console needs and the shape the agent already had.</p>
 */
/**
 * SH1132 — what a rename would do, or did.
 *
 * `live` is the one a curator has to read: both state rows move together, so a rename of a
 * published page changes the live URL immediately. `vacated` is the address that stops answering
 * and `inbound` how many pages link to it, which is what a redirect would be for.
 */
export interface ShMovePlan {
  from: string | null
  to: string
  live: boolean
  vacated: string | null
  inbound: number | null
  redirect: boolean
  changes: ShBulkResult
}

export interface ShBulkChange {
  op: string
  address?: string
  action: string
  detail?: string | null
  id?: string
  problem?: { title?: string; detail?: string; fix?: string }
}

export interface ShBulkResult {
  dryRun: boolean
  ops: number
  changed: number
  confirm?: string
  results: ShBulkChange[]
}

export interface CreateUnifiedPostRequest {
  folderId: string
  postType?: string
  postTypeId?: string
  data: Record<string, unknown>
  state?: ShPostState
  locale?: string
  translationGroupId?: string
  /**
   * The address to answer at (SH651). Omitted — which is what the console does — the server
   * derives one from the post's title field and corrects it to the first free slug in the
   * site. Sent, it is taken as identity (SH81) and never renumbered. Before SH651 nothing
   * derived one and a console-created post answered at its folder's address, i.e. nowhere.
   */
  furl?: string
  /**
   * SH1333 — a parent path the derived address starts with: `about` and a title "Our team" answer
   * at `/about/our-team`. Sent by the pages view's "new page here"; the server keeps choosing the
   * free slug, so the console never guesses one. Ignored when `furl` is sent.
   */
  under?: string
}

export interface UpdateUnifiedPostRequest {
  data: Record<string, unknown>
  state?: ShPostState
}

export class ShPostUnifiedService {
  /**
   * SH985 — the version each row was last seen at, keyed by post and state.
   *
   * The agent has had `expect` since SH721: a write carrying the digest it was planned against is
   * refused when somebody wrote in between. The console sent no version at all, so a curator saving
   * a form loaded five minutes ago silently discarded whatever an agent had written since — and the
   * agent is the writer with a review queue and an audit row, which is what makes that the worse
   * direction of the same collision.
   *
   * Held here rather than in each page because the guarantee has to hold for *every* caller of this
   * service: a page that forgot to thread the tag through would be back to last-write-wins, and
   * nothing about the call would look wrong. A read stores the tag, a write sends it and stores the
   * one the server answers with, so a save loop never needs a second round trip (P3).
   */
  private readonly etags = new Map<string, string>()

  /**
   * Keyed by state as well as by id, because the editor loads both rows on every open.
   *
   * Keying by id alone looks right and is not: `load()` reads the draft and the live row in one
   * `Promise.allSettled`, so the second answer overwrites the first and a save composed against
   * the draft would go out carrying the published row's version. It would be refused, correctly
   * and unhelpfully, on the very first save.
   */
  private static key(id: string, state: ShPostState): string {
    return `${id}|${state}`
  }

  /** What the server last said this row digests as, if this service has read or written it. */
  versionOf(id: string, state: ShPostState = "DRAFT"): string | undefined {
    return this.etags.get(ShPostUnifiedService.key(id, state))
  }

  /** Forget a post's versions — after a conflict the caller resolved by reloading. */
  forgetVersion(id: string): void {
    for (const state of ["DRAFT", "PUBLISHED"] as const) {
      this.etags.delete(ShPostUnifiedService.key(id, state))
    }
  }

  private remember(id: string, state: ShPostState, headers: unknown): void {
    const etag = (headers as Record<string, unknown> | undefined)?.etag
    if (typeof etag === "string" && etag) this.etags.set(ShPostUnifiedService.key(id, state), etag)
  }

  /**
   * `If-Match` for a row this service has seen, and nothing at all for one it has not.
   *
   * Exactly that state, with no falling back to the other one: the server compares a write against
   * the row it is about to change, so sending the live row's version for a write to the draft is a
   * refusal the caller did nothing to deserve — and the way out a caller finds for that is to stop
   * sending the header. An unconditional write is the honest answer when nothing was read.
   */
  private ifMatch(id: string, state: ShPostState): Record<string, string> {
    const held = this.versionOf(id, state)
    return held ? { "If-Match": held } : {}
  }

  /** For a delete, which is about the post: either row proves the caller read it as it is now. */
  private ifMatchEither(id: string): Record<string, string> {
    const held = this.versionOf(id, "DRAFT") ?? this.versionOf(id, "PUBLISHED")
    return held ? { "If-Match": held } : {}
  }

  async get(id: string, state: ShPostState = "PUBLISHED", read: ShRead = {}): Promise<ShPostUnified> {
    const response = await axios.get<ShPostUnified>(`/v2/post-unified/${id}`, {
      params: { state },
      signal: read.signal,
    })
    this.remember(id, state, response.headers)
    return response.data
  }

  async versions(id: string): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/${id}/versions`)
    return response.data
  }

  /**
   * SH988 — two versions of a page and what changed, answered by the server. A side is `draft`,
   * `published` or `rev:<n>`; `before` is null for a page never published, which is a create.
   */
  async diff(id: string, from: string, to: string, read: ShRead = {}): Promise<ShPostDiff> {
    const response = await axios.get<ShPostDiff>(`/v2/post-unified/${id}/diff`,
      { params: { from, to }, signal: read.signal })
    return response.data
  }

  /** SH987 — the captured revisions, newest first, without their bodies. */
  async revisions(id: string, read: ShRead = {}): Promise<ShRevisionRow[]> {
    const response = await axios.get<ShRevisionRow[]>(`/v2/post-unified/${id}/revisions`,
      { signal: read.signal })
    return response.data
  }

  /** SH987 — put one revision back into the draft; it publishes nothing and adds a revision. */
  async restoreRevision(id: string, version: number): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(
      `/v2/post-unified/${id}/revisions/${version}/restore`, undefined,
      { headers: this.ifMatch(id, "DRAFT") })
    return response.data
  }

  async listByFolder(folderId: string, state: ShPostState = "PUBLISHED"): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/folder/${folderId}`, {
      params: { state },
    })
    return response.data
  }

  async create(req: CreateUnifiedPostRequest): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>("/v2/post-unified", req)
    return response.data
  }

  /**
   * Save the whole state (SH168), conditional on the version this service last saw (SH985).
   *
   * A 409 is the server saying somebody wrote in between. It carries a problem document whose
   * `fix` says to read again rather than to resend — resending is the overwrite the guard was
   * asked to prevent — and the editor answers it with the conflict view.
   */
  async update(id: string, req: UpdateUnifiedPostRequest): Promise<ShPostUnified> {
    const written = req.state ?? "DRAFT"
    const response = await axios.put<ShPostUnified>(`/v2/post-unified/${id}`, req, {
      headers: this.ifMatch(id, written),
    })
    this.remember(id, written, response.headers)
    return response.data
  }

  /**
   * Merge a few fields into the draft (SH335), conditional the same way.
   *
   * The Universal Editor's path, and the one where the collision is likeliest: two or three fields
   * written from a page a curator has had open while an agent worked on the same post.
   */
  async patch(
    id: string,
    attrs: Record<string, unknown>,
    publish = false
  ): Promise<ShPostUnified> {
    const response = await axios.patch<ShPostUnified>(`/v2/post-unified/${id}`, { attrs, publish },
      { headers: this.ifMatch(id, "DRAFT") })
    this.remember(id, "DRAFT", response.headers)
    return response.data
  }

  async delete(id: string): Promise<void> {
    await axios.delete(`/v2/post-unified/${id}`, { headers: this.ifMatchEither(id) })
    this.forgetVersion(id)
  }

  /**
   * Promote the draft to the live snapshot (SH654).
   *
   * The server reads the stored `DRAFT` row, so a caller with unsaved form state must
   * {@link update} first — publishing what the curator did not see is the failure this
   * split exists to make impossible to write by accident.
   *
   * Throws where no draft row exists (`Draft not found`), which is why the edit screen
   * only offers the button while it is editing one.
   */
  /**
   * @param override SH998 — the reason a curator gave for publishing past a failing gate. Sent only
   *                 when they typed one: an absent parameter is "no override", and an empty one is
   *                 an override with nothing written down, which the server refuses on purpose.
   * @param redirect SH1064 — keep the URL this publish vacates answering. The server decides
   *                 whether there is one to keep: it compares the live row with the draft, so a
   *                 checkbox left ticked on an ordinary publish writes nothing.
   */
  async publish(id: string, override?: string, redirect?: boolean): Promise<ShPostUnified> {
    const params: Record<string, string | boolean> = {}
    if (override) params.override = override
    if (redirect) params.redirect = true
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/publish`, undefined,
      { headers: this.ifMatch(id, "DRAFT"), params: Object.keys(params).length ? params : undefined })
    // The draft row survives a publish, so the tag the write answers with is the draft's.
    this.remember(id, "DRAFT", response.headers)
    return response.data
  }

  /**
   * SH996 — what publishing this draft would do, written by nothing.
   *
   * The server answers the agent's change-set shape, so the console shows the plan the batch surface
   * would have produced rather than a second description of publishing.
   */
  /*
   * SH1153 — this one takes a signal even though it is a POST, and that is not a contradiction of
   * `read-options.ts`'s "writes do not take this". The rule there is about a request that *changes*
   * something: aborting it does not undo it, so a cancelled write is a write whose answer was thrown
   * away. `dryRun=true` changes nothing — it is a read wearing a write's verb, because the plan it
   * returns is computed by the writer — so hanging up on it loses only an answer nobody wants.
   */
  async publishPlan(id: string, read: ShRead = {}): Promise<ShPublishPlan> {
    const response = await axios.post<ShPublishPlan>(
      `/v2/post-unified/${id}/publish?dryRun=true`, undefined, { signal: read.signal })
    return response.data
  }

  /**
   * SH996 — which URLs that publish would move, from the read that computes them.
   *
   * Asked by `id:` address, the escape hatch SH172 kept for exactly this: the console holds an id and
   * the agent surface is addressed. Absent for a publish that moves no URL, which is most of them.
   */
  async publishRoutes(id: string, read: ShRead = {}): Promise<ShPublishRoutes | null> {
    const response = await axios.get<{ impact?: { routes?: ShPublishRoutes } }[]>("/v2/agent/read", {
      params: { address: `id:${id}`, include: "impact" },
      signal: read.signal,
    })
    return response.data?.[0]?.impact?.routes ?? null
  }

  /**
   * SH1021 — a verb over a selection, through the writer the agent's batch runs through.
   *
   * <p>The menu offered copy, move and delete, and publishing a selection meant publishing one post
   * at a time — which is not what an agent does, and not atomic. This is one call to one writer, so
   * the two surfaces cannot disagree about what publishing means.</p>
   *
   * <p>`dryRun` is the caller's, and every path here sends it deliberately: a bulk verb is the one
   * shape where a mistake is multiplied by the size of the selection.</p>
   */
  async bulk(op: "PUBLISH" | "UNPUBLISH" | "DELETE", ids: string[],
             options: { dryRun: boolean; confirm?: string; when?: string } = { dryRun: true },
  ): Promise<ShBulkResult> {
    const response = await axios.post<ShBulkResult>("/v2/post-unified/bulk", {
      op, ids, dryRun: options.dryRun, confirm: options.confirm, when: options.when,
    })
    return response.data
  }

  /**
   * SH1132 — rename this page, through the writer the agent's `post.move` runs through.
   *
   * <p>`dryRun` defaults to true for the reason a bulk verb's does, and one more that is specific
   * to a rename: **both state rows move together**, so renaming a published page changes the live
   * URL at once with no publish step. The plan is what stops that being a surprise — it says
   * whether the page is live, which address is being vacated, and how many pages link to it.</p>
   */
  async move(id: string, url: string,
             options: { redirect?: boolean; dryRun?: boolean } = {},
  ): Promise<ShMovePlan> {
    const dryRun = options.dryRun ?? true
    const response = await axios.post<ShMovePlan>(`/v2/post-unified/${id}/move`, {
      url,
      redirect: options.redirect ?? false,
      dryRun,
    // SH985/SH1132 — conditional on the real run, which is where the lost update is: a curator
    // renaming a page an agent has already moved would otherwise take the address back. `either`
    // because a rename is about the post rather than one snapshot. A plan writes nothing and sends
    // none, so the harmless step is not the one that needs a fresh read.
    }, { headers: dryRun ? {} : this.ifMatchEither(id) })
    // The address is part of the post, so every version this service held for it is now stale.
    if (!dryRun) this.forgetVersion(id)
    return response.data
  }

  /** Delete the live snapshot, leaving the draft to go on being edited (SH654). */
  async unpublish(id: string): Promise<void> {
    await axios.post(`/v2/post-unified/${id}/unpublish`)
  }

  /** Revert the draft to the last published snapshot (SH14). */
  async restore(id: string): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/restore`)
    return response.data
  }

  /** Set or clear the scheduled publish/unpublish timestamps (SH15). null clears. */
  async schedule(
    id: string,
    req: { publishAt: string | null; unpublishAt: string | null }
  ): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/schedule`, req)
    return response.data
  }

  /** Sibling translations of this post's group (SH53), DRAFT-preferred. */
  async translations(id: string, read: ShRead = {}): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>(`/v2/post-unified/${id}/translations`, { signal: read.signal })
    return response.data
  }

  /** Create a linked translation of this post in another locale (SH53). */
  async translate(id: string, locale: string, data?: Record<string, unknown>): Promise<ShPostUnified> {
    const response = await axios.post<ShPostUnified>(`/v2/post-unified/${id}/translate`, { locale, data })
    return response.data
  }

  /** Posts currently in the trash for the active tenant (SH55). */
  async trash(read: ShRead = {}): Promise<ShPostUnified[]> {
    const response = await axios.get<ShPostUnified[]>("/v2/post-unified/trash",
      { signal: read.signal })
    return response.data
  }

  /** Restore a trashed post (SH55). */
  async untrash(id: string): Promise<void> {
    await axios.post(`/v2/post-unified/${id}/untrash`)
  }

  /** Permanently delete a trashed post (SH55). */
  async purge(id: string): Promise<void> {
    await axios.delete(`/v2/post-unified/${id}/purge`)
  }
}
