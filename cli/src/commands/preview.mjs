/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio preview <file>` (SH1095) — see a content file's page before pushing it.
 *
 * Block I's claim is that `shio/content/**` describes a site completely and that editing files is
 * the agent's cheapest way to author (P4). What it could not do was look at the page **first**:
 * rendering from proposed values reached MCP, REST and the console (SH1014) and the files tier had
 * no verb for it. This is that verb, composed rather than invented (P10): the file is read the way
 * `push` reads it, its fields become the `data` map, and `POST /api/v2/agent/render` renders them
 * through the real page render service without saving anything.
 *
 * Three decisions, made here rather than inherited:
 *
 * - **Which address.** The file names the post it projects (its path, or `site:`/`url:` in the
 *   frontmatter), so the common case needs no flag. `--address` is for a file whose page lives at
 *   another address.
 * - **What it prints.** The page's outline and structural hash on stdout, and the rendered markup
 *   in a file whose path it prints, because a terminal is not a browser.
 * - **A page the instance does not have.** Refused with the reason: a render needs a page to render
 *   *at* (a friendly URL, a layout and a site), which is a fact about the instance, not the file.
 */
import { dirname, isAbsolute, join, relative, resolve } from "node:path";
import { tmpdir } from "node:os";

import { ShioHttpError } from "@viglet/shio-client";

import { ShioAdminClient } from "../admin-client.mjs";
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { ASSETS_DIR, assetLocationOf, fileFields } from "../content-assets.mjs";
import { parsePost } from "../content-files.mjs";
import { CONTENT_DIR } from "../content-store.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("preview");

export async function run(flags, positionals, ctx) {
  const file = positionals[0];
  if (!file) {
    throw new Error("shio preview: pass a content file. Example: shio preview shio/content/Acme/about.md");
  }
  const absolute = isAbsolute(file) ? file : resolve(ctx.cwd, file);
  const text = await ctx.fs.readFile(absolute, "utf8");
  const post = parsePost(contentRelative(ctx.cwd, absolute), text);

  // The sidecars the frontmatter names sit beside the file (SH191), and a render without them is a
  // page missing its code fields: read exactly as `push` reads them.
  const data = { ...post.data };
  for (const [field, name] of Object.entries(post.sidecarFiles ?? {})) {
    data[field] = await ctx.fs.readFile(join(dirname(absolute), name), "utf8");
  }

  const address = flags.address ?? `post:${post.site}${post.url}`;
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const admin = new ShioAdminClient({ baseUrl, user, password, fetch: ctx.fetch });

  const inlined = await inlineAssets(ctx, post, data, admin);

  let rendered;
  try {
    rendered = await client.renderProposed({ address, state: flags.state, data }, { html: true });
  } catch (error) {
    if (error instanceof ShioHttpError && error.status === 404) {
      throw new Error(
        `shio preview: ${address} is not on this instance yet. A render needs a page to render at `
          + "(a friendly URL, a layout and a site), so push the file once, or pass --address with "
          + "the page it will replace.",
      );
    }
    throw error;
  }

  const out = flags.out ?? join(tmpdir(), "shio-preview", post.site, `${post.url.replaceAll(/^\/+/g, "") || "index"}.html`);
  await ctx.fs.mkdir(dirname(out), { recursive: true });
  await ctx.fs.writeFile(out, rendered.html ?? "", "utf8");

  const digest = rendered.digest ?? rendered;
  ctx.stdout.write(summary(address, digest, out, inlined));
  return { address, out, digest, inlined };
}

/**
 * SH1143 — how large an asset this will carry inline, in bytes.
 *
 * <p>The render endpoint holds the request in memory and renders it synchronously, so the ceiling
 * is what stops a preview carrying a video. 1.5 MB of bytes is roughly 2 MB of base64, which is the
 * server's own `INLINE_CEILING`; they are the same number written on both sides of the wire, and
 * the server's is the one that decides — this one only lets the CLI say something useful first.</p>
 */
const INLINE_CEILING = 1_500_000;

/** Which file extensions are an image the browser will render from a `data:` URI. */
const IMAGE_TYPES = new Map(Object.entries({
  ".png": "image/png", ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".gif": "image/gif",
  ".webp": "image/webp", ".avif": "image/avif", ".svg": "image/svg+xml", ".ico": "image/x-icon",
}));

