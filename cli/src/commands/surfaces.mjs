/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio surfaces` (SH979) — what the console offers, and how to do each of those things
 * from here.
 *
 * The fourth reader of one declaration. The console builds its rail from it, the manifest
 * serves it behind `include=surfaces`, MCP publishes it as `shio://surfaces`, and this
 * prints it — so "is there an agent way to do what that screen does" is a command rather
 * than a question for whoever wrote the screen.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("surfaces");

export async function run(flags, _positionals, ctx) {
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const manifest = await client.getManifest(["surfaces"]);
  const surfaces = manifest.surfaces ?? [];

  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify(surfaces, null, 2)}\n`);
    return surfaces;
  }

  ctx.stdout.write(render(surfaces));
  return surfaces;
}

/**
 * One line per surface, and the answer beside it.
 *
 * The section heads the group because that is how the console groups them, and a reader
 * comparing the two is the reader this command has.
 */
function render(surfaces) {
  const lines = [`shio surfaces: ${surfaces.length}`];
  let section = null;
  for (const surface of surfaces) {
    if (surface.section !== section) {
      section = surface.section;
      lines.push(`${section}:`);
    }
    lines.push(`  ${surface.id.padEnd(20)} ${surface.route}`);
    lines.push(`    ${surface.capability}`);
    lines.push(`    ${answer(surface)}`);
  }
  return `${lines.join("\n")}\n`;
}

/** What an agent calls instead, or why there is nothing to call. */
function answer(surface) {
  if (surface.agent) return `→ ${surface.agent.kind} ${surface.agent.target}`;
  const exception = surface.exception ?? {};
  const closedBy = exception.closedBy ? ` (${exception.closedBy})` : "";
  return `✗ ${exception.reason}${closedBy}: ${exception.why}`;
}
