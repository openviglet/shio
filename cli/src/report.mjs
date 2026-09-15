/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH104 — the handoff report, as a pure function.
 *
 * The engine is separated from the command for the same reason `content-sync.mjs` is: what
 * is hard here is not the HTTP, it is deciding what a human needs to see and what must not
 * be quietly left out. That is testable without a network, and it is the half that will be
 * read again in a year.
 *
 * ## What this is for
 *
 * Block K removed the human round trip that existed because the agent could not check its
 * own work. This is the *other* half of the same brief: the round trip that exists because
 * a human cannot see what the agent did. `shio verify` is the agent's output for a machine —
 * a report an agent reads and acts on. This is the agent's output for a person: what changed,
 * where to look at it, and what is still open. A wall of prose is what an agent produces when
 * nobody specified the artifact; a reviewable summary with links is the artifact.
 *
 * ## The rules that are load-bearing
 *
 * **A change is never silently omitted.** A shortened list of changes reads exactly like a
 * complete review of a small change set — SH138's hazard, and it is worse here than anywhere
 * else on the surface, because the whole purpose of the document is to be the thing a human
 * trusts instead of reading the diff. So the feed's `more`, verify's `truncated` and a
 * `partial` verify run are all stated in the report body, not in a log line the reader never
 * sees.
 *
 * **A deletion is a first-class row.** Its object is gone, so it has no preview link and no
 * address to read — which makes it the row most likely to be dropped by an implementation that
 * builds the list out of links. It is also the row a reviewer most needs.
 *
 * **A preview link is never invented.** It exists when the site has a URL configured and the
 * post has a friendly URL; otherwise the report says which of the two is missing. A plausible
 * link that 404s costs a reviewer more than an honest absence — they follow it, see nothing,
 * and stop trusting the rest of the document.
 *
 * **Open questions are the warnings, not the errors.** An error is broken and belongs in the
 * agent's own loop before a human is asked to look (that is what `shio verify` is for, and the
 * report says so plainly if any survive). A warning is the class the CMS cannot settle from
 * the inside — an empty section, a link into a route the front end may own — which is exactly
 * the class that needs a person's judgement. Framing them as "worth a look" rather than
 * "failures" is what keeps the section short enough to actually be read.
 */
import { isFrontEnd } from "./front-end.mjs";

/** `CREATE`/`UPDATE`/`PUBLISH`/`UNPUBLISH`/`DELETE` → how a reviewer should read it. */
const ACTION_LABELS = {
  CREATE: "new",
  UPDATE: "edited",
  PUBLISH: "published",
  UNPUBLISH: "unpublished",
  DELETE: "deleted",
};

/**
 * Builds the handoff report.
 *
 * @param {object} input
 * @param {object} input.changes  an SH88 `/agent/changes` payload
 * @param {object} [input.verify] an SH99 `/agent/verify` report; omitted when `--no-verify`
 * @param {object} [input.context] a JSON `/agent/context` pack, for site URLs
 * @param {string} [input.title]  heading for the document
 * @returns {{ markdown: string, cursor: string|null, changed: number, caveats: string[] }}
 */
export function buildReport({ changes, verify, context, runs, verdicts, title } = {}) {
  const sites = siteUrls(context);
  const entries = (changes?.changes ?? []).map((change) => decorate(change, sites));
  const caveats = collectCaveats(changes, verify);

  const lines = [`# ${title ?? "Content handoff"}`, ""];
  lines.push(summary(entries, changes, verify));
  lines.push("");
  lines.push(...whatFor(runs));

  if (entries.length === 0) {
    lines.push("No content changed in this range.");
    lines.push("");
  } else {
    lines.push(...changeSection(entries));
  }

  lines.push(...decided(verdicts));
  lines.push(...broke(changes));
  lines.push(...openQuestions(verify));

  if (caveats.length > 0) {
    // Above the footer, not below it: what the report does *not* cover has to be read by
    // someone who stops reading once they have seen the list of changes.
    lines.push("## What this report does not cover", "");
    for (const caveat of caveats) lines.push(`- ${caveat}`);
    lines.push("");
  }

  lines.push(...footer(changes));
  return {
    markdown: `${lines.join("\n").replace(/\n{3,}/g, "\n\n").trimEnd()}\n`,
    cursor: changes?.cursor ?? null,
    changed: entries.length,
    caveats,
  };
}

/** Site name → public base URL, from the context pack's `sites` section. */
function siteUrls(context) {
  const sites = new Map();
  for (const site of context?.sites ?? []) {
    if (site?.name) sites.set(site.name, trimSlash(site.url));
  }
  return sites;
}

/**
 * One change, with the two things a reviewer needs that the feed does not carry: a label
 * they can scan, and a link they can open.
 */
