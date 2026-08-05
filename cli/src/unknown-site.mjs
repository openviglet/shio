/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * SH439 — **a refusal must name a remedy the surface it arrived on can express.**
 *
 * `shio convert --site Viglet2` against a site that does not exist printed the plan and then refused
 * with `No readable site 'Viglet2'` and the fix *"Address a site that exists, or add a `site.upsert`
 * op for it earlier in the same batch."* That message is the shared write resolver's, and on the
 * batch surface it is exactly right — a caller composing ops can add one. `convert` composes its own
 * document and hands it over, so the one thing the reader was told to do was the one thing this
 * surface could not express. P6 makes an instruction naming something that does not exist worse than
 * no instruction at all, and it landed at the worst moment: converting into a **new** site is the
 * normal shape of a replication, so the first real run of the phase hit it.
 *
 * The flag is the fix — `--create-site` on `convert` and on `apply`'s document mode, compiled to the
 * same `site.upsert` op, so it means one thing everywhere. This module is the other half: the
 * problem body is rewritten on the way out so the *message* names the flag rather than the op. Only
 * the `fix` and the `example` change; the title, detail, `allowed` list and `didYouMean` are the
 * resolver's and stay, because they are about the site and not about the surface.
 */

/** The problem `type` suffix the write resolver raises for a site nobody has made. */
const UNKNOWN_SITE = "/errors/unknown-site";

/**
 * The same failure, told in the flags of the command it happened on.
 *
 * <p>Mutates and returns the error rather than wrapping it: `formatError` prints the SH80 teaching
 * body, and a wrapper would either lose that body or print two versions of one problem.</p>
 *
 * <p>Anything that is not this problem is returned untouched — including a 404 with no teaching body
 * at all. Guessing that some other 404 is a missing site is how a caller ends up passing
 * `--create-site` at a problem it cannot fix.</p>
 *
 * @param error   the failure from a plan or apply call
 * @param command the command name, for the example line
 * @param site    the site the document names
 */
export function retellUnknownSite(error, { command, site }) {
  if (error?.status !== 404 || typeof error.body !== "string") return error;
  let problem = null;
  try {
    problem = JSON.parse(error.body);
  } catch {
    return error;
  }
  if (!problem || typeof problem !== "object") return error;
  if (typeof problem.type !== "string" || !problem.type.endsWith(UNKNOWN_SITE)) return error;

  problem.fix = "Address a site that exists, or pass --create-site to make it (blank) as this "
    + "run's first step.";
  problem.example = `shio ${command} --site ${site} --create-site`;
  error.body = JSON.stringify(problem);
  return error;
}
