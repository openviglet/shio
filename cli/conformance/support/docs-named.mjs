/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * Whether the published documentation names a subject, the code-to-docs direction (SH1365).
 *
 * One reader for two callers: `docs-vocabulary.conformance.mjs`, which fails on a gap, and
 * `.claude/skills/shio-docs-sync/docs-sync-report.mjs`, which prints every gap. The report found
 * the drift this exists for, and had already been wrong once about what a page names (it did not
 * read the configuration reference's sibling shorthand), so the two must not hold separate copies
 * of the rule.
 */

const kebabToCamel = (s) => s.replace(/-([a-z])/g, (_, c) => c.toUpperCase());

/**
 * @param {string} prose every published page, joined; COVERAGE.md is not a page and must not be in it
 * @returns {(subject: string) => boolean}
 */
export function docsNamer(prose) {
  // The configuration reference writes one row of related keys as
  // `` `shio.agent.read.default-limit` · `.max-limit` ``: each `.suffix` replaces the last segment
  // of the full key that opens the run.
  const shorthand = new Set();
  for (const [, head, tail] of prose.matchAll(/`([a-z][\w.-]*\.)[\w-]+`((?:\s*·\s*`\.[\w-]+`)+)/g)) {
    for (const [, suffix] of tail.matchAll(/`\.([\w-]+)`/g)) shorthand.add(head + suffix);
  }
  return (subject) => prose.includes(subject) || shorthand.has(subject)
    // Spring relaxed binding: `shio.allowed-origins` may be documented as `shio.allowedOrigins`.
    || (subject.startsWith("shio.") && prose.includes(kebabToCamel(subject)));
}
