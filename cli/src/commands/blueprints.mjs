/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio blueprints [name]` (SH92) — the appliable starting points an instance carries.
 *
 * Why a list command and not just documentation. A blueprint is only worth having if
 * choosing one is cheaper than building the thing by hand, and that means the catalogue
 * has to be readable where the work happens: in the terminal, and (over MCP) in the
 * model's context. Prose in a README is a fourth place to keep in sync with the packages
 * that actually shipped — design law P1, in the small.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

/**
 * What `shio blueprints --help` prints (SH435).
 *
 * Here rather than in `index.mjs` so the flags and their meanings are one edit apart: the
 * global help described some commands' flags and no command's completely, and learning this
 * one's meant grepping the source for `flags.` — reading an implementation to discover an
 * interface, having already paid for a help text that answered something else.
 */
export const HELP = helpText("blueprints");

export async function run(flags, positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const name = positionals[0] ?? (typeof flags.name === "string" ? flags.name : null);

  if (name) {
    const detail = await client.blueprint(name, { files: Boolean(flags.files) });
    ctx.stdout.write(flags.json ? `${JSON.stringify(detail, null, 2)}\n` : renderDetail(detail));
    return detail;
  }

  const catalogue = await client.blueprints();
  ctx.stdout.write(flags.json ? `${JSON.stringify(catalogue, null, 2)}\n` : renderList(catalogue));
  return { blueprints: catalogue };
}

/**
 * One line per package. The description is wrapped rather than truncated: it is the field
 * the choice is actually made on, and a cut description is how someone picks the wrong
 * starting point and finds out four pages later.
 */
function renderList(catalogue) {
  if (!catalogue.length) {
    return "shio blueprints: this instance carries none.\n";
  }
  const width = Math.max(...catalogue.map((entry) => entry.name.length));
  const lines = catalogue.map((entry) => {
    const head = `  ${entry.name.padEnd(width)}  ${entry.version ?? "-"}`;
    const counts = [
      entry.postTypes ? `${entry.postTypes} type(s)` : null,
      entry.posts ? `${entry.posts} post(s)` : null,
      entry.files ? `${entry.files} file(s)` : null,
    ]
      .filter(Boolean)
      .join(", ");
    const params = paramNames(entry.params);
    return [
      `${head}  ${counts}`,
      `      ${wrap(entry.description ?? "", 74, "      ")}`,
      params ? `      params: ${params}` : null,
      // SH96 — printed only when present, which is only for a package the instance did
      // not ship with. Somebody about to apply a package to their own site should not have
      // to ask where it came from, and the first-party ones saying "classpath" would be
      // six lines of noise that trains the reader to skip the line that matters.
      entry.origin ? `      from:   ${entry.origin} (not a built-in package)` : null,
    ]
      .filter(Boolean)
      .join("\n");
  });
  return `shio blueprints: ${catalogue.length}\n${lines.join("\n")}\n`;
}

function renderDetail(detail) {
  const manifest = detail.manifest ?? {};
  const out = [
    `${manifest.name} ${manifest.version ?? ""} — ${manifest.title ?? ""}`.trim(),
    wrap(manifest.description ?? "", 78, ""),
    "",
    `provisions: ${(detail.postTypes ?? []).join(", ") || "no post-types"}`,
    `declares:   ${detail.folders ?? 0} folder(s), ${detail.posts ?? 0} post(s)`,
  ];
  const required = new Set(manifest.params?.required ?? []);
  const properties = manifest.params?.properties ?? {};
  const names = Object.keys(properties);
  if (names.length) {
    out.push("", "parameters:");
    for (const name of names) {
      const spec = properties[name] ?? {};
      const bits = [
        spec.type ?? "string",
        required.has(name) ? "required" : null,
        spec.default === undefined ? null : `default ${JSON.stringify(spec.default)}`,
        Array.isArray(spec.enum) ? `one of ${spec.enum.join("|")}` : null,
      ]
        .filter(Boolean)
        .join(", ");
      out.push(`  --param ${name}=…  (${bits})`);
      if (spec.description) out.push(`      ${wrap(spec.description, 70, "      ")}`);
    }
  }
  // Said out loud, because the server never writes these: a caller that assumed
  // otherwise would report a finished front end that does not exist.
  const files = manifest.files ?? [];
  if (files.length) {
    out.push("", "front-end files (written by `shio apply --blueprint`, never by the server):");
    for (const file of files) out.push(`  ${file.path}`);
  }
  out.push(
    "",
    `apply it:   shio apply --blueprint ${manifest.name} --site <site> --dry-run`,
  );
  return `${out.join("\n")}\n`;
}

function paramNames(schema) {
  const required = new Set(schema?.required ?? []);
  const names = Object.keys(schema?.properties ?? {});
  return names.length
    ? names.map((name) => (required.has(name) ? `${name} (required)` : name)).join(", ")
    : null;
}

/** Greedy wrap — enough to keep a description readable in an 80-column terminal. */
function wrap(text, width, indent) {
  const words = String(text).split(/\s+/).filter(Boolean);
  const lines = [];
  let line = "";
  for (const word of words) {
    if (line && line.length + 1 + word.length > width) {
      lines.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) lines.push(line);
  return lines.join(`\n${indent}`);
}
