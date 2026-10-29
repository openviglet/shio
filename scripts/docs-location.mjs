/*
 * Copyright (C) 2016-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0.
 *
 * SH1262 — where the end-user documentation is, for every gate that reads it.
 *
 * It used to be `docs-shio/` in the vigletdocs repository, so each reader carried that checkout's
 * path as its default and CI cloned a second repository to run them. The pages and the coverage
 * matrix now live in this tree, under the Starlight area at openviglet.github.io/shio/docs, and this is the
 * one place that says so. `SHIO_DOCS_DIR` still overrides it, which is how a test points a reader
 * at a fixture.
 */
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

/** The pages, with `COVERAGE.md` beside them (the content collection leaves it out). */
export const DEFAULT_DOCS_DIR = join(dirname(fileURLToPath(import.meta.url)), "..",
  "shio-site", "docs", "src", "content", "docs");

export const DOCS_DIR = process.env.SHIO_DOCS_DIR ?? DEFAULT_DOCS_DIR;
