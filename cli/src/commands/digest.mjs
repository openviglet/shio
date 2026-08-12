/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio digest <address|url>` (SH101) — a page's structure, without a browser.
 *
 * Prints the heading outline, landmarks, links and images (marking the ones with no alt
 * text) and the structural hash. The hash is the reason to keep the output: comparing it
 * across two runs answers "did my edit change the shape of this page" without re-reading
 * either version.
 *
 * A bare positional is an address; anything starting with `http` is treated as a URL, so
 * `shio digest post:Blog/blog/hello` and `shio digest https://site/blog/hello` both do
 * what they look like.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { pageTarget } from "../snapshot.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio digest --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("digest");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  // SH365 named `snapshot` and a grep found this one too: the same expression, the same collision
  // with `resolveAdmin` two lines up, and nothing had said so.
  const target = pageTarget(flags, positionals, (message) =>
    ctx.stderr.write(`shio digest: ${message}\n`));
  if (!target) {
    throw new Error(
      "shio digest: pass a page. Example: shio digest post:Blog/blog/hello-world " +
        "(or a delivery URL, when the instance allows fetching).",
    );
  }
  const isUrl = /^https?:\/\//i.test(String(target));
  const digest = await client.digest(
    isUrl ? { url: target } : { address: target },
  );

  ctx.stdout.write(render(digest));
  return digest;
}

function render(digest) {
  const lines = [
    `shio digest: ${digest.digest} (${digest.source}` +
      (digest.status ? ` ${digest.status}` : "") +
      `, ${digest.words} words)`,
  ];
  for (const heading of digest.headings ?? []) {
    lines.push(`${"  ".repeat(Math.max(heading.level - 1, 0))}h${heading.level} ${heading.text}`);
  }
  const landmarks = Object.entries(digest.landmarks ?? {});
  if (landmarks.length) {
    lines.push(`landmarks: ${landmarks.map(([name, count]) => `${name}×${count}`).join(" ")}`);
  }
  lines.push(...regionLines(digest.regions ?? []));
  for (const link of digest.links ?? []) {
    lines.push(`link ${link.internal ? "→" : "↗"} ${link.href}${link.text ? ` (${link.text})` : ""}`);
  }
  for (const image of digest.images ?? []) {
    // `no alt` is the finding, `alt=""` is correct decorative markup — the digest keeps
    // them apart, so the CLI must too.
    lines.push(
      `img ${image.src} ${image.alt === undefined || image.alt === null ? "[no alt]" : `alt="${image.alt}"`}`,
    );
  }
  if ((digest.headings ?? []).length === 0 && (digest.words ?? 0) === 0) {
    lines.push("(nothing to digest — this page has no rich-text content)");
  }
  return `${lines.join("\n")}\n`;
}

/**
 * The regions in document order, which is the half `landmarks` cannot state (SH687).
 *
 * <p>`footer×1 header×1 nav×1 section×9` cannot say the footer renders above the content, and on the
 * `www.viglet.org` replica that is exactly what it did — a defect it took a screenshot from a human
 * to see, on a page every textual instrument called fine.</p>
 *
 * <p>Runs of one tag with the same provenance are collapsed to `section×9  3-11`, which is the shape
 * the task asked for. Collapsed only where they are **contiguous**: nine sections split by a footer
 * are three rows, and merging them would hide the very thing this is here to show.</p>
 */
function regionLines(regions) {
  if (!regions.length) return [];
  const lines = ["regions (document order):"];
  for (const run of runsOf(regions)) {
    const span = run.from === run.to ? `${run.from}` : `${run.from}-${run.to}`;
    const label = run.count === 1 ? run.tag : `${run.tag}×${run.count}`;
    lines.push(
      `  ${label.padEnd(12)}${span.padEnd(7)}${run.markup.padEnd(10)}`
        + `${run.unstyled ? `${run.unstyled} unstyled` : "0 unstyled"}`,
    );
  }
  return lines;
}

/** Contiguous regions sharing a tag and a provenance, in order. */
function runsOf(regions) {
  const runs = [];
  for (const region of regions) {
    const last = runs[runs.length - 1];
    if (last && last.tag === region.tag && last.markup === region.markup) {
      last.to = region.order;
      last.count += 1;
      last.unstyled += region.unstyled ?? 0;
      continue;
    }
    runs.push({
      tag: region.tag,
      markup: region.markup,
      from: region.order,
      to: region.order,
      count: 1,
      unstyled: region.unstyled ?? 0,
    });
  }
  return runs;
}
