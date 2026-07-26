/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Tiny, dependency-free argv parser shared by every `shio` subcommand. Supports
 * `--key value`, `--key=value`, boolean `--flag`, `-h`/`-y` shorthands and bare
 * positionals. Kebab-case flag names are exposed as camelCase (`--import-from`
 * → `importFrom`).
 *
 * A flag named in `repeatable` collects into an array instead of last-one-wins. Added
 * for `shio apply --blueprint … --param k=v --param k2=v2` (SH93): a blueprint's
 * parameters are a set, and silently keeping only the last one would apply a package
 * with defaults the caller thought they had overridden.
 */

function toCamel(key) {
  return key.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
}

/**
 * @param {string[]} args
 * @param {{ booleans?: string[], repeatable?: string[] }} [opts] `booleans` take no
 *   value; `repeatable` collect every occurrence into an array.
 * @returns {{ flags: Record<string, string|boolean|string[]>, positionals: string[] }}
 */
export function parseArgs(args, { booleans = [], repeatable = [] } = {}) {
  const flags = {};
  const positionals = [];
  const boolSet = new Set(booleans);
  const manySet = new Set(repeatable.map(toCamel));
  const set = (key, value) => {
    const name = toCamel(key);
    if (!manySet.has(name)) {
      flags[name] = value;
      return;
    }
    const existing = flags[name];
    flags[name] = Array.isArray(existing) ? [...existing, value] : [value];
  };
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a.startsWith("--")) {
      const eq = a.indexOf("=");
      if (eq !== -1) {
        set(a.slice(2, eq), a.slice(eq + 1));
      } else {
        const key = a.slice(2);
        if (boolSet.has(key)) {
          set(key, true);
        } else if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
          set(key, args[++i]);
        } else {
          set(key, true);
        }
      }
    } else if (a === "-h") {
      flags.help = true;
    } else if (a === "-y") {
      flags.yes = true;
    } else if (a.startsWith("-")) {
      flags[a.slice(1)] = true;
    } else {
      positionals.push(a);
    }
  }
  return { flags, positionals };
}
