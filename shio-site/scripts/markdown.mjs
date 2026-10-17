// SH537: the Markdown twin of a prerendered page.
//
// ## Why this converts HTML rather than rendering from the content data
//
// The copy lives in `src/lib/*-content.ts` as data, but the *composition* (which
// sections, in what order, under what heading) lives in the JSX. Generating
// Markdown from the data alone would mean re-declaring that composition here,
// which is a second hand-maintained copy of the page, and the divergence always
// lands in the version nobody reads. So the source stays singular and only the
// rendering is doubled: the prerender already produces the page's markup, and
// this turns that markup into Markdown.
//
// ## What it deliberately drops
//
// The nav and the footer. They are chrome, they are identical on all ten routes,
// and repeating them in every `.md` is exactly the token waste this whole site
// argues against. Only `<main>` is converted. Icons (`<svg>`), decorative
// elements (`aria-hidden`) and anything in a `<button>` go the same way.
//
// ## The failure mode it refuses
//
// Unknown markup degrades to its text rather than throwing or vanishing. A build
// that fails on a new tag is a build somebody routes around; a converter that
// silently drops one loses content nobody notices. Both are worse than a
// paragraph that lost its emphasis.

/** Elements that never have a closing tag. */
const VOID = new Set([
  "area", "base", "br", "col", "embed", "hr", "img", "input",
  "link", "meta", "param", "source", "track", "wbr",
]);

/** Dropped whole, children included. */
const DROP = new Set(["svg", "script", "style", "button", "noscript"]);

const BLOCK = new Set([
  "address", "article", "aside", "blockquote", "div", "dl", "dd", "dt",
  "figcaption", "figure", "footer", "form", "h1", "h2", "h3", "h4", "h5", "h6",
  "header", "hr", "li", "main", "nav", "ol", "p", "pre", "section", "table",
  "tbody", "td", "th", "thead", "tr", "ul",
]);

const ENTITIES = {
  amp: "&", lt: "<", gt: ">", quot: '"', "#39": "'", apos: "'", nbsp: " ",
  mdash: "\u2014", ndash: "–", rsquo: "’", lsquo: "‘",
  ldquo: "“", rdquo: "”", hellip: "…", middot: "·",
  rarr: "→", times: "×", copy: "©",
};

export function decodeEntities(text) {
  return text.replace(/&(#x?[0-9a-fA-F]+|[a-zA-Z]+);/g, (whole, name) => {
    if (Object.hasOwn(ENTITIES, name)) return ENTITIES[name];
    if (name.startsWith("#x") || name.startsWith("#X")) {
      return String.fromCodePoint(Number.parseInt(name.slice(2), 16));
    }
    if (name.startsWith("#")) return String.fromCodePoint(Number.parseInt(name.slice(1), 10));
    return whole;
  });
}

/**
 * Parse well-formed markup into a tree.
 *
 * A scanner rather than a general parser, and the same rule the CLI's tag reader
 * is written under: one linear pass, quote-aware inside a tag, no repeated group
 * that could backtrack. The input is this site's own SSR output, so the strictness
 * it can assume is real, but an unbalanced close tag still unwinds to the nearest
 * matching open instead of throwing.
 */
export function parseHtml(html) {
  const root = { tag: "#root", attrs: {}, children: [] };
  const stack = [root];
  let i = 0;

  const pushText = (raw) => {
    if (!raw) return;
    stack.at(-1).children.push({ text: decodeEntities(raw) });
  };

  while (i < html.length) {
    const lt = html.indexOf("<", i);
    if (lt === -1) {
      pushText(html.slice(i));
      break;
    }
    pushText(html.slice(i, lt));

    // Comments and doctype: skipped whole.
    if (html.startsWith("<!--", lt)) {
      const end = html.indexOf("-->", lt);
      i = end === -1 ? html.length : end + 3;
      continue;
    }
    if (html.startsWith("<!", lt)) {
      const end = html.indexOf(">", lt);
      i = end === -1 ? html.length : end + 1;
      continue;
    }

    // Find the end of the tag, respecting quoted attribute values.
    let j = lt + 1;
    let quote = "";
    while (j < html.length) {
      const ch = html[j];
      if (quote) {
        if (ch === quote) quote = "";
      } else if (ch === '"' || ch === "'") {
        quote = ch;
      } else if (ch === ">") {
        break;
      }
      j += 1;
    }
    if (j >= html.length) {
      pushText(html.slice(lt));
      break;
    }

    const inner = html.slice(lt + 1, j);
    i = j + 1;

    if (inner.startsWith("/")) {
      const name = inner.slice(1).trim().toLowerCase();
      // Unwind to the nearest matching open tag; ignore a close with no open.
      const at = stack.findLastIndex((n) => n.tag === name);
      if (at > 0) stack.length = at;
      continue;
    }

    const selfClosing = inner.endsWith("/");
    const body = selfClosing ? inner.slice(0, -1) : inner;
    const space = body.search(/\s/);
    const tag = (space === -1 ? body : body.slice(0, space)).toLowerCase();
    const node = { tag, attrs: parseAttrs(space === -1 ? "" : body.slice(space)), children: [] };
    stack.at(-1).children.push(node);
    if (!selfClosing && !VOID.has(tag)) stack.push(node);
  }

  return root;
}

