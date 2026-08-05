/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
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
export async function requireBrowser({ command, why, needs = "this command" }, ctx) {
  const load = typeof ctx?.import === "function" ? ctx.import : (name) => import(name);
  try {
    return await load("playwright");
  } catch {
    throw new Error(
      `shio ${command}: ${needs} needs playwright, which is not installed.\n` +
        `  ${INSTALL}\n` +
        why +
        (ctx?.env?.SHIO_DEBUG ? `\n(resolved from ${process.cwd()})` : ""),
    );
  }
}
