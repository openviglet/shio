/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio gate` (SH997) — the CI form of the checks a site runs before content reaches visitors.
 *
 * The gates themselves live on the instance and are evaluated wherever a publish happens, so this is
 * not where they are enforced: it is where a pipeline finds out **before** it publishes, and where
 * the one check no instance can run lives. `audit` drives a browser per viewport and "the instance
 * does not run a browser" is a standing non-goal, so a gate file may name it, the push leaves it
 * behind, and `shio gate run` is what runs it.
 *
 * The verdict is the server's. Judging the content here would give a pipeline a second opinion about
 * its own site, and the day the two disagreed the green one would be the one nobody re-read.
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";
import { readLocalGates } from "../gates-sync.mjs";
import { ciSide } from "../gates-store.mjs";

export const HELP = helpText("gate");

export async function run(flags, positionals, ctx) {
  const site = typeof flags.site === "string" ? flags.site : undefined;
  if (!site) {
    throw new Error(
      "shio gate run: --site <name> says whose gates to ask about. A gate belongs to a site, and " +
        "running every site's checks at once would report on content this pipeline is not about.",
    );
  }
  const action = positionals[0] ?? "run";
  if (action !== "run" && action !== "list") {
    throw new Error(`shio gate: '${action}' is not an action. Use \`run\` or \`list\`.`);
  }
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });

  if (action === "list") {
    const gates = await client.gates(site);
    ctx.stdout.write(render(gates, site));
    return { gates: gates.length };
  }

  const addresses = await targets(flags, positionals.slice(1), site, client);
  const blocked = [];
  const reported = [];
  for (const address of addresses) {
    for (const outcome of await client.checkGates(site, address)) {
      for (const failure of outcome.failures ?? []) {
        const row = { address, gate: outcome.gate, ...failure };
        (failure.severity === "error" ? blocked : reported).push(row);
      }
    }
  }
  ctx.stdout.write(verdict(addresses, blocked, reported));
  // A pipeline reads the exit code, and a gate that reported a blocking failure and exited 0 would
  // be a gate that publishes anyway one step later.
  return { addresses: addresses.length, blocked: blocked.length, ok: blocked.length === 0 };
}

/** What to check: the addresses named, or every draft the site holds. */
async function targets(flags, named, site, client) {
  if (named.length) return named;
  const found = await client.findAll({ site, state: "draft" });
  if (found.truncated) {
    throw new Error(
      `shio gate run: could not read the whole site (${found.results.length} row(s), and the walk ` +
        "was cut short), so this would report on some of it and pass. Name the addresses, or raise " +
        "shio.agent.read.max-limit on the server.",
    );
  }
  return found.results.map((row) => row.address).filter(Boolean);
}

function render(gates, site) {
  if (!gates.length) return `shio gate: ${site} declares no gates\n`;
  const lines = gates.map((gate) => {
    const checks = (gate.checks ?? []).map((check) => `${check.check}=${check.severity}`).join(" ");
    return `  ${gate.name}${gate.enabled === false ? " (off)" : ""}  ${checks}`;
  });
  return [`shio gate: ${gates.length} gate(s) in ${site}`, ...lines, ""].join("\n");
}

function verdict(addresses, blocked, reported) {
  const head = `shio gate run: ${addresses.length} address(es), ${blocked.length} blocked, ` +
    `${reported.length} reported`;
  const rows = [...blocked, ...reported].map(
    (row) => `  ${row.severity === "error" ? "✖" : "!"} ${row.address} — gate '${row.gate}' ` +
      `${row.check}: ${row.detail}`,
  );
  const ci = [];
  for (const check of new Set(reported.concat(blocked).map((row) => row.check))) {
    if (ciSide({ checks: [{ check }] }).length) ci.push(check);
  }
  return [head, ...rows, ...ci.map((check) => `  note ${check} runs here, not on the instance`), ""]
    .join("\n");
}
