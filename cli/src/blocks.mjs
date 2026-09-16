/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1015 — a blocks document as a Markdown file, and back.
 *
 * ## Why this exists twice
 *
 * `ShBlocks.markdown` is the reference implementation and this is the one `shio pull` runs. Two
 * implementations of one rule drift, so `blocks-parity.test.mjs` runs both over the same fixtures
 * and fails when they disagree — the instrument is what makes the duplication safe, and without it
 * this file would be a second opinion rather than a second copy.
 *
 * The alternative was asking the instance to project, and it is worse: a projection is what `pull`
 * writes to disk, and a `pull` that cannot format a file without a round trip to the server is not
 * the files tier P4 describes.
 *
 * ## Why the keys ride in the frontmatter
 *
 * A block's `key` is its identity and Markdown has nowhere to put one. Three options and this is
 * the least bad:
 *
 * - **In the body**, as a comment per block. Exact, and it litters the file an author edits, which
 *   is the file P4 exists to make pleasant.
 * - **Derived** from content. Then editing a paragraph changes its identity, which is the opposite
 *   of what a key is for.
 * - **Beside the body**, as an ordered list in the frontmatter. Exact for an unedited file — which
 *   is the property `shio pull` needs, since that is what makes a re-pull produce identical bytes
 *   and the three-way merge's base trustworthy.
 *
 * Its failure mode is named rather than hidden: insert a paragraph in the middle by hand and the
 * keys after it shift by one, so those blocks are re-identified. Nothing outside the document
 * references a key today, so the cost is that an editor re-keys those blocks once. Content is never
 * lost or reordered, which is the property that mattered.
 */

/** The key in a post's frontmatter that carries the block identities, in order. */
export const BLOCK_KEYS = "blockKeys";

const FENCE = "```";

/** Markdown for one block, matching `ShBlocks.markdownBlock` exactly. */
function blockToMarkdown(block) {
  const text = marked(block);
  switch (block?.type) {
    case "heading":
      return `${"#".repeat(Math.max(1, block.level ?? 1))} ${text ?? ""}`;
    case "quote":
      return `> ${(text ?? "").replace(/\n/gu, "\n> ")}`;
    case "code":
      return `${FENCE}${block.language ?? ""}\n${block.text ?? ""}\n${FENCE}`;
    case "image":
      return `![${block.alt ?? ""}](${block.src ?? ""})`;
    case "embed":
      return `<!-- embed ${block.url ?? ""} -->`;
    case "reference":
      return `<!-- reference ${block.address ?? ""} -->`;
    case "list":
      return (block.items ?? [])
        .map((item, index) => (block.ordered ? `${index + 1}. ${item}` : `- ${item}`))
        .join("\n");
    default:
      return text ?? "";
  }
}

/**
 * The block's text with its marks applied, back to front.
 *
 * Front to back would need every later mark's offsets adjusted by what the earlier ones inserted,
 * which is the same bug written more expensively — and it is the shape that silently mis-places an
 * emphasis rather than failing.
 */
function marked(block) {
  const text = typeof block?.text === "string" ? block.text : null;
  if (text === null) return null;
  const marks = Array.isArray(block.marks) ? [...block.marks] : [];
  if (!marks.length) return text;

  marks.sort((a, b) => (b.from ?? 0) - (a.from ?? 0));
  let out = text;
  for (const mark of marks) {
    const from = mark.from ?? 0;
    const to = mark.to ?? 0;
    if (from < 0 || to > out.length || to < from) continue;
    if (mark.type === "link") {
      out = `${out.slice(0, to)}](${mark.href ?? ""})${out.slice(to)}`;
      out = `${out.slice(0, from)}[${out.slice(from)}`;
      continue;
    }
    const delimiter = { strong: "**", em: "*", code: "`", strike: "~~" }[mark.type] ?? "";
    if (!delimiter) continue;
    out = `${out.slice(0, to)}${delimiter}${out.slice(to)}`;
    out = `${out.slice(0, from)}${delimiter}${out.slice(from)}`;
  }
  return out;
}

/** A blocks array as the Markdown body of its file. */
export function toMarkdown(blocks) {
  if (!Array.isArray(blocks)) return "";
  return blocks
    .map(blockToMarkdown)
    .filter((written) => written !== "")
    .join("\n\n");
}

/** The keys, in order — what the frontmatter carries so the parse can put them back. */
export function keysOf(blocks) {
  return (Array.isArray(blocks) ? blocks : []).map((block) => block?.key ?? "");
}

