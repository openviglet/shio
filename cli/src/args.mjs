/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 *
 * Tiny, dependency-free argv parser shared by every `shio` subcommand. Supports
 * `--key value`, `--key=value`, boolean `--flag`, `-h`/`-y` shorthands and bare
 * positionals. Kebab-case flag names are exposed as camelCase (`--import-from`
 * → `importFrom`).
 */

function toCamel(key) {
  return key.replace(/-([a-z])/g, (_, c) => c.toUpperCase());
}

/**
 * @param {string[]} args
 * @param {{ booleans?: string[] }} [opts] flag names that take no value.
 * @returns {{ flags: Record<string, string|boolean>, positionals: string[] }}
 */
export function parseArgs(args, { booleans = [] } = {}) {
  const flags = {};
  const positionals = [];
  const boolSet = new Set(booleans);
  for (let i = 0; i < args.length; i++) {
    const a = args[i];
    if (a.startsWith("--")) {
      const eq = a.indexOf("=");
      if (eq !== -1) {
        flags[toCamel(a.slice(2, eq))] = a.slice(eq + 1);
      } else {
        const key = a.slice(2);
        if (boolSet.has(key)) {
          flags[toCamel(key)] = true;
        } else if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
          flags[toCamel(key)] = args[++i];
        } else {
          flags[toCamel(key)] = true;
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
