import assert from "node:assert/strict";
import { existsSync, readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { join } from "node:path";
import { test } from "node:test";

// @scans prose reason: the shipped Dockerfile and the Maven pom are read as text, and the profile
// and base image named inside them are the subject rather than any code (SH740).

/**
 * SH1249 — the native image is a measured no-go, and this keeps it one until somebody decides
 * otherwise in a diff.
 *
 * <p>The spike (docs/specs/SH1249-graalvm-native-spike.md) built a GraalVM CE native image that
 * boots in about 4 s, and answers 500 on any reflective path its tracing run never visited, with
 * nothing in the log. So the `native-spike` Maven profile exists to reproduce the table and must
 * stay opt-in, and the shipped image must stay the JVM one. Both are one-line changes that read as
 * harmless in review, which is why they are asserted rather than remembered.</p>
 */

const ROOT = fileURLToPath(new URL("../../", import.meta.url));
const read = (path) => readFileSync(join(ROOT, path), "utf8");

test("SH1249: the native-spike profile is opt-in, never active by default", () => {
  const pom = read("shio-app/pom.xml");
  const profile = /<profile>\s*<id>native-spike<\/id>([\s\S]*?)<\/profile>/.exec(pom);
  assert.ok(profile, "shio-app/pom.xml should still declare the native-spike profile the spec reproduces with");
  assert.doesNotMatch(profile[1], /<activation>/, "native-spike must not activate on its own");
});

test("SH1249: the shipped image runs the JVM jar, not a native binary", () => {
  const dockerfile = read("Dockerfile");
  assert.match(dockerfile, /^FROM eclipse-temurin:\d+-jre\b/m, "the runtime stage is a JRE image");
  assert.match(dockerfile, /-jar \/app\/app\.jar/, "the entry point starts the jar");
  assert.doesNotMatch(dockerfile, /native-image|native-spike/, "the native build stays in scripts/native-spike/");
});

test("SH1249: the spike can still be reproduced from the files the spec names", () => {
  for (const file of ["Dockerfile.native", "Dockerfile.jvm", "measure.mjs", "measure-all.ps1",
    "probe.mjs", "prune-agent-metadata.mjs"]) {
    assert.ok(existsSync(join(ROOT, "scripts", "native-spike", file)), `scripts/native-spike/${file}`);
  }
  assert.ok(existsSync(join(ROOT, "docs", "specs", "SH1249-graalvm-native-spike.md")));
});