/**
 * Markdown back to blocks, with the keys from the frontmatter re-attached by position.
 *
 * A block beyond the key list is new — somebody added a paragraph — and gets a fresh key rather
 * than an empty one: an empty key is refused by the server, which would turn "I added a paragraph"
 * into a failed push with nothing saying why.
 */
export function fromMarkdown(markdown, keys = [], mintKey = defaultKey) {
  const blocks = [];
  const lines = String(markdown ?? "").split(/\r?\n/u);
  let index = 0;

  while (index < lines.length) {
    const line = lines[index];
    if (line.trim() === "") {
      index++;
      continue;
    }

    // A fence first: everything inside it is content, including lines that look like other blocks.
    if (line.startsWith(FENCE)) {
      const language = line.slice(FENCE.length).trim();
      const body = [];
      index++;
      while (index < lines.length && !lines[index].startsWith(FENCE)) {
        body.push(lines[index]);
        index++;
      }
      index++;
      blocks.push({ type: "code", language, text: body.join("\n") });
      continue;
    }

    const heading = /^(#{1,6})\s+(.*)$/u.exec(line);
    if (heading) {
      blocks.push({ type: "heading", level: heading[1].length, ...unmarked(heading[2]) });
      index++;
      continue;
    }

    const comment = /^<!--\s+(embed|reference)\s+(.*?)\s*-->$/u.exec(line);
    if (comment) {
      blocks.push(comment[1] === "embed"
        ? { type: "embed", url: comment[2] }
        : { type: "reference", address: comment[2] });
      index++;
      continue;
    }

    const image = /^!\[(.*)\]\((.*)\)$/u.exec(line);
    if (image) {
      blocks.push({ type: "image", alt: image[1], src: image[2] });
      index++;
      continue;
    }

    if (/^\s*([-*]|\d+\.)\s+/u.test(line)) {
      const items = [];
      const ordered = /^\s*\d+\.\s+/u.test(line);
      while (index < lines.length && /^\s*([-*]|\d+\.)\s+/u.test(lines[index])) {
        items.push(lines[index].replace(/^\s*([-*]|\d+\.)\s+/u, ""));
        index++;
      }
      blocks.push({ type: "list", ordered, items });
      continue;
    }

    if (line.startsWith("> ")) {
      const body = [];
      while (index < lines.length && lines[index].startsWith("> ")) {
        body.push(lines[index].slice(2));
        index++;
      }
      blocks.push({ type: "quote", ...unmarked(body.join("\n")) });
      continue;
    }

    // Everything else is a paragraph, and it runs until a blank line — a paragraph broken across
    // lines is one block, which is what an author means by wrapping their prose.
    const body = [];
    while (index < lines.length && lines[index].trim() !== ""
      && !lines[index].startsWith(FENCE) && !/^#{1,6}\s/u.test(lines[index])) {
      body.push(lines[index]);
      index++;
    }
    blocks.push({ type: "paragraph", ...unmarked(body.join("\n")) });
  }

  return blocks.map((block, position) => ({
    key: keys[position] || mintKey(position),
    ...block,
  }));
}

/** Delimiters back to marks — the inverse of {@link marked}, left to right over the stripped text. */
function unmarked(raw) {
  const patterns = [
    [/\*\*([^*]+)\*\*/u, "strong", 2],
    [/(?<!\*)\*([^*]+)\*(?!\*)/u, "em", 1],
    [/~~([^~]+)~~/u, "strike", 2],
    [/`([^`]+)`/u, "code", 1],
  ];
  let text = String(raw ?? "");
  const marks = [];

  // One pass per match, re-scanning from the start each time: the offsets a mark records are
  // offsets into the FINAL text, so each removal has to be reflected in the ones already found.
  let changed = true;
  while (changed) {
    changed = false;
    for (const [pattern, type, width] of patterns) {
      const match = pattern.exec(text);
      if (!match) continue;
      const from = match.index;
      const to = from + match[1].length;
      text = text.slice(0, from) + match[1] + text.slice(from + match[0].length);
      for (const mark of marks) {
        if (mark.from > from) {
          mark.from -= width * 2;
          mark.to -= width * 2;
        }
      }
      marks.push({ from, to, type });
      changed = true;
      break;
    }
  }

  marks.sort((a, b) => a.from - b.from);
  return marks.length ? { text, marks } : { text };
}

function defaultKey(position) {
  return `b${position + 1}`;
}
