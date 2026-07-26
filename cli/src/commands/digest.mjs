/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  const target = flags.address ?? flags.url ?? positionals[0];
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