function parseAttrs(source) {
  const attrs = {};
  const re = /([a-zA-Z_:][-\w:.]*)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+)))?/g;
  let m = re.exec(source);
  while (m !== null) {
    attrs[m[1].toLowerCase()] = decodeEntities(m[2] ?? m[3] ?? m[4] ?? "");
    m = re.exec(source);
  }
  return attrs;
}

/** Find the first descendant with this tag, or null. */
export function findTag(node, tag) {
  if (node.tag === tag) return node;
  for (const child of node.children ?? []) {
    const hit = findTag(child, tag);
    if (hit) return hit;
  }
  return null;
}

const skipped = (node) =>
  DROP.has(node.tag) ||
  node.attrs?.["aria-hidden"] === "true" ||
  node.attrs?.hidden === "" ||
  // The page's own declaration that a region is chrome. A call to action is a
  // conversion device for a person and forty repeated words for an agent, and
  // guessing at it from the markup would be a heuristic that breaks silently.
  node.attrs?.["data-md-skip"] !== undefined;

/**
 * Join two inline fragments, inserting the space the markup implied.
 *
 * A law chip is a `<span>` beside a heading's text, and concatenating them gives
 * `three consumersP8`; two buttons in a flex row give `[a](x)[b](y)`. But the
 * same junction before punctuation must NOT gain a space, or every inline
 * `<code>` mid-sentence is followed by a floating full stop. So: only when the
 * right side starts a word.
 */