function decorate(change, sites) {
  const action = String(change?.action ?? "").toUpperCase();
  const address = change?.address ?? null;
  const path = pathOf(address);
  const site = change?.site ?? siteOf(address);
  const origin = site ? sites.get(site) : undefined;
  const deleted = action === "DELETE";

  let preview = null;
  let why = null;
  if (deleted) {
    // Not a gap to explain: there is nothing left to preview, and saying "no URL" about a
    // deleted page would read as a defect rather than as the point.
    why = null;
  } else if (!path) {
    // SH172's `id:` fallback, or an object that never had a friendly URL.
    why = "no friendly URL, so nothing serves it";
  } else if (origin === undefined) {
    why = `site ${site ?? "?"} is not in the context pack`;
  } else if (!origin) {
    why = `site ${site} has no front-end URL configured`;
  } else if (!isFrontEnd(origin)) {
    // SH442 — the same truthiness test `resolveUrl` had, and here it produced `cafeteria/blog/hello`
    // as a link. A broken link presented as a working one is worse than saying there is none: the
    // reader clicks it, and a handoff whose links do not open is a handoff nobody trusts twice.
    why = `site ${site} has \`${origin}\` in its URL field, which is not an absolute http(s) URL`;
  } else {
    preview = `${origin}${path}`;
  }
  return {
    action,
    label: ACTION_LABELS[action] ?? action.toLowerCase(),
    address,
    site,
    path,
    preview,
    why,
    deleted,
    what: change?.what ?? null,
    by: change?.by ?? null,
    at: change?.at ?? null,
  };
}

/**
 * What each run in this range said it was for (SH1000).
 *
 * Above the changes, because a reviewer who reads one section reads the first one, and the premise
 * is what decides whether the list below is the right list. A run that declared none is named with
 * the flag that would have supplied it rather than left out — an absent premise is a fact about the
 * run, and silently omitting it makes a handoff of ten runs look like a handoff of the three that
 * happened to say something.
 */
function whatFor(runs) {
  if (!Array.isArray(runs) || runs.length === 0) return [];
  const lines = ["## What these runs were for", ""];
  for (const run of runs) {
    const who = run.clientName ?? run.session;
    lines.push(
      run.intent
        ? `- **${who}** — ${run.intent}`
        : `- **${who}** — no intent declared (pass \`--intent "<sentence>"\`)`,
    );
  }
  lines.push("");
  return lines;
}

/**
 * What the curator decided (SH1002).
 *
 * Below the changes and above the caveats: a reader wants to know what was written before they can
 * read what was turned down. Rejections carry their reason, which is the field the whole read
 * exists for; the other two verdicts are a line each, because "approved" and "held" are legible in
 * the content and the reason is usually absent.
 */
function decided(verdicts) {
  if (!Array.isArray(verdicts) || verdicts.length === 0) return [];
  const lines = ["## What the curator decided", ""];
  for (const verdict of verdicts) {
    const why = verdict.note ? ` — ${verdict.note}` : "";
    lines.push(`- **${verdict.decision}** \`${verdict.address ?? "(gone)"}\`` +
      `${verdict.by ? ` by ${verdict.by}` : ""}${why}`);
  }
  lines.push("");
  return lines;
}

function summary(entries, changes, verify) {
  const counts = new Map();
  for (const entry of entries) counts.set(entry.label, (counts.get(entry.label) ?? 0) + 1);
  const breakdown = [...counts.entries()]
    .sort((a, b) => a[0].localeCompare(b[0]))
    .map(([label, count]) => `${count} ${label}`)
    .join(", ");

  const parts = [
    `**${entries.length} change${entries.length === 1 ? "" : "s"}**${breakdown ? ` (${breakdown})` : ""}`,
  ];
  if (verify) {
    // The reviewer's first question is whether this is ready to look at, so the answer goes
    // in the first paragraph rather than in a section they scroll to.
    parts.push(
      verify.errors > 0
        ? `⚠ **${verify.errors} error${verify.errors === 1 ? "" : "s"} still open** — ` +
          `this is not ready to merge; run \`shio verify\` for the fixes`
        : "✓ no errors: `shio verify` passes",
    );
    if (verify.warnings > 0) {
      parts.push(`${verify.warnings} warning${verify.warnings === 1 ? "" : "s"} for review`);
    }
  }
  if (changes?.window) {
    parts.push("covering the most recent activity, not a delta");
  }
  return `${parts.join(" · ")}.`;
}

