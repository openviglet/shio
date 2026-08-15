/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH499 — what this product actually calls things, read from the product.
 *
 * ## Why this is read rather than listed
 *
 * The failure Block R repairs is a page written once against a product that then moved. A
 * checked-in list of "the verbs as of today" would rot in exactly the same way and by exactly
 * the same mechanism — it would be a second copy of the docs, one directory closer. So every
 * name here is derived from the file that declares it: `COMMANDS` in `cli/src/index.mjs`, the
 * Spring mapping annotations, the `"shio_*"` literals the MCP tools return from `name()`, and
 * the `@ConfigurationProperties` classes. Rename any of those and this reader reports the new
 * name on the next run, with no edit here.
 *
 * ## What it deliberately does not do
 *
 * It does not start an instance, and it does not parse Java. Regexes over source are exactly
 * as much as is warranted: the question is "does this name still exist", not "what does it
 * do", and a wrong answer is a **failing** run somebody reads rather than a silent pass. The
 * one place that bites is a mapping whose path is a constant (`@RequestMapping(ShSitesAPI.BASE)`),
 * which is common enough here to be resolved rather than skipped — see `constantsOf`.
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

// @scans both reason: verbs and tool names are extracted from JS and Java source, and the
// registries it reads are declarations rather than prose, but the walk does not strip (SH740).

const REPO = fileURLToPath(new URL("../../../", import.meta.url));

const JAVA_MAIN = join(REPO, "shio-app", "src", "main", "java");
const CLI_ENTRY = join(REPO, "cli", "src", "index.mjs");
const APP_PROPERTIES = join(REPO, "shio-app", "src", "main", "resources", "application.properties");

/** `multiTenant` → `multi-tenant`: Spring's relaxed binding, and the spelling docs use. */
const kebab = (name) => name.replace(/[A-Z]/g, (upper) => `-${upper.toLowerCase()}`);

function javaFiles(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) javaFiles(full, out);
    else if (name.endsWith(".java")) out.push(full);
  }
  return out;
}

/**
 * The CLI's verbs, from the registry `index.mjs` dispatches on.
 *
 * <p>The same slice `cli/test/help.test.mjs` takes, on purpose: two readers of one registry
 * that disagree about how to read it would be a third thing to keep in sync.</p>
 */
export function cliVerbs(entry = CLI_ENTRY) {
  const source = readFileSync(entry, "utf8");
  const block = source.slice(source.indexOf("const COMMANDS = {"), source.indexOf("const HELP ="));
  const names = [...block.matchAll(/^\s{2}(\w+):/gm)].map(([, name]) => name);
  if (names.length < 5) throw new Error(`SH499: no command registry found in ${entry}`);
  return new Set(names);
}

/** `public static final String BASE = "/sites";` → `{ BASE: "/sites" }`, per file. */
function constantsOf(source) {
  const out = new Map();
  for (const [, name, value] of source.matchAll(
    /static\s+final\s+String\s+(\w+)\s*=\s*"([^"]*)"\s*;/g,
  )) {
    out.set(name, value);
  }
  return out;
}

/**
 * One mapping annotation's paths.
 *
 * <p>Returns `[""]` — not `[]` — for the no-argument and attributes-only forms
 * (`@GetMapping`, `@PostMapping(consumes = …)`), because both map the class's own base path and
 * dropping them would report a documented endpoint as missing. `POST /mcp` is exactly that
 * shape, so this is not a hypothetical.</p>
 */
