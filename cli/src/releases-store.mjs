/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH993 — a release as a file: `shio/releases/<site>/<name>.yaml`, so a launch is reviewable in a
 * pull request rather than only in an instance.
 *
 * The format half is pure (no fs, no network), like `content-files.mjs`, so what a release looks like
 * on disk is testable without an instance and without a temp directory.
 *
 * **Two of the five fields are read-only, and that is a safety decision rather than an omission.**
 * `state` and `scheduledAt` are written so a reviewer can see what a release is doing, and ignored on
 * push: a file that could schedule or publish a launch would mean `shio push --content` — the command
 * a CI job runs on every merge — putting forty pages live because somebody edited a line. Publishing
 * stays `release.run` with its confirm token (P7). What the file DOES declare is the membership and
 * the description, which is the half a person actually reviews.
 */

/** Where a release lands in the tree. */
export const RELEASES_DIR = "shio/releases";

/** `shio/releases/<site>/<name>.yaml` — one directory per site, the shape `shio/content` uses. */
export function releasePathFor(site, name) {
  return `${site}/${name}.yaml`;
}

const READ_ONLY = ["state", "scheduledAt"];

/**
 * The file a release projects to.
 *
 * Hand-written rather than emitted by a YAML library, for `content-files.mjs`'s reason: the projection
 * has to be byte-stable (P5) so a re-pull of unchanged content is an empty diff, and a serializer's
 * defaults are not a contract this repository controls.
 */
export function renderRelease(release) {
  const lines = [
    "# Written by `shio pull --content`. `state` and `scheduledAt` are read-only:",
    "# running a release is `release.run`, which takes a confirm token (SH993).",
    `name: ${release.name}`,
    `site: ${release.site}`,
  ];
  if (release.description) lines.push(`description: ${quote(release.description)}`);
  lines.push(`state: ${release.state}`);
  if (release.scheduledAt) lines.push(`scheduledAt: ${release.scheduledAt}`);
  lines.push("posts:");
  for (const address of release.posts) lines.push(`  - ${address}`);
  return `${lines.join("\n")}\n`;
}

/**
 * Reads one back.
 *
 * A deliberately small parser over the shape this file writes: `key: value` and a `-` list under
 * `posts:`. It refuses what it cannot read rather than guessing, because a release file that parses
 * to half its posts is a launch missing pages nobody will notice until it runs.
 */
export function parseRelease(text, path = "<release>") {
  const release = { posts: [] };
  let inPosts = false;
  for (const raw of String(text).split(/\r?\n/)) {
    const line = raw.trimEnd();
    if (!line || line.trimStart().startsWith("#")) continue;
    if (inPosts && line.startsWith("  - ")) {
      release.posts.push(line.slice(4).trim());
      continue;
    }
    const match = /^([A-Za-z]+):\s*(.*)$/.exec(line);
    if (!match) {
      throw new Error(
        `${path}: cannot read line ${JSON.stringify(line)}. A release file is \`key: value\` ` +
          "lines plus a `posts:` list of `  - post:<site>/<url>` entries; re-create it with " +
          "`shio pull --content`.",
      );
    }
    inPosts = match[1] === "posts";
    if (!inPosts) release[match[1]] = unquote(match[2]);
  }
  if (!release.name || !release.site) {
    throw new Error(`${path}: a release file needs \`name\` and \`site\`.`);
  }
  return release;
}

/**
 * The ops that make the instance match the files, and the releases left alone with the reason.
 *
 * A release that has run is closed (SH993), so converging its membership would be refused op by op —
 * reported here instead, which is the difference between a push that says what it skipped and one
 * that fails halfway through a launch somebody already shipped.
 */
export function releaseOps(local, remote) {
  const ops = [];
  const skipped = [];
  const byAddress = new Map(remote.map((release) => [addressOf(release), release]));
  for (const release of local) {
    const address = addressOf(release);
    const held = byAddress.get(address);
    if (held && held.state !== "PLANNED" && held.state !== "SCHEDULED") {
      skipped.push({ address, reason: `it is ${held.state} — a release that ran is closed` });
      continue;
    }
    if (!held || (release.description ?? "") !== (held.description ?? "")) {
      ops.push({
        op: "release.upsert",
        address,
        ...(release.description ? { data: { description: release.description } } : {}),
      });
    }
    const wanted = new Set(release.posts);
    const there = new Set(held ? held.posts : []);
    const add = release.posts.filter((post) => !there.has(post));
    const remove = [...there].filter((post) => !wanted.has(post));
    if (add.length) ops.push({ op: "release.add", address, data: { posts: add } });
    if (remove.length) ops.push({ op: "release.remove", address, data: { posts: remove } });
  }
  return { ops, skipped };
}

/** The addresses a projection writes, so a pull can delete the files for releases that are gone. */
export function addressOf(release) {
  return `release:${release.site}/${release.name}`;
}

/** Only where it has to be: a description with a `#` or a leading space would not read back. */
function quote(value) {
  return /^[A-Za-z0-9][A-Za-z0-9 ,.\-_()]*$/.test(value) ? value : JSON.stringify(value);
}

function unquote(value) {
  const text = value.trim();
  if (!text.startsWith('"')) return text;
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

/** The fields a file states and a push ignores — named here so the report can say so. */
export const READ_ONLY_FIELDS = READ_ONLY;
