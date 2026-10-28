import type { ReactNode } from "react"

/**
 * SH1253 — a package README, rendered rather than shown as raw markdown.
 *
 * <p>No library: the console has no markdown renderer, and a README here is the short document
 * `shio-site/scripts/marketplace.mjs` generates (headings, paragraphs, lists, fenced code, inline
 * code, bold, links). This covers exactly that and builds React elements, never HTML strings, so a
 * README from a stranger's catalogue cannot inject markup: an unrecognised construct is shown as
 * the text it is. Loaded only when a dialog opens, beside the setup panel.</p>
 */
export function MarketplaceReadme({ source }: { source: string }) {
  return <div className="flex flex-col gap-2 text-sm">{blocks(source)}</div>
}

function blocks(source: string): ReactNode[] {
  const out: ReactNode[] = []
  const lines = source.replace(/\r\n/g, "\n").split("\n")
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    if (line.startsWith("```")) {
      const code: string[] = []
      i++
      while (i < lines.length && !lines[i].startsWith("```")) code.push(lines[i++])
      i++
      out.push(
        <pre key={out.length} className="overflow-auto rounded-md bg-muted p-2 text-xs">
          {code.join("\n")}
        </pre>,
      )
      continue
    }
    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      const size = heading[1].length <= 1 ? "text-base" : "text-sm"
      out.push(
        <p key={out.length} className={`${size} font-semibold`}>
          {inline(heading[2])}
        </p>,
      )
      i++
      continue
    }
    if (/^\s*[-*]\s+/.test(line)) {
      const items: string[] = []
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) items.push(lines[i++].replace(/^\s*[-*]\s+/, ""))
      out.push(
        <ul key={out.length} className="list-disc pl-5">
          {items.map((item, index) => (
            <li key={index}>{inline(item)}</li>
          ))}
        </ul>,
      )
      continue
    }
    if (line.trim() === "" || /^-{3,}$/.test(line.trim())) {
      i++
      continue
    }
    const paragraph: string[] = []
    while (i < lines.length && lines[i].trim() !== "" && !/^(#|```|\s*[-*]\s)/.test(lines[i])) {
      paragraph.push(lines[i++])
    }
    out.push(
      <p key={out.length} className="text-muted-foreground">
        {inline(paragraph.join(" "))}
      </p>,
    )
  }
  return out
}

/** `code`, **bold** and [text](https://…) links; everything else is text. */
function inline(text: string): ReactNode[] {
  const out: ReactNode[] = []
  const pattern = /`([^`]+)`|\*\*([^*]+)\*\*|\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g
  let last = 0
  for (const match of text.matchAll(pattern)) {
    if (match.index > last) out.push(text.slice(last, match.index))
    if (match[1] !== undefined) out.push(<code key={out.length}>{match[1]}</code>)
    else if (match[2] !== undefined) out.push(<strong key={out.length}>{match[2]}</strong>)
    else
      out.push(
        <a key={out.length} href={match[4]} target="_blank" rel="noreferrer noopener" className="underline">
          {match[3]}
        </a>,
      )
    last = match.index + match[0].length
  }
  if (last < text.length) out.push(text.slice(last))
  return out
}
