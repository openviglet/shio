/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio context` (SH126) — print the agent context pack: the content model, the
 * sitemap by path, and this instance's conventions, in one call.
 *
 * Why the CLI carries it at all, given the endpoint exists: an agent working in a
 * checkout reaches for a command, not for HTTP plumbing (design law P4 — files and
 * commands beat APIs for authoring). `shio context --format terse` is one line of
 * shell that yields a few thousand tokens of ground truth, and `--out` makes it a
 * committable artifact that a later session, or a reviewer, can diff.
 */
import { dirname, join } from "node:path";

import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";

/** `terse` is the default here, unlike the endpoint: the caller is an agent. */
const DEFAULT_FORMAT = "terse";

/**
 * What `shio context --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = `shio context — the agent context pack: model + sitemap + conventions, in one call

  --site <idOrName>    Scope to one site       (omit for every readable site)
  --include <sections> instance,sites,model,sitemap,conventions (default all)
  --format <fmt>       terse (default), json, or agents-md
  --as agents-md       Same as --format agents-md; pair with --out AGENTS.md so the file
                       cannot rot — regenerate it and the diff is the drift
  --depth <n>          Folder-tree depth to walk
  --budget <tokens>    Estimated-token ceiling (0 = unbounded)
  --from <id>          Resume cursor from a truncated pack
  --ids                Put object ids back on every row (off by default — a row is
                       addressable without one, and a UUID is the priciest field)
  --out <file>         Write the pack to a file instead of stdout

Examples:
  shio context --site Acme
  shio context --as agents-md --out AGENTS.md
`;

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  // `--as` and `--format` name the same thing. Both exist because the roadmap word
  // for the third rendering is "as agents-md" and the first two have always been a
  // format; refusing one of the two spellings would be a paper cut with no upside.
  const format = normalizeFormat(flags.as ?? flags.format);

  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const { body, estTokens } = await client.getContext({
    site: flags.site,
    depth: flags.depth,
    budget: flags.budget,
    include: flags.include,
    from: flags.from,
    format,
    // Off unless asked for (SH121). The endpoint already defaults it off; sending
    // the flag only when set keeps the request URL — and so the server-side cache
    // key — identical to what a bare `shio context` produced before.
    ids: flags.ids ? "true" : undefined,
  });

  if (flags.out) {
    const target = join(ctx.cwd, flags.out);
    await ctx.fs.mkdir(dirname(target), { recursive: true });
    await ctx.fs.writeFile(target, body.endsWith("\n") ? body : `${body}\n`, "utf8");
    ctx.stdout.write(
      `shio context: wrote ${flags.out}${estTokens ? ` (~${estTokens} tokens)` : ""}\n`,
    );
  } else {
    // Straight to stdout, unreformatted — the bytes are the artifact, and the pack's
    // digest is computed over them. Pretty-printing here would break `--out` diffs.
    ctx.stdout.write(body.endsWith("\n") ? body : `${body}\n`);
  }

  return { format, estTokens, bytes: body.length };
}

/** The renderings the endpoint serves (SH78 terse, SH115 agents-md). */
const FORMATS = ["terse", "json", "agents-md"];

/**
 * Accepts `terse` / `json` / `agents-md`; anything else is rejected with the same
 * shape the server's teaching errors use (SH80) — a wrong value costs one message,
 * not a round trip to find out what is allowed.
 */
function normalizeFormat(raw) {
  if (raw === undefined || raw === null || raw === "" || raw === true) return DEFAULT_FORMAT;
  const format = String(raw).trim().toLowerCase();
  if (!FORMATS.includes(format)) {
    throw new Error(
      `shio context: unknown --format "${raw}". Allowed: ${FORMATS.join(", ")}. ` +
        `Example: shio context --format terse`,
    );
  }
  return format;
}
