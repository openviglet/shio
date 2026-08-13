/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH434 — the optional browser, resolved **before anything is armed**.
 *
 * ## Why this is a module and not three copies of a try/catch
 *
 * `snapshot`, `audit` and `clone --render` each imported `playwright` dynamically at the moment they
 * needed it, each with its own near-identical teaching error. Two things went wrong with that, and
 * only the second one is visible:
 *
 * - **Where** the refusal happened. The import sat behind work that had already run: `snapshot` and
 *   `audit` had asked the instance for its sites, `clone --render` had fetched robots.txt. So a run
 *   that could never succeed printed its refusal while the process was still carrying live handles,
 *   and `process.exit(1)` on Windows was measured aborting instead — `Assertion failed:
 *   !(handle->flags & UV_HANDLE_CLOSING)` and exit **-1073740791** (`0xC0000409`), which is
 *   `abort()`. A refusal that crashes is not a refusal: a gate reads a crash code where the CLI
 *   intended one, and a native assertion printed under a Shio command reads as a Shio defect.
 * - **How many** refusals there were. Three copies of one message is three places for the install
 *   line to drift, and `clone.mjs` already carried the rule the other two were missing — SH378's
 *   comment says the asset-site check runs "before any request at all, including robots.txt", one
 *   line above the browser import that did not.
 *
 * So: one function, called **first** in each of the three commands, before a socket is opened or a
 * file is touched. The `ctx.import` seam is what makes the absence testable without uninstalling
 * anything (`defaultContext` wires the real one).
 *
 * ## What it deliberately does not check
 *
 * Whether the **browser** is downloaded, as opposed to the package being installed. That is a real
 * second state — `pnpm add -D playwright` without `npx playwright install chromium` — and
 * `chromium.launch()` is what reports it. Pre-empting it here would mean deciding, from a path, that
 * a working install is broken, and a false refusal on a machine that can take the picture is worse
 * than the message arriving one step later.
 */

/** Both halves, in one line, because installing the package without the browser is a real state. */
export const INSTALL = "pnpm add -D playwright && npx playwright install chromium";

/**
 * Resolves `playwright`, or refuses in a way the caller can act on (P6).
 *
 * @param {object} options
 * @param {string} options.command the command name, so the message says which one refused
 * @param {string} options.why     why the dependency is optional *for this command* — the sentence
 *                                 that points at the browser-free alternative, which is different
 *                                 for a picture, an audit and a crawl
 * @param {string} [options.needs] what needs it, when it is a flag rather than the command
 * @param {object} ctx             the IO context; `import` is the seam, `env.SHIO_DEBUG` adds the
 *                                 resolution root
 */
/**
 * Whether `playwright` resolves at all — asked, never assumed (SH688).
 *
 * <p>`shio judge` used to leave `audit` out unless `--browser` was passed, and reported it as
 * *"not asked for"*. On the one investigation whose whole question was **why does this page look
 * wrong**, the only instrument that renders in a browser and the only one that returns geometry sat
 * out, and the session went to hand-parsing 1.1 MB of HTML and then to asking a human for a
 * screenshot.</p>
 *
 * <p>So the default is a fact rather than a preference: run it when it can run. This is the question
 * that makes that possible, and it is deliberately <b>not</b> {@link requireBrowser} with a
 * try/catch — a caller reading a boolean must not have to construct and discard a teaching error to
 * learn a yes, and the refusal's wording is for somebody who asked for the browser.</p>
 *
 * <p>Resolution only. It cannot tell whether `chromium` itself is installed — `pnpm add -D
 * playwright` without `npx playwright install chromium` is a real second state, and `launch()` is
 * what reports it. That failure still arrives from the instrument, which is where the class note
 * says it belongs.</p>
 */
export async function browserAvailable(ctx) {
  const load = typeof ctx?.import === "function" ? ctx.import : (name) => import(name);
  try {
    await load("playwright");
    return true;
  } catch {
    return false;
  }
}

export async function requireBrowser({ command, why, needs = "this command" }, ctx) {
  const load = typeof ctx?.import === "function" ? ctx.import : (name) => import(name);
  let playwright;
  try {
    playwright = await load("playwright");
  } catch {
    throw new Error(refusal({ command, why, needs }, ctx, "which is not installed"));
  }
  // SH726 — remembered against the resolved module, so `launchBrowser` can write the SAME refusal
  // without any caller repeating a `why`. The three commands launch chromium from inside helpers
  // that never had these fields in scope (`renderer`, `withBrowser`, `inspect`), and passing them
  // down would have put three copies of the sentence back — which is the drift this module's header
  // is about. A WeakMap because the key is a module namespace nobody may mutate.
  REFUSALS.set(playwright, { command, why, needs });
  return playwright;
}

/** What each resolved `playwright` was asked for, so the second refusal reads like the first. */
const REFUSALS = new WeakMap();

/** The one refusal both states produce, so the install line is written once (SH726). */
function refusal({ command, why, needs }, ctx, state) {
  return `shio ${command}: ${needs} needs playwright, ${state}.\n`
    + `  ${INSTALL}\n`
    + why
    + (ctx?.env?.SHIO_DEBUG ? `\n(resolved from ${process.cwd()})` : "");
}

/**
 * Playwright's own way of saying the package is here and the browser is not.
 *
 * <p>Matched on the sentence rather than on an error class, because it arrives as a plain `Error`;
 * both halves are required so an unrelated launch failure — a sandbox refusing, a profile locked —
 * keeps its own message instead of being relabelled as a missing install, which would send the
 * reader to a command that changes nothing.</p>
 */
function isMissingBrowser(error) {
  const message = String(error?.message ?? "");
  return /Executable doesn't exist/i.test(message) && /playwright install/i.test(message);
}

/**
 * Launch chromium, or refuse the way {@link requireBrowser} does (SH726).
 *
 * <h2>The state this closes, and the one it deliberately still does not pre-empt</h2>
 *
 * <p>This module's header decides not to check whether the browser is <em>downloaded</em>: that
 * cannot be answered from a path without refusing machines that work, so it is left to `launch()`.
 * Correct, and incomplete — nothing translated what `launch()` then says. A plain `pnpm install`
 * fetches the package and not the binary, so on a runner the reader gets Playwright's box drawing
 * naming `pnpm exec playwright install`, and never the line this product publishes in three
 * commands' help. CI found it, on the one environment where that state is the normal one.</p>
 *
 * <p>Nothing is decided in advance here either. The launch is attempted exactly as before and only
 * a failure that says so is re-labelled, so a machine that can take the picture still does.</p>
 *
 * @param playwright the resolved module, as {@link requireBrowser} returns it
 * @param ctx        the IO context
 */
export async function launchBrowser(playwright, ctx) {
  try {
    return await playwright.chromium.launch();
  } catch (error) {
    if (!isMissingBrowser(error)) throw error;
    // A caller that resolved the module some other way still gets the install line; what it loses
    // is the sentence naming the browser-free alternative, which is per-command and unguessable.
    const asked = REFUSALS.get(playwright)
      ?? { command: "clone", needs: "the browser", why: "" };
    throw new Error(
      `${refusal(asked, ctx, "whose browser is not downloaded")}\n`
      + `(playwright said: ${String(error.message).split("\n")[0]})`,
    );
  }
}
