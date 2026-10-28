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
  assert.match(dockerfile, /-jar \/app\/(cds\/)?app\.jar/, "the entry point starts the jar");
  assert.doesNotMatch(dockerfile, /native-image|native-spike/, "the native build stays in scripts/native-spike/");
});

test("SH1260: both shipped images start from the class data sharing archive with SerialGC", () => {
  for (const file of ["Dockerfile", "Dockerfile.local"]) {
    const dockerfile = read(file);
    assert.match(dockerfile, /-XX:ArchiveClassesAtExit=cds\/app\.jsa -Dspring\.context\.exit=onRefresh/,
      `${file}: the archive is trained in the image build`);
    assert.match(dockerfile, /exec java -XX:SharedArchiveFile=\/app\/cds\/app\.jsa \$\{JAVA_OPTS\}/,
      `${file}: the archive is in the ENTRYPOINT, ahead of JAVA_OPTS, so overriding the heap keeps it`);
    assert.match(dockerfile, /ENV JAVA_OPTS="-XX:\+UseSerialGC -Xms64m -Xmx512m"/,
      `${file}: the SerialGC flags SH1249 measured, not the pinned 512 MB G1 heap`);
    assert.doesNotMatch(dockerfile, /Artemis 2\.43\.0 \(via Netty\) crashes/,
      `${file}: the old Java 21 reason is gone, because no Artemis jar ships any more`);
  }
});

test("SH1261: Hazelcast and JGit, which nothing used, stay out of the jar", () => {
  // The spike found both on the classpath with nothing importing them and no Hazelcast instance
  // ever starting: about 30 of the jar's 163 MB, plus a hazelcast.xml that told a reader a cache
  // was running. A cluster cache that comes back does so as its own task, with a test that proves
  // the instance starts, so this refuses the quiet return of either artifact.
  const pom = read("shio-app/pom.xml");
  for (const artifact of ["hazelcast-all", "hazelcast", "org.eclipse.jgit"]) {
    assert.ok(!pom.includes(`<artifactId>${artifact}</artifactId>`),
      `shio-app/pom.xml declares ${artifact} again`);
  }
  assert.ok(!existsSync(join(ROOT, "shio-app", "src", "main", "resources", "hazelcast.xml")),
    "hazelcast.xml configures a cache nothing starts");
});

test("SH1249: the spike can still be reproduced from the files the spec names", () => {
  for (const file of ["Dockerfile.native", "Dockerfile.jvm", "measure.mjs", "measure-all.ps1",
    "probe.mjs", "prune-agent-metadata.mjs"]) {
    assert.ok(existsSync(join(ROOT, "scripts", "native-spike", file)), `scripts/native-spike/${file}`);
  }
  assert.ok(existsSync(join(ROOT, "docs", "specs", "SH1249-graalvm-native-spike.md")));
});