function pathsOf(arg, constants) {
  if (arg == null || arg.trim() === "") return [""];
  // A path literal starts with `/` or `*`. Without this, `@GetMapping(produces = "application/
  // javascript")` contributes a route of `/preview` + `application/javascript` — a media type read
  // as a path, which is a table that answers questions nobody asked and hides none that matter.
  const literals = [...arg.matchAll(/"([^"]*)"/g)]
    .map(([, value]) => value)
    .filter((value) => /^[/*]/.test(value));
  const named = [...arg.matchAll(/(?:^|[\s,({])(?:\w+\.)?([A-Z][A-Z0-9_]{2,})\b/g)]
    .map(([, name]) => constants.get(name))
    .filter((value) => value != null);
  const found = [...literals, ...named];
  if (found.length) return found;
  // Attributes only (`consumes = …`, `produces = …`): a mapping with no path is the base.
  return /=/.test(arg) ? [""] : [];
}

const joinPath = (base, sub) => `${base}${sub}`.replace(/\/{2,}/g, "/").replace(/(.)\/$/, "$1");

/**
 * Every `{method, path}` this backend maps, as `["GET /api/v2/agent/manifest", …]`.
 *
 * <p>Class-level `@RequestMapping` is taken as the base and method-level `@(Get|Post|…)Mapping`
 * as the leaf. `@RequestMapping` at method level is read too — rare here, and silently dropping
 * it would make a real endpoint look retired.</p>
 */
export function httpRoutes(dir = JAVA_MAIN) {
  const routes = new Set();
  for (const file of javaFiles(dir)) {
    const source = readFileSync(file, "utf8");
    if (!/@RequestMapping|@(Get|Post|Put|Patch|Delete)Mapping/.test(source)) continue;
    for (const route of routesOf(source)) routes.add(route);
  }
  return routes;
}

const VERB = { Get: "GET", Post: "POST", Put: "PUT", Patch: "PATCH", Delete: "DELETE" };

/** The class-level `@RequestMapping`, which every leaf below hangs off. */
function baseOf(source, constants) {
  const classDecl = source.search(/\n(?:public\s+)?(?:final\s+)?class\s/);
  const header = classDecl < 0 ? source : source.slice(0, classDecl);
  const bases = [...header.matchAll(/@RequestMapping\s*\(([^)]*)\)/g)]
    .flatMap(([, arg]) => pathsOf(arg, constants));
  return bases.find((each) => each.startsWith("/")) ?? "";
}

/** One controller's routes. Split out of `httpRoutes` so each annotation form reads on its own. */
function routesOf(source) {
  const constants = constantsOf(source);
  const base = baseOf(source, constants);
  const routes = [];

  for (const [, verb, arg] of source.matchAll(
    /@(Get|Post|Put|Patch|Delete)Mapping\s*\(([\s\S]*?)\)\s*(?:public|@)/g,
  )) {
    for (const sub of pathsOf(arg, constants)) routes.push(`${VERB[verb]} ${joinPath(base, sub)}`);
  }
  // The no-argument form, which the pattern above cannot see because it has no parentheses.
  for (const [, verb] of source.matchAll(/@(Get|Post|Put|Patch|Delete)Mapping\s*\n/g)) {
    routes.push(`${VERB[verb]} ${joinPath(base, "")}`);
  }
  // `@RequestMapping(method = …)` at method level: rare here, and dropping it would retire a
  // real endpoint on paper.
  for (const [, arg] of source.matchAll(/@RequestMapping\s*\(([^)]*method[^)]*)\)/g)) {
    for (const [, method] of arg.matchAll(/RequestMethod\.(\w+)/g)) {
      for (const sub of pathsOf(arg, constants)) routes.push(`${method} ${joinPath(base, sub)}`);
    }
  }
  return routes;
}

/**
 * Whether a documented path is one of `routes`.
 *
 * <p>Segment-wise, because the two sides spell a variable differently and both spellings are
 * correct: a page writes `/api/v2/post-type/Article` with a real value where the mapping writes
 * `{name}`, and `/sites/{site}/{format}/{locale}/{path}` against a mapping of `/sites/**`. So a
 * `{…}` on either side matches anything, a trailing `**` matches the rest, and everything else
 * is compared literally — a *renamed* segment, which is the failure this exists to catch, still
 * fails.</p>
 */
export function routeExists(method, path, routes) {
  const want = path.split("?")[0].replace(/\/$/, "").split("/");
  for (const route of routes) {
    const [verb, template] = route.split(" ");
    if (verb === method && segmentsMatch(want, template.replace(/\/$/, "").split("/"))) return route;
  }
  return null;
}

/** A `{…}`/`<…>` on either side matches anything, a trailing `**` matches the rest. */
function segmentsMatch(want, have) {
  const variable = (segment) => /^[{<].*[}>]$/.test(segment);
  for (let i = 0; i < Math.max(want.length, have.length); i += 1) {
    if (have[i] === "**") return true;
    if (want[i] == null || have[i] == null) return false;
    if (variable(want[i]) || variable(have[i])) continue;
    if (want[i] !== have[i]) return false;
  }
  return true;
}

/** The MCP tool names, from the `name()` each tool returns. */
export function mcpTools(dir = join(JAVA_MAIN, "com", "viglet", "shio", "api", "agent", "mcp")) {
  const names = new Set();
  for (const file of javaFiles(dir)) {
    for (const [, name] of readFileSync(file, "utf8").matchAll(/"(shio_[a-z_]+)"/g)) names.add(name);
  }
  if (!names.size) throw new Error(`SH499: no MCP tool names found under ${dir}`);
  return names;
}

/**
 * The MCP resource URIs this build registers (SH760).
 *
 * <p>Read from the beans rather than listed, for `mcpTools`' reason: a hand-typed vocabulary is
 * the thing that rots. A **template** is kept as its declared prefix — `shio://schema/` — because
 * that is what `ShMcpResource#matches` keys on and therefore what a caller must get right; the
 * variable after it is the caller's own, and a population that expanded it would be counting
 * post-types rather than resources.</p>
 */
export function mcpResources(dir = join(JAVA_MAIN, "com", "viglet", "shio", "api", "agent", "mcp")) {
  const uris = new Set();
  for (const file of javaFiles(dir)) {
    for (const [, uri] of readFileSync(file, "utf8").matchAll(/"(shio:\/\/[^"]*)"/g)) uris.add(uri);
  }
  if (!uris.size) throw new Error(`SH760: no MCP resource URIs found under ${dir}`);
  return uris;
}

