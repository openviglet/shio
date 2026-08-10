/**
 * Reading a site's blueprint record back (SH220).
 *
 * <h2>Why a re-apply should default to it</h2>
 *
 * <p>SH155 records the package, its version and its parameters as an instance-memory note, so
 * "which blueprint is this site" is answerable. Re-applying still meant re-supplying every
 * `--param` from memory or from a transcript — and that is not merely tedious. <b>A parameter the
 * operator misremembers does not fail.</b> It rebuilds the site with a different brand, headline
 * or CTA, and `changed: n` reports it as ordinary work. The safe default is the value that
 * produced the site.</p>
 *
 * <h2>The note is prose, and it can be cut</h2>
 *
 * <p>`ShBlueprintRecord` writes a sentence — `Built from blueprint 'blog' 1.0.0 — params:
 * authorName=Ada; blogTitle=Notes` — bounded at 400 characters, and when it does not fit it ends
 * with `(+3 more)`. That admission is the whole reason this parser can be trusted at all: a
 * truncated record supplies *some* parameters and omits others, which is the same defect the task
 * is about arriving from the other side, and silently.</p>
 *
 * <p>So `truncated` travels with the values and the caller has to decide. It is not this module's
 * decision to make, and it must not be invisible.</p>
 *
 * @module
 */

/** The sentence's opening, as `ShBlueprintRecord.noteFor` writes it. */
const HEAD = /^Built from blueprint '([^']+)'/u;

/** Where the parameter list starts, when there is one. */
const PARAMS = " — params: ";

/** The admission that the sentence was cut. */
const CUT = /\(\+(\d+)\s+more\)\s*$/u;

/**
 * Parses one record note.
 *
 * @param {string} note the memory note's text
 * @returns {{blueprint: string, version: string|null, params: Record<string,string>,
 *            truncated: number}|null} `null` when the text is not a record at all
 */
export function parseRecord(note) {
  const text = String(note ?? "").trim();
  const head = HEAD.exec(text);
  if (!head) return null;

  const cut = CUT.exec(text);
  const at = text.indexOf(PARAMS);

  // The version is everything between the closing quote and either the parameter list or the
  // full stop that ends a parameterless sentence. Taken by boundary rather than by one clever
  // pattern: the first version of this used a regex whose terminator alternation included `.`,
  // so `1.0.0` parsed as `1` — a semver read as a major, which is the kind of wrong that looks
  // right in every test that only checks the name.
  const afterName = text.slice(head[0].length, at >= 0 ? at : undefined).trim();
  const version = afterName.replace(/\.$/u, "").trim();

  const params = {};
  if (at >= 0) {
    const body = text.slice(at + PARAMS.length, cut ? cut.index : undefined);
    for (const pair of body.split(";")) {
      const eq = pair.indexOf("=");
      if (eq < 0) continue;
      const name = pair.slice(0, eq).trim();
      // Everything after the FIRST `=`, because a value may contain one — a query string, a
      // base64 fragment, a CTA href with a tracking parameter on it.
      if (name) params[name] = pair.slice(eq + 1).trim();
    }
  }

  return {
    blueprint: head[1],
    version: version || null,
    params,
    truncated: cut ? Number(cut[1]) : 0,
  };
}

/**
 * The record for one site, out of what `GET /agent/memory` returned.
 *
 * <p>Keyed on the blueprint name rather than taking the first record: a site can carry more than
 * one — SH155 files a note per package — and re-applying `docs` into a site that also has `blog`
 * must not inherit `blog`'s brand.</p>
 */
export function recordFor(notes, blueprint) {
  for (const note of Array.isArray(notes) ? notes : []) {
    const parsed = parseRecord(note?.note ?? note?.text ?? "");
    if (parsed && parsed.blueprint === blueprint) return parsed;
  }
  return null;
}

/**
 * Merges a record's values under the ones the caller gave.
 *
 * <p><b>An explicit `--param` always wins.</b> The record is a default, not a policy: an operator
 * re-applying with a new headline is doing the thing the flag is for, and a record that overrode
 * it would make the site unchangeable through the command that changes it.</p>
 *
 * @returns {{params: Record<string,string>, defaulted: string[], truncated: number}}
 *          `defaulted` names the values that came from the record — which is the load-bearing
 *          half, because <b>a parameter that was silently supplied is one nobody reviews</b>
 */
export function withRecordDefaults(given, record) {
  const params = { ...(given ?? {}) };
  const defaulted = [];
  if (!record) return { params, defaulted, truncated: 0 };

  for (const [name, value] of Object.entries(record.params)) {
    if (Object.hasOwn(params, name)) continue;
    params[name] = value;
    defaulted.push(name);
  }
  return { params, defaulted: defaulted.sort(), truncated: record.truncated };
}

/**
 * What the caller is told, and it is never nothing when a value was defaulted.
 *
 * <p>The truncation warning is separate from the list and worded as a limit rather than as a
 * failure: the record did not lose the parameters, it never held them, and an operator who reads
 * "3 more" knows exactly how much of the answer is theirs to supply.</p>
 */
export function describeDefaults({ defaulted, truncated }, blueprint) {
  const lines = [];
  if (defaulted.length) {
    lines.push(
      `shio apply: ${defaulted.length} parameter(s) taken from what this site was built with `
        + `(${blueprint}): ${defaulted.join(", ")}. Pass --param to override any of them.`,
    );
  }
  if (truncated > 0) {
    lines.push(
      `shio apply: the record was cut short and does not name ${truncated} of its parameter(s), `
        + "so those fall back to the package's defaults. Pass them explicitly if they matter.",
    );
  }
  return lines.length ? `${lines.join("\n")}\n` : "";
}
