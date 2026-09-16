/**
 * SH1017 — the block vocabulary, console-side.
 *
 * The authority is `ShBlocks` on the server: it refuses an unknown type, a duplicate key, a heading
 * with no level and a mark that runs off its own text. This mirrors the same vocabulary so the
 * editor offers what the server accepts — an editor that can compose a document the instance will
 * refuse is one that teaches its user a shape that does not exist.
 */

/** The registered kinds, in the order the insert menu offers them. */
export const BLOCK_TYPES = [
  "paragraph", "heading", "list", "quote", "code", "image", "embed", "reference",
] as const

export type ShBlockType = (typeof BLOCK_TYPES)[number]

/** The inline marks a block's text may carry. */
export const MARK_TYPES = ["strong", "em", "code", "link", "strike"] as const

export interface ShBlockMark {
  from: number
  to: number
  type: string
  href?: string
}

export interface ShBlock {
  key: string
  type: ShBlockType
  text?: string
  marks?: ShBlockMark[]
  level?: number
  ordered?: boolean
  items?: string[]
  language?: string
  src?: string
  alt?: string
  url?: string
  address?: string
}

/** A new block of a kind, with the fields that kind requires already present. */
export function blankBlock(type: ShBlockType): ShBlock {
  const block: ShBlock = { key: crypto.randomUUID(), type }
  // A heading without a level is refused by the server, so the editor never composes one: a
  // default here is the difference between a working insert and a teaching 400 on save.
  if (type === "heading") block.level = 2
  if (type === "list") {
    block.ordered = false
    block.items = [""]
  }
  if (type === "paragraph" || type === "quote" || type === "code") block.text = ""
  return block
}

/**
 * Adds a mark, keeping the list sorted and dropping an exact duplicate.
 *
 * Sorted because the stored document is canonicalized by key on the server and a stable order here
 * means the form does not report itself dirty for having re-emitted the same marks (SH1011).
 */
export function withMark(block: ShBlock, mark: ShBlockMark): ShBlock {
  const marks = [...(block.marks ?? [])]
  const already = marks.some((current) => current.from === mark.from && current.to === mark.to
    && current.type === mark.type)
  if (already) return block
  marks.push(mark)
  marks.sort((a, b) => a.from - b.from || a.to - b.to || a.type.localeCompare(b.type))
  return { ...block, marks }
}

export function withoutMark(block: ShBlock, index: number): ShBlock {
  const marks = (block.marks ?? []).filter((_, i) => i !== index)
  return marks.length ? { ...block, marks } : { ...block, marks: undefined }
}

/**
 * Clipboard HTML as blocks.
 *
 * <p>The widget this replaces had no paste handling at all, so a paste from a word processor
 * arrived as the spans and inline styles that processor emits. This reads only the structure the
 * model has a name for — headings, paragraphs, list items, quotes, code — and drops everything
 * else, which is the same rule the server's allow-list follows: a shape with no name is not
 * content this instance can store.</p>
 *
 * <p>Text is taken as text, never as markup, so a paste can never inject a tag. That is the
 * property the block model has and `innerHTML` could not.</p>
 */
export function blocksFromPastedHtml(html: string): ShBlock[] {
  if (typeof DOMParser === "undefined") return []
  const document_ = new DOMParser().parseFromString(html, "text/html")
  const blocks: ShBlock[] = []

  for (const element of Array.from(document_.body.querySelectorAll("h1,h2,h3,h4,h5,h6,p,ul,ol,blockquote,pre"))) {
    const tag = element.tagName.toLowerCase()
    const text = (element.textContent ?? "").trim()
    if (tag === "ul" || tag === "ol") {
      const items = Array.from(element.querySelectorAll("li"))
        .map((item) => (item.textContent ?? "").trim())
        .filter(Boolean)
      if (items.length) {
        blocks.push({ ...blankBlock("list"), ordered: tag === "ol", items })
      }
      continue
    }
    if (!text) continue
    if (/^h[1-6]$/u.test(tag)) {
      blocks.push({ ...blankBlock("heading"), level: Number(tag.slice(1)), text })
    } else if (tag === "blockquote") {
      blocks.push({ ...blankBlock("quote"), text })
    } else if (tag === "pre") {
      blocks.push({ ...blankBlock("code"), text })
    } else {
      blocks.push({ ...blankBlock("paragraph"), text })
    }
  }
  return blocks
}