function joinInline(left, right) {
  if (!left || !right) return left + right;
  const needsSpace = !/\s$/.test(left) && /^[\w[(]/.test(right);
  return needsSpace ? `${left} ${right}` : left + right;
}

/** The text of a subtree, with inline emphasis and links kept as Markdown. */
function inline(node) {
  if (node.text !== undefined) return node.text;
  if (skipped(node)) return "";
  const inner = node.children.reduce(
    (acc, child) => (child.text !== undefined ? acc + inline(child) : joinInline(acc, inline(child))),
    ""
  );
  const trimmed = inner.trim();
  if (!trimmed) return "";
  switch (node.tag) {
    case "code":
      // A `pre > code` is handled by the block writer; this is the inline form.
      return `\`${trimmed}\``;
    case "strong":
    case "b":
      return `**${trimmed}**`;
    case "em":
    case "i":
      return `*${trimmed}*`;
    case "a": {
      const href = node.attrs.href;
      return href && !href.startsWith("#") ? `[${trimmed}](${href})` : trimmed;
    }
    default:
      return inner;
  }
}

/** Collapse runs of whitespace, which JSX indentation produces in quantity. */
const tidy = (s) => s.replace(/\s+/g, " ").trim();

function trimNewlines(s) {
  let start = 0;
  let end = s.length;
  while (start < end && s[start] === "\n") start += 1;
  while (end > start && s[end - 1] === "\n") end -= 1;
  return s.slice(start, end);
}

function writeBlocks(node, out, depth) {
  if (skipped(node)) return;

  switch (node.tag) {
    case "h1":
    case "h2":
    case "h3":
    case "h4":
    case "h5":
    case "h6": {
      const text = tidy(inline(node));
      if (text) out.push(`${"#".repeat(Number(node.tag[1]))} ${text}`);
      return;
    }
    case "p":
    case "figcaption":
    case "dd": {
      const text = tidy(inline(node));
      if (text) out.push(text);
      return;
    }
    case "dt": {
      const text = tidy(inline(node));
      if (text) out.push(`**${text}**`);
      return;
    }
    case "pre": {
      // Trimmed by index, not by /^\n+|\n+$/: an anchored repeated group over a
      // run of newlines backtracks super-linearly, and a code block is the one
      // place a long run of them is ordinary.
      const code = trimNewlines(preText(node));
      if (code) out.push(`\`\`\`\n${code}\n\`\`\``);
      return;
    }
    case "li": {
      // A list item may itself hold blocks; take its own text first, then recurse
      // into anything block-shaped so a nested list is not swallowed.
      const own = tidy(node.children.filter((c) => !isBlock(c)).map(inline).join(""));
      if (own) out.push(`${"  ".repeat(depth)}- ${own}`);
      for (const child of node.children) {
        if (isBlock(child)) writeBlocks(child, out, depth + 1);
      }
      return;
    }
    case "tr": {
      const cells = node.children
        .filter((c) => c.tag === "td" || c.tag === "th")
        .map((c) => tidy(inline(c)) || "-");
      if (!cells.length) return;
      out.push(`| ${cells.join(" | ")} |`);
      if (node.children.some((c) => c.tag === "th")) {
        out.push(`| ${cells.map(() => "---").join(" | ")} |`);
      }
      return;
    }
    case "ul":
    case "ol":
      for (const child of node.children) writeBlocks(child, out, depth);
      return;
    default:
      break;
  }

  // A container whose children are all inline is a paragraph; otherwise recurse.
  if (node.children.some(isBlock)) {
    for (const child of node.children) {
      if (isBlock(child)) writeBlocks(child, out, depth);
    }
  } else {
    const text = tidy(inline(node));
    if (text) out.push(text);
  }
}

const isBlock = (n) => n.tag !== undefined && (BLOCK.has(n.tag) || DROP.has(n.tag));

/** Text with no entity-decoding surprises and no inline markers, for `<pre>`. */
function rawText(node) {
  if (node.text !== undefined) return node.text;
  if (skipped(node)) return "";
  return node.children.map(rawText).join("");
}

/**
 * The raw text of a `<pre>`, with one line per line-shaped child.
 *
 * A code block on this site comes in two markups. `CodeBlock` puts one string
 * with real newlines inside `<code>`, and concatenating its children is right.
 * The session panels instead emit one `<span class="block">` per line (because
 * each line is coloured independently), and those spans carry no newline at
 * all: concatenating them ran a six-line file tree into one unreadable line in
 * the Markdown twin, which is the failure this whole converter exists to avoid.
 *
 * So a child that renders as its own line contributes one. Detected from the
 * class rather than from the tag, because the tag is `span` in both cases and
 * only the class says which one is a line.
 */
function preText(node) {
  const parts = [];
  const walk = (n) => {
    if (n.text !== undefined) {
      parts.push(n.text);
      return;
    }
    if (skipped(n)) return;
    const isLine = (n.attrs?.class ?? "").split(/\s+/).includes("block");
    if (isLine && parts.length && !parts.at(-1).endsWith("\n")) parts.push("\n");
    n.children.forEach(walk);
    if (isLine) parts.push("\n");
  };
  node.children.forEach(walk);
  return parts.join("");
}

/**
 * Render one page's `<main>` to Markdown.
 *
 * `title` and `description` come from the prerender's route table, so the twin
 * carries the same metadata the HTML `<head>` does: an agent that fetches only
 * this file still learns what the page is.
 */
export function pageToMarkdown(html, { title, description, canonical }) {
  const main = findTag(parseHtml(html), "main");
  const out = [];
  if (main) writeBlocks(main, out, 0);

  // Blocks are separated by a blank line, except within a list or a table,
  // where a blank line between the rows breaks them into separate lists and
  // stops the table being a table at all.
  const tight = (line) => /^\s*- /.test(line) || line.startsWith("| ");
  let body = "";
  for (const [i, line] of out.entries()) {
    if (i > 0) body += tight(line) && tight(out[i - 1]) ? "\n" : "\n\n";
    body += line;
  }

  return [
    `# ${title}`,
    "",
    `> ${description}`,
    "",
    `Canonical: ${canonical}`,
    "",
    "---",
    "",
    body,
    "",
  ].join("\n");
}