function changeSection(entries) {
  const lines = ["## What changed", ""];
  for (const entry of entries) {
    const where = entry.address ?? "(object no longer exists)";
    const link = entry.preview ? ` — [preview](${entry.preview})` : "";
    lines.push(`- **${entry.label}** \`${where}\`${link}`);
    const detail = [entry.what, entry.by ? `by ${entry.by}` : null, entry.at]
      .filter(Boolean)
      .join(" · ");
    if (detail) lines.push(`  ${detail}`);
    if (entry.why) lines.push(`  _no preview: ${entry.why}_`);
  }
  lines.push("");
  return lines;
}

/**
 * The asynchronous failures in the same range (SH144).
 *
 * <p>They arrive on the feed this report already reads, which is the payoff of folding them
 * into it: a webhook the front end never received is precisely a handoff concern — the content
 * is right and the site is stale — and before SH144 a reviewer would have had to know to make a
 * second call. It gets its own section rather than joining the change list, because "what the
 * agent did" and "what broke while it did" are different questions with different owners.
 */
function broke(changes) {
  const incidents = changes?.incidents ?? [];
  if (incidents.length === 0) return [];
  const lines = ["## What broke", ""];
  for (const incident of incidents) {
    const repeats = incident.count > 1 ? ` (×${incident.count})` : "";
    const subject = incident.subject ? ` \`${incident.subject}\`` : "";
    lines.push(`- **${incident.kind}**${subject} — ${incident.message ?? ""}${repeats}`);
    if (incident.fix) lines.push(`  ${incident.fix}`);
    const retry = retryCommand(incident.retry);
    if (retry) lines.push(`  retry: \`${retry}\``);
  }
  lines.push("");
  return lines;
}

/**
 * The command that attempts a failed scheduled transition again, or null (SH969).
 *
 * The feed sends the batch op and the address; a person reading a terminal or a handoff wants the
 * command they would type, and `shio publish` takes exactly that address.
 */
export function retryCommand(retry) {
  if (!retry?.address) return null;
  if (retry.op === "post.publish") return `shio publish ${retry.address}`;
  if (retry.op === "post.unpublish") return `shio publish --unpublish ${retry.address}`;
  return null;
}

/**
 * The warnings, as questions a person can answer.
 *
 * Errors are deliberately *not* listed one by one: they belong to the agent's own loop, the
 * summary already says how many survived, and reproducing them here would turn the handoff
 * document into a second copy of the lint report that drifts from the first.
 */
function openQuestions(verify) {
  const warnings = (verify?.findings ?? []).filter((finding) => finding?.severity === "warning");
  if (warnings.length === 0) return [];
  const lines = ["## Open questions", "", "Things the CMS cannot decide on its own:", ""];
  for (const finding of warnings) {
    const where = [finding.address, finding.field].filter(Boolean).join(" · ");
    lines.push(`- ${finding.message}${where ? ` (\`${where}\`)` : ""}`);
  }
  lines.push("");
  return lines;
}

/**
 * Everything that would make the lists above less than complete.
 *
 * Collected rather than inlined so the caller can also act on it — `shio report --mark`
 * refuses to advance its cursor past a range it could not read whole, which is the same
 * refusal `pull --content` makes about a truncated projection.
 */
function collectCaveats(changes, verify) {
  const caveats = [];
  if (changes?.more) {
    caveats.push(
      "More changes exist after this range — re-run from the cursor below to see the rest.",
    );
  }
  for (const note of changes?.notes ?? []) caveats.push(note);
  if (verify?.truncated) {
    caveats.push(
      `The verification report was cut at ${verify.findings?.length ?? 0} of ${verify.found} ` +
        "findings — raise `--limit` to see them all.",
    );
  }
  if (verify?.partial) {
    caveats.push(
      `The verification covered a subset of the content${verify.scope ? ` (${verify.scope})` : ""}, ` +
        "so a clean result is not a statement about the whole site.",
    );
  }
  for (const note of verify?.notes ?? []) caveats.push(note);
  return caveats;
}

function footer(changes) {
  const cursor = changes?.cursor;
  if (!cursor) return [];
  return [
    "---",
    "",
    `Range ends at cursor \`${cursor}\` — pass it as \`--since\` for the next handoff.`,
  ];
}

/** `post:Blog/blog/hello` → `Blog`. */
function siteOf(address) {
  const match = /^post:([^/]+)\//.exec(String(address ?? ""));
  return match ? match[1] : null;
}

/** `post:Blog/blog/hello` → `/blog/hello`. An `id:` or `folder:` address has no path. */
function pathOf(address) {
  const match = /^post:[^/]+(\/.*)$/.exec(String(address ?? ""));
  return match ? match[1] : null;
}

function trimSlash(url) {
  if (!url) return url === undefined ? undefined : "";
  return String(url).replace(/\/+$/, "");
}
