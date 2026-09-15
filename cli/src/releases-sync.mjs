/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH993 — the disk and wire ends of the release projection, kept apart from the format the way
 * `content-store.mjs` is kept apart from `content-files.mjs`.
 *
 * The remote read is two shipped calls and no new endpoint (P10): the context pack's
 * `include=releases` says which releases a site has, and `find?site=&release=<name>` says what each
 * one holds. That is one call per release, which is the right cost for a set of five or ten and is
 * paid only by `--content`, the command that is already walking a site.
 */
import { join } from "node:path";

import {
  RELEASES_DIR,
  addressOf,
  parseRelease,
  releasePathFor,
  renderRelease,
} from "./releases-store.mjs";

/** The release files a tree holds, parsed, in path order. */
export async function readLocalReleases(ctx, { dir = RELEASES_DIR, site = null } = {}) {
  const root = join(ctx.cwd, dir);
  const releases = [];
  for (const siteDir of await entries(ctx, root)) {
    if (site && siteDir !== site) continue;
    for (const file of await entries(ctx, join(root, siteDir))) {
      if (!file.endsWith(".yaml")) continue;
      const path = `${dir}/${siteDir}/${file}`;
      const text = await ctx.fs.readFile(join(root, siteDir, file), "utf8");
      const release = parseRelease(text, path);
      if (release.site !== siteDir) {
        throw new Error(
          `${path}: this file says site ${release.site} and sits under ${siteDir}. The directory is ` +
            "the site, so one of the two is a rename that did not finish.",
        );
      }
      releases.push(release);
    }
  }
  return releases.sort((left, right) => addressOf(left).localeCompare(addressOf(right)));
}

/** Every release of the named sites, with its posts, read through the agent surface. */
export async function readRemoteReleases(agent, sites) {
  const releases = [];
  for (const site of sites) {
    const pack = await agent.getContext({ site, include: "releases", depth: 0 });
    for (const listed of pack.releases ?? []) {
      const name = String(listed.address).split("/").pop();
      const found = await agent.findAll({ site, release: name });
      releases.push({
        name,
        site,
        description: listed.description ?? "",
        state: listed.state,
        scheduledAt: listed.scheduledAt ?? "",
        posts: (found.results ?? []).map((row) => row.address).filter(Boolean).sort(),
      });
    }
  }
  return releases;
}

/**
 * Writes the projection and removes the files of releases the instance no longer lists.
 *
 * A file whose release was archived goes: archiving is how a launch stops being something the next
 * reader scrolls past, and a tree that kept the file would put it straight back in the review.
 */
export async function writeReleases(ctx, releases, { dir = RELEASES_DIR, site = null } = {}) {
  const written = [];
  for (const release of releases) {
    const path = `${dir}/${releasePathFor(release.site, release.name)}`;
    const target = join(ctx.cwd, path);
    await ctx.fs.mkdir(join(ctx.cwd, dir, release.site), { recursive: true });
    await ctx.fs.writeFile(target, renderRelease(release), "utf8");
    written.push(path);
  }
  const keep = new Set(written);
  const removed = [];
  const root = join(ctx.cwd, dir);
  for (const siteDir of await entries(ctx, root)) {
    if (site && siteDir !== site) continue;
    for (const file of await entries(ctx, join(root, siteDir))) {
      const path = `${dir}/${siteDir}/${file}`;
      if (!file.endsWith(".yaml") || keep.has(path)) continue;
      await ctx.fs.rm(join(root, siteDir, file));
      removed.push(path);
    }
  }
  return { written, removed };
}

/** Directory entries, or nothing when the directory is not there yet. */
async function entries(ctx, path) {
  try {
    const found = await ctx.fs.readdir(path);
    return [...found].map((entry) => (typeof entry === "string" ? entry : entry.name)).sort();
  } catch {
    // A tree with no releases is the ordinary case, not a failure: `shio pull --content` into an
    // empty directory has nothing to read, and every other absence here is reported by the write.
    return [];
  }
}