/**
 * The field names a `@ConfigurationProperties` class declares.
 *
 * <p>Cut-then-split rather than one regex: a type is `[\w.<>,\s]` and a name is `\w`, so the two
 * are ambiguous where they meet and a pattern spanning both backtracks superlinearly. This removes
 * the ambiguity outright instead of bounding it — the same trade `docs/agents/cli.md` records for
 * the tag scanner, for the same reason. It also fixes what the greedy form got *wrong*: on
 * `private boolean deIndexOnUnpublish = true;` a trailing `(\w+)[ \t]*[=;]` matches `true` at the
 * semicolon, so a field with an initializer was read as its default value and the two turing keys
 * a page documents came back missing.</p>
 */
function declaredFields(source) {
  return fieldsWithTypes(source).map(({ name }) => name);
}

/** The same cut-then-split, keeping the declared type — a nested group is identified by it. */
function fieldsWithTypes(source) {
  const out = [];
  for (const line of source.split("\n")) {
    if (!/^[ \t]*private[ \t]/.test(line)) continue;
    const declaration = line.split(/[=;]/)[0].trim().split(/[ \t]+/);
    const name = declaration.at(-1);
    if (declaration.length >= 3 && /^\w+$/.test(name)) out.push({ name, type: declaration.at(-2) });
  }
  return out;
}

/**
 * The nested `public static class` groups a properties class declares, by name.
 *
 * <p>SH628 — the reader used to flatten every `private` field in the file onto the outer prefix,
 * which is wrong in <b>both</b> directions and was invisible because the two errors hide each
 * other. `ShCdaProperties` binds `shio.cda` and holds `Cache`, `RateLimit` and `Preview`: the flat
 * read invented `shio.cda.ttl-seconds`, a key nothing binds, and never derived
 * `shio.cda.preview.ttl-seconds`, a key that exists — so a page documenting the real key was
 * reported as documenting a key that does not exist, and a page documenting the invented one would
 * have passed. Found when SH624's preview-token page wrote the three keys correctly and the gate
 * called all three missing.</p>
 *
 * <p>Brace counting rather than a regex over the body: a nested class contains its own braces, and
 * "up to the next `}`" stops at the first field with an initializer block.</p>
 */
function nestedGroups(source) {
  const out = new Map();
  const opener = /(?:public|protected|private)?\s*static\s+class\s+(\w+)[^{]*\{/g;
  for (const match of source.matchAll(opener)) {
    const from = match.index + match[0].length;
    let depth = 1;
    let at = from;
    while (at < source.length && depth > 0) {
      if (source[at] === "{") depth += 1;
      else if (source[at] === "}") depth -= 1;
      at += 1;
    }
    out.set(match[1], source.slice(from, at - 1));
  }
  return out;
}

/**
 * Every key one `@ConfigurationProperties` class binds, nested groups included.
 *
 * <p>A field whose declared type is one of the file's nested classes is a <i>group</i>, not a
 * value: its own name becomes a path segment and the nested class's fields hang off it. The outer
 * field itself is deliberately not emitted — `shio.cda.preview` alone binds nothing a reader can
 * set.</p>
 */
function boundKeys(source, prefix) {
  const groups = nestedGroups(source);
  // Cut each group's body out first, so what remains is exactly the outer class's own fields.
  let outer = source;
  for (const body of groups.values()) outer = outer.replace(body, "");
  const keys = [];
  for (const field of fieldsWithTypes(outer)) {
    if (groups.has(field.type)) {
      for (const inner of declaredFields(groups.get(field.type))) {
        keys.push(`${prefix}.${kebab(field.name)}.${kebab(inner)}`);
      }
      continue;
    }
    keys.push(`${prefix}.${kebab(field.name)}`);
  }
  return keys;
}

/**
 * Every `shio.*` key this instance binds, from the classes that bind them.
 *
 * <p>A `@ConfigurationProperties("shio.turing")` class with a field `apiKey` is the declaration
 * of `shio.turing.api-key`; there is no list of keys anywhere else, which is why the docs are
 * the only other place they are written down and why they can drift unnoticed.</p>
 */
export function shioProperties(dir = JAVA_MAIN, properties = APP_PROPERTIES) {
  const keys = new Set();
  for (const file of javaFiles(dir)) {
    const source = readFileSync(file, "utf8");
    const bound = source.match(/@ConfigurationProperties\(\s*(?:prefix\s*=\s*)?"([^"]+)"/);
    if (bound) {
      for (const key of boundKeys(source, bound[1])) keys.add(key);
    }
    for (const [, key] of source.matchAll(/\$\{(shio\.[a-zA-Z0-9.<>-]+)/g)) keys.add(key);
  }
  try {
    for (const line of readFileSync(properties, "utf8").split("\n")) {
      const declared = /^[ \t]*(shio\.[\w.-]+)[ \t]*=/.exec(line);
      if (declared) keys.add(declared[1]);
    }
  } catch { /* An instance may ship without one; the bound classes are the authority anyway. */ }
  return keys;
}

/** All four, read once — every instrument wants the same set and none should read twice. */
export function productVocabulary() {
  return {
    cliVerbs: cliVerbs(),
    routes: httpRoutes(),
    mcpTools: mcpTools(),
    properties: shioProperties(),
  };
}
