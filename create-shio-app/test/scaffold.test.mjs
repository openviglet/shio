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
      "app/api/revalidate/route.ts",
      "app/api/preview/route.ts",
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
    // SH9 preview/webhook keys are scaffolded empty.
    assert.match(env, /SHIO_PREVIEW_TOKEN=\n/);
    assert.match(env, /SHIO_PREVIEW_SECRET=\n/);
    assert.match(env, /SHIO_WEBHOOK_SECRET=\n/);
  });
});

test("revalidate route verifies the webhook signature and calls revalidatePath", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({ targetDir, projectName: "my-site" });
    const route = await readFile(
      join(targetDir, "app/api/revalidate/route.ts"),
      "utf8"
    );
    assert.match(route, /x-shio-signature/);
    assert.match(route, /timingSafeEqual/);
    assert.match(route, /revalidatePath/);
  });
});

test("preview route gates on the secret and toggles draft mode", async () => {
  await withTmp(async (base) => {
    const targetDir = join(base, "my-site");
    await scaffold({ targetDir, projectName: "my-site" });
    const route = await readFile(
      join(targetDir, "app/api/preview/route.ts"),
      "utf8"
    );
    assert.match(route, /draftMode/);
    assert.match(route, /PREVIEW_SECRET/);
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

/* --- SH119: the pieces `shio init --blueprint` reuses --- */

test("substituteParams applies the same rule the server does", async () => {
  const { substituteParams } = await import("../src/scaffold.mjs");
  assert.equal(substituteParams('{"name":"{{projectName}}"}', { projectName: "x" }), '{"name":"x"}');
  assert.equal(substituteParams("{{ projectName }}", { projectName: "x" }), "x");
  // Character-for-character the server's `[A-Za-z0-9_.\-]+`, so a package cannot have a
  // parameter one half fills in and the other leaves standing.
  assert.equal(substituteParams("{{brand-name}}", { "brand-name": "Acme" }), "Acme");
  // JSX is not a placeholder — which is what lets a starter tree carry both.
  assert.equal(substituteParams('style={{ color: "#666" }}', { color: "red" }),
    'style={{ color: "#666" }}');
  // An unknown placeholder stays visible rather than being silently blanked.
  assert.equal(substituteParams("{{nope}}", { projectName: "x" }), "{{nope}}");
});

test("readTemplateFiles returns the tree in the shape a blueprint's files arrive in", async () => {
  const { readTemplateFiles } = await import("../src/scaffold.mjs");
  const files = await readTemplateFiles();
  assert.ok(files.length > 10, "the starter is more than a handful of files");
  for (const file of files) {
    assert.equal(typeof file.path, "string");
    assert.equal(typeof file.text, "string");
    assert.ok(file.path.length > 0);
  }
  // The npm dotfile dodge is undone once, here, so both consumers agree about it.
  assert.ok(files.some((f) => f.path === ".gitignore"));
  assert.ok(!files.some((f) => f.path === "gitignore"));
});

test("writeProject skips an existing file unless forced", async () => {
  const { writeProject } = await import("../src/scaffold.mjs");
  await withTmp(async (base) => {
    const targetDir = join(base, "proj");
    await mkdir(targetDir, { recursive: true });
    await writeFile(join(targetDir, "keep.txt"), "mine", "utf8");

    const first = await writeProject({
      targetDir,
      files: [{ path: "keep.txt", text: "theirs" }, { path: "new.txt", text: "hi" }],
    });
    assert.deepEqual(first.skipped, ["keep.txt"]);
    assert.deepEqual(first.files, ["new.txt"]);
    assert.equal(await readFile(join(targetDir, "keep.txt"), "utf8"), "mine");

    const forced = await writeProject({
      targetDir,
      files: [{ path: "keep.txt", text: "theirs" }],
      force: true,
    });
    assert.deepEqual(forced.files, ["keep.txt"]);
    assert.equal(await readFile(join(targetDir, "keep.txt"), "utf8"), "theirs");
  });
});
