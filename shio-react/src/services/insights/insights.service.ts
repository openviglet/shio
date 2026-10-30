import axios from "@/lib/axios"
import type { ShRead } from "@/lib/read-options"

/** One thing the linter found, as `/agent/verify` returns it. */
export interface ShInsightFinding {
  check: string
  rule: string
  severity: string
  address?: string
  /** The field it is about, where the rule knows one — what lets the panel anchor to an input. */
  field?: string
  message: string
  fix?: string
}

/** The lint report, narrowed to what a panel about one page needs. */
export interface ShInsightReport {
  ok: boolean
  checked: number
  errors: number
  warnings: number
  findings: ShInsightFinding[]
}

/** One image as the digest inventories it — `alt` absent means the attribute is absent. */
export interface ShDigestImage {
  src: string
  alt?: string
}

/** A page's shape, as `/agent/render` computes it (SH101). */
export interface ShPageDigest {
  headings: { level: number; text: string }[]
  landmarks?: Record<string, number>
  links: { href: string; internal?: boolean; text?: string }[]
  images: ShDigestImage[]
  words: number
  digest?: string
}

/**
 * SH1013 — the content intelligence the backend already computes, asked for by the editor.
 *
 * <h2>Two reads, because they answer different questions</h2>
 *
 * <p>`/agent/verify` is the linter: rules with a fix each, and — since SH955 — the field the
 * finding is about, which is what lets a finding become a link to an input. `/agent/render` is the
 * structural digest: the heading outline, the link and image inventory, the word count. Neither
 * substitutes for the other, and the console called neither.</p>
 *
 * <h2>Why the image-alt count comes from the digest and not from the linter</h2>
 *
 * <p>`ShAgentVerifyService.markup` scans **published rows only**, deliberately — a draft's broken
 * link is work in progress, and a site-wide sweep that reported every half-written page would be
 * noise. That decision is right where it is and this does not touch it. But the panel's subject is
 * the page in front of the curator, where a missing alt is worth saying now, and the digest
 * inventories the images of a draft with their alt text. So the same fact arrives by the read whose
 * scope already matches the question.</p>
 */
export class ShInsightsService {
  /** What the linter says about this page as it is being edited. */
  async verify(address: string, read: ShRead = {}): Promise<ShInsightReport> {
    const response = await axios.get<ShInsightReport>("/v2/agent/verify", {
      params: { address, state: "draft" },
      signal: read.signal,
    })
    return response.data
  }

  /**
   * The page's shape. Absent rather than thrown when it cannot render — a post with no layout has
   * no digest, which is a fact about the page and not an error the curator has to dismiss.
   */
  async digest(address: string, read: ShRead = {}): Promise<ShPageDigest | null> {
    return axios
      .get<ShPageDigest>("/v2/agent/render", { params: { address, state: "draft" }, signal: read.signal })
      .then((response) => response.data ?? null)
      .catch(() => null)
  }
}

/**
 * SH1014 — the page as it would look, from the values on screen.
 *
 * <p>`POST /v2/agent/render` renders through the same service `/preview` uses, over a transient
 * copy of the post, and persists nothing. `format=html` is what a frame needs; without it the same
 * call answers the digest, which is what an agent wants and what this would otherwise pay for.</p>
 *
 * <p>A POST that takes a signal, which `read-options.ts` reserves for reads — and this is one: the
 * verb carries a body the query string could not, and nothing is written (SH1116). Aborting it loses
 * only a render that a newer keystroke or a save has already superseded.</p>
 */
export class ShProposedRenderService {
  /** SH1323 — `on` draws a section's values on a page that uses it, a section having no layout. */
  async html(address: string, data: Record<string, unknown>, read: ShRead = {}, on?: string): Promise<string> {
    const response = await axios.post<{ html?: string }>(
      "/v2/agent/render?format=html",
      { address, state: "draft", data, on },
      { signal: read.signal },
    )
    return response.data?.html ?? ""
  }
}
