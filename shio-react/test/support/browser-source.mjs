/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * SH1145 — the browser is a directory, so "the browser's source" is the page plus its modules.
 *
 * <p>Fourteen suites read `object.children.page.tsx` and match regexes against it. That was the
 * whole of the browser when they were written and it is 592 lines of it now: the listing's reads,
 * the listbox, the verbs and four dialogs moved into `content-browser/`. A test left reading the
 * page alone asserts nothing about the code it was written for — the worst outcome available here,
 * because it keeps passing (SH1118's shape, by relocation instead of by a stripper).</p>
 *
 * <p>So a scan whose subject is <b>the browser</b> reads all of it. A scan whose subject is one
 * file — every `doesNotMatch` saying a particular module must not do a particular thing — keeps
 * naming that file, because widening those would quietly weaken them.</p>
 *
 * <p>Concatenated in a stable order, and the directory is read rather than listed, so a module
 * added tomorrow is in the subject without anybody remembering to add it.</p>
 */
import { readFileSync, readdirSync } from "node:fs"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"

const SRC = join(dirname(fileURLToPath(import.meta.url)), "..", "..", "src")
const BROWSER = join(SRC, "app", "content", "content-browser")

/** The browser page and every module under `content-browser/`, in one string. */
export function browserSource() {
  const parts = [readFileSync(join(SRC, "app", "content", "object.children.page.tsx"), "utf8")]
  for (const name of readdirSync(BROWSER).sort()) {
    if (/\.tsx?$/.test(name)) parts.push(readFileSync(join(BROWSER, name), "utf8"))
  }
  return parts.join("\n")
}

/** One module of the browser, for a scan whose subject is that file and not the browser. */
export function browserModule(name) {
  return readFileSync(join(BROWSER, name), "utf8")
}