/**
 * SH1143 — put the bytes in the field for any asset the instance does not have yet.
 *
 * <h2>The defect</h2>
 *
 * <p>A content file spells a `File` field as a tree path (`Blog/img/hero.png`); the renderer reads
 * that field as a **File post id** and emits `/file_source/<id>/<name>`. `push` maps path to id as
 * it uploads, and a preview stores nothing — so an image the author has just dropped under
 * `shio/assets/**` has no id, `findById` finds nothing, and the page shows a broken image where it
 * will show a picture. <b>The author learns the wrong thing about the page, which is the one thing
 * a preview must not do.</b></p>
 *
 * <h2>Why the bytes and not a staged upload</h2>
 *
 * <p>§LXVIII.20 offered both and preferred this one: staging the asset under a disposable name
 * reuses the upload path but <em>writes</em>, and a write needs sweeping — a failed preview would
 * leave rubbish on the instance. Inline bytes keep P7's promise that a preview stores nothing.</p>
 *
 * <h2>Only what the instance lacks</h2>
 *
 * <p>An asset already uploaded is sent as its id, exactly as `push` would: that is the page the
 * author will get, srcset and all, and inlining it instead would show them a different one. So this
 * asks the instance first and inlines only what it does not have.</p>
 *
 * @returns {Promise<string[]>} the asset paths whose bytes were inlined, for the summary
 */
async function inlineAssets(ctx, post, data, admin) {
  let names;
  try {
    names = fileFields([await admin.getPostTypeDef(post.type)]).get(post.type) ?? new Set();
  } catch {
    // A type this instance does not have is the render's 404 to report, not this function's: it
    // has a sentence for that already. Nothing is inlined, which is what happened before SH1143.
    return [];
  }
  if (!names.size) return [];

  const inlined = [];
  for (const name of names) {
    const value = data[name];
    // Not a path is an id already — a reference this tree never projected, left exactly as it is
    // (SH145's read side leaves those alone too).
    if (typeof value !== "string" || !value || !assetLocationOf(value)) continue;

    const dot = value.lastIndexOf(".");
    const type = dot < 0 ? null : IMAGE_TYPES.get(value.slice(dot).toLowerCase());
    if (!type) continue;

    let bytes;
    try {
      bytes = await ctx.fs.readFile(join(ctx.cwd, ASSETS_DIR, value));
    } catch {
      // No bytes in the tree either. The field names something neither side has, which `shio
      // verify` reports as a dangling reference — a preview is not where that is discovered.
      continue;
    }
    if (bytes.length > INLINE_CEILING) {
      ctx.stdout.write(
        `shio preview: ${value} is ${Math.round(bytes.length / 1024)} KB, over the `
        + `${Math.round(INLINE_CEILING / 1024)} KB a render request carries. It renders as it will `
        + "once pushed, which is to say not at all until then.\n",
      );
      continue;
    }
    data[name] = `data:${type};base64,${Buffer.from(bytes).toString("base64")}`;
    inlined.push(value);
  }
  return inlined;
}

/** The path `parsePost` expects: relative to the content root when the file is under it. */
function contentRelative(cwd, absolute) {
  const root = resolve(cwd, CONTENT_DIR);
  const inside = relative(root, absolute);
  return inside.startsWith("..") || isAbsolute(inside) ? null : inside.replace(/\\/g, "/");
}

function summary(address, digest, out, inlined = []) {
  const lines = [`shio preview: ${address} (unsaved) digest ${digest.digest ?? "?"}, ${digest.words ?? 0} words`];
  // SH1143 — said out loud, because an inlined image renders without a srcset and is the one way
  // this page differs from the one a push would produce.
  if (inlined.length) lines.push(`${inlined.length} image(s) rendered from the tree: ${inlined.join(", ")}`);
  for (const heading of digest.headings ?? []) {
    lines.push(`${"  ".repeat(Math.max(heading.level - 1, 0))}h${heading.level} ${heading.text}`);
  }
  const noAlt = (digest.images ?? []).filter((image) => image.alt === undefined || image.alt === null);
  if (noAlt.length) lines.push(`${noAlt.length} image(s) with no alt`);
  lines.push(`markup: ${out}`);
  return `${lines.join("\n")}\n`;
}
