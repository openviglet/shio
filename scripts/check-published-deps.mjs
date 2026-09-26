/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1076 — the viglet-core BOM that pom.xml pins is on Maven Central, or this says so first.
 *
 * SH110 raised the pin to 2026.3.11, a version only one machine's ~/.m2 held. Every local gate kept
 * passing and every CI run failed before the reactor — no count, no failing name — for a month. A
 * pin is a claim about a repository this checkout does not control, so it is checked against that
 * repository: the first step of the Java job, where a missing artifact now fails with the version,
 * the URL asked and what to do, instead of as a POM Maven cannot read.
 *
 * Network, so never in `pnpm test`. The pure half — which version is pinned, and where Central
 * keeps it — is exported and tested there.
 */
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const CENTRAL = "https://repo1.maven.org/maven2";

/** The `<viglet-core.version>` pom.xml pins, or null when it pins none. */
export function pinnedVersion(pom) {
  const match = /<viglet-core\.version>\s*([^<\s]+)\s*<\/viglet-core\.version>/.exec(pom);
  return match ? match[1] : null;
}

/** Where Central keeps that version of the BOM. */
export function bomUrl(version) {
  return `${CENTRAL}/com/viglet/core/viglet-core-bom/${version}/viglet-core-bom-${version}.pom`;
}

async function main() {
  const root = join(dirname(fileURLToPath(import.meta.url)), "..");
  const version = pinnedVersion(readFileSync(join(root, "pom.xml"), "utf8"));
  if (!version) {
    console.error("SH1076: pom.xml pins no <viglet-core.version>; nothing to check.");
    return 1;
  }
  const url = bomUrl(version);
  const res = await fetch(url, { method: "HEAD" });
  if (res.ok) {
    console.log(`SH1076: viglet-core-bom ${version} is on Maven Central.`);
    return 0;
  }
  console.error(`SH1076: viglet-core-bom ${version} is not on Maven Central (HEAD ${url} answered `
    + `${res.status}). pom.xml pins a version CI cannot resolve, so every job would fail before a `
    + "test ran. Publish viglet-core " + version + ", or pin the last published version: "
    + `${CENTRAL}/com/viglet/core/viglet-core-bom/maven-metadata.xml lists them.`);
  return 1;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === process.argv[1]) {
  process.exitCode = await main();
}
