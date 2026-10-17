// SH566: who is allowed to move the window.
//
// The hero player kept the lit step in view with `scrollIntoView`, which reads
// as a container-local call and is not one: it scrolls **every** scrollable
// ancestor, the document included. A reader who scrolled past the hero was
// dragged back to it every 2.6s, for as long as the script ran. The symptom was
// a scroll trap on the home page; the cause was one API doing more than its
// call site claimed.
//
// The assertion is therefore about reach, not about that component: only an
// explicit, reader-initiated navigation may move the window, and on this site
// that is the hash routing in `App.tsx`. Anything that keeps its own content in
// view scrolls its own element: `Element.scrollBy` / `scrollTo` stop there.
//
// A source lint rather than a DOM test because the defect is invisible in
// jsdom, which has no layout and so never scrolls anything.

import { test } from "node:test";
import assert from "node:assert/strict";
import { readdirSync, readFileSync, statSync } from "node:fs";
import { join, relative } from "node:path";
import { fileURLToPath } from "node:url";

const SRC = fileURLToPath(new URL("../src", import.meta.url));

/** The one file whose job *is* to scroll the page: `#anchor` navigation. */
const MAY_SCROLL_THE_WINDOW = new Set(["App.tsx"]);

function sources(dir) {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry);
    if (statSync(path).isDirectory()) return sources(path);
    return /\.tsx?$/.test(entry) ? [path] : [];
  });
}

test("only reader-initiated navigation scrolls the window", () => {
  const offenders = sources(SRC)
    .filter((path) => readFileSync(path, "utf8").includes("scrollIntoView("))
    .map((path) => relative(SRC, path).replaceAll("\\", "/"))
    .filter((path) => !MAY_SCROLL_THE_WINDOW.has(path));

  assert.deepEqual(
    offenders,
    [],
    `scrollIntoView() scrolls every scrollable ancestor including the document, so ` +
      `an autoplaying panel that calls it hijacks the page (SH566). Scroll the ` +
      `container instead: list.scrollBy({ top: delta }). Offenders: ${offenders.join(", ")}`
  );
});

test("the transcript player keeps the lit step in view without leaving its list", () => {
  const src = readFileSync(join(SRC, "components", "agent-transcript.tsx"), "utf8");
  assert.match(src, /list\.scrollBy\(/);
});
