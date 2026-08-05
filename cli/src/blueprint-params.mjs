/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH447 — a blueprint's parameters, read and checked in one place.
 *
 * ## Why this module exists
 *
 * `shio apply --blueprint` had `--param`/`--params` and `shio init --blueprint` had neither, so a
 * package declaring a required parameter could not be scaffolded through `init` **at all**: it sent
 * `{projectName}`, the server refused on the parameter it was never given, and the run exited
 * non-zero having already written the whole Next.js app. Measured: 15 files, `app/[...slug]/page.tsx`,
 * a correct `.env.local` — and one line of output that mentions none of it.
 *
 * The flags are therefore the same flags, which means one parser (SH245: the second copy of a verb is
 * the one that stops matching), and the check that makes a refusal *true* lives beside them.
 *
 * ## What this checks and what it deliberately leaves to the server
 *
 * **Only the required-and-absent case**, because that is the one a caller can be told about before a
 * request exists — a manifest declares its parameters, so "you have not given me `brand`" is
 * answerable locally and is the whole of SH447's defect. Everything else is the server's: an unknown
 * name gets `ShBlueprintParams`' teaching 400 with the declared list and the nearest match, a wrong
 * type gets one naming the type, and a second copy of those rules here would be two validators that
 * eventually disagree about one schema (P10).
 */
import { join } from "node:path";

/**
 * `--params <file.json>` as the base, `--param k=v` on top.
 *
 * @param {string} command the caller's name, so a refusal names the command that was actually run
 */
export async function readParams(flags, ctx, command) {
  const values = {};
  if (typeof flags.params === "string") {
    const raw = await ctx.fs.readFile(join(ctx.cwd, flags.params), "utf8").catch((error) => {
      if (error?.code === "ENOENT") {
        throw new Error(`shio ${command}: no such params file "${flags.params}".`);
      }
      throw error;
    });
    let parsed;
    try {
      parsed = JSON.parse(raw);
    } catch (error) {
      throw new Error(`shio ${command}: ${flags.params} is not valid JSON (${error.message}).`);
    }
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      throw new Error(
        `shio ${command}: ${flags.params} must hold a JSON object of parameter values, ` +
          'e.g. {"brand":"Acme"}.',
      );
    }
    Object.assign(values, parsed);
  }
  const pairs = flags.param === undefined ? [] : [flags.param].flat();
  for (const pair of pairs) {
    if (typeof pair !== "string" || !pair.includes("=")) {
      throw new Error(
        `shio ${command}: --param takes name=value (got "${pair}"). ` +
          "Example: --param brand=Acme",
      );
    }
    const cut = pair.indexOf("=");
    values[pair.slice(0, cut)] = pair.slice(cut + 1);
  }
  return values;
}

/**
 * Coerces string values to the types the package declared. An unknown parameter is left
 * exactly as typed so the *server* rejects it — its teaching error names the declared
 * parameters and the nearest match, which is a better message than this file could write.
 */
export function coerceParams(values, schema) {
  const properties = schema?.properties ?? {};
  const out = {};
  for (const [name, value] of Object.entries(values)) {
    const type = properties[name]?.type;
    if (typeof value !== "string" || !type || type === "string") {
      out[name] = value;
    } else if (type === "integer" || type === "number") {
      const parsed = Number(value);
      out[name] = Number.isFinite(parsed) ? parsed : value;
    } else if (type === "boolean") {
      out[name] = value === "true" ? true : value === "false" ? false : value;
    } else {
      out[name] = value;
    }
  }
  return out;
}

/**
 * The declared parameters this run has no value for (SH447).
 *
 * <p><b>Deliberately the server's own rule, restated</b>: `ShBlueprintParams.resolve` refuses a
 * parameter that is in the schema's `required` list, has no `default`, and got no value — in that
 * order, so a required parameter *with* a default is filled rather than refused. Anything looser
 * here would refuse a run the server would have accepted, which is a worse failure than the one this
 * task is fixing: a caller told they cannot do something they can.</p>
 *
 * <p>An empty string counts as a value. It is what `--param brand=` sends, and a package that
 * declares a parameter it will substitute into copy is entitled to be given an empty one on
 * purpose.</p>
 *
 * @returns {Array<{name: string, description: string}>} in the schema's own order (P5)
 */
export function missingParams(schema, values = {}) {
  const properties = schema?.properties ?? {};
  const required = Array.isArray(schema?.required) ? schema.required : [];
  const missing = [];
  for (const name of required) {
    const spec = properties[name];
    if (values[name] !== undefined) continue;
    if (spec !== null && typeof spec === "object" && spec.default !== undefined && spec.default !== null) {
      continue;
    }
    missing.push({ name, description: describe(spec) });
  }
  return missing;
}

function describe(spec) {
  const text = spec === null || typeof spec !== "object" ? null : spec.description;
  return typeof text === "string" && text.trim()
    ? text.trim()
    : "It has no default, so the package cannot fill it in.";
}

/**
 * The refusal for a run that cannot succeed, written before anything is on disk.
 *
 * <p>It names every missing parameter rather than the first, because the alternative is a caller
 * running the command once per parameter — and it names the read that states them, since a package's
 * schema is the authority and this message is a projection of it.</p>
 */
export function missingParamsMessage(command, blueprint, missing) {
  return (
    `shio ${command}: blueprint "${blueprint}" requires `
      + `${missing.length} parameter(s) this run has no value for.\n`
      + missing.map((row) => `  ${row.name} — ${row.description}\n`).join("")
      + `Checked before anything was written, so nothing is on disk to clean up.\n`
      + `  shio ${command} … ${missing.map((row) => `--param ${row.name}=<value>`).join(" ")}\n`
      + `  shio blueprints ${blueprint}   # the whole schema, including the optional ones`
  );
}
