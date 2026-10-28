#!/usr/bin/env node
// SH1249 — removes from the tracing agent's output what a native image must NOT carry.
//
// The agent records a JVM run, and on the JVM Hibernate generates its lazy-loading proxies with
// ByteBuddy: so the output registers ByteBuddy's BytecodeProvider and the service file that
// names it. In the native image that provider cannot define a class, and Hibernate finds it by
// ServiceLoader before falling back to "none", so it has to be absent, not merely unused.
//
//   node scripts/native-spike/prune-agent-metadata.mjs scripts/native-spike/agent-metadata/reachability-metadata.json

import { readFileSync, writeFileSync } from "node:fs";

const PRUNED = [
  (entry) => entry.type === "org.hibernate.bytecode.internal.bytebuddy.BytecodeProviderImpl",
  (entry) => entry.glob === "META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider",
];

const file = process.argv[2];
const metadata = JSON.parse(readFileSync(file, "utf8"));
let removed = 0;
for (const [section, entries] of Object.entries(metadata)) {
  if (!Array.isArray(entries)) continue;
  metadata[section] = entries.filter((entry) => {
    const drop = PRUNED.some((match) => match(entry));
    if (drop) removed++;
    return !drop;
  });
}
writeFileSync(file, `${JSON.stringify(metadata, null, 2)}\n`);
console.log(`pruned ${removed} entries from ${file}`);
