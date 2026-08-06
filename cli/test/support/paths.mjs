/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH198 — why every fake filesystem in this suite normalizes its keys.
 *
 * The commands join paths with `node:path`, which is correct: they write to a real disk.
 * On Windows that means `join("/repo", "shio", "verify-baseline.json")` is
 * `\repo\shio\verify-baseline.json`, so a fake fs keyed on the POSIX string the test
 * author typed never matches — `readFile` throws ENOENT and the command takes its
 * "no such file" branch. The test then fails on a *message*, several assertions away
 * from the cause, which is why this class of failure read as four unrelated defects.
 *
 * The fix is not to make the commands emit forward slashes. It is for the fake fs to
 * compare paths the way a filesystem does — separator-insensitively — so a test can go
 * on writing the readable literal. `test/content-commands.test.mjs` had this from the
 * start; the files added later did not, so it lives here now and there is one copy.
 */

/** A path as a fake-fs key: one separator, no doubles, comparable across platforms. */
export function norm(path) {
  return String(path).replace(/\\/g, "/").replace(/\/+/g, "/");
}

/** True when `path` ends with `suffix`, whichever separators either one happens to use. */
export function endsWithPath(path, suffix) {
  return norm(path).endsWith(norm(suffix));
}
