/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under GPL-3.0.
 */
import assert from "node:assert/strict";
import { test } from "node:test";
import { mkdtemp, readFile, readdir, rm, writeFile, mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

import { scaffold } from "../src/scaffold.mjs";

async function withTmp(fn) {
  const base = await mkdtemp(join(tmpdir(), "create-shio-app-"));
  try {
    return await fn(base);
  } finally {
    await rm(base, { recursive: true, force: true });
  }
}

test("scaffolds the full template tree", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    const { files } = await scaffold({
      targetDir,
      projectName: "my-site",
      url: "https://cms.example.test",
      site: "site-123",
      token: "tok-xyz",
    });

    for (const expected of [
      "package.json",
      "tsconfig.json",
      "next.config.mjs",
      "lib/shio.ts",
      "app/layout.tsx",
      "app/page.tsx",
      "app/[...slug]/page.tsx",
    ]) {
      assert.ok(files.includes(expected), `missing ${expected}`);
    }
  });
});

test("renames shipped 'gitignore' to '.gitignore'", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({ targetDir, projectName: "my-site" });
    assert.ok(existsSync(join(targetDir, ".gitignore")), ".gitignore should exist");
    assert.ok(!existsSync(join(targetDir, "gitignore")), "raw 'gitignore' should be gone");
  });
});

test("stamps the project name into package.json", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "cool-site");
    await scaffold({ targetDir, projectName: "cool-site" });
    const pkg = JSON.parse(await readFile(join(targetDir, "package.json"), "utf8"));
    assert.equal(pkg.name, "cool-site");
    assert.ok(pkg.dependencies["@viglet/shio-client"], "depends on @viglet/shio-client");
    assert.ok(pkg.dependencies["next"], "depends on next");
  });
});

test("writes .env.local from the supplied connection details", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({
      targetDir,
      projectName: "my-site",
      url: "https://cms.example.test",
      site: "site-123",
      token: "tok-xyz",
    });
    const env = await readFile(join(targetDir, ".env.local"), "utf8");
    assert.match(env, /SHIO_URL=https:\/\/cms\.example\.test/);
    assert.match(env, /SHIO_SITE_ID=site-123/);
    assert.match(env, /SHIO_CDA_TOKEN=tok-xyz/);
  });
});

test("env defaults are sensible when details are omitted", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({ targetDir, projectName: "my-site" });
    const env = await readFile(join(targetDir, ".env.local"), "utf8");
    assert.match(env, /SHIO_URL=http:\/\/localhost:2710/);
    assert.match(env, /SHIO_SITE_ID=\n/);
  });
});

test("catch-all page wires getPostByUrl with the site id", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({ targetDir, projectName: "my-site" });
    const page = await readFile(join(targetDir, "app/[...slug]/page.tsx"), "utf8");
    assert.match(page, /getPostByUrl/);
    assert.match(page, /generateStaticParams/);
    const lib = await readFile(join(targetDir, "lib/shio.ts"), "utf8");
    assert.match(lib, /@viglet\/shio-client/);
  });
});

test("refuses a non-empty target directory", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "occupied");
    await mkdir(targetDir, { recursive: true });
    await writeFile(join(targetDir, "keep.txt"), "x", "utf8");
    await assert.rejects(
      () => scaffold({ targetDir, projectName: "occupied" }),
      /not empty/
    );
  });
});

test("required args are validated", async () => {
  await assert.rejects(() => scaffold({ projectName: "x" }), /targetDir/);
  await withTmp(async (base) =>
    assert.rejects(() => scaffold({ targetDir: join(base, "p") }), /projectName/)
  );
});
