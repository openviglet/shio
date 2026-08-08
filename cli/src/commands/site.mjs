/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * `shio site` (SH591) — list the sites this instance holds, and sweep the ones nobody meant to keep.
 *
 * <p>Twelve replication rounds left thirteen sites on one instance — `R6Fid`, `R6Aut`, `R9Aut`,
 * `R9Loc`, `R10Ssr`, `R11`, `R12` and six older ones — every one made to answer a question that has
 * since been answered. Removing them meant thirteen `site.delete` ops, each answering 428 and each
 * needing its own confirm token: SH108 working exactly as designed, applied to the one case where
 * the surprise it guards against cannot happen.</p>
 *
 * <h2>One confirmation, and no new mechanism</h2>
 *
 * <p>The confirm token has always been <b>per batch</b> — `ShAgentConfirm.ofPlan` digests every
 * deletion the plan reports — so thirteen deletes in one document have always needed one token. The
 * cost was never the protocol; it was that nothing marked a site as disposable, so the sweep could
 * not be *composed*. That is the whole of what this adds, and it is why nothing in SH108 changed.</p>
 *
 * <h2>What keeps it from becoming a fast way to delete a real site</h2>
 *
 * <p>`--scratch` is not a filter the caller can widen. A site without the flag is untouched at any
 * count, `--scratch` is required (there is no bare `rm --all`), and the flag itself can only be set
 * by the run that <em>created</em> the site — so every site this can remove was declared disposable
 * in a document a human could read before there was anything in it to lose. That is P7 satisfied
 * rather than waived: the declaration preceded the content.</p>
 */
import { ShioAgentClient } from "../agent-client.mjs";
import { resolveAdmin } from "../context.mjs";
import { helpText } from "../help.mjs";

export const HELP = helpText("site");

export async function run(flags, positionals, ctx) {
  const verb = String(positionals[0] ?? "ls").trim().toLowerCase();
  const { baseUrl, user, password } = resolveAdmin(flags, ctx.env);
  const client = new ShioAgentClient({ baseUrl, user, password, fetch: ctx.fetch });
  const sites = await listSites(client);

  if (verb === "ls" || verb === "list") {
    return list(sites, flags, ctx);
  }
  if (verb !== "rm" && verb !== "delete") {
    throw new Error(
      `shio site: "${positionals[0]}" is not a verb this takes.\n`
        + "  allowed: ls, rm\n"
        + "  example: shio site rm --scratch --dry-run",
    );
  }
  return remove(client, sites, flags, ctx);
}

/**
 * The listing, and the second half of SH591 is that it says which is which.
 *
 * <p>`shio context` and the console showed thirteen sites and no difference between the two a human
 * curates and the eleven an agent left behind — and the human is the one who has to look at that
 * list. A sweep that works and a listing that cannot tell them apart would leave the reader exactly
 * where they were.</p>
 */
function list(sites, flags, ctx) {
  const scratch = sites.filter((site) => site.scratch);
  if (flags.json) {
    ctx.stdout.write(`${JSON.stringify({ sites, scratch: scratch.length }, null, 2)}\n`);
    return { sites, scratch: scratch.length };
  }
  ctx.stdout.write(
    `shio site: ${sites.length} site(s)`
      + `${scratch.length ? `, ${scratch.length} declared disposable` : ""}\n`,
  );
  for (const site of sites) {
    ctx.stdout.write(
      `  ${site.name}${site.scratch ? "  SCRATCH" : ""}`
        + `  ${site.posts} post(s)${site.url ? `  ${site.url}` : ""}\n`,
    );
  }
  if (scratch.length) {
    ctx.stdout.write(`  shio site rm --scratch removes the ${scratch.length} marked SCRATCH\n`);
  }
  return { sites, scratch: scratch.length };
}

/**
 * The sweep: one document, N deletes, one confirm token.
 *
 * <p>Two calls, because that is what SH108 asks of anything destructive and this is not exempt from
 * it — it is exempt from paying it thirteen times. The plan comes back with the digest of what it
 * would destroy, and the second call carries it; a site added or removed in between changes the
 * digest and the run refuses, which is the guarantee the token exists for and one a per-site loop
 * gave up thirteen times over.</p>
 */
async function remove(client, sites, flags, ctx) {
  if (!flags.scratch) {
    throw new Error(
      "shio site rm: --scratch is required.\n"
        + "  why: this deletes sites in bulk, and the only set it will ever act on is the one\n"
        + "       declared disposable by whoever created it. There is no `--all`, and a site\n"
        + "       without the flag is untouched at any count.\n"
        + "  example: shio site rm --scratch --dry-run",
    );
  }
  const doomed = sites.filter((site) => site.scratch);
  if (!doomed.length) {
    ctx.stdout.write(
      `shio site rm: nothing to sweep — none of the ${sites.length} site(s) is marked scratch. `
        + "Mark one when you make it: shio convert --create-site --scratch\n",
    );
    return { deleted: [], planned: [] };
  }

  const ops = doomed.map((site) => ({ op: "site.delete", address: `site:${site.name}` }));
  // `dryRun` is a key of the document, not a query parameter — the batch endpoint reads it
  // off the body, and a plan that quietly ran would be the worst possible way to learn that.
  const plan = await client.batch({ ops, dryRun: true });
  const names = doomed.map((site) => site.name);

  if (flags.dryRun) {
    ctx.stdout.write(
      `shio site rm: would delete ${names.length} scratch site(s) — ${names.join(", ")}\n`
        + "  purged, not trashed: there is no undo (SH463)\n",
    );
    return { planned: names, deleted: [], exitCode: 0 };
  }

  // The token is the plan's, spent on the run it was computed for. One for the whole sweep, which
  // is the entire saving: the protocol always allowed it and nothing could compose the document.
  const result = await client.batch({ ops, confirm: plan.confirm });
  ctx.stdout.write(
    `shio site rm: deleted ${names.length} scratch site(s) — ${names.join(", ")}\n`
      + "  one confirmation, purged and not trashed (SH463)\n",
  );
  return { deleted: names, planned: names, result };
}

/** The sites the context pack reports, with SH591's flag. */
async function listSites(client) {
  const { body } = await client.getContext({ include: "sites", format: "json" });
  const sites = JSON.parse(body).sites ?? [];
  return sites
    .filter((site) => site?.name)
    .map((site) => ({
      name: site.name,
      url: site.url ?? "",
      posts: site.posts ?? 0,
      // Present only when true — the pack omits it otherwise, so absence is "kept" and not "unknown".
      scratch: site.scratch === true,
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
}
