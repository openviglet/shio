/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1433 — the console's strings name the product in full.
 *
 * The site's full-names lint (shio-site/scripts/full-names.test.mjs) never read the console's locale
 * files, so "outra instância do Shio" shipped on the first-site screen after the owner's rule
 * (always "Viglet Shio", on screen and in voice) was already enforced everywhere it looked. This reads
 * every string value in every locale file; keys are identifiers and are not read.
 */
import { strict as assert } from "node:assert"
import { readdirSync, readFileSync, statSync } from "node:fs"
import { dirname, join, relative } from "node:path"
import { test } from "node:test"
import { fileURLToPath } from "node:url"

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..")
const LOCALES = join(ROOT, "src", "i18n", "locales")

/** A bare product name: not after `Viglet `, not part of a path, a package or a file name. */
const BARE = /(?<!Viglet )(?<![\w/.@-])(Shio|Turing|Dumont)\b(?![\w-]*\.(?:java|ts|mjs|md))/u

const walk = (dir) => readdirSync(dir).flatMap((name) => {
  const path = join(dir, name)
  return statSync(path).isDirectory() ? walk(path) : [path]
})

/** Every string in a locale document, nested ones included, with its key path. */
function strings(value, path = []) {
  if (typeof value === "string") return [[path.join("."), value]]
  if (value && typeof value === "object") {
    return Object.entries(value).flatMap(([key, each]) => strings(each, [...path, key]))
  }
  return []
}

test("SH1433: every console string names the product Viglet Shio", () => {
  const files = walk(LOCALES).filter((path) => path.endsWith(".json"))
  assert.ok(files.length >= 2, "SH1433: no locale file was read, so an empty answer would prove nothing")
  const found = files.flatMap((file) => strings(JSON.parse(readFileSync(file, "utf8")))
    .filter(([, text]) => BARE.test(text))
    .map(([key, text]) => `${relative(ROOT, file).replaceAll("\\", "/")} ${key}: ${text.slice(0, 100)}`))
  assert.deepEqual(found, [],
    "SH1433: a console string calls the product by its short name; write Viglet Shio")
})
