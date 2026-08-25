/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * Find JSX elements by tag name, without being fooled by `>` inside an attribute.
 *
 * ## Why this is not a regex
 *
 * `/<Button\b([^>]*?)>([\s\S]*?)<\/Button>/` is the obvious version and it is **wrong in the
 * direction that matters**: an arrow function in a prop —
 *
 *     <Button
 *       size="icon"
 *       onClick={() => openBrowserAt()}
 *     >
 *       <Search />
 *     </Button>
 *
 * — contains a `>` in `=>`, so `[^>]*?` ends the opening tag there. The "attributes" become
 * everything up to `onClick={()` and the "body" starts mid-prop, carrying the handler's own
 * identifier. Any check that asks "does the body contain readable text?" then answers *yes*,
 * because `openBrowserAt` is letters.
 *
 * That is a **false negative**, and SH893's first measurement had it: two adjacent unnamed
 * icon buttons in `content-select.widget.tsx`, one reported and one not, differing only in
 * whether their `onClick` was an arrow. A gate that under-reports is worse than no gate — it is
 * a green run that reads as coverage.
 *
 * ## What it does instead
 *
 * Scans forward from the tag name tracking brace depth and string quotes, so the opening tag
 * ends at the first `>` that is genuinely outside a `{…}` expression and outside a quoted
 * string. Then, for a non-self-closing element, matches `</Tag>` accounting for nesting of the
 * same tag.
 *
 * Still not a parser: it does not understand comments containing `{`, or template literals with
 * nested braces. Both are visible in the elements this is pointed at, and a real parser is a
 * dependency this package's test suite does not have. Where a case is genuinely ambiguous the
 * scanner returns nothing for it rather than guessing, so the failure mode is a finding that is
 * missed loudly (the count moves) rather than an element silently mis-parsed.
 */
export function findElements(source, tagName) {
  const found = []
  const open = new RegExp(`<${tagName}(?=[\\s/>])`, "g")

  for (const match of source.matchAll(open)) {
    const tagEnd = endOfOpeningTag(source, match.index + match[0].length)
    if (tagEnd === -1) continue

    const attrs = source.slice(match.index + match[0].length, tagEnd.index)
    const line = source.slice(0, match.index).split("\n").length

    if (tagEnd.selfClosing) {
      found.push({ tag: tagName, attrs, body: "", line, index: match.index, selfClosing: true })
      continue
    }

    const body = bodyOf(source, tagEnd.index + 1, tagName)
    if (body === null) continue
    found.push({ tag: tagName, attrs, body, line, index: match.index, selfClosing: false })
  }
  return found
}

/**
 * The index of the `>` closing an opening tag, skipping any inside `{…}` or a string.
 *
 * Exported since SH926: a caller that needs **nesting** rather than bodies cannot use
 * `findElements` for it. `bodyOf` treats every `<Tag` before the next `</Tag>` as a nested open,
 * including self-closing ones, so a container with thirty self-closing children of the same tag —
 * which is exactly what `App.tsx`'s route trees are — never balances and the element is dropped.
 * Reading tag ends directly is the honest way to walk that, and it is why `spa-contexts.test.mjs`
 * does its own depth counting instead.
 */
export function endOfOpeningTag(source, from) {
  let depth = 0
  let quote = null
  for (let i = from; i < source.length; i++) {
    const ch = source[i]
    if (quote) {
      if (ch === "\\") i++
      else if (ch === quote) quote = null
      continue
    }
    if (ch === '"' || ch === "'" || ch === "`") { quote = ch; continue }
    if (ch === "{") { depth++; continue }
    if (ch === "}") { depth--; continue }
    if (depth > 0) continue
    if (ch === "/" && source[i + 1] === ">") return { index: i + 1, selfClosing: true }
    if (ch === ">") return { index: i, selfClosing: false }
  }
  return -1
}

/** Everything up to the matching `</Tag>`, allowing the same tag to nest inside itself. */
function bodyOf(source, from, tagName) {
  const openTag = new RegExp(`<${tagName}(?=[\\s/>])`, "g")
  const closeTag = `</${tagName}>`
  let depth = 0
  let i = from
  while (i < source.length) {
    const close = source.indexOf(closeTag, i)
    if (close === -1) return null
    openTag.lastIndex = i
    let nested = 0
    let m
    while ((m = openTag.exec(source)) !== null && m.index < close) nested++
    if (nested === 0 && depth === 0) return source.slice(from, close)
    depth += nested - 1
    i = close + closeTag.length
    if (depth < 0) return source.slice(from, close)
  }
  return null
}
